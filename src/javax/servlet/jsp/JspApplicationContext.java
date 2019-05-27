package javax.servlet.jsp;

import javax.el.ELContextListener;
import javax.el.ELResolver;
import javax.el.ExpressionFactory;

/**
 * <p>
 * 保存<i>application</i>- JSP容器的范围信息.
 * </p>
 */
public interface JspApplicationContext {

    /**
     * 注册一个被通知的<code>ELContextListener</code>， 当创建<code>ELContext</code>的时候.
     * <p>
     * 至少, 所有实例化的<code>ELContext</code>将参考<code>JspContext.class</code>下的<code>JspContext</code>.
     *
     * @param listener 要添加的监听器
     */
    public void addELContextListener(ELContextListener listener);

    /**
     * <p>
     * 添加一个<code>ELResolver</code>到EL变量和JSP页面和标签文件中的属性管理的链中.
     * </p>
     * <p>
     * JSP在链中具有一个默认的ELResolver，给所有EL赋值:
     * </p>
     * <ul>
     * <li><code>ImplicitObjectELResolver</code></li>
     * <li>这个方法注册的<code>ELResolver</code>实例</li>
     * <li><code>MapELResolver</code></li>
     * <li><code>ListELResolver</code></li>
     * <li><code>ArrayELResolver</code></li>
     * <li><code>BeanELResolver</code></li>
     * <li><code>ScopedAttributeELResolver</code></li>
     * </ul>
     *
     * @param resolver 额外的解析器
     * @throws IllegalStateException 如果在应用的<code>ServletContextListeners</code>初始化之后调用.
     */
    public void addELResolver(ELResolver resolver) throws IllegalStateException;

    /**
     * <p>
     * 返回JSP容器的<code>ExpressionFactory</code>实现类，用于EL.
     * </p>
     *
     * @return 一个<code>ExpressionFactory</code>实现
     */
    public ExpressionFactory getExpressionFactory();

}
