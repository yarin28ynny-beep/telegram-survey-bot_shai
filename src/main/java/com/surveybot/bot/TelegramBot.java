package com.surveybot.bot;

import com.surveybot.config.Config;
import com.surveybot.models.CommunityUser;
import com.surveybot.models.Survey;
import com.surveybot.models.SurveyQuestion;
import com.surveybot.services.CommunityService;
import com.surveybot.services.SimpleJson;
import com.surveybot.services.SurveyService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * מריץ לולאת long-polling מול Telegram Bot API, ומתרגם עדכונים נכנסים
 * (הודעות טקסט, לחיצות על כפתורים) לקריאות לשירותי הקהילה/הסקרים.
 * <p>
 * גם מטמיע listener על {@link SurveyService} כדי לדעת מתי לשדר סקר
 * חדש לקהילה או לשלוח תזכורות.
 */
public class TelegramBot {

    private static final char[] HEBREW_OPTION_LETTERS = {'א', 'ב', 'ג', 'ד'};

    private final TelegramClient client;
    private final CommunityService communityService;
    private final SurveyService surveyService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong updateOffset = new AtomicLong(0);
    private Thread pollingThread;

    /** telegramId -> chatId. אנחנו זקוקים לזה כדי לדעת לאן לשלוח סקרים ותזכורות. */
    private final Map<Long, Long> chatIdByUserId = new java.util.concurrent.ConcurrentHashMap<>();

    public TelegramBot(CommunityService communityService, SurveyService surveyService) {
        this.client = new TelegramClient();
        this.communityService = communityService;
        this.surveyService = surveyService;

        this.surveyService.addListener(new SurveyService.Listener() {
            @Override
            public void onSurveyStarted(Survey survey) {
                broadcastSurvey(survey);
            }

            @Override
            public void onAnswerRecorded(Survey survey, long telegramId) {
                // אין פעולה נדרשת מצד הבוט — ה-UI מתעדכן דרך ה-listener שלו.
            }

            @Override
            public void onReminderDue(Survey survey, List<Long> usersToRemind) {
                for (Long userId : usersToRemind) {
                    Long chatId = chatIdByUserId.get(userId);
                    if (chatId != null) {
                        client.sendMessage(chatId,
                                "⏰ תזכורת: הסקר \"" + survey.getTitle() + "\" עדיין פתוח וטרם השלמת אותו. " +
                                "יש לך עוד קצת זמן לענות!");
                    }
                }
            }

            @Override
            public void onSurveyClosed(Survey survey) {
                // הודעת סיום לכלל המשתתפים (לא רק למי שלא השלים).
                for (Long userId : survey.getParticipations().keySet()) {
                    Long chatId = chatIdByUserId.get(userId);
                    if (chatId != null) {
                        client.sendMessage(chatId, "🔒 הסקר \"" + survey.getTitle() + "\" נסגר. תודה על ההשתתפות!");
                    }
                }
            }
        });
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            pollingThread = new Thread(this::pollLoop, "telegram-bot-polling");
            pollingThread.setDaemon(true);
            pollingThread.start();
        }
    }

    public void stop() {
        running.set(false);
        if (pollingThread != null) {
            pollingThread.interrupt();
        }
    }

    private void pollLoop() {
        while (running.get()) {
            try {
                List<Object> updates = client.getUpdates(updateOffset.get());
                for (Object updateObj : updates) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> update = (Map<String, Object>) updateObj;
                    long updateId = SimpleJson.getLong(update, "update_id", 0);
                    updateOffset.set(updateId + 1);
                    handleUpdate(update);
                }
            } catch (Exception e) {
                System.err.println("שגיאה בלולאת ה-polling של הבוט: " + e.getMessage());
                sleepQuietly(3000);
            }
        }
    }

    private void handleUpdate(Map<String, Object> update) {
        Map<String, Object> message = SimpleJson.getObject(update, "message");
        if (message != null) {
            handleIncomingMessage(message);
            return;
        }
        Map<String, Object> callbackQuery = SimpleJson.getObject(update, "callback_query");
        if (callbackQuery != null) {
            handleCallbackQuery(callbackQuery);
        }
    }

    private void handleIncomingMessage(Map<String, Object> message) {
        Map<String, Object> chat = SimpleJson.getObject(message, "chat");
        Map<String, Object> from = SimpleJson.getObject(message, "from");
        if (chat == null || from == null) return;

        long chatId = SimpleJson.getLong(chat, "id", 0);
        long userId = SimpleJson.getLong(from, "id", 0);
        String text = SimpleJson.getString(message, "text", "").trim();

        chatIdByUserId.put(userId, chatId);

        boolean isJoinTrigger = text.equals("/start")
                || text.equalsIgnoreCase("היי")
                || text.equalsIgnoreCase("שלום");

        if (!isJoinTrigger) {
            return; // "כל הודעה אחרת לא תגרום לצירוף משתמש לקהילה" — ומחוץ לכך אין תגובה מוגדרת.
        }

        String username = SimpleJson.getString(from, "username", "");
        String firstName = SimpleJson.getString(from, "first_name", "");
        String lastName = SimpleJson.getString(from, "last_name", "");

        boolean isNewJoin = communityService.addUserIfAbsent(userId, username, firstName, lastName);

        if (isNewJoin) {
            CommunityUser newUser = communityService.getById(userId);
            client.sendMessage(chatId,
                    "🎉 ברוך הבא לקהילה, " + newUser.getDisplayName() + "!\n" +
                    "מעכשיו תקבל כאן סקרים של הקהילה ותוכל להשתתף בהם.");

            String announcement = "👋 חבר חדש הצטרף לקהילה: " + newUser.getDisplayName() + "\n" +
                    "גודל הקהילה כעת: " + communityService.size() + " חברים.";
            for (CommunityUser existingUser : communityService.getAllUsersSortedByJoinTime()) {
                if (existingUser.getTelegramId() == userId) continue; // לא לשלוח למצטרף החדש את ההודעה על עצמו
                Long existingChatId = chatIdByUserId.get(existingUser.getTelegramId());
                if (existingChatId != null) {
                    client.sendMessage(existingChatId, announcement);
                }
            }
        } else {
            client.sendMessage(chatId, "אתה כבר חבר בקהילה. 🙂");
        }
    }

    private void handleCallbackQuery(Map<String, Object> callbackQuery) {
        String callbackId = SimpleJson.getString(callbackQuery, "id", "");
        String data = SimpleJson.getString(callbackQuery, "data", "");
        Map<String, Object> from = SimpleJson.getObject(callbackQuery, "from");
        Map<String, Object> callbackMessage = SimpleJson.getObject(callbackQuery, "message");
        if (from == null || callbackMessage == null) return;

        long userId = SimpleJson.getLong(from, "id", 0);
        long messageId = SimpleJson.getLong(callbackMessage, "message_id", 0);
        Map<String, Object> chat = SimpleJson.getObject(callbackMessage, "chat");
        long chatId = chat == null ? 0 : SimpleJson.getLong(chat, "id", 0);

        // פורמט callback_data: "ans:<surveyId>:<questionIndex>:<optionIndex>"
        String[] parts = data.split(":", 4);
        if (parts.length != 4 || !parts[0].equals("ans")) {
            client.answerCallbackQuery(callbackId, null);
            return;
        }

        String surveyId = parts[1];
        int questionIndex;
        int optionIndex;
        try {
            questionIndex = Integer.parseInt(parts[2]);
            optionIndex = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            client.answerCallbackQuery(callbackId, "שגיאה בעיבוד התשובה.");
            return;
        }

        Survey liveSurvey = surveyService.getLiveSurvey();
        if (liveSurvey == null || !liveSurvey.getId().equals(surveyId)) {
            client.answerCallbackQuery(callbackId, "הסקר הזה כבר אינו פעיל.");
            return;
        }

        try {
            surveyService.recordAnswer(surveyId, userId, questionIndex, optionIndex);
            client.answerCallbackQuery(callbackId, "✅ התשובה נרשמה!");

            var participation = liveSurvey.getParticipation(userId);
            int answered = participation.getAnsweredCount();
            int total = liveSurvey.getQuestions().size();
            String progressNote = answered >= total
                    ? "✅ השלמת את כל השאלות בסקר. תודה!"
                    : "נרשם! השלמת עד כה " + answered + " מתוך " + total + " שאלות.";

            client.editMessageText(chatId, messageId,
                    formatQuestionText(liveSurvey, questionIndex) + "\n\n" + progressNote);

        } catch (IllegalStateException e) {
            client.answerCallbackQuery(callbackId, e.getMessage());
        }
    }

    /** שולח את כל שאלות הסקר, כל אחת כהודעה נפרדת עם כפתורי inline keyboard, לכל משתתף. */
    private void broadcastSurvey(Survey survey) {
        for (Long userId : survey.getParticipations().keySet()) {
            Long chatId = chatIdByUserId.get(userId);
            if (chatId == null) continue; // לא סביר (המשתמש חייב היה לכתוב לבוט כדי להיות בקהילה) אבל ליתר ביטחון

            client.sendMessage(chatId, "📊 סקר חדש מהקהילה: \"" + survey.getTitle() + "\"\n" +
                    "יש לך 5 דקות לענות על " + survey.getQuestions().size() + " שאלות. בהצלחה!");

            for (int qIdx = 0; qIdx < survey.getQuestions().size(); qIdx++) {
                sendQuestion(chatId, survey, qIdx);
            }
        }
    }

    private void sendQuestion(long chatId, Survey survey, int questionIndex) {
        SurveyQuestion question = survey.getQuestions().get(questionIndex);
        String text = formatQuestionText(survey, questionIndex);

        List<List<String[]>> rows = new java.util.ArrayList<>();
        for (int optIdx = 0; optIdx < question.getOptions().size(); optIdx++) {
            String label = HEBREW_OPTION_LETTERS[optIdx] + ". " + question.getOptions().get(optIdx).getText();
            String callbackData = "ans:" + survey.getId() + ":" + questionIndex + ":" + optIdx;

            List<String[]> singleButtonRow = new java.util.ArrayList<>();
            singleButtonRow.add(new String[]{label, callbackData});
            rows.add(singleButtonRow);
        }

        client.sendMessageWithButtons(chatId, text, rows);
    }

    private String formatQuestionText(Survey survey, int questionIndex) {
        SurveyQuestion question = survey.getQuestions().get(questionIndex);
        return "שאלה " + (questionIndex + 1) + " מתוך " + survey.getQuestions().size() + ":\n" + question.getText();
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
