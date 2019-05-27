package org.apache.jasper.compiler;

import java.io.PrintWriter;

/**
 * 用于生成servlet.
 */
public class ServletWriter implements AutoCloseable {

    private static final int TAB_WIDTH = 2;
    private static final String SPACES = "                              ";

    /**
     * 当前的缩进级别.
     */
    private int indent = 0;
    private int virtual_indent = 0;

    /**
     * The sink writer.
     */
    private final PrintWriter writer;

    /**
     * servlet 行号, 从 1 开始
     */
    private int javaLine = 1;


    public ServletWriter(PrintWriter writer) {
        this.writer = writer;
    }

    @Override
    public void close() {
        writer.close();
    }


    // -------------------- Access informations --------------------

    public int getJavaLine() {
        return javaLine;
    }


    // -------------------- Formatting --------------------

    public void pushIndent() {
        virtual_indent += TAB_WIDTH;
        if (virtual_indent >= 0 && virtual_indent <= SPACES.length())
            indent = virtual_indent;
    }

    public void popIndent() {
        virtual_indent -= TAB_WIDTH;
        if (virtual_indent >= 0 && virtual_indent <= SPACES.length())
            indent = virtual_indent;
    }

    /**
     * 打印给定的字符串, 后跟'\n'
     * @param s The string
     */
    public void println(String s) {
        javaLine++;
        writer.println(s);
    }

    /**
     * 打印'\n'
     */
    public void println() {
        javaLine++;
        writer.println("");
    }

    /**
     * 打印当前缩进
     */
    public void printin() {
        writer.print(SPACES.substring(0, indent));
    }

    /**
     * 打印当前缩进, 后跟给定的字符串
     * @param s The string
     */
    public void printin(String s) {
        writer.print(SPACES.substring(0, indent));
        writer.print(s);
    }

    /**
     * 打印当前缩进, 然后是字符串, 和 '\n'.
     * @param s The string
     */
    public void printil(String s) {
        javaLine++;
        writer.print(SPACES.substring(0, indent));
        writer.println(s);
    }

    public void print(char c) {
        writer.print(c);
    }

    public void print(int i) {
        writer.print(i);
    }

    /**
     * 字符串不能包含任何 '\n', 否则，行计数将关闭.
     * @param s The string
     */
    public void print(String s) {
        writer.print(s);
    }

    /**
     * 如果字符串跨越多行, 行计数将相应地调整.
     * @param s The string
     */
    public void printMultiLn(String s) {
        int index = 0;

        // 寻找字符串内隐藏的换行符
        while ((index=s.indexOf('\n',index)) > -1 ) {
            javaLine++;
            index++;
        }

        writer.print(s);
    }
}
