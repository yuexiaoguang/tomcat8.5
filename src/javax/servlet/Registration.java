package javax.servlet;

import java.util.Map;
import java.util.Set;

/**
 * 用于过滤器和servlet注册的通用接口.
 */
public interface Registration {

    public String getName();

    public String getClassName();

    /**
     * 添加一个初始化参数，如果还没有添加.
     *
     * @param name  初始化参数的名称
     * @param value 初始化参数的值
     * @return <code>true</code>如果初始化参数已经设置,
     *         <code>false</code>如果初始化参数没有被设置，因为一个初始化参数相同的名称已经存在
     * @throws IllegalArgumentException 如果名称或值是<code>null</code>
     * @throws IllegalStateException 如果关联的ServletContext已经被初始化
     */
    public boolean setInitParameter(String name, String value);

    /**
     * 获取一个初始化参数的值.
     *
     * @param name  初始化参数名称
     *
     * @return 指定的初始化参数的值
     */
    public String getInitParameter(String name);

    /**
     * 添加多个初始化参数. 如果提供的初始化参数与现有的初始化参数冲突, 不会执行任何更新.
     *
     * @param initParameters 要添加的初始化参数
     *
     * @return 和现有的初始化参数冲突的初始化参数名称列表. 如果没有冲突, 这个Set将是空的.
     * @throws IllegalArgumentException 如果提供的初始化参数有一个名称或值是null
     * @throws IllegalStateException 如果关联的ServletContext已经被初始化
     */
    public Set<String> setInitParameters(Map<String,String> initParameters);

    /**
     * 获取所有初始化参数的名称和值.
     */
    public Map<String, String> getInitParameters();

    public interface Dynamic extends Registration {

        /**
         * 标记这个Servlet/Filter是否支持异步处理.
         *
         * @param isAsyncSupported  这个Servlet/Filter是否支持异步处理
         *
         * @throws IllegalStateException 如果关联的ServletContext已经被初始化
         */
        public void setAsyncSupported(boolean isAsyncSupported);
    }
}
