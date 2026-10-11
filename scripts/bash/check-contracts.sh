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
