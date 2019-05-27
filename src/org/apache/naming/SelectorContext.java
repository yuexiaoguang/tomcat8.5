package org.apache.naming;

import java.util.Hashtable;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NotContextException;
import javax.naming.OperationNotSupportedException;
import javax.naming.directory.InvalidAttributesException;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Catalina JNDI Context implementation.
 */
public class SelectorContext implements Context {


    // -------------------------------------------------------------- Constants


    /**
     * Namespace URL.
     */
    public static final String prefix = "java:";


    /**
     * Namespace URL length.
     */
    public static final int prefixLength = prefix.length();


    /**
     * 初始上下文前缀.
     */
    public static final String IC_PREFIX = "IC_";


    private static final Log log = LogFactory.getLog(SelectorContext.class);

    // ----------------------------------------------------------- Constructors


    /**
     * 构建使用给定环境的Catalina选择器上下文
     * @param env The environment
     */
    public SelectorContext(Hashtable<String,Object> env) {
        this.env = env;
        this.initialContext = false;
    }


    /**
     * 构建使用给定环境的Catalina选择器上下文
     * @param env The environment
     * @param initialContext <code>true</code>如果这是主要的初始上下文
     */
    public SelectorContext(Hashtable<String,Object> env, boolean initialContext) {
        this.env = env;
        this.initialContext = initialContext;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Environment.
     */
    protected final Hashtable<String,Object> env;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(SelectorContext.class);


    /**
     * 请求初始上下文.
     */
    protected final boolean initialContext;

    // -------------------------------------------------------- Context Methods


    /**
     * 检索命名对象. 如果名字是空的, 返回此上下文的新实例(它表示与此上下文相同的命名上下文, 但它的环境可以独立修改，可以并发访问).
     * 
     * @param name 要查找的对象的名称
     * @return 绑定到名称的对象
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public Object lookup(Name name)
        throws NamingException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("selectorContext.methodUsingName", "lookup",
                    name));
        }

        // 跳过URL header
        // 根据当前绑定找到合适的 NamingContext
        // 在该上下文中执行查找
        return getBoundContext().lookup(parseName(name));
    }


    /**
     * 检索命名对象.
     *
     * @param name 要查找的对象的名称
     * @return 绑定到名称的对象
     * @throws NamingException 如果遇到命名异常
     */
    @Override
    public Object lookup(String name)
        throws NamingException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("selectorContext.methodUsingString", "lookup",
                    name));
        }

        // Strip the URL header
        // 根据当前绑定找到合适的 NamingContext
        // 在该上下文中执行查找
        return getBoundContext().lookup(parseName(name));
    }


    /**
     * 将名称绑定到对象. 所有中间上下文和目标上下文必须存在(除了名称的最终原子组件以外的所有名称) 
     * 
     * @param name 绑定的名称; 可能是空
     * @param obj 要绑定的对象; 可能是null
     * @exception NameAlreadyBoundException 如果名称已绑定
     * @exception InvalidAttributesException 如果对象没有提供所有强制属性
     * @throws NamingException 如果遇到命名异常
     */
    @Override
    public void bind(Name name, Object obj)
        throws NamingException {
        getBoundContext().bind(parseName(name), obj);
    }


    /**
     * 将名称绑定到对象
     * 
     * @param name 绑定的名称; 可能是空
     * @param obj 要绑定的对象; 可能是null
     * @exception NameAlreadyBoundException 如果名称已绑定
     * @exception InvalidAttributesException 如果对象没有提供所有强制属性
     * @throws NamingException 如果遇到命名异常
     */
    @Override
    public void bind(String name, Object obj)
        throws NamingException {
        getBoundContext().bind(parseName(name), obj);
    }


    /**
     * 将名称绑定到对象, 覆盖任何现有的绑定.
     * 所有中间上下文和目标上下文必须已经存在 (除了名称的最终原子组件以外的所有名称)
     * <p>
     * 如果对象是一个DirContext, 与该名称相关联的任何现有属性都替换为该对象的属性. 
     * 否则, 与名称相关联的任何现有属性都保持不变
     * 
     * @param name 绑定的名称; 可能是空
     * @param obj 要绑定的对象; 可能是null
     * @exception InvalidAttributesException 如果对象没有提供所有强制属性
     * @throws NamingException 如果遇到命名异常
     */
    @Override
    public void rebind(Name name, Object obj)
        throws NamingException {
        getBoundContext().rebind(parseName(name), obj);
    }


    /**
     * 将名称绑定到对象, 覆盖任何现有的绑定.
     * 
     * @param name 绑定的名称; 可能是空
     * @param obj 要绑定的对象; 可能是null
     * @exception InvalidAttributesException 如果对象没有提供所有强制属性
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public void rebind(String name, Object obj)
        throws NamingException {
        getBoundContext().rebind(parseName(name), obj);
    }


    /**
     * 取消绑定命名对象. 从目标上下文中删除名称中的终端原子名称--除了名称的最终原子部分以外的所有名称.
     * <p>
     * 此方法是幂等的. 即使在目标上下文中没有绑定终端原子名称，它也会成功,
     * 但是抛出NameNotFoundException ,如果任何中间上下文不存在. 
     * 
     * @param name 绑定的名称; 可能是空
     * @exception NameNotFoundException 如果中间上下文不存在
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public void unbind(Name name)
        throws NamingException {
        getBoundContext().unbind(parseName(name));
    }


    /**
     * 取消绑定命名对象.
     * 
     * @param name 绑定的名称; 可能是空
     * @exception NameNotFoundException 如果中间上下文不存在
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public void unbind(String name)
        throws NamingException {
        getBoundContext().unbind(parseName(name));
    }


    /**
     * 将新名称绑定到对象, 并取消绑定旧名称. 两个名称都与此上下文相关. 与旧名称相关联的任何属性都与新名称关联. 
     * 旧名称的中间上下文没有改变.
     * 
     * @param oldName 现有绑定的名称; 不能为空
     * @param newName 新绑定的名称; 不能为空
     * @exception NameAlreadyBoundException 如果newName已绑定
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public void rename(Name oldName, Name newName)
        throws NamingException {
        getBoundContext().rename(parseName(oldName), parseName(newName));
    }


    /**
     * 将新名称绑定到对象, 并取消绑定旧名称.
     * 
     * @param oldName 现有绑定的名称; 不能为空
     * @param newName 新绑定的名称; 不能为空
     * @exception NameAlreadyBoundException 如果newName已绑定
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public void rename(String oldName, String newName)
        throws NamingException {
        getBoundContext().rename(parseName(oldName), parseName(newName));
    }


    /**
     * 枚举命名上下文中绑定的名称, 连同绑定到它们的对象的类名. 不包括任何子上下文的内容.
     * <p>
     * 如果在该上下文中添加或删除绑定, 它对先前返回的枚举的影响是未定义的.
     * 
     * @param name 要列出的上下文的名称
     * @return 在此上下文中绑定的名称和类名的枚举. 每个枚举元素的类型是NameClassPair.
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public NamingEnumeration<NameClassPair> list(Name name)
        throws NamingException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("selectorContext.methodUsingName", "list",
                    name));
        }

        return getBoundContext().list(parseName(name));
    }


    /**
     * 枚举命名上下文中绑定的名称, 连同绑定到它们的对象的类名.
     * 
     * @param name 要列出的上下文的名称
     * @return 在此上下文中绑定的名称和类名的枚举. 每个枚举元素的类型是NameClassPair.
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public NamingEnumeration<NameClassPair> list(String name)
        throws NamingException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("selectorContext.methodUsingString", "list",
                    name));
        }

        return getBoundContext().list(parseName(name));
    }


    /**
     * 枚举命名上下文中绑定的名称, 连同绑定到它们的对象的类名. 不包括任何子上下文的内容.
     * <p>
     * 如果在该上下文中添加或删除绑定, 它对先前返回的枚举的影响是未定义的.
     * 
     * @param name 要列出的上下文的名称
     * @return 在此上下文中绑定的枚举. 每个枚举元素的类型是Binding.
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public NamingEnumeration<Binding> listBindings(Name name)
        throws NamingException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("selectorContext.methodUsingName",
                    "listBindings", name));
        }

        return getBoundContext().listBindings(parseName(name));
    }


    /**
     * 枚举命名上下文中绑定的名称, 连同绑定到它们的对象.
     * 
     * @param name 要列出的上下文的名称
     * @return 在此上下文中绑定的枚举. 每个枚举元素的类型是Binding.
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public NamingEnumeration<Binding> listBindings(String name)
        throws NamingException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("selectorContext.methodUsingString",
                    "listBindings", name));
        }

        return getBoundContext().listBindings(parseName(name));
    }


    /**
     * 销毁指定的上下文并将其从命名空间中删除. 与名称相关联的任何属性也被移除. 中间上下文不会被销毁.
     * <p>
     * 此方法是幂等的. 即使在目标上下文中没有绑定终端原子名称，它也会成功, 但是会抛出NameNotFoundException, 如果任何中间上下文不存在. 
     * 
     * 在联合命名系统中, 一个命名系统的上下文可以绑定到另一个名称中. 随后可以在外来的上下文中使用复合名称查找和执行操作.
     * 但是, 销毁上下文的时候使用这个复合名称将失败并抛出NotContextException, 因为外来的上下文不是绑定的上下文的 "subcontext".
     * 相反, 使用 unbind() 删除绑定的外来上下文. 销毁外来的上下文需要执行外来上下文的"native"命名系统的上下文的 destroySubcontext()方法.
     * 
     * @param name 要销毁的上下文的名称; 不能是空
     * @exception NameNotFoundException 如果中间上下文不存在
     * @exception NotContextException 如果名称被绑定，但不命名上下文, 或者不命名适当类型的上下文
     */
    @Override
    public void destroySubcontext(Name name)
        throws NamingException {
        getBoundContext().destroySubcontext(parseName(name));
    }


    /**
     * 销毁指定的上下文并将其从命名空间中删除.
     * 
     * @param name 要销毁的上下文的名称; 不能是空
     * @exception NameNotFoundException 如果中间上下文不存在
     * @exception NotContextException 如果名称被绑定，但不命名上下文, 或者不命名适当类型的上下文
     */
    @Override
    public void destroySubcontext(String name)
        throws NamingException {
        getBoundContext().destroySubcontext(parseName(name));
    }


    /**
     * 创建并绑定新上下文. 使用给定名称创建新上下文并将其绑定到目标上下文中(除了名称的最终原子组件以外的所有名称).
     * 所有中间上下文和目标上下文必须已经存在.
     * 
     * @param name 要创建的上下文的名称; 不能是空
     * @return 新创建的上下文
     * @exception NameAlreadyBoundException 如果名称已绑定
     * @exception InvalidAttributesException 如果子上下文的创建需要属性的强制性规范
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public Context createSubcontext(Name name)
        throws NamingException {
        return getBoundContext().createSubcontext(parseName(name));
    }


    /**
     * 创建并绑定新上下文.
     * 
     * @param name 要创建的上下文的名称; 不能是空
     * @return 新创建的上下文
     * @exception NameAlreadyBoundException 如果名称已绑定
     * @exception InvalidAttributesException 如果子上下文的创建需要属性的强制性规范
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public Context createSubcontext(String name)
        throws NamingException {
        return getBoundContext().createSubcontext(parseName(name));
    }


    /**
     * 检索命名对象, 以下链接，除了名称的终端原子组件.如果绑定到名称的对象不是链接, 返回对象本身.
     * 
     * @param name 要查找的对象的名称
     * @return 绑定到名称的对象, 不遵循终端链接
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public Object lookupLink(Name name)
        throws NamingException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("selectorContext.methodUsingName",
                    "lookupLink", name));
        }

        return getBoundContext().lookupLink(parseName(name));
    }


    /**
     * 检索命名对象, 以下链接，除了名称的终端原子组件.
     * 
     * @param name 要查找的对象的名称
     * @return 绑定到名称的对象, 不遵循终端链接
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public Object lookupLink(String name)
        throws NamingException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("selectorContext.methodUsingString",
                    "lookupLink", name));
        }

        return getBoundContext().lookupLink(parseName(name));
    }


    /**
     * 检索与命名上下文关联的解析器. 在名称空间联合中, 不同的命名系统将解析的名称不同.
     * 此方法允许应用程序获得一个解析器，使用特定命名系统的命名约定将名称解析为它们的原子组件. 在任何单一命名系统中, 
     * 此方法返回的NameParser对象必须相等(使用equals() 比较).
     * 
     * @param name 获取解析器的上下文的名称
     * @return 可以将复合名称解析为原子组件的名称解析器
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public NameParser getNameParser(Name name)
        throws NamingException {
        return getBoundContext().getNameParser(parseName(name));
    }


    /**
     * 检索与命名上下文关联的解析器
     * 
     * @param name 获取解析器的上下文的名称
     * @return 可以将复合名称解析为原子组件的名称解析器
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public NameParser getNameParser(String name)
        throws NamingException {
        return getBoundContext().getNameParser(parseName(name));
    }


    /**
     * 用与此上下文相关的名称组成此上下文的名称.
     * <p>
     * 给出与此上下文相关的名称(name), 这个上下文的名称（前缀）相对于它的祖先之一, 此方法使用与命名系统相关的语法来返回两个名称的联合.
     * 也就是说, 如果名称命名一个与此上下文相关的对象, 结果是相同对象的名称, 但相对于祖先的上下文. 名称不能为 null.
     * 
     * @param name 与此上下文相关的名称
     * @param prefix 此上下文相对于其祖先之一的名称
     * @return 前缀和名称的构成
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public Name composeName(Name name, Name prefix)
        throws NamingException {
        Name prefixClone = (Name) prefix.clone();
        return prefixClone.addAll(name);
    }


    /**
     * 用与此上下文相关的名称组成此上下文的名称.
     * 
     * @param name 与此上下文相关的名称
     * @param prefix 此上下文相对于其祖先之一的名称
     * @return 前缀和名称的构成
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public String composeName(String name, String prefix)
        throws NamingException {
        return prefix + "/" + name;
    }


    /**
     * 将新的环境属性添加到此上下文的环境中.
     * 如果属性已经存在, 它的值被覆盖.
     * 
     * @param propName 要添加的环境属性的名称; 不能是null
     * @param propVal 要添加的属性的值; 不能是 null
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public Object addToEnvironment(String propName, Object propVal)
        throws NamingException {
        return getBoundContext().addToEnvironment(propName, propVal);
    }


    /**
     * 从此上下文环境中移除环境属性. 
     * 
     * @param propName 要删除的环境属性的名称;不能是null
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public Object removeFromEnvironment(String propName)
        throws NamingException {
        return getBoundContext().removeFromEnvironment(propName);
    }


    /**
     * 检索此上下文中有效的环境. 有关环境属性的详细信息，请参见类描述. 
     * 调用者不应对返回的对象做任何更改: 它们对上下文的影响是未定义的. 此上下文的环境可以使用 addToEnvironment() 和 removeFromEnvironment().
     * 
     * @return 这个上下文的环境; never null
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public Hashtable<?,?> getEnvironment()
        throws NamingException {
        return getBoundContext().getEnvironment();
    }


    /**
     * 关闭这个上下文. 此方法立即释放此上下文的资源, 而不是等待垃圾收集器自动释放它们.
     * 此方法是幂等的: 在已经关闭的上下文中调用它没有效果. 不允许在封闭上下文中调用任何其他方法, 并导致未定义的行为.
     * 
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public void close()
        throws NamingException {
        getBoundContext().close();
    }


    /**
     * 在自己的命名空间中检索此上下文的全名
     * <p>
     * 许多命名服务都有一个 "full name"对于各自名称空间中的对象.
     * 例如, LDAP条目有一个专有名称, DNS记录具有完全限定名. 此方法允许客户端应用程序检索此名称. 
     * 此方法返回的字符串不是一个JNDI复合名称, 而且不应直接传递到上下文的方法. 在命名系统中，全名这个概念没有意义, 
     * OperationNotSupportedException 将被抛出.
     * 
     * @return 在自己的命名空间中的此上下文的名称; never null
     * @exception OperationNotSupportedException 如果命名系统没有全名的概念
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public String getNameInNamespace()
        throws NamingException {
        return prefix;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 获取绑定的上下文.
     * @return 当前线程或当前类加载器绑定的 Context
     * @throws NamingException Bindings exception
     */
    protected Context getBoundContext()
        throws NamingException {

        if (initialContext) {
            String ICName = IC_PREFIX;
            if (ContextBindings.isThreadBound()) {
                ICName += ContextBindings.getThreadName();
            } else if (ContextBindings.isClassLoaderBound()) {
                ICName += ContextBindings.getClassLoaderName();
            }
            Context initialContext = ContextBindings.getContext(ICName);
            if (initialContext == null) {
                // 分配一个新的上下文并将其绑定到适当的名称
                initialContext = new NamingContext(env, ICName);
                ContextBindings.bindContext(ICName, initialContext);
            }
            return initialContext;
        } else {
            if (ContextBindings.isThreadBound()) {
                return ContextBindings.getThread();
            } else {
                return ContextBindings.getClassLoader();
            }
        }
    }


    /**
     * 删除URL header.
     * 
     * @param name The name
     * 
     * @return 解析的名字
     * @exception NamingException 如果没有"java:" header或如果没有命名上下文已经绑定到该线程
     */
    protected String parseName(String name) throws NamingException {

        if ((!initialContext) && (name.startsWith(prefix))) {
            return (name.substring(prefixLength));
        } else {
            if (initialContext) {
                return (name);
            } else {
                throw new NamingException
                    (sm.getString("selectorContext.noJavaUrl"));
            }
        }
    }


    /**
     * 删除URL header.
     * 
     * @param name The name
     * 
     * @return 解析的名字
     * @exception NamingException 如果没有"java:" header或如果没有命名上下文已经绑定到该线程
     */
    protected Name parseName(Name name) throws NamingException {

        if (!initialContext && !name.isEmpty() &&
                name.get(0).startsWith(prefix)) {
            if (name.get(0).equals(prefix)) {
                return name.getSuffix(1);
            } else {
                Name result = name.getSuffix(1);
                result.add(0, name.get(0).substring(prefixLength));
                return result;
            }
        } else {
            if (initialContext) {
                return name;
            } else {
                throw new NamingException(
                        sm.getString("selectorContext.noJavaUrl"));
            }
        }
    }
}
