#!/bin/bash
# 配置 MySQL 主从复制（GTID 自动定位，无需手动查 binlog 位置）
# 使用方法: bash setup-replication.sh

set -e

echo "=== 等待 Master 就绪 ==="
until docker exec sharding-master mysqladmin ping -h localhost --silent; do
    sleep 2
done

echo "=== 等待 Slave 就绪 ==="
until docker exec sharding-slave mysqladmin ping -h localhost --silent; do
    sleep 2
done

echo "=== 配置 Slave GTID 复制 ==="
docker exec sharding-slave mysql -uroot -proot123 -e "
CHANGE MASTER TO
    MASTER_HOST='mysql-master',
    MASTER_USER='repl',
    MASTER_PASSWORD='repl123',
    MASTER_AUTO_POSITION = 1;   -- GTID: 自动协商起始位点
START SLAVE;
"

sleep 3
echo "=== 验证 ==="
docker exec sharding-slave mysql -uroot -proot123 -e "
SHOW SLAVE STATUS\G" 2>/dev/null | grep -E "Slave_IO_Running|Slave_SQL_Running|Auto_Position|Last_Error"

echo ""
echo "两个 Running 都是 Yes → 复制正常"
echo "Master: localhost:3307"
echo "Slave:  localhost:3308"
