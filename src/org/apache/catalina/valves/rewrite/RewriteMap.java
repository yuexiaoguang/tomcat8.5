package org.apache.catalina.valves.rewrite;

public interface RewriteMap {

    public String setParameters(String params);

    public String lookup(String key);
}
