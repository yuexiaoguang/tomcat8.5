package javax.servlet.http;

/**
 * HTTP升级过程与新协议之间的接口.
 */
public interface HttpUpgradeHandler {

    /**
     * 这个方法调用一次，调用{@link HttpServletRequest#upgrade(Class)}的请求/响应对已经完成处理，
     * 并传递给 {@link HttpUpgradeHandler}.
     *
     * @param connection    已升级的连接
     */
    void init(WebConnection connection);

    /**
     * 升级连接已关闭后调用此方法.
     */
    void destroy();
}
