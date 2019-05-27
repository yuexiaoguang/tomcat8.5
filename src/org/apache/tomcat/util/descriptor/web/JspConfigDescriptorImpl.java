package org.apache.tomcat.util.descriptor.web;

import java.util.ArrayList;
import java.util.Collection;

import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.descriptor.JspPropertyGroupDescriptor;
import javax.servlet.descriptor.TaglibDescriptor;

public class JspConfigDescriptorImpl implements JspConfigDescriptor {

    private final Collection<JspPropertyGroupDescriptor> jspPropertyGroups;
    private final Collection<TaglibDescriptor> taglibs;

    public JspConfigDescriptorImpl(Collection<JspPropertyGroupDescriptor> jspPropertyGroups,
                                   Collection<TaglibDescriptor> taglibs) {
        this.jspPropertyGroups = jspPropertyGroups;
        this.taglibs = taglibs;
    }

    @Override
    public Collection<JspPropertyGroupDescriptor> getJspPropertyGroups() {
        return new ArrayList<>(jspPropertyGroups);
    }

    @Override
    public Collection<TaglibDescriptor> getTaglibs() {
        return new ArrayList<>(taglibs);
    }
}
