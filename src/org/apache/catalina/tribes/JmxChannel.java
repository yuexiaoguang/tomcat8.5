package org.apache.catalina.tribes;

import javax.management.MBeanRegistration;


public interface JmxChannel extends MBeanRegistration {

    /**
     * 如果设置为 true, 这个channel使用jmx注册.
     * 
     * @return true 如果这个 channel 将使用jmx注册.
     */
    public boolean isJmxEnabled();

    /**
     * 如果设置为 true, 这个channel使用jmx注册.
     * @param jmxEnabled  true 如果这个 channel使用jmx注册.
     */
    public void setJmxEnabled(boolean jmxEnabled);

    /**
     * 返回这个channel注册的 jmx域名.
     * @return jmxDomain
     */
    public String getJmxDomain();

    /**
     * 设置这个channel注册的 jmx域名.
     * 
     * @param jmxDomain 这个channel注册的 jmx域名.
     */
    public void setJmxDomain(String jmxDomain);

    /**
     * 返回channel ObjectName使用的 jmx 前缀.
     */
    public String getJmxPrefix();

    /**
     * 设置channel ObjectName使用的 jmx 前缀.
     * 
     * @param jmxPrefix 使用的 jmx 前缀.
     */
    public void setJmxPrefix(String jmxPrefix);

}
