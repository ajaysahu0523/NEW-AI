package com.example.util;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java recursive-descent math evaluator supporting standard expressions
 * and spoken Hindi/English/Hinglish arithmetic phrases.
 */
public final class MathEvaluator {

    private static final Pattern PERCENT_OF_PATTERN =
            Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*(?:%|percent|प्रतिशत|फीसदी)\\s*(?:of|का|के|की)\\s*(-?\\d+(?:\\.\\d+)?)",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern HIN_PERCENT_PATTERN =
            Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*(?:ka|ke|ki|का|के|की)\\s*(-?\\d+(?:\\.\\d+)?)\\s*(?:%|percent|pratishat|प्रतिशत)",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern SQRT_PATTERN =
            Pattern.compile("(?:square root of|sqrt of|sqrt|वर्गमूल)\\s*(-?\\d+(?:\\.\\d+)?)",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private MathEvaluator() {}

    public static Double tryEvaluate(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return null;
        }
        String text = rawInput.trim().toLowerCase(Locale.ROOT);

        // Handle "X% of Y"
        Matcher m1 = PERCENT_OF_PATTERN.matcher(text);
        if (m1.find()) {
            try {
                double pct = Double.parseDouble(m1.group(1));
                double base = Double.parseDouble(m1.group(2));
                return (pct / 100.0) * base;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        // Handle "2500 ka 18 percent"
        Matcher m2 = HIN_PERCENT_PATTERN.matcher(text);
        if (m2.find()) {
            try {
                double base = Double.parseDouble(m2.group(1));
                double pct = Double.parseDouble(m2.group(2));
                return (pct / 100.0) * base;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        // Handle "square root of 144"
        Matcher m3 = SQRT_PATTERN.matcher(text);
        if (m3.find()) {
            try {
                double val = Double.parseDouble(m3.group(1));
                if (val < 0) return null;
                return Math.sqrt(val);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        // Normalize spoken math operators
        String expr = text
                .replaceAll("^(calculate|compute|solve|what is|evaluate|बताओ|हिसाब करो|जोड़ो|calculate karo)\\s+", "")
                .replace("multiplied by", "*")
                .replace("into", "*")
                .replace("times", "*")
                .replace("गुणा", "*")
                .replace("×", "*")
                .replace("x", "*")
                .replace("divided by", "/")
                .replace("divide by", "/")
                .replace("भाग", "/")
                .replace("÷", "/")
                .replace("plus", "+")
                .replace("जोड़", "+")
                .replace("minus", "-")
                .replace("घटा", "-")
                .replace("power", "^")
                .replace("to the power of", "^")
                .replace("mod", "%")
                .replace("?", "")
                .trim();

        // Must contain at least one digit and one operator, or parentheses with digits
        if (!expr.matches(".*[0-9].*") || !expr.matches(".*[+\\-*/^%].*")) {
            return null;
        }
        // Reject if letters remain (means it's a general sentence, not a pure math expression)
        if (expr.matches(".*[a-zA-Z\\u0900-\\u097F].*")) {
            return null;
        }

        try {
            Parser parser = new Parser(expr);
            double result = parser.parse();
            if (Double.isNaN(result) || Double.isInfinite(result)) {
                return null;
            }
            return result;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-9) {
            return String.valueOf((long) Math.rint(value));
        }
        DecimalFormat df = new DecimalFormat("#.######");
        return df.format(value);
    }

    private static final class Parser {
        private final String str;
        private int pos = -1;
        private int ch;

        Parser(String str) {
            this.str = str;
            nextChar();
        }

        void nextChar() {
            ch = (++pos < str.length()) ? str.charAt(pos) : -1;
        }

        boolean eat(int charToEat) {
            while (ch == ' ') nextChar();
            if (ch == charToEat) {
                nextChar();
                return true;
            }
            return false;
        }

        double parse() {
            double x = parseExpression();
            while (ch == ' ') nextChar();
            if (pos < str.length()) {
                throw new RuntimeException("Unexpected char: " + (char) ch);
            }
            return x;
        }

        double parseExpression() {
            double x = parseTerm();
            for (;;) {
                if (eat('+')) x += parseTerm();
                else if (eat('-')) x -= parseTerm();
                else return x;
            }
        }

        double parseTerm() {
            double x = parseFactor();
            for (;;) {
                if (eat('*')) x *= parseFactor();
                else if (eat('/')) {
                    double d = parseFactor();
                    if (d == 0.0) throw new RuntimeException("Division by zero");
                    x /= d;
                } else if (eat('%')) {
                    double d = parseFactor();
                    if (d == 0.0) throw new RuntimeException("Modulo by zero");
                    x %= d;
                } else return x;
            }
        }

        double parseFactor() {
            if (eat('+')) return +parseFactor();
            if (eat('-')) return -parseFactor();

            double x;
            int startPos = this.pos;
            if (eat('(')) {
                x = parseExpression();
                if (!eat(')')) throw new RuntimeException("Missing ')'");
            } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                x = Double.parseDouble(str.substring(startPos, this.pos));
            } else {
                throw new RuntimeException("Unexpected: " + (char) ch);
            }

            if (eat('^')) x = Math.pow(x, parseFactor());
            return x;
        }
    }
}
