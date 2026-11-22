package billing.util;

public final class Lang {
    private static volatile boolean english;

    private Lang() { }

    public static boolean isEnglish() { return english; }

    public static void setEnglish(boolean value) { english = value; }

    public static String pick(String vietnamese, String english) {
        return Lang.english ? english : vietnamese;
    }
}
