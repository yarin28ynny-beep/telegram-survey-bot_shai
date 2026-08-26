package com.surveybot.services;

import com.surveybot.config.Config;
import com.surveybot.models.Survey;
import com.surveybot.models.SurveyQuestion;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * הליבה העסקית של ניהול סקרים: יצירה, תזמון שליחה (מיידית או מושהית),
 * אכיפת "סקר פעיל אחד בלבד", סגירה אוטומטית אחרי 5 דקות או השלמה מלאה,
 * ותזכורת אחרי 3 דקות למי שטרם השלים.
 * <p>
 * כל הפעולות שמשנות מצב הן synchronized על ה-service, כי טיימרים רצים
 * על thread נפרד ועלולים "להתנגש" עם פעולות שמגיעות מה-UI thread או
 * מ-thread ה-polling של הבוט.
 */
public class SurveyService {

    /** מאזין לאירועי מחזור חיים של סקר — UI ובוט טלגרם נרשמים כאן. */
    public interface Listener {
        /** הסקר יצא בפועל לדרך (לאחר עיכוב, אם היה) ונשלח לקהילה. */
        void onSurveyStarted(Survey survey);

        /** תשובה חדשה נרשמה — הזדמנות טובה לעדכן UI חי. */
        void onAnswerRecorded(Survey survey, long telegramId);

        /** הגיע רגע שליחת התזכורת — יש לשלוח הודעה למי שטרם השלים. */
        void onReminderDue(Survey survey, List<Long> usersToRemind);

        /** הסקר נסגר (הזמן נגמר, או כולם השלימו). */
        void onSurveyClosed(Survey survey);
    }

    private final PersistenceService persistence;
    private final CommunityService communityService;
    private final List<Survey> allSurveys;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    /** הסקר הפעיל היחיד כרגע (ACTIVE או SCHEDULED) — null אם אין. */
    private Survey liveSurvey;
    private Timer deadlineTimer;
    private Timer reminderTimer;
    private Timer scheduledStartTimer;

    public SurveyService(PersistenceService persistence, CommunityService communityService) {
        this.persistence = persistence;
        this.communityService = communityService;
        this.allSurveys = new ArrayList<>(persistence.loadSurveys());

        // אם האפליקציה נסגרה בזמן שסקר היה פעיל, נשחזר אותו כ-"סגור" בבטחה
        // בהפעלה מחדש — כדי לא להשאיר את המערכת במצב לא עקבי (טיימרים
        // ממילא לא שורדים הפעלה מחדש של ה-JVM).
        for (Survey s : allSurveys) {
            if (s.getStatus() == Survey.Status.ACTIVE || s.getStatus() == Survey.Status.SCHEDULED) {
                s.markClosed();
            }
        }
        persistence.saveSurveys(allSurveys);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    /** מסיר listener שנרשם קודם — למניעת דליפת זיכרון ברכיבי UI זמניים שנבנים מחדש. */
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public synchronized boolean hasLiveSurvey() {
        return liveSurvey != null;
    }

    public synchronized Survey getLiveSurvey() {
        return liveSurvey;
    }

    public synchronized List<Survey> getAllSurveys() {
        return new ArrayList<>(allSurveys);
    }

    /**
     * יוצר סקר חדש ומתזמן את שליחתו.
     *
     * @param delaySeconds 0 = שליחה מיידית; אחרת, מספר שניות עד לשליחה בפועל.
     * @throws IllegalStateException אם כבר יש סקר פעיל, או שאין מספיק חברי קהילה.
     */
    public synchronized Survey createAndScheduleSurvey(String title, List<SurveyQuestion> questions, long delaySeconds) {
        if (liveSurvey != null) {
            throw new IllegalStateException("קיים כבר סקר פעיל. יש להמתין לסיומו לפני יצירת סקר חדש.");
        }
        int communitySize = communityService.size();
        if (communitySize < Config.MIN_COMMUNITY_SIZE_TO_START) {
            throw new IllegalStateException(
                    "לא ניתן להתחיל סקר: יש רק " + communitySize + " חברי קהילה, " +
                    "ונדרשים לפחות " + Config.MIN_COMMUNITY_SIZE_TO_START + "."
            );
        }
        if (questions.isEmpty() || questions.size() > Config.MAX_QUESTIONS) {
            throw new IllegalStateException("מספר שאלות לא תקין: נדרש בין " +
                    Config.MIN_QUESTIONS + " ל-" + Config.MAX_QUESTIONS + ".");
        }

        Survey survey = new Survey(title);
        for (SurveyQuestion q : questions) {
            survey.addQuestion(q);
        }

        if (delaySeconds > 0) {
            survey.setScheduledFor(LocalDateTime.now().plusSeconds(delaySeconds));
            survey.setStatus(Survey.Status.SCHEDULED);
        }

        allSurveys.add(survey);
        liveSurvey = survey;
        persist();

        if (delaySeconds <= 0) {
            startSurveyNow(survey);
        } else {
            scheduledStartTimer = new Timer("survey-scheduled-start", true);
            scheduledStartTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    startSurveyNow(survey);
                }
            }, delaySeconds * 1000L);
        }

        return survey;
    }

    private synchronized void startSurveyNow(Survey survey) {
        // ייתכן שהאובייקט survey כבר לא רלוונטי (למשל אם המשתמש הצליח
        // ליצור מצב אחר בין הרגע שהוזמן הטיימר לרגע שהוא רץ) — בדיקת הגנה.
        if (liveSurvey != survey) {
            return;
        }

        survey.initializeParticipants(communityService.getAllUserIds());
        survey.markStarted();
        persist();

        for (Listener l : listeners) {
            l.onSurveyStarted(survey);
        }

        // טיימר סגירה אחרי 5 דקות בדיוק
        deadlineTimer = new Timer("survey-deadline", true);
        deadlineTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                closeSurveyIfStillLive(survey, false);
            }
        }, Config.SURVEY_DURATION_SECONDS * 1000L);

        // טיימר תזכורת אחרי 3 דקות
        reminderTimer = new Timer("survey-reminder", true);
        reminderTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                fireReminderIfStillLive(survey);
            }
        }, Config.REMINDER_AFTER_SECONDS * 1000L);
    }

    /**
     * רושם תשובה של משתמש לשאלה בסקר החי. אם זו התשובה האחרונה שהייתה
     * חסרה לו וכעת כולם השלימו — הסקר נסגר מיידית (לפי ההוראות).
     */
    public synchronized void recordAnswer(String surveyId, long telegramId, int questionIndex, int optionIndex) {
        if (liveSurvey == null || !liveSurvey.getId().equals(surveyId)) {
            throw new IllegalStateException("הסקר אינו פעיל יותר.");
        }
        if (!liveSurvey.isParticipant(telegramId)) {
            throw new IllegalStateException("משתמש זה אינו משתתף בסקר הנוכחי.");
        }

        liveSurvey.recordAnswer(telegramId, questionIndex, optionIndex);
        persist();

        for (Listener l : listeners) {
            l.onAnswerRecorded(liveSurvey, telegramId);
        }

        if (liveSurvey.isEveryoneComplete()) {
            closeSurveyIfStillLive(liveSurvey, true);
        }
    }

    private synchronized void fireReminderIfStillLive(Survey survey) {
        if (liveSurvey != survey || survey.getStatus() != Survey.Status.ACTIVE) {
            return;
        }
        List<Long> toRemind = new ArrayList<>();
        for (var entry : survey.getParticipations().entrySet()) {
            var participation = entry.getValue();
            if (!participation.isComplete(survey.getQuestions().size()) && !participation.isReminderSent()) {
                participation.markReminderSent();
                toRemind.add(entry.getKey());
            }
        }
        if (!toRemind.isEmpty()) {
            persist();
            for (Listener l : listeners) {
                l.onReminderDue(survey, toRemind);
            }
        }
    }

    private synchronized void closeSurveyIfStillLive(Survey survey, boolean dueToCompletion) {
        if (liveSurvey != survey) {
            return; // כבר נסגר ממקור אחר
        }
        cancelTimersQuietly();
        survey.markClosed();
        liveSurvey = null;
        persist();

        for (Listener l : listeners) {
            l.onSurveyClosed(survey);
        }
    }

    private void cancelTimersQuietly() {
        if (deadlineTimer != null) { deadlineTimer.cancel(); deadlineTimer = null; }
        if (reminderTimer != null) { reminderTimer.cancel(); reminderTimer = null; }
        if (scheduledStartTimer != null) { scheduledStartTimer.cancel(); scheduledStartTimer = null; }
    }

    private void persist() {
        persistence.saveSurveys(allSurveys);
    }
}
