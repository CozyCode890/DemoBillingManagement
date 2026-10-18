#!/bin/bash
# =====================================================================
#  Setup.command -- BẢN DỰ PHÒNG của Setup.app trên macOS
# ---------------------------------------------------------------------
#  Cách chạy thông thường: bấm đúp Setup.app.
#
#  File này giữ lại cho hai trường hợp:
#    1. Bạn tải repo về dạng ZIP thay vì git clone. Khi đó macOS gắn cờ
#       "cách ly" (quarantine) lên mọi file trong ZIP và Gatekeeper sẽ
#       chặn Setup.app với thông báo "không mở được vì không rõ nhà phát
#       triển". Chạy file này sẽ gỡ cờ đó rồi mở app hộ bạn.
#    2. Bạn thích chạy từ Terminal và muốn thấy log ngay trong cửa sổ.
#
#  Lưu ý: KHÔNG kiểm tra Java bằng "command -v java" ở đây. Trên macOS
#  /usr/bin/java luôn tồn tại dù máy chưa cài JDK (nó là vỏ rỗng của
#  Apple), nên phép thử đó luôn đúng và ta sẽ bỏ lỡ nhánh báo lỗi. Việc
#  dò Java để nguyên một chỗ trong Setup.app/Contents/MacOS/Setup.
# =====================================================================

cd "$(dirname "$0")" || exit 1
java -jar SetupApp.jar
APP="$PWD/Setup.app"
LAUNCHER="$APP/Contents/MacOS/Setup"

if [ ! -d "$APP" ]; then
    echo "[LỖI] Không thấy Setup.app cạnh Setup.command."
    echo "      Tải lại dự án hoặc chạy: java -jar SetupApp.jar"
    read -r -p "Nhấn Enter để đóng..." _
    exit 1
fi

# Gỡ cờ cách ly nếu có (im lặng khi không có gì để gỡ).
if /usr/bin/xattr -p com.apple.quarantine "$APP" >/dev/null 2>&1; then
    echo "Đang gỡ cờ cách ly của macOS khỏi Setup.app..."
    /usr/bin/xattr -dr com.apple.quarantine "$APP"
fi

# Git và ZIP đôi khi làm mất quyền thực thi của file launcher bên trong.
if [ ! -x "$LAUNCHER" ]; then
    echo "Đang cấp lại quyền thực thi cho $LAUNCHER..."
    chmod +x "$LAUNCHER"
fi

echo "Đang mở Setup.app..."
/usr/bin/open "$APP"
