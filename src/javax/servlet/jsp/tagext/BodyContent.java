package javax.servlet.jsp.tagext;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import javax.servlet.jsp.JspWriter;

/**
 * 对动作主体的计算的封装，因此它可以被标记处理程序使用. BodyContent是JspWriter的子类.
 * <p>
 * 注意BodyContent的主体是计算的结果, 所以它不会包含动作之类的, 除了它们的调用结果.
 * <p>
 * BodyContent有方法将其内容转换成 String, 来读取其内容, 和清空内容.
 * <p>
 * BodyContent对象的缓冲区大小是未绑定的. BodyContent对象不能是autoFlush模型. 不能在一个BodyContent对象上执行刷新, 因为没有支持流.
 * <p>
 * 通过调用PageContext类的pushBody 和 popBody方法创建 BodyContent实例.
 * BodyContent是和另一个JspWriter封装在一起的 (可能是另一个BodyContent对象)跟随其相关动作的结构.
 * <p>
 * BodyContent调用setBodyContent()让BodyTag可用.
 * 标签处理程序可以使用对象直到调用doEndTag().
 */
public abstract class BodyContent extends JspWriter {

    /**
     * @param e 封装的JspWriter
     */
    protected BodyContent(JspWriter e) {
        super(UNBOUNDED_BUFFER, false);
        this.enclosingWriter = e;
    }

    /**
     * 重新定义的 flush()，因此它不是合法的.
     * <p>
     * 不能刷新一个BodyContent，因为背后没有支持流.
     *
     * @throws IOException
     */
    @Override
    public void flush() throws IOException {
        throw new IOException("Illegal to flush within a custom tag");
    }

    /**
     * 清空主体，但不抛出任何异常.
     */
    public void clearBody() {
        try {
            this.clear();
        } catch (IOException ex) {
            // TODO -- clean this one up.
            throw new Error("internal error!;");
        }
    }

    /**
     * 返回这个BodyContent的值作为一个 Reader.
     */
    public abstract Reader getReader();

    /**
     * 返回这个BodyContent的值作为一个String.
     */
    public abstract String getString();

    /**
     * 将这个BodyContent的内容写入一个Writer. 子类可以优化公共调用模式.
     *
     * @param out 
     * @throws IOException 如果发生了I/O错误，在将这个BodyContent的内容写入给定的Writer期间
     */
    public abstract void writeOut(Writer out) throws IOException;

    /**
     * 获取封装的JspWriter.
     */
    public JspWriter getEnclosingWriter() {
        return enclosingWriter;
    }

    private final JspWriter enclosingWriter;
}
