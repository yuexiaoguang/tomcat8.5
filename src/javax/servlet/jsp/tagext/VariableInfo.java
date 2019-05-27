package javax.servlet.jsp.tagext;

/**
 * 有关由标记创建/修改的脚本变量的信息 (在运行时). 这个信息由TagExtraInfo类提供，而且它在JSP的翻译阶段使用.
 * <p>
 * AT_BEGIN, NESTED, AT_END范围的自定义的操作生成脚本变量.
 * <p>
 * 返回的对象中的类名(VariableInfo.getClassName)用于确定脚本变量的类型.
 * 注意，因为脚本变量从scoped属性分配它们的值，但是scoped属性不能是原始类型, &quot;boxed&quot; 必须使用封装类型<code>java.lang.Integer</code>等.
 * <p>
 * 类名可以是完全限定的类名或短类名.
 * <p>
 * 如果是完全限定的类名, 它应该是Web应用的CLASSPATH中的类 (查看Servlet 2.4规范 - 本质上它是WEB-INF/lib 和 WEB-INF/classes).
 * 如果不这样做，将导致翻译时错误.
 * <p>
 * 如果使用VariableInfo对象给定短类名, 那么类名称必须是在自定义操作出现的页面的导入指令上下文中的公共类的名称.
 * 类也必须在Web应用的CLASSPATH中. 如果不这样做，将导致翻译时错误.
 * <p>
 * <B>使用注释</B>
 * <p>
 * 通常，完全限定类名将引用标记库已知的类, 与标记处理程序在同一JAR文件中传递. 在剩下的大多数情况下，它将引用构建JSP处理器的平台中的类(like J2EE).
 * 以这种方式使用完全限定类名使用相对抵抗配置错误.
 * <p>
 * 短名称通常是基于来自自定义操作用户传递的一些属性的标记库生成的, 这样就不那么健壮了:
 * 例如，在引用JSP页面中一个丢失的导入指令将导致无效的短名称类和翻译错误.
 * <p>
 * <B>同步协议</B>
 * <p>
 * getVariableInfo执行的结果是一组VariableInfo对象. 每一个这样的对象通过提供它的名称、类型、变量是否是新的、它的作用域是什么来描述脚本变量.
 * 作用域最好通过图片描述:
 * <p>
 * <IMG src="doc-files/VariableInfo-1.gif" alt="NESTED, AT_BEGIN and AT_END Variable Scopes">
 * <p>
 * JSP 2.0规范定义了对3个值的解释:
 * <ul>
 * <li>NESTED,如果脚本变量在开始标记和定义它的操作的结束标记之间可用.
 * <li>AT_BEGIN, 如果脚本变量从定义它的动作的开始标记到作用域的结束中可用.
 * <li>AT_END, 如果脚本变量在定义了它的动作的结束标记之后，直到作用域结束之后可用.
 * </ul>
 * 变量的作用域值意味着什么方法可能影响它的值，因此需要同步，如下表所示.
 * <b>Note:</b> 变量的同步将在调用了各自的方法后发生. <blockquote>
 * <table cellpadding="2" cellspacing="2" border="0" width="55%"
 *        style="background-color:#999999" summary="Variable Synchronization Points">
 * <tbody>
 * <tr align="center">
 * <td valign="top" colspan="6" style="background-color:#999999">
 *   <u><b>Variable Synchronization Points</b></u><br>
 * </td>
 * </tr>
 * <tr>
 * <th valign="top" style="background-color:#c0c0c0">&nbsp;</th>
 * <th valign="top" style="background-color:#c0c0c0" align="center">doStartTag()</th>
 * <th valign="top" style="background-color:#c0c0c0" align="center">doInitBody()</th>
 * <th valign="top" style="background-color:#c0c0c0" align="center">doAfterBody()</th>
 * <th valign="top" style="background-color:#c0c0c0" align="center">doEndTag()</th>
 * <th valign="top" style="background-color:#c0c0c0" align="center">doTag()</th>
 * </tr>
 * <tr>
 * <td valign="top" style="background-color:#c0c0c0"><b>Tag<br>
 * </b></td>
 * <td valign="top" align="center" style="background-color:#ffffff">AT_BEGIN, NESTED<br>
 * </td>
 * <td valign="top" align="center" style="background-color:#ffffff"><br>
 * </td>
 * <td valign="top" align="center" style="background-color:#ffffff"><br>
 * </td>
 * <td valign="top" align="center" style="background-color:#ffffff">AT_BEGIN, AT_END<br>
 * </td>
 * <td valign="top" align="center" style="background-color:#ffffff"><br>
 * </td>
 * </tr>
 * <tr>
 * <td valign="top" style="background-color:#c0c0c0"><b>IterationTag<br>
 * </b></td>
 * <td valign="top" align="center" style="background-color:#ffffff">AT_BEGIN, NESTED<br>
 * </td>
 * <td valign="top" align="center" style="background-color:#ffffff"><br>
 * </td>
 * <td valign="top" align="center" style="background-color:#ffffff">AT_BEGIN, NESTED<br>
 * </td>
 * <td valign="top" align="center" style="background-color:#ffffff">AT_BEGIN, AT_END<br>
 * </td>
 * <td valign="top" align="center" style="background-color:#ffffff"><br>
 * </td>
 * </tr>
 * <tr>
 * <td valign="top" style="background-color:#c0c0c0"><b>BodyTag<br>
 * </b></td>
 * <td valign="top" align="center" style="background-color:#ffffff">AT_BEGIN,
 * NESTED<sup>1</sup><br>
 * </td>
 * <td valign="top" align="center" style="background-color:#ffffff">AT_BEGIN,
 * NESTED<sup>1</sup><br>
 * </td>
 * <td valign="top" align="center" style="background-color:#ffffff">AT_BEGIN, NESTED<br>
 * </td>
 * <td valign="top" align="center" style="background-color:#ffffff">AT_BEGIN, AT_END<br>
 * </td>
 * <td valign="top" align="center" style="background-color:#ffffff"><br>
 * </td>
 * </tr>
 * <tr>
 * <td valign="top" style="background-color:#c0c0c0"><b>SimpleTag<br>
 * </b></td>
 * <td valign="top" align="center" style="background-color:#ffffff"><br>
 * </td>
 * <td valign="top" align="center" style="background-color:#ffffff"><br>
 * </td>
 * <td valign="top" align="center" style="background-color:#ffffff"><br>
 * </td>
 * <td valign="top" align="center" style="background-color:#ffffff"><br>
 * </td>
 * <td valign="top" align="center" style="background-color:#ffffff">AT_BEGIN, AT_END<br>
 * </td>
 * </tr>
 * </tbody>
 * </table>
 * <sup>1</sup>在<code>doStartTag()</code>之后调用，如果返回<code>EVAL_BODY_INCLUDE</code>, 否则在<code>doInitBody()</code>之后调用.</blockquote>
 * <p>
 * <B>TLD中的变量信息</B>
 * <p>
 * 脚本变量信息也可以在大多数情况下直接编码到标记库描述符中，使用&lt;tag&gt;元素的子元素 &lt;variable&gt; . 查看JSP 规范.
 */
public class VariableInfo {

    /**
     * 脚本变量只在开始/结束标记中可见的作用域信息.
     */
    public static final int NESTED = 0;

    /**
     * 在开始标记之后脚本变量可见的作用域信息.
     */
    public static final int AT_BEGIN = 1;

    /**
     * 脚本变量在结束标记之后可见的作用域信息.
     */
    public static final int AT_END = 2;

    /**
     * @param varName 脚本变量的名称
     * @param className 变量的类型
     * @param declare 如果是 true, 它是一个新变量(在某些语言中，这需要声明)
     * @param scope 变量的词法作用域的指示
     */
    public VariableInfo(String varName, String className, boolean declare,
            int scope) {
        this.varName = varName;
        this.className = className;
        this.declare = declare;
        this.scope = scope;
    }

    // Accessor methods

    /**
     * 返回脚本变量的名称.
     */
    public String getVarName() {
        return varName;
    }

    /**
     * 返回该变量的类型.
     */
    public String getClassName() {
        return className;
    }

    /**
     * 返回是否是一个新变量. 如果是这样的话，在某些语言中，这就需要声明.
     */
    public boolean getDeclare() {
        return declare;
    }

    /**
     * 返回变量的词法作用域.
     *
     * @return AT_BEGIN, AT_END, NESTED.
     */
    public int getScope() {
        return scope;
    }

    // == private data
    private final String varName;
    private final String className;
    private final boolean declare;
    private final int scope;
}
