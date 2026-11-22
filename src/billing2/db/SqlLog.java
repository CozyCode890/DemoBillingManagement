package billing2.db;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Ghi MỌI câu SQL app gửi xuống MySQL vào logs/sql.log -- tab "SQL Log" của
 * Setup App đọc file này để bạn thấy tận mắt mỗi cú bấm chạy câu gì.
 *
 * Định dạng là HỢP ĐỒNG với Setup App: một bản ghi = đúng MỘT dòng, 5 trường:
 *   2026-10-10 14:03:21.123 | 12 ms | 3 rows | {call sp_get_invoice_lines(?)} | ? 1=INV-0001
 */
public class SqlLog {

    private static final Path LOG_FILE    = Paths.get("logs", "sql.log");
    private static final Path ROLLED_FILE = Paths.get("logs", "sql.log.1");
    private static final long MAX_BYTES   = 2L * 1024 * 1024;     // quá 2 MB thì xoay vòng
    private static final int  MAX_SQL_LEN = 500;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** Ghi file lỗi một lần là tắt hẳn: log hỏng không được làm sập việc bán hàng. */
    private static boolean disabled = false;

    /** Dòng mốc đầu phiên -- file chứa nhật ký của nhiều ngày, nhiều lần mở app. */
    public static synchronized void appStart(String dbUrl) {
        String line = "=== APP START "
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            + " | pid " + ProcessHandle.current().pid() + " | " + oneLine(dbUrl, MAX_SQL_LEN) + " ===";
        System.out.println(line);
        write(line);
    }

    /** Thêm một bản ghi. synchronized: hai luồng ghi xen nhau sẽ làm vỡ "một dòng một bản ghi". */
    public static synchronized void add(String sql, Object[] params, long millis, int rows) {
        String line = LocalDateTime.now().format(STAMP)
            + " | " + millis + " ms"
            + " | " + rows + " rows"
            + " | " + oneLine(sql, MAX_SQL_LEN)
            + " | " + formatParams(params);
        System.out.println(line);
        write(line);
    }

    /** Gộp mọi khoảng trắng (kể cả xuống dòng) thành một dấu cách, cắt nếu quá dài. */
    private static String oneLine(String s, int max) {
        if (s == null) return "";
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max) + "...";
    }

    /** "? 1=INV-0001, 2=2026-10-10" hoặc chuỗi rỗng. */
    private static String formatParams(Object[] params) {
        if (params == null || params.length == 0) return "";
        StringBuilder sb = new StringBuilder("? ");
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(i + 1).append('=').append(oneLine(String.valueOf(params[i]), 120));
        }
        return sb.toString();
    }

    /** Mở - ghi - đóng cho từng dòng: không giữ file, để Setup App xoá được trên Windows. */
    private static void write(String line) {
        if (disabled) return;
        try {
            Files.createDirectories(LOG_FILE.getParent());
            if (Files.isRegularFile(LOG_FILE) && Files.size(LOG_FILE) >= MAX_BYTES) {
                Files.move(LOG_FILE, ROLLED_FILE, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(LOG_FILE, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            disabled = true;
            System.err.println("[SqlLog] Cannot write " + LOG_FILE.toAbsolutePath()
                + " -- file logging turned off, console only. Reason: " + e.getMessage());
        }
    }
}
