package javax.servlet.jsp.tagext;

import java.util.Map;

/**
 * 一个JSP页面翻译时验证类.
 * 验证器在JSP页面相关的XML视图.
 *
 * <p>
 * TagLibraryValidator类关联的 TLD 文件，和一些标签库中的初始化参数.
 *
 * <p>
 * JSP容器负责查找适当子类的适当实例
 * <ul>
 * <li> 新的实例, 或重用一个实例
 * <li> 调用实例的setInitParams(Map)方法
 * </ul>
 *
 * 一旦初始化, validate(String, String, PageData)方法将被调用, 其中前两个参数是XML视图中此标记库的前缀和URI.
 * 前缀旨在使生成错误消息更容易. 然而，它并不总是准确的. 在一个单一URI被映射到XML视图中的多个前缀的情况下, 提供第一个URI的前缀.
 * 因此, 在检查标记元素本身的情况下提供高质量的错误消息, 应忽略前缀参数，而应使用元素的实际前缀.
 * TagLibraryValidator 应该始终使用URI来标识属于标记库的元素，而不是前缀.
 *
 * <p>
 * TagLibraryValidator实例可以在内部创建辅助对象来执行验证(即 XSchema 验证器) 可以重用它在给定的翻译运行中的所有页面.
 *
 * <p>
 * JSP容器不能保证序列化调用validate() 方法, 而且TagLibraryValidator 应该执行它们可能需要的任何同步.
 *
 * <p>
 * 在JSP 2.0版本中, JSP容器必须提供 jsp:id 属性提供更高质量的验证错误.
 * 容器将跟踪传递给容器的JSP页面, 并标识每个元素一个唯一的 "id", 其将作为 jsp:id属性的值. XML视图中的每个XML元素都将使用此属性进行扩展.
 * TagLibraryValidator可以在一个或多个ValidationMessage对象中使用属性. 然后，容器可以使用这些值来提供关于错误位置的更精确的信息.
 *
 * <p>
 * <code>id</code>属性真实的前缀不一定是<code>jsp</code>, 但它总是映射到名称空间<code>http://java.sun.com/JSP/Page</code>.
 * TagLibraryValidator实现必须依赖于URI，而不是<code>id</code>属性的前缀.
 */
public abstract class TagLibraryValidator {

    public TagLibraryValidator() {
        // NOOP by default
    }

    /**
     * 设置TLD中的初始化数据.
     * 参数名作为key, 参数值作为值.
     *
     * @param map 描述初始化参数的Map
     */
    public void setInitParameters(Map<String, Object> map) {
        initParameters = map;
    }


    /**
     * 将init参数数据作为不可变Map获取.
     * 参数名作为key, 参数值作为值.
     *
     * @return init参数作为不可变映射.
     */
    public Map<String, Object> getInitParameters() {
        return initParameters;
    }

    /**
     * 验证JSP 页面.
     * 这将在XML视图中的每个唯一标记库URI中调用一次. 如果页面有效，此方法将返回null; 否则返回ValidationMessage数组对象.
     * 长度为零的数组也被解释为没有错误.
     *
     * @param prefix 在XML视图中标记库关联的第一个前缀. 注意，如果命名空间被重新定义，一些标记可能使用不同的前缀.
     * @param uri 标记库的唯一标识符
     * @param page JspData页面对象
     * @return null 对象,或零长度数组, 或ValidationMessage数组.
     */
    public ValidationMessage[] validate(String prefix, String uri,
        PageData page) {
        return null;
    }

    /**
     * 释放此实例保存的任何数据以供验证之用.
     */
    public void release() {
        initParameters = null;
    }

    // Private data
    private Map<String, Object> initParameters;

}
