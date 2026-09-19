#!/usr/bin/env bash
# One-click launcher for Agents-Flex Showcase on Linux/macOS.
# Starts backend (18080) and frontend (15173) in the background and tails both logs.
# Ports are overridable: BACKEND_PORT / FRONTEND_PORT
set -e
cd "$(dirname "$0")"

BACKEND_PORT="${BACKEND_PORT:-18080}"
FRONTEND_PORT="${FRONTEND_PORT:-15173}"

port_in_use() {
  (netstat -an 2>/dev/null || ss -ltn 2>/dev/null) | grep -E "[:.]$1\b" | grep -i listen >/dev/null 2>&1
}

command -v java >/dev/null 2>&1 || { echo "[ERROR] java not found (JDK 8+ required)"; exit 1; }
command -v mvn  >/dev/null 2>&1 || { echo "[ERROR] mvn not found (Maven 3.x required)"; exit 1; }
command -v node >/dev/null 2>&1 || { echo "[ERROR] node not found (Node.js 18+ required)"; exit 1; }
command -v pnpm >/dev/null 2>&1 || { echo "[ERROR] pnpm not found"; exit 1; }

port_in_use "$BACKEND_PORT"  && { echo "[ERROR] Port $BACKEND_PORT is already in use"; exit 1; }
port_in_use "$FRONTEND_PORT" && { echo "[ERROR] Port $FRONTEND_PORT is already in use"; exit 1; }

if [ ! -d frontend/node_modules ]; then
  echo "[frontend] node_modules missing, running pnpm install ..."
  (cd frontend && pnpm install)
fi

mkdir -p logs
echo "[backend]  Starting Spring Boot on http://localhost:$BACKEND_PORT ..."
(cd "$(pwd)" && SERVER_PORT="$BACKEND_PORT" mvn -B spring-boot:run > logs/backend.log 2>&1) &
BACKEND_PID=$!

echo "[frontend] Starting Vite on http://localhost:$FRONTEND_PORT ..."
(cd frontend && FRONTEND_PORT="$FRONTEND_PORT" BACKEND_PORT="$BACKEND_PORT" pnpm dev > ../logs/frontend.log 2>&1) &
FRONTEND_PID=$!

echo ""
echo "Both services are starting. Logs: logs/backend.log, logs/frontend.log"
echo "  Backend  -> http://localhost:$BACKEND_PORT"
echo "  Frontend -> http://localhost:$FRONTEND_PORT"
echo "Stop with: kill $BACKEND_PID $FRONTEND_PID"
