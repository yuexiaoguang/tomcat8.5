package javax.servlet.jsp.tagext;

/**
 * 标记库中标记的标记信息;
 * 这个类是从标记库描述符文件 (TLD)实例化的，只在翻译时可用.
*/
public class TagInfo {

    public static final String BODY_CONTENT_JSP = "JSP";

    public static final String BODY_CONTENT_TAG_DEPENDENT = "tagdependent";


    public static final String BODY_CONTENT_EMPTY = "empty";

    public static final String BODY_CONTENT_SCRIPTLESS = "scriptless";

    /**
     * 注意, 因为TagLibraryInfo反映了TLD信息和taglib指令信息, TagInfo 实例依赖taglib指令.
     * 这可能是一个设计错误, 可能在将来固定下来.
     *
     * @param tagName 这个标签的名称
     * @param tagClassName 标记处理程序类的名称
     * @param bodycontent 关于这些标签的正文内容的信息
     * @param infoString 此标记的字符串信息(可选)
     * @param taglib 包含标记库的实例.
     * @param tagExtraInfo 提供附加标记信息的实例. 可能是null
     * @param attributeInfo AttributeInfo数组. 可能是null;
     */
    public TagInfo(String tagName,
            String tagClassName,
            String bodycontent,
            String infoString,
            TagLibraryInfo taglib,
            TagExtraInfo tagExtraInfo,
            TagAttributeInfo[] attributeInfo) {
        this.tagName       = tagName;
        this.tagClassName  = tagClassName;
        this.bodyContent   = bodycontent;
        this.infoString    = infoString;
        this.tagLibrary    = taglib;
        this.tagExtraInfo  = tagExtraInfo;
        this.attributeInfo = attributeInfo;

        // 对未指定值使用默认值
        this.displayName = null;
        this.largeIcon = null;
        this.smallIcon = null;
        this.tagVariableInfo = null;
        this.dynamicAttributes = false;

        if (tagExtraInfo != null)
            tagExtraInfo.setTagInfo(this);
    }

    /**
     * 注意, 因为TagLibraryInfo反映了TLD信息和taglib指令信息, TagInfo 实例依赖taglib指令.
     * 这可能是一个设计错误, 可能在将来固定下来.
     *
     * @param tagName 这个标签的名称
     * @param tagClassName 标记处理程序类的名称
     * @param bodycontent 关于这些标签的正文内容的信息
     * @param infoString 此标记的字符串信息(可选)
     * @param taglib 包含标记库的实例.
     * @param tagExtraInfo 提供附加标记信息的实例. 可能是null
     * @param attributeInfo AttributeInfo数组. 可能是null;
     * @param displayName 由工具显示的短名称
     * @param smallIcon 通过工具显示的小图标的路径
     * @param largeIcon 通过工具显示的大型图标的路径
     * @param tvi TagVariableInfo数组 (或 null)
     */
    public TagInfo(String tagName,
            String tagClassName,
            String bodycontent,
            String infoString,
            TagLibraryInfo taglib,
            TagExtraInfo tagExtraInfo,
            TagAttributeInfo[] attributeInfo,
            String displayName,
            String smallIcon,
            String largeIcon,
            TagVariableInfo[] tvi) {
        this.tagName       = tagName;
        this.tagClassName  = tagClassName;
        this.bodyContent   = bodycontent;
        this.infoString    = infoString;
        this.tagLibrary    = taglib;
        this.tagExtraInfo  = tagExtraInfo;
        this.attributeInfo = attributeInfo;
        this.displayName = displayName;
        this.smallIcon = smallIcon;
        this.largeIcon = largeIcon;
        this.tagVariableInfo = tvi;

        // 对未指定值使用默认值
        this.dynamicAttributes = false;

        if (tagExtraInfo != null)
            tagExtraInfo.setTagInfo(this);
    }

    /**
     * 注意, 因为TagLibraryInfo反映了TLD信息和taglib指令信息, TagInfo 实例依赖taglib指令.
     * 这可能是一个设计错误, 可能在将来固定下来.
     *
     * @param tagName 这个标签的名称
     * @param tagClassName 标记处理程序类的名称
     * @param bodycontent 关于这些标签的正文内容的信息
     * @param infoString 此标记的字符串信息(可选)
     * @param taglib 包含标记库的实例.
     * @param tagExtraInfo 提供附加标记信息的实例. 可能是null
     * @param attributeInfo AttributeInfo数组. 可能是null;
     * @param displayName 由工具显示的短名称
     * @param smallIcon 通过工具显示的小图标的路径
     * @param largeIcon 通过工具显示的大型图标的路径
     * @param tvi TagVariableInfo数组 (或 null)
     * @param dynamicAttributes True 如果支持动态属性
     *
     * @since 2.0
     */
    public TagInfo(String tagName,
            String tagClassName,
            String bodycontent,
            String infoString,
            TagLibraryInfo taglib,
            TagExtraInfo tagExtraInfo,
            TagAttributeInfo[] attributeInfo,
            String displayName,
            String smallIcon,
            String largeIcon,
            TagVariableInfo[] tvi,
            boolean dynamicAttributes) {
        this.tagName       = tagName;
        this.tagClassName  = tagClassName;
        this.bodyContent   = bodycontent;
        this.infoString    = infoString;
        this.tagLibrary    = taglib;
        this.tagExtraInfo  = tagExtraInfo;
        this.attributeInfo = attributeInfo;
        this.displayName = displayName;
        this.smallIcon = smallIcon;
        this.largeIcon = largeIcon;
        this.tagVariableInfo = tvi;
        this.dynamicAttributes = dynamicAttributes;

        if (tagExtraInfo != null)
            tagExtraInfo.setTagInfo(this);
    }

    /**
     * 标签名称.
     */
    public String getTagName() {
        return tagName;
    }

    /**
     * 标签上的属性信息（在TLD中）.
     * 返回是描述此标记属性的数组, 如TLD所示.
     */
   public TagAttributeInfo[] getAttributes() {
       return attributeInfo;
   }

    /**
     * 关于此标记在运行时创建的脚本对象的信息.
     * 在关联的TagExtraInfo 类上的方便的方法.
     *
     * @param data 描述这个动作的TagData.
     */
   public VariableInfo[] getVariableInfo(TagData data) {
       VariableInfo[] result = null;
       TagExtraInfo tei = getTagExtraInfo();
       if (tei != null) {
           result = tei.getVariableInfo( data );
       }
       return result;
   }

    /**
     * 属性的翻译时验证.
     *
     * @param data 翻译时的TagData 实例.
     * @return 数据是否有效.
     */
    public boolean isValid(TagData data) {
        TagExtraInfo tei = getTagExtraInfo();
        if (tei == null) {
            return true;
        }
        return tei.isValid(data);
    }

    /**
     * 属性的翻译时验证.
     *
     * @param data 翻译时的TagData 实例.
     * @return null, 零长度数组, 或ValidationMessage数组.
     * @since 2.0
     */
    public ValidationMessage[] validate( TagData data ) {
        TagExtraInfo tei = getTagExtraInfo();
        if( tei == null ) {
            return null;
        }
        return tei.validate( data );
    }

    /**
     * 设置额外标记信息的实例.
     *
     * @param tei TagExtraInfo实例
     */
    public void setTagExtraInfo(TagExtraInfo tei) {
        tagExtraInfo = tei;
    }


    /**
     * 额外标签信息的实例.
     *
     * @return TagExtraInfo实例.
     */
    public TagExtraInfo getTagExtraInfo() {
        return tagExtraInfo;
    }


    /**
     * 为该标记提供处理程序的类的名称.
     *
     * @return 标记处理程序类的名称.
     */
    public String getTagClassName() {
        return tagClassName;
    }


    /**
     * 这个标签的主体内容信息.
     * 如果没有为此标记定义主体内容, 将返回默认的JSP.
     *
     * @return 主体内容字符串.
     */
    public String getBodyContent() {
        return bodyContent;
    }


    /**
     * 标签字符信息.
     *
     * @return 信息字符串, 或null
     */
    public String getInfoString() {
        return infoString;
    }


    /**
     * 设置TagLibraryInfo 属性.
     *
     * 注意：TagLibraryInfo 元素不仅取决于TLD信息，也包括指定使用的taglib 实例. 这意味着大量的工作需要做，构造和初始化TagLib对象.
     *
     * 如果小心使用, 这个setter 可以避免为每个taglib指令创建新的TagInfo元素.
     *
     * @param tl 分配的TagLibraryInfo
     */
    public void setTagLibrary(TagLibraryInfo tl) {
        tagLibrary = tl;
    }

    /**
     * 所属的TabLibraryInfo实例.
     *
     * @return 所属的标记库实例
     */
    public TagLibraryInfo getTagLibrary() {
        return tagLibrary;
    }


    // ============== JSP 2.0 TLD Information ========


    /**
     * 由工具显示的短名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取小图标的路径.
     */
    public String getSmallIcon() {
        return smallIcon;
    }

    /**
     * 获取大图标的路径.
     */
    public String getLargeIcon() {
        return largeIcon;
    }

    /**
     * 获取这个TagInfo关联的TagVariableInfo对象.
     *
     * @return 对应于此标记声明的变量的TagVariableInfo 对象数组, 或零长度数组
     */
    public TagVariableInfo[] getTagVariableInfos() {
        return tagVariableInfo;
    }


    // ============== JSP 2.0 TLD Information ========

    /**
     * 获取dynamicAttributes.
     *
     * @return True 如果标记处理程序支持动态属性
     * @since 2.0
     */
    public boolean hasDynamicAttributes() {
        return dynamicAttributes;
    }

    /*
     * private fields for 1.1 info
     */
    private final String             tagName; // the name of the tag
    private final String             tagClassName;
    private final String             bodyContent;
    private final String             infoString;
    private TagLibraryInfo           tagLibrary;
    private TagExtraInfo             tagExtraInfo; // instance of TagExtraInfo
    private final TagAttributeInfo[] attributeInfo;

    /*
     * private fields for 1.2 info
     */
    private final String             displayName;
    private final String             smallIcon;
    private final String             largeIcon;
    private final TagVariableInfo[]  tagVariableInfo;

    /*
     * Additional private fields for 2.0 info
     */
    private final boolean dynamicAttributes;
}
