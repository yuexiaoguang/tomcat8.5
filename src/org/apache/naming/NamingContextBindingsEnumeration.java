package org.apache.naming;

import java.util.Iterator;

import javax.naming.Binding;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

/**
 * 命名枚举实现.
 */
public class NamingContextBindingsEnumeration implements NamingEnumeration<Binding> {


    // ----------------------------------------------------------- Constructors


    public NamingContextBindingsEnumeration(Iterator<NamingEntry> entries,
            Context ctx) {
        iterator = entries;
        this.ctx = ctx;
    }

    // -------------------------------------------------------------- Variables


    /**
     * Underlying enumeration.
     */
    protected final Iterator<NamingEntry> iterator;


    /**
     * 生成枚举的上下文.
     */
    private final Context ctx;


    // --------------------------------------------------------- Public Methods


    /**
     * 检索枚举中的下一个元素.
     */
    @Override
    public Binding next()
        throws NamingException {
        return nextElementInternal();
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
    public Binding nextElement() {
        try {
            return nextElementInternal();
        } catch (NamingException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private Binding nextElementInternal() throws NamingException {
        NamingEntry entry = iterator.next();
        Object value;

        // If the entry is a reference, resolve it
        if (entry.type == NamingEntry.REFERENCE
                || entry.type == NamingEntry.LINK_REF) {
            try {
                value = ctx.lookup(new CompositeName(entry.name));
            } catch (NamingException e) {
                throw e;
            } catch (Exception e) {
                NamingException ne = new NamingException(e.getMessage());
                ne.initCause(e);
                throw ne;
            }
        } else {
            value = entry.value;
        }

        return new Binding(entry.name, value.getClass().getName(), value, true);
    }
}

