package org.apache.el.parser;

public interface NodeVisitor {
    public void visit(Node node) throws Exception;
}
