package org.apache.tomcat.util.descriptor.web;

import java.util.List;

public interface Injectable {
    public String getName();
    public void addInjectionTarget(String injectionTargetName, String jndiName);
    public List<InjectionTarget> getInjectionTargets();
}
