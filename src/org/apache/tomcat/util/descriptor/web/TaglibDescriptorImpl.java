package org.apache.tomcat.util.descriptor.web;

import javax.servlet.descriptor.TaglibDescriptor;

public class TaglibDescriptorImpl implements TaglibDescriptor {

    private final String location;
    private final String uri;

    public TaglibDescriptorImpl(String location, String uri) {
        this.location = location;
        this.uri = uri;
    }

    @Override
    public String getTaglibLocation() {
        return location;
    }

    @Override
    public String getTaglibURI() {
        return uri;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((location == null) ? 0 : location.hashCode());
        result = prime * result + ((uri == null) ? 0 : uri.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TaglibDescriptorImpl)) {
            return false;
        }
        TaglibDescriptorImpl other = (TaglibDescriptorImpl) obj;
        if (location == null) {
            if (other.location != null) {
                return false;
            }
        } else if (!location.equals(other.location)) {
            return false;
        }
        if (uri == null) {
            if (other.uri != null) {
                return false;
            }
        } else if (!uri.equals(other.uri)) {
            return false;
        }
        return true;
    }

}
