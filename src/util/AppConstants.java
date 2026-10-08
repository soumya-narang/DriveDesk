package util;

/** Application-wide constants. final: cannot be extended; private constructor: no objects. */
public final class AppConstants {
    public static final int NEAR_EXPIRY_DAYS = 7;
    public static final int URGENT_EXPIRY_DAYS = 3;
    public static final int REPEAT_WINDOW_DAYS = 30;
    public static final int DEFAULT_PORT = 8090;
    public static final String DATA_DIR = "data/";
    public static final String WEB_DIR = "web/public";

    private AppConstants() {}
}
