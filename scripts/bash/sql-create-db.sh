#!/bin/bash
# =====================================================================
#  sql-create-db.sh -- Tạo Database retail_billing và các bảng / routines
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
    echo -e "${RED}[LOI] Khong tim thay mysql client. Vui long cai dat MySQL qua brew install mysql.${NC}"
    exit 1
fi

echo -e "${CYAN}--- Tao Database retail_billing va cac bang ---${NC}"
export MYSQL_PWD="$PW"

echo -e "${YELLOW}Dang thuc thi 01_schema.sql...${NC}"
mysql -u "$USER" -h 127.0.0.1 -P 3306 --default-character-set=utf8mb4 < "$PROJECT_ROOT/sql/01_schema.sql"

echo -e "${YELLOW}Dang thuc thi 04_routines.sql...${NC}"
mysql -u "$USER" -h 127.0.0.1 -P 3306 --default-character-set=utf8mb4 < "$PROJECT_ROOT/sql/04_routines.sql"

unset MYSQL_PWD
echo -e "${GREEN}[OK] Da tao thanh cong Database retail_billing va cac Store Procedures / Views.${NC}"
