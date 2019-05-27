package javax.servlet.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 表示{@link javax.servlet.Servlet}是否使用{@code multipart/form-data} MIME类型. <br>
 * <br>
 *
 * 给定的{@code multipart/form-data}请求的{@link javax.servlet.http.Part}组件由{@code MultipartConfig}注解的Servlet检索，
 * 通过调用{@link javax.servlet.http.HttpServletRequest#getPart}或{@link javax.servlet.http.HttpServletRequest#getParts}.<br>
 * <br>
 *
 * 即<code>@WebServlet("/upload")}</code><br>
 *
 * <code>@MultipartConfig()</code> <code>public class UploadServlet extends HttpServlet ... } </code><br>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MultipartConfig {

    /**
     * @return 容器存储临时文件的位置
     */
    String location() default "";

    /**
     * @return 允许上传文件的最大大小 (in bytes)
     */
    long maxFileSize() default -1L;

    /**
     * @return 允许的请求的最大大小{@code multipart/form-data}
     */
    long maxRequestSize() default -1L;

    /**
     * @return 将文件写入磁盘的大小阈值
     */
    int fileSizeThreshold() default 0;
}
