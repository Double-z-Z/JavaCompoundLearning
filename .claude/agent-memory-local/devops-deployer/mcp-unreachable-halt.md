---
name: MCP Server Unreachable — Halt Immediately
description: When MCP server on port 8081 is unreachable, stop and report instead of attempting workarounds
type: feedback
---

When the MCP server (localhost:8081/mcp) returns connection refused or is otherwise unreachable, STOP IMMEDIATELY. Do NOT attempt any workarounds such as:
- Querying PostgreSQL directly via Docker
- Manual SSH into target VMs
- Docker exec or docker inspect on the server container
- Any other bypass of the MCP server

The only correct action is to report to the user: "MCP server 未启动，请先启动 devops-dashboard MCP server，然后再调用我。"

**Why:** During the 2026-05-26 redis-counter-service cleanup on VM 103, the agent spent 80+ tool calls and 70k+ tokens trying to work around a missing MCP server. This was a massive waste of time, cost, and context. The MCP server is the single source of truth for all deployment operations — working around it bypasses state machines, validation, and auditing.

**How to apply:** Before any deployment, cleanup, or environment operation, the first step MUST be to verify MCP server reachability. Run `curl -s -X POST localhost:8081/mcp` (or equivalent health check). If the connection is refused or the request fails, halt immediately and report the message above. This check applies to all operations in the devops-dashboard project.
