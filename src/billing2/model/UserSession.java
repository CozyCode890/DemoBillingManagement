package billing2.model;

/** Phiên làm việc: tài khoản đang đăng nhập (null = chưa đăng nhập). */
public class UserSession {

    private static Account currentUser;

    public static Account getCurrentUser()            { return currentUser; }
    public static void    setCurrentUser(Account a)   { currentUser = a; }
    public static void    clear()                     { currentUser = null; }
    public static boolean isLoggedIn()                { return currentUser != null; }
}
