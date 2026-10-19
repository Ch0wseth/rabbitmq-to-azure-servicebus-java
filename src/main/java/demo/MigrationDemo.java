package demo;

import com.azure.core.amqp.AmqpTransportType;
import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.core.util.BinaryData;
import com.azure.identity.AzureCliCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import com.azure.messaging.servicebus.models.SubQueue;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MigrationDemo {
    private static final String RABBIT_QUEUE = "orders";
    private static final String AZURE_QUEUE = env("SERVICEBUS_QUEUE", "orders");

    private MigrationDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("""
                publish-rabbit <run-id>     5 valid + 1 retry + 1 poison
                bridge <count> [crash]      crash after first send, before Rabbit ACK
                consume <run-id> <success-count> <poison-count>
                publish-azure <run-id>      3 valid orders, native producer
                dlq [run-id]               inspect DLQ; optionally verify poison order
                rabbit-depth <expected>    assert source backlog
                """);
            return;
        }
        switch (args[0]) {
            case "publish-rabbit" -> {
                requireArgs(args, 2);
                publishRabbit(runId(args[1]));
            }
            case "bridge" -> {
                if (args.length != 2 && args.length != 3) {
                    throw new IllegalArgumentException("bridge <count> [crash]");
                }
                if (args.length == 3 && !"crash".equals(args[2])) {
                    throw new IllegalArgumentException("Only optional argument: crash");
                }
                bridge(positive(args[1]), args.length == 3);
            }
            case "consume" -> {
                requireArgs(args, 4);
                consume(runId(args[1]), positive(args[2]), nonNegative(args[3]));
            }
            case "publish-azure" -> {
                requireArgs(args, 2);
                publishAzure(runId(args[1]));
            }
            case "dlq" -> {
                if (args.length != 1 && args.length != 2) {
                    throw new IllegalArgumentException("dlq [run-id]");
                }
                inspectDlq(args.length == 2 ? runId(args[1]) : null);
            }
            case "rabbit-depth" -> {
                requireArgs(args, 2);
                try (Connection connection = rabbit(); Channel channel = connection.createChannel()) {
                    declareQueue(channel);
                    int depth = channel.queueDeclarePassive(RABBIT_QUEUE).getMessageCount();
                    System.out.printf("RABBIT_DEPTH count=%d%n", depth);
                    if (depth != nonNegative(args[1])) {
                        throw new IllegalStateException("Unexpected RabbitMQ backlog: " + depth);
                    }
                }
            }
            default -> throw new IllegalArgumentException("Unknown command: " + args[0]);
        }
    }

    private static Connection rabbit() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setUsername(env("RABBITMQ_USER", "guest"));
        factory.setPassword(env("RABBITMQ_PASSWORD", "guest"));
        factory.setAutomaticRecoveryEnabled(false);
        return factory.newConnection("migration-demo");
    }

    private static void declareQueue(Channel channel) throws Exception {
        channel.queueDeclare(RABBIT_QUEUE, true, false, false, null);
    }

    private static ServiceBusClientBuilder azure() {
        String namespace = requiredEnv("SERVICEBUS_NAMESPACE");
        // Azure CLI is deterministic for this local demo; use ManagedIdentityCredential in Azure.
        return new ServiceBusClientBuilder()
            .credential(namespace, new AzureCliCredentialBuilder()
                .subscription(requiredEnv("AZURE_SUBSCRIPTION_ID"))
                .processTimeout(Duration.ofSeconds(30)).build())
            .retryOptions(new AmqpRetryOptions().setTryTimeout(Duration.ofSeconds(20))
                .setMaxRetries(2).setDelay(Duration.ofSeconds(1)).setMaxDelay(Duration.ofSeconds(5)))
            .transportType(AmqpTransportType.AMQP_WEB_SOCKETS);
    }

    private static ServiceBusSenderClient sender() {
        return azure().sender().queueName(AZURE_QUEUE).buildClient();
    }

    private static void publishRabbit(String run) throws Exception {
        try (Connection connection = rabbit(); Channel channel = connection.createChannel()) {
            declareQueue(channel);
            channel.confirmSelect();
            AtomicBoolean returned = new AtomicBoolean();
            channel.addReturnListener(message -> returned.set(true));
            for (int i = 1; i <= 7; i++) {
                String kind = i == 6 ? "retry" : i == 7 ? "poison" : "normal";
                String id = run + "-" + i;
                AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                    .messageId(id).correlationId(run).type(kind)
                    .contentType("application/json").deliveryMode(2).build();
                channel.basicPublish("", RABBIT_QUEUE, true, properties, body(id, kind).getBytes(StandardCharsets.UTF_8));
                channel.waitForConfirmsOrDie(10_000);
                if (returned.get()) {
                    throw new IllegalStateException("RabbitMQ returned an unroutable order " + id);
                }
                System.out.printf("RABBIT_CONFIRMED id=%s kind=%s%n", id, kind);
            }
        }
    }

    static ServiceBusMessage map(AMQP.BasicProperties properties, byte[] body) {
        String id = properties.getMessageId();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Source message has no stable messageId; not acknowledged");
        }
        ServiceBusMessage message = new ServiceBusMessage(BinaryData.fromBytes(body)).setMessageId(id);
        if (properties.getCorrelationId() != null) {
            message.setCorrelationId(properties.getCorrelationId());
        }
        if (properties.getContentType() != null) {
            message.setContentType(properties.getContentType());
        }
        if (properties.getType() != null) {
            message.setSubject(properties.getType());
        }
        message.getApplicationProperties().put("migrationSource", "rabbitmq");
        return message;
    }

    @FunctionalInterface
    interface Action {
        void run() throws Exception;
    }

    static void sendThenAck(Action send, Action ack, boolean crash) throws Exception {
        send.run();
        if (crash) {
            throw new IllegalStateException("SIMULATED_CRASH_AFTER_SEND_BEFORE_ACK");
        }
        ack.run();
    }

    private static void bridge(int count, boolean crash) throws Exception {
        try (Connection connection = rabbit();
             Channel channel = connection.createChannel();
             ServiceBusSenderClient sender = sender()) {
            declareQueue(channel);
            Instant deadline = Instant.now().plusSeconds(45);
            int forwarded = 0;
            while (forwarded < count && Instant.now().isBefore(deadline)) {
                GetResponse source = channel.basicGet(RABBIT_QUEUE, false);
                if (source == null) {
                    Thread.sleep(200);
                    continue;
                }
                ServiceBusMessage message = map(source.getProps(), source.getBody());
                sendThenAck(
                    () -> {
                        sender.sendMessage(message);
                        System.out.printf("SERVICEBUS_ACCEPTED id=%s redelivered=%s%n",
                            message.getMessageId(), source.getEnvelope().isRedeliver());
                    },
                    () -> {
                        channel.basicAck(source.getEnvelope().getDeliveryTag(), false);
                        System.out.printf("RABBIT_ACK id=%s%n", message.getMessageId());
                    },
                    crash);
                forwarded++;
            }
            if (forwarded != count) {
                throw new IllegalStateException("Bridge timeout: forwarded " + forwarded + "/" + count);
            }
        }
    }

    private static void publishAzure(String run) {
        try (ServiceBusSenderClient sender = sender()) {
            for (int i = 1; i <= 3; i++) {
                String id = run + "-" + i;
                sender.sendMessage(new ServiceBusMessage(body(id, "normal"))
                    .setMessageId(id).setCorrelationId(run)
                    .setContentType("application/json").setSubject("normal"));
                System.out.printf("NATIVE_AZURE_SENT id=%s%n", id);
            }
        }
    }

    private static void consume(String run, int expectedSuccess, int expectedPoison) {
        Set<String> completed = new HashSet<>();
        Set<String> dead = new HashSet<>();
        Set<String> retried = new HashSet<>();
        try (ServiceBusReceiverClient receiver = azure().receiver().queueName(AZURE_QUEUE)
            .receiveMode(ServiceBusReceiveMode.PEEK_LOCK).disableAutoComplete().prefetchCount(0).buildClient()) {
            Instant deadline = Instant.now().plusSeconds(90);
            while (Instant.now().isBefore(deadline)) {
                boolean received = false;
                for (ServiceBusReceivedMessage message : receiver.receiveMessages(1, Duration.ofSeconds(5))) {
                    received = true;
                    if (!run.equals(message.getCorrelationId())) {
                        receiver.abandon(message);
                        throw new IllegalStateException("Another run is pending: " + message.getMessageId()
                            + ". Use an isolated demo queue.");
                    }
                    String id = message.getMessageId();
                    if (completed.contains(id) || dead.contains(id)) {
                        receiver.abandon(message);
                        throw new IllegalStateException("Duplicate business delivery: " + id);
                    }
                    if ("poison".equals(message.getSubject())) {
                        receiver.deadLetter(message, new DeadLetterOptions()
                            .setDeadLetterReason("InvalidOrder")
                            .setDeadLetterErrorDescription("Demo: order amount is negative"));
                        dead.add(id);
                        System.out.printf("DEAD_LETTER id=%s reason=InvalidOrder%n", id);
                    } else if ("retry".equals(message.getSubject()) && !retried.contains(id)) {
                        receiver.abandon(message);
                        retried.add(id);
                        System.out.printf("ABANDON id=%s delivery=%d%n", id, message.getDeliveryCount());
                    } else {
                        // No real business side effect: production needs a durable inbox/idempotency key.
                        receiver.complete(message);
                        completed.add(id);
                        System.out.printf("COMPLETE id=%s delivery=%d%n", id, message.getDeliveryCount());
                    }
                    if (completed.size() > expectedSuccess || dead.size() > expectedPoison) {
                        throw new IllegalStateException("More messages than expected");
                    }
                }
                if (!received && completed.size() == expectedSuccess && dead.size() == expectedPoison) {
                    if (expectedPoison > 0 && retried.size() != 1) {
                        throw new IllegalStateException("Expected exactly one transient retry");
                    }
                    System.out.printf("VERIFIED run=%s completed=%d dead=%d retried=%d%n",
                        run, completed.size(), dead.size(), retried.size());
                    return;
                }
            }
            throw new IllegalStateException("Receive timeout: completed=" + completed.size() + ", dead=" + dead.size());
        }
    }

    private static void inspectDlq(String run) {
        try (ServiceBusReceiverClient receiver = azure().receiver().queueName(AZURE_QUEUE)
            .subQueue(SubQueue.DEAD_LETTER_QUEUE).buildClient()) {
            int count = 0;
            int matches = 0;
            for (ServiceBusReceivedMessage message : receiver.peekMessages(100)) {
                System.out.printf("DLQ id=%s reason=%s body=%s%n", message.getMessageId(),
                    message.getDeadLetterReason(), message.getBody());
                count++;
                if (run != null && (run + "-7").equals(message.getMessageId())
                    && "InvalidOrder".equals(message.getDeadLetterReason())) {
                    matches++;
                }
            }
            System.out.printf("DLQ_PEEK count=%d (up to 100, not removed)%n", count);
            if (run != null && matches != 1) {
                throw new IllegalStateException("Expected one InvalidOrder in first 100 DLQ messages for " + run);
            }
        }
    }

    private static String body(String id, String kind) {
        return "{\"orderId\":\"" + id + "\",\"amount\":" + ("poison".equals(kind) ? -1 : 42)
            + ",\"currency\":\"EUR\",\"kind\":\"" + kind + "\"}";
    }

    private static void requireArgs(String[] args, int count) {
        if (args.length != count) {
            throw new IllegalArgumentException("Invalid arguments; run without arguments for help");
        }
    }

    private static String runId(String value) {
        if (!value.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("run-id must be 1-80 letters, digits, _ or -");
        }
        return value;
    }

    private static int positive(String value) {
        int number = nonNegative(value);
        if (number == 0) {
            throw new IllegalArgumentException("Count must be positive");
        }
        return number;
    }

    private static int nonNegative(String value) {
        int number = Integer.parseInt(value);
        if (number < 0) {
            throw new IllegalArgumentException("Count cannot be negative");
        }
        return number;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Set environment variable " + name);
        }
        return value;
    }
}
