#!/bin/bash
# =====================================================================
#  Setup.command -- Launcher 1-click cho macOS (tương đương Setup.bat)
#  Người dùng click đúp vào file này trong Finder để mở GUI Setup App.
# =====================================================================

cd "$(dirname "$0")"

# Nạp môi trường Homebrew (Apple Silicon & Intel)
if [ -x "/opt/homebrew/bin/brew" ]; then
    eval "$(/opt/homebrew/bin/brew shellenv)"
elif [ -x "/usr/local/bin/brew" ]; then
    eval "$(/usr/local/bin/brew shellenv)"
fi

# Nạp JAVA_HOME trên macOS nếu chưa có trong biến môi trường
if [ -z "$JAVA_HOME" ] && [ -x "/usr/libexec/java_home" ]; then
    JH=$(/usr/libexec/java_home 2>/dev/null || true)
    if [ -n "$JH" ]; then
        export JAVA_HOME="$JH"
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
fi

# Chạy SetupApp.jar
java -Dfile.encoding=UTF-8 -jar SetupApp.jar &
