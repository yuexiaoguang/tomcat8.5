package org.apache.tomcat.util.digester;

import org.xml.sax.Attributes;


/**
 * <p>使用{@link ObjectCreationFactory}创建一个新对象，并将其推送到对象堆栈.  元素完成后，将弹出对象.</p>
 *
 * <p>此规则适用于在创建对象之前需要元素属性的情况.  一个常见的场景是ObjectCreationFactory实现在调用工厂方法或非空构造函数时将属性用作参数.
 */
public class FactoryCreateRule extends Rule {

    // ----------------------------------------------------------- Fields

    /** 是否应忽略工厂抛出的异常? */
    private boolean ignoreCreateExceptions;
    /** Stock to manage */
    private ArrayStack<Boolean> exceptionIgnoredStack;


    // ----------------------------------------------------------- Constructors

    /**
     * @param creationFactory 调用来创建对象.
     * @param ignoreCreateExceptions 如果是 true, 对象创建工厂抛出的异常将被忽略.
     */
    public FactoryCreateRule(
                            ObjectCreationFactory creationFactory,
                            boolean ignoreCreateExceptions) {

        this.creationFactory = creationFactory;
        this.ignoreCreateExceptions = ignoreCreateExceptions;
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * 将使用对象创建工厂, 基于匹配的XML元素中指定的属性, 根据需要实例化对象.
     */
    protected ObjectCreationFactory creationFactory = null;


    // --------------------------------------------------------- Public Methods


    /**
     * 处理此元素的开头.
     *
     * @param attributes 此元素的属性列表
     */
    @Override
    public void begin(String namespace, String name, Attributes attributes) throws Exception {

        if (ignoreCreateExceptions) {

            if (exceptionIgnoredStack == null) {
                exceptionIgnoredStack = new ArrayStack<>();
            }

            try {
                Object instance = creationFactory.createObject(attributes);

                if (digester.log.isDebugEnabled()) {
                    digester.log.debug("[FactoryCreateRule]{" + digester.match +
                            "} New " + instance.getClass().getName());
                }
                digester.push(instance);
                exceptionIgnoredStack.push(Boolean.FALSE);

            } catch (Exception e) {
                // 记录消息和错误
                if (digester.log.isInfoEnabled()) {
                    digester.log.info("[FactoryCreateRule] Create exception ignored: " +
                        ((e.getMessage() == null) ? e.getClass().getName() : e.getMessage()));
                    if (digester.log.isDebugEnabled()) {
                        digester.log.debug("[FactoryCreateRule] Ignored exception:", e);
                    }
                }
                exceptionIgnoredStack.push(Boolean.TRUE);
            }

        } else {
            Object instance = creationFactory.createObject(attributes);

            if (digester.log.isDebugEnabled()) {
                digester.log.debug("[FactoryCreateRule]{" + digester.match +
                        "} New " + instance.getClass().getName());
            }
            digester.push(instance);
        }
    }


    /**
     * 处理此元素的结尾.
     */
    @Override
    public void end(String namespace, String name) throws Exception {

        // 检查对象是否已创建
        // 只有在抛出异常且忽略它们时才会发生这种情况
        if (
                ignoreCreateExceptions &&
                exceptionIgnoredStack != null &&
                !(exceptionIgnoredStack.empty())) {

            if ((exceptionIgnoredStack.pop()).booleanValue()) {
                // 创建异常被忽略了
                // 没有任何东西放在堆栈上
                if (digester.log.isTraceEnabled()) {
                    digester.log.trace("[FactoryCreateRule] No creation so no push so no pop");
                }
                return;
            }
        }

        Object top = digester.pop();
        if (digester.log.isDebugEnabled()) {
            digester.log.debug("[FactoryCreateRule]{" + digester.match +
                    "} Pop " + top.getClass().getName());
        }

    }


    /**
     * 解析完成后清理.
     */
    @Override
    public void finish() throws Exception {
        // NO-OP
    }


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("FactoryCreateRule[");
        if (creationFactory != null) {
            sb.append("creationFactory=");
            sb.append(creationFactory);
        }
        sb.append("]");
        return (sb.toString());

    }
}
