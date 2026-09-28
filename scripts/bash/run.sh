#!/bin/bash
# =====================================================================
#  run.sh -- Biên dịch rồi chạy ứng dụng Java Retail Billing
#  Hỗ trợ macOS & Linux
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

# --- 0. Môi trường macOS / Homebrew / Java -----------------------------
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
    echo -e "${RED}[LOI] Chua co JDK trong PATH. Hay chay: ./scripts/bash/setup.sh${NC}"
    exit 1
fi

JAR_DRIVER=$(ls "$PROJECT_ROOT/lib"/mysql-connector-j-*.jar 2>/dev/null | head -n 1 || true)
if [ -z "$JAR_DRIVER" ]; then
    echo -e "${RED}[LOI] Thieu driver MySQL trong lib/. Hay chay: ./scripts/bash/setup.sh${NC}"
    exit 1
fi

# --- 1. Đọc Main Class từ config.properties --------------------------
MAIN_CLASS=""
if [ -f "$PROJECT_ROOT/config.properties" ]; then
    CONFIG_CLASS=$(grep -E '^\s*app\.main\.class\s*=' "$PROJECT_ROOT/config.properties" | cut -d'=' -f2- | tr -d ' \r\n')
    if [ -n "$CONFIG_CLASS" ]; then
        MAIN_CLASS="$CONFIG_CLASS"
    fi
fi

# --- 2. Dọn dẹp tiến trình cũ ----------------------------------------
if [ -f "$SCRIPT_DIR/billing-stop.sh" ]; then
    bash "$SCRIPT_DIR/billing-stop.sh" || true
fi

# --- 3. Quét danh sách file .java trong src/ --------------------------
echo -e "${CYAN}Đang quét mã nguồn trong src/...${NC}"
JAVA_FILES=()
while IFS= read -r -d '' file; do
    JAVA_FILES+=("$file")
done < <(find "$PROJECT_ROOT/src" -type f -name "*.java" ! -path "*/envsetup/*" -print0)

TOTAL_FILES=${#JAVA_FILES[@]}
if [ $TOTAL_FILES -eq 0 ]; then
    echo -e "${RED}[LỖI] Không tìm thấy file mã nguồn .java nào trong src/ (đã loại trừ src/envsetup).${NC}"
    exit 1
fi

# Tự động tìm Main Class nếu chưa cấu hình
if [ -z "$MAIN_CLASS" ]; then
    for f in "${JAVA_FILES[@]}"; do
        if [[ "$(basename "$f")" == *"Main.java"* ]]; then
            PKG=$(grep -E '^\s*package\s+[^;]+;' "$f" | head -n 1 | sed -E 's/^\s*package\s+([^;]+);/\1/' | tr -d ' \r\n')
            BASE=$(basename "$f" .java)
            if [ -n "$PKG" ]; then
                MAIN_CLASS="${PKG}.${BASE}"
            else
                MAIN_CLASS="$BASE"
            fi
            break
        fi
    done
fi

if [ -z "$MAIN_CLASS" ]; then
    MAIN_CLASS="billing.Main"
fi

echo -e "${YELLOW}Lớp khởi chạy chính (Main Class): $MAIN_CLASS${NC}"

# --- 4. Biên dịch ----------------------------------------------------
echo -e "${CYAN}Đang biên dịch $TOTAL_FILES file mã nguồn...${NC}"
mkdir -p "$PROJECT_ROOT/out"
rm -rf "$PROJECT_ROOT/out"/* 2>/dev/null || true

TMP_SOURCES=$(mktemp /tmp/billing-sources.XXXXXX)
for f in "${JAVA_FILES[@]}"; do
    echo "\"$f\"" >> "$TMP_SOURCES"
done

javac -encoding UTF-8 -d "$PROJECT_ROOT/out" -cp "$PROJECT_ROOT/lib/*" @"$TMP_SOURCES"
rm -f "$TMP_SOURCES"

echo -e "${GREEN}[OK] Biên dịch xong vào out/${NC}"

# --- 5. Khởi chạy -----------------------------------------------------
echo -e "${CYAN}Đang khởi động app ($MAIN_CLASS)...${NC}"
java -Dfile.encoding=UTF-8 -cp "$PROJECT_ROOT/out:$PROJECT_ROOT/lib/*" "$MAIN_CLASS"
