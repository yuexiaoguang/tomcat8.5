package org.apache.catalina.ant;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Redirector;
import org.apache.tools.ant.types.RedirectorElement;

/**
 * 支持Catalina Ant任务的输出重定向的抽象基类. 这些任务必须Ant 1.5 或更新.
 * <br>
 * <strong>WARNING:</strong> 由于依赖链, Ant可以不止一次调用任务，这可能在配置时影响输出重定向.
 * 如果正在收集属性中的输出, 它将只收集第一次运行的输出, 因为Ant属性是不可变的，而且一旦创建，它们就不能被改变.
 * <br>
 * 如果在文件中收集输出，则该文件将被最后一次运行的输出覆盖, 除非设置 append="true", 在这种情况下，每次运行都会将其输出追加到文件中.
 */
public abstract class BaseRedirectorHelperTask extends Task {

    /** 重定向帮助类 */
    protected final Redirector redirector = new Redirector(this);

    /** 这个任务的重定向元素 */
    protected RedirectorElement redirectorElement = null;

    /** 信息输出流 */
    protected OutputStream redirectOutStream = null;

    /** 错误输出流 */
    protected OutputStream redirectErrStream = null;

    /** 信息输出的打印流 */
    PrintStream redirectOutPrintStream = null;

    /** 错误输出的打印流 */
    PrintStream redirectErrPrintStream = null;

    /**
     * 是否失败(抛出 BuildException)如果ManagerServlet 返回一个错误. 默认行为是这样做的.
     * <b>
     * 此标志不控制参数检查. 如果任务获得错误或无效的参数, 将从这个标记设置的地方抛出 BuildException
     */
    protected boolean failOnError = true;

    /**
     * <code>true</code>当为这个任务请求输出重定向时.
      * 默认是记录到Ant日志.
     */
    protected boolean redirectOutput = false;

    /**
     * 将被设置为<code>true</code> 当重定向器配置完成.
     */
    protected boolean redirectorConfigured = false;

    /**
     * 标志表明, 如果重定向, 输出也应该始终发送到日志. 默认的输出只发送到重定向流.
     */
    protected boolean alwaysLog = false;


    /**
     * 是否失败(抛出 BuildException), 如果ManagerServlet 返回一个错误. 默认行为是这样做的. 
     *
     * @param fail The new value of failonerror
     */
    public void setFailonerror(boolean fail) {
        failOnError = fail;
    }


    /**
     * @return <code>true</code>如果错误发生，任务继续,
     *         否则<code>false</code>
     */
    public boolean isFailOnError() {
        return failOnError;
    }


    /**
     * 设置任务的输出重定向到的文件.
     *
     * @param out 输出文件的名称
     */
    public void setOutput(File out) {
        redirector.setOutput(out);
        redirectOutput = true;
    }


    /**
     * 设置任务的错误输出重定向到的文件..
     *
     * @param error 错误文件的名称
     *
     */
    public void setError(File error) {
        redirector.setError(error);
        redirectOutput = true;
    }


    /**
     * 控制是否记录错误输出. 当输出被重定向和Ant日志中需要输出错误输出时，才有用
     *
     * @param logError 如果为true，则将标准错误发送到Ant日志系统，而不是发送到输出流
     */
    public void setLogError(boolean logError) {
        redirector.setLogError(logError);
        redirectOutput = true;
    }


    /**
     * 属性名称，其值应设置为任务的输出.
     *
     * @param outputProperty property name
     *
     */
    public void setOutputproperty(String outputProperty) {
        redirector.setOutputProperty(outputProperty);
        redirectOutput = true;
    }


    /**
     * 属性名称，其值应设置为任务的错误.
     *
     * @param errorProperty property name
     */
    public void setErrorProperty(String errorProperty) {
        redirector.setErrorProperty(errorProperty);
        redirectOutput = true;
    }


    /**
     * 如果为true，则将输出追加到现有文件.
     *
     * @param append 如果为true，则将输出追加到现有文件
     */
    public void setAppend(boolean append) {
        redirector.setAppend(append);
        redirectOutput = true;
    }


    /**
     * 如果为true, （错误和非错误）输出将被重定向, 像指定的那样, 当被发送到Ant日志记录机制时，就好像没有进行重定向一样.
     * 默认是 false.
     * <br>
     * 其实在内部处理, Ant 1.6.3 将通过<code>Redirector</code>本身处理.
     *
     * @param alwaysLog
     */
    public void setAlwaysLog(boolean alwaysLog) {
        this.alwaysLog = alwaysLog;
        redirectOutput = true;
    }


    /**
     * 即使空时也应该创建输出和错误文件.
     * 默认是 true.
     *
     * @param createEmptyFiles
     */
    public void setCreateEmptyFiles(boolean createEmptyFiles) {
        redirector.setCreateEmptyFiles(createEmptyFiles);
        redirectOutput = true;
    }


    /**
     * 添加<CODE>RedirectorElement</CODE>到这个任务.
     *
     * @param redirectorElement <CODE>RedirectorElement</CODE>.
     */
    public void addConfiguredRedirector(RedirectorElement redirectorElement) {
        if (this.redirectorElement != null) {
            throw new BuildException("Cannot have > 1 nested <redirector>s");
        } else {
            this.redirectorElement = redirectorElement;
        }
    }


    /**
     * 从RedirectorElement设置Redirector上的属性.
     */
    private void configureRedirector() {
        if (redirectorElement != null) {
            redirectorElement.configure(redirector);
            redirectOutput = true;
        }
        /*
         * 由于依赖链, Ant 可以不止一次地调用任务,
         * 这是为了防止尝试配置不止一次的Redirector.
         */
        redirectorConfigured = true;
    }


    /**
     * 设置Redirector的属性并创建输出流
     */
    protected void openRedirector() {
        if (!redirectorConfigured) {
            configureRedirector();
        }
        if (redirectOutput) {
            redirector.createStreams();
            redirectOutStream = redirector.getOutputStream();
            redirectOutPrintStream = new PrintStream(redirectOutStream);
            redirectErrStream = redirector.getErrorStream();
            redirectErrPrintStream = new PrintStream(redirectErrStream);
        }
    }


    /**
     * 请关闭所有流重定向. 在离开任务使流刷新到内容之前，调用此方法是必要的.
     * 如果正在收集属性中的输出, 只有在调用此方法时才会创建它, 否则你会发现它没有设置.
     */
    protected void closeRedirector() {
        try {
            if (redirectOutput && redirectOutPrintStream != null) {
                redirector.complete();
            }
        } catch (IOException ioe) {
            log("Error closing redirector: " + ioe.getMessage(), Project.MSG_ERR);
        }
        /*
         * 由于依赖链, Ant 可以不止一次地调用任务,
         * 这是为了防止尝试配置不止一次的Redirector.
         */
        redirectOutStream = null;
        redirectOutPrintStream = null;
        redirectErrStream = null;
        redirectErrPrintStream = null;
    }


    /**
     * 使用INFO优先级处理输出.
     *
     * @param output 日志输出. 不应该是<code>null</code>.
     */
    @Override
    protected void handleOutput(String output) {
        if (redirectOutput) {
            if (redirectOutPrintStream == null) {
                openRedirector();
            }
            redirectOutPrintStream.println(output);
            if (alwaysLog) {
                log(output, Project.MSG_INFO);
            }
        } else {
            log(output, Project.MSG_INFO);
        }
    }


    /**
     * 使用INFO优先级处理输出并刷新输出流.
     *
     * @param output 日志输出. 不应该是<code>null</code>.
     */
    @Override
    protected void handleFlush(String output) {
        handleOutput(output);
        redirectOutPrintStream.flush();
    }


    /**
     * 使用ERR优先级处理错误输出.
     *
     * @param output 错误输出. 不应该是<code>null</code>.
     */
    @Override
    protected void handleErrorOutput(String output) {
        if (redirectOutput) {
            if (redirectErrPrintStream == null) {
                openRedirector();
            }
            redirectErrPrintStream.println(output);
            if (alwaysLog) {
                log(output, Project.MSG_ERR);
            }
        } else {
            log(output, Project.MSG_ERR);
        }
    }


    /**
     * 使用ERR优先级处理错误输出并刷新输出流.
     *
     * @param output 错误输出. 不应该是<code>null</code>.
     */
    @Override
    protected void handleErrorFlush(String output) {
        handleErrorOutput(output);
        redirectErrPrintStream.flush();
    }


    /**
     * 处理ERR优先级的输出到错误流，其他优先级的输出到输出流.
     *
     * @param output 输出. 不能是<code>null</code>.
     * @param priority 应使用的优先级别
     */
    protected void handleOutput(String output, int priority) {
        if (priority == Project.MSG_ERR) {
            handleErrorOutput(output);
        } else {
            handleOutput(output);
        }
    }
}
