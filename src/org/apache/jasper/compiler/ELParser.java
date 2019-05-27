package org.apache.jasper.compiler;

import org.apache.jasper.JasperException;
import org.apache.jasper.compiler.ELNode.ELText;
import org.apache.jasper.compiler.ELNode.Function;
import org.apache.jasper.compiler.ELNode.Root;
import org.apache.jasper.compiler.ELNode.Text;

/**
 * 这个类实现了EL表达式的解析器.
 *
 * 它以字符串的形式 xxx${..}yyy${..}zzz 等等, 把它变成一个ELNode.Nodes.
 *
 * 目前, 它只处理文本外部的 ${..} 和 ${ ..}中的函数.
 */
public class ELParser {

    private Token curToken;  // current token
    private Token prevToken; // previous token
    private String whiteSpace = "";

    private final ELNode.Nodes expr;

    private ELNode.Nodes ELexpr;

    private int index; // 表达式当前索引

    private final String expression; // EL表达式

    private char type;

    private final boolean isDeferredSyntaxAllowedAsLiteral;

    private static final String reservedWords[] = { "and", "div", "empty",
            "eq", "false", "ge", "gt", "instanceof", "le", "lt", "mod", "ne",
            "not", "null", "or", "true" };

    public ELParser(String expression, boolean isDeferredSyntaxAllowedAsLiteral) {
        index = 0;
        this.expression = expression;
        this.isDeferredSyntaxAllowedAsLiteral = isDeferredSyntaxAllowedAsLiteral;
        expr = new ELNode.Nodes();
    }

    /**
     * 解析EL表达式
     * @param expression 输入表达式字符串形式的 Char* ('${' Char* '}')* Char*
     * @param isDeferredSyntaxAllowedAsLiteral    Are deferred expressions treated as literals?
     * @return Parsed EL expression in ELNode.Nodes
     */
    public static ELNode.Nodes parse(String expression,
            boolean isDeferredSyntaxAllowedAsLiteral) {
        ELParser parser = new ELParser(expression,
                isDeferredSyntaxAllowedAsLiteral);
        while (parser.hasNextChar()) {
            String text = parser.skipUntilEL();
            if (text.length() > 0) {
                parser.expr.add(new ELNode.Text(text));
            }
            ELNode.Nodes elexpr = parser.parseEL();
            if (!elexpr.isEmpty()) {
                parser.expr.add(new ELNode.Root(elexpr, parser.type));
            }
        }
        return parser.expr;
    }

    /**
     * 解析EL表达式字符串 '${...}'. 目前只将EL分隔为函数和其他所有的东西.
     *
     * @return 表示EL表达式的ELNode.Nodes
     *
     * Note: 由于EL API不提供解析表达式所需的访问级别，因此不能重构使用标准EL实现.
     */
    private ELNode.Nodes parseEL() {

        StringBuilder buf = new StringBuilder();
        ELexpr = new ELNode.Nodes();
        curToken = null;
        prevToken = null;
        while (hasNext()) {
            curToken = nextToken();
            if (curToken instanceof Char) {
                if (curToken.toChar() == '}') {
                    break;
                }
                buf.append(curToken.toString());
            } else {
                // Output whatever is in buffer
                if (buf.length() > 0) {
                    ELexpr.add(new ELNode.ELText(buf.toString()));
                    buf.setLength(0);
                }
                if (!parseFunction()) {
                    ELexpr.add(new ELNode.ELText(curToken.toString()));
                }
            }
        }
        if (curToken != null) {
            buf.append(curToken.getWhiteSpace());
        }
        if (buf.length() > 0) {
            ELexpr.add(new ELNode.ELText(buf.toString()));
        }

        return ELexpr;
    }

    /**
     * 解析函数
     * FunctionInvokation ::= (identifier ':')? identifier
     * '(' (Expression (,Expression)*)? ')'
     * Note: 目前不解析参数
     */
    private boolean parseFunction() {
        if (!(curToken instanceof Id) || isELReserved(curToken.toTrimmedString()) ||
                prevToken instanceof Char && prevToken.toChar() == '.') {
            return false;
        }
        String s1 = null; // Function prefix
        String s2 = curToken.toTrimmedString(); // Function name
        int start = index - curToken.toString().length();
        Token original = curToken;
        if (hasNext()) {
            int mark = getIndex() - whiteSpace.length();
            curToken = nextToken();
            if (curToken.toChar() == ':') {
                if (hasNext()) {
                    Token t2 = nextToken();
                    if (t2 instanceof Id) {
                        s1 = s2;
                        s2 = t2.toTrimmedString();
                        if (hasNext()) {
                            curToken = nextToken();
                        }
                    }
                }
            }
            if (curToken.toChar() == '(') {
                ELexpr.add(new ELNode.Function(s1, s2, expression.substring(start, index - 1)));
                return true;
            }
            curToken = original;
            setIndex(mark);
        }
        return false;
    }

    /**
     * 测试EL中的ID是否为保留字
     */
    private boolean isELReserved(String id) {
        int i = 0;
        int j = reservedWords.length;
        while (i < j) {
            int k = (i + j) / 2;
            int result = reservedWords[k].compareTo(id);
            if (result == 0) {
                return true;
            }
            if (result < 0) {
                i = k + 1;
            } else {
                j = k;
            }
        }
        return false;
    }

    /**
     * 跳过直到EL表达式('${')达到, 允许转义序列 '\\' 和 '\$'.
     * @return 文本字符串到EL表达式
     */
    private String skipUntilEL() {
        StringBuilder buf = new StringBuilder();
        while (hasNextChar()) {
            char ch = nextChar();
            if (ch == '\\') {
                // Is this the start of a "\$" or "\#" escape sequence?
                char p0 = peek(0);
                if (p0 == '$' || (p0 == '#' && !isDeferredSyntaxAllowedAsLiteral)) {
                    buf.append(nextChar());
                } else {
                    buf.append(ch);
                }
            } else if ((ch == '$' || (ch == '#' && !isDeferredSyntaxAllowedAsLiteral)) &&
                    peek(0) == '{') {
                this.type = ch;
                nextChar();
                break;
            } else {
                buf.append(ch);
            }
        }
        return buf.toString();
    }


    /**
     * 转义 '$' 和 '#', 倒转{@link #skipUntilEL()}中未转义的执行, 但只包括 ${ 和 #{ , 因为转义  $ 和 # 是可选的.
     *
     * @param input 要转义的Non-EL输入
     * @param isDeferredSyntaxAllowedAsLiteral
     *
     * @return 输入的转义版本
     */
    static String escapeLiteralExpression(String input,
            boolean isDeferredSyntaxAllowedAsLiteral) {
        int len = input.length();
        int lastAppend = 0;
        StringBuilder output = null;
        for (int i = 0; i < len; i++) {
            char ch = input.charAt(i);
            if (ch =='$' || (!isDeferredSyntaxAllowedAsLiteral && ch == '#')) {
                if (i + 1 < len && input.charAt(i + 1) == '{') {
                    if (output == null) {
                        output = new StringBuilder(len + 20);
                    }
                    output.append(input.substring(lastAppend, i));
                    lastAppend = i + 1;
                    output.append('\\');
                    output.append(ch);
                }
            }
        }
        if (output == null) {
            return input;
        } else {
            output.append(input.substring(lastAppend, len));
            return output.toString();
        }
    }


    /**
     * 转义 '\\', '\'' 和 '\"', 倒转{@link #skipUntilEL()}中未转义的执行
     *
     * @param input 要转义的Non-EL输入
     * @param isDeferredSyntaxAllowedAsLiteral
     *
     * @return 输入的转义版本
     */
    private static String escapeELText(String input) {
        int len = input.length();
        char quote = 0;
        int lastAppend = 0;
        int start = 0;
        int end = len;

        // Look to see if the value is quoted
        String trimmed = input.trim();
        int trimmedLen = trimmed.length();
        if (trimmedLen > 1) {
            // Might be quoted
            quote = trimmed.charAt(0);
            if (quote == '\'' || quote == '\"') {
                if (trimmed.charAt(trimmedLen - 1) != quote) {
                    throw new IllegalArgumentException(Localizer.getMessage(
                            "org.apache.jasper.compiler.ELParser.invalidQuotesForStringLiteral",
                            input));
                }
                start = input.indexOf(quote) + 1;
                end = start + trimmedLen - 2;
            } else {
                quote = 0;
            }
        }

        StringBuilder output = null;
        for (int i = start; i < end; i++) {
            char ch = input.charAt(i);
            if (ch == '\\' || ch == quote) {
                if (output == null) {
                    output = new StringBuilder(len + 20);
                }
                output.append(input.substring(lastAppend, i));
                lastAppend = i + 1;
                output.append('\\');
                output.append(ch);
            }
        }
        if (output == null) {
            return input;
        } else {
            output.append(input.substring(lastAppend, len));
            return output.toString();
        }
    }


    /*
     * @return true 如果在EL表达式缓冲区中除了空格之外还有其他东西.
     */
    private boolean hasNext() {
        skipSpaces();
        return hasNextChar();
    }

    private String getAndResetWhiteSpace() {
        String result = whiteSpace;
        whiteSpace = "";
        return result;
    }

    /*
     * 实现注意: 这个方法假设总是在调用hasNext()之前发生, 为了正确处理空格.
     *
     * @return EL表达式缓冲区中的下一个 token.
     */
    private Token nextToken() {
        prevToken = curToken;
        if (hasNextChar()) {
            char ch = nextChar();
            if (Character.isJavaIdentifierStart(ch)) {
                int start = index - 1;
                while (index < expression.length() &&
                        Character.isJavaIdentifierPart(
                                ch = expression.charAt(index))) {
                    nextChar();
                }
                return new Id(getAndResetWhiteSpace(), expression.substring(start, index));
            }

            if (ch == '\'' || ch == '"') {
                return parseQuotedChars(ch);
            } else {
                // For now...
                return new Char(getAndResetWhiteSpace(), ch);
            }
        }
        return null;
    }

    /*
     * 解析单引号或双引号中的字符串, 允许转义序列 '\\', and ('\"', or "\'")
     */
    private Token parseQuotedChars(char quote) {
        StringBuilder buf = new StringBuilder();
        buf.append(quote);
        while (hasNextChar()) {
            char ch = nextChar();
            if (ch == '\\') {
                ch = nextChar();
                if (ch == '\\' || ch == '\'' || ch == '\"') {
                    buf.append(ch);
                } else {
                    throw new IllegalArgumentException(Localizer.getMessage(
                            "org.apache.jasper.compiler.ELParser.invalidQuoting",
                            expression));
                }
            } else if (ch == quote) {
                buf.append(ch);
                break;
            } else {
                buf.append(ch);
            }
        }
        return new QuotedString(getAndResetWhiteSpace(), buf.toString());
    }

    /*
     * 处理EL表达式缓冲区中字符的低层次解析方法的集合.
     */
    private void skipSpaces() {
        int start = index;
        while (hasNextChar()) {
            char c = expression.charAt(index);
            if (c > ' ')
                break;
            index++;
        }
        whiteSpace = expression.substring(start, index);
    }

    private boolean hasNextChar() {
        return index < expression.length();
    }

    private char nextChar() {
        if (index >= expression.length()) {
            return (char) -1;
        }
        return expression.charAt(index++);
    }

    private char peek(int advance) {
        int target = index + advance;
        if (target >= expression.length()) {
            return (char) -1;
        }
        return expression.charAt(target);
    }

    private int getIndex() {
        return index;
    }

    private void setIndex(int i) {
        index = i;
    }

    /*
     * 表示EL表达式字符串中的token
     */
    private static class Token {

        protected final String whiteSpace;

        Token(String whiteSpace) {
            this.whiteSpace = whiteSpace;
        }

        char toChar() {
            return 0;
        }

        @Override
        public String toString() {
            return whiteSpace;
        }

        String toTrimmedString() {
            return "";
        }

        String getWhiteSpace() {
            return whiteSpace;
        }
    }

    /*
     * 表示EL中的 ID
     */
    private static class Id extends Token {
        String id;

        Id(String whiteSpace, String id) {
            super(whiteSpace);
            this.id = id;
        }

        @Override
        public String toString() {
            return whiteSpace + id;
        }

        @Override
        String toTrimmedString() {
            return id;
        }
    }

    /*
     * 表示EL中的 character token
     */
    private static class Char extends Token {

        private char ch;

        Char(String whiteSpace, char ch) {
            super(whiteSpace);
            this.ch = ch;
        }

        @Override
        char toChar() {
            return ch;
        }

        @Override
        public String toString() {
            return whiteSpace + ch;
        }

        @Override
        String toTrimmedString() {
            return "" + ch;
        }
    }

    /*
     * 表示EL中带引号（单或双）字符串token
     */
    private static class QuotedString extends Token {

        private String value;

        QuotedString(String whiteSpace, String v) {
            super(whiteSpace);
            this.value = v;
        }

        @Override
        public String toString() {
            return whiteSpace + value;
        }

        @Override
        String toTrimmedString() {
            return value;
        }
    }

    public char getType() {
        return type;
    }


    static class TextBuilder extends ELNode.Visitor {

        protected final boolean isDeferredSyntaxAllowedAsLiteral;
        protected final StringBuilder output = new StringBuilder();

        protected TextBuilder(boolean isDeferredSyntaxAllowedAsLiteral) {
            this.isDeferredSyntaxAllowedAsLiteral = isDeferredSyntaxAllowedAsLiteral;
        }

        public String getText() {
            return output.toString();
        }

        @Override
        public void visit(Root n) throws JasperException {
            output.append(n.getType());
            output.append('{');
            n.getExpression().visit(this);
            output.append('}');
        }

        @Override
        public void visit(Function n) throws JasperException {
            output.append(escapeLiteralExpression(n.getOriginalText(), isDeferredSyntaxAllowedAsLiteral));
            output.append('(');
        }

        @Override
        public void visit(Text n) throws JasperException {
            output.append(escapeLiteralExpression(n.getText(),isDeferredSyntaxAllowedAsLiteral));
        }

        @Override
        public void visit(ELText n) throws JasperException {
            output.append(escapeELText(n.getText()));
        }
    }
}
