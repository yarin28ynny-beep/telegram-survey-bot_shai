package com.surveybot.ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * מרכז עיצוב אחיד לכל הממשק: צבעים, פונטים ומרווחים.
 * מטרתו למנוע "עיצוב מפוזר" שבו כל מסך נראה קצת אחרת, ולתת מראה
 * מקצועי ועקבי — בהתאם לדגש המפורש בהוראות המשימה על איכות UX/UI.
 */
public final class Theme {

    private Theme() {
    }

    // ---------- פלטת צבעים ----------
    public static final Color BG_MAIN = new Color(0xF5, 0xF7, 0xFA);
    public static final Color BG_CARD = Color.WHITE;
    public static final Color BG_SIDEBAR = new Color(0x1E, 0x29, 0x3B);

    public static final Color PRIMARY = new Color(0x2F, 0x6F, 0xED);
    public static final Color PRIMARY_DARK = new Color(0x1E, 0x54, 0xC4);
    public static final Color PRIMARY_LIGHT = new Color(0xE8, 0xF0, 0xFE);

    public static final Color SUCCESS = new Color(0x1F, 0xA1, 0x5C);
    public static final Color SUCCESS_LIGHT = new Color(0xE6, 0xF7, 0xEE);
    public static final Color WARNING = new Color(0xE0, 0x8E, 0x0B);
    public static final Color WARNING_LIGHT = new Color(0xFD, 0xF3, 0xDE);
    public static final Color DANGER = new Color(0xD6, 0x33, 0x3E);
    public static final Color DANGER_LIGHT = new Color(0xFC, 0xE9, 0xEA);

    public static final Color TEXT_PRIMARY = new Color(0x1A, 0x1F, 0x2B);
    public static final Color TEXT_SECONDARY = new Color(0x60, 0x6B, 0x7D);
    public static final Color TEXT_ON_DARK = new Color(0xE9, 0xED, 0xF5);
    public static final Color TEXT_ON_DARK_MUTED = new Color(0x9A, 0xA5, 0xBD);

    public static final Color BORDER = new Color(0xE1, 0xE5, 0xEC);
    public static final Color BORDER_STRONG = new Color(0xC7, 0xCE, 0xDB);

    // ---------- פונטים ----------
    private static final String FONT_FAMILY = pickAvailableFont();

    public static Font h1() { return new Font(FONT_FAMILY, Font.BOLD, 24); }
    public static Font h2() { return new Font(FONT_FAMILY, Font.BOLD, 18); }
    public static Font h3() { return new Font(FONT_FAMILY, Font.BOLD, 15); }
    public static Font body() { return new Font(FONT_FAMILY, Font.PLAIN, 14); }
    public static Font bodyBold() { return new Font(FONT_FAMILY, Font.BOLD, 14); }
    public static Font small() { return new Font(FONT_FAMILY, Font.PLAIN, 12); }
    public static Font smallBold() { return new Font(FONT_FAMILY, Font.BOLD, 12); }
    public static Font mono() { return new Font(Font.MONOSPACED, Font.BOLD, 15); }
    public static Font countdown() { return new Font(FONT_FAMILY, Font.BOLD, 32); }

    private static String pickAvailableFont() {
        // מעדיף פונטים שתומכים היטב בעברית ונראים טוב על Windows.
        String[] preferred = {"Segoe UI", "Arial", "Tahoma"};
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        java.util.Set<String> availableSet = new java.util.HashSet<>(java.util.Arrays.asList(available));
        for (String candidate : preferred) {
            if (availableSet.contains(candidate)) return candidate;
        }
        return Font.SANS_SERIF;
    }

    // ---------- מרווחים ----------
    public static final int SPACING_XS = 4;
    public static final int SPACING_SM = 8;
    public static final int SPACING_MD = 16;
    public static final int SPACING_LG = 24;
    public static final int SPACING_XL = 32;

    public static Border padding(int all) {
        return new EmptyBorder(all, all, all, all);
    }

    public static Border padding(int vertical, int horizontal) {
        return new EmptyBorder(vertical, horizontal, vertical, horizontal);
    }

    /** גבול "כרטיס" — קו עדין + פינות מעוגלות רכות (מדומה עם MatteBorder כי Swing מוגבל בעיגול אמיתי). */
    public static Border cardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                padding(SPACING_MD)
        );
    }

    // ---------- רכיבי עזר ----------

    public static JPanel card() {
        JPanel panel = new JPanel();
        panel.setBackground(BG_CARD);
        panel.setBorder(cardBorder());
        return panel;
    }

    public static JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(h2());
        label.setForeground(TEXT_PRIMARY);
        return label;
    }

    public static JLabel subtitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(body());
        label.setForeground(TEXT_SECONDARY);
        return label;
    }

    /** "פיל" קטן וצבעוני — לתגי סטטוס כמו "פעיל" / "טרם ענה" / "הושלם". */
    public static JLabel pill(String text, Color background, Color foreground) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setOpaque(true);
        label.setBackground(background);
        label.setForeground(foreground);
        label.setFont(smallBold());
        label.setBorder(padding(4, 10));
        return label;
    }

    public static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        styleButton(button, PRIMARY, Color.WHITE, PRIMARY_DARK);
        return button;
    }

    public static JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        styleButton(button, BG_CARD, TEXT_PRIMARY, BORDER_STRONG);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_STRONG, 1, true),
                padding(8, 16)
        ));
        return button;
    }

    public static JButton dangerButton(String text) {
        JButton button = new JButton(text);
        styleButton(button, DANGER, Color.WHITE, new Color(0xB8, 0x28, 0x32));
        return button;
    }

    private static void styleButton(JButton button, Color bg, Color fg, Color borderColor) {
        button.setFont(bodyBold());
        button.setBackground(bg);
        button.setForeground(fg);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (bg != BG_CARD) {
            button.setBorder(padding(10, 20));
        }
        button.setOpaque(true);
        button.setBorderPainted(false);
    }
}
