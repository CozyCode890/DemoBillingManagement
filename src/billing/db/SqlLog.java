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

public class SqlLog {
    private static final Path LOG_FILE = Paths.get("logs", "sql.log");
    private static final Path ROLLED_FILE = Paths.get("logs", "sql.log.1");

    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final int MAX_SQL_LEN = 500;

    private static final DateTimeFormatter STAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final DateTimeFormatter STAMP_SECONDS =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static boolean disabled = false;

    public static synchronized void appStart(String dbUrl) {
        String line = "=== APP START " + LocalDateTime.now().format(STAMP_SECONDS)
            + " | pid " + ProcessHandle.current().pid()
            + " | " + oneLine(dbUrl, MAX_SQL_LEN) + " ===";
        System.out.println(line);
        write(line);
    }

    public static synchronized void add(String sql, Object[] params, long millis, int rows) {
        String line = LocalDateTime.now().format(STAMP)
            + " | " + millis + " ms"
            + " | " + rows + " rows"
            + " | " + oneLine(sql, MAX_SQL_LEN)
            + " | " + formatParams(params);
        System.out.println(line);
        write(line);
    }

    private static String oneLine(String s, int max) {
        if (s == null) {
            return "";
        }
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max) + "...";
    }

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
            System.err.println("[SqlLog] Cannot write " + LOG_FILE.toAbsolutePath()
                + " -- file logging turned off, console only. Reason: " + e.getMessage());
        }
    }

    private static void rotateIfTooBig() throws IOException {
        if (!Files.isRegularFile(LOG_FILE) || Files.size(LOG_FILE) < MAX_BYTES) {
            return;
        }
        Files.move(LOG_FILE, ROLLED_FILE, StandardCopyOption.REPLACE_EXISTING);
    }
}
