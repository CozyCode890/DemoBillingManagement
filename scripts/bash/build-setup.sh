#!/bin/bash
# =====================================================================
#  build-setup.sh -- Biên dịch & đóng gói SetupApp.jar trên macOS / Linux
# =====================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

GREEN='\033[0;32m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

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

if ! command -v javac >/dev/null 2>&1 || ! command -v jar >/dev/null 2>&1; then
    echo -e "${RED}[LOI] Khong tim thay javac hoac jar trong PATH!${NC}"
    exit 1
fi

echo -e "${CYAN}Dang bien dich Setup App...${NC}"
OUT_DIR="$PROJECT_ROOT/out/setup"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

SOURCES_LIST=$(mktemp /tmp/envsetup-sources.XXXXXX)
find "$PROJECT_ROOT/src/envsetup" -type f -name "*.java" -print0 | while IFS= read -r -d '' f; do
    echo "\"$f\"" >> "$SOURCES_LIST"
done

javac -encoding UTF-8 -d "$OUT_DIR" @"$SOURCES_LIST"
rm -f "$SOURCES_LIST"

JAR_PATH="$PROJECT_ROOT/SetupApp.jar"
echo -e "${CYAN}Dang dong goi SetupApp.jar tai $JAR_PATH...${NC}"
jar cfe "$JAR_PATH" envsetup.Main -C "$OUT_DIR" .

rm -rf "$OUT_DIR"
echo -e "${GREEN}[OK] Da tao thanh cong SetupApp.jar tai thu muc goc.${NC}"
