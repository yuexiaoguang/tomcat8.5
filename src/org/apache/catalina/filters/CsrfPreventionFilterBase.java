package org.apache.catalina.filters;

import java.security.SecureRandom;
import java.util.Random;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public abstract class CsrfPreventionFilterBase extends FilterBase {

    private static final Log log = LogFactory.getLog(CsrfPreventionFilterBase.class);

    private String randomClass = SecureRandom.class.getName();

    private Random randomSource;

    private int denyStatus = HttpServletResponse.SC_FORBIDDEN;

    @Override
    protected Log getLogger() {
        return log;
    }

    /**
     * @return 拒绝请求的响应状态码.
     */
    public int getDenyStatus() {
        return denyStatus;
    }

    /**
     * 设置拒绝请求的响应状态码. 如果未设置, 默认值为 403.
     *
     * @param denyStatus HTTP状态码
     */
    public void setDenyStatus(int denyStatus) {
        this.denyStatus = denyStatus;
    }

    /**
     * 指定要使用的类来生成nonces. 必须是{@link Random}实例.
     *
     * @param randomClass 要使用的类的名称
     */
    public void setRandomClass(String randomClass) {
        this.randomClass = randomClass;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Set the parameters
        super.init(filterConfig);

        try {
            Class<?> clazz = Class.forName(randomClass);
            randomSource = (Random) clazz.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            ServletException se = new ServletException(sm.getString(
                    "csrfPrevention.invalidRandomClass", randomClass), e);
            throw se;
        }
    }

    @Override
    protected boolean isConfigProblemFatal() {
        return true;
    }

    /**
     * 生成用于验证后续请求的一次性令牌. 随机数生成是一个简化的版本
     * ManagerBase.generateSessionId().
     *
     * @return the generated nonce
     */
    protected String generateNonce() {
        byte random[] = new byte[16];

        // Render the result as a String of hexadecimal digits
        StringBuilder buffer = new StringBuilder();

        randomSource.nextBytes(random);

        for (int j = 0; j < random.length; j++) {
            byte b1 = (byte) ((random[j] & 0xf0) >> 4);
            byte b2 = (byte) (random[j] & 0x0f);
            if (b1 < 10) {
                buffer.append((char) ('0' + b1));
            } else {
                buffer.append((char) ('A' + (b1 - 10)));
            }
            if (b2 < 10) {
                buffer.append((char) ('0' + b2));
            } else {
                buffer.append((char) ('A' + (b2 - 10)));
            }
        }

        return buffer.toString();
    }

    protected String getRequestedPath(HttpServletRequest request) {
        String path = request.getServletPath();
        if (request.getPathInfo() != null) {
            path = path + request.getPathInfo();
        }
        return path;
    }
}
