package javax.servlet;

/**
 * 定义servlet或过滤器抛出的异常，表明它是永久的或暂时不可用的.
 * <p>
 * 当servlet或过滤器永久不可用时, 它还有点问题, 在采取某些行动之前，它不能处理请求.
 * 例如, servlet可能配置不正确, 或者过滤器的状态可能被破坏了. 组件应记录所需的错误和纠正措施.
 * <p>
 * 如果某个servlet或过滤器由于某些系统范围问题不能及时处理请求，则暂时无法使用它.
 * 例如, 第三层服务器可能无法访问, 或者可能没有足够的内存或磁盘存储来处理请求. 系统管理员可能需要采取纠正措施.
 * <p>
 * servlet容器可以以同样的方式安全地处理这两种类型的不可用异常. 但是, 有效地处理暂时不可用，使servlet容器更加健壮.
 * 具体而言，servlet容器可能会阻塞对servlet或过滤器的请求，这是由异常建议的一段时间, 而不是在servlet容器重新启动之前拒绝它们.
 */
public class UnavailableException extends ServletException {

    private static final long serialVersionUID = 1L;

    private final boolean permanent; // needs admin action?
    private final int seconds; // unavailability estimate

    /**
     * @param msg 描述信息
     */
    public UnavailableException(String msg) {
        super(msg);
        seconds = 0;
        permanent = true;
    }

    /**
     * 在某些情况下，servlet无法做出估计. 例如, servlet可能知道它需要的服务器没有运行, 但不能报告需要多长时间才能恢复.
     *
     * @param msg 描述信息, 可以写入日志文件或显示给用户.
     * @param seconds 指定servlet期望不可用的秒数; 如果为零或负, servlet不能作出估计
     */
    public UnavailableException(String msg, int seconds) {
        super(msg);

        if (seconds <= 0)
            this.seconds = -1;
        else
            this.seconds = seconds;
        permanent = false;
    }

    /**
     * 返回servlet是否永久不可用.
     * 如果是这样，servlet有问题，系统管理员必须采取一些纠正措施.
     *
     * @return <code>true</code>如果servlet永久不可用;
     *         <code>false</code>如果servlet可用或暂时不可用
     */
    public boolean isPermanent() {
        return permanent;
    }

    /**
     * 返回servlet期望暂时不可用的秒数.
     * <p>
     * 如果该方法返回一个负数，servlet将永远无法使用，或者无法提供它将不能使用多长时间的估计. 自首次报告异常以来，没有时间对所经过的时间进行纠正.
     *
     * @return servlet暂时不可用的秒数, 或一个负数，如果servlet是永久不可用或不能作出估计
     */
    public int getUnavailableSeconds() {
        return permanent ? -1 : seconds;
    }
}
