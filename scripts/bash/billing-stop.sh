#!/bin/bash
# =====================================================================
#  billing-stop.sh -- Dừng an toàn tiến trình Java của ứng dụng billing
# =====================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
NC='\033[0m'

TARGET_CLASS="billing.Main"
if [ -f "$PROJECT_ROOT/config.properties" ]; then
    CONFIG_CLASS=$(grep -E '^\s*app\.main\.class\s*=' "$PROJECT_ROOT/config.properties" | cut -d'=' -f2- | tr -d ' \r\n')
    if [ -n "$CONFIG_CLASS" ]; then
        TARGET_CLASS="$CONFIG_CLASS"
    fi
fi

echo -e "${CYAN}Dang kiem tra tien trinh ung dung Java ($TARGET_CLASS)...${NC}"

PIDS=$(pgrep -f "$TARGET_CLASS" 2>/dev/null || true)
if [ -n "$PIDS" ]; then
    for pid in $PIDS; do
        if [ "$pid" != "$$" ]; then
            kill -15 "$pid" 2>/dev/null || kill -9 "$pid" 2>/dev/null || true
            echo -e "${GREEN}[OK] Da dung tien trinh Java (PID $pid).${NC}"
        fi
    done
else
    echo -e "${YELLOW}Khong co tien trinh ung dung nao dang chay.${NC}"
fi
