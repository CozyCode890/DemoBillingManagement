package billing.util;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public final class AppConfig {
    private static final Properties P = new Properties();

    static {
        try (InputStream in = new FileInputStream("config.properties")) {
            P.load(in);
        } catch (Exception e) {
            System.out.println("[AppConfig] Cannot read config.properties, using default values.");
        }
    }

    public static String get(String key, String fallback) {
        String v = P.getProperty(key);
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    public static boolean getBoolean(String key, boolean fallback) {
        String v = get(key, null);
        if (v == null) {
            return fallback;
        }
        return v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes");
    }

    private AppConfig() { }
}
