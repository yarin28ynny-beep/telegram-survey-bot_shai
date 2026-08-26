package com.surveybot.models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * משתמש הרשום בקהילה הגלובלית.
 * זהו מידע גלובלי בלבד — לא כולל שום דבר הקשור לסקר ספציפי (זה תפקידו של
 * {@link SurveyParticipation}), בהתאם להפרדה המפורשת שדורשות ההוראות.
 */
public class CommunityUser {

    private final long telegramId;
    private final String username;   // ה-@handle בטלגרם, יכול להיות ריק
    private final String firstName;
    private final String lastName;
    private final LocalDateTime joinedAt;

    public CommunityUser(long telegramId, String username, String firstName, String lastName, LocalDateTime joinedAt) {
        this.telegramId = telegramId;
        this.username = username == null ? "" : username;
        this.firstName = firstName == null ? "" : firstName;
        this.lastName = lastName == null ? "" : lastName;
        this.joinedAt = joinedAt == null ? LocalDateTime.now() : joinedAt;
    }

    public long getTelegramId() {
        return telegramId;
    }

    public String getUsername() {
        return username;
    }

    public boolean hasUsername() {
        return !username.isEmpty();
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    /** שם תצוגה מלא — נופל חזרה ל-username ואז ל-ID אם אין שם פרטי. */
    public String getDisplayName() {
        String full = (firstName + " " + lastName).trim();
        if (!full.isEmpty()) {
            return full;
        }
        if (hasUsername()) {
            return "@" + username;
        }
        return "משתמש #" + telegramId;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public String getJoinedAtFormatted() {
        return joinedAt.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommunityUser)) return false;
        return telegramId == ((CommunityUser) o).telegramId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(telegramId);
    }
}
