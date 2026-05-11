# MiniDB Dashboard

A shadcn-style dashboard for MiniDatabase built with React, Tailwind CSS, and Lucide icons.

## Features in this first delivery

- Login authentication to connect to a specific MiniDB instance (host/port/user/password)
- Live top bar with connection status, active user, and WAL/recovery indicators
- Overview cards with live health and protocol capability checks
- Live schema explorer backed by SHOW DATABASES / SHOW SCHEMAS / SHOW TABLES protocol commands
- Table-wise live data preview from selected schema/table
- Quick object actions for create/update/delete of databases, schemas, and tables (dialog-based)
- Right-click on database/schema/table nodes to open create/update/delete actions
- Dropdown and right-click schema sidebar menus now include relevant object management by scope
- Table right-click menu includes DDL/DML template actions that open SQL editor draft
- SQL editor with multiline script execution (semicolon-separated statements)
- DDL/DML template generation by clicking table names and action buttons
- Column-aware INSERT/UPDATE template generation from live table metadata
- Safety confirmations for DROP/ALTER actions in quick object workflows
- Query history and saved snippets persisted locally in the browser
- Live query monitor with cancel controls
- Result grid and raw response panel
- Recovery panel showing WAL/checkpoint readiness indicators
- Live session monitor backed by SHOW SESSIONS

The dashboard connects through a local Node API bridge that speaks MiniDB's existing TCP text protocol.

## Prerequisites

- Node.js 18+
- MiniDatabase server running (default: `localhost:5544`)

## Setup

```bash
cd minidb-dashboard
cp .env.example .env
npm install
```

## Run in development

```bash
npm run dev
```

- Web UI: `http://localhost:5173`
- API bridge: `http://localhost:7070`

## Build

```bash
npm run build
```

## Environment variables

- `MINIDB_HOST` default `127.0.0.1`
- `MINIDB_PORT` default `5544`
- `MINIDB_USER` default `admin`
- `MINIDB_PASSWORD` default `minidb`
- `DASHBOARD_API_PORT` default `7070`

## Notes

- The current MiniDB server protocol is line-based text plus `END` terminator.
- The dashboard bridge exposes metadata/session/query APIs through the MiniDB text protocol.
- Authenticated endpoints require login via the dashboard login form (Bearer token stored locally).
- Query history and snippets are local-first in this MVP.
- Query monitor cancel is best-effort at the bridge layer until deeper server-side preemption is added.
- Create/update/delete object actions are opened from a modal dialog instead of inline page-bottom forms.

