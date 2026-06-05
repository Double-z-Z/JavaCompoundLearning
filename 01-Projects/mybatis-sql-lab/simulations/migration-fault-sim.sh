#!/usr/bin/env bash
# =============================================================================
# Migration Fault Injection Simulation
# =============================================================================
# 模拟从单库迁移到分库分表的完整生命周期，注入物理机故障（ds1 宕机），
# 触发回退，恢复后继续推进到完成。
#
# 用法:
#   bash migration-fault-sim.sh                        # 交互模式
#   bash migration-fault-sim.sh --no-pause              # CI 模式
#   bash migration-fault-sim.sh --seed-count 500        # 自定义数据量
#   bash migration-fault-sim.sh --cleanup               # 恢复环境
#   bash migration-fault-sim.sh --reset-only            # 仅重置表
#
# 前提条件:
#   1. Docker 容器 sharding-master、sharding-slave 运行中
#   2. Spring Boot 应用在 8089 端口运行，migration.active=true
# =============================================================================

set +e  # 故障注入阶段 curl 会有预期失败，不能中断脚本

# ===== Configuration =====
BASE_URL="${BASE_URL:-http://localhost:8089}"
SEED_COUNT="${SEED_COUNT:-200}"
PAUSE_SEC="${PAUSE_SEC:-3}"
NO_PAUSE=false
CLEANUP_ONLY=false
RESET_ONLY=false
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
METRICS_DIR="${SCRIPT_DIR}/metrics-${TIMESTAMP}"
PHASE_NUM=0
TOTAL_PHASES=17

mkdir -p "$METRICS_DIR"

# ===== Colors =====
RED='\033[0;31m';    GREEN='\033[0;32m';    YELLOW='\033[1;33m'
BLUE='\033[0;34m';   MAGENTA='\033[0;35m';  CYAN='\033[0;36m'
BOLD='\033[1m';      NC='\033[0m'

# ===== Argument Parsing =====
while [[ $# -gt 0 ]]; do
    case "$1" in
        --base-url)    BASE_URL="$2";    shift 2 ;;
        --seed-count)  SEED_COUNT="$2";  shift 2 ;;
        --pause)       PAUSE_SEC="$2";   shift 2 ;;
        --no-pause)    NO_PAUSE=true;    shift   ;;
        --cleanup)     CLEANUP_ONLY=true; shift ;;
        --reset-only)  RESET_ONLY=true;  shift ;;
        --help)
            echo "Usage: bash migration-fault-sim.sh [options]"
            echo "  --base-url <url>     API base URL (default: http://localhost:8089)"
            echo "  --seed-count <N>     Orders to seed (default: 200)"
            echo "  --pause <sec>        Seconds between phases (default: 3)"
            echo "  --no-pause           Skip pauses (CI mode)"
            echo "  --cleanup            Restore environment only"
            echo "  --help               This message"
            exit 0 ;;
        *) echo "Unknown: $1"; exit 1 ;;
    esac
done

# ===== Reset Mode =====
if $RESET_ONLY; then
    log_phase "Reset Tables Only"
    docker start sharding-slave 2>/dev/null || true
    phase_reset_tables
    echo "Tables reset. Ready for fresh simulation."
    exit 0
fi

# ===== Cleanup Mode =====
if $CLEANUP_ONLY; then
    log_phase "Cleanup + Reset"
    docker start sharding-slave 2>/dev/null || true
    sleep 3
    phase_reset_tables
    echo "Environment cleaned and tables reset."
    exit 0
fi

# ===== Helpers =====

_ts() { date "+%Y-%m-%d %H:%M:%S"; }

log_phase() {
    PHASE_NUM=$((PHASE_NUM + 1))
    echo ""
    echo -e "${CYAN}${BOLD}══════════════════════════════════════════════════════════════${NC}"
    echo -e "${CYAN}${BOLD}  [$PHASE_NUM/$TOTAL_PHASES] $1${NC}"
    echo -e "${CYAN}${BOLD}  $(_ts)${NC}"
    echo -e "${CYAN}${BOLD}══════════════════════════════════════════════════════════════${NC}"
}

log_step()  { echo -e "${GREEN}  ✓ $1${NC}"; }
log_warn()  { echo -e "${YELLOW}  ⚠ $1${NC}"; }
log_error() { echo -e "${RED}  ✗ $1${NC}"; }
log_fault() { echo -e "${MAGENTA}${BOLD}  ⚡ FAULT: $1${NC}"; }
log_info()  { echo -e "${BLUE}  → $1${NC}"; }

pause() {
    if ! $NO_PAUSE; then
        echo -e "  ${YELLOW}[pause ${PAUSE_SEC}s]${NC}"
        sleep "$PAUSE_SEC"
    fi
}

# curl wrapper: returns body on success, logs error on failure
_curl() {
    local method="$1" url="$2" data="${3:-}"
    local http_code body
    if [ -z "$data" ]; then
        body=$(curl -s -w '\n%{http_code}' -X "$method" "$url" 2>&1)
    else
        body=$(curl -s -w '\n%{http_code}' -X "$method" "$url" \
            -H 'Content-Type: application/json' -d "$data" 2>&1)
    fi
    http_code=$(echo "$body" | tail -1)
    body=$(echo "$body" | sed '$d')
    if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
        echo "$body"
        return 0
    else
        echo "$body"
        return 1
    fi
}

curl_ok()  { _curl "$@" 2>/dev/null; }
curl_maybe_fail() {
    # Used when failure is expected (fault injection)
    local result; result=$(_curl "$@" 2>&1); local rc=$?
    if [ $rc -ne 0 ]; then
        log_error "Request failed (possibly expected): ${result:0:200}"
    else
        log_step "Request succeeded: ${result:0:200}"
    fi
    return $rc
}

capture_metrics() {
    local label="$1"
    local fname="${label}-$(date +%H%M%S)"
    curl_ok GET "${BASE_URL}/api/v2/metrics/shards"     > "${METRICS_DIR}/shards-${fname}.json"  2>/dev/null || true
    curl_ok GET "${BASE_URL}/api/v2/metrics/migration"   > "${METRICS_DIR}/migration-${fname}.json" 2>/dev/null || true
    curl_ok GET "${BASE_URL}/api/v2/migration-sim/status" > "${METRICS_DIR}/status-${fname}.json"  2>/dev/null || true
    curl_ok GET "${BASE_URL}/api/v2/migration-sim/verify" > "${METRICS_DIR}/verify-${fname}.json"  2>/dev/null || true
    log_step "Metrics captured → ${METRICS_DIR}/*-${fname}.json"
}

docker_cmd() {
    docker "$@" 2>&1
    local rc=$?
    if [ $rc -ne 0 ]; then
        log_error "docker $* failed (rc=$rc)"
        return $rc
    fi
    return 0
}

# improved jq formatting, falling back to Python if unavailable
jq_pretty() {
    if command -v jq &>/dev/null; then
        jq '.' 2>/dev/null || cat
    elif command -v python3 &>/dev/null; then
        python3 -m json.tool 2>/dev/null || cat
    else
        cat
    fi
}

# ===== Phase Functions =====

phase_banner() {
    echo ""
    echo -e "${BOLD}${CYAN}"
    echo "  ╔═══════════════════════════════════════════════════════╗"
    echo "  ║     Migration Fault Injection Simulation              ║"
    echo "  ║     单库 → 分库分表 全流程 + 故障注入                  ║"
    echo "  ╚═══════════════════════════════════════════════════════╝"
    echo -e "${NC}"
    echo "  Base URL:    ${BASE_URL}"
    echo "  Seed Count:  ${SEED_COUNT}"
    echo "  Metrics Dir: ${METRICS_DIR}"
    echo "  Started:     $(_ts)"
    echo ""
}

phase_prerequisites() {
    log_phase "Environment Check"
    log_info "Checking Docker containers..."
    local containers; containers=$(docker ps --format '{{.Names}}' 2>/dev/null)
    if echo "$containers" | grep -q "sharding-master"; then
        log_step "sharding-master running"
    else
        log_error "sharding-master NOT running — please start: docker-compose up -d"
        exit 1
    fi
    if echo "$containers" | grep -q "sharding-slave"; then
        log_step "sharding-slave running"
    else
        log_error "sharding-slave NOT running — please start: docker-compose up -d"
        exit 1
    fi

    log_info "Checking MySQL instances (independent shards, no replication)..."
    local ds0_ok; ds0_ok=$(docker exec sharding-master mysqladmin ping -uroot -proot123 --silent 2>/dev/null && echo "yes" || echo "no")
    local ds1_ok; ds1_ok=$(docker exec sharding-slave mysqladmin ping -uroot -proot123 --silent 2>/dev/null && echo "yes" || echo "no")
    local ds0_rw; ds0_rw=$(docker exec sharding-master mysql -uroot -proot123 -e "SELECT @@read_only;" 2>/dev/null | tail -1)
    local ds1_rw; ds1_rw=$(docker exec sharding-slave mysql -uroot -proot123 -e "SELECT @@read_only;" 2>/dev/null | tail -1)
    log_step "ds0 (3307): ping=${ds0_ok}, read_only=${ds0_rw}"
    log_step "ds1 (3308): ping=${ds1_ok}, read_only=${ds1_rw}"
    if [ "$ds1_rw" = "1" ]; then
        log_warn "ds1 is read_only! Run: docker exec sharding-slave mysql ... -e \"SET GLOBAL read_only=OFF\""
    fi

    log_info "Checking application..."
    local health; health=$(curl_ok GET "${BASE_URL}/actuator/health" 2>/dev/null || echo '{"status":"DOWN"}')
    if echo "$health" | grep -q '"status":"UP"'; then
        log_step "Application UP"
    else
        log_error "Application DOWN or unreachable at ${BASE_URL}"
        exit 1
    fi

    log_info "Checking migration mode..."
    local mig_status; mig_status=$(curl_ok GET "${BASE_URL}/api/v2/metrics/migration" 2>/dev/null || echo '{"enabled":false}')
    if echo "$mig_status" | grep -q '"enabled":true'; then
        log_step "migration.active = true"
        echo "$mig_status" | jq_pretty
    else
        log_warn "migration metrics returned: $mig_status"
        log_warn "If migration is not active, set migration.active=true in application.yml and restart"
    fi
    pause
}

phase_reset_tables() {
    log_phase "Reset Tables — Clean All Shard + Old Data"
    log_info "Truncating shard tables on master (ds0) and old single table..."
    docker exec sharding-master mysql -uroot -proot123 order_db -e "
        TRUNCATE TABLE orders_0;
        TRUNCATE TABLE orders_1;
        TRUNCATE TABLE order_items_0;
        TRUNCATE TABLE order_items_1;
        TRUNCATE TABLE orders;
        TRUNCATE TABLE migration_compensation;
        UPDATE migration_state SET phase='IDLE', advance_percent=0, migrated_records=0,
            snapshot_max_id=0, cursor_id=0, snapshot_caught_up=FALSE,
            double_write_enabled=FALSE, rolled_back_shards='{}';
    " 2>/dev/null
    log_step "Shard tables on ds0 truncated"

    # ds1 是独立分片, 也需要清表（binlog 复制已解除, 不会自动同步）
    docker exec sharding-slave mysql -uroot -proot123 order_db -e "
        TRUNCATE TABLE orders_0;
        TRUNCATE TABLE orders_1;
        TRUNCATE TABLE order_items_0;
        TRUNCATE TABLE order_items_1;
        SET GLOBAL read_only=OFF;
        SET GLOBAL super_read_only=OFF;
    " 2>/dev/null
    log_step "Shard tables on ds1 truncated, read_only=OFF"
    pause
}

phase_seed() {
    log_phase "Seed Test Data"
    log_info "POST /api/v2/migration-sim/seed?count=${SEED_COUNT}"
    local resp; resp=$(curl_ok POST "${BASE_URL}/api/v2/migration-sim/seed?count=${SEED_COUNT}")
    if [ $? -eq 0 ]; then
        echo "$resp" | jq_pretty
        log_step "Seeded ${SEED_COUNT} orders into old single DB"
    else
        log_error "Seed failed: $resp"
    fi
    capture_metrics "01-seed"
    pause
}

phase_start_migration() {
    log_phase "Start Migration (IDLE → SYNCING)"
    log_info "POST /api/v2/migration-sim/start"
    local resp; resp=$(curl_ok POST "${BASE_URL}/api/v2/migration-sim/start")
    echo "$resp" | jq_pretty
    if echo "$resp" | grep -q '"phase":"SYNCING"'; then
        log_step "Migration entered SYNCING phase"
        log_info "Snapshot maxId captured, background batch migration started (@Scheduled 1s)"
    else
        log_error "Failed to start migration: $resp"
    fi
    pause
}

phase_wait_sync() {
    log_phase "Wait for Snapshot Catch-up"
    log_info "Polling /api/v2/metrics/migration until snapshotCaughtUp=true..."
    local max_wait=120 elapsed=0
    while [ $elapsed -lt $max_wait ]; do
        local resp; resp=$(curl_ok GET "${BASE_URL}/api/v2/metrics/migration" 2>/dev/null)
        local caught_up; caught_up=$(echo "$resp" | grep -o '"snapshotCaughtUp":true' || true)
        local migrated; migrated=$(echo "$resp" | grep -o '"migratedRecords":[0-9]*' | grep -o '[0-9]*')
        local lag; lag=$(echo "$resp" | grep -o '"snapshotLag":[0-9]*' | grep -o '[0-9]*')

        if [ -n "$caught_up" ]; then
            log_step "Snapshot caught up! migratedRecords=${migrated:-?}, lag=${lag:-?}"
            break
        fi
        echo -ne "  ${BLUE}[${elapsed}s] migrated=${migrated:-?} lag=${lag:-?} ... waiting${NC}\r"
        sleep 2
        elapsed=$((elapsed + 2))
    done
    echo ""
    if [ $elapsed -ge $max_wait ]; then
        log_warn "Timeout waiting for snapshot catch-up (${max_wait}s). Continuing anyway..."
    fi
    capture_metrics "02-syncing"
    pause
}

phase_double_write() {
    log_phase "Enable Double-Write (SYNCING → DOUBLE_WRITE)"
    log_info "POST /api/v2/migration-sim/double-write"
    local resp; resp=$(curl_ok POST "${BASE_URL}/api/v2/migration-sim/double-write")
    echo "$resp" | jq_pretty
    if echo "$resp" | grep -q '"phase":"DOUBLE_WRITE"'; then
        log_step "Entered DOUBLE_WRITE — all new writes go to BOTH old DB and new shards"
    else
        log_error "Failed: $resp"
    fi
    capture_metrics "03-double-write"
    pause
}

phase_advance() {
    local pct="$1" label="$2"
    log_phase "Gray Advance: ${pct}% ${label}"
    log_info "POST /api/v2/migration-sim/advance?percent=${pct}"
    local resp; resp=$(curl_ok POST "${BASE_URL}/api/v2/migration-sim/advance?percent=${pct}")
    echo "$resp" | jq_pretty
    if echo "$resp" | grep -q '"advancePercent":'${pct}; then
        log_step "Advanced to ${pct}%: userId % 100 < ${pct} → reads route to new shard"
        log_info "Users 0-$((pct - 1)) read from new shard; Users ${pct}-99 read from old DB"
    else
        log_error "Advance may have failed: $resp"
    fi
    capture_metrics "${label}"
    pause
}

phase_inject_fault() {
    log_phase "INJECT FAULT: Stop ds1 (sharding-slave)"
    echo ""
    log_fault "Simulating physical machine failure on ds1 (port 3308)"
    log_fault "This is the MySQL slave — all shard data for odd userIds lives here"
    echo ""

    log_info "Stopping sharding-slave container..."
    docker_cmd stop sharding-slave

    sleep 2
    if docker ps --format '{{.Names}}' | grep -q "sharding-slave"; then
        log_error "sharding-slave is still running! Check docker."
    else
        log_fault "sharding-slave STOPPED. ds1 is now unreachable."
        log_fault "Impact: ShardingSphere cannot reach ds1 (connection refused)"
        log_fault "  - Reads for userId % 2 == 1 (odd) → FAIL"
        log_fault "  - Reads for userId % 2 == 0 (even) → OK (ds0 on master)"
        log_fault "  - Writes routing to ds1 → FAIL"
        log_fault "  - Old single DB reads/writes → OK (independent HikariCP pool)"
    fi
    pause
}

phase_test_fault_read() {
    log_phase "Test Reads During Fault Window"

    log_info "Test 1: GET /api/v2/orders?userId=1  (odd → ds1 → SHOULD FAIL)"
    echo -e "  ${MAGENTA}Expected: 500 error — ds1 unreachable${NC}"
    local resp1 rc1; resp1=$(curl_ok GET "${BASE_URL}/api/v2/orders?userId=1" 2>&1); rc1=$?
    if [ $rc1 -ne 0 ]; then
        log_fault "userId=1 read FAILED (expected): ShardingSphere cannot route to ds1"
        echo "  Response: ${resp1:0:300}"
    else
        log_warn "userId=1 read SUCCEEDED (unexpected — ds1 may still be reachable?)"
        echo "  Response: ${resp1:0:300}"
    fi

    echo ""
    log_info "Test 2: GET /api/v2/orders?userId=2  (even → ds0 → SHOULD SUCCEED)"
    echo -e "  ${GREEN}Expected: 200 OK — ds0 (master) is healthy${NC}"
    local resp2 rc2; resp2=$(curl_ok GET "${BASE_URL}/api/v2/orders?userId=2" 2>&1); rc2=$?
    if [ $rc2 -eq 0 ]; then
        log_step "userId=2 read SUCCEEDED: ds0 (master) unaffected"
        echo "  $(echo "$resp2" | jq_pretty | head -8)"
    else
        log_error "userId=2 read FAILED (unexpected): $resp2"
    fi

    echo ""
    log_info "Test 3: POST /api/v2/orders (write during fault — userId=1 routes to ds1)"
    echo -e "  ${MAGENTA}Expected: primary write to old DB succeeds; AOP double-write to ds1 fails${NC}"
    local resp3; resp3=$(curl_ok POST "${BASE_URL}/api/v2/orders" \
        '{"userId":1,"orderNo":"FAULT-TEST-'"$(date +%s)"'","totalAmount":99.99,"productNames":["test-item"]}' 2>&1)
    local rc3=$?
    if [ $rc3 -ne 0 ]; then
        log_fault "Write FAILED (double-write to ds1 failed): $resp3"
        log_fault "NOTE: primary write to old DB may have already committed → inconsistency risk"
    else
        log_step "Write succeeded: $resp3"
    fi

    capture_metrics "04-fault-active"
    pause
}

phase_rollback() {
    log_phase "Rollback Shard 1 (ds1)"
    log_info "POST /api/v2/migration-sim/rollback?shard=1"
    local resp; resp=$(curl_ok POST "${BASE_URL}/api/v2/migration-sim/rollback?shard=1")
    echo "$resp" | jq_pretty

    log_info "Rollback behavior:"
    log_info "  1. Phase → ROLLING_BACK (500ms drain)"
    log_info "  2. rolledBackShards[1] = true (tracking)"
    log_info "  3. advancePercent reduced by 10"
    log_info "  4. Phase → ADVANCING (resumes at lower percent)"
    log_info "  5. Reads for newly excluded users route back to old DB"
    log_warn "Note: rollback does NOT delete data from shards. It only redirects reads."
    capture_metrics "05-post-rollback"
    pause
}

phase_recover() {
    log_phase "Recover: Restart ds1 (sharding-slave)"
    log_info "Starting sharding-slave container..."
    docker_cmd start sharding-slave
    sleep 3

    log_info "Waiting for MySQL to be ready..."
    local ready=false
    for i in $(seq 1 30); do
        if docker exec sharding-slave mysqladmin ping -uroot -proot123 --silent 2>/dev/null; then
            log_step "MySQL on slave is ready"
            ready=true
            break
        fi
        echo -ne "  ${BLUE}[$i/30] waiting for MySQL...${NC}\r"
        sleep 2
    done
    echo ""

    if ! $ready; then
        log_error "Slave MySQL did not become ready within 60s"
        return 1
    fi

    log_info "Verifying ds1 is writable (no replication)..."
    local rw; rw=$(docker exec sharding-slave mysql -uroot -proot123 -e "SELECT @@read_only;" 2>/dev/null | tail -1)
    if [ "$rw" = "1" ]; then
        log_warn "ds1 still read_only, disabling..."
        docker exec sharding-slave mysql -uroot -proot123 -e "SET GLOBAL read_only=OFF; SET GLOBAL super_read_only=OFF;" 2>/dev/null
    fi
    # 确保 ShardingSphere 连接池有时间恢复
    sleep 3

    log_info "Resuming shard 1..."
    curl_ok POST "${BASE_URL}/api/v2/migration-sim/rollback?shard=1" > /dev/null 2>&1 || true

    log_step "ds1 recovered — independent MySQL ready for reads & writes"
    capture_metrics "06-post-recovery"
    pause
}

phase_verify() {
    log_phase "Data Consistency Verification"
    log_info "GET /api/v2/migration-sim/verify"
    local resp; resp=$(curl_ok GET "${BASE_URL}/api/v2/migration-sim/verify")
    echo "$resp" | jq_pretty
    if echo "$resp" | grep -q '"consistency":"CONSISTENT"'; then
        log_step "CONSISTENT — old DB and new shards have matching row counts"
    else
        log_error "DRIFT detected: $resp"
    fi
    pause
}

phase_complete() {
    log_phase "Complete Migration"
    log_info "POST /api/v2/migration-sim/complete"
    local resp; resp=$(curl_ok POST "${BASE_URL}/api/v2/migration-sim/complete")
    echo "$resp" | jq_pretty
    if echo "$resp" | grep -q '"phase":"COMPLETE"'; then
        log_step "Migration COMPLETE — all reads/writes now go through ShardingSphere only"
        log_step "Double-write disabled. Old single DB no longer used."
    else
        log_error "Complete may have failed: $resp"
    fi
    pause
}

phase_final_metrics() {
    log_phase "Final Metrics Snapshot"
    capture_metrics "07-final"
    log_step "All metrics saved to: ${METRICS_DIR}/"

    echo ""
    echo -e "${BOLD}Final Verification:${NC}"
    curl_ok GET "${BASE_URL}/api/v2/migration-sim/status" | jq_pretty
    echo ""
    curl_ok GET "${BASE_URL}/api/v2/migration-sim/verify" | jq_pretty
    pause
}

print_summary() {
    echo ""
    echo -e "${CYAN}${BOLD}"
    echo "  ╔═══════════════════════════════════════════════════════╗"
    echo "  ║           SIMULATION COMPLETE                         ║"
    echo "  ╚═══════════════════════════════════════════════════════╝"
    echo -e "${NC}"
    echo "  Completed: $(_ts)"
    echo "  Metrics:   ${METRICS_DIR}/"
    echo "  Files:     $(ls ${METRICS_DIR} | wc -l) JSON snapshots"
    echo ""
    echo -e "${BOLD}Phase Summary:${NC}"
    echo "  1. Seed data          → ${SEED_COUNT} orders in old DB"
    echo "  2. Start migration    → SYNCING (snapshot + batch)"
    echo "  3. Wait for sync      → snapshotCaughtUp"
    echo "  4. Enable double-write → DOUBLE_WRITE"
    echo "  5. Advance 10%        → partial new-shard reads"
    echo "  6. Advance 30%        → more reads to new shard"
    echo "  7. ⚡ FAULT INJECTED  → ds1 STOPPED"
    echo "  8. Fault read test    → odd userIds failed (expected)"
    echo "  9. Rollback shard 1   → advancePercent reduced"
    echo " 10. Recover ds1        → slave restarted + replication caught up"
    echo " 11. Advance 50%        → continued after recovery"
    echo " 12. Advance 100%       → full migration"
    echo " 13. Verify consistency → row count check"
    echo " 14. Complete           → old DB decommissioned"
    echo ""
    echo -e "${YELLOW}⚠ Known Issues to Review in Report:${NC}"
    echo "  - rolledBackShards map is informational only (not used in routing)"
    echo "  - Double-write to ds1 fails during fault (old DB commit already done)"
    echo "  - No transaction coordination between old/new DB writes"
    echo ""
}

cleanup_on_interrupt() {
    echo ""
    log_warn "Interrupted! Restoring sharding-slave..."
    docker start sharding-slave 2>/dev/null || true
    echo "sharding-slave started (if it was stopped)."
    exit 130
}

# ===== Main =====

main() {
    trap cleanup_on_interrupt INT TERM

    phase_banner
    phase_prerequisites
    phase_reset_tables

    TOTAL_PHASES=15

    # Phase 1-4: Normal migration startup
    phase_seed
    phase_start_migration
    phase_wait_sync
    phase_double_write

    # Phase 5-6: Gray advancement before fault
    phase_advance 10 "04-advance-10"
    phase_advance 30 "05-advance-30"

    # Phase 7-9: Fault injection + test + rollback
    phase_inject_fault
    phase_test_fault_read
    phase_rollback

    # Phase 10-11: Recovery + resume
    phase_recover
    phase_advance 50 "10-advance-50"

    # Phase 12-14: Finalize
    phase_advance 100 "11-advance-100"
    phase_verify
    phase_complete
    phase_final_metrics

    print_summary
}

main "$@"
