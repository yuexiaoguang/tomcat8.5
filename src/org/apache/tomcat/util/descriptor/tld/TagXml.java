package org.apache.tomcat.util.descriptor.tld;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.jsp.tagext.TagAttributeInfo;
import javax.servlet.jsp.tagext.TagInfo;
import javax.servlet.jsp.tagext.TagVariableInfo;

/**
 * 在标签库描述符中定义的标签模型.
 * 这表示从XML解析但与TagInfo不同的信息, 因为它不提供返回到定义它的标记库的链接.
 */
public class TagXml {
    private String name;
    private String tagClass;
    private String teiClass;
    private String bodyContent = TagInfo.BODY_CONTENT_JSP;
    private String displayName;
    private String smallIcon;
    private String largeIcon;
    private String info;
    private boolean dynamicAttributes;
    private final List<TagAttributeInfo> attributes = new ArrayList<>();
    private final List<TagVariableInfo> variables = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTagClass() {
        return tagClass;
    }

    public void setTagClass(String tagClass) {
        this.tagClass = tagClass;
    }

    public String getTeiClass() {
        return teiClass;
    }

    public void setTeiClass(String teiClass) {
        this.teiClass = teiClass;
    }

    public String getBodyContent() {
        return bodyContent;
    }

    public void setBodyContent(String bodyContent) {
        this.bodyContent = bodyContent;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getSmallIcon() {
        return smallIcon;
    }

    public void setSmallIcon(String smallIcon) {
        this.smallIcon = smallIcon;
    }

    public String getLargeIcon() {
        return largeIcon;
    }

    public void setLargeIcon(String largeIcon) {
        this.largeIcon = largeIcon;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public boolean hasDynamicAttributes() {
        return dynamicAttributes;
    }

    public void setDynamicAttributes(boolean dynamicAttributes) {
        this.dynamicAttributes = dynamicAttributes;
    }

    public List<TagAttributeInfo> getAttributes() {
        return attributes;
    }

    public List<TagVariableInfo> getVariables() {
        return variables;
    }
}
