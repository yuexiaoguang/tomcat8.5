package org.apache.jasper.compiler;

/**
 * 将JSP属性值转换为不带引号的等效值. 该属性可能包含EL表达式, 在这种情况下，需要注意避免任何含糊之处.
 * 例如, 考虑属性值 "${1+1}" 和 "\${1+1}". 不加引号后, 都出现为 "${1+1}", 但是可能第一个应该计算为 "2", 第二个为 "${1+1}".
 * \, $, # 需要特殊对待, 来确保没有歧义. JSP属性去引号的涵盖 \\, \", \', \$, \#, %\&gt;, &lt;\%, &amp;apos; and &amp;quot;
 */
public class AttributeParser {

    /**
     * 将提供的输入String解析为JSP属性并返回未加引号的值.
     *
     * @param input         The input.
     * @param quote         属性的引号字符或脚本表达式为0.
     * @param isELIgnored   是否在定义JSP属性的页面上忽略表达式语言.
     * @param isDeferredSyntaxAllowedAsLiteral 延迟表达式被视为文字?
     * @param strict        是否应严格应用JSP.1.6中用于转义引号的规则?
     * @param quoteAttributeEL 是否应将JSP.1.6中用于转义属性的规则应用于属性值中的EL?
     * 
     * @return              一个不带引号的JSP属性, 如果它包含表达式语言, 可以安全地传递给EL处理器而不用担心歧义.
     */
    public static String getUnquoted(String input, char quote,
            boolean isELIgnored, boolean isDeferredSyntaxAllowedAsLiteral,
            boolean strict, boolean quoteAttributeEL) {
        return (new AttributeParser(input, quote, isELIgnored,
                isDeferredSyntaxAllowedAsLiteral, strict, quoteAttributeEL)).getUnquoted();
    }

    /* 带引号的输入字符串. */
    private final String input;

    /* 用于属性的引号 - null 用于脚本表达式. */
    private final char quote;

    /* 是否忽略表达语言. \$ 和 \# 被视为文字而不是带引号的值. */
    private final boolean isELIgnored;

    /* 延迟表达式被视为文字 */
    private final boolean isDeferredSyntaxAllowedAsLiteral;

    /* 是否应严格应用JSP.1.6中用于转义引号的规则?
     */
    private final boolean strict;

    private final boolean quoteAttributeEL;

    /* 表达式的类型 ($ or #). 文字的类型为 null. */
    private final char type;

    /* 带引号的输入字符串的长度. */
    private final int size;

    /* 跟踪输入String中解析器的当前位置. */
    private int i = 0;

    /* nextChar() 返回的最后一个字符是否转义. */
    private boolean lastChEscaped = false;

    /* 不带引号的结果. */
    private final StringBuilder result;


    /**
     * 用于测试.
     */
    private AttributeParser(String input, char quote,
            boolean isELIgnored, boolean isDeferredSyntaxAllowedAsLiteral,
            boolean strict, boolean quoteAttributeEL) {
        this.input = input;
        this.quote = quote;
        this.isELIgnored = isELIgnored;
        this.isDeferredSyntaxAllowedAsLiteral =
            isDeferredSyntaxAllowedAsLiteral;
        this.strict = strict;
        this.quoteAttributeEL = quoteAttributeEL;
        this.type = getType(input);
        this.size = input.length();
        result = new StringBuilder(size);
    }

    /*
     * 通过输入查找文字和表达式，直到输入全部被读取.
     */
    private String getUnquoted() {
        while (i < size) {
            parseLiteral();
            parseEL();
        }
        return result.toString();
    }

    /*
     * 此方法获取下一个未加引号的字符并查找 - 需要转换为EL处理的文字
     *   \ -> type{'\\'}
     *   $ -> type{'$'}
     *   # -> type{'#'}
     * - EL 开始
     *   ${
     *   #{
     * 注意上面的所有示例都不包括在Java代码中使用值所需的转义.
     */
    private void parseLiteral() {
        boolean foundEL = false;
        while (i < size && !foundEL) {
            char ch = nextChar();
            if (!isELIgnored && ch == '\\') {
                if (type == 0) {
                    result.append("\\");
                } else {
                    result.append(type);
                    result.append("{'\\\\'}");
                }
            } else if (!isELIgnored && ch == '$' && lastChEscaped){
                if (type == 0) {
                    result.append("\\$");
                } else {
                    result.append(type);
                    result.append("{'$'}");
                }
            } else if (!isELIgnored && ch == '#' && lastChEscaped){
                // 如果 isDeferredSyntaxAllowedAsLiteral==true, \# 不会被视为转义
                if (type == 0) {
                    result.append("\\#");
                } else {
                    result.append(type);
                    result.append("{'#'}");
                }
            } else if (ch == type){
                if (i < size) {
                    char next = input.charAt(i);
                    if (next == '{') {
                        foundEL = true;
                        // 回到EL的开始
                        i--;
                    } else {
                        result.append(ch);
                    }
                } else {
                    result.append(ch);
                }
            } else {
                result.append(ch);
            }
        }
    }

    /*
     * 一旦进入EL, 无需去掉引号或转换任何内容. EL终止于 '}'.
     * '}' 的唯一的其他有效位置在一个StringLiteral 中. 文字由 '\'' 或 '\"'分隔.
     * '\'' 或 '\"' 的唯一的其他有效位置也在一个 StringLiteral 中.
     * StringLiteral 中带引号的字符串必须被转义, 如果相同的引号字符用于分隔 StringLiteral.
     */
    private void parseEL() {
        boolean endEL = false;
        boolean insideLiteral = false;
        char literalQuote = 0;
        while (i < size && !endEL) {
            char ch;
            if (quoteAttributeEL) {
                ch = nextChar();
            } else {
                ch = input.charAt(i++);
            }
            if (ch == '\'' || ch == '\"') {
                if (insideLiteral) {
                    if (literalQuote == ch) {
                        insideLiteral = false;
                    }
                } else {
                    insideLiteral = true;
                    literalQuote = ch;
                }
                result.append(ch);
            } else if (ch == '\\') {
                result.append(ch);
                if (insideLiteral && size < i) {
                    if (quoteAttributeEL) {
                        ch = nextChar();
                    } else {
                        ch = input.charAt(i++);
                    }
                    result.append(ch);
                }
            } else if (ch == '}') {
                if (!insideLiteral) {
                    endEL = true;
                }
                result.append(ch);
            } else {
                result.append(ch);
            }
        }
    }

    /*
     * 返回下一个不带引号的字符, 并设置 lastChEscaped 标志指示它是否被引号/转义.
     * &apos; 转义为 '
     * &quot; 转义为 "
     * \" 转义为 "
     * \' 转义为 '
     * \\ 转义为 \
     * \$ 转义为 $ 如果不忽略EL
     * \# 转义为 # 如果不忽略EL
     * <\% 转义为 <%
     * %\> 转义为 %>
     */
    private char nextChar() {
        lastChEscaped = false;
        char ch = input.charAt(i);

        if (ch == '&') {
            if (i + 5 < size && input.charAt(i + 1) == 'a' &&
                    input.charAt(i + 2) == 'p' && input.charAt(i + 3) == 'o' &&
                    input.charAt(i + 4) == 's' && input.charAt(i + 5) == ';') {
                ch = '\'';
                i += 6;
            } else if (i + 5 < size && input.charAt(i + 1) == 'q' &&
                    input.charAt(i + 2) == 'u' && input.charAt(i + 3) == 'o' &&
                    input.charAt(i + 4) == 't' && input.charAt(i + 5) == ';') {
                ch = '\"';
                i += 6;
            } else {
                ++i;
            }
        } else if (ch == '\\' && i + 1 < size) {
            ch = input.charAt(i + 1);
            if (ch == '\\' || ch == '\"' || ch == '\'' ||
                    (!isELIgnored &&
                            (ch == '$' ||
                                    (!isDeferredSyntaxAllowedAsLiteral &&
                                            ch == '#')))) {
                i += 2;
                lastChEscaped = true;
            } else {
                ch = '\\';
                ++i;
            }
        } else if (ch == '<' && (i + 2 < size) && input.charAt(i + 1) == '\\' &&
                input.charAt(i + 2) == '%') {
            // 注意这是一个黑客, 从 nextChar 只返回一个 char
            // 这是安全的, 因为 <% EL或文字不需要特殊处理
            result.append('<');
            i+=3;
            return '%';
        } else if (ch == '%' && i + 2 < size && input.charAt(i + 1) == '\\' &&
                input.charAt(i + 2) == '>') {
            // 注意这是一个黑客, 从 nextChar 只返回一个 char
            // 这是安全的, 因为 <% EL或文字不需要特殊处理
            result.append('%');
            i+=3;
            return '>';
        } else if (ch == quote && strict) {
            String msg = Localizer.getMessage("jsp.error.attribute.noescape",
                    input, ""+ quote);
            throw new IllegalArgumentException(msg);
        } else {
            ++i;
        }

        return ch;
    }

    /*
     * 通过查找第一个不带引号的 ${ 或 #{表达式来确定表达式的类型.
     */
    private char getType(String value) {
        if (value == null) {
            return 0;
        }

        if (isELIgnored) {
            return 0;
        }

        int j = 0;
        int len = value.length();
        char current;

        while (j < len) {
            current = value.charAt(j);
            if (current == '\\') {
                // 转义字符 - 跳过一个字符
                j++;
            } else if (current == '#' && !isDeferredSyntaxAllowedAsLiteral) {
                if (j < (len -1) && value.charAt(j + 1) == '{') {
                    return '#';
                }
            } else if (current == '$') {
                if (j < (len - 1) && value.charAt(j + 1) == '{') {
                    return '$';
                }
            }
            j++;
        }
        return 0;
    }
}
