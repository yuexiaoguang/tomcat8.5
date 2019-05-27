package javax.servlet.jsp;

/**
 * 提供有关当前JSP引擎的信息.
 */
public abstract class JspEngineInfo {

    public JspEngineInfo() {
        // NOOP by default
    }

    /**
     * 返回此JSP引擎支持的JSP规范的版本号.
     * <p>
     * 由正整数组成的规范版本号, 使用 "."分隔, 例如, "2.0" 或 "1.2.3.4.5.6.7".
     * 这允许使用可扩展的数字来表示主要的、次要的、微的等版本.
     * 版本号必须以数字开头.
     * </p>
     *
     * @return 规范版本号, 或者null
     */

    public abstract String getSpecificationVersion();
}
