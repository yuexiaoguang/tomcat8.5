package org.apache.catalina.manager;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;

import javax.management.Attribute;
import javax.management.MBeanException;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.mbeans.MBeanDumper;
import org.apache.tomcat.util.modeler.Registry;

/**
 * 这个servlet 以简单的格式将卸载 JMX属性, 为建模者实现代理服务.
 */
public class JMXProxyServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // 当调用JMX操作时没有任何参数.
    private static final String[] NO_PARAMETERS = new String[0];

    // ----------------------------------------------------- Instance Variables
    /**
     * MBean server.
     */
    protected transient MBeanServer mBeanServer = null;
    protected transient Registry registry;


    // --------------------------------------------------------- Public Methods
    /**
     * 初始化这个servlet.
     */
    @Override
    public void init() throws ServletException {
        // 检索 MBean 服务器
        registry = Registry.getRegistry(null, null);
        mBeanServer = Registry.getRegistry(null, null).getMBeanServer();
    }


    /**
     * 处理指定资源的 GET请求.
     *
     * @param request 正在处理的servlet请求
     * @param response 正在创建的servlet响应
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果servlet指定的错误发生
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        response.setContentType("text/plain");

        PrintWriter writer = response.getWriter();

        if (mBeanServer == null) {
            writer.println("Error - No mbean server");
            return;
        }

        String qry = request.getParameter("set");
        if (qry != null) {
            String name = request.getParameter("att");
            String val = request.getParameter("val");

            setAttribute(writer, qry, name, val);
            return;
        }
        qry = request.getParameter("get");
        if (qry != null) {
            String name = request.getParameter("att");
            getAttribute(writer, qry, name, request.getParameter("key"));
            return;
        }
        qry = request.getParameter("invoke");
        if (qry != null) {
            String opName = request.getParameter("op");
            String[] params = getInvokeParameters(request.getParameter("ps"));
            invokeOperation(writer, qry, opName, params);
            return;
        }
        qry = request.getParameter("qry");
        if (qry == null) {
            qry = "*:*";
        }

        listBeans(writer, qry);
    }


    public void getAttribute(PrintWriter writer, String onameStr, String att, String key) {
        try {
            ObjectName oname = new ObjectName(onameStr);
            Object value = mBeanServer.getAttribute(oname, att);

            if (null != key && value instanceof CompositeData)
                value = ((CompositeData) value).get(key);

            String valueStr;
            if (value != null) {
                valueStr = value.toString();
            } else {
                valueStr = "<null>";
            }

            writer.print("OK - Attribute get '");
            writer.print(onameStr);
            writer.print("' - ");
            writer.print(att);

            if (null != key) {
                writer.print(" - key '");
                writer.print(key);
                writer.print("'");
            }

            writer.print(" = ");

            writer.println(MBeanDumper.escape(valueStr));
        } catch (Exception ex) {
            writer.println("Error - " + ex.toString());
            ex.printStackTrace(writer);
        }
    }


    public void setAttribute(PrintWriter writer, String onameStr, String att, String val) {
        try {
            setAttributeInternal(onameStr, att, val);
            writer.println("OK - Attribute set");
        } catch (Exception ex) {
            writer.println("Error - " + ex.toString());
            ex.printStackTrace(writer);
        }
    }


    public void listBeans(PrintWriter writer, String qry) {

        Set<ObjectName> names = null;
        try {
            names = mBeanServer.queryNames(new ObjectName(qry), null);
            writer.println("OK - Number of results: " + names.size());
            writer.println();
        } catch (Exception ex) {
            writer.println("Error - " + ex.toString());
            ex.printStackTrace(writer);
            return;
        }

        String dump = MBeanDumper.dumpBeans(mBeanServer, names);
        writer.print(dump);
    }


    /**
     * 确定是否支持指定的类型.
     *
     * @param type 要检查的类型
     * @return 总是返回<code>true</code>
     */
    public boolean isSupported(String type) {
        return true;
    }


    private void invokeOperation(PrintWriter writer, String onameStr, String op,
            String[] valuesStr) {
        try {
            Object retVal = invokeOperationInternal(onameStr, op, valuesStr);
            if (retVal != null) {
                writer.println("OK - Operation " + op + " returned:");
                output("", writer, retVal);
            } else {
                writer.println("OK - Operation " + op + " without return value");
            }
        } catch (Exception ex) {
            writer.println("Error - " + ex.toString());
            ex.printStackTrace(writer);
        }
    }


    /**
     * 从参数字符串解析参数值.
     *
     * @param paramString 包含逗号分隔的参数的字符串, 或者code>null</code>
     * 
     * @return 参数数组 (如果<code>paramString</code>是<code>null</code>，则是空数组).
     */
    private String[] getInvokeParameters(String paramString) {
        if (paramString == null)
            return NO_PARAMETERS;
        else
            return paramString.split(",");
    }


    /**
     * 设置MBean属性值.
     */
    private void setAttributeInternal(String onameStr, String attributeName, String value)
            throws OperationsException, MBeanException, ReflectionException {
        ObjectName oname = new ObjectName(onameStr);
        String type = registry.getType(oname, attributeName);
        Object valueObj = registry.convertValue(type, value);
        mBeanServer.setAttribute(oname, new Attribute(attributeName, valueObj));
    }


    /**
     * 执行MBean上的操作.
     *
     * @param onameStr MBean名称.
     * @param operation 要调用的操作的名称.
     * @param parameters 包含操作参数的字符串数组. 它们将被转换为适当类型以调用请求的操作.
     *            
     * @return 请求操作返回的值.
     */
    private Object invokeOperationInternal(String onameStr, String operation, String[] parameters)
            throws OperationsException, MBeanException, ReflectionException {
        ObjectName oname = new ObjectName(onameStr);
        MBeanOperationInfo methodInfo = registry.getMethodInfo(oname, operation);
        MBeanParameterInfo[] signature = methodInfo.getSignature();
        String[] signatureTypes = new String[signature.length];
        Object[] values = new Object[signature.length];
        for (int i = 0; i < signature.length; i++) {
            MBeanParameterInfo pi = signature[i];
            signatureTypes[i] = pi.getType();
            values[i] = registry.convertValue(pi.getType(), parameters[i]);
        }

        return mBeanServer.invoke(oname, operation, values, signatureTypes);
    }


    private void output(String indent, PrintWriter writer, Object result) {
        if (result instanceof Object[]) {
            for (Object obj : (Object[]) result) {
                output("  " + indent, writer, obj);
            }
        } else {
            String strValue;
            if (result != null) {
                strValue = result.toString();
            } else {
                strValue = "<null>";
            }
            writer.println(indent + strValue);
        }
    }
}
