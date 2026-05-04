# Security and Hardening Guide

This guide describes the current security model of MiniDatabase, the hardening measures that are already present, the gaps that still exist, and the recommended path to make the system safer for real environments.

This document is implementation-aligned.

---

## 1. Overview

MiniDatabase currently has a minimal but real security boundary:

1. A client connects over TCP
2. The protocol layer requires authentication before entering the SQL command loop
3. Only authenticated clients can submit SQL
4. Errors returned to clients are sanitized to reduce protocol-level leakage

That gives the system a usable access boundary for development and internal testing, but it is not yet a production-grade security model.

---

## 2. Security model implemented now

## 2.1 Authentication

Authentication is enforced by the text protocol handler:
- `minidb-transport/src/main/java/com/minidb/transport/protocol/TextProtocolHandler.java`

Current handshake:
1. Server sends `MINIDB 1.0`
2. Server sends `AUTH`
3. Client sends username
4. Client sends password
5. `AuthService.authenticate(...)` is invoked
6. Server returns `OK` or `ERROR: Authentication failed`

### Current auth implementation
- `InMemoryAuthService`
- Default account: `admin` / `minidb`
- Optional environment-driven account injection:
  - `MINIDB_USER`
  - `MINIDB_PASSWORD`

### What this means in practice
- Authentication exists and is enforced before command execution.
- Credentials are not persisted in a database catalog yet.
- There is no user lifecycle management API yet.

## 2.2 Transport exposure

The server listens on a TCP socket:
- `TcpTransportServer`

Current transport properties:
- Plain TCP
- No TLS encryption
- No mutual authentication
- No IP allowlist
- No connection quota per identity

## 2.3 Error sanitization

`TextProtocolHandler` currently sanitizes error messages before returning them to clients:
- newlines removed
- carriage returns removed

This is useful because it avoids protocol injection through server error payloads.

## 2.4 Session security

Current session model is socket-scoped:
- one socket
- one authenticated command loop
- one active client identity in handler context

There is no separate server-side session registry yet.

---

## 3. Current hardening already present

Even though the system is early-stage, several useful hardening choices are already in place.

### 3.1 Bounded worker queue
`TcpTransportServer` uses:
- fixed-size thread pool
- bounded queue (`LinkedBlockingQueue<>(1024)`)
- `CallerRunsPolicy`

This is not full abuse protection, but it is better than unbounded task growth.

### 3.2 Graceful shutdown path
`DatabaseServer` registers a shutdown hook that stops the transport server cleanly.

This reduces the chance of leaving server resources open during normal stop paths.

### 3.3 Dirty-page flushing discipline
The storage layer flushes dirty pages through the buffer pool and disk manager rather than writing through many ad hoc paths.

That is an operational hardening benefit because storage writes are more centralized.

### 3.4 Authentication-before-SQL
The system does not enter SQL command loop until auth succeeds.

That prevents anonymous SQL execution in the current protocol path.

---

## 4. Security gaps and risks in the current implementation

## 4.1 No transport encryption
This is the biggest current gap.

Implications:
- Username/password travel in plaintext over TCP
- SQL traffic is readable on the network
- Returned data is readable on the network

Operational guidance:
- Use only in trusted local/dev/internal networks for now
- If needed, place behind a TLS tunnel or private network boundary

## 4.2 No authorization layer
Current behavior:
- If authenticated, the client can execute any currently supported SQL path

Missing pieces:
- roles
- grants/revokes
- schema-level permissions
- table-level permissions
- admin-only operations

## 4.3 No audit trail
There is no persistent audit log for:
- who connected
- what SQL they executed
- what data they changed
- when authentication failed

Only basic runtime logging exists.

## 4.4 In-memory user store only
The default auth service is in-memory.

Implications:
- no persistent user catalog
- no password rotation workflow
- no role membership model
- restart behavior depends on startup environment, not durable identity storage

## 4.5 No rate limiting / brute force protection
Current protocol handler does not implement:
- failed login throttling
- lockouts
- per-IP throttling
- connection rate limiting per origin

## 4.6 No SQL-level resource controls
There are no resource governance controls yet for:
- max query runtime
- max rows returned
- max memory per query
- max connections per user

---

## 5. How security currently works end-to-end

### Connection and auth flow
1. Client opens socket
2. Server sends greeting and auth prompt
3. Credentials are submitted
4. `AuthService` decides allow/deny
5. On success, SQL command loop begins
6. On failure, connection is closed

### Query flow after auth
1. Client sends SQL line
2. SQL is parsed
3. AST is executed by `Engine`
4. Result text is returned to authenticated client

Important note:
- after authentication, there is currently no second-layer authorization check inside the engine

---

## 6. Hardening recommendations for the next phase

## 6.1 Immediate hardening
These are the highest-value improvements.

### Add TLS or protected transport boundary
Recommended options:
1. Native TLS in transport module
2. Reverse proxy / tunnel termination in front of MiniDB
3. Private network-only deployment until native TLS exists

### Replace in-memory auth with persistent identity catalog
Introduce:
- `UserRepository`
- durable credential storage
- password hashing policy
- admin/user roles

### Add authorization checks in execution path
Enforce privileges for:
- CREATE/DROP DATABASE
- CREATE/DROP SCHEMA
- CREATE/DROP TABLE
- INSERT/UPDATE/DELETE
- SELECT

### Add connection/session controls
Implement:
- connection limits
- login throttling
- failed-auth counters
- idle timeout

## 6.2 Medium-term hardening
- audit logging
- structured security events
- query timeout enforcement
- optional statement allowlist/denylist
- password policy and rotation
- secret externalization

## 6.3 Advanced hardening roadmap
- RBAC / ABAC model
- per-schema/table grants
- transport certificates / mTLS
- security metrics dashboard
- intrusion/anomaly detection hooks

---

## 7. Recommended deployment posture today

### Safe-enough for now
- local development
- single-node internal demo
- isolated test environments
- trusted developer network

### Not yet recommended for
- public internet exposure
- multi-tenant deployment
- compliance-sensitive workloads
- production systems requiring encryption, auditability, and role-based permissions

---

## 8. Security checklist for operators

- [ ] Run only on trusted/private network unless protected by tunnel/proxy
- [ ] Change default credentials via environment variables when starting server
- [ ] Limit host exposure and firewall the DB port
- [ ] Back up `data` directory regularly
- [ ] Monitor auth failures and connection churn
- [ ] Use separate environments for test vs development data
- [ ] Do not assume TLS, RBAC, or audit logging exists yet

---

## 9. Suggested future implementation structure

Recommended modules/classes to add:

### Authentication/identity
- persistent `UserRepository`
- `RoleRepository`
- `PermissionService`
- password hashing service with policy controls

### Transport security
- TLS-enabled transport variant
- secure config loader for certificates/keys

### Execution authorization
- privilege interceptor before `Engine.execute(...)`
- object-level permission checks in DDL/DML/SELECT path

### Auditing
- auth success/failure events
- SQL execution audit events
- data mutation audit events

---

## 10. Related documents

- `docs/Architecture-and-Execution-Guide.md`
- `docs/End-to-End-Data-Flow.md`
- `docs/Operations-Runbook.md`
- `docs/Flow-and-Visualization.md`

