package billing.db;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Ghi lại MỌI câu SQL mà app đã gửi xuống MySQL vào file {@code logs/sql.log}.
 *
 * <p>Đây không phải thứ bắt buộc của JDBC -- tôi thêm vào để bạn mở tab
 * "SQL Log" trong app quản lý môi trường (Setup App) và thấy tận mắt: mỗi lần
 * bạn bấm một nút trên giao diện thì Java đã gửi câu lệnh gì xuống database.
 *
 * <p>App billing chỉ còn nhiệm vụ <b>ghi</b>; màn hình xem nhật ký nằm bên Setup
 * App. Hai app là hai tiến trình JVM riêng, không dùng chung đối tượng Java nào
 * được, nên <b>định dạng file chính là hợp đồng duy nhất</b> giữa chúng: sửa một
 * bên phải sửa bên kia. Đặc tả đầy đủ:
 * {@code plan/10-KE-HOACH-KHOI-1-SQL-LOG-SANG-SETUP-APP.md} mục 2.
 *
 * <p>Bất biến bắt buộc: <b>một bản ghi = đúng một dòng</b>, 5 trường cách nhau
 * bằng {@code " | "}:
 * <pre>
 * 2026-10-10 14:03:21.123 | 12 ms | 3 dong | {call sp_get_invoice_lines(?)} | ? 1=INV-0001
 * </pre>
 */
public class SqlLog {

    /** Đường dẫn tương đối: app billing luôn chạy với thư mục làm việc là gốc dự án. */
    private static final Path LOG_FILE = Paths.get("logs", "sql.log");
    private static final Path ROLLED_FILE = Paths.get("logs", "sql.log.1");

    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final int MAX_SQL_LEN = 500;

    /** Có cả ngày chứ không chỉ giờ: file sống qua nhiều phiên làm việc, nhiều ngày. */
    private static final DateTimeFormatter STAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final DateTimeFormatter STAMP_SECONDS =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Bật lên khi ghi file thất bại: từ đó chỉ còn in console, không thử ghi nữa. */
    private static boolean disabled = false;

    /**
     * Ghi dòng mốc đánh dấu một phiên chạy mới. Gọi một lần từ {@code billing.Main}.
     * Nhờ dòng này, người đọc file phân biệt được nhật ký của phiên nào.
     */
    public static synchronized void appStart(String dbUrl) {
        String line = "=== APP START " + LocalDateTime.now().format(STAMP_SECONDS)
            + " | pid " + ProcessHandle.current().pid()
            + " | " + oneLine(dbUrl, MAX_SQL_LEN) + " ===";
        System.out.println(line);
        write(line);
    }

    /**
     * Thêm một mục vào nhật ký.
     *
     * <p>Giữ nguyên chữ ký cũ: có khoảng 20 chỗ gọi rải khắp {@code billing/dao}.
     *
     * <p>{@code synchronized} vì phần lớn lời gọi đến từ EDT nhưng không có gì bảo
     * đảm điều đó về sau (ví dụ DAO chạy trong {@code SwingWorker}), mà hai luồng
     * ghi xen nhau sẽ phá bất biến "một dòng một bản ghi".
     */
    public static synchronized void add(String sql, Object[] params, long millis, int rows) {
        String line = LocalDateTime.now().format(STAMP)
            + " | " + millis + " ms"
            + " | " + rows + " dong"
            + " | " + oneLine(sql, MAX_SQL_LEN)
            + " | " + formatParams(params);
        System.out.println(line);   // console vẫn thấy, tab Java của Setup App cũng thấy
        write(line);
    }

    // ------------------------------------------------------------------
    //  Dựng chuỗi
    // ------------------------------------------------------------------

    /**
     * Co mọi khoảng trắng liên tiếp (kể cả {@code \r\n}) thành một dấu cách rồi
     * cắt nếu quá dài.
     *
     * <p>Thay {@code \r} và {@code \n} là bắt buộc chứ không phải cho đẹp: một
     * dòng trong file <i>phải</i> là một bản ghi, nếu không thì bên Setup App
     * đọc lệch và ô "lọc dòng chứa..." trở nên vô dụng.
     */
    private static String oneLine(String s, int max) {
        if (s == null) {
            return "";
        }
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max) + "...";
    }

    /** {@code "? 1=INV-0001, 2=2026-10-10"} hoặc chuỗi rỗng nếu không có tham số. */
    private static String formatParams(Object[] params) {
        if (params == null || params.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder("? ");
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(i + 1).append('=').append(oneLine(String.valueOf(params[i]), 120));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    //  Ghi file
    // ------------------------------------------------------------------

    /**
     * Mở -- ghi -- đóng ngay cho từng bản ghi, không giữ {@code Writer} mở suốt phiên.
     *
     * <p>Lý do: trên Windows, file đang bị tiến trình khác giữ handle thì xoá hay
     * đổi tên hay bị từ chối -- nút "Xoá nhật ký" bên Setup App sẽ thất bại đúng
     * lúc người dùng cần nó nhất. Tần suất ghi ở đây do người dùng bấm chuột quyết
     * định (vài câu mỗi lần bấm) nên chi phí mở/đóng file là không đáng kể.
     *
     * <p>Lỗi ghi log tuyệt đối không được làm app billing chết: nghiệp vụ bán hàng
     * không bao giờ được đổ vì cái ổ đĩa đầy. Lỗi đầu tiên in một dòng cảnh báo rồi
     * tắt hẳn việc ghi file cho phần còn lại của phiên, để không spam ở mỗi câu SQL.
     */
    private static void write(String line) {
        if (disabled) {
            return;
        }
        try {
            Path parent = LOG_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            rotateIfTooBig();
            Files.writeString(LOG_FILE, line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            disabled = true;
            System.err.println("[SqlLog] Khong ghi duoc " + LOG_FILE.toAbsolutePath()
                + " -- tat ghi file, chi con in console. Ly do: " + e.getMessage());
        }
    }

    /**
     * Giữ đúng một bản lưu: nhật ký SQL của app demo không cần lưu trữ lâu dài,
     * chỉ cần không phình vô hạn.
     */
    private static void rotateIfTooBig() throws IOException {
        if (!Files.isRegularFile(LOG_FILE) || Files.size(LOG_FILE) < MAX_BYTES) {
            return;
        }
        Files.move(LOG_FILE, ROLLED_FILE, StandardCopyOption.REPLACE_EXISTING);
    }
}
