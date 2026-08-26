package com.surveybot.services;

import com.surveybot.config.Config;
import com.surveybot.models.AnswerOption;
import com.surveybot.models.CommunityUser;
import com.surveybot.models.Survey;
import com.surveybot.models.SurveyParticipation;
import com.surveybot.models.SurveyQuestion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * אחראי על שמירה וטעינה של המצב המלא (קהילה + סקרים) לקבצי JSON בדיסק.
 * כל שינוי משמעותי נשמר מיידית כדי שהמערכת תשרוד הפעלה מחדש.
 * <p>
 * נתיבי הקבצים מוזרקים בבנאי (עם ברירת מחדל מ-{@link Config}) ולא נקראים
 * כקבועים סטטיים בתוך כל מתודה — כך שאפשר להריץ את השירות הזה גם מתוך
 * טסטים עם תיקיית נתונים זמנית, בלי לגעת בנתונים האמיתיים של המשתמש.
 */
public class PersistenceService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final String communityFilePath;
    private final String surveysFilePath;

    /** בנאי רגיל — משתמש בנתיבים הקבועים מ-{@link Config}, לשימוש בהרצה רגילה של האפליקציה. */
    public PersistenceService() {
        this(Config.COMMUNITY_FILE, Config.SURVEYS_FILE);
    }

    /** בנאי עם נתיבים מפורשים — בעיקר לצורך טסטים עם תיקיית נתונים זמנית ומבודדת. */
    public PersistenceService(String communityFilePath, String surveysFilePath) {
        this.communityFilePath = communityFilePath;
        this.surveysFilePath = surveysFilePath;
        try {
            Path parent = Paths.get(communityFilePath).getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new RuntimeException("לא ניתן ליצור תיקיית נתונים עבור: " + communityFilePath, e);
        }
    }

    // ==================== קהילה ====================

    public synchronized void saveCommunity(List<CommunityUser> users) {
        List<Object> arr = new ArrayList<>();
        for (CommunityUser u : users) {
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("telegramId", u.getTelegramId());
            obj.put("username", u.getUsername());
            obj.put("firstName", u.getFirstName());
            obj.put("lastName", u.getLastName());
            obj.put("joinedAt", u.getJoinedAt().format(TS));
            arr.add(obj);
        }
        writeFile(communityFilePath, SimpleJson.write(arr));
    }

    public synchronized List<CommunityUser> loadCommunity() {
        List<CommunityUser> result = new ArrayList<>();
        String content = readFileOrNull(communityFilePath);
        if (content == null || content.trim().isEmpty()) {
            return result;
        }
        List<Object> arr = SimpleJson.parseArray(content);
        for (Object item : arr) {
            @SuppressWarnings("unchecked")
            Map<String, Object> obj = (Map<String, Object>) item;
            long id = SimpleJson.getLong(obj, "telegramId", 0);
            String username = SimpleJson.getString(obj, "username", "");
            String firstName = SimpleJson.getString(obj, "firstName", "");
            String lastName = SimpleJson.getString(obj, "lastName", "");
            String joinedAtStr = SimpleJson.getString(obj, "joinedAt", null);
            LocalDateTime joinedAt = joinedAtStr == null ? LocalDateTime.now() : LocalDateTime.parse(joinedAtStr, TS);
            result.add(new CommunityUser(id, username, firstName, lastName, joinedAt));
        }
        return result;
    }

    // ==================== סקרים ====================

    public synchronized void saveSurveys(List<Survey> surveys) {
        List<Object> arr = new ArrayList<>();
        for (Survey survey : surveys) {
            arr.add(surveyToMap(survey));
        }
        writeFile(surveysFilePath, SimpleJson.write(arr));
    }

    public synchronized List<Survey> loadSurveys() {
        List<Survey> result = new ArrayList<>();
        String content = readFileOrNull(surveysFilePath);
        if (content == null || content.trim().isEmpty()) {
            return result;
        }
        List<Object> arr = SimpleJson.parseArray(content);
        for (Object item : arr) {
            @SuppressWarnings("unchecked")
            Map<String, Object> obj = (Map<String, Object>) item;
            result.add(mapToSurvey(obj));
        }
        return result;
    }

    private Map<String, Object> surveyToMap(Survey survey) {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("id", survey.getId());
        obj.put("title", survey.getTitle());
        obj.put("status", survey.getStatus().name());
        obj.put("createdAt", survey.getCreatedAt().format(TS));
        obj.put("scheduledFor", survey.getScheduledFor() == null ? null : survey.getScheduledFor().format(TS));
        obj.put("startedAt", survey.getStartedAt() == null ? null : survey.getStartedAt().format(TS));
        obj.put("closedAt", survey.getClosedAt() == null ? null : survey.getClosedAt().format(TS));

        List<Object> questionsArr = new ArrayList<>();
        for (SurveyQuestion q : survey.getQuestions()) {
            Map<String, Object> qObj = new LinkedHashMap<>();
            qObj.put("text", q.getText());
            List<Object> optionsArr = new ArrayList<>();
            for (AnswerOption opt : q.getOptions()) {
                Map<String, Object> optObj = new LinkedHashMap<>();
                optObj.put("text", opt.getText());
                optObj.put("votes", opt.getVoteCount());
                optionsArr.add(optObj);
            }
            qObj.put("options", optionsArr);
            questionsArr.add(qObj);
        }
        obj.put("questions", questionsArr);

        List<Object> participationsArr = new ArrayList<>();
        for (Map.Entry<Long, SurveyParticipation> entry : survey.getParticipations().entrySet()) {
            SurveyParticipation p = entry.getValue();
            Map<String, Object> pObj = new LinkedHashMap<>();
            pObj.put("telegramId", entry.getKey());
            pObj.put("reminderSent", p.isReminderSent());
            Map<String, Object> answersObj = new LinkedHashMap<>();
            for (Map.Entry<Integer, Integer> ans : p.getAnswers().entrySet()) {
                answersObj.put(String.valueOf(ans.getKey()), ans.getValue());
            }
            pObj.put("answers", answersObj);
            participationsArr.add(pObj);
        }
        obj.put("participations", participationsArr);

        return obj;
    }

    private Survey mapToSurvey(Map<String, Object> obj) {
        String title = SimpleJson.getString(obj, "title", "(ללא כותרת)");
        Survey survey = new Survey(title);
        survey.restoreId(SimpleJson.getString(obj, "id", survey.getId()));

        String createdAtStr = SimpleJson.getString(obj, "createdAt", null);
        if (createdAtStr != null) {
            survey.restoreCreatedAt(LocalDateTime.parse(createdAtStr, TS));
        }

        survey.setStatus(Survey.Status.valueOf(SimpleJson.getString(obj, "status", "DRAFT")));

        String scheduledForStr = SimpleJson.getString(obj, "scheduledFor", null);
        if (scheduledForStr != null) {
            survey.setScheduledFor(LocalDateTime.parse(scheduledForStr, TS));
        }

        String startedAtStr = SimpleJson.getString(obj, "startedAt", null);
        if (startedAtStr != null) {
            survey.restoreStartedAt(LocalDateTime.parse(startedAtStr, TS));
        }

        String closedAtStr = SimpleJson.getString(obj, "closedAt", null);
        if (closedAtStr != null) {
            survey.restoreClosedAt(LocalDateTime.parse(closedAtStr, TS));
        }

        List<Object> questionsArr = SimpleJson.getArray(obj, "questions");
        for (Object qItem : questionsArr) {
            @SuppressWarnings("unchecked")
            Map<String, Object> qObj = (Map<String, Object>) qItem;
            SurveyQuestion question = new SurveyQuestion(SimpleJson.getString(qObj, "text", ""));
            List<Object> optionsArr = SimpleJson.getArray(qObj, "options");
            for (Object optItem : optionsArr) {
                @SuppressWarnings("unchecked")
                Map<String, Object> optObj = (Map<String, Object>) optItem;
                question.addOption(SimpleJson.getString(optObj, "text", ""));
                int votes = SimpleJson.getInt(optObj, "votes", 0);
                AnswerOption addedOption = question.getOptions().get(question.getOptions().size() - 1);
                for (int i = 0; i < votes; i++) {
                    addedOption.incrementVotes();
                }
            }
            survey.addQuestion(question);
        }

        List<Object> participationsArr = SimpleJson.getArray(obj, "participations");
        for (Object pItem : participationsArr) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pObj = (Map<String, Object>) pItem;
            long telegramId = SimpleJson.getLong(pObj, "telegramId", 0);
            survey.getParticipations().put(telegramId, new SurveyParticipation(telegramId));
            SurveyParticipation p = survey.getParticipation(telegramId);

            Map<String, Object> answersObj = SimpleJson.getObject(pObj, "answers");
            if (answersObj != null) {
                for (Map.Entry<String, Object> ans : answersObj.entrySet()) {
                    int qIdx = Integer.parseInt(ans.getKey());
                    int optIdx = ((Number) ans.getValue()).intValue();
                    p.recordAnswer(qIdx, optIdx);
                }
            }
            if (SimpleJson.getBoolean(pObj, "reminderSent", false)) {
                p.markReminderSent();
            }
        }

        return survey;
    }

    // ==================== IO גולמי ====================

    private void writeFile(String path, String content) {
        try {
            Files.write(Paths.get(path), content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("כשל בשמירת קובץ: " + path, e);
        }
    }

    private String readFileOrNull(String path) {
        Path p = Paths.get(path);
        if (!Files.exists(p)) {
            return null;
        }
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("כשל בקריאת קובץ: " + path, e);
        }
    }
}
