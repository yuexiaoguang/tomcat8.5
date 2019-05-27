package org.apache.catalina.ha;

import org.apache.catalina.Valve;

/**
 * 集群阀门是Tomcat阀门结构的一个简单扩展，除了能够引用它所在的容器中的集群组件之外，还有一个小的扩展.
 */
public interface ClusterValve extends Valve{
    /**
     * 返回集群部署符关联的集群
     */
    public CatalinaCluster getCluster();

    /**
     * 关联集群部署符和集群
     * 
     * @param cluster CatalinaCluster
     */
    public void setCluster(CatalinaCluster cluster);
}
