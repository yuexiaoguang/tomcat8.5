package javax.servlet.jsp.tagext;

/**
 * 由标记库作者提供的可选类，用于描述在TLD中未描述的额外翻译时间信息.
 * TagExtraInfo类在Tag Library Descriptor文件(TLD)中定义.
 *
 * <p>
 * 可以使用这个类:
 * <ul>
 * <li> 指示标记定义脚本变量
 * <li> 执行标记属性的翻译时间验证.
 * </ul>
 *
 * <p>
 * 这是JSP翻译器的职责，通过调用getTagInfo()返回的初始值对应于一个 TagInfo对象，为了翻译的标签.
 * 如果一个显式的setTagInfo()调用已经完成, 那么传递的对象在随后的getTagInfo()调用中将被返回.
 *
 * <p>
 * 影响getTagInfo()返回值的唯一途径是调用一个setTagInfo(), 因此, TagExtraInfo.setTagInfo()被JSP翻译器调用,
 * 和对应于翻译的标签的 TagInfo对象. 调用应该在validate()和getVariableInfo()执行之前发生.
 *
 * <p>
 * <tt>NOTE:</tt> It is a (translation time) error for a tag definition
 * in a TLD with one or more variable subelements to have an associated
 * TagExtraInfo implementation that returns a VariableInfo array with
 * one or more elements from a call to getVariableInfo().
 */
public abstract class TagExtraInfo {

    public TagExtraInfo() {
        // NOOP by default
    }

    /**
     * 和TagExtraInfo实例关联的标签定义的脚本变量的信息.
     * Request-time属性显示在TagData 参数中.
     *
     * @param data TagData 实例.
     * @return 如果没有定义脚本变量返回 null 或零长度数组.
     */
    public VariableInfo[] getVariableInfo(TagData data) {
        return ZERO_VARIABLE_INFO;
    }

    /**
     * 属性验证.
     * Request-time属性显示在TagData 参数中.
     * 注意，进行验证的首选方法是和validate()方法一起, 因为它可以返回更详细的信息.
     *
     * @param data TagData 实例.
     * @return 这个标记实例是否有效.
     */
    public boolean isValid(TagData data) {
        return true;
    }

    /**
     * 属性验证.
     * Request-time属性显示在TagData 参数中.
     *
     * <p>JSP 2.0 和更高版本的容器调用validate(), 而不是isValid().
     * 这个方法的默认实现调用 isValid(). 如果isValid()返回false, 返回一个通用的 ValidationMessage[] 表示 isValid() 返回 false.</p>
     *
     * @param data TagData 实例.
     * @return null 对象, 或零长度数组, 或者 ValidationMessage数组.
     * @since 2.0
     */
    public ValidationMessage[] validate( TagData data ) {
        ValidationMessage[] result = null;

        if( !isValid( data ) ) {
            result = new ValidationMessage[] {
                new ValidationMessage( data.getId(), "isValid() == false" ) };
        }

        return result;
    }

    public final void setTagInfo(TagInfo tagInfo) {
        this.tagInfo = tagInfo;
    }

    public final TagInfo getTagInfo() {
        return tagInfo;
    }

    // private data
    private  TagInfo tagInfo;

    // zero length VariableInfo array
    private static final VariableInfo[] ZERO_VARIABLE_INFO = { };
}

