package org.apache.catalina.core;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;

/**
 * 包装一个<code>javax.servlet.ServletRequest</code>，
 * 转换应用程序请求对象(这可能是传递给servlet的原始消息, 或者可能基于 2.3
 * <code>javax.servlet.ServletRequestWrapper</code> class)
 * 回到一个内部的<code>org.apache.catalina.Request</code>.
 * <p>
 * <strong>WARNING</strong>: 
 * 由于java不支持多重继承, <code>ApplicationRequest</code>中的所有逻辑在<code>ApplicationHttpRequest</code>是重复的. 
 * 确保在进行更改时保持这两个类同步!
 */
class ApplicationRequest extends ServletRequestWrapper {


    // ------------------------------------------------------- Static Variables


    /**
     * 请求调度程序特殊的属性名称集合.
     */
    protected static final String specials[] =
    { RequestDispatcher.INCLUDE_REQUEST_URI,
      RequestDispatcher.INCLUDE_CONTEXT_PATH,
      RequestDispatcher.INCLUDE_SERVLET_PATH,
      RequestDispatcher.INCLUDE_PATH_INFO,
      RequestDispatcher.INCLUDE_QUERY_STRING,
      RequestDispatcher.FORWARD_REQUEST_URI,
      RequestDispatcher.FORWARD_CONTEXT_PATH,
      RequestDispatcher.FORWARD_SERVLET_PATH,
      RequestDispatcher.FORWARD_PATH_INFO,
      RequestDispatcher.FORWARD_QUERY_STRING };


    // ----------------------------------------------------------- Constructors


    /**
     * @param request The servlet request being wrapped
     */
    public ApplicationRequest(ServletRequest request) {
        super(request);
        setRequest(request);
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 这个请求的请求属性. 
     * 这是从包装请求初始化的，但是允许更新.
     */
    protected final HashMap<String, Object> attributes = new HashMap<>();


    // ------------------------------------------------- ServletRequest Methods


    /**
     * @param name 要检索的属性的名称
     */
    @Override
    public Object getAttribute(String name) {
        synchronized (attributes) {
            return (attributes.get(name));
        }
    }


    @Override
    public Enumeration<String> getAttributeNames() {

        synchronized (attributes) {
            return Collections.enumeration(attributes.keySet());
        }

    }


    /**
     * @param name 要删除的属性的名称
     */
    @Override
    public void removeAttribute(String name) {

        synchronized (attributes) {
            attributes.remove(name);
            if (!isSpecial(name))
                getRequest().removeAttribute(name);
        }

    }


    /**
     * @param name 要设置的属性的名称
     * @param value 属性值
     */
    @Override
    public void setAttribute(String name, Object value) {

        synchronized (attributes) {
            attributes.put(name, value);
            if (!isSpecial(name))
                getRequest().setAttribute(name, value);
        }

    }


    // ------------------------------------------ ServletRequestWrapper Methods


    /**
     * 设置包装的请求.
     *
     * @param request The new wrapped request
     */
    @Override
    public void setRequest(ServletRequest request) {

        super.setRequest(request);

        // 初始化此请求的属性
        synchronized (attributes) {
            attributes.clear();
            Enumeration<String> names = request.getAttributeNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                Object value = request.getAttribute(name);
                attributes.put(name, value);
            }
        }
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 是否是一个特殊的属性名称，只加入included servlet?
     *
     * @param name 要测试的属性名称
     */
    protected boolean isSpecial(String name) {

        for (int i = 0; i < specials.length; i++) {
            if (specials[i].equals(name))
                return true;
        }
        return false;
    }
}
