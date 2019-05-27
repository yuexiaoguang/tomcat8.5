package javax.servlet.jsp.tagext;

import java.io.InputStream;

/**
 * JSP页面上的翻译时间信息. 该信息对应于JSP页面的XML视图.
 *
 * <p>
 * 这种类型的对象由JSP翻译器生成, e.g. 当被传递给TagLibraryValidator实例的时候.
 */
public abstract class PageData {

    public PageData() {
        // NOOP by default
    }

    /**
     * 返回JSP页面的XML视图上的输入流.
     * 流使用 UTF-8编码. JSP页面的XML视图包含了扩展的指令.
     *
     * @return 文档上的输入流.
     */
   public abstract InputStream getInputStream();
}
