package org.apache.catalina.tribes.tipis;

import java.io.IOException;
import java.io.Serializable;

/**
 * 对于智能复制, 对象可以实现这个接口来复制差异<br>
 * 复制逻辑将按以下顺序调用方法:<br>
 * <code>
 * 1. if ( entry.isDirty() ) <br>
 *      try {
 * 2.     entry.lock();<br>
 * 3.     byte[] diff = entry.getDiff();<br>
 * 4.     entry.reset();<br>
 *      } finally {<br>
 * 5.     entry.unlock();<br>
 *      }<br>
 *    }<br>
 * </code>
 * <br>
 * <br>
 * 当数据反序列化时，按以下顺序调用逻辑<br>
 * <code>
 * 1. ReplicatedMapEntry entry = (ReplicatedMapEntry)objectIn.readObject();<br>
 * 2. if ( isBackup(entry)||isPrimary(entry) ) entry.setOwner(owner); <br>
 * </code>
 * <br>
 */
public interface ReplicatedMapEntry extends Serializable {

    /**
     * 自从上次复制以来, 对象是否已更改, 并且未处于锁定状态
     * @return boolean
     */
    public boolean isDirty();

    /**
     * 如果返回 true, map 将使用getDiff()提取差异. 否则它将序列化整个对象.
     * @return boolean
     */
    public boolean isDiffable();

    /**
     * 返回一个差异，并将脏map设置为false
     * @return 序列化的差异的数据
     * @throws IOException IO error serializing
     */
    public byte[] getDiff() throws IOException;


    /**
     * 应用一个差异到现有对象.
     * @param diff 序列化的差异数据
     * @param offset Array offset
     * @param length Array length
     * @throws IOException 反序列化IO 错误
     * @throws ClassNotFoundException 序列化错误
     */
    public void applyDiff(byte[] diff, int offset, int length) throws IOException, ClassNotFoundException;

    /**
     * 重置当前差异状态并重置脏标志
     */
    public void resetDiff();

    /**
     * 序列化过程中锁定
     */
    public void lock();

    /**
     * 序列化后解锁
     */
    public void unlock();

    /**
     * 在远程map上创建对象之后调用此方法. 在这个方法上, 对象可以为任何未初始化的数据初始化自身
     *
     * @param owner Object
     */
    public void setOwner(Object owner);

    /**
     * 用于精度检查, 序列化属性可以包含版本号. 随着数据的修改, 这个数目增加了.
     * 复制的 map 可以使用它来确保周期性的准确性.
     * 
     * @return long - 版本号或 -1, 如果数据没有版本控制
     */
    public long getVersion();

    /**
     * 强制一个确定的版本到一个复制的map条目<br>
     * @param version long
     */
    public void setVersion(long version);

    /**
     * @return 最后复制时间.
     */
    public long getLastTimeReplicated();

    /**
     * 设置最后复制时间.
     * @param lastTimeReplicated New timestamp
     */
    public void setLastTimeReplicated(long lastTimeReplicated);

    /**
     * 如果返回 true, 复制对象已被访问
     * @return boolean
     */
    public boolean isAccessReplicate();

    /**
     * 访问一个现有对象.
     */
    public void accessEntry();

}