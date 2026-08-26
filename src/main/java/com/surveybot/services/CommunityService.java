package com.surveybot.services;

import com.surveybot.models.CommunityUser;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * מנהל את הקהילה הגלובלית: הוספת משתמשים, שאילתות, ושידור עדכונים
 * ל-listeners (בעיקר ממשק ה-Swing, כדי לעדכן את הטבלה בזמן אמת ללא
 * צורך ברענון ידני).
 */
public class CommunityService {

    /** מאזין לאירועי קהילה — משמש בעיקר את ה-UI לעדכון בזמן אמת. */
    public interface Listener {
        void onUserJoined(CommunityUser user, int newCommunitySize);
    }

    private final PersistenceService persistence;
    private final Map<Long, CommunityUser> usersById = new LinkedHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public CommunityService(PersistenceService persistence) {
        this.persistence = persistence;
        for (CommunityUser u : persistence.loadCommunity()) {
            usersById.put(u.getTelegramId(), u);
        }
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    /** מסיר listener שנרשם קודם — חשוב לקרוא לזה כשרכיב UI זמני (למשל CreateSurveyPanel שנבנה מחדש בכל מחזור סקר) יורד מהמסך, כדי למנוע דליפת זיכרון. */
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /**
     * מנסה להוסיף משתמש לקהילה. מחזיר true אם זו הצטרפות חדשה בפועל,
     * false אם המשתמש כבר היה חבר קהילה (ואז לא נעשה דבר — לפי ההוראות
     * "משתמש חבר בקהילה לא יצטרף אליה פעם נוספת").
     */
    public synchronized boolean addUserIfAbsent(long telegramId, String username, String firstName, String lastName) {
        if (usersById.containsKey(telegramId)) {
            return false;
        }
        CommunityUser user = new CommunityUser(telegramId, username, firstName, lastName, LocalDateTime.now());
        usersById.put(telegramId, user);
        persistAll();

        int size = usersById.size();
        for (Listener l : listeners) {
            l.onUserJoined(user, size);
        }
        return true;
    }

    public synchronized List<CommunityUser> getAllUsersSortedByJoinTime() {
        List<CommunityUser> list = new ArrayList<>(usersById.values());
        list.sort(Comparator.comparing(CommunityUser::getJoinedAt));
        return list;
    }

    public synchronized List<Long> getAllUserIds() {
        return new ArrayList<>(usersById.keySet());
    }

    public synchronized int size() {
        return usersById.size();
    }

    public synchronized CommunityUser getById(long telegramId) {
        return usersById.get(telegramId);
    }

    private void persistAll() {
        persistence.saveCommunity(new ArrayList<>(usersById.values()));
    }
}
