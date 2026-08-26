package com.surveybot.ui.pages;

import com.surveybot.models.CommunityUser;
import com.surveybot.services.CommunityService;
import com.surveybot.ui.Theme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * דף "קהילה": מציג בזמן אמת את כל חברי הקהילה הגלובלית — שם, שם משתמש
 * בטלגרם, ומועד הצטרפות — יחד עם מונה חברים גדול וברור.
 * מתעדכן אוטומטית דרך {@link CommunityService.Listener} בלי שום פעולה
 * מצד המשתמש (בהתאם לדרישה המפורשת בהוראות).
 */
public class CommunityPage extends JPanel {

    private final CommunityService communityService;
    private final DefaultTableModel tableModel;
    private final JLabel memberCountLabel;
    private final JLabel memberCountCaption;

    public CommunityPage(CommunityService communityService) {
        this.communityService = communityService;

        setLayout(new BorderLayout(0, Theme.SPACING_MD));
        setBorder(Theme.padding(Theme.SPACING_LG));
        setBackground(Theme.BG_MAIN);

        add(buildHeader(), BorderLayout.NORTH);

        this.tableModel = new DefaultTableModel(
                new Object[]{"שם", "שם משתמש בטלגרם", "מועד הצטרפות"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable table = buildTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
        scrollPane.getViewport().setBackground(Color.WHITE);

        JPanel tableCard = Theme.card();
        tableCard.setLayout(new BorderLayout());
        tableCard.add(scrollPane, BorderLayout.CENTER);
        add(tableCard, BorderLayout.CENTER);

        this.memberCountLabel = new JLabel();
        this.memberCountCaption = new JLabel("חברי קהילה");
        add(buildFooterCountCard(), BorderLayout.SOUTH);

        communityService.addListener((user, newSize) -> SwingUtilities.invokeLater(this::refresh));
        refresh();
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.add(Theme.sectionTitle("חברי הקהילה"));
        titleBlock.add(Box.createVerticalStrut(4));
        titleBlock.add(Theme.subtitle("רשימה גלובלית — אינה קשורה לסקר ספציפי, ומתעדכנת בזמן אמת"));

        header.add(titleBlock, BorderLayout.EAST);
        return header;
    }

    private JTable buildTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFont(Theme.body());
        table.setRowHeight(36);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(Theme.PRIMARY_LIGHT);
        table.setSelectionForeground(Theme.TEXT_PRIMARY);
        table.setFillsViewportHeight(true);

        table.getTableHeader().setFont(Theme.smallBold());
        table.getTableHeader().setBackground(new Color(0xF0, 0xF2, 0xF6));
        table.getTableHeader().setForeground(Theme.TEXT_SECONDARY);
        table.getTableHeader().setPreferredSize(new Dimension(0, 38));

        table.getColumnModel().getColumn(0).setPreferredWidth(220);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(140);

        return table;
    }

    /** בונה כרטיס פאנל תחתון עם מונה החברים — נקרא פעם אחת מ-refresh הראשון. */
    private JComponent buildFooterCountCard() {
        JPanel card = Theme.card();
        card.setLayout(new FlowLayout(FlowLayout.RIGHT, Theme.SPACING_SM, Theme.SPACING_SM));
        card.setBackground(Theme.PRIMARY_LIGHT);

        memberCountLabel.setFont(Theme.h1());
        memberCountLabel.setForeground(Theme.PRIMARY_DARK);
        memberCountCaption.setFont(Theme.body());
        memberCountCaption.setForeground(Theme.TEXT_SECONDARY);

        card.add(memberCountCaption);
        card.add(memberCountLabel);
        return card;
    }

    private void refresh() {
        List<CommunityUser> users = communityService.getAllUsersSortedByJoinTime();

        tableModel.setRowCount(0);
        for (CommunityUser user : users) {
            String usernameDisplay = user.hasUsername() ? ("@" + user.getUsername()) : "—";
            tableModel.addRow(new Object[]{
                    user.getDisplayName(),
                    usernameDisplay,
                    user.getJoinedAtFormatted()
            });
        }

        memberCountLabel.setText(String.valueOf(users.size()));
    }
}
