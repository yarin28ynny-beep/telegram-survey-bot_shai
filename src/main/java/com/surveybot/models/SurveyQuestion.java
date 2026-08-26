package com.surveybot.models;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * שאלה בתוך סקר, עם 2-4 אפשרויות תשובה (ראו Config.MIN_ANSWERS/MAX_ANSWERS).
 */
public class SurveyQuestion {

    private final String text;
    private final List<AnswerOption> options;

    public SurveyQuestion(String text) {
        this.text = text;
        this.options = new ArrayList<>();
    }

    public String getText() {
        return text;
    }

    public List<AnswerOption> getOptions() {
        return options;
    }

    public void addOption(String optionText) {
        options.add(new AnswerOption(optionText));
    }

    public int getTotalVotes() {
        int sum = 0;
        for (AnswerOption o : options) {
            sum += o.getVoteCount();
        }
        return sum;
    }

    /** אפשרויות התשובה ממוינות מהכי הרבה קולות להכי מעט — לתצוגת תוצאות. */
    public List<AnswerOption> getOptionsSortedByVotesDesc() {
        List<AnswerOption> copy = new ArrayList<>(options);
        copy.sort(Comparator.comparingInt(AnswerOption::getVoteCount).reversed());
        return copy;
    }

    public void registerVote(int optionIndex) {
        if (optionIndex < 0 || optionIndex >= options.size()) {
            throw new IllegalArgumentException("אינדקס תשובה לא תקין: " + optionIndex);
        }
        options.get(optionIndex).incrementVotes();
    }
}
