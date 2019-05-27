package org.apache.el.parser;

import javax.el.ELException;
import javax.el.MethodInfo;
import javax.el.ValueReference;

import org.apache.el.lang.EvaluationContext;


/* 所有的 AST 节点必须实现这个接口. 它为构建节点间父子关系提供了基本的机制. */
@SuppressWarnings("all")
public interface Node {

  /** 此方法在节点已成为当前节点之后调用. 指示现在可以将子节点添加到它. */
  public void jjtOpen();

  /** 在添加所有子节点之后调用此方法. */
  public void jjtClose();

  /** 这两个方法来通知它的父节点. */
  public void jjtSetParent(Node n);
  public Node jjtGetParent();

  /** 这个方法告诉节点添加它的参数到子节点的列表.  */
  public void jjtAddChild(Node n, int i);

  /** 此方法返回子节点. 子节点从零开始编号, 从左到右. */
  public Node jjtGetChild(int i);

  /** 返回节点有多少个子节点. */
  public int jjtGetNumChildren();

  public String getImage();

  public Object getValue(EvaluationContext ctx) throws ELException;
  public void setValue(EvaluationContext ctx, Object value) throws ELException;
  public Class<?> getType(EvaluationContext ctx) throws ELException;
  public boolean isReadOnly(EvaluationContext ctx) throws ELException;
  public void accept(NodeVisitor visitor) throws Exception;
  public MethodInfo getMethodInfo(EvaluationContext ctx, Class<?>[] paramTypes)
          throws ELException;
  public Object invoke(EvaluationContext ctx, Class<?>[] paramTypes,
          Object[] paramValues) throws ELException;

  /**
   * @since EL 2.2
   */
  public ValueReference getValueReference(EvaluationContext ctx);

  /**
   * @since EL 2.2
   */
  public boolean isParametersProvided();
}
