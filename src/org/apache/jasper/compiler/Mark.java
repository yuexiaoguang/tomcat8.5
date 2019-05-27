package org.apache.jasper.compiler;

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.jasper.JspCompilationContext;

/**
 * JSP输入中的一个点. 
 */
final class Mark {

    // 当前流中的位置
    int cursor, line, col;

    // 当前流
    char[] stream = null;

    // 当前文件名
    private String fileName;

    private JspCompilationContext ctxt;

    /**
     * @param reader 所属的JspReader
     * @param inStream 此标记的当前流
     * @param name JSP文件名
     */
    Mark(JspReader reader, char[] inStream, String name) {
        this.ctxt = reader.getJspCompilationContext();
        this.stream = inStream;
        this.cursor = 0;
        this.line = 1;
        this.col = 1;
        this.fileName = name;
    }


    Mark(Mark other) {
       init(other, false);
    }

    void update(int cursor, int line, int col) {
        this.cursor = cursor;
        this.line = line;
        this.col = col;
    }

    void init(Mark other, boolean singleFile) {
        this.cursor = other.cursor;
        this.line = other.line;
        this.col = other.col;

        if (!singleFile) {
            this.ctxt = other.ctxt;
            this.stream = other.stream;
            this.fileName = other.fileName;
        }
    }


    Mark(JspCompilationContext ctxt, String filename, int line, int col) {
        this.ctxt = ctxt;
        this.stream = null;
        this.cursor = 0;
        this.line = line;
        this.col = col;
        this.fileName = filename;
    }


    public int getLineNumber() {
        return line;
    }

    public int getColumnNumber() {
        return col;
    }

    @Override
    public String toString() {
        return getFile()+"("+line+","+col+")";
    }

    public String getFile() {
        return this.fileName;
    }

    /**
     * 获取与此Mark关联的资源的URL
     *
     * @return 与此Mark关联的资源的URL
     *
     * @exception MalformedURLException 如果资源的路径是不正确的
     */
    public URL getURL() throws MalformedURLException {
        return ctxt.getResource(getFile());
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Mark) {
            Mark m = (Mark) other;
            return this.cursor == m.cursor && this.line == m.line && this.col == m.col;
        }
        return false;
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + col;
        result = prime * result + cursor;
        result = prime * result + line;
        return result;
    }
}
