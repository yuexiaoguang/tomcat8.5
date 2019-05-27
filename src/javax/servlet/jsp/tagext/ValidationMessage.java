package javax.servlet.jsp.tagext;

/**
 * TagLibraryValidator 或 TagExtraInfo的验证消息.
 * <p>
 * 在JSP 2.0中, JSP容器必须支持jsp:id 属性来提供更高质量的验证错误.
 * 容器将跟踪传递给容器的JSP页面, 并将赋予每个元素一个唯一的 "id", 并作为 jsp:id 属性的值. XML视图中的每个XML元素都将使用此属性进行扩展.
 * TagLibraryValidator可以使用一个或多个ValidationMessage对象中的属性. 然后，容器可以使用这些值来提供关于错误位置的更精确的信息.
 * <p>
 * <code>id</code>属性真实的前缀不一定是<code>jsp</code>，但它总是映射到命名空间<code>http://java.sun.com/JSP/Page</code>.
 * TagLibraryValidator实现必须依赖于URI, 而不是<code>id</code>属性的前缀.
 */
public class ValidationMessage {

    /**
     * @param id null, 或jsp:id 属性的值.
     * @param message 本地化验证消息.
     */
    public ValidationMessage(String id, String message) {
        this.id = id;
        this.message = message;
    }

    /**
     * 获取jsp:id. Null 意味着没有可用的信息.
     */
    public String getId() {
        return id;
    }

    /**
     * 本地化验证消息.
     */
    public String getMessage() {
        return message;
    }

    // Private data
    private final String id;
    private final String message;
}
