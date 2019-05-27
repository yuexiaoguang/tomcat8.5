package javax.servlet.descriptor;

import java.util.Collection;

public interface JspConfigDescriptor {
    public Collection<TaglibDescriptor> getTaglibs();
    public Collection<JspPropertyGroupDescriptor> getJspPropertyGroups();
}
