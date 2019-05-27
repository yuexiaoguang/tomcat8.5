package org.apache.jasper.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * 表示一个资源映射(SMAP),它用于将输入JSP文件的行与final .class文件中生成的servlet中的行关联起来, 根据JSR-045 规范.
 */
public class SmapGenerator {

    //*********************************************************************
    // Overview

    /*
     * SMAP 语法是合理的straightforward. 这个类目前的目的是双重的:
     *  - 提供一个简单而低级的java接口建立一个逻辑SMAP
     *  - 序列化该逻辑SMAP最终直接纳入到一个.class 文件.
     */


    //*********************************************************************
    // Private state

    private String outputFileName;
    private String defaultStratum = "Java";
    private final List<SmapStratum> strata = new ArrayList<>();
    private final List<String> embedded = new ArrayList<>();
    private boolean doEmbedded = true;

    //*********************************************************************
    // Methods for adding mapping data

    /**
     * 设置生成的资源文件的文件名(不包括路径信息).  E.g., "foo$jsp.java".
     * 
     * @param x The file name
     */
    public synchronized void setOutputFileName(String x) {
        outputFileName = x;
    }


    /**
     * 设置SMAP的默认和唯一层级.
     *
     * @param stratum the SmapStratum object to add
     */
    public synchronized void setStratum(SmapStratum stratum) {
        addStratum(stratum, true);
    }


    /**
     * 添加给定的SmapStratum 对象, 表示一个Stratum与逻辑关联的FileSection 和 LineSection 块, 到当前SmapGenerator. 
     * 如果<tt>default</tt>是 true, 这个stratum 作为默认 stratum, 覆盖之前设置的默认值.
     *
     * @param stratum 要添加的SmapStratum 对象
     * @param defaultStratum 如果<tt>true</tt>, 这个SmapStratum 是默认的SMAP stratum, 除非重写
     *
     * @deprecated Use {@link #setStratum(SmapStratum)}
     */
    @Deprecated
    public synchronized void addStratum(SmapStratum stratum,
                                        boolean defaultStratum) {
        strata.add(stratum);
        if (defaultStratum)
            this.defaultStratum = stratum.getStratumName();
    }

    /**
     * 添加给定字符串作为一个给定层级名称嵌入的SMAP.
     *
     * @param smap 要嵌入的SMAP
     * @param stratumName 编译输出的层的名称, 生成要嵌入的<tt>smap</tt>
     *
     * @deprecated Unused. This will be removed in Tomcat 9.0.x
     */
    @Deprecated
    public synchronized void addSmap(String smap, String stratumName) {
        embedded.add("*O " + stratumName + "\n"
                   + smap
                   + "*C " + stratumName + "\n");
    }

    /**
     * 指示 SmapGenerator 是否真的打印任何嵌入的SMAP. 作为没有SMAP 解析器的解决方案.
     *
     * @param status 如果是<tt>false</tt>, 忽略任何嵌入的SMAP.
     *
     * @deprecated Unused. Will be removed in Tomcat 9.0.x
     */
    @Deprecated
    public void setDoEmbedded(boolean status) {
        doEmbedded = status;
    }

    //*********************************************************************
    // Methods for serializing the logical SMAP

    public synchronized String getString() {
        // 检查状态和初始化缓冲区
        if (outputFileName == null)
            throw new IllegalStateException();
        StringBuilder out = new StringBuilder();

        // start the SMAP
        out.append("SMAP\n");
        out.append(outputFileName + '\n');
        out.append(defaultStratum + '\n');

        // 包括嵌入的SMAP
        if (doEmbedded) {
            int nEmbedded = embedded.size();
            for (int i = 0; i < nEmbedded; i++) {
                out.append(embedded.get(i));
            }
        }

        // print our StratumSections, FileSections, and LineSections
        int nStrata = strata.size();
        for (int i = 0; i < nStrata; i++) {
            SmapStratum s = strata.get(i);
            out.append(s.getString());
        }

        // end the SMAP
        out.append("*E\n");

        return out.toString();
    }

    @Override
    public String toString() { return getString(); }

    //*********************************************************************
    // For testing (and as an example of use)...

    @SuppressWarnings("deprecation")
    public static void main(String args[]) {
        SmapGenerator g = new SmapGenerator();
        g.setOutputFileName("foo.java");
        SmapStratum s = new SmapStratum();
        s.addFile("foo.jsp");
        s.addFile("bar.jsp", "/foo/foo/bar.jsp");
        s.addLineData(1, "foo.jsp", 1, 1, 1);
        s.addLineData(2, "foo.jsp", 1, 6, 1);
        s.addLineData(3, "foo.jsp", 2, 10, 5);
        s.addLineData(20, "bar.jsp", 1, 30, 1);
        g.addStratum(s, true);
        System.out.print(g);

        System.out.println("---");

        SmapGenerator embedded = new SmapGenerator();
        embedded.setOutputFileName("blargh.tier2");
        s = new SmapStratum("Tier2");
        s.addFile("1.tier2");
        s.addLineData(1, "1.tier2", 1, 1, 1);
        embedded.addStratum(s, true);
        g.addSmap(embedded.toString(), "JSP");
        System.out.println(g);
    }
}
