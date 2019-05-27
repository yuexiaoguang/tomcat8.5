package org.apache.el.parser;

import java.util.Arrays;

import javax.el.ELException;
import javax.el.MethodInfo;
import javax.el.PropertyNotWritableException;
import javax.el.ValueReference;

import org.apache.el.lang.ELSupport;
import org.apache.el.lang.EvaluationContext;
import org.apache.el.util.MessageFactory;

public abstract class SimpleNode extends ELSupport implements Node {
    protected Node parent;

    protected Node[] children;

    protected final int id;

    protected String image;

    public SimpleNode(int i) {
        id = i;
    }

    @Override
    public void jjtOpen() {
        // NOOP by default
    }

    @Override
    public void jjtClose() {
        // NOOP by default
    }

    @Override
    public void jjtSetParent(Node n) {
        parent = n;
    }

    @Override
    public Node jjtGetParent() {
        return parent;
    }

    @Override
    public void jjtAddChild(Node n, int i) {
        if (children == null) {
            children = new Node[i + 1];
        } else if (i >= children.length) {
            Node c[] = new Node[i + 1];
            System.arraycopy(children, 0, c, 0, children.length);
            children = c;
        }
        children[i] = n;
    }

    @Override
    public Node jjtGetChild(int i) {
        return children[i];
    }

    @Override
    public int jjtGetNumChildren() {
        return (children == null) ? 0 : children.length;
    }

    /*
     * 可以在子类中重写这两种方法, 当树被废弃时, 自定义节点出现的方式.
     * 如果输出多于一行, 需要重写 toString(String), 否则大多需要重写 toString().
     */

    @Override
    public String toString() {
        if (this.image != null) {
            return ELParserTreeConstants.jjtNodeName[id] + "[" + this.image
                    + "]";
        }
        return ELParserTreeConstants.jjtNodeName[id];
    }

    @Override
    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    @Override
    public Class<?> getType(EvaluationContext ctx)
            throws ELException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getValue(EvaluationContext ctx)
            throws ELException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isReadOnly(EvaluationContext ctx)
            throws ELException {
        return true;
    }

    @Override
    public void setValue(EvaluationContext ctx, Object value)
            throws ELException {
        throw new PropertyNotWritableException(MessageFactory.get("error.syntax.set"));
    }

    @Override
    public void accept(NodeVisitor visitor) throws Exception {
        visitor.visit(this);
        if (this.children != null && this.children.length > 0) {
            for (int i = 0; i < this.children.length; i++) {
                this.children[i].accept(visitor);
            }
        }
    }

    @Override
    public Object invoke(EvaluationContext ctx, Class<?>[] paramTypes,
            Object[] paramValues) throws ELException {
        throw new UnsupportedOperationException();
    }

    @Override
    public MethodInfo getMethodInfo(EvaluationContext ctx,
            Class<?>[] paramTypes) throws ELException {
        throw new UnsupportedOperationException();
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(children);
        result = prime * result + id;
        result = prime * result + ((image == null) ? 0 : image.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SimpleNode)) {
            return false;
        }
        SimpleNode other = (SimpleNode) obj;
        if (!Arrays.equals(children, other.children)) {
            return false;
        }
        if (id != other.id) {
            return false;
        }
        if (image == null) {
            if (other.image != null) {
                return false;
            }
        } else if (!image.equals(other.image)) {
            return false;
        }
        return true;
    }

    /**
     * @since EL 2.2
     */
    @Override
    public ValueReference getValueReference(EvaluationContext ctx) {
        return null;
    }

    /**
     * @since EL 2.2
     */
    @Override
    public boolean isParametersProvided() {
        return false;
    }
}
