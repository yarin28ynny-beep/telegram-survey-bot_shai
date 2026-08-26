package com.surveybot.ui.components;

import com.surveybot.models.CommunityUser;
import com.surveybot.models.Survey;
import com.surveybot.models.SurveyParticipation;
import com.surveybot.services.CommunityService;
import com.surveybot.ui.Theme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * מסך המעקב החי אחר סקר פעיל. מציג:
 * <ul>
 *   <li>Countdown חי וברור עד לסגירת הסקר (5 דקות מתחילתו).</li>
 *   <li>סטטיסטיקות מצטברות: כמה השלימו, כמה בתהליך, כמה טרם התחילו.</li>
 *   <li>טבלת משתתפים מפורטת עם התקדמות אישית של כל אחד.</li>
 * </ul>
 * מתעדכן כל שנייה באמצעות {@link javax.swing.Timer} כדי שה-countdown
 * וההתקדמות תמיד יהיו עדכניים, בהתאם לדרישה המפורשת בהוראות.
 */
public class LiveSurveyPanel extends JPanel {

    private final Survey survey;
    private final CommunityService communityService;

    private final JLabel countdownLabel = new JLabel();
    private final JLabel countdownCaption = new JLabel();
    private final JLabel completedStatLabel = new JLabel();
    private final JLabel inProgressStatLabel = new JLabel();
    private final JLabel notStartedStatLabel = new JLabel();
    private final JLabel toastLabel = new JLabel(" ");

    private final DefaultTableModel tableModel;
    private final javax.swing.Timer tickTimer;

    public LiveSurveyPanel(Survey survey, CommunityService communityService) {
        this.survey = survey;
        this.communityService = communityService;

        setLayout(new BorderLayout(0, Theme.SPACING_MD));
        setOpaque(false);

        add(buildHeader(), BorderLayout.NORTH);

        JPanel centerStack = new JPanel();
        centerStack.setOpaque(false);
        centerStack.setLayout(new BoxLayout(centerStack, BoxLayout.Y_AXIS));

        centerStack.add(buildCountdownCard());
        centerStack.add(Box.createVerticalStrut(Theme.SPACING_MD));
        centerStack.add(buildStatsRow());
        centerStack.add(Box.createVerticalStrut(Theme.SPACING_MD));

        tableModel = new DefaultTableModel(new Object[]{"משתתף", "התקדמות", "סטטוס"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = buildTable();
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));

        JPanel tableCard = Theme.card();
        tableCard.setLayout(new BorderLayout(0, Theme.SPACING_SM));
        JLabel tableTitle = new JLabel("מצב משתתפים");
        tableTitle.setFont(Theme.h3());
        tableCard.add(tableTitle, BorderLayout.NORTH);
        tableCard.add(scrollPane, BorderLayout.CENTER);

        centerStack.add(tableCard);
        add(centerStack, BorderLayout.CENTER);

        toastLabel.setFont(Theme.smallBold());
        toastLabel.setForeground(Theme.WARNING);
        add(toastLabel, BorderLayout.SOUTH);

        refresh();

        tickTimer = new javax.swing.Timer(1000, e -> refresh());
        tickTimer.start();

        addAncestorListenerForCleanup();
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel title = Theme.sectionTitle(survey.getTitle());
        title.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JLabel subtitle = Theme.subtitle("סקר פעיל — עוקב בזמן אמת אחר תשובות המשתתפים");
        subtitle.setAlignmentX(Component.RIGHT_ALIGNMENT);

        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        return header;
    }

    private JComponent buildCountdownCard() {
        JPanel card = Theme.card();
        card.setBackground(Theme.PRIMARY_LIGHT);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        countdownCaption.setFont(Theme.body());
        countdownCaption.setForeground(Theme.TEXT_SECONDARY);
        countdownCaption.setAlignmentX(Component.CENTER_ALIGNMENT);
        countdownCaption.setText("זמן שנותר עד לסגירת הסקר");

        countdownLabel.setFont(Theme.countdown());
        countdownLabel.setForeground(Theme.PRIMARY_DARK);
        countdownLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        countdownLabel.setHorizontalAlignment(SwingConstants.CENTER);

        card.add(countdownCaption);
        card.add(Box.createVerticalStrut(4));
        card.add(countdownLabel);
        return card;
    }

    private JComponent buildStatsRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, Theme.SPACING_MD, 0));
        row.setOpaque(false);

        row.add(buildStatCard("סה\"כ משתתפים", String.valueOf(survey.getParticipantCount()), Theme.TEXT_PRIMARY, Theme.BG_CARD));
        row.add(buildStatCard("השלימו", completedStatLabel, Theme.SUCCESS, Theme.SUCCESS_LIGHT));
        row.add(buildStatCard("בתהליך", inProgressStatLabel, Theme.WARNING, Theme.WARNING_LIGHT));
        row.add(buildStatCard("טרם התחילו", notStartedStatLabel, Theme.TEXT_SECONDARY, Theme.BG_MAIN));

        return row;
    }

    private JComponent buildStatCard(String caption, String staticValue, Color fg, Color bg) {
        JLabel valueLabel = new JLabel(staticValue);
        return buildStatCardInternal(caption, valueLabel, fg, bg);
    }

    private JComponent buildStatCard(String caption, JLabel dynamicValueLabel, Color fg, Color bg) {
        return buildStatCardInternal(caption, dynamicValueLabel, fg, bg);
    }

    private JComponent buildStatCardInternal(String caption, JLabel valueLabel, Color fg, Color bg) {
        JPanel card = Theme.card();
        card.setBackground(bg);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel captionLabel = new JLabel(caption);
        captionLabel.setFont(Theme.small());
        captionLabel.setForeground(Theme.TEXT_SECONDARY);
        captionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        valueLabel.setFont(Theme.h1());
        valueLabel.setForeground(fg);
        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);

        card.add(captionLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(valueLabel);
        return card;
    }

    private JTable buildTable() {
        JTable table = new JTable(tableModel);
        table.setFont(Theme.body());
        table.setRowHeight(34);
        table.setShowGrid(false);
        table.setSelectionBackground(Theme.PRIMARY_LIGHT);
        table.getTableHeader().setFont(Theme.smallBold());
        table.getTableHeader().setBackground(new Color(0xF0, 0xF2, 0xF6));
        table.getTableHeader().setPreferredSize(new Dimension(0, 36));
        return table;
    }

    /** נקרא ע"י ה-hub כשמתקבלת תשובה חדשה, וגם כל שנייה מהטיימר הפנימי. */
    public void refresh() {
        updateCountdown();
        updateStats();
        updateTable();
    }

    public void showReminderToast(int remindedCount) {
        toastLabel.setText("⏰ נשלחה תזכורת ל-" + remindedCount + " משתתפים שטרם סיימו את הסקר.");
        javax.swing.Timer clearTimer = new javax.swing.Timer(6000, e -> toastLabel.setText(" "));
        clearTimer.setRepeats(false);
        clearTimer.start();
    }

    private void updateCountdown() {
        long remainingSeconds = survey.getSecondsRemaining();
        long minutes = remainingSeconds / 60;
        long seconds = remainingSeconds % 60;
        countdownLabel.setText(String.format("%02d:%02d", minutes, seconds));

        if (remainingSeconds <= 30) {
            countdownLabel.setForeground(Theme.DANGER);
        } else if (remainingSeconds <= 90) {
            countdownLabel.setForeground(Theme.WARNING);
        } else {
            countdownLabel.setForeground(Theme.PRIMARY_DARK);
        }
    }

    private void updateStats() {
        completedStatLabel.setText(String.valueOf(survey.getCompletedCount()));
        inProgressStatLabel.setText(String.valueOf(survey.getInProgressCount()));
        notStartedStatLabel.setText(String.valueOf(survey.getNotStartedCount()));
    }

    private void updateTable() {
        int totalQuestions = survey.getQuestions().size();

        // ממיינים לפי התקדמות (הכי הרבה תשובות קודם, כדי שמנהל יראה
        // מיד מי טרם השלים), ובתוך אותה רמת התקדמות ממיינים לפי telegramId
        // כמפתח משני קבוע — כך שהסדר בין שורות "שוות" לא יקפוץ אקראית
        // בין רענון לרענון (שכן HashMap אינו מבטיח סדר יציב מעצמו).
        List<Map.Entry<Long, SurveyParticipation>> entries = new java.util.ArrayList<>(survey.getParticipations().entrySet());
        entries.sort(
                Comparator.<Map.Entry<Long, SurveyParticipation>>comparingInt(e -> e.getValue().getAnsweredCount())
                        .reversed()
                        .thenComparingLong(Map.Entry::getKey)
        );

        tableModel.setRowCount(0);
        for (Map.Entry<Long, SurveyParticipation> entry : entries) {
            SurveyParticipation participation = entry.getValue();
            int answered = participation.getAnsweredCount();

            long telegramId = entry.getKey();
            CommunityUser communityUser = communityService.getById(telegramId);
            String participantName = communityUser != null
                    ? communityUser.getDisplayName()
                    : "משתתף #" + telegramId;
            String progress = answered + "/" + totalQuestions;
            String status;
            if (answered == 0) {
                status = "⏳ טרם התחיל";
            } else if (answered < totalQuestions) {
                status = "🔄 בתהליך";
            } else {
                status = "✅ השלים";
            }

            tableModel.addRow(new Object[]{participantName, progress, status});
        }
    }

    /** עוצר את הטיימר הפנימי כשה-panel יורד מהמסך, כדי לא לדלוף threads. */
    private void addAncestorListenerForCleanup() {
        addHierarchyListener(e -> {
            if (!isDisplayable() && tickTimer.isRunning()) {
                tickTimer.stop();
            }
        });
    }
}
