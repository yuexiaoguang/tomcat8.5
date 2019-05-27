package org.apache.tomcat.util.digester;

import org.apache.tomcat.util.IntrospectionUtils;
import org.xml.sax.Attributes;

/**
 * <p>在堆栈上的对象上调用方法的 Rule 实现 (通常是顶级/父级对象), 传递从后续<code>CallParamRule</code>规则或从该元素的主体收集的参数. </p>
 */
public class CallMethodRule extends Rule {

    // ----------------------------------------------------------- Constructors

    /**
     * 参数类型默认是 java.lang.String.
     *
     * @param methodName 要调用的父方法的方法名称
     * @param paramCount 要收集的参数数量; 或为零, 来自该元素主体的单个参数.
     */
    public CallMethodRule(String methodName,
                          int paramCount) {
        this(0, methodName, paramCount);
    }

    /**
     * 参数类型默认是  java.lang.String.
     *
     * @param targetOffset 目标对象的位置. 正数相对于 digester 对象堆栈的顶部. 负数相对于堆栈的底部. 零意味着堆栈中的顶部对象.
     * @param methodName 要调用的父方法的方法名称
     * @param paramCount 要收集的参数数量; 或为零, 来自该元素主体的单个参数.
     */
    public CallMethodRule(int targetOffset,
                          String methodName,
                          int paramCount) {

        this.targetOffset = targetOffset;
        this.methodName = methodName;
        this.paramCount = paramCount;
        if (paramCount == 0) {
            this.paramTypes = new Class[] { String.class };
        } else {
            this.paramTypes = new Class[paramCount];
            for (int i = 0; i < this.paramTypes.length; i++) {
                this.paramTypes[i] = String.class;
            }
        }
        this.paramClassNames = null;
    }

    /**
     * 该方法不应接受任何参数.
     *
     * @param methodName 要调用的父方法的方法名称
     */
    public CallMethodRule(String methodName) {
        this(0, methodName, 0, (Class[]) null);
    }


    /**
     * 如果<code>paramCount</code>设置为零, 则规则将使用此元素的主体作为方法的单个参数, 除非<code>paramTypes</code>是 null 或空,
     * 在这种情况下, 规则将调用不带参数的指定方法.
     *
     * @param targetOffset 目标对象的位置. 正数相对于 digester 对象堆栈的顶部. 负数相对于堆栈的底部. 零意味着堆栈中的顶部对象.
     * @param methodName 要调用的父方法的方法名称
     * @param paramCount 要收集的参数数量; 或为零, 来自该元素主体的单个参数.
     * @param paramTypes 表示方法参数的参数类型的Java类
     *  (如果你想使用原始类型, 请改为相应的Java包装类, 如<code>java.lang.Boolean.TYPE</code>对应于<code>boolean</code> 参数)
     */
    public CallMethodRule(  int targetOffset,
                            String methodName,
                            int paramCount,
                            Class<?> paramTypes[]) {

        this.targetOffset = targetOffset;
        this.methodName = methodName;
        this.paramCount = paramCount;
        if (paramTypes == null) {
            this.paramTypes = new Class[paramCount];
            for (int i = 0; i < this.paramTypes.length; i++) {
                this.paramTypes[i] = String.class;
            }
        } else {
            this.paramTypes = new Class[paramTypes.length];
            for (int i = 0; i < this.paramTypes.length; i++) {
                this.paramTypes[i] = paramTypes[i];
            }
        }
        this.paramClassNames = null;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 从该元素收集的正文主体.
     */
    protected String bodyText = null;


    /**
     * 调用的目标对象的位置, 相对于 digester 对象堆栈的顶部.
     * 默认值零表示目标对象是堆栈顶部的对象.
     */
    protected final int targetOffset;

    /**
     * 要在父对象上调用的方法名称.
     */
    protected final String methodName;


    /**
     * 要从<code>MethodParam</code>规则中收集的参数数量.
     * 如果该值为零, 将从该元素的主体中收集单个参数.
     */
    protected final int paramCount;


    /**
     * 要收集的参数的参数类型.
     */
    protected Class<?> paramTypes[] = null;

    /**
     * 要收集的参数类的名称.
     * 此属性允许创建要延迟的类，直到设置了digester.
     *
     * @deprecated Unused. This will be removed in Tomcat 9.
     */
    @Deprecated
    protected final String paramClassNames[];

    /**
     * 是否应该将<code>MethodUtils.invokeExactMethod</code>用于反射.
     */
    protected boolean useExactMatch = false;

    // --------------------------------------------------------- Public Methods

    /**
     * 是否应该将<code>MethodUtils.invokeExactMethod</code>用于反射.
     * 
     * @return <code>true</code>是
     */
    public boolean getUseExactMatch() {
        return useExactMatch;
    }

    /**
     * 是否应该将<code>MethodUtils.invokeExactMethod</code>用于反射.
     * 
     * @param useExactMatch
     */
    public void setUseExactMatch(boolean useExactMatch) {
        this.useExactMatch = useExactMatch;
    }

    /**
     * 设置关联的 digester.
     * 如果需要，此类将从其名称加载参数类.
     */
    @Override
    public void setDigester(Digester digester)
    {
        // call superclass
        super.setDigester(digester);
        // if necessary, load parameter classes
        if (this.paramClassNames != null) {
            this.paramTypes = new Class[paramClassNames.length];
            for (int i = 0; i < this.paramClassNames.length; i++) {
                try {
                    this.paramTypes[i] =
                            digester.getClassLoader().loadClass(this.paramClassNames[i]);
                } catch (ClassNotFoundException e) {
                    // use the digester log
                    digester.getLogger().error("(CallMethodRule) Cannot load class " + this.paramClassNames[i], e);
                    this.paramTypes[i] = null; // Will cause NPE later
                }
            }
        }
    }

    /**
     * 处理此元素的开始.
     *
     * @param namespace 匹配元素的命名空间URI; 如果解析器不支持命名空间或元素没有命名空间, 则为空字符串
     * @param name 如果解析器是命名空间感知的本地名称, 或者只是元素名称
     * @param attributes 此元素的属性列表
     */
    @Override
    public void begin(String namespace, String name, Attributes attributes)
            throws Exception {

        // 推送数组以捕获参数值
        if (paramCount > 0) {
            Object parameters[] = new Object[paramCount];
            for (int i = 0; i < parameters.length; i++) {
                parameters[i] = null;
            }
            digester.pushParams(parameters);
        }

    }


    /**
     * 处理此元素的正文主体.
     *
     * @param namespace 匹配元素的命名空间URI; 如果解析器不支持命名空间或元素没有命名空间, 则为空字符串
     * @param name 如果解析器是命名空间感知的本地名称, 或者只是元素名称
     * @param bodyText 这个元素的正文主体
     */
    @Override
    public void body(String namespace, String name, String bodyText)
            throws Exception {

        if (paramCount == 0) {
            this.bodyText = bodyText.trim();
        }

    }


    /**
     * 处理此元素的结尾.
     *
     * @param namespace 匹配元素的命名空间URI; 如果解析器不支持命名空间或元素没有命名空间, 则为空字符串
     * @param name 如果解析器是命名空间感知的本地名称, 或者只是元素名称
     */
    @SuppressWarnings("null") // parameters can't trigger NPE
    @Override
    public void end(String namespace, String name) throws Exception {

        // 检索或构造参数值数组
        Object parameters[] = null;
        if (paramCount > 0) {

            parameters = (Object[]) digester.popParams();

            if (digester.log.isTraceEnabled()) {
                for (int i=0,size=parameters.length;i<size;i++) {
                    digester.log.trace("[CallMethodRule](" + i + ")" + parameters[i]) ;
                }
            }

            // 如果方法的参数取自属性，并且该属性实际上未在源XML文件中定义，则跳过方法调用
            if (paramCount == 1 && parameters[0] == null) {
                return;
            }

        } else if (paramTypes != null && paramTypes.length != 0) {

            // 如果方法的参数取自正文文本，但源XML文件中不包含正文文本，则跳过方法调用
            if (bodyText == null) {
                return;
            }

            parameters = new Object[1];
            parameters[0] = bodyText;
        }

        // 构造需要的参数值数组
        // 如果param值是String并且指定的paramType不是String, 只进行转换.
        Object paramValues[] = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            // 转换 null 值并转换非stringy param类型的stringy参数
            if(
                parameters[i] == null ||
                 (parameters[i] instanceof String &&
                   !String.class.isAssignableFrom(paramTypes[i]))) {

                paramValues[i] =
                        IntrospectionUtils.convert((String) parameters[i], paramTypes[i]);
            } else {
                paramValues[i] = parameters[i];
            }
        }

        // 确定方法调用的目标对象
        Object target;
        if (targetOffset >= 0) {
            target = digester.peek(targetOffset);
        } else {
            target = digester.peek( digester.getCount() + targetOffset );
        }

        if (target == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("[CallMethodRule]{");
            sb.append(digester.match);
            sb.append("} Call target is null (");
            sb.append("targetOffset=");
            sb.append(targetOffset);
            sb.append(",stackdepth=");
            sb.append(digester.getCount());
            sb.append(")");
            throw new org.xml.sax.SAXException(sb.toString());
        }

        // 在顶部对象上调用所需的方法
        if (digester.log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder("[CallMethodRule]{");
            sb.append(digester.match);
            sb.append("} Call ");
            sb.append(target.getClass().getName());
            sb.append(".");
            sb.append(methodName);
            sb.append("(");
            for (int i = 0; i < paramValues.length; i++) {
                if (i > 0) {
                    sb.append(",");
                }
                if (paramValues[i] == null) {
                    sb.append("null");
                } else {
                    sb.append(paramValues[i].toString());
                }
                sb.append("/");
                if (paramTypes[i] == null) {
                    sb.append("null");
                } else {
                    sb.append(paramTypes[i].getName());
                }
            }
            sb.append(")");
            digester.log.debug(sb.toString());
        }
        Object result = IntrospectionUtils.callMethodN(target, methodName,
                paramValues, paramTypes);
        processMethodCallResult(result);
    }


    /**
     * 解析完成后清理.
     */
    @Override
    public void finish() throws Exception {
        bodyText = null;
    }

    /**
     * 子类可以重写此方法, 以执行对调用方法的结果的附加处理.
     *
     * @param result 调用的方法返回的Object, 可能是 null
     */
    protected void processMethodCallResult(Object result) {
        // do nothing
    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("CallMethodRule[");
        sb.append("methodName=");
        sb.append(methodName);
        sb.append(", paramCount=");
        sb.append(paramCount);
        sb.append(", paramTypes={");
        if (paramTypes != null) {
            for (int i = 0; i < paramTypes.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(paramTypes[i].getName());
            }
        }
        sb.append("}");
        sb.append("]");
        return (sb.toString());
    }
}
