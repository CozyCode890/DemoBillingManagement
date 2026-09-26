#!/bin/bash
# =====================================================================
#  setup.sh -- tải thư viện driver MySQL cho Java & kiểm tra môi trường
#  Hỗ trợ macOS (Homebrew) & Linux
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

echo -e "${CYAN}================================================================${NC}"
echo -e "${CYAN}       THIẾT LẬP MÔI TRƯỜNG & RUNTIME (MACOS / LINUX)          ${NC}"
echo -e "${CYAN}================================================================${NC}"

# --- 1. Tải JDBC Driver ----------------------------------------------
VERSION="8.4.0"
JAR_NAME="mysql-connector-j-${VERSION}.jar"
URL="https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/${VERSION}/${JAR_NAME}"
DEST="${PROJECT_ROOT}/lib/${JAR_NAME}"

mkdir -p "${PROJECT_ROOT}/lib"

if [ -f "$DEST" ]; then
    echo -e "${GREEN}[OK] Da co san: ${DEST}${NC}"
else
    echo -e "${CYAN}Dang tai ${JAR_NAME} tu Maven Central...${NC}"
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL "$URL" -o "$DEST"
    elif command -v wget >/dev/null 2>&1; then
        wget -q "$URL" -O "$DEST"
    else
        echo -e "${RED}[LOI] Khong tim thay curl hoac wget de tai file!${NC}"
        exit 1
    fi
    echo -e "${GREEN}[OK] Da tai xong driver vao: ${DEST}${NC}"
fi

# --- 2. Kiem tra Homebrew tren macOS ----------------------------------
OS="$(uname -s)"
if [ "$OS" = "Darwin" ]; then
    echo -e "\n${CYAN}--- Kiem tra Homebrew (macOS) ---${NC}"
    if ! command -v brew >/dev/null 2>&1; then
        if [ -x "/opt/homebrew/bin/brew" ]; then
            eval "$(/opt/homebrew/bin/brew shellenv)"
        elif [ -x "/usr/local/bin/brew" ]; then
            eval "$(/usr/local/bin/brew shellenv)"
        fi
    fi

    if command -v brew >/dev/null 2>&1; then
        echo -e "${GREEN}[OK] Tim thay Homebrew: $(command -v brew) ($(brew --version | head -n 1))${NC}"
    else
        echo -e "${YELLOW}[!!] Chua cai dat Homebrew.${NC}"
        echo -e "${YELLOW}     De cai dat, hay mo Terminal va chay lenh:${NC}"
        echo -e "     /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
    fi
fi

# --- 3. Kiem tra JDK --------------------------------------------------
echo -e "\n${CYAN}--- Kiem tra JDK 21 ---${NC}"
if command -v javac >/dev/null 2>&1; then
    echo -e "${GREEN}[OK] Tim thay JDK: $(javac -version 2>&1)${NC}"
else
    # Thu kiem tra java_home tren mac
    if [ "$OS" = "Darwin" ] && [ -x "/usr/libexec/java_home" ]; then
        JH=$(/usr/libexec/java_home 2>/dev/null || true)
        if [ -n "$JH" ] && [ -x "$JH/bin/javac" ]; then
            export PATH="$JH/bin:$PATH"
            echo -e "${GREEN}[OK] Tim thay JDK tai $JH: $(javac -version 2>&1)${NC}"
        fi
    fi
fi

if ! command -v javac >/dev/null 2>&1; then
    echo -e "${YELLOW}[!!] Chua tim thay JDK trong PATH.${NC}"
    if [ "$OS" = "Darwin" ] && command -v brew >/dev/null 2>&1; then
        echo -e "${CYAN}Ban co the cai bang Homebrew:${NC}"
        echo -e "     brew install openjdk@21"
        echo -e "     sudo ln -sfn \$(brew --prefix)/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk"
    else
        echo -e "${YELLOW}     Vui long cai dat JDK 21 (Eclipse Temurin 21 hoac OpenJDK 21).${NC}"
    fi
fi

# --- 4. Kiem tra MySQL Client & Server --------------------------------
echo -e "\n${CYAN}--- Kiem tra MySQL Client & Server ---${NC}"
if command -v mysql >/dev/null 2>&1; then
    echo -e "${GREEN}[OK] Tim thay MySQL client: $(command -v mysql)${NC}"
else
    echo -e "${YELLOW}[!!] Chua tim thay lệnh mysql trong PATH.${NC}"
    if [ "$OS" = "Darwin" ] && command -v brew >/dev/null 2>&1; then
        echo -e "${CYAN}Ban co the cai dat MySQL qua Homebrew:${NC}"
        echo -e "     brew install mysql"
        echo -e "     brew services start mysql"
    fi
fi

# Kiem tra tien trinh mysqld
if pgrep -x mysqld >/dev/null 2>&1 || pgrep -x mariadbd >/dev/null 2>&1; then
    echo -e "${GREEN}[OK] MySQL Server dang chay.${NC}"
elif command -v mysqladmin >/dev/null 2>&1 && mysqladmin ping --silent 2>/dev/null; then
    echo -e "${GREEN}[OK] MySQL Server dang chay va phan hoi ping.${NC}"
else
    echo -e "${YELLOW}[!!] MySQL Server chua chay.${NC}"
    if [ "$OS" = "Darwin" ] && command -v brew >/dev/null 2>&1; then
        echo -e "     Ban co the bat MySQL bang: brew services start mysql hoac chay ./scripts/bash/mysql-start.sh"
    else
        echo -e "     Mo cua so khac va chay: ./scripts/bash/mysql-start.sh"
    fi
fi

echo -e "\n${CYAN}----------------------------------------------------------------${NC}"
echo -e "${CYAN}Buoc tiep theo: ./scripts/bash/mysql-start.sh -> ./scripts/bash/sql-create-db.sh -> ./scripts/bash/run.sh${NC}"
echo -e "${CYAN}----------------------------------------------------------------${NC}"
