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

APP="$PWD/Setup.app"
LAUNCHER="$APP/Contents/MacOS/Setup"

# Gỡ cờ cách ly nếu có (trên toàn bộ thư mục dự án và Setup.app).
if /usr/bin/xattr -p com.apple.quarantine "$PWD" >/dev/null 2>&1 || ([ -d "$APP" ] && /usr/bin/xattr -p com.apple.quarantine "$APP" >/dev/null 2>&1); then
    echo "Đang gỡ cờ cách ly của macOS khỏi dự án..."
    /usr/bin/xattr -dr com.apple.quarantine "$PWD" 2>/dev/null || true
fi

# Đảm bảo quyền đọc cho SetupApp.jar nếu có
if [ -f "$PWD/SetupApp.jar" ] && [ ! -r "$PWD/SetupApp.jar" ]; then
    chmod +r "$PWD/SetupApp.jar" 2>/dev/null || true
fi

if [ -f "$LAUNCHER" ]; then
    # Git và ZIP đôi khi làm mất quyền thực thi của file launcher bên trong.
    if [ ! -x "$LAUNCHER" ]; then
        echo "Đang cấp lại quyền thực thi cho $LAUNCHER..."
        chmod +x "$LAUNCHER"
    fi

    echo "Đang khởi chạy Setup App qua Terminal..."
    exec "$LAUNCHER" "$@"
fi

# Dự phòng khi thiếu Setup.app: chạy thẳng bằng lệnh java của hệ thống
if [ -f "$PWD/SetupApp.jar" ]; then
    echo "Không tìm thấy $LAUNCHER, chuyển sang chạy trực tiếp SetupApp.jar..."
    exec java -Dfile.encoding=UTF-8 -jar "$PWD/SetupApp.jar" "$@"
fi

echo "[LỖI] Không thấy Setup.app hoặc SetupApp.jar cạnh Setup.command."
echo "      Tải lại dự án hoặc chạy: bash scripts/bash/build-setup.sh"
read -r -p "Nhấn Enter để đóng..." _
exit 1
