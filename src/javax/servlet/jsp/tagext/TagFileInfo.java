package javax.servlet.jsp.tagext;

/**
 * 标记库中标记文件的标记信息;
 * 这个类从Tag Library Descriptor文件(TLD)实例化，只在翻译时可用.
 */
public class TagFileInfo {

    /**
     * 需要注意的是，由于TagLibraryInfo反映TLD信息和taglib伪指令信息，TagFileInfo实例依赖taglib伪指令.
     * 这可能是一个设计错误，可能会在将来修复.
     *
     * @param name 此标记的唯一动作名
     * @param path 实现这个动作的.tag文件的路径, 相对于TLD 文件的位置.
     * @param tagInfo 关于这个标签的详细信息, 从标记文件中的指令解析.
     */
    public TagFileInfo( String name, String path, TagInfo tagInfo ) {
        this.name = name;
        this.path = path;
        this.tagInfo = tagInfo;
    }

    /**
     * 此标记的唯一动作名.
     */
    public String getName() {
        return name;
    }

    /**
     * @return 实现这个动作的.tag文件的路径, 相对于TLD 文件的位置, 或"." 如果标签文件是在一个隐式标记文件中定义的.
     */
    public String getPath() {
        return path;
    }

    /**
     * 返回此标记的信息，从标记文件中的指令解析.
     */
    public TagInfo getTagInfo() {
        return tagInfo;
    }

    private final String name;
    private final String path;
    private final TagInfo tagInfo;
}
