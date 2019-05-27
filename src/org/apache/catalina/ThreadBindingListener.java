package org.apache.catalina;


/**
 * 当进入应用范围时，建立命名关联的回调. 这相当于设置上下文类加载器.
 */
public interface ThreadBindingListener {

    public void bind();
    public void unbind();

}
