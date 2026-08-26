package com.surveybot.ui.components;

import com.surveybot.config.Config;
import com.surveybot.models.SurveyQuestion;
import com.surveybot.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * כרטיס עריכה לשאלה בודדת: שדה טקסט לשאלה עצמה, ורשימת שדות תשובה
 * דינמית (2 עד 4, ניתן להוסיף/להסיר) — בדיוק בטווח שההוראות דורשות.
 */
public class QuestionEditorRow extends JPanel {

    private int questionNumber;
    private final Runnable onRemoveRequested;
    private final Runnable onChanged;
    private final JLabel titleLabel;

    private final JTextField questionField;
    private final JPanel optionsContainer;
    private final List<JTextField> optionFields = new ArrayList<>();
    private final JButton addOptionButton;
    private final JLabel optionCountHint;

    public QuestionEditorRow(int questionNumber, Runnable onRemoveRequested, Runnable onChanged) {
        this.questionNumber = questionNumber;
        this.onRemoveRequested = onRemoveRequested;
        this.onChanged = onChanged;

        setLayout(new BorderLayout(0, Theme.SPACING_SM));
        setBackground(Theme.BG_CARD);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                Theme.padding(Theme.SPACING_MD)
        ));

        titleLabel = new JLabel("שאלה " + questionNumber);
        titleLabel.setFont(Theme.h3());
        titleLabel.setForeground(Theme.PRIMARY_DARK);

        add(buildHeaderRow(), BorderLayout.NORTH);

        questionField = new JTextField();
        questionField.setFont(Theme.body());
        questionField.putClientProperty("JTextField.placeholderText", "נוסח השאלה");

        JPanel questionBlock = new JPanel(new BorderLayout(0, 4));
        questionBlock.setOpaque(false);
        JLabel questionLabel = new JLabel("טקסט השאלה:");
        questionLabel.setFont(Theme.smallBold());
        questionLabel.setForeground(Theme.TEXT_SECONDARY);
        questionBlock.add(questionLabel, BorderLayout.NORTH);
        questionBlock.add(questionField, BorderLayout.CENTER);

        optionsContainer = new JPanel();
        optionsContainer.setOpaque(false);
        optionsContainer.setLayout(new BoxLayout(optionsContainer, BoxLayout.Y_AXIS));

        optionCountHint = new JLabel();
        optionCountHint.setFont(Theme.small());
        optionCountHint.setForeground(Theme.TEXT_SECONDARY);

        addOptionButton = Theme.secondaryButton("➕ הוסף אפשרות תשובה");
        addOptionButton.addActionListener(e -> addOptionField(""));

        JPanel optionsBlock = new JPanel();
        optionsBlock.setOpaque(false);
        optionsBlock.setLayout(new BoxLayout(optionsBlock, BoxLayout.Y_AXIS));
        JLabel optionsLabel = new JLabel("אפשרויות תשובה (2 עד 4):");
        optionsLabel.setFont(Theme.smallBold());
        optionsLabel.setForeground(Theme.TEXT_SECONDARY);
        optionsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        optionsContainer.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        addRow.setOpaque(false);
        addRow.add(addOptionButton);
        addRow.add(Box.createHorizontalStrut(Theme.SPACING_SM));
        addRow.add(optionCountHint);
        addRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        optionsBlock.add(optionsLabel);
        optionsBlock.add(Box.createVerticalStrut(4));
        optionsBlock.add(optionsContainer);
        optionsBlock.add(Box.createVerticalStrut(Theme.SPACING_SM));
        optionsBlock.add(addRow);

        JPanel centerStack = new JPanel();
        centerStack.setOpaque(false);
        centerStack.setLayout(new BoxLayout(centerStack, BoxLayout.Y_AXIS));
        centerStack.add(questionBlock);
        centerStack.add(Box.createVerticalStrut(Theme.SPACING_MD));
        centerStack.add(optionsBlock);

        add(centerStack, BorderLayout.CENTER);

        // מתחילים עם 2 שדות תשובה ריקים — המינימום הנדרש.
        addOptionField("");
        addOptionField("");
        updateOptionControlsState();
    }

    private JComponent buildHeaderRow() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        header.add(titleLabel, BorderLayout.EAST);

        JButton removeButton = new JButton("✕ הסר שאלה");
        removeButton.setFont(Theme.small());
        removeButton.setForeground(Theme.DANGER);
        removeButton.setBorderPainted(false);
        removeButton.setContentAreaFilled(false);
        removeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        removeButton.addActionListener(e -> onRemoveRequested.run());
        header.add(removeButton, BorderLayout.WEST);

        return header;
    }

    private void addOptionField(String initialText) {
        if (optionFields.size() >= Config.MAX_ANSWERS) return;

        JTextField field = new JTextField(initialText);
        field.setFont(Theme.body());
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        optionFields.add(field);

        JPanel row = new JPanel(new BorderLayout(Theme.SPACING_SM, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        char letter = (char) ('א' + (optionFields.size() - 1));
        JLabel prefix = new JLabel(letter + ".");
        prefix.setFont(Theme.bodyBold());
        prefix.setForeground(Theme.TEXT_SECONDARY);
        prefix.setPreferredSize(new Dimension(20, 20));

        row.add(prefix, BorderLayout.EAST);
        row.add(field, BorderLayout.CENTER);

        if (optionFields.size() > Config.MIN_ANSWERS) {
            JButton removeOptionButton = new JButton("✕");
            removeOptionButton.setFont(Theme.small());
            removeOptionButton.setForeground(Theme.TEXT_SECONDARY);
            removeOptionButton.setBorderPainted(false);
            removeOptionButton.setContentAreaFilled(false);
            removeOptionButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            removeOptionButton.addActionListener(e -> removeOptionRow(row, field));
            row.add(removeOptionButton, BorderLayout.WEST);
        }

        optionsContainer.add(row);
        optionsContainer.add(Box.createVerticalStrut(6));
        optionsContainer.revalidate();
        optionsContainer.repaint();
        updateOptionControlsState();
        if (onChanged != null) onChanged.run();
    }

    private void removeOptionRow(JPanel row, JTextField field) {
        optionFields.remove(field);
        optionsContainer.remove(row);
        rebuildOptionLetters();
        optionsContainer.revalidate();
        optionsContainer.repaint();
        updateOptionControlsState();
        if (onChanged != null) onChanged.run();
    }

    /** אחרי הסרת אפשרות באמצע הרשימה, האותיות (א./ב./ג.) צריכות להתעדכן ברצף. */
    private void rebuildOptionLetters() {
        Component[] rows = optionsContainer.getComponents();
        int optionIndex = 0;
        for (Component comp : rows) {
            if (comp instanceof JPanel) {
                JPanel row = (JPanel) comp;
                for (Component inner : row.getComponents()) {
                    if (inner instanceof JLabel) {
                        char letter = (char) ('א' + optionIndex);
                        ((JLabel) inner).setText(letter + ".");
                    }
                }
                optionIndex++;
            }
        }
    }

    private void updateOptionControlsState() {
        addOptionButton.setEnabled(optionFields.size() < Config.MAX_ANSWERS);
        optionCountHint.setText(optionFields.size() + " מתוך " + Config.MAX_ANSWERS + " אפשרויות מקסימום");
    }

    // ---------- קריאת נתונים ----------

    /**
     * מאמת ובונה {@link SurveyQuestion} מתוך תוכן השדות.
     *
     * @throws IllegalStateException עם הודעה קריאה למשתמש אם משהו חסר/לא תקין.
     */
    public SurveyQuestion buildValidatedQuestion() {
        String text = questionField.getText().trim();
        if (text.isEmpty()) {
            throw new IllegalStateException("שאלה " + questionNumber + ": חסר נוסח לשאלה.");
        }

        List<String> nonEmptyOptions = new ArrayList<>();
        for (JTextField field : optionFields) {
            String value = field.getText().trim();
            if (!value.isEmpty()) {
                nonEmptyOptions.add(value);
            }
        }

        if (nonEmptyOptions.size() < Config.MIN_ANSWERS) {
            throw new IllegalStateException("שאלה " + questionNumber + ": נדרשות לפחות " +
                    Config.MIN_ANSWERS + " אפשרויות תשובה מלאות.");
        }

        SurveyQuestion question = new SurveyQuestion(text);
        for (String opt : nonEmptyOptions) {
            question.addOption(opt);
        }
        return question;
    }

    /** ממלא את השדות מתוך שאלה קיימת (למשל תוצאה שהתקבלה מ-ChatGPT). */
    public void populateFrom(SurveyQuestion question) {
        questionField.setText(question.getText());

        while (!optionFields.isEmpty()) {
            JTextField last = optionFields.get(optionFields.size() - 1);
            for (Component comp : optionsContainer.getComponents()) {
                if (comp instanceof JPanel && containsField((JPanel) comp, last)) {
                    optionsContainer.remove(comp);
                    break;
                }
            }
            optionFields.remove(last);
        }

        for (var option : question.getOptions()) {
            addOptionField(option.getText());
        }
        optionsContainer.revalidate();
        optionsContainer.repaint();
    }

    /** מעדכן את המספר המוצג ("שאלה N") — נקרא כשמסירים שאלה אחרת מהרשימה ויש לרנמר. */
    public void updateQuestionNumber(int newNumber) {
        this.questionNumber = newNumber;
        titleLabel.setText("שאלה " + newNumber);
    }

    private boolean containsField(JPanel row, JTextField field) {
        for (Component c : row.getComponents()) {
            if (c == field) return true;
        }
        return false;
    }
}
