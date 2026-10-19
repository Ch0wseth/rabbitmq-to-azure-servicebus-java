---
title: "Java and Spring application inspection"
description: "Read to trace RabbitMQ producers and consumers and plan explicit Azure Java adaptations."
---

## Inspect before selecting an adapter

Read `pom.xml` or Gradle configuration and the relevant source.
Look for `com.rabbitmq.client`, `spring-boot-starter-amqp`, `RabbitTemplate`,
`RabbitAdmin`, `@RabbitListener`, `SimpleRabbitListenerContainerFactory`,
`basicPublish`, `basicConsume`, `basicGet`, `basicAck`, `basicNack` and confirms.
Also inspect messaging abstractions such as Spring Cloud Stream or JMS when present.
Avoid searching generated artifacts or secret-bearing configuration.

Trace the effective acknowledgement behavior. A listener annotation alone does
not establish manual acknowledgement, retry policy or atomicity with a database.
Inspect container configuration and transaction boundaries.
For a method receiving a `Channel`, confirms, QoS, transactions and exception handlers
may be configured by its caller. No `confirmSelect` or `basicQos` in the method is
only a local observation, not proof of disabled confirms or unlimited prefetch.
Prefetch and processing concurrency are different settings. If setup is unavailable,
label both unknown. Do not assert callback exception/requeue behavior from a missing
catch alone; verify the actual client, handlers and connection lifecycle.
An unimplemented business-effect stub is not evidence of a completed payment.
Do not replace RabbitTemplate with a presumed drop-in Service Bus template or
rename annotations without checking an actual supported framework integration.

## Decide and preserve behavior

For a native SDK adaptation, verify current `com.azure:azure-messaging-servicebus`
and `com.azure:azure-identity` compatibility with the application. Keep connections
long-lived, close resources, and use repository logging and failure conventions.
Use explicit PeekLock settlement consistent with the chosen sync/async/processor API.
Framework and SDK defaults differ; verify delivery-count semantics before writing
a first-attempt check.

Locate the business side effect and its durable transaction. An in-memory set,
broker deduplication or a `complete` call does not make that effect idempotent.
Preserve a stable business/message ID, use an inbox/outbox where required, and
test a restart after the effect but before confirmed settlement.

Distinguish transport retries from business retries. Define retry budget, delay,
permanent-error classification, lock renewal and DLQ remediation with the client.
Do not expose raw payloads or credentials in logs.

For a temporary bridge, map only metadata with agreed semantics. Preserve bytes
where no transformation is intended. Verify destination send success before source
ACK and leave failures visible. Do not claim all headers, expiration or routing
are preserved by a simple body-and-ID copy.

## Local development and Azure authentication

Use explicit target subscription selection with Azure CLI authentication in
multi-account environments. Validate tenant/subscription and resolve RBAC identities
in the target tenant. Use managed identity for Azure-hosted components and
entity-scoped Sender/Receiver roles rather than broad management roles.
Never read a credential store to generate configuration.

AMQP-over-WebSockets may help environments with port 443 egress, but a proxy can
still block it. Treat deployment, service startup and data-plane access as separate
approved operations, not part of a code-only assessment.

## Sources

* [Java Service Bus quickstart](https://learn.microsoft.com/azure/service-bus-messaging/service-bus-java-how-to-use-queues)
* [Service Bus locks and settlement](https://learn.microsoft.com/azure/service-bus-messaging/message-transfers-locks-settlement)
* [Service Bus Entra authentication](https://learn.microsoft.com/azure/service-bus-messaging/authenticate-application)
* [AzureCliCredentialBuilder](https://learn.microsoft.com/java/api/com.azure.identity.azureclicredentialbuilder)
* [Spring AMQP reference](https://docs.spring.io/spring-amqp/reference/)
