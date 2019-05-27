package javax.servlet;

import java.util.Set;

/**
 * ServletContainerInitializer (SCIs)通过一个条目注册到文件 META-INF/services/javax.servlet.ServletContainerInitializer
 * ，必须包含在包含SCI实现的JAR文件中.
 * <p>
 * 无论元数据完成的设置如何，都进行SCI处理.
 * SCI处理可以通过片段排序控制每个JAR文件. 如果一个绝对排序被定义, 只有排序中的这些JAR将被处理为SCI. 完全禁用SCI处理, 可以定义空的绝对排序.
 * <p>
 */
public interface ServletContainerInitializer {

    /**
     * 在符合{@link javax.servlet.annotation.HandlesTypes}注解定义的条件的web应用启动时，接受通知.
     *
     * @param c     符合指定条件的一组类 (可能是 null)
     * @param ctx   发现类的web应用的ServletContext
     *
     * @throws ServletException 如果发生一个错误
     */
    void onStartup(Set<Class<?>> c, ServletContext ctx) throws ServletException;
}
