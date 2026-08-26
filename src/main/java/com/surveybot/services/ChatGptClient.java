package com.surveybot.services;

import com.surveybot.config.Config;
import com.surveybot.models.SurveyQuestion;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * לקוח ל-API הגישור לChatGPT, בפורמט שנבדק ידנית מול השרת בפועל:
 * GET .../api-request?token=...&text=...
 * מחזיר JSON עם שדה "value" שמכיל את תשובת ה-AI כטקסט חופשי בפורמט:
 * <pre>
 * ### שאלה 1:
 * טקסט השאלה?
 * א. אפשרות 1
 * ב. אפשרות 2
 * ג. אפשרות 3
 * </pre>
 */
public class ChatGptClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    // תופס: "### שאלה <מספר>:" ואז כל הטקסט עד ל-"###" הבא או לסוף המחרוזת.
    private static final Pattern QUESTION_BLOCK = Pattern.compile(
            "###\\s*שאלה\\s*\\d+\\s*:\\s*(.*?)(?=###\\s*שאלה\\s*\\d+\\s*:|$)",
            Pattern.DOTALL
    );

    // בתוך בלוק שאלה: שורה ראשונה לא ריקה = טקסט השאלה, שאר השורות שמתחילות
    // באות עברית ואז נקודה = אפשרויות תשובה (א./ב./ג./ד.)
    private static final Pattern ANSWER_LINE = Pattern.compile(
            "^\\s*[אבגד]\\s*[.).]\\s*(.+?)\\s*$"
    );

    /**
     * מבקש מה-AI ליצור שאלות לנושא נתון, ומחזיר רשימת {@link SurveyQuestion}
     * מוכנות (עם אפשרויות התשובה, ללא קולות עדיין).
     *
     * @throws ChatGptException אם הבקשה נכשלה, או אם לא הצלחנו לפרסר תוצאה
     *                          תקינה מהתשובה החופשית של ה-AI.
     */
    public List<SurveyQuestion> generateQuestions(String topic, int questionCount, int answersPerQuestion) throws ChatGptException {
        String prompt = buildPrompt(topic, questionCount, answersPerQuestion);
        String rawValue = callApi(prompt);
        List<SurveyQuestion> parsed = parseQuestions(rawValue, answersPerQuestion);

        if (parsed.isEmpty()) {
            throw new ChatGptException(
                    "לא הצלחנו לפענח שאלות מתגובת ה-AI. נסה שוב, או נסח את הנושא בצורה שונה.\n\n" +
                    "תגובה גולמית שהתקבלה:\n" + rawValue
            );
        }
        return parsed;
    }

    private String buildPrompt(String topic, int questionCount, int answersPerQuestion) {
        return String.format(
                "צור בדיוק %d שאלות סקר בנושא: %s. " +
                "לכל שאלה בדיוק %d אפשרויות תשובה קצרות. " +
                "החזר את התשובה בפורמט המדויק הבא, בלי הסברים נוספים: " +
                "לכל שאלה כתוב שורה \"### שאלה <מספר>:\" ואז את השאלה, " +
                "ואז כל אפשרות תשובה בשורה נפרדת שמתחילה באות עברית ונקודה, לדוגמה: " +
                "\"א. אפשרות ראשונה\".",
                questionCount, topic, answersPerQuestion
        );
    }

    private String callApi(String text) throws ChatGptException {
        try {
            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String url = Config.CHATGPT_API_URL
                    + "?token=" + Config.CHATGPT_API_TOKEN
                    + "&text=" + encodedText;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                throw new ChatGptException("שגיאת שרת ה-AI: קוד תשובה " + response.statusCode());
            }

            Map<String, Object> json = SimpleJson.parseObject(response.body());
            boolean isError = SimpleJson.getBoolean(json, "error", false);
            String value = SimpleJson.getString(json, "value", "");

            if (isError) {
                throw new ChatGptException("שרת ה-AI החזיר שגיאה: " + value);
            }
            if (value.isEmpty()) {
                throw new ChatGptException("שרת ה-AI החזיר תגובה ריקה.");
            }
            return value;

        } catch (ChatGptException e) {
            throw e;
        } catch (Exception e) {
            throw new ChatGptException("כשל בתקשורת עם שרת ה-AI: " + e.getMessage());
        }
    }

    private List<SurveyQuestion> parseQuestions(String rawValue, int expectedAnswersPerQuestion) {
        List<SurveyQuestion> result = new ArrayList<>();
        Matcher blockMatcher = QUESTION_BLOCK.matcher(rawValue);

        while (blockMatcher.find()) {
            String block = blockMatcher.group(1).trim();
            String[] lines = block.split("\\r?\\n");
            if (lines.length == 0) continue;

            String questionText = lines[0].trim();
            if (questionText.isEmpty()) continue;

            SurveyQuestion question = new SurveyQuestion(questionText);
            for (int i = 1; i < lines.length; i++) {
                Matcher answerMatcher = ANSWER_LINE.matcher(lines[i]);
                if (answerMatcher.matches()) {
                    String answerText = answerMatcher.group(1).trim();
                    if (!answerText.isEmpty()) {
                        question.addOption(answerText);
                    }
                }
            }

            // מקבלים רק שאלות שבאמת יש להן טווח תשובות תקין (2-4),
            // גם אם ביקשנו מספר מדויק — ה-AI לפעמים סוטה קצת.
            int optionCount = question.getOptions().size();
            if (optionCount >= Config.MIN_ANSWERS && optionCount <= Config.MAX_ANSWERS) {
                result.add(question);
            }
        }

        // לא חורגים ממספר השאלות המרבי המותר, גם אם ה-AI סיפק יותר.
        if (result.size() > Config.MAX_QUESTIONS) {
            result = new ArrayList<>(result.subList(0, Config.MAX_QUESTIONS));
        }

        return result;
    }

    /** חריגה ייעודית לכשלים בתקשורת/פרסור מול שרת ה-AI, עם הודעה קריאה למשתמש. */
    public static class ChatGptException extends Exception {
        public ChatGptException(String message) {
            super(message);
        }
    }
}
