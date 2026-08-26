package com.surveybot;

import com.surveybot.bot.TelegramBot;
import com.surveybot.config.Config;
import com.surveybot.services.CommunityService;
import com.surveybot.services.PersistenceService;
import com.surveybot.services.SurveyService;
import com.surveybot.ui.MainWindow;

import javax.swing.*;

/**
 * נקודת הכניסה לאפליקציה. מרכיבה את שכבת ה-persistence, השירותים,
 * הבוט, ולבסוף פותחת את חלון ה-Swing.
 */
public final class Application {

    private Application() {
    }

    public static void main(String[] args) {
        // בדיקת קונפיגורציה מוקדמת ומפורשת — *לפני* כל דבר אחר, ועל ה-thread
        // הראשי ממש (לא בתוך invokeLater ולא אחרי שנפתחו threads נוספים).
        // הסיבה: Config נטען בעצלנות (Java טוען מחלקה רק בפעם הראשונה
        // שנוגעים בה), ו-TelegramBot.start() פותח thread נפרד משלו
        // (telegram-bot-polling) שרק בתוכו, מאוחר יותר, הייתה מתרחשת
        // הנגיעה הראשונה ב-Config. אם TELEGRAM_BOT_TOKEN/CHATGPT_API_TOKEN
        // חסרים, השגיאה הייתה נזרקת שם — על thread נפרד, מחוץ לכל
        // try/catch כאן — וגורמת לקריסה שקטה לגמרי בלי שום הודעה למשתמש.
        // הקריאה המפורשת הזו מכריחה את הטעינה לקרות כאן, מוקדם, איפה
        // שאפשר לתפוס אותה כראוי.
        try {
            Class.forName(Config.class.getName());
        } catch (Throwable configError) {
            Throwable rootCause = unwrapToRootCause(configError);
            rootCause.printStackTrace();
            // אין ל-JOptionPane גישה ל-look-and-feel המערכתי בשלב הזה עדיין
            // (הוא מוגדר רק בהמשך), אבל זה לא משנה: החלון עדיין ייפתח תקין,
            // רק בעיצוב ברירת המחדל של Swing.
            JOptionPane.showMessageDialog(
                    null,
                    "אירעה שגיאה בהפעלת האפליקציה:\n" + rootCause.getMessage(),
                    "שגיאת הפעלה",
                    JOptionPane.ERROR_MESSAGE
            );
            System.exit(1);
            return;
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // אם ה-look and feel של המערכת לא זמין מסיבה כלשהי, נמשיך עם ברירת המחדל של Swing.
        }

        SwingUtilities.invokeLater(() -> {
            try {
                PersistenceService persistenceService = new PersistenceService();
                CommunityService communityService = new CommunityService(persistenceService);
                SurveyService surveyService = new SurveyService(persistenceService, communityService);

                TelegramBot telegramBot = new TelegramBot(communityService, surveyService);
                telegramBot.start();

                MainWindow mainWindow = new MainWindow(communityService, surveyService, telegramBot);
                mainWindow.setVisible(true);

            } catch (Throwable e) {
                Throwable rootCause = unwrapToRootCause(e);
                rootCause.printStackTrace();
                JOptionPane.showMessageDialog(
                        null,
                        "אירעה שגיאה בהפעלת האפליקציה:\n" + rootCause.getMessage(),
                        "שגיאת הפעלה",
                        JOptionPane.ERROR_MESSAGE
                );
                System.exit(1);
            }
        });
    }

    private static Throwable unwrapToRootCause(Throwable e) {
        Throwable rootCause = e;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }
}
