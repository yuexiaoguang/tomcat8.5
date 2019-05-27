package org.apache.catalina;


import java.beans.PropertyChangeListener;
import java.io.IOException;


/**
 * <b>Store</b>是一个Catalina组件的抽象, 提供持久存储和Session加载及其关联的用户数据的功能.
 * 实现类可以自由地保存和加载Session到任何媒体, 但是它假设保存的Session通过服务器或上下文重启持久化.
 */
public interface Store {

    // ------------------------------------------------------------- Properties

    public Manager getManager();


    public void setManager(Manager manager);


    /**
     * 返回当前Session的数量.
     *
     * @exception IOException 如果发生输入/输出错误
     */
    public int getSize() throws IOException;


    // --------------------------------------------------------- Public Methods


    /**
     * 添加一个属性修改监听器.
     *
     * @param listener 要监听的监听器
     */
    public void addPropertyChangeListener(PropertyChangeListener listener);


    /**
     * 返回包含当前存储在该Store中的所有会话的ID的数组。如果没有，则返回零长度数组。.
     *
     * @exception IOException 如果发生输入/输出错误
     */
    public String[] keys() throws IOException;


    /**
     *  从Store中加载并返回指定ID的Session, 不删除.如果没有, 返回<code>null</code>.
     *
     * @param id 要加载的会话的会话标识符
     *
     * @exception ClassNotFoundException 如果反序列化错误发生
     * @exception IOException 如果发生输入/输出错误
     * 
     * @return 加载的Session实例
     */
    public Session load(String id)
        throws ClassNotFoundException, IOException;


    /**
     * 移除指定的session. 如果没有, 不执行任何操作.
     *
     * @param id 要删除的会话的会话标识符
     *
     * @exception IOException 如果发生输入/输出错误
     */
    public void remove(String id) throws IOException;


    /**
     * 移除所有的session.
     *
     * @exception IOException 如果发生输入/输出错误
     */
    public void clear() throws IOException;


    /**
     * 移除属性监听器.
     *
     * @param listener 要移除的监听器
     */
    public void removePropertyChangeListener(PropertyChangeListener listener);


    /**
     * 保存指定的Session到Store. 替换先前保存的相同ID的session的信息.
     *
     * @param session 要保存的Session
     *
     * @exception IOException 如果发生输入/输出错误
     */
    public void save(Session session) throws IOException;


}
