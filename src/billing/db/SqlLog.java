package billing.db;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Ghi lại MỌI câu SQL mà app đã gửi xuống MySQL.
 *
 * Đây không phải thứ bắt buộc của JDBC -- tôi thêm vào để bạn mở tab
 * "Nhật ký SQL" trong app và thấy tận mắt: mỗi lần bạn bấm một nút trên
 * giao diện thì Java đã gửi câu lệnh gì xuống database.
 */
public class SqlLog {

    private static final List<String> lines = new ArrayList<>();
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Thêm một mục vào nhật ký. */
    public static void add(String sql, Object[] params, long millis, int rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(LocalTime.now().format(HMS)).append("]  ")
          .append(millis).append(" ms, ").append(rows).append(" dong\n");
        sb.append(sql.trim()).append("\n");
        if (params != null && params.length > 0) {
            sb.append("   tham so (?): ");
            for (int i = 0; i < params.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(i + 1).append("=").append(params[i]);
            }
            sb.append("\n");
        }
        sb.append("------------------------------------------------------------\n");

        lines.add(sb.toString());
        System.out.print(sb);          // in luôn ra console cho tiện xem
    }

    public static String dump() {
        if (lines.isEmpty()) return "(chua chay cau SQL nao)";
        StringBuilder sb = new StringBuilder();
        for (String s : lines) sb.append(s);
        return sb.toString();
    }

    public static void clear() { lines.clear(); }
}
