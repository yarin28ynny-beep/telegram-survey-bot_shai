package com.surveybot.models;

/**
 * אפשרות תשובה בודדת בתוך שאלה, כולל ספירת ההצבעות שקיבלה.
 */
public class AnswerOption {

    private final String text;
    private int voteCount;

    public AnswerOption(String text) {
        this.text = text;
        this.voteCount = 0;
    }

    public String getText() {
        return text;
    }

    public int getVoteCount() {
        return voteCount;
    }

    public void incrementVotes() {
        voteCount++;
    }

    public double getPercentageOf(int totalVotesForQuestion) {
        if (totalVotesForQuestion <= 0) {
            return 0.0;
        }
        return (voteCount * 100.0) / totalVotesForQuestion;
    }
}
