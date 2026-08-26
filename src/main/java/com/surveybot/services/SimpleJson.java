package com.surveybot.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * כלי JSON מינימלי, עצמאי (ללא תלות בספרייה חיצונית).
 * <p>
 * ההחלטה לכתוב פרסר עצמאי במקום להשתמש בספריית JSON חיצונית (למשל org.json)
 * היא מכוונת: פרויקט שמצהיר על תלות Maven חיצונית עלול להיכשל ב-build אצל
 * המשתמש אם יש חסימת רשת/פרוקסי/פיירוול ארגוני שמונע גישה ל-Maven Central.
 * מימוש עצמאי מבטיח build נקי ועקבי בכל סביבה שיש בה JDK בלבד.
 * <p>
 * התומך רק במה שהמערכת בפועל צריכה: אובייקטים, מערכים, מחרוזות, מספרים,
 * בוליאנים ו-null. זה מספיק לחלוטין לצורך סריאליזציה/דה-סריאליזציה של
 * הישויות הפנימיות שלנו ולפענוח תגובות ה-API של טלגרם ו-ChatGPT.
 */
public final class SimpleJson {

    private SimpleJson() {
    }

    // ============================================================
    //  כתיבה (Object -> String)
    // ============================================================

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(value, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString((String) value, sb);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value.toString());
        } else if (value instanceof Map) {
            writeObject((Map<String, Object>) value, sb);
        } else if (value instanceof List) {
            writeArray((List<Object>) value, sb);
        } else {
            // ברירת מחדל בטוחה — מייצג ככל string
            writeString(value.toString(), sb);
        }
    }

    private static void writeObject(Map<String, Object> map, StringBuilder sb) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            writeString(entry.getKey(), sb);
            sb.append(':');
            writeValue(entry.getValue(), sb);
        }
        sb.append('}');
    }

    private static void writeArray(List<Object> list, StringBuilder sb) {
        sb.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) sb.append(',');
            first = false;
            writeValue(item, sb);
        }
        sb.append(']');
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ============================================================
    //  קריאה (String -> Map/List/String/Number/Boolean/null)
    // ============================================================

    public static Object parse(String json) {
        Parser p = new Parser(json);
        Object result = p.parseValue();
        p.skipWhitespace();
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        return (Map<String, Object>) parse(json);
    }

    @SuppressWarnings("unchecked")
    public static List<Object> parseArray(String json) {
        return (List<Object>) parse(json);
    }

    private static final class Parser {
        private final String s;
        private int pos = 0;

        Parser(String s) {
            this.s = s;
        }

        void skipWhitespace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        char peek() {
            return s.charAt(pos);
        }

        void expect(char c) {
            if (pos >= s.length() || s.charAt(pos) != c) {
                throw new IllegalArgumentException(
                        "JSON לא תקין: ציפיתי ל-'" + c + "' במיקום " + pos +
                        " אבל קיבלתי '" + (pos < s.length() ? s.charAt(pos) : "EOF") + "'");
            }
            pos++;
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= s.length()) {
                throw new IllegalArgumentException("JSON לא תקין: סוף קלט לא צפוי");
            }
            char c = peek();
            if (c == '{') return parseObjectInternal();
            if (c == '[') return parseArrayInternal();
            if (c == '"') return parseStringInternal();
            if (c == 't') { expectLiteral("true"); return Boolean.TRUE; }
            if (c == 'f') { expectLiteral("false"); return Boolean.FALSE; }
            if (c == 'n') { expectLiteral("null"); return null; }
            return parseNumberInternal();
        }

        Map<String, Object> parseObjectInternal() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (pos < s.length() && peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseStringInternal();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (pos < s.length() && peek() == ',') {
                    pos++;
                    continue;
                }
                break;
            }
            skipWhitespace();
            expect('}');
            return map;
        }

        List<Object> parseArrayInternal() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (pos < s.length() && peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                Object value = parseValue();
                list.add(value);
                skipWhitespace();
                if (pos < s.length() && peek() == ',') {
                    pos++;
                    continue;
                }
                break;
            }
            skipWhitespace();
            expect(']');
            return list;
        }

        String parseStringInternal() {
            skipWhitespace();
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= s.length()) {
                    throw new IllegalArgumentException("JSON לא תקין: מחרוזת לא סגורה");
                }
                char c = s.charAt(pos++);
                if (c == '"') break;
                if (c == '\\') {
                    char esc = s.charAt(pos++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            String hex = s.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                            break;
                        default:
                            sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Object parseNumberInternal() {
            int start = pos;
            if (pos < s.length() && (peek() == '-' || peek() == '+')) pos++;
            boolean isDouble = false;
            while (pos < s.length()) {
                char c = peek();
                if (Character.isDigit(c)) {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '-' || c == '+') {
                    isDouble = true;
                    pos++;
                } else {
                    break;
                }
            }
            String numStr = s.substring(start, pos);
            if (numStr.isEmpty()) {
                throw new IllegalArgumentException("JSON לא תקין: מספר ריק במיקום " + start);
            }
            if (isDouble) {
                return Double.parseDouble(numStr);
            }
            try {
                return Long.parseLong(numStr);
            } catch (NumberFormatException e) {
                return Double.parseDouble(numStr);
            }
        }

        void expectLiteral(String literal) {
            if (pos + literal.length() > s.length() || !s.startsWith(literal, pos)) {
                throw new IllegalArgumentException("JSON לא תקין: ציפיתי ל-'" + literal + "' במיקום " + pos);
            }
            pos += literal.length();
        }
    }

    // ============================================================
    //  Helpers נוחים לקריאה בטוחה מ-Map שהתקבל מ-parse
    // ============================================================

    public static String getString(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v == null ? defaultValue : v.toString();
    }

    public static long getLong(Map<String, Object> map, String key, long defaultValue) {
        Object v = map.get(key);
        if (v == null) return defaultValue;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static int getInt(Map<String, Object> map, String key, int defaultValue) {
        return (int) getLong(map, key, defaultValue);
    }

    public static boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object v = map.get(key);
        if (v == null) return defaultValue;
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.parseBoolean(v.toString());
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getObject(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : (Map<String, Object>) v;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> getArray(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? new ArrayList<>() : (List<Object>) v;
    }
}
