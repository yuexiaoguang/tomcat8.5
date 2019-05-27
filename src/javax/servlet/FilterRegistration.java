package javax.servlet;

import java.util.Collection;
import java.util.EnumSet;

public interface FilterRegistration extends Registration {

    /**
     * 添加此过滤器的一个或多个命名servlet映射.
     *
     * @param dispatcherTypes 此过滤器的分派类型
     * @param isMatchAfter    在部署描述符中定义的任何映射之后或(<code>true</code>)之前应用这个过滤器吗?
     * @param servletNames    映射到这些servlet的请求将被这个过滤器处理
     * @throws IllegalArgumentException 如果servlet名称列表为空或NULL
     * @throws IllegalStateException 如果关联的ServletContext已经被初始化
     */
    public void addMappingForServletNames(EnumSet<DispatcherType> dispatcherTypes,
            boolean isMatchAfter, String... servletNames);
    
    public Collection<String> getServletNameMappings();

    /**
     * 将此过滤器的映射添加到一个或多个URL模式.
     *
     * @param dispatcherTypes 此过滤器的分派类型
     * @param isMatchAfter    在部署描述符中定义的任何映射之后或(<code>true</code>)之前应用这个过滤器吗?
     * @param urlPatterns     应用此过滤器的URL模式
     * @throws IllegalArgumentException 如果URL模式列表为空或NULL
     * @throws IllegalStateException 如果关联的ServletContext已经被初始化
     */
    public void addMappingForUrlPatterns(
            EnumSet<DispatcherType> dispatcherTypes,
            boolean isMatchAfter, String... urlPatterns);

    public Collection<String> getUrlPatternMappings();

    public static interface Dynamic extends FilterRegistration, Registration.Dynamic {
        // 没有额外的方法
    }
}
