package billing.util;

/**
 * Ngon ngu hien tai cho cac lop KHONG thuoc giao dien (util, model) -- chung
 * tao ra thong bao cho nguoi dung (vd. {@link CashRules}) nhung khong duoc phu
 * thuoc vao goi {@code billing.ui}.
 *
 * <p>{@code billing.ui.I18n} la noi duy nhat doi gia tri nay (khi bam nut doi
 * ngon ngu). Mac dinh la tieng Viet.
 */
public final class Lang {

    private static volatile boolean english;

    private Lang() { }

    public static boolean isEnglish() { return english; }

    /** Chi {@code billing.ui.I18n} goi ham nay. */
    public static void setEnglish(boolean value) { english = value; }

    /** Chon ban tieng Viet (co dau) hoac tieng Anh theo ngon ngu hien tai. */
    public static String pick(String vietnamese, String english) {
        return Lang.english ? english : vietnamese;
    }
}
