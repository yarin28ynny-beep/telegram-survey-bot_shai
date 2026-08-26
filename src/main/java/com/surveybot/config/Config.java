package com.surveybot.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * קונפיגורציה מרכזית של המערכת.
 * כל הקבועים וההגבלות העסקיות מרוכזים כאן במקום אחד.
 * <p>
 * ה-API tokens (טלגרם, ChatGPT) <b>אינם</b> כתובים בקוד המקור בכוונה —
 * הם נטענים בזמן ריצה ממשתני סביבה, עם נפילה-אחורה לקובץ {@code .env}
 * מקומי בשורש הפרויקט אם קיים. כך אפשר להעלות את הקוד ל-GitHub בבטחה,
 * בלי לחשוף טוקנים בהיסטוריית ה-git.
 * <p>
 * להרצה מקומית: צור קובץ {@code .env} בשורש הפרויקט (ליד ה-pom.xml) לפי
 * הדוגמה ב-{@code .env.example}. הקובץ הזה נמצא ב-{@code .gitignore}
 * ולעולם לא יעלה ל-git.
 */
public final class Config {

    private Config() {
    }

    private static final Map<String, String> ENV_FILE_VALUES = loadDotEnvFile();

    /**
     * קורא ערך קונפיגורציה בסדר עדיפות: משתנה סביבה אמיתי (System.getenv)
     * קודם, ואם אינו קיים — הערך המקביל מתוך קובץ {@code .env} המקומי.
     * זורק שגיאה ברורה בזמן טעינת המחלקה אם שני המקורות ריקים, כדי
     * שהמשתמש יידע מיד שחסרה הגדרה, במקום לקבל NullPointerException
     * מבלבל מאוחר יותר עמוק בתוך קריאת HTTP.
     */
    private static String requireEnv(String key) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        String fromFile = ENV_FILE_VALUES.get(key);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile;
        }
        throw new IllegalStateException(
                "חסר ערך קונפיגורציה נדרש: " + key + "\n" +
                "הגדר אותו כמשתנה סביבה, או צור קובץ .env בשורש הפרויקט " +
                "(ליד pom.xml) לפי הדוגמה ב-.env.example."
        );
    }

    private static Map<String, String> loadDotEnvFile() {
        Map<String, String> values = new HashMap<>();
        Path envPath = Paths.get(".env");
        if (!Files.exists(envPath)) {
            return values;
        }
        try {
            for (String line : Files.readAllLines(envPath)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int eq = trimmed.indexOf('=');
                if (eq <= 0) continue;
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                // מסיר גרשיים אופציונליים סביב הערך, אם המשתמש הוסיף כאלה.
                if (value.length() >= 2 &&
                        ((value.startsWith("\"") && value.endsWith("\"")) ||
                         (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (IOException e) {
            System.err.println("אזהרה: לא ניתן היה לקרוא את קובץ .env: " + e.getMessage());
        }
        return values;
    }

    // ===================== Telegram Bot =====================
    public static final String TELEGRAM_BOT_TOKEN = requireEnv("TELEGRAM_BOT_TOKEN");
    public static final String TELEGRAM_BOT_USERNAME = "@communityn_bot";
    public static final String TELEGRAM_API_BASE = "https://api.telegram.org/bot" + TELEGRAM_BOT_TOKEN + "/";
    public static final int LONG_POLLING_TIMEOUT_SECONDS = 30;

    // ===================== ChatGPT Bridge API =====================
    public static final String CHATGPT_API_URL = "https://shaitest-production-3066.up.railway.app/api-request";
    public static final String CHATGPT_API_TOKEN = requireEnv("CHATGPT_API_TOKEN");

    // ===================== הגבלות עסקיות (מתוך הוראות המשימה) =====================
    /** מספר חברי קהילה מינימלי כדי שיהיה אפשר להתחיל סקר. */
    public static final int MIN_COMMUNITY_SIZE_TO_START = 3;

    /** טווח מספר השאלות המותר בסקר. */
    public static final int MIN_QUESTIONS = 1;
    public static final int MAX_QUESTIONS = 3;

    /** טווח מספר אפשרויות התשובה לשאלה. */
    public static final int MIN_ANSWERS = 2;
    public static final int MAX_ANSWERS = 4;

    /** משך הסקר המרבי בשניות (5 דקות) — לאחריו הסקר נסגר גם אם לא כולם ענו. */
    public static final long SURVEY_DURATION_SECONDS = 5 * 60L;

    /** מתי לשלוח תזכורת (3 דקות מתחילת הסקר) למי שטרם השלים. */
    public static final long REMINDER_AFTER_SECONDS = 3 * 60L;

    // ===================== אחסון נתונים =====================
    public static final String DATA_DIR = "data";
    public static final String COMMUNITY_FILE = DATA_DIR + "/community.json";
    public static final String SURVEYS_FILE = DATA_DIR + "/surveys.json";

    // ===================== UI =====================
    public static final String APP_TITLE = "מערכת ניהול סקרי קהילה — Telegram Survey Manager";
    public static final int WINDOW_WIDTH = 1280;
    public static final int WINDOW_HEIGHT = 820;
}
