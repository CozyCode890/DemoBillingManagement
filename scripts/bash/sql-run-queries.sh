#!/bin/bash
# =====================================================================
#  sql-run-queries.sh -- Chạy bài tập và in kết quả 03_queries.sql
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

echo -e "${CYAN}================ CHAY BAI TAP 03_QUERIES.SQL ================${NC}"
export MYSQL_PWD="$PW"

mysql -u "$USER" -h 127.0.0.1 -P 3306 -t --default-character-set=utf8mb4 -e "source $PROJECT_ROOT/sql/03_queries.sql"

unset MYSQL_PWD
echo -e "${GREEN}================ HOAN THANH CHAY CAC QUERY ================${NC}"
