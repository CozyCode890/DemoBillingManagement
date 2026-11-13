#!/bin/bash
# =====================================================================
#  sql-wipe-data.sh -- Xoa DU LIEU cua retail_billing, giu nguyen 16
#  BANG va moi ham / thu tuc. Dac ta: plan/16.
#  Hỗ trợ macOS & Linux
# ---------------------------------------------------------------------
#  Hai muc (plan/16 muc 3.1):
#    transactions  xoa 7 bang giao dich, giu 9 bang danh muc + tai khoan
#                  -> CALL sp_wipe_transactions()
#    all           xoa ca 16 bang, ke ca Account
#                  -> CALL sp_wipe_all_data()
#  Thu tu xoa KHONG nam o day ma o 04_routines.sql phan 6 (mot ban chep).
#
#  Cach dung:
#    ./scripts/bash/sql-wipe-data.sh transactions
#    ./scripts/bash/sql-wipe-data.sh all
#  Setup App truyen pham vi qua bien moi truong WIPE_SCOPE (giong
#  BILLING_LOGIN cua run.sh). Tham so dong lenh thang bien moi truong.
#
#  KHONG co pham vi mac dinh va KHONG hoi xac nhan: thieu pham vi la tu
#  choi. Mot script xoa du lieu ma tu doan "chac la xoa het" la mot cai
#  bay. Xac nhan la viec cua hop thoai trong Setup App; chay tay thi viec
#  phai go pham vi da la mot lan xac nhan.
#
#  Exit 0 = da xoa xong. Exit 1 = loi (MySQL tat, chua co database, mot
#  lenh DELETE that bai). Exit 2 = thieu / sai pham vi, KHONG xoa gi.
# =====================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
GRAY='\033[0;90m'
NC='\033[0m'

# --- Buoc 0: pham vi -- kiem TRUOC khi cham toi MySQL ------------------
RAW_SCOPE="${1:-${WIPE_SCOPE:-}}"
SCOPE=$(printf '%s' "$RAW_SCOPE" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')
case "$SCOPE" in
    transactions) PROC="sp_wipe_transactions" ;;
    all)          PROC="sp_wipe_all_data" ;;
    *)
        echo -e "${RED}[LOI] Thieu hoac sai pham vi xoa: '$RAW_SCOPE'. Khong xoa gi.${NC}"
        echo -e "${YELLOW}      Cach dung:${NC}"
        echo -e "${YELLOW}        ./scripts/bash/sql-wipe-data.sh transactions   # chi du lieu giao dich${NC}"
        echo -e "${YELLOW}        ./scripts/bash/sql-wipe-data.sh all            # toan bo 16 bang${NC}"
        exit 2
        ;;
esac

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

# --- Thong tin dang nhap: lay tu config.properties nhu check-seed.sh ---
DB_USER='root'
DB_PW='root'
if [ -f config.properties ]; then
    V=$(sed -n 's/^[[:space:]]*db\.user[[:space:]]*=[[:space:]]*\(.*\)$/\1/p' config.properties | tail -n 1)
    [ -n "$V" ] && DB_USER="$V"
    V=$(sed -n 's/^[[:space:]]*db\.password[[:space:]]*=[[:space:]]*\(.*\)$/\1/p' config.properties | tail -n 1)
    [ -n "$V" ] && DB_PW="$V"
fi

# --- Bang dem so dong cua 16 bang -------------------------------------
# Dung COUNT(*) chu khong dung information_schema.TABLES.TABLE_ROWS: voi
# InnoDB cot do chi la UOC LUONG, va MySQL 8 con cache no 24 gio
# (information_schema_stats_expiry), nen ngay sau khi xoa no van hien so
# cu. Viet MOT lan, dung cho ca cot Truoc lan cot Sau.
# Day la ban chep chi de HIEN THI: lech thi bao cao thieu dong, du lieu
# khong sai. Them bang moi vao 01_schema.sql thi them ca o day (va o
# sql-wipe-data.ps1) -- xem 04_routines.sql phan 6.
# Heredoc 'EOF' co nhay: bash khong dung vao dau ` cua `Return`.
DEM=$(cat <<'EOF'
          SELECT  1 AS Thu_tu, 'GIAO DICH' AS Nhom, 'Invoice' AS Bang, COUNT(*) AS So_dong FROM Invoice
UNION ALL SELECT  2, 'GIAO DICH', 'Invoice_Line',    COUNT(*) FROM Invoice_Line
UNION ALL SELECT  3, 'GIAO DICH', 'Return',          COUNT(*) FROM `Return`
UNION ALL SELECT  4, 'GIAO DICH', 'Payment',         COUNT(*) FROM Payment
UNION ALL SELECT  5, 'GIAO DICH', 'Cash_Payment',    COUNT(*) FROM Cash_Payment
UNION ALL SELECT  6, 'GIAO DICH', 'Card_Payment',    COUNT(*) FROM Card_Payment
UNION ALL SELECT  7, 'GIAO DICH', 'EWallet_Payment', COUNT(*) FROM EWallet_Payment
UNION ALL SELECT  8, 'DANH MUC',  'Shop',            COUNT(*) FROM Shop
UNION ALL SELECT  9, 'DANH MUC',  'Counter',         COUNT(*) FROM Counter
UNION ALL SELECT 10, 'DANH MUC',  'Cashier',         COUNT(*) FROM Cashier
UNION ALL SELECT 11, 'DANH MUC',  'Customer',        COUNT(*) FROM Customer
UNION ALL SELECT 12, 'DANH MUC',  'Product',         COUNT(*) FROM Product
UNION ALL SELECT 13, 'DANH MUC',  'Price_History',   COUNT(*) FROM Price_History
UNION ALL SELECT 14, 'DANH MUC',  'Promotion',       COUNT(*) FROM Promotion
UNION ALL SELECT 15, 'DANH MUC',  'Applies_To',      COUNT(*) FROM Applies_To
UNION ALL SELECT 16, 'DANH MUC',  'Account',         COUNT(*) FROM Account
EOF
)

if [ "$SCOPE" = "all" ]; then
    echo -e "${CYAN}--- Xoa TOAN BO du lieu cua retail_billing (16 bang) ---${NC}"
else
    echo -e "${CYAN}--- Xoa du lieu GIAO DICH cua retail_billing (7 bang) ---${NC}"
fi
export MYSQL_PWD="$DB_PW"

# --- Buoc 1: database co ton tai? -------------------------------------
# Hoi information_schema chu khong USE retail_billing: chua co database
# thi cau nay van chay, va ta bao duoc mot loi de hieu.
set +e
HAS_DB=$(mysql -u "$DB_USER" -h 127.0.0.1 -P 3306 -N -B \
    -e "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'retail_billing';")
CODE=$?
set -e
if [ "$CODE" -ne 0 ]; then
    unset MYSQL_PWD
    echo -e "${RED}[LOI] Khong ket noi duoc MySQL bang tai khoan '$DB_USER' (xem config.properties).${NC}"
    exit 1
fi
if [ "$HAS_DB" = "0" ]; then
    unset MYSQL_PWD
    echo -e "${RED}[LOI] Chua co database retail_billing -- khong co gi de xoa.${NC}"
    echo -e "${YELLOW}      Bam 'Tao database': database moi tao von da TRONG (01 + 04, khong seed).${NC}"
    exit 1
fi

# --- Buoc 2: hai thu tuc da co chua? ----------------------------------
# Database tao TRUOC plan/16 thi chua co. Tu nap lai 04_routines.sql thay
# vi bat bam "Tao database" (thuc chat la bat xoa muc 'all' kem theo
# routine). Cung ly do check-seed.sh tu nap 05_invariants.sql: file do chi
# DROP / CREATE ham va thu tuc, khong cham mot dong du lieu nao.
set +e
FOUND=$(mysql -u "$DB_USER" -h 127.0.0.1 -P 3306 -N -B \
    -e "SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = 'retail_billing' AND ROUTINE_TYPE = 'PROCEDURE' AND ROUTINE_NAME IN ('sp_wipe_transactions', 'sp_wipe_all_data');")
set -e
if [ "$FOUND" != "2" ]; then
    echo -e "${YELLOW}[!!] Database chua co sp_wipe_* (duoc tao truoc plan/16). Nap lai sql/04_routines.sql --${NC}"
    echo -e "${YELLOW}     file do chi tao lai ham / thu tuc, khong cham mot dong du lieu nao.${NC}"
    set +e
    mysql -u "$DB_USER" -h 127.0.0.1 -P 3306 \
          --default-character-set=utf8mb4 < "$PROJECT_ROOT/sql/04_routines.sql" >/dev/null
    LOAD=$?
    set -e
    if [ "$LOAD" -ne 0 ]; then
        unset MYSQL_PWD
        echo -e "${RED}[LOI] Khong nap duoc sql/04_routines.sql.${NC}"
        exit 1
    fi
fi

# --- Buoc 3: dem -> xoa -> dem lai, trong CUNG mot phien --------------
# Bang tam tmp_truoc chi song trong phien nay, nen ca lo phai di qua MOT
# lan goi mysql.
set +e
printf '%s\n' \
    "CREATE TEMPORARY TABLE tmp_truoc AS" \
    "$DEM;" \
    "CALL $PROC();" \
    "SELECT t.Nhom, t.Bang, t.So_dong AS Truoc, s.So_dong AS Sau" \
    "FROM   tmp_truoc t" \
    "JOIN   ($DEM) s ON s.Bang = t.Bang" \
    "ORDER  BY t.Thu_tu;" \
    | mysql -u "$DB_USER" -h 127.0.0.1 -P 3306 --default-character-set=utf8mb4 -t retail_billing
CODE=$?
set -e
unset MYSQL_PWD

if [ "$CODE" -ne 0 ]; then
    echo -e "${RED}[LOI] Loi khi chay $PROC.${NC}"
    exit 1
fi

if [ "$SCOPE" = "all" ]; then
    echo -e "${GREEN}[OK] Da xoa toan bo du lieu. 16 bang va moi ham / thu tuc giu nguyen.${NC}"
    echo -e "${YELLOW}     Bang Account dang trong: app billing chua dang nhap duoc cho toi khi seed them tai khoan.${NC}"
else
    echo -e "${GREEN}[OK] Da xoa du lieu giao dich. 9 bang danh muc va tai khoan giu nguyen.${NC}"
fi
echo -e "${GRAY}Buoc tiep: chay seed cua ban (hoac 'Tao du lieu mau'), roi kiem bang ./scripts/bash/check-seed.sh${NC}"
echo -e "${GRAY}     R12 (91 ngay deu co hoa don) la cam ket rieng cua seed chinh thuc -- seed tu viet khong phu du thi R12 khac 0.${NC}"

exit 0
