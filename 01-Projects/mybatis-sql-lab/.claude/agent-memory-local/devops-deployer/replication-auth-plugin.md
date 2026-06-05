---
name: MySQL 8.0 Replication Auth Plugin
description: MySQL 8.0 replication requires mysql_native_password instead of caching_sha2_password for the replication user when not using SSL
type: reference
---

**Issue**: MySQL 8.0 defaults to `caching_sha2_password` authentication plugin. When configuring GTID replication within a Docker network without SSL, the slave IO thread fails with: "Authentication requires secure connection."

**Fix**: After creating the replication user on the master, alter it to use `mysql_native_password`:
```sql
ALTER USER 'repl'@'%' IDENTIFIED WITH mysql_native_password BY 'repl123';
FLUSH PRIVILEGES;
```
Then stop/start slave on the replica.

**Why**: `caching_sha2_password` requires either SSL or an extra round-trip RSA exchange that isn't supported over the standard replication protocol connection without configuration.

**How to apply**: For any MySQL 8.0 Docker-based replication setup, explicitly set the replication user to `mysql_native_password` unless SSL is configured for the replication channel.
