package javax.el;

import java.lang.reflect.Method;

public abstract class FunctionMapper {

    public abstract Method resolveFunction(String prefix, String localName);

    /**
     * 将方法映射到函数名.
     *
     * @param prefix    函数前缀
     * @param localName 函数名
     * @param method    方法
     */
    public void mapFunction(String prefix, String localName, Method method) {
        // NO-OP
    }
}
