package javax.servlet;

import java.util.Collection;
import java.util.Set;

public interface ServletRegistration extends Registration {

    /**
     * TODO
     * @param urlPatterns 这个servlet应该映射到的URL模式
     * @return TODO
     * @throws IllegalArgumentException 如果urlPattern是 null 或 empty
     * @throws IllegalStateException 如果相关的ServletContext已经初始化
     */
    public Set<String> addMapping(String... urlPatterns);

    public Collection<String> getMappings();

    public String getRunAsRole();

    public static interface Dynamic
    extends ServletRegistration, Registration.Dynamic {
        public void setLoadOnStartup(int loadOnStartup);
        public void setMultipartConfig(MultipartConfigElement multipartConfig);
        public void setRunAsRole(String roleName);
        public Set<String> setServletSecurity(ServletSecurityElement constraint);
    }
}
