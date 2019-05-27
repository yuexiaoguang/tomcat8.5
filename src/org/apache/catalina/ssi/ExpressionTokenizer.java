package org.apache.catalina.ssi;


/**
 * 解析表达式字符串返回单个令牌.
 * 这是模仿JDK的StreamTokenizer而定制的SSI条件表达式解析.
 */
public class ExpressionTokenizer {
    public static final int TOKEN_STRING = 0;
    public static final int TOKEN_AND = 1;
    public static final int TOKEN_OR = 2;
    public static final int TOKEN_NOT = 3;
    public static final int TOKEN_EQ = 4;
    public static final int TOKEN_NOT_EQ = 5;
    public static final int TOKEN_RBRACE = 6;
    public static final int TOKEN_LBRACE = 7;
    public static final int TOKEN_GE = 8;
    public static final int TOKEN_LE = 9;
    public static final int TOKEN_GT = 10;
    public static final int TOKEN_LT = 11;
    public static final int TOKEN_END = 12;
    private final char[] expr;
    private String tokenVal = null;
    private int index;
    private final int length;


    /**
     * @param expr The expression
     */
    public ExpressionTokenizer(String expr) {
        this.expr = expr.trim().toCharArray();
        this.length = this.expr.length;
    }


    /**
     * @return <code>true</code>有更多令牌.
     */
    public boolean hasMoreTokens() {
        return index < length;
    }


    /**
     * @return 用于错误报告目的的当前索引.
     */
    public int getIndex() {
        return index;
    }


    protected boolean isMetaChar(char c) {
        return Character.isWhitespace(c) || c == '(' || c == ')' || c == '!'
                || c == '<' || c == '>' || c == '|' || c == '&' || c == '=';
    }


    /**
     * @return 下一个令牌类型，并据此初始化任何状态变量.
     */
    public int nextToken() {
        // Skip any leading white space
        while (index < length && Character.isWhitespace(expr[index]))
            index++;
        // Clear the current token val
        tokenVal = null;
        if (index == length) return TOKEN_END; // End of string
        int start = index;
        char currentChar = expr[index];
        char nextChar = (char)0;
        index++;
        if (index < length) nextChar = expr[index];
        // Check for a known token start
        switch (currentChar) {
            case '(' :
                return TOKEN_LBRACE;
            case ')' :
                return TOKEN_RBRACE;
            case '=' :
                return TOKEN_EQ;
            case '!' :
                if (nextChar == '=') {
                    index++;
                    return TOKEN_NOT_EQ;
                }
                return TOKEN_NOT;
            case '|' :
                if (nextChar == '|') {
                    index++;
                    return TOKEN_OR;
                }
                break;
            case '&' :
                if (nextChar == '&') {
                    index++;
                    return TOKEN_AND;
                }
                break;
            case '>' :
                if (nextChar == '=') {
                    index++;
                    return TOKEN_GE; // Greater than or equal
                }
                return TOKEN_GT; // Greater than
            case '<' :
                if (nextChar == '=') {
                    index++;
                    return TOKEN_LE; // Less than or equal
                }
                return TOKEN_LT; // Less than
            default :
                // Otherwise it's a string
                break;
        }
        int end = index;
        if (currentChar == '"' || currentChar == '\'') {
            // It's a quoted string and the end is the next unescaped quote
            char endChar = currentChar;
            boolean escaped = false;
            start++;
            for (; index < length; index++) {
                if (expr[index] == '\\' && !escaped) {
                    escaped = true;
                    continue;
                }
                if (expr[index] == endChar && !escaped) break;
                escaped = false;
            }
            end = index;
            index++; // Skip the end quote
        } else if (currentChar == '/') {
            // It's a regular expression and the end is the next unescaped /
            char endChar = currentChar;
            boolean escaped = false;
            for (; index < length; index++) {
                if (expr[index] == '\\' && !escaped) {
                    escaped = true;
                    continue;
                }
                if (expr[index] == endChar && !escaped) break;
                escaped = false;
            }
            end = ++index;
        } else {
            // End is the next whitespace character
            for (; index < length; index++) {
                if (isMetaChar(expr[index])) break;
            }
            end = index;
        }
        // Extract the string from the array
        this.tokenVal = new String(expr, start, end - start);
        return TOKEN_STRING;
    }


    /**
     * @return 令牌的字符串值，如果它是TOKEN_STRING类型. 否则返回null.
     */
    public String getTokenValue() {
        return tokenVal;
    }
}