---
name: Manual Docker cleanup for non-MCP deployments
description: How to handle cleanup of Docker containers that were deployed outside the MCP system (no environment record exists in the MCP database)
type: reference
---

# Non-MCP Deployment Cleanup Pattern

When asked to undo a deployment on a specific VM, the deployment may exist as a Docker container running on the VM but without a corresponding MCP environment record. In this case:

1. Check the MCP database (`devops_mcp`) for any environment on the target host using Docker exec:
   `docker exec devops-postgres psql -U devops -d devops_mcp -c "SELECT env_id, name, status, host_id FROM environments;"`
2. If no environment exists, the deployment was done outside MCP — only Docker-level cleanup is needed
3. SSH to the VM using the appropriate user from `hosts.yml` and clean up manually:
   - `docker stop <container>` and `docker rm <container>`
   - `docker rmi <image>` to remove cached images

## Database Schema Fix

The `environments` table in the `devops_dashboard` database had a schema mismatch:
- The `@EmbeddedId` field in `EnvironmentId.java` uses `@Column(name = "env_id")` 
- But the actual database column was named `value` (the Java field name) because `ddl-auto: update` cannot rename columns
- Fix: `ALTER TABLE environments RENAME COLUMN value TO env_id;`
- PostgreSQL automatically updates FK references when renaming PK columns
- The `devops_mcp` database already had the correct column name

## Host Access
- VM 103 (`vm-redis-master-103`, 10.0.0.103): SSH user is `redis`, key at `~/.ssh/id_rsa`
- VM 103 has `capabilities: [native]` (no Docker listed in capabilities, but Docker IS present)
