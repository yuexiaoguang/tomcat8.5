package org.apache.catalina.core;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletSecurityElement;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Wrapper;
import org.apache.catalina.util.ParameterMap;
import org.apache.tomcat.util.buf.UDecoder;
import org.apache.tomcat.util.res.StringManager;

public class ApplicationServletRegistration
        implements ServletRegistration.Dynamic {

    /**
     * The string manager for this package.
     */
    private static final StringManager sm =
      StringManager.getManager(Constants.Package);

    private final Wrapper wrapper;
    private final Context context;

    public ApplicationServletRegistration(Wrapper wrapper,
            Context context) {
        this.wrapper = wrapper;
        this.context = context;

    }

    @Override
    public String getClassName() {
        return wrapper.getServletClass();
    }

    @Override
    public String getInitParameter(String name) {
        return wrapper.findInitParameter(name);
    }

    @Override
    public Map<String, String> getInitParameters() {
        ParameterMap<String,String> result = new ParameterMap<>();

        String[] parameterNames = wrapper.findInitParameters();

        for (String parameterName : parameterNames) {
            result.put(parameterName, wrapper.findInitParameter(parameterName));
        }

        result.setLocked(true);
        return result;
    }

    @Override
    public String getName() {
        return wrapper.getName();
    }

    @Override
    public boolean setInitParameter(String name, String value) {
        if (name == null || value == null) {
            throw new IllegalArgumentException(
                    sm.getString("applicationFilterRegistration.nullInitParam",
                            name, value));
        }
        if (getInitParameter(name) != null) {
            return false;
        }

        wrapper.addInitParameter(name, value);

        return true;
    }

    @Override
    public Set<String> setInitParameters(Map<String, String> initParameters) {

        Set<String> conflicts = new HashSet<>();

        for (Map.Entry<String, String> entry : initParameters.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException(sm.getString(
                        "applicationFilterRegistration.nullInitParams",
                                entry.getKey(), entry.getValue()));
            }
            if (getInitParameter(entry.getKey()) != null) {
                conflicts.add(entry.getKey());
            }
        }

        // 必须添加一个单独的循环，因为如果有问题，SPEC根本不需要更新
        if (conflicts.isEmpty()) {
            for (Map.Entry<String, String> entry : initParameters.entrySet()) {
                setInitParameter(entry.getKey(), entry.getValue());
            }
        }

        return conflicts;
    }

    @Override
    public void setAsyncSupported(boolean asyncSupported) {
        wrapper.setAsyncSupported(asyncSupported);
    }

    @Override
    public void setLoadOnStartup(int loadOnStartup) {
        wrapper.setLoadOnStartup(loadOnStartup);
    }

    @Override
    public void setMultipartConfig(MultipartConfigElement multipartConfig) {
        wrapper.setMultipartConfigElement(multipartConfig);
    }

    @Override
    public void setRunAsRole(String roleName) {
        wrapper.setRunAs(roleName);
    }

    @Override
    public Set<String> setServletSecurity(ServletSecurityElement constraint) {
        if (constraint == null) {
            throw new IllegalArgumentException(sm.getString(
                    "applicationServletRegistration.setServletSecurity.iae",
                    getName(), context.getName()));
        }

        if (!context.getState().equals(LifecycleState.STARTING_PREP)) {
            throw new IllegalStateException(sm.getString(
                    "applicationServletRegistration.setServletSecurity.ise",
                    getName(), context.getName()));
        }

        return context.addServletSecurity(this, constraint);
    }


    @Override
    public Set<String> addMapping(String... urlPatterns) {
        if (urlPatterns == null) {
            return Collections.emptySet();
        }

        Set<String> conflicts = new HashSet<>();

        for (String urlPattern : urlPatterns) {
            String wrapperName = context.findServletMapping(urlPattern);
            if (wrapperName != null) {
                Wrapper wrapper = (Wrapper) context.findChild(wrapperName);
                if (wrapper.isOverridable()) {
                    // Some Wrappers (从全局和主机 web.xml)可以重写而不是生成冲突
                    context.removeServletMapping(urlPattern);
                } else {
                    conflicts.add(urlPattern);
                }
            }
        }

        if (!conflicts.isEmpty()) {
            return conflicts;
        }

        for (String urlPattern : urlPatterns) {
            context.addServletMappingDecoded(
                    UDecoder.URLDecode(urlPattern, StandardCharsets.UTF_8), wrapper.getName());
        }
        return Collections.emptySet();
    }

    @Override
    public Collection<String> getMappings() {

        Set<String> result = new HashSet<>();
        String servletName = wrapper.getName();

        String[] urlPatterns = context.findServletMappings();
        for (String urlPattern : urlPatterns) {
            String name = context.findServletMapping(urlPattern);
            if (name.equals(servletName)) {
                result.add(urlPattern);
            }
        }
        return result;
    }

    @Override
    public String getRunAsRole() {
        return wrapper.getRunAs();
    }

}
