#!/usr/bin/env bash
# Agents-Flex Showcase 单 jar 部署的服务端管理脚本。
# 放在部署目录（与 jar、data/ 同级）使用：
#   ./restart.sh            # 重启（默认）：停止 -> 后台启动 -> 等待就绪
#   ./restart.sh start      # 仅启动
#   ./restart.sh stop       # 仅停止（先 SIGTERM 优雅关机，30s 后强杀）
#   ./restart.sh status     # 查看运行状态
#
# 可用环境变量覆盖默认值：
#   JAR_FILE      jar 路径（默认取部署目录里最新的 *.jar）
#   SERVER_PORT   服务端口（默认 18080，需与 application.yml/环境一致）
#   JAVA_BIN      java 可执行文件（版本必须 ≥21；不设则自动识别，见下）
#   JAVA_OPTS     JVM 参数（默认 "-Xms512m -Xmx2g"）
#
# java 运行时自动识别顺序：JAVA_BIN → 部署目录捆绑的 JRE/JDK（runtime/jdk*/、
# runtime/jre*/、顶层 jdk*/、jre*/，把 deploy/runtime 的 tar.gz 解压到部署目录即可）
# → JAVA_HOME → PATH（后两者要求版本 ≥21，否则报错并给出解决办法）。

# 用 sh 调用时切回 bash（dash 不支持 /dev/tcp、部分探测语法）
if [ -z "${BASH_VERSION:-}" ]; then
    exec bash "$0" "$@"
fi

set -u
cd "$(dirname "$0")"

SERVER_PORT="${SERVER_PORT:-18080}"
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx2g}"
LOG_DIR="logs"
PID_FILE="$LOG_DIR/backend.pid"
KEEP_LOGS=10

if [ -z "${JAR_FILE:-}" ]; then
    JAR_FILE="$(ls -t *.jar 2>/dev/null | head -1 || true)"
fi

log() { echo "[restart] $*"; }

# 解析 java 主版本号（兼容 "21.0.12" 与遗留 "1.8.0_xxx" 两种格式）
java_major() {
    "$1" -version 2>&1 | grep -oE 'version "[0-9.]+' | head -1 \
        | grep -oE '[0-9]+' | awk 'NR==1{v=$1; if (v!=1) {print v; exit}} NR==2{print $1}'
}

# 运行时解析顺序：
#   1) JAVA_BIN 环境变量（显式指定，版本必须 ≥21）
#   2) 部署目录捆绑的 JRE/JDK（runtime/jdk*/、runtime/jre*/、顶层 jdk*/、jre*/）
#   3) JAVA_HOME（版本 ≥21）
#   4) PATH 中的 java（版本 ≥21）
# 服务器免装 JDK：把 runtime/*.tar.gz 解压到部署目录即可被自动识别。
resolve_java() {
    local candidate major
    if [ -n "${JAVA_BIN:-}" ]; then
        major="$(java_major "$JAVA_BIN" 2>/dev/null || echo 0)"
        if [ "${major:-0}" -lt 21 ]; then
            log "错误: JAVA_BIN=$JAVA_BIN 版本为 ${major:-未知}，本应用需要 21+" >&2
            exit 1
        fi
        echo "$JAVA_BIN"
        return 0
    fi
    for candidate in runtime/jdk*/bin/java runtime/jre*/bin/java \
                     jdk*/bin/java jre*/bin/java; do
        [ -x "$candidate" ] || continue
        echo "$candidate"
        return 0
    done
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        candidate="$JAVA_HOME/bin/java"
        major="$(java_major "$candidate" 2>/dev/null || echo 0)"
        if [ "${major:-0}" -ge 21 ]; then
            echo "$candidate"
            return 0
        fi
    fi
    candidate="$(command -v java 2>/dev/null || true)"
    if [ -n "$candidate" ]; then
        major="$(java_major "$candidate" 2>/dev/null || echo 0)"
        if [ "${major:-0}" -ge 21 ]; then
            echo "$candidate"
            return 0
        fi
        log "错误: PATH 中的 java 版本为 ${major:-未知}，本应用需要 21+。" >&2
        log "解决: 把 deploy/runtime 下的 JRE 压缩包解压到部署目录（自动识别），" >&2
        log "      或设置 JAVA_BIN/JAVA_HOME 指向 JDK 21。" >&2
        exit 1
    fi
    log "错误: 未找到 java。请解压 runtime/*.tar.gz 到部署目录，或设置 JAVA_BIN/JAVA_HOME。" >&2
    exit 1
}

# 端口监听者的 PID：ss / lsof / netstat / fuser 多重兜底
port_pid() {
    if command -v ss >/dev/null 2>&1; then
        ss -ltnp 2>/dev/null | grep -E "[:.]$SERVER_PORT\b" | grep -i listen \
            | grep -oE 'pid=[0-9]+' | head -1 | cut -d= -f2
        return 0
    fi
    if command -v lsof >/dev/null 2>&1; then
        lsof -t -iTCP:"$SERVER_PORT" -sTCP:LISTEN 2>/dev/null | head -1
        return 0
    fi
    if command -v fuser >/dev/null 2>&1; then
        fuser "$SERVER_PORT"/tcp 2>/dev/null | tr -s ' \t' '\n' | grep -E '^[0-9]+$' | head -1
        return 0
    fi
    if command -v netstat >/dev/null 2>&1; then
        netstat -ltnp 2>/dev/null | grep -E "[:.]$SERVER_PORT\b" \
            | grep -oE '[0-9]+/' | head -1 | tr -d '/'
    fi
}

# 端口可达性：curl / wget / bash 内建 /dev/tcp 多重兜底
port_open() {
    if command -v curl >/dev/null 2>&1; then
        [ "$(curl -s -o /dev/null -w '%{http_code}' -m 2 "http://127.0.0.1:$SERVER_PORT/" 2>/dev/null)" != "000" ] && return 0
    fi
    if command -v wget >/dev/null 2>&1; then
        wget -q -T 2 -O /dev/null "http://127.0.0.1:$SERVER_PORT/" 2>/dev/null && return 0
        return 1
    fi
    (exec 3<>"/dev/tcp/127.0.0.1/$SERVER_PORT") 2>/dev/null && { exec 3>&- 2>/dev/null; return 0; }
    return 1
}

# 全部可能的进程 PID：pid 文件 + 端口监听者 + ps 按 jar 名匹配的历史遗留进程
# （ps -ef 在 Linux 与 Git Bash/MSYS 上都可用，覆盖无 pid 记录的手动启动实例）
candidate_pids() {
    if [ -f "$PID_FILE" ]; then
        cat "$PID_FILE" 2>/dev/null
    fi
    port_pid || true
    if [ -n "${JAR_FILE:-}" ]; then
        ps -ef 2>/dev/null | grep -F "$JAR_FILE" | grep -v grep \
            | awk '$2 ~ /^[0-9]+$/ {print $2}'
    fi
}

alive() { kill -0 "$1" 2>/dev/null; }

do_stop() {
    local pids pid
    pids="$(candidate_pids | sort -u | tr '\n' ' ')"
    if [ -z "$(echo "$pids" | tr -d ' ')" ]; then
        if port_open; then
            log "端口 $SERVER_PORT 有监听但无法定位 PID（缺 ss/lsof/fuser），"
            log "请手动处理: ss -ltnp | grep $SERVER_PORT，找到 PID 后 kill"
            exit 1
        fi
        log "服务未在运行（端口 $SERVER_PORT 无监听进程）"
        return 0
    fi
    log "停止进程: $pids（SIGTERM 优雅关机，等待释放 Neo4j/DuckDB 文件句柄）..."
    for pid in $pids; do
        kill -TERM "$pid" 2>/dev/null || true
    done
    for _ in $(seq 1 30); do
        local any_alive=0
        for pid in $pids; do
            alive "$pid" && any_alive=1
        done
        [ "$any_alive" = "0" ] && break
        sleep 1
    done
    for pid in $pids; do
        if alive "$pid"; then
            log "进程 $pid 30s 未退出，强制结束（kill -9）..."
            kill -KILL "$pid" 2>/dev/null || true
        fi
    done
    rm -f "$PID_FILE"
    # 等端口真正释放，避免新旧实例交接时的绑定失败
    for _ in $(seq 1 10); do
        port_open || break
        sleep 1
    done
    log "已停止"
}

do_start() {
    if [ -z "$JAR_FILE" ]; then
        log "错误: 部署目录中没有找到 jar 文件（可用 JAR_FILE=路径 指定）"
        exit 1
    fi
    if port_open; then
        log "错误: 端口 $SERVER_PORT 已被占用。"
        log "若是本应用的旧实例，执行 ./restart.sh stop 停止；"
        log "否则用 ss -ltnp | grep $SERVER_PORT 找到占用者处理，或换 SERVER_PORT。"
        exit 1
    fi
    mkdir -p "$LOG_DIR"
    local java_bin
    java_bin="$(resolve_java)" || exit 1
    local logfile="$LOG_DIR/backend-$(date +%Y%m%d-%H%M%S).log"
    log "启动 $JAR_FILE（java: $java_bin，端口 $SERVER_PORT，日志 $logfile）..."
    nohup "$java_bin" $JAVA_OPTS -jar "$JAR_FILE" \
        --server.port="$SERVER_PORT" > "$logfile" 2>&1 &
    local pid=$!
    echo "$pid" > "$PID_FILE"
    log "进程 $pid 启动中，等待服务就绪（最长 120s）..."
    for _ in $(seq 1 60); do
        if ! alive "$pid"; then
            log "错误: 进程已退出，最近日志："
            tail -25 "$logfile" >&2
            rm -f "$PID_FILE"
            exit 1
        fi
        if port_open; then
            log "服务已就绪: http://127.0.0.1:$SERVER_PORT/"
            prune_logs
            return 0
        fi
        sleep 2
    done
    log "错误: 120s 内未就绪，最近日志："
    tail -25 "$logfile" >&2
    exit 1
}

do_status() {
    local pid
    pid="$(running_pid)"
    if [ -n "$pid" ]; then
        log "运行中: PID $pid，端口 $SERVER_PORT，jar: ${JAR_FILE:-未知}"
    elif port_open; then
        log "端口 $SERVER_PORT 有监听但不是本脚本管理的进程"
    else
        log "未运行"
    fi
}

running_pid() {
    if [ -f "$PID_FILE" ]; then
        local pid
        pid="$(cat "$PID_FILE" 2>/dev/null || true)"
        if [ -n "$pid" ] && alive "$pid"; then
            echo "$pid"
            return 0
        fi
    fi
    port_pid
}

prune_logs() {
    ls -t "$LOG_DIR"/backend-*.log 2>/dev/null | tail -n +"$((KEEP_LOGS + 1))" | while read -r old; do
        rm -f "$old"
    done
}

case "${1:-restart}" in
    restart)
        do_stop
        do_start
        ;;
    start)
        do_start
        ;;
    stop)
        do_stop
        ;;
    status)
        do_status
        ;;
    *)
        echo "用法: $0 [restart|start|stop|status]"
        exit 1
        ;;
esac
