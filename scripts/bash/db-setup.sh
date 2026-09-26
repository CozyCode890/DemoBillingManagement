#!/bin/bash
# =====================================================================
#  db-setup.sh -- Tạo database + nạp dữ liệu mẫu (dành cho Terminal)
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

if ! command -v mysql >/dev/null 2>&1; then
    echo -e "${RED}[LOI] Khong tim thay mysql client.${NC}"
    exit 1
fi

if ! pgrep -x mysqld >/dev/null 2>&1 && ! (command -v mysqladmin >/dev/null 2>&1 && mysqladmin ping --silent 2>/dev/null); then
    echo -e "${RED}[LOI] MySQL Server chua chay.${NC}"
    echo -e "${YELLOW}Vui long khoi dong MySQL bang: ./scripts/bash/mysql-start.sh${NC}"
    exit 1
fi

read -r -p "Ten dang nhap MySQL (Enter = root): " USER
USER=${USER:-root}

read -r -s -p "Mat khau MySQL (Enter = root): " PW
echo ""
PW=${PW:-root}

export MYSQL_PWD="$PW"

echo -e "\n${CYAN}--- Buoc 1/3: tao 15 bang (01_schema.sql) ---${NC}"
mysql -u "$USER" -h 127.0.0.1 -P 3306 --default-character-set=utf8mb4 < "$PROJECT_ROOT/sql/01_schema.sql"

echo -e "\n${CYAN}--- Buoc 2/3: nap du lieu mau (02_seed.sql) ---${NC}"
mysql -u "$USER" -h 127.0.0.1 -P 3306 --default-character-set=utf8mb4 < "$PROJECT_ROOT/sql/02_seed.sql"

echo -e "\n${CYAN}--- Buoc 3/3: tao cac ham va thu tuc SQL (04_routines.sql) ---${NC}"
mysql -u "$USER" -h 127.0.0.1 -P 3306 --default-character-set=utf8mb4 < "$PROJECT_ROOT/sql/04_routines.sql"

unset MYSQL_PWD

echo -e "\n${GREEN}[OK] Database 'retail_billing' da san sang.${NC}"
echo -e "${YELLOW}Kiem tra lai dong db.password trong config.properties truoc khi chay ./scripts/bash/run.sh${NC}"
