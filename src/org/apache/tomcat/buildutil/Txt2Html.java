package org.apache.tomcat.buildutil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

/**
 * Ant任务将给定的文件集从Text转换为HTML.
 * 插入包含预标记的HTML标头，并将特殊字符替换为HTML转义等效字符.
 *
 * <p>ant脚本目前使用此任务来构建我们的示例</p>
 */
public class Txt2Html extends Task {

    /** 包含结果文件的目录 */
    private File todir;

    /** 要转换为HTML的文件 */
    private final List<FileSet> filesets = new LinkedList<>();

    /**
     * 源文件的编码 (.java 和 .jsp). 一旦使用 UTF-8, 这个需要更新.
     */
    private static final String SOURCE_ENCODING = "ISO-8859-1";

    /**
     * 行终止符, 用于分隔生成的HTML页面的行, 独立于 "line.separator" 系统属性.
     */
    private static final String LINE_SEPARATOR = "\r\n";

    /**
     * 设置包含结果文件的目录
     *
     * @param todir 目录
     */
    public void setTodir( File todir ) {
        this.todir = todir;
    }

    /**
     * 设置要转换为HTML的文件
     *
     * @param fs 要转换的文件集.
     */
    public void addFileset( FileSet fs ) {
        filesets.add( fs );
    }

    /**
     * 执行转换
     *
     * @throws BuildException 如果在执行此任务期间发生错误.
     */
    @Override
    public void execute()
        throws BuildException
    {
        int count = 0;

        // 逐步浏览每个文件并进行转换.
        Iterator<FileSet> iter = filesets.iterator();
        while( iter.hasNext() ) {
            FileSet fs = iter.next();
            DirectoryScanner ds = fs.getDirectoryScanner(getProject());
            File basedir = ds.getBasedir();
            String[] files = ds.getIncludedFiles();
            for( int i = 0; i < files.length; i++ ) {
                File from = new File( basedir, files[i] );
                File to = new File( todir, files[i] + ".html" );
                if( !to.exists() ||
                    (from.lastModified() > to.lastModified()) )
                {
                    log( "Converting file '" + from.getAbsolutePath() +
                        "' to '" + to.getAbsolutePath(), Project.MSG_VERBOSE );
                    try {
                        convert( from, to );
                    }
                    catch( IOException e ) {
                        throw new BuildException( "Could not convert '" +
                            from.getAbsolutePath() + "' to '" +
                            to.getAbsolutePath() + "'", e );
                    }
                    count++;
                }
            }
            if( count > 0 ) {
                log( "Converted " + count + " file" + (count > 1 ? "s" : "") +
                    " to " + todir.getAbsolutePath() );
            }
        }
    }

    /**
     * 执行实际的复制和转换
     *
     * @param from 输入文件
     * @param to 输出文件
     * @throws IOException 如果在转换期间发生错误，则抛出该异常
     */
    private void convert( File from, File to )
        throws IOException
    {
        // Open files:
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                new FileInputStream(from), SOURCE_ENCODING))) {
            try (PrintWriter out = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(to), "UTF-8"))) {

                // Output header:
                out.print("<!DOCTYPE html><html><head><meta charset=\"UTF-8\" />"
                        + "<title>Source Code</title></head><body><pre>" );

                // Convert, line-by-line:
                String line;
                while( (line = in.readLine()) != null ) {
                    StringBuilder result = new StringBuilder();
                    int len = line.length();
                    for( int i = 0; i < len; i++ ) {
                        char c = line.charAt( i );
                        switch( c ) {
                            case '&':
                                result.append( "&amp;" );
                                break;
                            case '<':
                                result.append( "&lt;" );
                                break;
                            default:
                                result.append( c );
                        }
                    }
                    out.print( result.toString() + LINE_SEPARATOR );
                }

                // Output footer:
                out.print( "</pre></body></html>" );
            }
        }
    }
}


