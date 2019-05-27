package org.apache.tomcat.util.bcel.classfile;

/**
 * 当BCEL尝试读取类文件, 并确定文件格式错误或无法解释为类文件时抛出.
 */
public class ClassFormatException extends RuntimeException {

    private static final long serialVersionUID = 3243149520175287759L;

    public ClassFormatException() {
        super();
    }


    public ClassFormatException(final String s) {
        super(s);
    }
}
