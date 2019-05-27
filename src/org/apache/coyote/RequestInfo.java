package org.apache.coyote;

import javax.management.ObjectName;


/**
 * 保存 Request 和 Response 对象的结构. 它还保存关于请求处理的统计信息，并提供关于正在处理的请求的管理信息.
 *
 * 每个线程使用一个 Request/Response 对, 在每个请求上循环使用.
 * 此对象提供了收集全局低级别统计数据的地方 - 不必处理同步问题 (因为每个线程都有它自己的 RequestProcessorMX ).
 */
public class RequestInfo  {
    private RequestGroupInfo global=null;

    // ----------------------------------------------------------- Constructors

    public RequestInfo( Request req) {
        this.req=req;
    }

    public RequestGroupInfo getGlobalProcessor() {
        return global;
    }

    public void setGlobalProcessor(RequestGroupInfo global) {
        if( global != null) {
            this.global=global;
            global.addRequestProcessor( this );
        } else {
            if (this.global != null) {
                this.global.removeRequestProcessor( this );
                this.global = null;
            }
        }
    }


    // ----------------------------------------------------- Instance Variables
    private final Request req;
    private int stage = Constants.STAGE_NEW;
    private String workerThreadName;
    private ObjectName rpName;

    // -------------------- Information about the current request  -----------
    // 这对于只运行长时间的请求非常有用

    public String getMethod() {
        return req.method().toString();
    }

    public String getCurrentUri() {
        return req.requestURI().toString();
    }

    public String getCurrentQueryString() {
        return req.queryString().toString();
    }

    public String getProtocol() {
        return req.protocol().toString();
    }

    public String getVirtualHost() {
        return req.serverName().toString();
    }

    public int getServerPort() {
        return req.getServerPort();
    }

    public String getRemoteAddr() {
        req.action(ActionCode.REQ_HOST_ADDR_ATTRIBUTE, null);
        return req.remoteAddr().toString();
    }

    /**
     * 获取中间代理所报告的此连接的远程地址.
     *
     * @return 此连接的远程地址
     */
    public String getRemoteAddrForwarded() {
        String remoteAddrProxy = (String) req.getAttribute(Constants.REMOTE_ADDR_ATTRIBUTE);
        if (remoteAddrProxy == null) {
            return getRemoteAddr();
        }
        return remoteAddrProxy;
    }

    public int getContentLength() {
        return req.getContentLength();
    }

    public long getRequestBytesReceived() {
        return req.getBytesRead();
    }

    public long getRequestBytesSent() {
        return req.getResponse().getContentWritten();
    }

    public long getRequestProcessingTime() {
        // 不完美, 但足够避免因为并发更新返回奇怪的值.
        long startTime = req.getStartTime();
        if (getStage() == org.apache.coyote.Constants.STAGE_ENDED || startTime < 0) {
            return 0;
        } else {
            return System.currentTimeMillis() - startTime;
        }
    }

    // -------------------- Statistical data  --------------------
    // 在每个请求结束时收集.
    private long bytesSent;
    private long bytesReceived;

    // Total time = divide by requestCount to get average.
    private long processingTime;
    // 请求的最长响应时间
    private long maxTime;
    // 占用maxTime的请求的URI
    private String maxRequestUri;

    private int requestCount;
    // 响应代码 >= 400
    private int errorCount;

    // 最后请求的时间
    private long lastRequestProcessingTime = 0;


    /** 
     * 在调用请求之前由处理器调用. 它会收集统计信息.
     */
    void updateCounters() {
        bytesReceived+=req.getBytesRead();
        bytesSent+=req.getResponse().getContentWritten();

        requestCount++;
        if( req.getResponse().getStatus() >=400 )
            errorCount++;
        long t0=req.getStartTime();
        long t1=System.currentTimeMillis();
        long time=t1-t0;
        this.lastRequestProcessingTime = time;
        processingTime+=time;
        if( maxTime < time ) {
            maxTime=time;
            maxRequestUri=req.requestURI().toString();
        }
    }

    public int getStage() {
        return stage;
    }

    public void setStage(int stage) {
        this.stage = stage;
    }

    public long getBytesSent() {
        return bytesSent;
    }

    public void setBytesSent(long bytesSent) {
        this.bytesSent = bytesSent;
    }

    public long getBytesReceived() {
        return bytesReceived;
    }

    public void setBytesReceived(long bytesReceived) {
        this.bytesReceived = bytesReceived;
    }

    public long getProcessingTime() {
        return processingTime;
    }

    public void setProcessingTime(long processingTime) {
        this.processingTime = processingTime;
    }

    public long getMaxTime() {
        return maxTime;
    }

    public void setMaxTime(long maxTime) {
        this.maxTime = maxTime;
    }

    public String getMaxRequestUri() {
        return maxRequestUri;
    }

    public void setMaxRequestUri(String maxRequestUri) {
        this.maxRequestUri = maxRequestUri;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(int requestCount) {
        this.requestCount = requestCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public String getWorkerThreadName() {
        return workerThreadName;
    }

    public ObjectName getRpName() {
        return rpName;
    }

    public long getLastRequestProcessingTime() {
        return lastRequestProcessingTime;
    }

    public void setWorkerThreadName(String workerThreadName) {
        this.workerThreadName = workerThreadName;
    }

    public void setRpName(ObjectName rpName) {
        this.rpName = rpName;
    }

    public void setLastRequestProcessingTime(long lastRequestProcessingTime) {
        this.lastRequestProcessingTime = lastRequestProcessingTime;
    }
}
