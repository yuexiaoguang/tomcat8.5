package org.apache.tomcat.util.digester;

import org.xml.sax.Attributes;

/**
 * <p>保存参数以供周围<code>CallMethodRule</code>使用的Rule实现.</p>
 *
 * <p>这个参数可能是:</p>
 * <ul>
 * <li>来自当前元素的属性
 * See {@link #CallParamRule(int paramIndex, String attributeName)}
 * <li>来自当前的元素主体
 * See {@link #CallParamRule(int paramIndex)}
 * </ul>
 */
public class CallParamRule extends Rule {

    // ----------------------------------------------------------- Constructors

    /**
     * 将保存此元素的正文文本作为参数值.
     *
     * @param paramIndex 零相对参数编号
     */
    public CallParamRule(int paramIndex) {
        this(paramIndex, null);
    }


    /**
     * 将保存指定属性的值作为参数值.
     *
     * @param paramIndex 零相对参数编号
     * @param attributeName 要保存的属性的名称
     */
    public CallParamRule(int paramIndex,
                         String attributeName) {
        this(attributeName, paramIndex, 0, false);
    }


    private CallParamRule(String attributeName, int paramIndex, int stackIndex,
            boolean fromStack) {
        this.attributeName = attributeName;
        this.paramIndex = paramIndex;
        this.stackIndex = stackIndex;
        this.fromStack = fromStack;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 用于保存参数值的属性
     */
    protected final String attributeName;


    /**
     * 正在保存的参数的零相对索引.
     */
    protected final int paramIndex;


    /**
     * 是否从堆栈中设置参数?
     */
    protected final boolean fromStack;

    /**
     * 对象从堆栈顶部的位置
     */
    protected final int stackIndex;

    /**
     * Stack用于允许处理嵌套的正文文本.
     * 延迟创建.
     */
    protected ArrayStack<String> bodyTextStack;

    // --------------------------------------------------------- Public Methods


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

        Object param = null;

        if (attributeName != null) {

            param = attributes.getValue(attributeName);

        } else if(fromStack) {

            param = digester.peek(stackIndex);

            if (digester.log.isDebugEnabled()) {

                StringBuilder sb = new StringBuilder("[CallParamRule]{");
                sb.append(digester.match);
                sb.append("} Save from stack; from stack?").append(fromStack);
                sb.append("; object=").append(param);
                digester.log.debug(sb.toString());
            }
        }

        // 必须在这里将param对象保存到param堆栈帧.
        // 不能等到 end(). 否则, 对象将丢失.
        // 无法将对象保存为实例变量, 因为如果在后续嵌套中重用此CallParamRule, 实例变量将被覆盖.

        if(param != null) {
            Object parameters[] = (Object[]) digester.peekParams();
            parameters[paramIndex] = param;
        }
    }


    /**
     * 处理此元素的正文.
     *
     * @param namespace 匹配元素的命名空间URI; 如果解析器不支持命名空间或元素没有命名空间, 则为空字符串
     * @param name 如果解析器是命名空间感知的本地名称, 或者只是元素名称
     * @param bodyText 这个元素的正文主体
     */
    @Override
    public void body(String namespace, String name, String bodyText)
            throws Exception {

        if (attributeName == null && !fromStack) {
            // 必须等待设置参数直到结束，这样我们才能确保正确的参数集位于堆栈的顶部
            if (bodyTextStack == null) {
                bodyTextStack = new ArrayStack<>();
            }
            bodyTextStack.push(bodyText.trim());
        }

    }

    /**
     * 现在处理正文主体.
     */
    @Override
    public void end(String namespace, String name) {
        if (bodyTextStack != null && !bodyTextStack.empty()) {
            // 现在所做的是将一个参数推到顶部参数集上
            Object parameters[] = (Object[]) digester.peekParams();
            parameters[paramIndex] = bodyTextStack.pop();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CallParamRule[");
        sb.append("paramIndex=");
        sb.append(paramIndex);
        sb.append(", attributeName=");
        sb.append(attributeName);
        sb.append(", from stack=");
        sb.append(fromStack);
        sb.append("]");
        return (sb.toString());
    }
}
