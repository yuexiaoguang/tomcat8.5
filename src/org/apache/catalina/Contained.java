package org.apache.catalina;

/**
 * <p>去耦接口，它指定一个实现类和最多一个<strong>Container</strong>实例关联.</p>
 */
public interface Contained {

    /**
     * 获取这个实例关联的 {@link Container}.
     *
     * @return 关联的Container或<code>null</code>
     */
    Container getContainer();


    /**
     * 设置这个实例关联的 {@link Container}.
     *
     * @param container 关联的Container, 或<code>null</code>来取消关联
     */
    void setContainer(Container container);
}
