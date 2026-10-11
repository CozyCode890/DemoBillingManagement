#!/bin/bash
# =====================================================================
#  check-seed.sh -- chot bang so cho DU LIEU (14 bat bien)
#  Hỗ trợ macOS & Linux
# ---------------------------------------------------------------------
#  Khoi 5 chot bon hop dong cua GIAO DIEN bang code (check-contracts.sh).
#  Khoi 6 chot nam hop dong cua DU LIEU bang SQL: sql/05_invariants.sql.
#  Dac ta: plan/15 muc 3 va muc 4.7.
#
#  Chay duoc BAT CU LUC NAO, khong chi sau khi nap seed -- ke ca sau khi
#  nguoi dung da bam chuot sua/xoa du lieu bang giao dien. Do la diem khac
#  quan trong nhat so voi "kiem luc nap seed".
#
#  Yeu cau: MySQL dang chay va database retail_billing ton tai. Script TU
#  NAP sql/05_invariants.sql truoc khi goi -- xem khoi comment o buoc 1.
#
#  Exit 0 = ca 14 quy tac deu 0 vi pham. Exit 1 = co vi pham (hoac khong
#  nap duoc file). KHONG ghi mot dong DU LIEU nao vao database (chi tao lai
#  thu tuc sp_check_seed).
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

# --- Thong tin dang nhap: lay tu config.properties nhu sql-seed-data.sh ---
DB_USER='root'
DB_PW='root'
if [ -f config.properties ]; then
    V=$(sed -n 's/^[[:space:]]*db\.user[[:space:]]*=[[:space:]]*\(.*\)$/\1/p' config.properties | tail -n 1)
    [ -n "$V" ] && DB_USER="$V"
    V=$(sed -n 's/^[[:space:]]*db\.password[[:space:]]*=[[:space:]]*\(.*\)$/\1/p' config.properties | tail -n 1)
    [ -n "$V" ] && DB_PW="$V"
fi
export MYSQL_PWD="$DB_PW"

echo -e "${CYAN}--- Kiem 14 bat bien cua du lieu (sp_check_seed) ---${NC}"

# --- Buoc 1: tu nap 05_invariants.sql --------------------------------
# Vi sao nap moi lan chay thay vi doi nguoi dung nap tay:
#   01_schema.sql mo dau bang DROP DATABASE, nen MOI lan chay db-setup
#   hoac bam "Tao database" trong Setup App la sp_check_seed bien mat.
#   De script tu nap thi no chay duoc sau BAT KY duong dung database nao --
#   va khong phai them mot buoc vao hai duong nap do (duong cua Setup App
#   thi khoi 6 cam khong duoc sua -- plan/15 muc 4.1 diem 2).
# File nay chi CREATE mot thu tuc va co DROP ... IF EXISTS o dau, nen nap
# lai bao nhieu lan cung xong va khong cham mot dong du lieu nao.
set +e
mysql -u "$DB_USER" -h 127.0.0.1 -P 3306 \
      --default-character-set=utf8mb4 < "$PROJECT_ROOT/sql/05_invariants.sql" >/dev/null 2>&1
LOAD=$?
set -e
if [ "$LOAD" -ne 0 ]; then
    unset MYSQL_PWD
    echo -e "${RED}[LOI] Khong nap duoc sql/05_invariants.sql.${NC}"
    echo -e "${YELLOW}      Database retail_billing da duoc tao chua? Chay ./scripts/bash/db-setup.sh${NC}"
    exit 1
fi

# --- Buoc 2: goi thu tuc ---------------------------------------------
# Mot lan goi duy nhat: thu tuc tra ve BANG (in ra cho nguoi doc) va dat
# @v (tong so vi pham). Dong TONG_VI_PHAM=n la cho script doc con so --
# goi thu tuc hai lan chi de lay lai con so la chay R1/R2 hai lan.
set +e
OUT=$(printf "CALL sp_check_seed(@v);\nSELECT CONCAT('TONG_VI_PHAM=', @v) AS ket_qua;\n" \
      | mysql -u "$DB_USER" -h 127.0.0.1 -P 3306 \
              --default-character-set=utf8mb4 --table retail_billing 2>&1)
CODE=$?
set -e
unset MYSQL_PWD

echo "$OUT"

if [ "$CODE" -ne 0 ]; then
    echo ""
    echo -e "${RED}[LOI] Khong chay duoc sp_check_seed.${NC}"
    exit 1
fi

TOTAL=$(printf '%s\n' "$OUT" | sed -n 's/.*TONG_VI_PHAM=\([0-9]\{1,\}\).*/\1/p' | tail -n 1)

echo ""
if [ -z "$TOTAL" ]; then
    echo -e "${RED}[LOI] Khong doc duoc tong so vi pham tu ket qua.${NC}"
    exit 1
fi
if [ "$TOTAL" -eq 0 ]; then
    echo -e "${GREEN}[OK] Ca 14 bat bien deu dat (0 vi pham).${NC}"
    exit 0
fi
echo -e "${RED}[LOI] Co $TOTAL vi pham -- xem cac dong 'SAI' o bang tren.${NC}"
echo -e "${YELLOW}      Cot Hop_dong chi ra muc nao cua plan/15 dang bi vi pham.${NC}"
exit 1
