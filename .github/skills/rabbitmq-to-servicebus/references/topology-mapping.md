---
title: "RabbitMQ and Service Bus topology decisions"
description: "Read during migration assessment to identify semantic gaps rather than assuming protocol or routing parity."
---

## Inspect the real topology

Separate application declarations from deployed state. Policies, operator overrides
and bindings may exist only on the broker. Accept redacted definitions when provided;
do not fetch broker state or credentials without approval.
The durable/exclusive/auto-delete flags do not identify classic versus quorum.
If `x-queue-type` and effective broker/vhost defaults or an existing queue definition
are unavailable, report the queue type as unknown, not classic by default.

| Observed RabbitMQ behavior | Service Bus option and decision to verify |
|---------------------------|------------------------------------------|
| Work queue | A queue, with competing consumers; verify settlement and retry semantics |
| Fanout to independent queues | Topic with one subscription per independent delivery stream |
| Direct exchange | Application properties and subscription filters; compare all bindings |
| Topic exchange with `*` / `#` | Explicit routing design and tests; wildcard grammar is not interchangeable |
| Headers exchange | Property mapping and filter design; identify unsupported types or predicates |
| Priority queue | No assumed direct equivalent; agree on separate queues or scheduler and fairness |
| Quorum queue | Broker implementation property, not an Azure queue setting to copy |
| RabbitMQ DLX | Target DLQ plus an explicit remediation/replay workflow, not a copied binding |
| Per-key ordering | Consider sessions and stable session IDs; verify concurrency and retry ordering |
| TTL / expiration | Preserve the intended expiry deadline, not a fresh TTL on every resend |

AMQP 0-9-1 clients cannot migrate by changing the endpoint to Service Bus AMQP 1.0.
Check the actual client protocol; some systems use AMQP 1.0 or JMS and have different
adaptation paths. Do not infer compatibility just from the string "AMQP".

## Preserve message contracts

Record the body encoding/schema, message and correlation IDs, headers, routing key,
type, expiry, persistence and any session/partition requirements.
Stable message IDs must survive bridge restarts. Missing IDs require an approved
business-key or quarantine policy, not a fresh random ID per delivery.

Deduplication is finite-window broker behavior, available in Standard/Premium,
not Basic. It does not make business side effects exactly-once. Check partition
and session settings as they affect identity and routing.
Verify current service limits, SDK compatibility and regional/SKU features in
official documentation before generating infrastructure.

## Evidence to request when absent

Queue depth including unacknowledged messages, message sizes, throughput and bursts,
processing time distribution, redelivery rate, existing DLQ backlog, required
ordering, recovery objectives and acceptable duplication of business effects.
Keep unknowns explicit instead of inventing operating thresholds.

## Sources

* [RabbitMQ exchanges](https://www.rabbitmq.com/docs/exchanges)
* [RabbitMQ confirms and acknowledgements](https://www.rabbitmq.com/docs/confirms)
* [Service Bus AMQP overview](https://learn.microsoft.com/azure/service-bus-messaging/service-bus-amqp-overview)
* [Service Bus topic filters](https://learn.microsoft.com/azure/service-bus-messaging/topic-filters)
* [Service Bus duplicate detection](https://learn.microsoft.com/azure/service-bus-messaging/duplicate-detection)
* [Service Bus message sessions](https://learn.microsoft.com/azure/service-bus-messaging/message-sessions)
