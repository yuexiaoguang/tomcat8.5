package org.apache.tomcat.util.descriptor.web;

import java.util.ArrayList;
import java.util.Collection;

import javax.servlet.descriptor.JspPropertyGroupDescriptor;

public class JspPropertyGroupDescriptorImpl
        implements JspPropertyGroupDescriptor{

    private final JspPropertyGroup jspPropertyGroup;


    public JspPropertyGroupDescriptorImpl(
            JspPropertyGroup jspPropertyGroup) {
        this.jspPropertyGroup = jspPropertyGroup;
    }


    @Override
    public String getBuffer() {
        return jspPropertyGroup.getBuffer();
    }


    @Override
    public String getDefaultContentType() {
        return jspPropertyGroup.getDefaultContentType();
    }


    @Override
    public String getDeferredSyntaxAllowedAsLiteral() {
        String result = null;

        if (jspPropertyGroup.getDeferredSyntax() != null) {
            result = jspPropertyGroup.getDeferredSyntax().toString();
        }

        return result;
    }


    @Override
    public String getElIgnored() {
        String result = null;

        if (jspPropertyGroup.getElIgnored() != null) {
            result = jspPropertyGroup.getElIgnored().toString();
        }

        return result;
    }


    @Override
    public String getErrorOnUndeclaredNamespace() {
        String result = null;

        if (jspPropertyGroup.getErrorOnUndeclaredNamespace() != null) {
            result =
                jspPropertyGroup.getErrorOnUndeclaredNamespace().toString();
        }

        return result;
    }


    @Override
    public Collection<String> getIncludeCodas() {
        return new ArrayList<>(jspPropertyGroup.getIncludeCodas());
    }


    @Override
    public Collection<String> getIncludePreludes() {
        return new ArrayList<>(jspPropertyGroup.getIncludePreludes());
    }


    @Override
    public String getIsXml() {
        String result = null;

        if (jspPropertyGroup.getIsXml() != null) {
            result = jspPropertyGroup.getIsXml().toString();
        }

        return result;
    }


    @Override
    public String getPageEncoding() {
        return jspPropertyGroup.getPageEncoding();
    }


    @Override
    public String getScriptingInvalid() {
        String result = null;

        if (jspPropertyGroup.getScriptingInvalid() != null) {
            result = jspPropertyGroup.getScriptingInvalid().toString();
        }

        return result;
    }


    @Override
    public String getTrimDirectiveWhitespaces() {
        String result = null;

        if (jspPropertyGroup.getTrimWhitespace() != null) {
            result = jspPropertyGroup.getTrimWhitespace().toString();
        }

        return result;
    }


    @Override
    public Collection<String> getUrlPatterns() {
        return new ArrayList<>(jspPropertyGroup.getUrlPatterns());
    }
}
