package javax.servlet.jsp.tagext;

import java.io.IOException;
import java.io.Writer;

import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.JspException;

/**
 * 将一部分JSP代码封装在一个可以在需要时多次调用的对象中.
 * <p>
 * JSP片段的定义必须只包含模板文本和JSP操作元素. 换句话说，它必须不包含脚本或脚本表达式. 
 * 翻译时, 容器生成一个JspFragment抽象类的实现执行定义的片段.
 * <p>
 * 标记处理程序可以调用片段零次或多次, 或者把它传递给其他标签, 在返回之前.
 * <p>
 * 注意，标记库开发人员和页面作者不应手动生成 JspFragment实现类.
 * <p>
 * <i>Implementation Note</i>: 没有必要为每个片段生成一个单独的类. 一个可能的实现是为每个实现了JspFragment的页面生成一个辅助类.
 * 可以通过一个鉴别器来选择该实例将执行的片段.
 */
public abstract class JspFragment {

    /**
     * 执行片段并输出到给定的Writer, 或者片段关联的JspContext的getOut()方法返回的JspWriter.
     *
     * @param out 输出到的Writer, 或者null 如果输出应该发送到JspContext.getOut().
     * @throws javax.servlet.jsp.JspException 在调用此片段时发生错误时抛出.
     * @throws javax.servlet.jsp.SkipPageException 如果页面（直接或间接）调用了调用此片段的标记处理程序将停止计算.
     * 容器必须抛出此异常, 如果 Classic Tag Handler返回Tag.SKIP_PAGE，或者如果Simple Tag Handler抛出 SkipPageException.
     * @throws java.io.IOException 如果写入流时出错.
     */
    public abstract void invoke( Writer out )
        throws JspException, IOException;

    /**
     * 返回绑定到这个JspFragment的JspContext.
     *
     * @return 在执行时这个片段使用的JspContext.
     */
    public abstract JspContext getJspContext();

}
