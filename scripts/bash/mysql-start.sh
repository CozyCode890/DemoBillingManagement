#!/bin/bash
# =====================================================================
#  mysql-start.sh -- khởi động MySQL Server chạy nền trên cổng 3306
#  Hỗ trợ macOS (Homebrew, mysql.server) & Linux
# =====================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Đảm bảo môi trường Homebrew nếu trên macOS
if [ "$(uname -s)" = "Darwin" ]; then
    if [ -x "/opt/homebrew/bin/brew" ]; then
        eval "$(/opt/homebrew/bin/brew shellenv)"
    elif [ -x "/usr/local/bin/brew" ]; then
        eval "$(/usr/local/bin/brew shellenv)"
    fi
fi

# 1. Kiểm tra nếu MySQL đã chạy sẵn
if pgrep -x mysqld >/dev/null 2>&1 || (command -v mysqladmin >/dev/null 2>&1 && mysqladmin ping --silent 2>/dev/null); then
    echo -e "${GREEN}[OK] MySQL Server dang chay san.${NC}"
    exit 0
fi

echo -e "${CYAN}Dang khoi dong MySQL Server tren cong 3306...${NC}"

# 2. Khởi động theo thứ tự ưu tiên: brew services -> mysql.server -> systemctl -> mysqld_safe
STARTED=0

if command -v brew >/dev/null 2>&1 && brew services list 2>/dev/null | grep -q "mysql"; then
    echo -e "${CYAN}Goi 'brew services start mysql'...${NC}"
    brew services start mysql
    STARTED=1
elif command -v mysql.server >/dev/null 2>&1; then
    echo -e "${CYAN}Goi 'mysql.server start'...${NC}"
    mysql.server start
    STARTED=1
elif command -v systemctl >/dev/null 2>&1; then
    echo -e "${CYAN}Goi 'sudo systemctl start mysql'...${NC}"
    sudo systemctl start mysql || true
    STARTED=1
elif command -v mysqld_safe >/dev/null 2>&1; then
    echo -e "${CYAN}Goi 'mysqld_safe &'...${NC}"
    nohup mysqld_safe --port=3306 >/dev/null 2>&1 &
    STARTED=1
fi

if [ $STARTED -eq 0 ]; then
    echo -e "${RED}[LOI] Khong tim thay trinh quan ly dich vu MySQL (brew / mysql.server / systemctl).${NC}"
    echo -e "${YELLOW}Vui long kiem tra lai cai dat MySQL tren may cua ban.${NC}"
    exit 1
fi

# 3. Chờ cổng 3306 sẵn sàng
READY=0
for i in {1..10}; do
    sleep 1
    if pgrep -x mysqld >/dev/null 2>&1 || (command -v mysqladmin >/dev/null 2>&1 && mysqladmin ping --silent 2>/dev/null); then
        READY=1
        break
    fi
done

if [ $READY -eq 1 ]; then
    echo -e "${GREEN}[OK] MySQL Server da khoi dong thanh cong tren cong 3306.${NC}"
else
    echo -e "${YELLOW}[!!] MySQL khoi dong chua phan hoi sau 10 giay. Hay kiem tra lai log dich vu.${NC}"
fi
