# MiniDatabase Technical Documentation

This folder contains implementation-level documentation for the current MiniDatabase codebase.

## Documents

- `docs/Architecture-and-Execution-Guide.md`
  - Current architecture by module
  - Connection/session/auth/security handling
  - Query execution internals
  - Data persistence internals
  - Transaction and indexing behavior

- `docs/End-to-End-Data-Flow.md`
  - Narrative request-to-result explanation
  - Parse, plan, execute, persist, and return paths
  - Detailed explanation of how data is updated and saved

- `docs/Java-Application-Integration.md`
  - How another Java application connects to MiniDatabase
  - JDBC usage examples (query, update, transaction)
  - Raw socket protocol example
  - Operational notes and failure handling

- `docs/Flow-and-Visualization.md`
  - End-to-end flow diagrams (Mermaid)
  - Connection, query, and write paths
  - Suggested DB visualization options and implementation plan

- `docs/Operations-Runbook.md`
  - Troubleshooting guide for auth failures and connection resets
  - Corruption indicators and consistency checks
  - Recovery and containment steps
  - Operational response checklist

- `docs/Security-and-Hardening-Guide.md`
  - Current security model and hardening posture
  - Risks, gaps, and deployment recommendations
  - Security roadmap for authz, TLS, audit, and controls

- `docs/Recovery-Design-and-WAL-Roadmap.md`
  - Current rollback behavior vs WAL capabilities
  - Crash recovery target design
  - WAL integration milestones and recovery roadmap

- `docs/Schema-Catalog-and-Storage-Format.md`
  - Catalog model and metadata persistence
  - Row/page/table physical format explanation
  - Storage-format limitations and roadmap

- `docs/Server-Productization-Roadmap.md`
  - Milestone plan to reach full-fledged server readiness
  - JDBC/native protocol compatibility goals
  - Dashboard-readiness and operational acceptance criteria

## Recommended reading order

1. `docs/Architecture-and-Execution-Guide.md`
2. `docs/End-to-End-Data-Flow.md`
3. `docs/Schema-Catalog-and-Storage-Format.md`
4. `docs/Java-Application-Integration.md`
5. `docs/Flow-and-Visualization.md`
6. `docs/Operations-Runbook.md`
7. `docs/Security-and-Hardening-Guide.md`
8. `docs/Recovery-Design-and-WAL-Roadmap.md`
9. `docs/Server-Productization-Roadmap.md`

## Scope notes

This documentation reflects what is currently implemented in code, and explicitly calls out where behavior is scaffold-only or not yet fully integrated.
