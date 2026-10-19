---
title: "Transfer, cutover and rollback"
description: "Read before planning backlog movement or switching endpoints; execution requires environment-specific approval."
---

## Choose a migration unit

Agree on the producer, queues, consumer groups and business contract being migrated.
Prepare a redacted reconciliation ledger of stable IDs and outcomes, not client
payloads. Define acceptance criteria and a decision owner before moving messages.

Two consumers of the same RabbitMQ queue compete. An old consumer and a bridge do
not each receive every message. A shadow copy requires a dedicated queue/binding
and a decision about how to prevent duplicate business effects.
Naive dual publication has a partial-failure window; assess an outbox rather than
calling two sends an atomic transaction.

## Transfer protocol

1. Validate the target topology, permissions and consumer contract in an approved lab.
2. Approve how historical consumers are stopped or detached for the migration unit.
3. Run the bridge only with explicit source/target and data-movement approval.
4. Preserve stable IDs; send to the target, wait for confirmation, then ACK the source.
5. On send failure, leave the source unacknowledged and stop or retry within policy.
6. Reconcile ready and unacknowledged source messages, target accepted/processed
   outcomes and DLQs. Queue depth alone cannot prove business correctness.

This is at-least-once transfer. Crash after send-before-ACK can duplicate delivery;
destination deduplication helps within its configured window only.
Ensure retries cannot silently refresh expiry beyond the business deadline.
Quarantine messages that cannot be mapped using an approved, auditable policy.

## Cutover approval

Present the environment, migration unit, reconciliation result, target readiness,
producer/consumer changes, observation criteria and rollback owner.
Request approval immediately before switching endpoints. Provisioning approval is
not cutover approval. If ordering requires a freeze/drain interval, agree on it;
do not assume zero downtime.

After cutover, verify new publications and business outcomes and observe agreed
metrics. Source decommissioning and data deletion need separate approval.
Do not purge a queue or remove DLQ evidence to obtain a passing count.

## Rollback boundary

Before switching producers, specify where every new message will arrive and who
can process it. Once producers write to Service Bus, switching the endpoint back
does not move Azure backlog into RabbitMQ.
A later rollback may need a freeze, ID-based reconciliation and a reviewed reverse
transfer path with the same idempotency protections.
Keep the source available until business sign-off and retention requirements allow
decommissioning.

## Sources

* [RabbitMQ consumers and competing delivery](https://www.rabbitmq.com/docs/consumers)
* [Service Bus locks and settlement](https://learn.microsoft.com/azure/service-bus-messaging/message-transfers-locks-settlement)
* [Service Bus dead-letter queues](https://learn.microsoft.com/azure/service-bus-messaging/service-bus-dead-letter-queues)
* [Transactional outbox example with Cosmos DB](https://learn.microsoft.com/azure/architecture/databases/guide/transactional-out-box-cosmos)
