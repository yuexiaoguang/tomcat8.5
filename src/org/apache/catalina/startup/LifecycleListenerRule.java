package org.apache.catalina.startup;


import org.apache.catalina.Container;
import org.apache.catalina.LifecycleListener;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.digester.Rule;
import org.xml.sax.Attributes;


/**
 * 创建一个{@link LifecycleListener}的Rule, 并将其关联到栈顶对象, 其必须实现{@link Container}.
 * 要使用的实现类由一下决定:
 * <ol>
 * <li>当创建这个规则时，栈顶元素是否使用指定的属性指定实现类?</li>
 * <li>栈顶的{@link Container}的父级{@link Container}使用指定的属性指定实现类, 当创建这个规则时?</li>
 * <li>使用创建此规则时指定的默认实现类.</li>
 * </ol>
 */
public class LifecycleListenerRule extends Rule {


    // ----------------------------------------------------------- Constructors


    /**
     * @param listenerClass 创建的LifecycleListener实现类的默认名称
     * @param attributeName 属性名称，可以覆盖LifecycleListener实现类的名字
     */
    public LifecycleListenerRule(String listenerClass, String attributeName) {
        this.listenerClass = listenerClass;
        this.attributeName = attributeName;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 属性名称，可以覆盖LifecycleListener实现类的名字
     */
    private final String attributeName;


    /**
     * <code>LifecycleListener</code>实现类的名字.
     */
    private final String listenerClass;


    // --------------------------------------------------------- Public Methods


    /**
     * 处理xml元素的开头.
     *
     * @param attributes 元素的属性
     *
     * @exception Exception if a processing error occurs
     */
    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {

        Container c = (Container) digester.peek();
        Container p = null;
        Object obj = digester.peek(1);
        if (obj instanceof Container) {
            p = (Container) obj;
        }

        String className = null;

        // Check the container for the specified attribute
        if (attributeName != null) {
            String value = attributes.getValue(attributeName);
            if (value != null)
                className = value;
        }

        // Check the container's parent for the specified attribute
        if (p != null && className == null) {
            String configClass =
                (String) IntrospectionUtils.getProperty(p, attributeName);
            if (configClass != null && configClass.length() > 0) {
                className = configClass;
            }
        }

        // Use the default
        if (className == null) {
            className = listenerClass;
        }

        // Instantiate a new LifecycleListener implementation object
        Class<?> clazz = Class.forName(className);
        LifecycleListener listener = (LifecycleListener) clazz.getConstructor().newInstance();

        // Add this LifecycleListener to our associated component
        c.addLifecycleListener(listener);
    }
}
