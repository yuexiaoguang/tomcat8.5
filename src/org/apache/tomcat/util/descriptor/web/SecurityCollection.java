package org.apache.tomcat.util.descriptor.web;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

import org.apache.tomcat.util.buf.UDecoder;


/**
 * 表示Web应用程序的安全性约束的Web资源集合, 作为部署描述符中<code>&lt;web-resource-collection&gt;</code>元素的表示.
 * <p>
 * <b>WARNING</b>:  假设仅在单个线程的上下文中创建和修改此类的实例, 在实例对应用程序的其余部分可见之前.
 * 之后，只能进行读访问. 因此, 此类中的读取和写入访问都不会同步.
 */
public class SecurityCollection extends XmlEncodingBase implements Serializable {

    private static final long serialVersionUID = 1L;

    // ----------------------------------------------------------- Constructors


    public SecurityCollection() {
        this(null, null);
    }


    /**
     * @param name 此安全集合的名称
     * @param description 此安全集合的描述
     */
    public SecurityCollection(String name, String description) {
        super();
        setName(name);
        setDescription(description);
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 此Web资源集合的描述.
     */
    private String description = null;


    /**
     * 此Web资源集合明确涵盖的HTTP方法.
     */
    private String methods[] = new String[0];


    /**
     * 从此Web资源集合中明确排除的HTTP方法.
     */
    private String omittedMethods[] = new String[0];

    /**
     * 此Web资源集合的名称.
     */
    private String name = null;


    /**
     * 受此安全性集合保护的URL模式.
     */
    private String patterns[] = new String[0];


    /**
     * 此安全性集合由部署描述符建立.
     * 默认是 <code>true</code>.
     */
    private boolean isFromDescriptor = true;

    // ------------------------------------------------------------- Properties


    /**
     * @return 此Web资源集合的描述.
     */
    public String getDescription() {
        return (this.description);
    }


    /**
     * 设置此Web资源集合的描述.
     *
     * @param description 描述
     */
    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * @return 此Web资源集合的名称.
     */
    public String getName() {
        return (this.name);
    }


    /**
     * 设置此Web资源集合的名称
     *
     * @param name 名称
     */
    public void setName(String name) {
        this.name = name;
    }


    /**
     * @return 如果此约束是在部署描述符中定义的.
     */
    public boolean isFromDescriptor() {
        return isFromDescriptor;
    }


    /**
     * 设置是否在部署描述符中定义了此约束.
     * 
     * @param isFromDescriptor <code>true</code> 在描述符中声明
     */
    public void setFromDescriptor(boolean isFromDescriptor) {
        this.isFromDescriptor = isFromDescriptor;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 添加HTTP请求方法以明确地成为此Web资源集合的一部分.
     * 
     * @param method 方法
     */
    public void addMethod(String method) {

        if (method == null)
            return;
        String results[] = new String[methods.length + 1];
        for (int i = 0; i < methods.length; i++)
            results[i] = methods[i];
        results[methods.length] = method;
        methods = results;
    }


    /**
     * 将HTTP请求方法添加到从此Web资源集合中明确排除的方法.
     * 
     * @param method 方法
     */
    public void addOmittedMethod(String method) {
        if (method == null)
            return;
        String results[] = new String[omittedMethods.length + 1];
        for (int i = 0; i < omittedMethods.length; i++)
            results[i] = omittedMethods[i];
        results[omittedMethods.length] = method;
        omittedMethods = results;
    }

    /**
     * 添加URL模式以成为此Web资源集合的一部分.
     * 
     * @param pattern 模式
     */
    public void addPattern(String pattern) {
        addPatternDecoded(UDecoder.URLDecode(pattern, StandardCharsets.UTF_8));
    }
    public void addPatternDecoded(String pattern) {

        if (pattern == null)
            return;

        String decodedPattern = UDecoder.URLDecode(pattern);
        String results[] = new String[patterns.length + 1];
        for (int i = 0; i < patterns.length; i++) {
            results[i] = patterns[i];
        }
        results[patterns.length] = decodedPattern;
        patterns = results;
    }


    /**
     * 检查集合是否适用于指定的方法.
     * 
     * @param method 要求检查的方法
     * 
     * @return <code>true</code>如果指定的HTTP请求方法是此Web资源集合的一部分.
     */
    public boolean findMethod(String method) {

        if (methods.length == 0 && omittedMethods.length == 0)
            return true;
        if (methods.length > 0) {
            for (int i = 0; i < methods.length; i++) {
                if (methods[i].equals(method))
                    return true;
            }
            return false;
        }
        if (omittedMethods.length > 0) {
            for (int i = 0; i < omittedMethods.length; i++) {
                if (omittedMethods[i].equals(method))
                    return false;
            }
        }
        return true;
    }


    /**
     * @return 作为此Web资源集合一部分的HTTP请求方法集; 如果没有明确包含任何方法，则为零长度数组.
     */
    public String[] findMethods() {
        return (methods);
    }


    /**
     * @return 从此Web资源集合中明确排除的HTTP请求方法集; 如果没有排除请求方法，则为零长度数组.
     */
    public String[] findOmittedMethods() {
        return (omittedMethods);
    }


    /**
     * 指定的模式是否是此Web资源集合的一部分?
     *
     * @param pattern 要比较的模式
     * @return <code>true</code>如果模式是集合的一部分
     */
    public boolean findPattern(String pattern) {

        for (int i = 0; i < patterns.length; i++) {
            if (patterns[i].equals(pattern))
                return true;
        }
        return false;
    }


    /**
     * @return 作为此Web资源集合一部分的URL模式集. 如果没有指定, 返回零长度数组.
     */
    public String[] findPatterns() {
        return (patterns);
    }


    /**
     * 从属于此Web资源集合的方法中删除指定的HTTP请求方法.
     *
     * @param method 要删除的请求方法
     */
    public void removeMethod(String method) {

        if (method == null)
            return;
        int n = -1;
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].equals(method)) {
                n = i;
                break;
            }
        }
        if (n >= 0) {
            int j = 0;
            String results[] = new String[methods.length - 1];
            for (int i = 0; i < methods.length; i++) {
                if (i != n)
                    results[j++] = methods[i];
            }
            methods = results;
        }
    }


    /**
     * 从此Web资源集合中显式排除的方法中删除指定的HTTP请求方法.
     *
     * @param method 要删除的请求方法
     */
    public void removeOmittedMethod(String method) {

        if (method == null)
            return;
        int n = -1;
        for (int i = 0; i < omittedMethods.length; i++) {
            if (omittedMethods[i].equals(method)) {
                n = i;
                break;
            }
        }
        if (n >= 0) {
            int j = 0;
            String results[] = new String[omittedMethods.length - 1];
            for (int i = 0; i < omittedMethods.length; i++) {
                if (i != n)
                    results[j++] = omittedMethods[i];
            }
            omittedMethods = results;
        }
    }


    /**
     * 从属于此Web资源集合的URL模式中删除指定的URL模式.
     *
     * @param pattern 要删除的模式
     */
    public void removePattern(String pattern) {

        if (pattern == null)
            return;
        int n = -1;
        for (int i = 0; i < patterns.length; i++) {
            if (patterns[i].equals(pattern)) {
                n = i;
                break;
            }
        }
        if (n >= 0) {
            int j = 0;
            String results[] = new String[patterns.length - 1];
            for (int i = 0; i < patterns.length; i++) {
                if (i != n)
                    results[j++] = patterns[i];
            }
            patterns = results;
        }
    }


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("SecurityCollection[");
        sb.append(name);
        if (description != null) {
            sb.append(", ");
            sb.append(description);
        }
        sb.append("]");
        return (sb.toString());
    }
}
