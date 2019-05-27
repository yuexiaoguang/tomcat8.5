package org.apache.catalina.core;

import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

/**
 * <b>StandardWrapper</b>对象的外观模式
 */
public final class StandardWrapperFacade implements ServletConfig {


    // ----------------------------------------------------------- Constructors


    /**
     * @param config 关联的wrapper
     */
    public StandardWrapperFacade(StandardWrapper config) {
        super();
        this.config = config;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Wrapped config.
     */
    private final ServletConfig config;


    /**
     * Wrapped context (facade).
     */
    private ServletContext context = null;


    // -------------------------------------------------- ServletConfig Methods


    @Override
    public String getServletName() {
        return config.getServletName();
    }


    @Override
    public ServletContext getServletContext() {
        if (context == null) {
            context = config.getServletContext();
            if (context instanceof ApplicationContext) {
                context = ((ApplicationContext) context).getFacade();
            }
        }
        return (context);
    }


    @Override
    public String getInitParameter(String name) {
        return config.getInitParameter(name);
    }


    @Override
    public Enumeration<String> getInitParameterNames() {
        return config.getInitParameterNames();
    }


}
