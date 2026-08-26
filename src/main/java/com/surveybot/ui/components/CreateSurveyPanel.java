package com.surveybot.ui.components;

import com.surveybot.config.Config;
import com.surveybot.models.SurveyQuestion;
import com.surveybot.services.ChatGptClient;
import com.surveybot.services.CommunityService;
import com.surveybot.services.SurveyService;
import com.surveybot.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * מסך יצירת סקר. מציע שתי דרכי יצירה בלשוניות ברורות (ידנית / ChatGPT),
 * שדה עיכוב שליחה, ובדיקות תקינות מלאות לפני שליחה — עם הודעות שגיאה
 * והצלחה ברורות בהתאם לדגש שבהוראות על משוב איכותי למשתמש.
 */
public class CreateSurveyPanel extends JPanel {

    private final CommunityService communityService;
    private final SurveyService surveyService;
    private final CommunityService.Listener communityListener;

    private final JTextField titleField = new JTextField();
    private final JPanel questionsContainer = new JPanel();
    private final List<QuestionEditorRow> questionRows = new ArrayList<>();
    private final JLabel questionCountHint = new JLabel();
    private final JButton addQuestionButton;

    private final JTextField aiTopicField = new JTextField();
    private final JSpinner aiQuestionCountSpinner = new JSpinner(
            new SpinnerNumberModel(Config.MAX_QUESTIONS, Config.MIN_QUESTIONS, Config.MAX_QUESTIONS, 1));
    private final JSpinner aiAnswerCountSpinner = new JSpinner(
            new SpinnerNumberModel(3, Config.MIN_ANSWERS, Config.MAX_ANSWERS, 1));
    private final JLabel aiStatusLabel = new JLabel(" ");

    private final JSpinner delayMinutesSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 120, 1));
    private final JLabel communityStatusLabel = new JLabel();

    private final JTabbedPane methodTabs = new JTabbedPane();

    public CreateSurveyPanel(CommunityService communityService, SurveyService surveyService) {
        this.communityService = communityService;
        this.surveyService = surveyService;

        setLayout(new BorderLayout(0, Theme.SPACING_MD));
        setOpaque(false);

        add(buildHeader(), BorderLayout.NORTH);

        JPanel formStack = new JPanel();
        formStack.setOpaque(false);
        formStack.setLayout(new BoxLayout(formStack, BoxLayout.Y_AXIS));

        formStack.add(buildTitleCard());
        formStack.add(Box.createVerticalStrut(Theme.SPACING_MD));

        this.addQuestionButton = Theme.secondaryButton("➕ הוסף שאלה");
        addQuestionButton.addActionListener(e -> addQuestionRow());

        methodTabs.setFont(Theme.bodyBold());
        methodTabs.addTab("✍️  יצירה ידנית", buildManualTab());
        methodTabs.addTab("🤖  יצירה עם ChatGPT", buildAiTab());
        formStack.add(methodTabs);
        formStack.add(Box.createVerticalStrut(Theme.SPACING_MD));

        formStack.add(buildDeliveryCard());
        formStack.add(Box.createVerticalStrut(Theme.SPACING_MD));
        formStack.add(buildSubmitRow());

        JScrollPane scrollPane = new JScrollPane(formStack);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        add(scrollPane, BorderLayout.CENTER);

        addQuestionRow(); // מתחילים עם שאלה אחת ריקה, כדי לא להציג טופס ריק לגמרי
        refreshCommunityStatus();

        // עדכון חי אם מישהו מצטרף לקהילה בזמן שהמשתמש נמצא במסך הזה —
        // כך שהוא רואה מיד "עכשיו יש מספיק חברים" בלי לצטרך ללחוץ שלח כדי לגלות.
        // ה-listener מוסר אוטומטית כשה-panel יורד מהמסך (ר' addHierarchyListener
        // למטה), כי מסך זה נבנה מחדש בכל מחזור יצירת-סקר ואחרת היינו דולפים
        // listener ישן בכל פעם.
        this.communityListener = (user, newSize) -> SwingUtilities.invokeLater(this::refreshCommunityStatus);
        communityService.addListener(communityListener);

        addHierarchyListener(e -> {
            if (!isDisplayable()) {
                communityService.removeListener(communityListener);
            }
        });
    }

    // ==================== כותרת עליונה ====================

    private JComponent buildHeader() {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = Theme.sectionTitle("יצירת סקר חדש");
        JLabel subtitle = Theme.subtitle("הגדר שאלות, בחר מועד שליחה, ועקוב אחרי הסקר בזמן אמת");
        title.setAlignmentX(Component.RIGHT_ALIGNMENT);
        subtitle.setAlignmentX(Component.RIGHT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        return header;
    }

    // ==================== כרטיס כותרת הסקר ====================

    private JComponent buildTitleCard() {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 6));

        JLabel label = new JLabel("כותרת הסקר:");
        label.setFont(Theme.smallBold());
        label.setForeground(Theme.TEXT_SECONDARY);

        titleField.setFont(Theme.body());

        card.add(label, BorderLayout.NORTH);
        card.add(titleField, BorderLayout.CENTER);
        return card;
    }

    // ==================== לשונית ידנית ====================

    private JComponent buildManualTab() {
        JPanel wrapper = new JPanel(new BorderLayout(0, Theme.SPACING_SM));
        wrapper.setOpaque(false);
        wrapper.setBorder(Theme.padding(Theme.SPACING_MD, 0));

        questionsContainer.setOpaque(false);
        questionsContainer.setLayout(new BoxLayout(questionsContainer, BoxLayout.Y_AXIS));

        JPanel controlsRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACING_SM, 0));
        controlsRow.setOpaque(false);
        questionCountHint.setFont(Theme.small());
        questionCountHint.setForeground(Theme.TEXT_SECONDARY);
        controlsRow.add(questionCountHint);
        controlsRow.add(addQuestionButton);

        wrapper.add(questionsContainer, BorderLayout.CENTER);
        wrapper.add(controlsRow, BorderLayout.SOUTH);
        return wrapper;
    }

    private void addQuestionRow() {
        if (questionRows.size() >= Config.MAX_QUESTIONS) return;

        int number = questionRows.size() + 1;
        QuestionEditorRow[] holder = new QuestionEditorRow[1];
        QuestionEditorRow row = new QuestionEditorRow(number, () -> removeQuestionRow(holder[0]), null);
        holder[0] = row;

        questionRows.add(row);
        questionsContainer.add(row);
        questionsContainer.add(Box.createVerticalStrut(Theme.SPACING_SM));
        questionsContainer.revalidate();
        questionsContainer.repaint();
        renumberQuestions();
    }

    private void removeQuestionRow(QuestionEditorRow row) {
        if (row == null) return;
        if (questionRows.size() <= Config.MIN_QUESTIONS) {
            JOptionPane.showMessageDialog(this,
                    "חייבת להיות לפחות שאלה אחת בסקר.",
                    "לא ניתן להסיר", JOptionPane.WARNING_MESSAGE);
            return;
        }
        questionRows.remove(row);
        questionsContainer.remove(row);
        questionsContainer.revalidate();
        questionsContainer.repaint();
        renumberQuestions();
    }

    private void renumberQuestions() {
        for (int i = 0; i < questionRows.size(); i++) {
            questionRows.get(i).updateQuestionNumber(i + 1);
        }
        questionCountHint.setText(questionRows.size() + " מתוך " + Config.MAX_QUESTIONS + " שאלות מקסימום");
        addQuestionButton.setEnabled(questionRows.size() < Config.MAX_QUESTIONS);
    }

    // ==================== לשונית ChatGPT ====================

    private JComponent buildAiTab() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBorder(Theme.padding(Theme.SPACING_MD, 0));

        JLabel instructions = new JLabel(
                "<html>תאר נושא כללי לסקר (למשל: \"העדפות טכנולוגיות בקרב מהנדסי תוכנה\"), " +
                "וה-AI ייצור עבורך שאלות ואפשרויות תשובה מתאימות — שיוצגו כאן לעריכה ואישור לפני שליחה.</html>"
        );
        instructions.setFont(Theme.body());
        instructions.setForeground(Theme.TEXT_SECONDARY);
        instructions.setAlignmentX(Component.RIGHT_ALIGNMENT);

        aiTopicField.setFont(Theme.body());
        aiTopicField.setAlignmentX(Component.RIGHT_ALIGNMENT);
        aiTopicField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        JPanel countsRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACING_MD, 0));
        countsRow.setOpaque(false);
        countsRow.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JPanel questionCountBlock = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        questionCountBlock.setOpaque(false);
        JLabel questionCountLabel = new JLabel("מספר שאלות:");
        questionCountLabel.setFont(Theme.small());
        questionCountLabel.setForeground(Theme.TEXT_SECONDARY);
        aiQuestionCountSpinner.setFont(Theme.body());
        aiQuestionCountSpinner.getEditor().setPreferredSize(new Dimension(50, 28));
        questionCountBlock.add(questionCountLabel);
        questionCountBlock.add(aiQuestionCountSpinner);

        JPanel answerCountBlock = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        answerCountBlock.setOpaque(false);
        JLabel answerCountLabel = new JLabel("אפשרויות לשאלה:");
        answerCountLabel.setFont(Theme.small());
        answerCountLabel.setForeground(Theme.TEXT_SECONDARY);
        aiAnswerCountSpinner.setFont(Theme.body());
        aiAnswerCountSpinner.getEditor().setPreferredSize(new Dimension(50, 28));
        answerCountBlock.add(answerCountLabel);
        answerCountBlock.add(aiAnswerCountSpinner);

        countsRow.add(questionCountBlock);
        countsRow.add(answerCountBlock);

        JButton generateButton = Theme.primaryButton("✨ צור שאלות עם ChatGPT");
        generateButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
        generateButton.addActionListener(e -> generateQuestionsWithAi(generateButton));

        aiStatusLabel.setFont(Theme.small());
        aiStatusLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        wrapper.add(instructions);
        wrapper.add(Box.createVerticalStrut(Theme.SPACING_MD));
        wrapper.add(aiTopicField);
        wrapper.add(Box.createVerticalStrut(Theme.SPACING_SM));
        wrapper.add(countsRow);
        wrapper.add(Box.createVerticalStrut(Theme.SPACING_SM));

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonRow.setOpaque(false);
        buttonRow.setAlignmentX(Component.RIGHT_ALIGNMENT);
        buttonRow.add(generateButton);
        wrapper.add(buttonRow);

        wrapper.add(Box.createVerticalStrut(Theme.SPACING_SM));
        wrapper.add(aiStatusLabel);

        return wrapper;
    }

    private void generateQuestionsWithAi(JButton triggerButton) {
        String topic = aiTopicField.getText().trim();
        if (topic.isEmpty()) {
            aiStatusLabel.setForeground(Theme.DANGER);
            aiStatusLabel.setText("יש להזין נושא לפני היצירה.");
            return;
        }

        triggerButton.setEnabled(false);
        aiStatusLabel.setForeground(Theme.TEXT_SECONDARY);
        aiStatusLabel.setText("⏳ פונה ל-ChatGPT, זה עשוי לקחת כמה שניות...");

        int desiredQuestions = (Integer) aiQuestionCountSpinner.getValue();
        int desiredAnswers = (Integer) aiAnswerCountSpinner.getValue();

        SwingWorker<List<SurveyQuestion>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<SurveyQuestion> doInBackground() throws Exception {
                ChatGptClient client = new ChatGptClient();
                return client.generateQuestions(topic, desiredQuestions, desiredAnswers);
            }

            @Override
            protected void done() {
                triggerButton.setEnabled(true);
                try {
                    List<SurveyQuestion> generated = get();
                    applyGeneratedQuestions(generated);
                    aiStatusLabel.setForeground(Theme.SUCCESS);
                    aiStatusLabel.setText("✅ נוצרו " + generated.size() + " שאלות בהצלחה. עברו ללשונית \"יצירה ידנית\" לצפייה ועריכה.");
                    methodTabs.setSelectedIndex(0);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    aiStatusLabel.setForeground(Theme.DANGER);
                    aiStatusLabel.setText("❌ " + cause.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void applyGeneratedQuestions(List<SurveyQuestion> generated) {
        // מנקים את השאלות הקיימות ובונים מחדש לפי מה שהתקבל מה-AI.
        for (QuestionEditorRow row : new ArrayList<>(questionRows)) {
            questionsContainer.remove(row);
        }
        questionRows.clear();

        for (SurveyQuestion q : generated) {
            int number = questionRows.size() + 1;
            QuestionEditorRow[] holder = new QuestionEditorRow[1];
            QuestionEditorRow row = new QuestionEditorRow(number, () -> removeQuestionRow(holder[0]), null);
            holder[0] = row;
            row.populateFrom(q);
            questionRows.add(row);
            questionsContainer.add(row);
            questionsContainer.add(Box.createVerticalStrut(Theme.SPACING_SM));
        }

        questionsContainer.revalidate();
        questionsContainer.repaint();
        renumberQuestions();
    }

    // ==================== כרטיס תזמון שליחה ====================

    private JComponent buildDeliveryCard() {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout());

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel label = new JLabel("מועד שליחת הסקר:");
        label.setFont(Theme.smallBold());
        label.setForeground(Theme.TEXT_SECONDARY);
        JLabel hint = new JLabel("0 = שליחה מיידית לכל חברי הקהילה. ערך גבוה מ-0 = עיכוב במספר הדקות שצוין.");
        hint.setFont(Theme.small());
        hint.setForeground(Theme.TEXT_SECONDARY);
        left.add(label);
        left.add(Box.createVerticalStrut(4));
        left.add(hint);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACING_SM, 0));
        right.setOpaque(false);
        delayMinutesSpinner.setFont(Theme.body());
        JComponent editor = delayMinutesSpinner.getEditor();
        editor.setPreferredSize(new Dimension(70, 30));
        JLabel minutesLabel = new JLabel("דקות עיכוב");
        minutesLabel.setFont(Theme.body());
        right.add(minutesLabel);
        right.add(delayMinutesSpinner);

        card.add(left, BorderLayout.EAST);
        card.add(right, BorderLayout.WEST);
        return card;
    }

    // ==================== שורת שליחה ====================

    private JComponent buildSubmitRow() {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);

        communityStatusLabel.setFont(Theme.small());
        row.add(communityStatusLabel, BorderLayout.WEST);

        JButton submitButton = Theme.primaryButton("🚀  שלח את הסקר");
        submitButton.addActionListener(e -> submitSurvey());
        row.add(submitButton, BorderLayout.EAST);

        return row;
    }

    private void refreshCommunityStatus() {
        int size = communityService.size();
        if (size < Config.MIN_COMMUNITY_SIZE_TO_START) {
            communityStatusLabel.setForeground(Theme.DANGER);
            communityStatusLabel.setText("⚠ יש " + size + " חברי קהילה בלבד — נדרשים לפחות " +
                    Config.MIN_COMMUNITY_SIZE_TO_START + " כדי להתחיל סקר.");
        } else {
            communityStatusLabel.setForeground(Theme.SUCCESS);
            communityStatusLabel.setText("✓ " + size + " חברי קהילה — ניתן להתחיל סקר.");
        }
    }

    private void submitSurvey() {
        refreshCommunityStatus();

        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            showValidationError("יש להזין כותרת לסקר.");
            return;
        }

        List<SurveyQuestion> questions = new ArrayList<>();
        try {
            for (QuestionEditorRow row : questionRows) {
                questions.add(row.buildValidatedQuestion());
            }
        } catch (IllegalStateException validationError) {
            showValidationError(validationError.getMessage());
            return;
        }

        int delayMinutes = (Integer) delayMinutesSpinner.getValue();
        long delaySeconds = delayMinutes * 60L;

        try {
            surveyService.createAndScheduleSurvey(title, questions, delaySeconds);
            String successMessage = delaySeconds > 0
                    ? "הסקר נוצר בהצלחה ויישלח בעוד " + delayMinutes + " דקות."
                    : "הסקר נוצר ונשלח כעת לכל חברי הקהילה!";
            JOptionPane.showMessageDialog(this, successMessage, "הסקר נוצר בהצלחה", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalStateException businessError) {
            showValidationError(businessError.getMessage());
        }
    }

    private void showValidationError(String message) {
        JOptionPane.showMessageDialog(this, message, "לא ניתן לשלוח את הסקר", JOptionPane.ERROR_MESSAGE);
    }
}
