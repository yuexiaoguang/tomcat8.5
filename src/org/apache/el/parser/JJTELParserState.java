package org.apache.el.parser;

@SuppressWarnings("all")
public class JJTELParserState {
  private java.util.List<Node> nodes;
  private java.util.List<Integer> marks;

  private int sp;        // 堆栈节点数
  private int mk;        // 当前标记
  private boolean node_created;

  public JJTELParserState() {
    nodes = new java.util.ArrayList<Node>();
    marks = new java.util.ArrayList<Integer>();
    sp = 0;
    mk = 0;
  }

  /* 确定当前节点是否是封闭的和推送的. 这只能在节点范围的最终用户操作中调用.  */
  public boolean nodeCreated() {
    return node_created;
  }

  /* 重新初始化节点堆栈.  由解析器的 ReInit() 方法调用. */
  public void reset() {
    nodes.clear();
    marks.clear();
    sp = 0;
    mk = 0;
  }

  /* 返回 AST 的根节点.  在成功解析之后调用它是有意义的. */
  public Node rootNode() {
    return nodes.get(0);
  }

  /* 推送一个节点到堆栈上. */
  public void pushNode(Node n) {
    nodes.add(n);
    ++sp;
  }

  /* 返回堆栈顶部的节点, 并删除它.  */
  public Node popNode() {
    if (--sp < mk) {
      mk = marks.remove(marks.size()-1);
    }
    return nodes.remove(nodes.size()-1);
  }

  /* 返回当前堆栈顶部的节点. */
  public Node peekNode() {
    return nodes.get(nodes.size()-1);
  }

  /* 返回当前节点范围内堆栈上的子节点数. */
  public int nodeArity() {
    return sp - mk;
  }


  public void clearNodeScope(Node n) {
    while (sp > mk) {
      popNode();
    }
    mk = marks.remove(marks.size()-1);
  }


  public void openNodeScope(Node n) {
    marks.add(mk);
    mk = sp;
    n.jjtOpen();
  }


  public void closeNodeScope(Node n, int num) {
    mk = marks.remove(marks.size()-1);
    while (num-- > 0) {
      Node c = popNode();
      c.jjtSetParent(n);
      n.jjtAddChild(c, num);
    }
    n.jjtClose();
    pushNode(n);
    node_created = true;
  }


  public void closeNodeScope(Node n, boolean condition) {
    if (condition) {
      int a = nodeArity();
      mk = marks.remove(marks.size()-1);
      while (a-- > 0) {
        Node c = popNode();
        c.jjtSetParent(n);
        n.jjtAddChild(c, a);
      }
      n.jjtClose();
      pushNode(n);
      node_created = true;
    } else {
      mk = marks.remove(marks.size()-1);
      node_created = false;
    }
  }
}
/* JavaCC - OriginalChecksum=70ac39f1e0e1eed7476e1dae2dfa25fa (do not edit this line) */
