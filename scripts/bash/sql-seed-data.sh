#!/bin/bash
# =====================================================================
#  sql-seed-data.sh -- Nạp dữ liệu mẫu vào Database retail_billing
# =====================================================================

set -e

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

USER="root"
PW="root"
if [ -f "$PROJECT_ROOT/config.properties" ]; then
    CONFIG_USER=$(grep -E '^\s*db\.user\s*=' "$PROJECT_ROOT/config.properties" | cut -d'=' -f2- | tr -d ' \r\n')
    CONFIG_PW=$(grep -E '^\s*db\.password\s*=' "$PROJECT_ROOT/config.properties" | cut -d'=' -f2- | tr -d ' \r\n')
    [ -n "$CONFIG_USER" ] && USER="$CONFIG_USER"
    [ -n "$CONFIG_PW" ] && PW="$CONFIG_PW"
fi

if ! command -v mysql >/dev/null 2>&1; then
    echo -e "${RED}[LOI] Khong tim thay mysql client.${NC}"
    exit 1
fi

echo -e "${CYAN}--- Nap du lieu mau vao retail_billing ---${NC}"
export MYSQL_PWD="$PW"

echo -e "${YELLOW}Dang thuc thi 02_seed.sql...${NC}"
mysql -u "$USER" -h 127.0.0.1 -P 3306 --default-character-set=utf8mb4 < "$PROJECT_ROOT/sql/02_seed.sql"

unset MYSQL_PWD
echo -e "${GREEN}[OK] Da nap thanh cong du lieu mau vao Database.${NC}"
