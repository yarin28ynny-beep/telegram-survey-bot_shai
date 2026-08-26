package com.surveybot.ui.components;

import com.surveybot.models.AnswerOption;
import com.surveybot.models.Survey;
import com.surveybot.models.SurveyQuestion;
import com.surveybot.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * מסך תוצאות סקר שהסתיים. עבור כל שאלה מציג את אפשרויות התשובה
 * ממוינות מהכי פופולרית להכי פחות, עם בר אחוזים ויזואלי — בהתאם
 * לדרישה המפורשת בהוראות ("ממוינות לפי שכיחות").
 */
public class SurveyResultsPanel extends JPanel {

    public SurveyResultsPanel(Survey survey, Runnable onBackToCreate) {
        setLayout(new BorderLayout(0, Theme.SPACING_MD));
        setOpaque(false);

        add(buildHeader(survey), BorderLayout.NORTH);

        JPanel questionsStack = new JPanel();
        questionsStack.setOpaque(false);
        questionsStack.setLayout(new BoxLayout(questionsStack, BoxLayout.Y_AXIS));

        int questionNumber = 1;
        for (SurveyQuestion question : survey.getQuestions()) {
            questionsStack.add(buildQuestionResultCard(questionNumber, question));
            questionsStack.add(Box.createVerticalStrut(Theme.SPACING_MD));
            questionNumber++;
        }

        JScrollPane scrollPane = new JScrollPane(questionsStack);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        add(scrollPane, BorderLayout.CENTER);

        add(buildFooter(onBackToCreate), BorderLayout.SOUTH);
    }

    private JComponent buildHeader(Survey survey) {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel badge = Theme.pill("🔒 הסקר הסתיים", Theme.BG_MAIN, Theme.TEXT_SECONDARY);
        badge.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JLabel title = Theme.sectionTitle("תוצאות: " + survey.getTitle());
        title.setAlignmentX(Component.RIGHT_ALIGNMENT);

        int completed = survey.getCompletedCount();
        int total = survey.getParticipantCount();
        JLabel subtitle = Theme.subtitle(completed + " מתוך " + total + " משתתפים השלימו את הסקר");
        subtitle.setAlignmentX(Component.RIGHT_ALIGNMENT);

        header.add(badge);
        header.add(Box.createVerticalStrut(Theme.SPACING_SM));
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        return header;
    }

    private JComponent buildQuestionResultCard(int questionNumber, SurveyQuestion question) {
        JPanel card = Theme.card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel questionLabel = new JLabel("<html>שאלה " + questionNumber + ": " + escapeHtml(question.getText()) + "</html>");
        questionLabel.setFont(Theme.h3());
        questionLabel.setForeground(Theme.TEXT_PRIMARY);
        questionLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        card.add(questionLabel);
        card.add(Box.createVerticalStrut(Theme.SPACING_MD));

        int totalVotes = question.getTotalVotes();
        List<AnswerOption> sortedOptions = question.getOptionsSortedByVotesDesc();

        boolean firstRow = true;
        for (AnswerOption option : sortedOptions) {
            if (!firstRow) card.add(Box.createVerticalStrut(Theme.SPACING_SM));
            firstRow = false;
            card.add(buildAnswerBar(option, totalVotes));
        }

        if (totalVotes == 0) {
            JLabel noVotes = new JLabel("לא נאספו הצבעות עבור שאלה זו.");
            noVotes.setFont(Theme.small());
            noVotes.setForeground(Theme.TEXT_SECONDARY);
            noVotes.setAlignmentX(Component.RIGHT_ALIGNMENT);
            card.add(Box.createVerticalStrut(Theme.SPACING_SM));
            card.add(noVotes);
        }

        return card;
    }

    private JComponent buildAnswerBar(AnswerOption option, int totalVotes) {
        double percentage = option.getPercentageOf(totalVotes);

        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setAlignmentX(Component.RIGHT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        JPanel labelRow = new JPanel(new BorderLayout());
        labelRow.setOpaque(false);

        JLabel optionText = new JLabel(option.getText());
        optionText.setFont(Theme.body());
        optionText.setForeground(Theme.TEXT_PRIMARY);

        JLabel percentText = new JLabel(String.format("%.0f%%  (%d קולות)", percentage, option.getVoteCount()));
        percentText.setFont(Theme.smallBold());
        percentText.setForeground(Theme.TEXT_SECONDARY);

        labelRow.add(optionText, BorderLayout.EAST);
        labelRow.add(percentText, BorderLayout.WEST);

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue((int) Math.round(percentage));
        bar.setForeground(Theme.PRIMARY);
        bar.setBackground(new Color(0xEE, 0xF1, 0xF6));
        bar.setBorderPainted(false);
        bar.setPreferredSize(new Dimension(0, 10));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));

        row.add(labelRow);
        row.add(Box.createVerticalStrut(4));
        row.add(bar);
        return row;
    }

    private JComponent buildFooter(Runnable onBackToCreate) {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        footer.setOpaque(false);
        footer.setBorder(Theme.padding(Theme.SPACING_MD, 0));

        JButton backButton = Theme.primaryButton("➕  צור סקר חדש");
        backButton.addActionListener(e -> onBackToCreate.run());
        footer.add(backButton);
        return footer;
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
