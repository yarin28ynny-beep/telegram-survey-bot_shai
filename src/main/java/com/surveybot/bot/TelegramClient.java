package com.surveybot.bot;

import com.surveybot.config.Config;
import com.surveybot.services.SimpleJson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * שכבת תקשורת דקה מול ה-Telegram Bot API, מבוססת {@link HttpClient} הסטנדרטי
 * של ה-JDK (זמין מ-Java 11 ואילך) — כדי לא להוסיף תלות Maven חיצונית.
 */
public class TelegramClient {

    private final HttpClient httpClient;

    public TelegramClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** getUpdates עם long-polling — מחכה עד timeout שניות לעדכונים חדשים. */
    public List<Object> getUpdates(long offset) throws Exception {
        String url = Config.TELEGRAM_API_BASE + "getUpdates"
                + "?offset=" + offset
                + "&timeout=" + Config.LONG_POLLING_TIMEOUT_SECONDS;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Config.LONG_POLLING_TIMEOUT_SECONDS + 10L))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Map<String, Object> json = SimpleJson.parseObject(response.body());

        if (!SimpleJson.getBoolean(json, "ok", false)) {
            return List.of();
        }
        return SimpleJson.getArray(json, "result");
    }

    public void sendMessage(long chatId, String text) {
        sendMessageInternal(chatId, text, null);
    }

    /**
     * שולח הודעה עם כפתורי inline keyboard.
     * buttons: רשימת שורות; כל שורה היא רשימת [text, callbackData] pairs.
     */
    public void sendMessageWithButtons(long chatId, String text, List<List<String[]>> buttonRows) {
        sendMessageInternal(chatId, text, buttonRows);
    }

    private void sendMessageInternal(long chatId, String text, List<List<String[]>> buttonRows) {
        try {
            Map<String, Object> params = new java.util.LinkedHashMap<>();
            params.put("chat_id", chatId);
            params.put("text", text);

            if (buttonRows != null && !buttonRows.isEmpty()) {
                List<Object> keyboardRows = new java.util.ArrayList<>();
                for (List<String[]> row : buttonRows) {
                    List<Object> keyboardRow = new java.util.ArrayList<>();
                    for (String[] btn : row) {
                        Map<String, Object> button = new java.util.LinkedHashMap<>();
                        button.put("text", btn[0]);
                        button.put("callback_data", btn[1]);
                        keyboardRow.add(button);
                    }
                    keyboardRows.add(keyboardRow);
                }
                Map<String, Object> replyMarkup = new java.util.LinkedHashMap<>();
                replyMarkup.put("inline_keyboard", keyboardRows);
                params.put("reply_markup", replyMarkup);
            }

            postJson("sendMessage", params);
        } catch (Exception e) {
            System.err.println("שגיאה בשליחת הודעה לטלגרם: " + e.getMessage());
        }
    }

    /** עריכת טקסט הודעה קיימת — משמש לעדכון "תשובתך נרשמה" ולניקוי כפתורים לאחר בחירה. */
    public void editMessageText(long chatId, long messageId, String newText) {
        try {
            Map<String, Object> params = new java.util.LinkedHashMap<>();
            params.put("chat_id", chatId);
            params.put("message_id", messageId);
            params.put("text", newText);
            postJson("editMessageText", params);
        } catch (Exception e) {
            System.err.println("שגיאה בעריכת הודעה בטלגרם: " + e.getMessage());
        }
    }

    /** מענה שקט על callback query, כדי שכפתור הלחיצה בטלגרם יפסיק "לטעון". */
    public void answerCallbackQuery(String callbackQueryId, String toastText) {
        try {
            Map<String, Object> params = new java.util.LinkedHashMap<>();
            params.put("callback_query_id", callbackQueryId);
            if (toastText != null) {
                params.put("text", toastText);
            }
            postJson("answerCallbackQuery", params);
        } catch (Exception e) {
            System.err.println("שגיאה ב-answerCallbackQuery: " + e.getMessage());
        }
    }

    private void postJson(String method, Map<String, Object> params) throws Exception {
        String body = SimpleJson.write(params);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(Config.TELEGRAM_API_BASE + method))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }
}
