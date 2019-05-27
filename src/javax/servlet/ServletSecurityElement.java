package javax.servlet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.servlet.annotation.HttpMethodConstraint;
import javax.servlet.annotation.ServletSecurity;

public class ServletSecurityElement extends HttpConstraintElement {

    private final Map<String,HttpMethodConstraintElement> methodConstraints =
        new HashMap<>();

    public ServletSecurityElement() {
        super();
    }

    /**
     * @param httpConstraintElement 约束
     */
    public ServletSecurityElement(HttpConstraintElement httpConstraintElement) {
        this (httpConstraintElement, null);
    }

    /**
     * 使用指定的约束对指定的方法，其它的方法使用默认的HttpConstraintElement.
     * @param httpMethodConstraints 方法约束
     * @throws IllegalArgumentException 如果方法名称指定超过一次
     */
    public ServletSecurityElement(
            Collection<HttpMethodConstraintElement> httpMethodConstraints) {
        super();
        addHttpMethodConstraints(httpMethodConstraints);
    }


    /**
     * 使用指定的HttpConstraintElement作为默认的，并指定约束给指定的方法.
     * 
     * @param httpConstraintElement 默认的约束
     * @param httpMethodConstraints 方法约束
     * @throws IllegalArgumentException 如果方法名称指定超过一次
     */
    public ServletSecurityElement(HttpConstraintElement httpConstraintElement,
            Collection<HttpMethodConstraintElement> httpMethodConstraints) {
        super(httpConstraintElement.getEmptyRoleSemantic(),
                httpConstraintElement.getTransportGuarantee(),
                httpConstraintElement.getRolesAllowed());
        addHttpMethodConstraints(httpMethodConstraints);
    }

    /**
     * 从一个注解创建.
     * 
     * @param annotation 注解用作新实例的基础
     * @throws IllegalArgumentException 如果方法名称指定超过一次
     */
    public ServletSecurityElement(ServletSecurity annotation) {
        this(new HttpConstraintElement(annotation.value().value(),
                annotation.value().transportGuarantee(),
                annotation.value().rolesAllowed()));

        List<HttpMethodConstraintElement> l = new ArrayList<>();
        HttpMethodConstraint[] constraints = annotation.httpMethodConstraints();
        if (constraints != null) {
            for (int i = 0; i < constraints.length; i++) {
                HttpMethodConstraintElement e =
                    new HttpMethodConstraintElement(constraints[i].value(),
                            new HttpConstraintElement(
                                    constraints[i].emptyRoleSemantic(),
                                    constraints[i].transportGuarantee(),
                                    constraints[i].rolesAllowed()));
                l.add(e);
            }
        }
        addHttpMethodConstraints(l);
    }

    public Collection<HttpMethodConstraintElement> getHttpMethodConstraints() {
        Collection<HttpMethodConstraintElement> result = new HashSet<>();
        result.addAll(methodConstraints.values());
        return result;
    }

    public Collection<String> getMethodNames() {
        Collection<String> result = new HashSet<>();
        result.addAll(methodConstraints.keySet());
        return result;
    }

    private void addHttpMethodConstraints(
            Collection<HttpMethodConstraintElement> httpMethodConstraints) {
        if (httpMethodConstraints == null) {
            return;
        }
        for (HttpMethodConstraintElement constraint : httpMethodConstraints) {
            String method = constraint.getMethodName();
            if (methodConstraints.containsKey(method)) {
                throw new IllegalArgumentException(
                        "Duplicate method name: " + method);
            }
            methodConstraints.put(method, constraint);
        }
    }
}
