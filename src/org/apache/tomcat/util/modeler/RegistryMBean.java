package org.apache.tomcat.util.modeler;


import java.util.List;

import javax.management.ObjectName;

/**
 * 建模MBean的接口.
 *
 * 这是建模者的主要切入点. 它提供了创建和操作模型mbeans并简化其使用的方法.
 *
 * 从1.1版开始, 这不再是单例, 静态方法被强烈弃用. 在容器环境中，我们可以期望不同的应用程序使用不同的注册表.
 */
public interface RegistryMBean {

    /**
     * 在一组mbeans上调用一个操作.
     *
     * @param mbeans ObjectNames列表
     * @param operation 要执行的操作. 通常是 "init" "start" "stop" "destroy"
     * @param failFirst 异常情况下的行为 - 如果是 false, 忽略错误
     * @throws Exception 调用操作时出错
     */
    public void invoke(List<ObjectName> mbeans, String operation, boolean failFirst)
            throws Exception;

    /**
     * 通过创建建模器mbean并将其添加到MBeanServer, 来注册bean.
     *
     * 如果未加载元数据, 将在同一个包或其父级包中查找并读取"mbeans-descriptors.ser" 或 "mbeans-descriptors.xml"文件.
     *
     * 如果bean是DynamicMBean的一个实例. 它的元数据将被转换为模型mbean，我们将它包装起来 - 因此将支持建模服务
     *
     * 如果仍未找到元数据, 反射将用于自动提取它.
     *
     * 如果mbean已经以此名称注册, 它将首先取消注册.
     *
     * 如果组件实现了MBeanRegistration, 方法将被调用.
     * 如果该方法有一个将RegistryMBean作为参数的方法“setRegistry”, 它将被当前的注册表调用.
     *
     *
     * @param bean 要注册的对象
     * @param oname 用于注册的名称
     * @param type mbean的类型, 在mbeans-descriptors中声明. 如果是 null, 将使用该类的名称. 可以用作提示或子类.
     * 
     * @throws Exception 注册MBean时出错
     *
     * @since 1.1
     */
    public void registerComponent(Object bean, String oname, String type)
           throws Exception;

    /**
     * 取消注册组件. 首先检查它是否已注册, 并掩盖所有错误.
     *
     * @param oname bean使用的名称
     *
     * @since 1.1
     */
    public void unregisterComponent(String oname);


     /**
      * 返回一个int ID以便更快地访问. 将用于通知和想要优化的其他操作.
      *
      * @param domain 命名空间
      * @param name  通知的类型
      * @return  唯一的id是 domain:name 组合
      * @since 1.1
      */
    public int getId(String domain, String name);


    /**
     * 重置此注册表缓存的所有元数据. 应该被调用来支持重载. 现有的mbeans不会受到影响或修改.
     *
     * 如果Registry 未注册，将自动调用它.
     * @since 1.1
     */
    public void stop();
}
