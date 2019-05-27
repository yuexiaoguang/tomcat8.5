package org.apache.tomcat.util.modeler;


import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import javax.management.DynamicMBean;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.modeler.modules.ModelerSource;

/*
   Issues:
   - exceptions - too many "throws Exception"
   - double check the interfaces
   - 开始在tomcat中删除使用实验方法, 然后删除方法 ( before 1.1 final )
   - 足够防止使用Registry来避免mbean服务器中的权限检查 ?
*/

/**
 * 建模者MBean的注册表.
 *
 * 这是建模者的主要切入点. 它提供了创建和操作模型mbeans并简化其使用的方法.
 *
 * 这个类本身就是一个mbean.
 *
 * IMPORTANT: 未标记为@since x.x的公共方法是实验性的或内部的. 不应该使用.
 */
public class Registry implements RegistryMBean, MBeanRegistration  {
    
	private static final Log log = LogFactory.getLog(Registry.class);

    // Support for the factory methods

    /** 将用于隔离不同的应用程序并增强安全性.
     */
    private static final HashMap<Object,Registry> perLoaderRegistries = null;

    /**
     * 第一次调用时, 由工厂方法创建的注册表实例.
     */
    private static Registry registry = null;

    // Per registry fields

    /**
     * 将用于注册管理bean的<code>MBeanServer</code>实例.
     */
    private MBeanServer server = null;

    /**
     * 此注册表知道的bean的ManagedBean实例集, 由名称作为Key.
     */
    private HashMap<String,ManagedBean> descriptors = new HashMap<>();

    /** 托管bean列表s, 由类名作为Key
     */
    private HashMap<String,ManagedBean> descriptorsByClass = new HashMap<>();

    // 映射以避免重复搜索或加载描述符
    private HashMap<String,URL> searchedPaths = new HashMap<>();

    private Object guard;

    // Id - small ints to use array access. No reset on stop()
    // Used for notifications
    private final Hashtable<String,Hashtable<String,Integer>> idDomains =
        new Hashtable<>();
    private final Hashtable<String,int[]> ids = new Hashtable<>();


    // ----------------------------------------------------------- Constructors

     public Registry() {
        super();
    }

    // -------------------- Static methods  --------------------

    /**
     * 用于创建和返回<code>Registry</code>实例的工厂方法.
     *
     * 当前版本使用 static - 未来的版本可以使用线程类加载器.
     *
     * @param key 支持应用程序隔离. 如果是 null, 将使用上下文类加载器 (如果调用setUseContextClassLoader), 或者返回默认注册表.
     * @param guard 阻止不受信任的组件访问注册表
     * 
     * @return the registry
     * @since 1.1
     */
    public static synchronized Registry getRegistry(Object key, Object guard) {
        Registry localRegistry;
        if( perLoaderRegistries!=null ) {
            if( key==null )
                key=Thread.currentThread().getContextClassLoader();
            if( key != null ) {
                localRegistry = perLoaderRegistries.get(key);
                if( localRegistry == null ) {
                    localRegistry=new Registry();
//                    localRegistry.key=key;
                    localRegistry.guard=guard;
                    perLoaderRegistries.put( key, localRegistry );
                    return localRegistry;
                }
                if( localRegistry.guard != null &&
                        localRegistry.guard != guard ) {
                    return null; // XXX Should I throw a permission ex ?
                }
                return localRegistry;
            }
        }

        // static
        if (registry == null) {
            registry = new Registry();
        }
        if( registry.guard != null &&
                registry.guard != guard ) {
            return null;
        }
        return (registry);
    }

    // -------------------- Generic methods  --------------------

    /** 生命周期方法 - 清理注册表元数据.
     *  从 resetMetadata() 调用.
     *
     * @since 1.1
     */
    @Override
    public void stop() {
        descriptorsByClass = new HashMap<>();
        descriptors = new HashMap<>();
        searchedPaths=new HashMap<>();
    }

    /**
     * 通过创建建模器mbean并将其添加到MBeanServer来注册bean.
     *
     * 如果未加载元数据, 将在同一个包中或其父级包中查找并读取 "mbeans-descriptors.ser" 或 "mbeans-descriptors.xml"文件.
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
     * @since 1.1
     */
    @Override
    public void registerComponent(Object bean, String oname, String type)
           throws Exception
    {
        registerComponent(bean, new ObjectName(oname), type);
    }

    /**
     * 取消注册组件. 首先检查它是否已注册, 并掩盖所有错误.
     *
     * @param oname 用于取消注册的名称
     *
     * @since 1.1
     */
    @Override
    public void unregisterComponent( String oname ) {
        try {
            unregisterComponent(new ObjectName(oname));
        } catch (MalformedObjectNameException e) {
            log.info("Error creating object name " + e );
        }
    }


    /**
     * 在mbeans列表上调用操作. 可用于实现生命周期操作.
     *
     * @param mbeans 将调用操作的ObjectName列表
     * @param operation  操作名称 ( init, start, stop, etc)
     * @param failFirst  如果是 false, 异常将被忽略
     * 
     * @throws Exception 调用操作时出错
     * @since 1.1
     */
    @Override
    public void invoke(List<ObjectName> mbeans, String operation,
            boolean failFirst ) throws Exception {
        if( mbeans==null ) {
            return;
        }
        Iterator<ObjectName> itr = mbeans.iterator();
        while(itr.hasNext()) {
            ObjectName current = itr.next();
            try {
                if(current == null) {
                    continue;
                }
                if(getMethodInfo(current, operation) == null) {
                    continue;
                }
                getMBeanServer().invoke(current, operation,
                        new Object[] {}, new String[] {});

            } catch( Exception t ) {
                if( failFirst ) throw t;
                log.info("Error initializing " + current + " " + t.toString());
            }
        }
    }

    // -------------------- ID registry --------------------

    /**
     * 返回一个int ID以便更快地访问. 将用于通知和想要优化的其他操作.
     *
     * @param domain 命名空间
     * @param name 通知的类型
     * 
     * @return 唯一的id是 domain:name 组合
     * @since 1.1
     */
    @Override
    public synchronized int getId( String domain, String name) {
        if( domain==null) {
            domain="";
        }
        Hashtable<String,Integer> domainTable = idDomains.get(domain);
        if( domainTable == null ) {
            domainTable = new Hashtable<>();
            idDomains.put( domain, domainTable);
        }
        if( name==null ) {
            name="";
        }
        Integer i = domainTable.get(name);

        if( i!= null ) {
            return i.intValue();
        }

        int id[] = ids.get(domain);
        if( id == null ) {
            id=new int[1];
            ids.put( domain, id);
        }
        int code=id[0]++;
        domainTable.put( name, Integer.valueOf( code ));
        return code;
    }

    // -------------------- Metadata   --------------------
    // methods from 1.0

    /**
     * 将新的bean元数据添加到此注册表已知的bean集.
     * 这由内部组件使用.
     *
     * @param bean 要添加的托管bean
     * @since 1.0
     */
    public void addManagedBean(ManagedBean bean) {
        // XXX Use group + name
        descriptors.put(bean.getName(), bean);
        if( bean.getType() != null ) {
            descriptorsByClass.put( bean.getType(), bean );
        }
    }


    /**
     * 查找并返回指定bean名称的托管bean; 否则返回<code>null</code>.
     *
     * @param name 要返回的托管bean的名称. Since 1.1, 可以使用短名称或类的全名.
     * 
     * @return 托管bean
     * @since 1.0
     */
    public ManagedBean findManagedBean(String name) {
        // XXX Group ?? Use Group + Type
        ManagedBean mb = descriptors.get(name);
        if( mb==null )
            mb = descriptorsByClass.get(name);
        return mb;
    }

    // -------------------- Helpers  --------------------

    /**
     * 从元数据中获取对象的属性类型.
     *
     * @param oname bean 名称
     * @param attName 属性名
     * 
     * @return null 如果找不到有关该属性的元数据
     * @since 1.1
     */
    public String getType( ObjectName oname, String attName )
    {
        String type=null;
        MBeanInfo info=null;
        try {
            info=server.getMBeanInfo(oname);
        } catch (Exception e) {
            log.info( "Can't find metadata for object" + oname );
            return null;
        }

        MBeanAttributeInfo attInfo[]=info.getAttributes();
        for( int i=0; i<attInfo.length; i++ ) {
            if( attName.equals(attInfo[i].getName())) {
                type=attInfo[i].getType();
                return type;
            }
        }
        return null;
    }

    /**
     * 查找方法的操作信息
     *
     * @param oname bean 名称
     * @param opName 操作名称
     * 
     * @return 指定操作的操作信息
     */
    public MBeanOperationInfo getMethodInfo( ObjectName oname, String opName )
    {
        MBeanInfo info=null;
        try {
            info=server.getMBeanInfo(oname);
        } catch (Exception e) {
            log.info( "Can't find metadata " + oname );
            return null;
        }
        MBeanOperationInfo attInfo[]=info.getOperations();
        for( int i=0; i<attInfo.length; i++ ) {
            if( opName.equals(attInfo[i].getName())) {
                return attInfo[i];
            }
        }
        return null;
    }

    /**
     * 取消注册组件. 通过检查mbean是否已经注册来避免异常
     *
     * @param oname bean名称
     */
    public void unregisterComponent( ObjectName oname ) {
        try {
            if (oname != null && getMBeanServer().isRegistered(oname)) {
                getMBeanServer().unregisterMBean(oname);
            }
        } catch (Throwable t) {
            log.error("Error unregistering mbean", t);
        }
    }

    /**
     * 用于创建和返回<code>MBeanServer</code>实例的工厂方法.
     */
    public synchronized MBeanServer getMBeanServer() {
        if (server == null) {
            long t1 = System.currentTimeMillis();
            if (MBeanServerFactory.findMBeanServer(null).size() > 0) {
                server = MBeanServerFactory.findMBeanServer(null).get(0);
                if (log.isDebugEnabled()) {
                    log.debug("Using existing MBeanServer " + (System.currentTimeMillis() - t1));
                }
            } else {
                server = ManagementFactory.getPlatformMBeanServer();
                if (log.isDebugEnabled()) {
                    log.debug("Creating MBeanServer" + (System.currentTimeMillis() - t1));
                }
            }
        }
        return server;
    }

    /**
     * 查找或加载元数据.
     * 
     * @param bean The bean
     * @param beanClass The bean class
     * @param type 注册表类型
     * 
     * @return 托管bean
     * 
     * @throws Exception 发生错误
     */
    public ManagedBean findManagedBean(Object bean, Class<?> beanClass,
            String type) throws Exception {
        if( bean!=null && beanClass==null ) {
            beanClass=bean.getClass();
        }

        if( type==null ) {
            type=beanClass.getName();
        }

        // 首先寻找现有的描述符
        ManagedBean managed = findManagedBean(type);

        // 在同一个包中搜索描述符
        if( managed==null ) {
            // 检查包和父级包
            if( log.isDebugEnabled() ) {
                log.debug( "Looking for descriptor ");
            }
            findDescriptor( beanClass, type );

            managed=findManagedBean(type);
        }

        // 仍未找到 - 使用反射
        if( managed==null ) {
            if( log.isDebugEnabled() ) {
                log.debug( "Introspecting ");
            }

            // introspection
            load("MbeansDescriptorsIntrospectionSource", beanClass, type);

            managed=findManagedBean(type);
            if( managed==null ) {
                log.warn( "No metadata found for " + type );
                return null;
            }
            managed.setName( type );
            addManagedBean(managed);
        }
        return managed;
    }


    /**
     * EXPERIMENTAL 根据类型将字符串转换为对象. 由几个组件使用. 可以提供一些可插拔性. 这是为了保持一致，避免在其他任务中重复
     *
     * @param type 结果值的完全限定类名
     * @param value 要转换的字符串值
     * 
     * @return 转换后的值
     */
    public Object convertValue(String type, String value)
    {
        Object objValue=value;

        if( type==null || "java.lang.String".equals( type )) {
            // string is default
            objValue=value;
        } else if( "javax.management.ObjectName".equals( type ) ||
                "ObjectName".equals( type )) {
            try {
                objValue=new ObjectName( value );
            } catch (MalformedObjectNameException e) {
                return null;
            }
        } else if( "java.lang.Integer".equals( type ) ||
                "int".equals( type )) {
            objValue=Integer.valueOf( value );
        } else if( "java.lang.Long".equals( type ) ||
                "long".equals( type )) {
            objValue=Long.valueOf( value );
        } else if( "java.lang.Boolean".equals( type ) ||
                "boolean".equals( type )) {
            objValue=Boolean.valueOf( value );
        }
        return objValue;
    }

    /**
     * Experimental. 加载描述符.
     *
     * @param sourceType 源类型
     * @param source The bean
     * @param param 要加载的类型
     * 
     * @return 描述符列表
     * @throws Exception 加载描述符时出错
     */
    public List<ObjectName> load( String sourceType, Object source,
            String param) throws Exception {
        if( log.isTraceEnabled()) {
            log.trace("load " + source );
        }
        String location=null;
        String type=null;
        Object inputsource=null;

        if( source instanceof URL ) {
            URL url=(URL)source;
            location=url.toString();
            type=param;
            inputsource=url.openStream();
            if (sourceType == null && location.endsWith(".xml")) {
                sourceType = "MbeansDescriptorsDigesterSource";
            }
        } else if( source instanceof File ) {
            location=((File)source).getAbsolutePath();
            inputsource=new FileInputStream((File)source);
            type=param;
            if (sourceType == null && location.endsWith(".xml")) {
                sourceType = "MbeansDescriptorsDigesterSource";
            }
        } else if( source instanceof InputStream ) {
            type=param;
            inputsource=source;
        } else if( source instanceof Class<?> ) {
            location=((Class<?>)source).getName();
            type=param;
            inputsource=source;
            if( sourceType== null ) {
                sourceType="MbeansDescriptorsIntrospectionSource";
            }
        }

        if( sourceType==null ) {
            sourceType="MbeansDescriptorsDigesterSource";
        }
        ModelerSource ds=getModelerSource(sourceType);
        List<ObjectName> mbeans =
            ds.loadDescriptors(this, type, inputsource);

        return mbeans;
    }


    /**
     * 注册组件
     *
     * @param bean The bean
     * @param oname 对象名称
     * @param type 注册表类型
     * 
     * @throws Exception 注册组件时出错
     */
    public void registerComponent(Object bean, ObjectName oname, String type)
           throws Exception
    {
        if( log.isDebugEnabled() ) {
            log.debug( "Managed= "+ oname);
        }

        if( bean ==null ) {
            log.error("Null component " + oname );
            return;
        }

        try {
            if( type==null ) {
                type=bean.getClass().getName();
            }

            ManagedBean managed = findManagedBean(null, bean.getClass(), type);

            // 真正的mbean已创建并注册
            DynamicMBean mbean = managed.createMBean(bean);

            if(  getMBeanServer().isRegistered( oname )) {
                if( log.isDebugEnabled()) {
                    log.debug("Unregistering existing component " + oname );
                }
                getMBeanServer().unregisterMBean( oname );
            }

            getMBeanServer().registerMBean( mbean, oname);
        } catch( Exception ex) {
            log.error("Error registering " + oname, ex );
            throw ex;
        }
    }

    /**
     * 查找包和父包中的组件描述符.
     *
     * @param packageName 包名
     * @param classLoader 类加载器
     */
    public void loadDescriptors( String packageName, ClassLoader classLoader  ) {
        String res=packageName.replace( '.', '/');

        if( log.isTraceEnabled() ) {
            log.trace("Finding descriptor " + res );
        }

        if( searchedPaths.get( packageName ) != null ) {
            return;
        }

        String descriptors = res + "/mbeans-descriptors.xml";
        URL dURL = classLoader.getResource( descriptors );

        if (dURL == null) {
            return;
        }

        log.debug( "Found " + dURL);
        searchedPaths.put( packageName,  dURL );
        try {
            load("MbeansDescriptorsDigesterSource", dURL, null);
        } catch(Exception ex ) {
            log.error("Error loading " + dURL);
        }
    }

    /**
     * 查找包和父包中的组件描述符.
     */
    private void findDescriptor(Class<?> beanClass, String type) {
        if( type==null ) {
            type=beanClass.getName();
        }
        ClassLoader classLoader=null;
        if( beanClass!=null ) {
            classLoader=beanClass.getClassLoader();
        }
        if( classLoader==null ) {
            classLoader=Thread.currentThread().getContextClassLoader();
        }
        if( classLoader==null ) {
            classLoader=this.getClass().getClassLoader();
        }

        String className=type;
        String pkg=className;
        while( pkg.indexOf( ".") > 0 ) {
            int lastComp=pkg.lastIndexOf( ".");
            if( lastComp <= 0 ) return;
            pkg=pkg.substring(0, lastComp);
            if( searchedPaths.get( pkg ) != null ) {
                return;
            }
            loadDescriptors(pkg, classLoader);
        }
        return;
    }

    private ModelerSource getModelerSource( String type )
            throws Exception
    {
        if( type==null ) type="MbeansDescriptorsDigesterSource";
        if( type.indexOf( ".") < 0 ) {
            type="org.apache.tomcat.util.modeler.modules." + type;
        }

        Class<?> c = Class.forName(type);
        ModelerSource ds=(ModelerSource)c.getConstructor().newInstance();
        return ds;
    }


    // -------------------- Registration  --------------------

    @Override
    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name) throws Exception
    {
        this.server=server;
        return name;
    }

    @Override
    public void postRegister(Boolean registrationDone) {
    }

    @Override
    public void preDeregister() throws Exception {
    }

    @Override
    public void postDeregister() {
    }
}
