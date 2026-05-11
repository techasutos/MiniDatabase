# MiniDatabase Server Productization Roadmap

This roadmap defines what is required to evolve MiniDatabase from a strong foundation engine into a full-fledged database server suitable for third-party applications.

It is organized as milestones with acceptance criteria so implementation can proceed incrementally without losing interoperability.

## Objectives

- Run MiniDatabase as a stable long-running database server.
- Support third-party Java applications through JDBC.
- Maintain a native query protocol for custom clients.
- Add operational visibility and administration APIs.
- Enable a later-phase dashboard similar in spirit to pgAdmin.

## Current baseline (already achieved)

- TCP text protocol server with auth handshake.
- JDBC driver and connection primitives.
- SQL parser + execution pipeline including joins and planner index-scan selection.
- WAL-backed recovery baseline (startup replay, checkpoint, truncation, CRC validation).
- PageLSN and WAL flush-gate enforcement.

## Milestone plan

## Milestone 1: Protocol contract and compatibility foundation

Goal: make client/server behavior explicit and versioned before deep feature expansion.

Scope:
- Define protocol capability negotiation.
- Keep backward compatibility with current text SQL flow.
- Add connector compatibility matrix and acceptance tests for client handshake behavior.

Acceptance criteria:
- Server exposes capability/introspection command.
- JDBC client can query capabilities and gracefully fall back when unsupported.
- Existing query execution path remains backward compatible.

## Milestone 2: JDBC hardening for third-party apps

Goal: support mainstream application integration patterns.

Scope:
- Prepared statement behavior consistency.
- Result metadata correctness and typed value mapping.
- Connection lifecycle hardening (timeouts, retries, clean close semantics).
- Better SQLState/error code mapping.

Acceptance criteria:
- Connection pool smoke tests pass (for example HikariCP basic lifecycle).
- Spring JDBC smoke tests pass for CRUD and transactions.
- Driver behavior documented with unsupported feature list.

## Milestone 3: Transaction concurrency correctness

Goal: move from simplified single-session assumptions to multi-session correctness.

Scope:
- Lock manager or MVCC baseline.
- Isolation level definitions and enforcement.
- Deadlock detection/timeout policy.
- Transactional index maintenance guarantees.

Acceptance criteria:
- Concurrent write/read conflict tests pass deterministically.
- Isolation behavior documented and validated by tests.
- Failure-mode behavior defined for deadlocks/timeouts.

## Milestone 4: Durability and storage lifecycle hardening

Goal: production-safe recoverability and bounded restart behavior.

Scope:
- Background/interval checkpoint scheduling.
- WAL retention policies and lifecycle controls.
- Durable allocation/index metadata lifecycle.
- Corruption detection coverage expansion.

Acceptance criteria:
- Restart replay window is bounded by checkpoint policy.
- Operator-configurable WAL retention is available.
- Recovery observability metrics are emitted.

## Milestone 5: Server operations and security baseline

Goal: safe deployment in real environments.

Scope:
- Persistent users/roles/grants.
- TLS transport mode.
- Audit/event logging and session IDs.
- Health endpoints and metrics export.

Acceptance criteria:
- Authn/authz model supports role-scoped permissions.
- Secure transport option is documented and validated.
- Basic SRE runbook for backup/restore and incident response is complete.

## Milestone 6: Dashboard-ready administration APIs

Goal: prepare backend APIs needed by a pgAdmin-like UI.

Scope:
- Metadata browse APIs (databases/schemas/tables/indexes).
- Session/query monitoring APIs.
- Admin operations (cancel query, checkpoint trigger).
- Query history and diagnostics payloads.

Acceptance criteria:
- API contract for dashboard backend is versioned.
- Read-only admin views are stable.
- Controlled admin actions are permission-gated.

## Milestone 7: Dashboard (later phase)

Goal: provide operator/developer UI similar to pgAdmin-style workflows.

Scope:
- Schema object tree explorer.
- SQL editor and result grid.
- Query/session monitor.
- Basic index/storage and recovery insights panels.

Acceptance criteria:
- End-to-end workflow: connect, browse, query, monitor.
- Role-based admin actions available from UI.
- Deployment guide available for standalone dashboard service.

## Immediate implementation slice (starting now)

This branch starts with Milestone 1:

- Add protocol capability negotiation command.
- Add JDBC capability discovery with fallback.
- Keep existing clients fully compatible.

## Definition of done for "full-fledged database server"

MiniDatabase can be treated as full-fledged for application usage when all are true:

- Multi-session concurrency correctness is defined and tested.
- JDBC integrations work reliably with standard application stacks.
- Recovery and durability behavior is deterministic and operationally visible.
- Security model includes authn/authz and secure transport.
- Operator tooling exists for routine management and incident handling.

