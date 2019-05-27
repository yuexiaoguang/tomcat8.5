package org.apache.jasper.el;

import javax.el.PropertyNotWritableException;

public class JspPropertyNotWritableException extends
        PropertyNotWritableException {

    private static final long serialVersionUID = 1L;

    public JspPropertyNotWritableException(String mark, PropertyNotWritableException e) {
        super(mark + " " + e.getMessage(), e.getCause());
    }
}
