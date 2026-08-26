package com.surveybot.ui.pages;

import com.surveybot.models.Survey;
import com.surveybot.services.CommunityService;
import com.surveybot.services.SurveyService;
import com.surveybot.ui.Theme;
import com.surveybot.ui.components.CreateSurveyPanel;
import com.surveybot.ui.components.LiveSurveyPanel;
import com.surveybot.ui.components.SurveyResultsPanel;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * דף "סקרים" — מחליף אוטומטית בין שלושה מצבים בהתאם למחזור החיים
 * של הסקר, כדי שהמשתמש תמיד יראה בדיוק את המסך הרלוונטי:
 * <ol>
 *   <li>{@link CreateSurveyPanel} — כברירת מחדל, כשאין סקר חי.</li>
 *   <li>{@link LiveSurveyPanel} — ברגע שסקר יצא לדרך, עם countdown חי.</li>
 *   <li>{@link SurveyResultsPanel} — ברגע שהסקר נסגר, עם תוצאות.</li>
 * </ol>
 * <p>
 * במכוון לא נעשה שימוש ב-{@link CardLayout} לניהול המעברים: מכיוון
 * שכל מסך (בפרט מסך היצירה) חייב להיבנות מחדש בכל פעם ולא להישאר
 * "זכור" עם מצב ישן, פשוט יותר ובטוח יותר לנהל תצוגה יחידה בלבד
 * באמצעות {@code removeAll()} + {@code add()} מפורש.
 */
public class SurveyHubPage extends JPanel {

    private final CommunityService communityService;
    private final SurveyService surveyService;

    private LiveSurveyPanel currentLivePanel;

    public SurveyHubPage(CommunityService communityService, SurveyService surveyService) {
        this.communityService = communityService;
        this.surveyService = surveyService;

        setLayout(new BorderLayout());
        setBackground(Theme.BG_MAIN);
        setBorder(Theme.padding(Theme.SPACING_LG));

        surveyService.addListener(new SurveyService.Listener() {
            @Override
            public void onSurveyStarted(Survey survey) {
                SwingUtilities.invokeLater(() -> showLiveView(survey));
            }

            @Override
            public void onAnswerRecorded(Survey survey, long telegramId) {
                SwingUtilities.invokeLater(() -> {
                    if (currentLivePanel != null) currentLivePanel.refresh();
                });
            }

            @Override
            public void onReminderDue(Survey survey, List<Long> usersToRemind) {
                SwingUtilities.invokeLater(() -> {
                    if (currentLivePanel != null) currentLivePanel.showReminderToast(usersToRemind.size());
                });
            }

            @Override
            public void onSurveyClosed(Survey survey) {
                SwingUtilities.invokeLater(() -> showResultsView(survey));
            }
        });

        if (surveyService.hasLiveSurvey()) {
            showLiveView(surveyService.getLiveSurvey());
        } else {
            showCreateView();
        }
    }

    private void showCreateView() {
        currentLivePanel = null;
        CreateSurveyPanel createPanel = new CreateSurveyPanel(communityService, surveyService);
        replaceContent(createPanel);
    }

    private void showLiveView(Survey survey) {
        currentLivePanel = new LiveSurveyPanel(survey, communityService);
        replaceContent(currentLivePanel);
    }

    private void showResultsView(Survey survey) {
        currentLivePanel = null;
        SurveyResultsPanel resultsPanel = new SurveyResultsPanel(survey, this::showCreateView);
        replaceContent(resultsPanel);
    }

    private void replaceContent(JComponent newContent) {
        removeAll();
        add(newContent, BorderLayout.CENTER);
        revalidate();
        repaint();
    }
}
