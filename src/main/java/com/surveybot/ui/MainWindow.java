package com.surveybot.ui;

import com.surveybot.bot.TelegramBot;
import com.surveybot.config.Config;
import com.surveybot.services.CommunityService;
import com.surveybot.services.SurveyService;
import com.surveybot.ui.pages.CommunityPage;
import com.surveybot.ui.pages.SurveyHubPage;

import javax.swing.*;
import java.awt.*;

/**
 * חלון האפליקציה הראשי: ניווט צדדי בין "קהילה" ל"סקרים", ואזור תוכן
 * מרכזי שמתחלף לפי הבחירה. הניווט נשאר גלוי תמיד כדי שהמשתמש תמיד
 * יידע היכן הוא נמצא (עקביות ובהירות זרימה, כפי שההוראות מדגישות).
 */
public class MainWindow extends JFrame {

    private final CardLayout contentLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(contentLayout);

    private static final String PAGE_COMMUNITY = "community";
    private static final String PAGE_SURVEYS = "surveys";

    private JButton navCommunityButton;
    private JButton navSurveysButton;

    public MainWindow(CommunityService communityService, SurveyService surveyService, TelegramBot telegramBot) {
        super(Config.APP_TITLE);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        setMinimumSize(new Dimension(1000, 650));
        setLocationRelativeTo(null);

        getContentPane().setBackground(Theme.BG_MAIN);
        setLayout(new BorderLayout());

        add(buildSidebar(), BorderLayout.WEST);
        add(buildContentArea(communityService, surveyService, telegramBot), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                int choice = JOptionPane.showConfirmDialog(
                        MainWindow.this,
                        "לסגור את האפליקציה? הבוט יפסיק לפעול.",
                        "אישור סגירה",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );
                if (choice == JOptionPane.YES_OPTION) {
                    telegramBot.stop();
                    dispose();
                    System.exit(0);
                }
            }
        });

        showPage(PAGE_COMMUNITY);
    }

    private JComponent buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(Theme.BG_SIDEBAR);
        sidebar.setPreferredSize(new Dimension(220, 0));
        sidebar.setBorder(Theme.padding(Theme.SPACING_MD));

        JLabel appName = new JLabel("ניהול סקרים");
        appName.setFont(Theme.h2());
        appName.setForeground(Theme.TEXT_ON_DARK);
        appName.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JLabel botHandle = new JLabel(Config.TELEGRAM_BOT_USERNAME);
        botHandle.setFont(Theme.small());
        botHandle.setForeground(Theme.TEXT_ON_DARK_MUTED);
        botHandle.setAlignmentX(Component.RIGHT_ALIGNMENT);

        sidebar.add(appName);
        sidebar.add(Box.createVerticalStrut(2));
        sidebar.add(botHandle);
        sidebar.add(Box.createVerticalStrut(Theme.SPACING_XL));

        navCommunityButton = buildNavButton("👥   קהילה", PAGE_COMMUNITY);
        navSurveysButton = buildNavButton("📊   סקרים", PAGE_SURVEYS);

        sidebar.add(navCommunityButton);
        sidebar.add(Box.createVerticalStrut(Theme.SPACING_SM));
        sidebar.add(navSurveysButton);
        sidebar.add(Box.createVerticalGlue());

        return sidebar;
    }

    private JButton buildNavButton(String text, String pageKey) {
        JButton button = new JButton(text);
        button.setFont(Theme.bodyBold());
        button.setHorizontalAlignment(SwingConstants.RIGHT);
        button.setAlignmentX(Component.RIGHT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> showPage(pageKey));
        applyNavButtonStyle(button, false);
        return button;
    }

    private void applyNavButtonStyle(JButton button, boolean selected) {
        if (selected) {
            button.setBackground(Theme.PRIMARY);
            button.setForeground(Color.WHITE);
        } else {
            button.setBackground(Theme.BG_SIDEBAR);
            button.setForeground(Theme.TEXT_ON_DARK);
        }
        button.setOpaque(true);
    }

    private void showPage(String pageKey) {
        contentLayout.show(contentPanel, pageKey);
        applyNavButtonStyle(navCommunityButton, PAGE_COMMUNITY.equals(pageKey));
        applyNavButtonStyle(navSurveysButton, PAGE_SURVEYS.equals(pageKey));
    }

    private JComponent buildContentArea(CommunityService communityService, SurveyService surveyService, TelegramBot telegramBot) {
        contentPanel.setBackground(Theme.BG_MAIN);

        CommunityPage communityPage = new CommunityPage(communityService);
        SurveyHubPage surveyHubPage = new SurveyHubPage(communityService, surveyService);

        contentPanel.add(communityPage, PAGE_COMMUNITY);
        contentPanel.add(surveyHubPage, PAGE_SURVEYS);

        return contentPanel;
    }

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.BG_CARD);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                Theme.padding(6, 16)
        ));

        JLabel statusLabel = new JLabel("🟢 הבוט פעיל ומאזין להודעות בטלגרם");
        statusLabel.setFont(Theme.small());
        statusLabel.setForeground(Theme.TEXT_SECONDARY);
        bar.add(statusLabel, BorderLayout.EAST);

        return bar;
    }
}
