package org.apache.tomcat.util.http.fileupload;

/**
 * 处理请求时遇到的错误.
 */
public class FileUploadException extends Exception {

    private static final long serialVersionUID = -4222909057964038517L;

    public FileUploadException() {
        super();
    }

    public FileUploadException(String message, Throwable cause) {
        super(message, cause);
    }

    public FileUploadException(String message) {
        super(message);
    }

    public FileUploadException(Throwable cause) {
        super(cause);
    }
}
