package org.apache.tomcat.util.digester;

import org.xml.sax.Attributes;

/**
 * <p><code>ObjectCreationFactory</code>实现的抽象基类.</p>
 */
public abstract class AbstractObjectCreationFactory
        implements ObjectCreationFactory {


    // ----------------------------------------------------- Instance Variables


    /**
     * 初始化时由{@link FactoryCreateRule}设置的关联的<code>Digester</code>实例.
     */
    private Digester digester = null;


    // --------------------------------------------------------- Public Methods


    /**
     * <p>由{@link FactoryCreateRule}调用的工厂方法, 用于根据元素的属性提供对象.
     *
     * @param attributes 元素的属性
     *
     * @throws Exception 抛出的任何异常都将向上传播
     */
    @Override
    public abstract Object createObject(Attributes attributes) throws Exception;


    /**
     * <p>返回初始化时{@link FactoryCreateRule}设置的{@link Digester}.
     */
    @Override
    public Digester getDigester() {
        return (this.digester);
    }


    /**
     * <p>设置{@link Digester}以允许实现进行日志记录, 基于digester的类加载器的类加载, 等.
     *
     * @param digester 父级Digester对象
     */
    @Override
    public void setDigester(Digester digester) {
        this.digester = digester;
    }
}
