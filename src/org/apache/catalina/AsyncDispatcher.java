package org.apache.catalina;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

public interface AsyncDispatcher {

    /**
     * 执行异步调度. 该方法不检查请求是否处于适当的状态; 调用者负责检查.
     * 
     * @param request  要传递给调度目标的请求对象
     * @param response 传递给调度目标的响应对象
     * 
     * @throws ServletException 如果由调度目标抛出
     * @throws IOException      如果在处理调度时发生I/O错误
     */
    public void dispatch(ServletRequest request, ServletResponse response)
            throws ServletException, IOException;
}
