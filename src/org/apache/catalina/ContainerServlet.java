package org.apache.catalina;

/**
 * 访问Catalina内部功能的servlet, 从Catalina类加载器加载，而不是web应用类加载器.
 * 属性的setter方法必须通过容器调用，每当将这个servlet的新实例放入服务中时.
 */
public interface ContainerServlet {

    /**
     * 获取这个Servlet关联的Wrapper.
     */
    public Wrapper getWrapper();


    /**
     * 设置这个Servlet关联的Wrapper.
     */
    public void setWrapper(Wrapper wrapper);
}
