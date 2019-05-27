package org.apache.jasper.runtime;

import javax.servlet.ServletConfig;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.Tag;

import org.apache.jasper.Constants;
import org.apache.tomcat.InstanceManager;

/**
 * 可以重用的标签处理程序池.
 */
public class TagHandlerPool {

    private Tag[] handlers;

    public static final String OPTION_TAGPOOL = "tagpoolClassName";
    public static final String OPTION_MAXSIZE = "tagpoolMaxSize";

    // 下一个可用标签处理程序的索引
    private int current;
    protected InstanceManager instanceManager = null;

    public static TagHandlerPool getTagHandlerPool(ServletConfig config) {
        TagHandlerPool result = null;

        String tpClassName = getOption(config, OPTION_TAGPOOL, null);
        if (tpClassName != null) {
            try {
                Class<?> c = Class.forName(tpClassName);
                result = (TagHandlerPool) c.getConstructor().newInstance();
            } catch (Exception e) {
                e.printStackTrace();
                result = null;
            }
        }
        if (result == null)
            result = new TagHandlerPool();
        result.init(config);

        return result;
    }

    protected void init(ServletConfig config) {
        int maxSize = -1;
        String maxSizeS = getOption(config, OPTION_MAXSIZE, null);
        if (maxSizeS != null) {
            try {
                maxSize = Integer.parseInt(maxSizeS);
            } catch (Exception ex) {
                maxSize = -1;
            }
        }
        if (maxSize < 0) {
            maxSize = Constants.MAX_POOL_SIZE;
        }
        this.handlers = new Tag[maxSize];
        this.current = -1;
        instanceManager = InstanceManagerFactory.getInstanceManager(config);
    }

    public TagHandlerPool() {
        // Nothing - jasper 生成的servlet调用其他构造器, 应该在 future + init 中使用.
    }

    /**
     * 从这个标签处理程序池中获取下一个可用的标签处理程序, 实例化一个如果这个标签处理池是空的.
     *
     * @param handlerClass 标签处理程序类
     *
     * @return 重用或新实例化的标签处理程序
     *
     * @throws JspException 如果无法实例化标签处理程序
     */
    public Tag get(Class<? extends Tag> handlerClass) throws JspException {
        Tag handler;
        synchronized (this) {
            if (current >= 0) {
                handler = handlers[current--];
                return handler;
            }
        }

        // 不同步块 - 不需要其他线程来等待我们, 为这个线程构建一个标签.
        try {
            if (Constants.USE_INSTANCE_MANAGER_FOR_TAGS) {
                return (Tag) instanceManager.newInstance(
                        handlerClass.getName(), handlerClass.getClassLoader());
            } else {
                Tag instance = handlerClass.getConstructor().newInstance();
                instanceManager.newInstance(instance);
                return instance;
            }
        } catch (Exception e) {
            Throwable t = ExceptionUtils.unwrapInvocationTargetException(e);
            ExceptionUtils.handleThrowable(t);
            throw new JspException(e.getMessage(), t);
        }
    }

    /**
     * 将给定的标签处理程序添加到这个标签处理程序池中, 除非这个标签处理程序池已经达到它的容量, 这种情况下标签处理程序的 release()方法被调用.
     *
     * @param handler 添加到这个标签处理程序池的标签处理程序
     */
    public void reuse(Tag handler) {
        synchronized (this) {
            if (current < (handlers.length - 1)) {
                handlers[++current] = handler;
                return;
            }
        }
        // 不需要其他线程等待释放
        JspRuntimeLibrary.releaseTag(handler, instanceManager);
    }

    /**
     *调用这个标签处理程序池的所有标签处理程序的 release()方法.
     */
    public synchronized void release() {
        for (int i = current; i >= 0; i--) {
            JspRuntimeLibrary.releaseTag(handlers[i], instanceManager);
        }
    }


    protected static String getOption(ServletConfig config, String name,
            String defaultV) {
        if (config == null)
            return defaultV;

        String value = config.getInitParameter(name);
        if (value != null)
            return value;
        if (config.getServletContext() == null)
            return defaultV;
        value = config.getServletContext().getInitParameter(name);
        if (value != null)
            return value;
        return defaultV;
    }
}
