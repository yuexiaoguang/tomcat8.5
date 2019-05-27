package javax.servlet.jsp.tagext;


/**
 * 一个taglib指令相关的翻译时信息, 及其底层TLD文件. 大部分信息直接来自TLD, 除了使用taglib指令前缀和URI值
 */
public abstract class TagLibraryInfo {

    /**
     * @param prefix 实际使用的taglib指令前缀
     * @param uri 实际使用的taglib指令的URI
     */
    protected TagLibraryInfo(String prefix, String uri) {
        this.prefix = prefix;
        this.uri = uri;
    }

    /**
     * 这个库的taglib指令的URI属性的值.
     *
     * @return URI属性的值
     */
    public String getURI() {
        return uri;
    }

    /**
     * 从taglib指令分配给这个taglib的前缀
     */
    public String getPrefixString() {
        return prefix;
    }

    /**
     * TLD中所示的首选短名称（前缀）.
     * 当为这个库创建taglib指令的时候，作为首选前缀.
     */
    public String getShortName() {
        return shortname;
    }

    /**
     * TLD中(uri 元素)显示的"reliable" URN.
     * 当为这个库创建taglib指令的时候，作为全局标识符.
     */
    public String getReliableURN() {
        return urn;
    }

    /**
     * 这个TLD的信息.
     */
    public String getInfoString() {
        return info;
    }

    /**
     * 描述JSP容器所需版本的字符串.
     *
     * @return JSP容器的（最小）必需版本.
     */
    public String getRequiredVersion() {
        return jspversion;
    }

    /**
     * 描述标签库中定义的标记的数组.
     */
    public TagInfo[] getTags() {
        return tags;
    }

    /**
     * 描述标签库中定义的标记文件的数组.
     * @since 2.0
     */
    public TagFileInfo[] getTagFiles() {
        return tagFiles;
    }

    /**
     * 获取指定标签名的TagInfo, 查看这个标记库中的所有标记.
     *
     * @param shortname 标记的短名称（没有前缀）
     */
    public TagInfo getTag(String shortname) {
        TagInfo tags[] = getTags();

        if (tags == null || tags.length == 0 || shortname == null) {
            return null;
        }

        for (int i = 0; i < tags.length; i++) {
            if (shortname.equals(tags[i].getTagName())) {
                return tags[i];
            }
        }
        return null;
    }

    /**
     * 获取指定标签名的TagFileInfo, 查看这个标记库中的所有标记.
     *
     * @param shortname 标记的短名称（没有前缀）
     * @since 2.0
     */
    public TagFileInfo getTagFile(String shortname) {
        TagFileInfo tagFiles[] = getTagFiles();

        if (tagFiles == null || tagFiles.length == 0) {
            return null;
        }

        for (int i = 0; i < tagFiles.length; i++) {
            if (tagFiles[i].getName().equals(shortname)) {
                return tagFiles[i];
            }
        }
        return null;
    }

    /**
     * 描述在这个标记库中定义的函数的数组.
     *
     * @return 在这个标记库中定义的函数, 或零长度数组，如果标记库没有定义函数.
     * @since 2.0
     */
    public FunctionInfo[] getFunctions() {
        return functions;
    }

    /**
     * 获取给定函数名的FunctionInfo, 查看这个标记库中的所有函数.
     *
     * @param name 函数的名称（没有前缀）
     * @return 给定名称的函数的FunctionInfo, 或 null 如果没有这样的功能存在
     * @since 2.0
     */
    public FunctionInfo getFunction(String name) {

        if (functions == null || functions.length == 0) {
            return null;
        }

        for (int i = 0; i < functions.length; i++) {
            if (functions[i].getName().equals(name)) {
                return functions[i];
            }
        }
        return null;
    }

    /**
     * 返回TagLibraryInfo对象数组，表示整个标记库(包括这个TagLibraryInfo)， 通过翻译单元中的taglib指令引入.
     * 如果标签库不止一次被导入，并绑定到不同的前缀, 只有绑定到第一个前缀的TagLibraryInfo包含在返回数组中.
     * @since 2.1
     */
    public abstract javax.servlet.jsp.tagext.TagLibraryInfo[] getTagLibraryInfos();

    // Protected fields

    /**
     * 从taglib指令分配给此taglib的前缀.
     */
    protected String prefix;

    /**
     * 这个库的taglib指令的URI属性的值.
     */
    protected String uri;

    /**
     * 描述标签库中定义的标记的数组.
     */
    protected TagInfo[] tags;

    /**
     * 描述标签库中定义的标记文件的数组.
     *
     * @since 2.0
     */
    protected TagFileInfo[] tagFiles;

    /**
     * 描述在这个标记库中定义的函数的数组.
     *
     * @since 2.0
     */
    protected FunctionInfo[] functions;

    // Tag Library Data

    /**
     * 标签库版本.
     */
    protected String tlibversion; // required

    /**
     * JSP规范的版本.
     */
    protected String jspversion; // required

    /**
     * TLD中所示的首选短名称（前缀）.
     */
    protected String shortname; // required

    /**
     * TLD中显示的 "reliable" URN.
     */
    protected String urn; // required

    /**
     * TLD的描述信息.
     */
    protected String info; // optional
}
