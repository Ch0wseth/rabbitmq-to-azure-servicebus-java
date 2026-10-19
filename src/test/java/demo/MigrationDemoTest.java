package demo;

import com.rabbitmq.client.AMQP;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MigrationDemoTest {
    @Test
    void preservesIdentityPayloadAndRoutingMetadata() {
        byte[] body = "{\"orderId\":\"order-1\"}".getBytes(StandardCharsets.UTF_8);
        var properties = new AMQP.BasicProperties.Builder().messageId("order-1")
            .correlationId("run-1").contentType("application/json").type("retry").build();
        var message = MigrationDemo.map(properties, body);
        assertEquals("order-1", message.getMessageId());
        assertEquals("run-1", message.getCorrelationId());
        assertEquals("application/json", message.getContentType());
        assertEquals("retry", message.getSubject());
        assertArrayEquals(body, message.getBody().toBytes());
    }

    @Test
    void missingStableIdCannotBeForwarded() {
        assertThrows(IllegalArgumentException.class,
            () -> MigrationDemo.map(new AMQP.BasicProperties(), new byte[0]));
    }

    @Test
    void acknowledgesOnlyAfterSuccessfulSend() throws Exception {
        List<String> events = new ArrayList<>();
        MigrationDemo.sendThenAck(() -> events.add("send"), () -> events.add("ack"), false);
        assertEquals(List.of("send", "ack"), events);
    }

    @Test
    void failedSendLeavesSourceUnacknowledged() {
        List<String> events = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> MigrationDemo.sendThenAck(
            () -> { throw new IllegalStateException("Service Bus unavailable"); },
            () -> events.add("ack"), false));
        assertTrue(events.isEmpty());
    }

    @Test
    void simulatedCrashOccursAfterSendWithoutAcknowledging() {
        List<String> events = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> MigrationDemo.sendThenAck(
            () -> events.add("send"), () -> events.add("ack"), true));
        assertEquals(List.of("send"), events);
    }
}
