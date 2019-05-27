package org.apache.tomcat.jni;

public class LibraryNotFoundError extends UnsatisfiedLinkError {

    private static final long serialVersionUID = 1L;

    private final String libraryNames;

    /**
     *
     * @param libraryNames 加载失败的本地库的文件名列表
     * @param errors 尝试加载每个库时收到的错误消息列表
     */
    public LibraryNotFoundError(String libraryNames, String errors){
        super(errors);
        this.libraryNames = libraryNames;
    }

    public String getLibraryNames(){
        return libraryNames;
    }
}
