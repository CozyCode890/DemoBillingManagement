#!/bin/bash
# =====================================================================
#  check-contracts.sh -- kiem bon hop dong cua tang giao dien bang code
#  Hỗ trợ macOS & Linux
# ---------------------------------------------------------------------
#  Kiem nhung thu "sai thi khong ai thay": doc so tien / so luong, cong
#  thuc giam gia, hop dong tien mat, va ma QR.
#  Dac ta: plan/14 muc 3 va docs/05 muc 7.
#
#  CHI cac kich ban KHONG can bam chuot. Cac kich ban tuong tac Swing
#  van phai kiem bang tay -- xem plan/14 muc 9.3.
#
#  Yeu cau: MySQL dang chay, database retail_billing co du lieu seed.
#  Co ghi vao database: lap 3 hoa don that roi XOA chung. Khong cham seed.
#
#  Exit 0 = tat ca dat. Exit 1 = co phep kiem sai (in ra dong FAIL).
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

# --- 0. Moi truong macOS / Homebrew / Java ----------------------------
if [ "$(uname -s)" = "Darwin" ]; then
    if [ -x "/opt/homebrew/bin/brew" ]; then
        eval "$(/opt/homebrew/bin/brew shellenv)"
    elif [ -x "/usr/local/bin/brew" ]; then
        eval "$(/usr/local/bin/brew shellenv)"
    fi

    if [ -z "$JAVA_HOME" ] && [ -x "/usr/libexec/java_home" ]; then
        JH=$(/usr/libexec/java_home 2>/dev/null || true)
        if [ -n "$JH" ]; then
            export JAVA_HOME="$JH"
            export PATH="$JAVA_HOME/bin:$PATH"
        fi
    fi
fi

if ! command -v javac >/dev/null 2>&1; then
    echo -e "${RED}Chua co JDK. Chay: ./scripts/bash/setup.sh${NC}"
    exit 1
fi
if ! ls lib/mysql-connector-j-*.jar >/dev/null 2>&1; then
    echo -e "${RED}Thieu driver MySQL trong lib/. Chay: ./scripts/bash/setup.sh${NC}"
    exit 1
fi
if ! pgrep -x mysqld >/dev/null 2>&1; then
    echo -e "${RED}MySQL chua chay. Mo mot cua so khac va chay:${NC}"
    echo -e "${YELLOW}    ./scripts/bash/mysql-start.sh${NC}"
    exit 1
fi

# --- 0b. Nap sp_check_seed (nhom Seed cua khoi 6 goi no) --------------
# 01_schema.sql mo dau bang DROP DATABASE, nen moi lan dung lai database la
# thu tuc nay bien mat. Nap lai o day cho ContractCheck chay duoc sau BAT KY
# duong dung database nao. File chi CREATE mot thu tuc, khong cham du lieu.
if command -v mysql >/dev/null 2>&1; then
    DB_USER='root'
    DB_PW='root'
    if [ -f config.properties ]; then
        V=$(sed -n 's/^[[:space:]]*db\.user[[:space:]]*=[[:space:]]*\(.*\)$/\1/p' config.properties | tail -n 1)
        [ -n "$V" ] && DB_USER="$V"
        V=$(sed -n 's/^[[:space:]]*db\.password[[:space:]]*=[[:space:]]*\(.*\)$/\1/p' config.properties | tail -n 1)
        [ -n "$V" ] && DB_PW="$V"
    fi
    MYSQL_PWD="$DB_PW" mysql -u "$DB_USER" -h 127.0.0.1 -P 3306 \
        --default-character-set=utf8mb4 < sql/05_invariants.sql >/dev/null 2>&1 || true
fi

# --- 1. Bien dich app vao out/ ----------------------------------------
# ContractCheck goi vao NewInvoicePanel.splitPayment (package-private) nen
# no phai duoc bien dich NGOAI src/ roi dat canh out/ tren classpath.
echo -e "${CYAN}Dang bien dich app...${NC}"
find src -name '*.java' -not -path '*/envsetup/*' > /tmp/billing-check-sources.txt
javac -encoding UTF-8 -nowarn -d out -cp "lib/*" @/tmp/billing-check-sources.txt

# --- 2. Bien dich chuong trinh kiem -----------------------------------
echo -e "${CYAN}Dang bien dich tools/check/ContractCheck.java...${NC}"
javac -encoding UTF-8 -nowarn -d out-check -cp "out:lib/*" tools/check/ContractCheck.java

# --- 3. Chay ----------------------------------------------------------
# -Djava.awt.headless=true la BAT BUOC: ContractCheck phan biet "tu choi"
# voi "nhan gia tri" bang cach bat HeadlessException tu JOptionPane.
# Bo tham so nay di thi cac phep kiem duong "tu choi" se treo o mot hop
# thoai modal khong ai bam (xem javadoc cua ContractCheck).
echo -e "${CYAN}Dang chay cac phep kiem...${NC}"
echo ""
set +e
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true \
     -cp "out:lib/*:out-check" billing.ui.ContractCheck
CODE=$?
set -e

echo ""
if [ "$CODE" -eq 0 ]; then
    echo -e "${GREEN}[OK] Tat ca phep kiem deu dat.${NC}"
else
    echo -e "${RED}[LOI] Co phep kiem sai -- xem cac dong FAIL o tren.${NC}"
fi
exit $CODE
