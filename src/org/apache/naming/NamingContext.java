package org.apache.naming;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;

import javax.naming.Binding;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.LinkRef;
import javax.naming.Name;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NotContextException;
import javax.naming.OperationNotSupportedException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.directory.InvalidAttributesException;
import javax.naming.spi.NamingManager;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Catalina JNDI Context implementation.
 */
public class NamingContext implements Context {


    // -------------------------------------------------------------- Constants


    /**
     * 此上下文的名称分析器.
     */
    protected static final NameParser nameParser = new NameParserImpl();


    private static final Log log = LogFactory.getLog(NamingContext.class);


    // ----------------------------------------------------------- Constructors


    /**
     * 构建一个命名上下文.
     *
     * @param env 使用的环境
     * @param name 关联的Catalina Context的名称
     */
    public NamingContext(Hashtable<String,Object> env, String name) {
        this(env, name, new HashMap<String,NamingEntry>());
    }


    /**
     * 构建一个命名上下文.
     *
     * @param env 使用的环境
     * @param name 关联的Catalina Context的名称
     * @param bindings 命名上下文的初始绑定
     */
    public NamingContext(Hashtable<String,Object> env, String name,
            HashMap<String,NamingEntry> bindings) {

        this.env = new Hashtable<>();
        this.name = name;
        // Populating the environment hashtable
        if (env != null ) {
            Enumeration<String> envEntries = env.keys();
            while (envEntries.hasMoreElements()) {
                String entryName = envEntries.nextElement();
                addToEnvironment(entryName, env.get(entryName));
            }
        }
        this.bindings = bindings;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Environment.
     */
    protected final Hashtable<String,Object> env;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(NamingContext.class);


    /**
     * 这个上下文中的绑定.
     */
    protected final HashMap<String,NamingEntry> bindings;


    /**
     * 关联的Catalina Context的名称.
     */
    protected final String name;


    /**
     * 如果试图写入一个只读的上下文，是抛出异常，还是忽略这个请求.
     */
    private boolean exceptionOnFailedWrite = true;
    public boolean getExceptionOnFailedWrite() {
        return exceptionOnFailedWrite;
    }
    public void setExceptionOnFailedWrite(boolean exceptionOnFailedWrite) {
        this.exceptionOnFailedWrite = exceptionOnFailedWrite;
    }


    // -------------------------------------------------------- Context Methods

    /**
     * 检索命名对象. 如果名字是空的, 返回此上下文的新实例(它表示与此上下文相同的命名上下文, 但它的环境可以独立修改，可以并发访问).
     *
     * @param name 要查找的对象的名称
     * @return the object bound to name
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public Object lookup(Name name) throws NamingException {
        return lookup(name, true);
    }


    /**
     * 检索命名对象.
     *
     * @param name 要查找的对象的名称
     * @return the object bound to name
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public Object lookup(String name) throws NamingException {
        return lookup(new CompositeName(name), true);
    }


    /**
     * 将名称绑定到对象. 所有中间上下文和目标上下文必须存在(除了名称的最终原子组件以外的所有名称) 
     * 
     * @param name 绑定的名称; 可能是空
     * @param obj 要绑定的对象; 可能是null
     * @exception NameAlreadyBoundException 如果名称已绑定
     * @exception InvalidAttributesException 如果对象没有提供所有强制属性
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public void bind(Name name, Object obj)
        throws NamingException {
        bind(name, obj, false);
    }


    /**
     * 将名称绑定到对象
     * 
     * @param name 绑定的名称; 可能是空
     * @param obj 要绑定的对象; 可能是null
     * @exception NameAlreadyBoundException 如果名称已绑定
     * @exception InvalidAttributesException 如果对象没有提供所有强制属性
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public void bind(String name, Object obj)
        throws NamingException {
        bind(new CompositeName(name), obj);
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
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public void rebind(Name name, Object obj)
        throws NamingException {
        bind(name, obj, true);
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
        rebind(new CompositeName(name), obj);
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
    public void unbind(Name name) throws NamingException {

        if (!checkWritable()) {
            return;
        }

        while ((!name.isEmpty()) && (name.get(0).length() == 0))
            name = name.getSuffix(1);
        if (name.isEmpty())
            throw new NamingException
                (sm.getString("namingContext.invalidName"));

        NamingEntry entry = bindings.get(name.get(0));

        if (entry == null) {
            throw new NameNotFoundException
                (sm.getString("namingContext.nameNotBound", name, name.get(0)));
        }

        if (name.size() > 1) {
            if (entry.type == NamingEntry.CONTEXT) {
                ((Context) entry.value).unbind(name.getSuffix(1));
            } else {
                throw new NamingException
                    (sm.getString("namingContext.contextExpected"));
            }
        } else {
            bindings.remove(name.get(0));
        }

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
        unbind(new CompositeName(name));
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
        Object value = lookup(oldName);
        bind(newName, value);
        unbind(oldName);
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
        rename(new CompositeName(oldName), new CompositeName(newName));
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
        // Removing empty parts
        while ((!name.isEmpty()) && (name.get(0).length() == 0))
            name = name.getSuffix(1);
        if (name.isEmpty()) {
            return new NamingContextEnumeration(bindings.values().iterator());
        }

        NamingEntry entry = bindings.get(name.get(0));

        if (entry == null) {
            throw new NameNotFoundException
                (sm.getString("namingContext.nameNotBound", name, name.get(0)));
        }

        if (entry.type != NamingEntry.CONTEXT) {
            throw new NamingException
                (sm.getString("namingContext.contextExpected"));
        }
        return ((Context) entry.value).list(name.getSuffix(1));
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
        return list(new CompositeName(name));
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
        // Removing empty parts
        while ((!name.isEmpty()) && (name.get(0).length() == 0))
            name = name.getSuffix(1);
        if (name.isEmpty()) {
            return new NamingContextBindingsEnumeration(bindings.values().iterator(), this);
        }

        NamingEntry entry = bindings.get(name.get(0));

        if (entry == null) {
            throw new NameNotFoundException
                (sm.getString("namingContext.nameNotBound", name, name.get(0)));
        }

        if (entry.type != NamingEntry.CONTEXT) {
            throw new NamingException
                (sm.getString("namingContext.contextExpected"));
        }
        return ((Context) entry.value).listBindings(name.getSuffix(1));
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
        return listBindings(new CompositeName(name));
    }


    /**
     * 销毁指定的上下文并将其从命名空间中删除. 与名称相关联的任何属性也被移除. 中间上下文不会被销毁.
     * <p>
     * 此方法是幂等的. 即使在目标上下文中没有绑定终端原子名称，它也会成功, 但是会抛出NameNotFoundException, 如果任何中间上下文不存在. 
     * 
     * @param name 要销毁的上下文的名称; 不能是空
     * @exception NameNotFoundException 如果中间上下文不存在
     * @exception NotContextException 如果名称被绑定，但不命名上下文, 或者不命名适当类型的上下文
     */
    @Override
    public void destroySubcontext(Name name) throws NamingException {

        if (!checkWritable()) {
            return;
        }

        while ((!name.isEmpty()) && (name.get(0).length() == 0))
            name = name.getSuffix(1);
        if (name.isEmpty())
            throw new NamingException
                (sm.getString("namingContext.invalidName"));

        NamingEntry entry = bindings.get(name.get(0));

        if (entry == null) {
            throw new NameNotFoundException
                (sm.getString("namingContext.nameNotBound", name, name.get(0)));
        }

        if (name.size() > 1) {
            if (entry.type == NamingEntry.CONTEXT) {
                ((Context) entry.value).destroySubcontext(name.getSuffix(1));
            } else {
                throw new NamingException
                    (sm.getString("namingContext.contextExpected"));
            }
        } else {
            if (entry.type == NamingEntry.CONTEXT) {
                ((Context) entry.value).close();
                bindings.remove(name.get(0));
            } else {
                throw new NotContextException
                    (sm.getString("namingContext.contextExpected"));
            }
        }

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
        destroySubcontext(new CompositeName(name));
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
    public Context createSubcontext(Name name) throws NamingException {
        if (!checkWritable()) {
            return null;
        }

        NamingContext newContext = new NamingContext(env, this.name);
        bind(name, newContext);

        newContext.setExceptionOnFailedWrite(getExceptionOnFailedWrite());

        return newContext;
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
        return createSubcontext(new CompositeName(name));
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
        return lookup(name, false);
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
        return lookup(new CompositeName(name), false);
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

        while ((!name.isEmpty()) && (name.get(0).length() == 0))
            name = name.getSuffix(1);
        if (name.isEmpty())
            return nameParser;

        if (name.size() > 1) {
            Object obj = bindings.get(name.get(0));
            if (obj instanceof Context) {
                return ((Context) obj).getNameParser(name.getSuffix(1));
            } else {
                throw new NotContextException
                    (sm.getString("namingContext.contextExpected"));
            }
        }

        return nameParser;

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
        return getNameParser(new CompositeName(name));
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
    public Name composeName(Name name, Name prefix) throws NamingException {
        prefix = (Name) prefix.clone();
        return prefix.addAll(name);
    }


    /**
     *@param name 与此上下文相关的名称
     * @param prefix 此上下文相对于其祖先之一的名称
     * @return 前缀和名称的构成
     */
    @Override
    public String composeName(String name, String prefix) {
        return prefix + "/" + name;
    }


    /**
     * 将新的环境属性添加到此上下文的环境中.
     * 如果属性已经存在, 它的值被覆盖.
     * 
     * @param propName 要添加的环境属性的名称; 不能是null
     * @param propVal 要添加的属性的值; 不能是 null
     */
    @Override
    public Object addToEnvironment(String propName, Object propVal) {
        return env.put(propName, propVal);
    }


    /**
     * 从此上下文环境中移除环境属性. 
     * 
     * @param propName 要删除的环境属性的名称;不能是null
     */
    @Override
    public Object removeFromEnvironment(String propName){
        return env.remove(propName);
    }


    /**
     * 检索此上下文中有效的环境. 有关环境属性的详细信息，请参见类描述. 
     * 调用者不应对返回的对象做任何更改: 它们对上下文的影响是未定义的. 此上下文的环境可以使用 addToEnvironment() 和 removeFromEnvironment().
     * 
     * @return 这个上下文的环境; never null
     */
    @Override
    public Hashtable<?,?> getEnvironment() {
        return env;
    }


    /**
     * 关闭这个上下文. 此方法立即释放此上下文的资源, 而不是等待垃圾收集器自动释放它们.
     * 此方法是幂等的: 在已经关闭的上下文中调用它没有效果. 不允许在封闭上下文中调用任何其他方法, 并导致未定义的行为.
     *
     * @exception NamingException 如果遇到命名异常
     */
    @Override
    public void close() throws NamingException {
        if (!checkWritable()) {
            return;
        }
        env.clear();
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
        throw  new OperationNotSupportedException(sm.getString("namingContext.noAbsoluteName"));
        //FIXME ?
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 检索命名对象
     * 
     * @param name 要查找的对象的名称
     * @param resolveLinks 如果是true, 这些链接将得到解决
     * @return 绑定到名称的对象
     * @exception NamingException 如果遇到命名异常
     */
    protected Object lookup(Name name, boolean resolveLinks)
        throws NamingException {

        // 删除空的部分
        while ((!name.isEmpty()) && (name.get(0).length() == 0))
            name = name.getSuffix(1);
        if (name.isEmpty()) {
            // 如果名字是空的, 返回一个新分配的命名上下文
            return new NamingContext(env, this.name, bindings);
        }

        NamingEntry entry = bindings.get(name.get(0));

        if (entry == null) {
            throw new NameNotFoundException
                (sm.getString("namingContext.nameNotBound", name, name.get(0)));
        }

        if (name.size() > 1) {
            // 如果名称的大小大于 1, 然后我们通过一些子上下文.
            if (entry.type != NamingEntry.CONTEXT) {
                throw new NamingException
                    (sm.getString("namingContext.contextExpected"));
            }
            return ((Context) entry.value).lookup(name.getSuffix(1));
        } else {
            if ((resolveLinks) && (entry.type == NamingEntry.LINK_REF)) {
                String link = ((LinkRef) entry.value).getLinkName();
                if (link.startsWith(".")) {
                    // Link relative to this context
                    return lookup(link.substring(1));
                } else {
                    return (new InitialContext(env)).lookup(link);
                }
            } else if (entry.type == NamingEntry.REFERENCE) {
                try {
                    Object obj = NamingManager.getObjectInstance
                        (entry.value, name, this, env);
                    if(entry.value instanceof ResourceRef) {
                        boolean singleton = Boolean.parseBoolean(
                                    (String) ((ResourceRef) entry.value).get(
                                        "singleton").getContent());
                        if (singleton) {
                            entry.type = NamingEntry.ENTRY;
                            entry.value = obj;
                        }
                    }
                    return obj;
                } catch (NamingException e) {
                    throw e;
                } catch (Exception e) {
                    log.warn(sm.getString
                             ("namingContext.failResolvingReference"), e);
                    throw new NamingException(e.getMessage());
                }
            } else {
                return entry.value;
            }
        }

    }


    /**
     * 将名称绑定到对象. 所有中间上下文和目标上下文必须已经存在(除了名称的最终原子组件以外的所有名称) 
     * 
     * @param name 绑定名称; 不能是空
     * @param object 绑定对象; 可能是null
     * @param rebind 如果是true, 然后进行绑定 (覆盖)
     * @exception NameAlreadyBoundException 如果名称已绑定
     * @exception InvalidAttributesException 如果对象没有提供所有强制属性
     * @exception NamingException 如果遇到命名异常
     */
    protected void bind(Name name, Object obj, boolean rebind)
        throws NamingException {

        if (!checkWritable()) {
            return;
        }

        while ((!name.isEmpty()) && (name.get(0).length() == 0))
            name = name.getSuffix(1);
        if (name.isEmpty())
            throw new NamingException
                (sm.getString("namingContext.invalidName"));

        NamingEntry entry = bindings.get(name.get(0));

        if (name.size() > 1) {
            if (entry == null) {
                throw new NameNotFoundException(sm.getString(
                        "namingContext.nameNotBound", name, name.get(0)));
            }
            if (entry.type == NamingEntry.CONTEXT) {
                if (rebind) {
                    ((Context) entry.value).rebind(name.getSuffix(1), obj);
                } else {
                    ((Context) entry.value).bind(name.getSuffix(1), obj);
                }
            } else {
                throw new NamingException
                    (sm.getString("namingContext.contextExpected"));
            }
        } else {
            if ((!rebind) && (entry != null)) {
                throw new NameAlreadyBoundException
                    (sm.getString("namingContext.alreadyBound", name.get(0)));
            } else {
                // Getting the type of the object and wrapping it within a new
                // NamingEntry
                Object toBind =
                    NamingManager.getStateToBind(obj, name, this, env);
                if (toBind instanceof Context) {
                    entry = new NamingEntry(name.get(0), toBind,
                                            NamingEntry.CONTEXT);
                } else if (toBind instanceof LinkRef) {
                    entry = new NamingEntry(name.get(0), toBind,
                                            NamingEntry.LINK_REF);
                } else if (toBind instanceof Reference) {
                    entry = new NamingEntry(name.get(0), toBind,
                                            NamingEntry.REFERENCE);
                } else if (toBind instanceof Referenceable) {
                    toBind = ((Referenceable) toBind).getReference();
                    entry = new NamingEntry(name.get(0), toBind,
                                            NamingEntry.REFERENCE);
                } else {
                    entry = new NamingEntry(name.get(0), toBind,
                                            NamingEntry.ENTRY);
                }
                bindings.put(name.get(0), entry);
            }
        }

    }


    /**
     * @return <code>true</code> 如果上下文允许写.
     */
    protected boolean isWritable() {
        return ContextAccessController.isWritable(name);
    }


    /**
     * 抛出命名异常，如果上下文不可写
     * @return <code>true</code>如果Context可写
     * @throws NamingException 如果Context不可写, 而且<code>exceptionOnFailedWrite</code>是<code>true</code>
     */
    protected boolean checkWritable() throws NamingException {
        if (isWritable()) {
            return true;
        } else {
            if (exceptionOnFailedWrite) {
                throw new javax.naming.OperationNotSupportedException(
                        sm.getString("namingContext.readOnly"));
            }
        }
        return false;
    }
}

