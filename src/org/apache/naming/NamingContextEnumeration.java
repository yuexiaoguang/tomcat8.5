package org.apache.naming;

import java.util.Iterator;

import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

/**
 * 命名枚举实现.
 */
public class NamingContextEnumeration implements NamingEnumeration<NameClassPair> {

    public NamingContextEnumeration(Iterator<NamingEntry> entries) {
        iterator = entries;
    }


    /**
     * Underlying enumeration.
     */
    protected final Iterator<NamingEntry> iterator;


    // --------------------------------------------------------- Public Methods


    /**
     * 检索枚举中的下一个元素.
     */
    @Override
    public NameClassPair next()
        throws NamingException {
        return nextElement();
    }


    /**
     * 确定枚举中是否有更多元素.
     */
    @Override
    public boolean hasMore()
        throws NamingException {
        return iterator.hasNext();
    }


    /**
     * 关闭这个枚举.
     */
    @Override
    public void close()
        throws NamingException {
    }


    @Override
    public boolean hasMoreElements() {
        return iterator.hasNext();
    }


    @Override
    public NameClassPair nextElement() {
        NamingEntry entry = iterator.next();
        return new NameClassPair(entry.name, entry.value.getClass().getName());
    }
}
