package com.surveybot.models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * סקר בודד. מחזיק את השאלות, את רשימת המשתתפים (שמשוייכת לסקר עצמו ולא
 * לקהילה הגלובלית — ראו הוראות סעיף 5), ואת מצב החיים שלו (Status).
 */
public class Survey {

    public enum Status {
        DRAFT,       // בעריכה, טרם נשלח
        SCHEDULED,   // מתוזמן לשליחה מאוחרת, יש countdown פעיל
        ACTIVE,      // נשלח לקהילה, אוסף תשובות
        CLOSED       // הסתיים — או שכולם ענו, או שחלפו 5 דקות
    }

    private String id;
    private final String title;
    private final List<SurveyQuestion> questions = new ArrayList<>();

    /** telegramId -> SurveyParticipation. סדר הוספה נשמר לתצוגה נוחה. */
    private final Map<Long, SurveyParticipation> participations = new LinkedHashMap<>();

    private LocalDateTime createdAt;
    private LocalDateTime scheduledFor;   // null אם אין עיכוב
    private LocalDateTime startedAt;      // null עד שהסקר בפועל יוצא לדרך
    private LocalDateTime closedAt;       // null עד שהסקר נסגר

    private Status status = Status.DRAFT;

    /** יצירת סקר חדש (המסלול הרגיל, דרך ה-UI). */
    public Survey(String title) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.createdAt = LocalDateTime.now();
    }

    // ---------- זהות ותוכן ----------

    public String getId() {
        return id;
    }

    /** משמש אך ורק ל-persistence layer בעת שחזור סקר קיים מהדיסק. */
    public void restoreId(String id) {
        this.id = id;
    }

    /** משמש אך ורק ל-persistence layer בעת שחזור סקר קיים מהדיסק. */
    public void restoreCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getTitle() {
        return title;
    }

    public List<SurveyQuestion> getQuestions() {
        return questions;
    }

    public void addQuestion(SurveyQuestion q) {
        questions.add(q);
    }

    // ---------- משתתפים (שייכים לסקר, לא לקהילה הגלובלית) ----------

    /** קובע את קבוצת המשתתפים בהתאם לחברי הקהילה שקיימים ברגע תחילת הסקר. */
    public void initializeParticipants(List<Long> telegramIds) {
        for (Long id : telegramIds) {
            participations.putIfAbsent(id, new SurveyParticipation(id));
        }
    }

    public Map<Long, SurveyParticipation> getParticipations() {
        return participations;
    }

    public SurveyParticipation getParticipation(long telegramId) {
        return participations.get(telegramId);
    }

    public boolean isParticipant(long telegramId) {
        return participations.containsKey(telegramId);
    }

    public int getParticipantCount() {
        return participations.size();
    }

    public int getCompletedCount() {
        int count = 0;
        for (SurveyParticipation p : participations.values()) {
            if (p.isComplete(questions.size())) count++;
        }
        return count;
    }

    public int getInProgressCount() {
        int count = 0;
        for (SurveyParticipation p : participations.values()) {
            if (p.getAnsweredCount() > 0 && !p.isComplete(questions.size())) count++;
        }
        return count;
    }

    public int getNotStartedCount() {
        int count = 0;
        for (SurveyParticipation p : participations.values()) {
            if (p.getAnsweredCount() == 0) count++;
        }
        return count;
    }

    public boolean isEveryoneComplete() {
        if (participations.isEmpty()) return false;
        for (SurveyParticipation p : participations.values()) {
            if (!p.isComplete(questions.size())) return false;
        }
        return true;
    }

    // ---------- תשובות ----------

    public void recordAnswer(long telegramId, int questionIndex, int optionIndex) {
        SurveyParticipation p = participations.get(telegramId);
        if (p == null) {
            throw new IllegalStateException("המשתמש אינו משתתף בסקר זה");
        }
        p.recordAnswer(questionIndex, optionIndex);
        questions.get(questionIndex).registerVote(optionIndex);
    }

    // ---------- זמנים וסטטוס ----------

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getScheduledFor() {
        return scheduledFor;
    }

    public void setScheduledFor(LocalDateTime scheduledFor) {
        this.scheduledFor = scheduledFor;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void markStarted() {
        this.startedAt = LocalDateTime.now();
        this.status = Status.ACTIVE;
    }

    /** משמש אך ורק ל-persistence layer בעת שחזור סקר קיים מהדיסק. */
    public void restoreStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void markClosed() {
        this.closedAt = LocalDateTime.now();
        this.status = Status.CLOSED;
    }

    /** משמש אך ורק ל-persistence layer בעת שחזור סקר קיים מהדיסק. */
    public void restoreClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDateTime getDeadline() {
        if (startedAt == null) return null;
        return startedAt.plusSeconds(com.surveybot.config.Config.SURVEY_DURATION_SECONDS);
    }

    public LocalDateTime getReminderTime() {
        if (startedAt == null) return null;
        return startedAt.plusSeconds(com.surveybot.config.Config.REMINDER_AFTER_SECONDS);
    }

    public long getSecondsRemaining() {
        LocalDateTime deadline = getDeadline();
        if (deadline == null) return com.surveybot.config.Config.SURVEY_DURATION_SECONDS;
        long remaining = java.time.Duration.between(LocalDateTime.now(), deadline).getSeconds();
        return Math.max(0, remaining);
    }
}
