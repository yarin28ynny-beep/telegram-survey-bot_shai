package com.surveybot.models;

import java.util.HashMap;
import java.util.Map;

/**
 * מצב ההשתתפות של משתמש אחד בתוך סקר ספציפי אחד.
 * <p>
 * זהו בכוונה אובייקט נפרד מ-{@link CommunityUser}: המשתמש עצמו לא "יודע"
 * אם ענה או לא — לפי ההוראות, מצב המענה שייך לסקר, לא למשתמש הגלובלי.
 * לכן לכל סקר יש מיפוי משלו של telegramId -> SurveyParticipation.
 */
public class SurveyParticipation {

    private final long telegramId;
    /** questionIndex -> selectedOptionIndex. שאלה שלא מופיעה כאן = טרם נענתה. */
    private final Map<Integer, Integer> answeredQuestions = new HashMap<>();
    private boolean reminderSent = false;

    public SurveyParticipation(long telegramId) {
        this.telegramId = telegramId;
    }

    public long getTelegramId() {
        return telegramId;
    }

    public void recordAnswer(int questionIndex, int optionIndex) {
        answeredQuestions.put(questionIndex, optionIndex);
    }

    public boolean hasAnswered(int questionIndex) {
        return answeredQuestions.containsKey(questionIndex);
    }

    public int getAnsweredCount() {
        return answeredQuestions.size();
    }

    public Map<Integer, Integer> getAnswers() {
        return answeredQuestions;
    }

    public boolean isComplete(int totalQuestions) {
        return answeredQuestions.size() >= totalQuestions;
    }

    public boolean isReminderSent() {
        return reminderSent;
    }

    public void markReminderSent() {
        reminderSent = true;
    }
}
