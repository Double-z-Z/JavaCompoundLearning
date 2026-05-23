#!/bin/bash
# DevOps Dashboard MCP Server 启动脚本 (Streamable HTTP 协议)
#
# 使用方式:
#   ./start-mcp.sh          # 启动 MCP Server（默认 8081 端口）
#   ./start-mcp.sh build    # 先编译再启动（强制重编译）
#   ./start-mcp.sh stop     # 停止运行中的 MCP Server
#   ./start-mcp.sh test     # 测试端点（Streamable HTTP JSON-RPC）
#   ./start-mcp.sh restart  # 重启 MCP Server
#
# 协议: Streamable HTTP (JSON-RPC 2.0 over POST)
# 端点: POST /mcp
# 文档: https://modelcontextprotocol.io/specification/2025-03-26/basic/transports

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

MCP_PORT=${MCP_PORT:-8081}
MCP_LOG="./logs/mcp-server.log"
PID_FILE=".mcp-server.pid"
MCP_ENDPOINT="http://localhost:${MCP_PORT}/mcp"

echo -e "${CYAN}╔══════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║   DevOps Dashboard - MCP Server          ║${NC}"
echo -e "${CYAN}║   Protocol: Streamable HTTP (JSON-RPC)     ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════╝${NC}"
echo ""

stop_server() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        echo -e "${YELLOW}Stopping MCP Server (PID: $PID)...${NC}"
        kill $PID 2>/dev/null || true
        
        for i in {1..10}; do
            if ! kill -0 $PID 2>/dev/null; then break; fi
            sleep 1
        done
        
        if kill -0 $PID 2>/dev/null; then
            echo -e "${YELLOW}Force killing...${NC}"
            kill -9 $PID 2>/dev/null || true
            sleep 2
        fi
        
        rm -f "$PID_FILE"
        echo -e "${GREEN}✓ MCP Server stopped${NC}"
    else
        if ss -tlnp | grep -q ":${MCP_PORT} "; then
            echo -e "${YELLOW}Found process on port ${MCP_PORT}, stopping...${NC}"
            fuser -k -9 ${MCP_PORT}/tcp 2>/dev/null || true
            sleep 2
            echo -e "${GREEN}✓ Port ${MCP_PORT} released${NC}"
        else
            echo -e "${YELLOW}No running MCP Server found${NC}"
        fi
    fi
}

force_recompile() {
    local src_dir="src/main/java/com/devops/dashboard/mcp/server"
    local target_dir="target/classes/com/devops/dashboard/mcp/server"
    
    echo -e "${YELLOW}Checking for code changes...${NC}"
    
    needs_recompile=false
    
    if [ ! -d "$target_dir" ] || [ -z "$(ls -A $target_dir/*.class 2>/dev/null)" ]; then
        needs_recompile=true
        echo -e "${YELLOW}  - No compiled classes found, need to compile${NC}"
    else
        newest_src=$(find "$src_dir" -name "*.java" -type f -printf '%T@ %p\n' 2>/dev/null | sort -n | tail -1 | cut -d' ' -f2-)
        newest_class=$(find "$target_dir" -name "*.class" -type f -printf '%T@ %p\n' 2>/dev/null | sort -n | tail -1 | cut -d' ' -f2-)
        
        if [ -n "$newest_src" ] && [ -n "$newest_class" ]; then
            src_time=$(stat -c %Y "$newest_src" 2>/dev/null || stat -f %m "$newest_src")
            class_time=$(stat -c %Y "$newest_class" 2>/dev/null || stat -f %m "$newest_class")
            
            if [ "$src_time" -gt "$class_time" ]; then
                needs_recompile=true
                echo -e "${YELLOW}  - Source files are newer than compiled classes${NC}"
                echo "    Latest source: $(basename $newest_src)"
            else
                echo -e "${GREEN}  - Compiled classes are up-to-date${NC}"
            fi
        else
            needs_recompile=true
        fi
    fi
    
    if [ "$needs_recompile" = true ]; then
        echo ""
        echo -e "${BLUE}Force recompiling project...${NC}"
        
        rm -rf "$target_dir"
        
        mvn compile \
            -DskipTests \
            -Dmaven.compile.force=true \
            -Dmaven.compiler.forceJavacCompilerUse=true \
            -q
        
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✓ Recompilation completed successfully${NC}"
            
            if [ -f "$target_dir/DevOpsMcpServer.class" ]; then
                if strings "$target_dir/DevOpsMcpServer.class" | grep -q "Streamable HTTP\|POST.*mcp"; then
                    echo -e "${GREEN}  ✓ Verified: Using Streamable HTTP protocol${NC}"
                elif strings "$target_dir/DevOpsMcpServer.class" | grep -q "jsonrpc"; then
                    echo -e "${GREEN}  ✓ Verified: Standard MCP JSON-RPC format${NC}"
                else
                    echo -e "${YELLOW}  ⚠ Could not verify protocol version${NC}"
                fi
            fi
        else
            echo -e "${RED}✗ Recompilation failed${NC}"
            exit 1
        fi
    fi
}

jsonrpc_call() {
    local method=$1
    local params=${2:-"{}"}
    local id=${3:-1}
    
    curl -s --max-time 5 -X POST "$MCP_ENDPOINT" \
        -H "Content-Type: application/json" \
        -d "{\"jsonrpc\":\"2.0\",\"id\":$id,\"method\":\"$method\",\"params\":$params}"
}

test_endpoints() {
    echo -e "${BLUE}Testing MCP Endpoints (Streamable HTTP)...${NC}"
    echo ""
    
    echo -e "${YELLOW}1. Initialize Handshake (${MCP_ENDPOINT})${NC}"
    RESPONSE=$(jsonrpc_call "initialize" "{}" 1)
    if echo "$RESPONSE" | grep -q '"result"'; then
        PROTO_VERSION=$(echo "$RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('result',{}).get('protocolVersion','?'))" 2>/dev/null)
        SERVER_NAME=$(echo "$RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('result',{}).get('serverInfo',{}).get('name','?'))" 2>/dev/null)
        echo -e "${GREEN}   ✓ Initialize OK${NC}"
        echo -e "     Protocol: ${PROTO_VERSION}"
        echo -e "     Server: ${SERVER_NAME}"
    else
        echo -e "${RED}   ✗ Initialize FAILED${NC}"
        echo -e "     Response: $RESPONSE"
    fi
    
    echo ""
    echo -e "${YELLOW}2. Tools List (${MCP_ENDPOINT})${NC}"
    RESPONSE=$(jsonrpc_call "tools/list" "{}" 2)
    if echo "$RESPONSE" | grep -q '"tools"'; then
        TOOL_COUNT=$(echo "$RESPONSE" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('result',{}).get('tools',[])))" 2>/dev/null || echo "?")
        echo -e "${GREEN}   ✓ Tools list OK (${TOOL_COUNT} tools registered)${NC}"
        echo -e "     Tools: env_create, env_deploy_service, env_destroy,"
        echo "            env_list, test_load, test_health_check,"
        echo "            test_exec_command, analyze_network_path"
    else
        echo -e "${RED}   ✗ Tools list FAILED${NC}"
    fi
    
    echo ""
    echo -e "${YELLOW}3. Resources List (${MCP_ENDPOINT})${NC}"
    RESPONSE=$(jsonrpc_call "resources/list" "{}" 3)
    if echo "$RESPONSE" | grep -q '"resources"'; then
        RES_COUNT=$(echo "$RESPONSE" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('result',{}).get('resources',[])))" 2>/dev/null || echo "?")
        echo -e "${GREEN}   ✓ Resources list OK (${RES_COUNT} resources)${NC}"
        echo -e "     Resources: hosts://topology, templates://list"
    else
        echo -e "${RED}   ✗ Resources list FAILED${NC}"
    fi
    
    echo ""
    echo -e "${YELLOW}4. Tool Call - env_list (${MCP_ENDPOINT})${NC}"
    RESPONSE=$(jsonrpc_call "tools/call" '{"name":"env_list"}' 4)
    if echo "$RESPONSE" | grep -q '"result"\|"status"'; then
        ENV_COUNT=$(echo "$RESPONSE" | python3 -c "
import sys, json
data = json.load(sys.stdin)
result = data.get('result', data)
if isinstance(result, dict):
    envs = result.get('environments', [])
    print(len(envs) if isinstance(envs, list) else '?')
else:
    print('?')
" 2>/dev/null || echo "?")
        echo -e "${GREEN}   ✓ env_list OK (${ENV_COUNT} environments)${NC}"
    else
        echo -e "${YELLOW}   ℹ env_list returned: $RESPONSE${NC}"
    fi
    
    echo ""
    echo -e "${GREEN}═══════════════════════════════════════════${NC}"
    echo -e "${GREEN}MCP Server is running at: ${MCP_ENDPOINT}${NC}"
    echo -e "${GREEN}Protocol: Streamable HTTP (JSON-RPC 2.0)${NC}"
    echo -e "${GREEN}Trae Config: {\"url\":\"http://localhost:${MCP_PORT}/mcp\"}${NC}"
    echo -e "${GREEN}═══════════════════════════════════════════${NC}"
    
    echo ""
    echo -e "${CYAN}Quick Test Commands:${NC}"
    echo "  curl -X POST ${MCP_ENDPOINT} \\"
    echo "    -H 'Content-Type: application/json' \\"
    echo "    -d '{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}'"
    echo ""
    echo "  curl -X POST ${MCP_ENDPOINT} \\"
    echo "    -H 'Content-Type: application/json' \\"
    echo "    -d '{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}'"
}

case "${1:-start}" in
    build)
        echo -e "${YELLOW}Full rebuild (clean + package)...${NC}"
        mvn clean package -DskipTests -q
        echo -e "${GREEN}✓ Full build completed${NC}"
        echo ""
        ;&
    start|restart)
        if [ "$1" = "restart" ]; then
            stop_server
            sleep 3
        fi
        
        if [ -f "$PID_FILE" ] && kill -0 $(cat "$PID_FILE") 2>/dev/null; then
            echo -e "${RED}MCP Server is already running (PID: $(cat $PID_FILE))${NC}"
            echo -e "Use '$0 stop' or '$0 restart' to restart it"
            exit 1
        fi
        
        if ss -tlnp | grep -q ":${MCP_PORT} "; then
            echo -e "${YELLOW}Warning: Port ${MCP_PORT} is already in use${NC}"
            echo -e "${YELLOW}Attempting to release port...${NC}"
            fuser -k -9 ${MCP_PORT}/tcp 2>/dev/null || true
            sleep 3
            
            if ss -tlnp | grep -q ":${MCP_PORT} "; then
                echo -e "${RED}Error: Cannot release port ${MCP_PORT}${NC}"
                echo -e "${YELLOW}Try using a different port: MCP_PORT=8082 $0 start${NC}"
                exit 1
            fi
        fi
        
        mkdir -p logs
        
        force_recompile
        
        echo ""
        echo -e "${YELLOW}Starting MCP Server on port ${MCP_PORT}...${NC}"
        echo -e "${BLUE}Configuration:${NC}"
        echo "  - Profile: mcp"
        echo "  - Port: ${MCP_PORT}"
        echo "  - Log: ${MCP_LOG}"
        echo "  - Endpoint: POST /mcp"
        echo "  - Protocol: Streamable HTTP (JSON-RPC 2.0)"
        echo "  - Transport: Stateless (no session binding)"
        echo ""
        
        nohup mvn spring-boot:run \
            -Dspring-boot.run.profiles=mcp \
            -Dspring-boot.run.jvmArguments="-Xms256m -Xmx512m" \
            > "$MCP_LOG" 2>&1 &
        
        MAIN_PID=$!
        echo $MAIN_PID > "$PID_FILE"
        
        echo -e "${YELLOW}Waiting for server to start...${NC}"
        for i in {1..30}; do
            INIT_RESPONSE=$(curl -s --max-time 2 -X POST "$MCP_ENDPOINT" \
                -H "Content-Type: application/json" \
                -d '{"jsonrpc":"2.0","id":0,"method":"initialize","params":{}}' 2>/dev/null || echo "")
            
            if echo "$INIT_RESPONSE" | grep -q '"result"'; then
                echo -e "${GREEN}✓ MCP Server started successfully! (PID: ${MAIN_PID})${NC}"
                echo ""
                test_endpoints
                exit 0
            fi
            sleep 1
            echo -ne "\r  Attempt $i/30..."
        done
        
        echo ""
        echo -e "${RED}✗ Failed to start MCP Server${NC}"
        echo -e "${RED}Check log file: ${MCP_LOG}${NC}"
        echo ""
        echo -e "${YELLOW}Last 20 lines of log:${NC}"
        tail -20 "$MCP_LOG" 2>/dev/null || echo "(no log file)"
        exit 1
        ;;
    stop)
        stop_server
        ;;
    test)
        test_endpoints
        ;;
    status)
        if [ -f "$PID_FILE" ] && kill -0 $(cat "$PID_FILE") 2>/dev/null; then
            echo -e "${GREEN}MCP Server is running (PID: $(cat $PID_FILE))${NC}"
            echo -e "URL: ${MCP_ENDPOINT}"
            echo -e "Protocol: Streamable HTTP (JSON-RPC 2.0)"
            
            echo ""
            echo -e "${BLUE}Health Check:${NC}"
            HEALTH=$(curl -s --max-time 2 -X POST "$MCP_ENDPOINT" \
                -H "Content-Type: application/json" \
                -d '{"jsonrpc":"2.0","id":0,"method":"initialize"}' 2>/dev/null || echo "")
            
            if echo "$HEALTH" | grep -q '"protocolVersion"'; then
                PROTO=$(echo "$HEALTH" | python3 -c "import sys,json; print(json.load(sys.stdin).get('result',{}).get('protocolVersion','?'))" 2>/dev/null)
                echo -e "${GREEN}  ✓ Protocol: MCP ${PROTO}${NC}"
                echo -e "${GREEN}  ✓ Status: Healthy${NC}"
            else
                echo -e "${RED}  ✗ Health check failed${NC}"
            fi
        elif ss -tlnp | grep -q ":${MCP_PORT} "; then
            echo -e "${YELLOW}MCP Server is running on port ${MCP_PORT} (unknown PID)${NC}"
        else
            echo -e "${RED}MCP Server is not running${NC}"
            echo ""
            echo -e "${YELLOW}Start with: $0 start${NC}"
        fi
        ;;
    logs)
        if [ -f "$MCP_LOG" ]; then
            tail -f "$MCP_LOG"
        else
            echo -e "${RED}Log file not found: ${MCP_LOG}${NC}"
        fi
        ;;
    *)
        echo "Usage: $0 {start|build|stop|restart|test|status|logs}"
        echo ""
        echo "Commands:"
        echo "  start   - Start MCP Server with auto-recompile (default)"
        echo "  build   - Full clean build and start (recommended after code changes)"
        echo "  stop    - Stop MCP Server"
        echo "  restart - Restart MCP Server (stop + start with recompile)"
        echo "  test    - Test all MCP endpoints (Streamable HTTP JSON-RPC)"
        echo "  status  - Check MCP Server status and health"
        echo "  logs    - Tail MCP Server logs"
        echo ""
        echo "Environment:"
        echo "  MCP_PORT=8081 $0 start   # Custom port"
        echo ""
        echo "Protocol: Streamable HTTP (not SSE)"
        echo "Endpoint: POST /mcp"
        echo "Trae config:"
        echo '  {"mcpServers":{"devops-dashboard":{"url":"http://localhost:8081/mcp"}}}'
        exit 1
        ;;
esac
