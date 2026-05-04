# MiniDatabase Technical Documentation

This folder contains implementation-level documentation for the current MiniDatabase codebase.

## Documents

- `docs/Architecture-and-Execution-Guide.md`
  - Current architecture by module
  - Connection/session/auth/security handling
  - Query execution internals
  - Data persistence internals
  - Transaction and indexing behavior

- `docs/Java-Application-Integration.md`
  - How another Java application connects to MiniDatabase
  - JDBC usage examples (query, update, transaction)
  - Raw socket protocol example
  - Operational notes and failure handling

- `docs/Flow-and-Visualization.md`
  - End-to-end flow diagrams (Mermaid)
  - Connection, query, and write paths
  - Suggested DB visualization options and implementation plan

- `docs/End-to-End-Data-Flow.md`
  - Narrative request-to-result explanation
  - Parse, plan, execute, persist, and return paths
  - Detailed explanation of how data is updated and saved

- `docs/Operations-Runbook.md`
  - Troubleshooting guide for auth failures and connection resets
  - Corruption indicators and consistency checks
  - Recovery and containment steps
  - Operational response checklist

## Recommended reading order

1. `docs/Architecture-and-Execution-Guide.md`
2. `docs/End-to-End-Data-Flow.md`
3. `docs/Java-Application-Integration.md`
4. `docs/Flow-and-Visualization.md`
5. `docs/Operations-Runbook.md`

## Scope notes

This documentation reflects what is currently implemented in code, and explicitly calls out where behavior is scaffold-only or not yet fully integrated.
