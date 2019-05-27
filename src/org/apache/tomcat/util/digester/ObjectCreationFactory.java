package org.apache.tomcat.util.digester;


import org.xml.sax.Attributes;

/**
 * <p>用于{@link FactoryCreateRule}的接口.
 * 该规则调用{@link #createObject}创建一个对象, 只要匹配就可以将其推送到<code>Digester</code>堆栈.</p>
 *
 * <p> {@link AbstractObjectCreationFactory} 是一个适用于创建匿名<code>ObjectCreationFactory</code>实现的抽象实现.
 */
public interface ObjectCreationFactory {

    /**
     * 由{@link FactoryCreateRule}调用的工厂方法，用于根据元素的属性提供对象.
     *
     * @param attributes 元素的属性
     * 
     * @return 创建的对象
     * @throws Exception 抛出的任何异常都将向上传播
     */
    public Object createObject(Attributes attributes) throws Exception;

    /**
     * @return 初始化时由{@link FactoryCreateRule}设置的{@link Digester}.
     */
    public Digester getDigester();

    /**
     * 设置{@link Digester}以允许实现基于digester的类加载器进行日志记录，类加载等.
     *
     * @param digester 父Digester对象
     */
    public void setDigester(Digester digester);

}
