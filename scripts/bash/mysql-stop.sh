#!/bin/bash
# =====================================================================
#  mysql-stop.sh -- tắt MySQL Server một cách êm đẹp
# =====================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

if [ "$(uname -s)" = "Darwin" ]; then
    if [ -x "/opt/homebrew/bin/brew" ]; then
        eval "$(/opt/homebrew/bin/brew shellenv)"
    elif [ -x "/usr/local/bin/brew" ]; then
        eval "$(/usr/local/bin/brew shellenv)"
    fi
fi

if ! pgrep -x mysqld >/dev/null 2>&1 && ! (command -v mysqladmin >/dev/null 2>&1 && mysqladmin ping --silent 2>/dev/null); then
    echo -e "${YELLOW}MySQL khong chay.${NC}"
    exit 0
fi

echo -e "${CYAN}Dang dung MySQL Server...${NC}"

# Đọc password từ config.properties nếu có
PW="root"
if [ -f "$PROJECT_ROOT/config.properties" ]; then
    CONFIG_PW=$(grep -E '^\s*db\.password\s*=' "$PROJECT_ROOT/config.properties" | cut -d'=' -f2- | tr -d ' \r\n')
    if [ -n "$CONFIG_PW" ]; then
        PW="$CONFIG_PW"
    fi
fi

if command -v brew >/dev/null 2>&1 && brew services list 2>/dev/null | grep -q "mysql.*started"; then
    brew services stop mysql
elif command -v mysqladmin >/dev/null 2>&1; then
    mysqladmin -u root -p"$PW" -h 127.0.0.1 -P 3306 shutdown 2>&1 | grep -v 'Using a password' || true
elif command -v mysql.server >/dev/null 2>&1; then
    mysql.server stop
fi

sleep 2

if pgrep -x mysqld >/dev/null 2>&1; then
    echo -e "${YELLOW}[!!] MySQL van con tien trinh dang chay.${NC}"
else
    echo -e "${GREEN}[OK] Da tat MySQL Server an toan.${NC}"
fi
