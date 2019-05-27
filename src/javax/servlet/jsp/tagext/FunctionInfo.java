package javax.servlet.jsp.tagext;

/**
 * 标记库中函数的信息.
 * 这个类是从标记库描述符文件(TLD)实例化的，只在翻译时可用.
 */
public class FunctionInfo {

    /**
     * @param name 函数的名称
     * @param klass 函数的类
     * @param signature 函数的签名
     */
    public FunctionInfo(String name, String klass, String signature) {
        this.name = name;
        this.functionClass = klass;
        this.functionSignature = signature;
    }

    /**
     * 函数的名称.
     */
    public String getName() {
        return name;
    }

    /**
     * 函数的类.
     */
    public String getFunctionClass() {
        return functionClass;
    }

    /**
     * 函数的签名.
     */
    public String getFunctionSignature() {
        return functionSignature;
    }

    /*
     * fields
     */
    private final String name;
    private final String functionClass;
    private final String functionSignature;
}
