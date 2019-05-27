package org.apache.jasper.compiler;

import java.io.CharArrayWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.runtime.ExceptionUtils;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.Jar;

/**
 * JspReader 是JSP解析器的输入缓冲区. 它应该允许无限的lookahead 和 pushback. 它还有一组解析实用工具方法用于理解htmlesque.
 */
class JspReader {

    /**
     * Logger.
     */
    private final Log log = LogFactory.getLog(JspReader.class);

    /**
     * 文件中的当前位置.
     */
    private Mark current;

    /**
     * 编译上下文.
     */
    private final JspCompilationContext context;

    /**
     * Jasper错误调度器.
     */
    private final ErrorDispatcher err;

    /**
     * @param ctxt 编译上下文
     * @param fname 文件名
     * @param encoding 文件编码
     * @param jar ?
     * @param err 错误调度器
     * 
     * @throws JasperException 一个Jasper内部错误
     * @throws FileNotFoundException 如果未找到JSP错误(或不可读)
     * @throws IOException 如果发生IO级别错误, 例如读取文件
     */
    public JspReader(JspCompilationContext ctxt,
                     String fname,
                     String encoding,
                     Jar jar,
                     ErrorDispatcher err)
            throws JasperException, FileNotFoundException, IOException {

        this(ctxt, fname, JspUtil.getReader(fname, encoding, jar, ctxt, err),
             err);
    }

    /**
     * @param ctxt 编译上下文
     * @param fname 文件名
     * @param reader JSP源文件的读取器
     * @param err 错误调度器er
     *
     * @throws JasperException 如果解析JSP文件出现错误
     */
    public JspReader(JspCompilationContext ctxt,
                     String fname,
                     InputStreamReader reader,
                     ErrorDispatcher err)
            throws JasperException {

        this.context = ctxt;
        this.err = err;

        try {
            CharArrayWriter caw = new CharArrayWriter();
            char buf[] = new char[1024];
            for (int i = 0 ; (i = reader.read(buf)) != -1 ;)
                caw.write(buf, 0, i);
            caw.close();
            current = new Mark(this, caw.toCharArray(), fname);
        } catch (Throwable ex) {
            ExceptionUtils.handleThrowable(ex);
            log.error("Exception parsing file ", ex);
            err.jspError("jsp.error.file.cannot.read", fname);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception any) {
                    if(log.isDebugEnabled()) {
                        log.debug("Exception closing reader: ", any);
                    }
                }
            }
        }
    }


    /**
     * @return 这个JspReader关联的JSP编译上下文
     */
    JspCompilationContext getJspCompilationContext() {
        return context;
    }

    /**
     * 检查当前文件是否有更多的输入.
     *
     * @return True if more reading is possible
     */
    boolean hasMoreInput() {
        return current.cursor < current.stream.length;
    }

    int nextChar() {
        if (!hasMoreInput())
            return -1;

        int ch = current.stream[current.cursor];

        current.cursor++;

        if (ch == '\n') {
            current.line++;
            current.col = 0;
        } else {
            current.col++;
        }
        return ch;
    }

    /**
     * 比调用 {@link #mark()} & {@link #nextChar()}更快的方法.
     * 但是, 如果只在JspReader中使用标记，则此方法是安全的.
     */
    private int nextChar(Mark mark) {
        if (!hasMoreInput()) {
            return -1;
        }

        int ch = current.stream[current.cursor];

        mark.init(current, true);

        current.cursor++;

        if (ch == '\n') {
            current.line++;
            current.col = 0;
        } else {
            current.col++;
        }
        return ch;
    }

    /**
     * 搜索给定的字符, 如果找到, 然后将当前游标和游标点标记为下一个字符.
     */
    private Boolean indexOf(char c, Mark mark) {
        if (!hasMoreInput())
            return null;

        int end = current.stream.length;
        int ch;
        int line = current.line;
        int col = current.col;
        int i = current.cursor;
        for(; i < end; i ++) {
           ch = current.stream[i];

           if (ch == c) {
               mark.update(i, line, col);
           }
           if (ch == '\n') {
                line++;
                col = 0;
            } else {
                col++;
            }
           if (ch == c) {
               current.update(i+1, line, col);
               return Boolean.TRUE;
           }
        }
        current.update(i, line, col);
        return Boolean.FALSE;
    }

    /**
     * 用一个字符备份当前光标, 假设 current.cursor > 0, 而且推回来的字符不是 '\n'.
     */
    void pushChar() {
        current.cursor--;
        current.col--;
    }

    String getText(Mark start, Mark stop) {
        Mark oldstart = mark();
        reset(start);
        CharArrayWriter caw = new CharArrayWriter();
        while (!markEquals(stop)) {
            caw.write(nextChar());
        }
        caw.close();
        setCurrent(oldstart);
        return caw.toString();
    }

    /**
     * 在不移动光标的情况下, 向前读取一个字符.
     *
     * @return 下一个字符，或 -1，如果没有进一步的输入可用
     */
    int peekChar() {
        return peekChar(0);
    }

    /**
     * 在不移动光标的情况下, 向前读取一个字符.
     *
     * @param readAhead 要提前读取的字符数. NOTE: 从零开始.
     *
     * @return 请求的字符, 或 -1 如果输入的结尾到达第一的位置
     */
    int peekChar(int readAhead) {
        int target = current.cursor + readAhead;
        if (target < current.stream.length) {
            return current.stream[target];
        }
        return -1;
    }

    Mark mark() {
        return new Mark(current);
    }


    /**
     * 当做比较时时，避免调用 {@link #mark()}.
     */
    private boolean markEquals(Mark another) {
       return another.equals(current);
    }

    void reset(Mark mark) {
        current = new Mark(mark);
    }

    /**
     * 类似于{@link #reset(Mark)}， 但不会创建新的Mark.
     * 因此, 参数标记不能在其他地方使用.
     */
    private void setCurrent(Mark mark) {
       current = mark;
    }

    /**
     * 在流中搜索字符串的匹配项
     * @param string 要匹配的字符串
     * @return <strong>true</strong>表示找到了一个, 流中的当前位置位于搜索字符串之后,
     * 		否则<strong>false</strong>, 流中位置不变.
     */
    boolean matches(String string) {
       int len = string.length();
       int cursor = current.cursor;
       int streamSize = current.stream.length;
       if (cursor + len < streamSize) { //Try to scan in memory
           int line = current.line;
           int col = current.col;
           int ch;
           int i = 0;
           for(; i < len; i ++) {
               ch = current.stream[i+cursor];
               if (string.charAt(i) != ch) {
                   return false;
               }
               if (ch == '\n') {
                  line ++;
                  col = 0;
               } else {
                  col++;
               }
           }
           current.update(i+cursor, line, col);
       } else {
           Mark mark = mark();
           int ch = 0;
           int i = 0;
           do {
               ch = nextChar();
               if (((char) ch) != string.charAt(i++)) {
                   setCurrent(mark);
                   return false;
               }
           } while (i < len);
       }
       return true;
    }

    boolean matchesETag(String tagName) {
        Mark mark = mark();

        if (!matches("</" + tagName))
            return false;
        skipSpaces();
        if (nextChar() == '>')
            return true;

        setCurrent(mark);
        return false;
    }

    boolean matchesETagWithoutLessThan(String tagName) {
       Mark mark = mark();

       if (!matches("/" + tagName))
           return false;
       skipSpaces();
       if (nextChar() == '>')
           return true;

       setCurrent(mark);
       return false;
    }


    /**
     * 给定字符串后面是否有可选空格.
     * 如果有, 返回true, 并跳过这些空格和字符.
     * 如果没有, 返回false, 位置恢复到我们以前的位置.
     */
    boolean matchesOptionalSpacesFollowedBy(String s) {
        Mark mark = mark();

        skipSpaces();
        boolean result = matches( s );
        if( !result ) {
            setCurrent(mark);
        }

        return result;
    }

    int skipSpaces() {
        int i = 0;
        while (hasMoreInput() && isSpace()) {
            i++;
            nextChar();
        }
        return i;
    }

    /**
     * 跳过直到给定字符串在流中匹配为止.
     * 返回的时候, 上下文被定位在匹配结束之前.
     *
     * @param s 要匹配的字符串.
     * @return 一个非null <code>Mark</code>实例 (定位在搜索字符串之前), 或者<strong>null</strong>
     */
    Mark skipUntil(String limit) {
        Mark ret = mark();
        int limlen = limit.length();
        char firstChar = limit.charAt(0);
        Boolean result = null;
        Mark restart = null;

    skip:
        while((result = indexOf(firstChar, ret)) != null) {
           if (result.booleanValue()) {
               if (restart != null) {
                   restart.init(current, true);
               } else {
                   restart = mark();
               }
               for (int i = 1 ; i < limlen ; i++) {
                   if (peekChar() == limit.charAt(i)) {
                       nextChar();
                   } else {
                       current.init(restart, true);
                       continue skip;
                   }
               }
               return ret;
            }
        }
        return null;
    }

    /**
     * 跳过直到给定字符串在流中匹配为止, 但是忽略最初被'\'转义的字符.
     * 返回的时候, 上下文被定位在匹配结束之前.
     *
     * @param s 要匹配的字符串.
     * @param ignoreEL <code>true</code>如果看起来像EL的东西不应该被当作EL处理
     * 
     * @return 一个非null <code>Mark</code>实例 (定位在搜索字符串之前), 或者<strong>null</strong>
     */
    Mark skipUntilIgnoreEsc(String limit, boolean ignoreEL) {
        Mark ret = mark();
        int limlen = limit.length();
        int ch;
        int prev = 'x';        // Doesn't matter
        char firstChar = limit.charAt(0);
    skip:
        for (ch = nextChar(ret) ; ch != -1 ; prev = ch, ch = nextChar(ret)) {
            if (ch == '\\' && prev == '\\') {
                ch = 0;                // Double \ is not an escape char anymore
            } else if (prev == '\\') {
                continue;
            } else if (!ignoreEL && (ch == '$' || ch == '#') && peekChar() == '{' ) {
                // Move beyond the '{'
                nextChar();
                skipELExpression();
            } else if (ch == firstChar) {
                for (int i = 1 ; i < limlen ; i++) {
                    if (peekChar() == limit.charAt(i))
                        nextChar();
                    else
                        continue skip;
                }
                return ret;
            }
        }
        return null;
    }

    /**
     * 跳过，直到在流中匹配到给定的结束标签.
     * 返回的时候, 上下文被定位在匹配结束之前.
     *
     * @param tag 要匹配ETag (</tag>)的标签名称
     * @return 一个non-null <code>Mark</code>实例(立即定位在ETag之前), 或者<strong>null</strong>
     */
    Mark skipUntilETag(String tag) {
        Mark ret = skipUntil("</" + tag);
        if (ret != null) {
            skipSpaces();
            if (nextChar() != '>')
                ret = null;
        }
        return ret;
    }

    /**
     * 解析ELExpressionBody， 其是${} 或 #{}表达式的主体.
     * 初始化的读取器位置，最好是 '${' 或 '#{' 字符之后.
     * <p>
     * 如果成功, 这个方法返回<code>Mark</code>, 为了终止'}'之前的最后一个字符, 而且reader 在'}'字符之后.
     * 如果没有终止 '}', 这个方法返回<code>null</code>.
     * <p>
     * 从EL 3.0开始, 支持的嵌套的 {}.
     *
     * @return EL表达式的最后一个字符的Mark, 或<code>null</code>
     */
    Mark skipELExpression() {
        // ELExpressionBody.
        //  Starts with "#{" or "${".  Ends with "}".
        //  可能包含引号 "{", "}", '{', 或 '}' 和嵌套的 "{...}"
        Mark last = mark();
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        int nesting = 0;
        int currentChar;
        do {
            currentChar = nextChar(last);
            while (currentChar == '\\' && (singleQuoted || doubleQuoted)) {
                // 跳过'\'后面的字符
                // 不需要更新 'last', 因为这些字符都不能是封闭的 '}'.
                nextChar();
                currentChar = nextChar();
            }
            if (currentChar == -1) {
                return null;
            }
            if (currentChar == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
            } else if (currentChar == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
            } else if (currentChar == '{' && !doubleQuoted && !singleQuoted) {
                nesting++;
            } else if (currentChar =='}' && !doubleQuoted && !singleQuoted) {
                // Note: 也匹配嵌套的最后的 '}', 将设置为 -1 - 因此测试继续循环(currentChar != '}' || nesting > -1 ||...)期间, 直到检测到最后的 '}'
                nesting--;
            }
        } while (currentChar != '}' || singleQuoted || doubleQuoted || nesting > -1);

        return last;
    }

    final boolean isSpace() {
        // Note: 如果这个逻辑改变, 也更新 Node.TemplateText.rtrim()
        return peekChar() <= ' ';
    }

    /**
     * 解析空格分隔的token.
     * 如果引号了token 将消耗所有字符到匹配的引号, 否则, 它消耗到第一个分隔符.
     *
     * @param quoted <strong>true</strong>接受引号字符串.
     */
    String parseToken(boolean quoted) throws JasperException {
        StringBuilder StringBuilder = new StringBuilder();
        skipSpaces();
        StringBuilder.setLength(0);

        if (!hasMoreInput()) {
            return "";
        }

        int ch = peekChar();

        if (quoted) {
            if (ch == '"' || ch == '\'') {

                char endQuote = ch == '"' ? '"' : '\'';
                // 消耗打开的引号:
                ch = nextChar();
                for (ch = nextChar(); ch != -1 && ch != endQuote;
                         ch = nextChar()) {
                    if (ch == '\\')
                        ch = nextChar();
                    StringBuilder.append((char) ch);
                }
                // 检查引号的结尾, 跳过关闭的引号:
                if (ch == -1) {
                    err.jspError(mark(), "jsp.error.quotes.unterminated");
                }
            } else {
                err.jspError(mark(), "jsp.error.attr.quoted");
            }
        } else {
            if (!isDelimiter()) {
                // 读取值直到找到分隔符:
                do {
                    ch = nextChar();
                    // 小心引号.
                    if (ch == '\\') {
                        if (peekChar() == '"' || peekChar() == '\'' ||
                               peekChar() == '>' || peekChar() == '%')
                            ch = nextChar();
                    }
                    StringBuilder.append((char) ch);
                } while (!isDelimiter());
            }
        }

        return StringBuilder.toString();
    }


    /**
     * 解析工具 - 当前字符是令牌分隔符吗?
     * 分隔符目前定义为 =, &gt;, &lt;, ", ' 或<code>isSpace</code>定义的任何空格字符.
     *
     * @return A boolean.
     */
    private boolean isDelimiter() {
        if (! isSpace()) {
            int ch = peekChar();
            // 查找单个char工作定界符:
            if (ch == '=' || ch == '>' || ch == '"' || ch == '\''
                    || ch == '/') {
                return true;
            }
            // Look for an end-of-comment or end-of-tag:
            if (ch == '-') {
                Mark mark = mark();
                if (((ch = nextChar()) == '>')
                        || ((ch == '-') && (nextChar() == '>'))) {
                    setCurrent(mark);
                    return true;
                } else {
                    setCurrent(mark);
                    return false;
                }
            }
            return false;
        } else {
            return true;
        }
    }
}
