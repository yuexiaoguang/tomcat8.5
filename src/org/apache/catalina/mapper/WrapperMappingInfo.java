package org.apache.catalina.mapper;

import org.apache.catalina.Wrapper;

/**
 * 封装用于注册Wrapper映射的信息.
 */
public class WrapperMappingInfo {

    private final String mapping;
    private final Wrapper wrapper;
    private final boolean jspWildCard;
    private final boolean resourceOnly;

    public WrapperMappingInfo(String mapping, Wrapper wrapper,
            boolean jspWildCard, boolean resourceOnly) {
        this.mapping = mapping;
        this.wrapper = wrapper;
        this.jspWildCard = jspWildCard;
        this.resourceOnly = resourceOnly;
    }

    public String getMapping() {
        return mapping;
    }

    public Wrapper getWrapper() {
        return wrapper;
    }

    public boolean isJspWildCard() {
        return jspWildCard;
    }

    public boolean isResourceOnly() {
        return resourceOnly;
    }

}
