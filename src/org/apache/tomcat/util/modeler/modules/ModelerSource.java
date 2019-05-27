package org.apache.tomcat.util.modeler.modules;

import java.util.List;

import javax.management.ObjectName;

import org.apache.tomcat.util.modeler.Registry;

/**
 * 描述符数据的来源. 可以添加更多来源.
 */
public abstract class ModelerSource {
    protected Object source;

    /**
     * 加载数据，返回项目列表.
     *
     * @param registry 注册表
     * @param type bean注册表类型
     * @param source 反射对象或其他来源
     * 
     * @return 对象名称列表
     * @throws Exception 加载描述符时出错
     */
    public abstract List<ObjectName> loadDescriptors(Registry registry,
            String type, Object source) throws Exception;
}
