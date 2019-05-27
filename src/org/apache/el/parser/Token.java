package org.apache.el.parser;

/**
 * 描述输入令牌流.
 */
@SuppressWarnings("all")
public class Token implements java.io.Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * 描述这个token的类型.  这个编号系统是由JavaCCParser 确定的, 这些数字的表存储在文件中 ...Constants.java.
   */
  public int kind;

  /** 此Token的第一个字符的行号. */
  public int beginLine;
  /** 此Token的第一个字符的列号. */
  public int beginColumn;
  /** 此Token的最后一个字符的行号. */
  public int endLine;
  /** 此Token的最后一个字符的列号. */
  public int endColumn;

  /**
   * token的字符串图片.
   */
  public String image;

  /**
   * 来自输入流的下一个常规（非特殊）令牌.
   * 如果这是从输入流中的最后一个token, 或者如果令牌管理器没有读取下一个令牌, 这个字段设置为 null.
   * 只有当这个token也是一个常规token时设置为 true.
   */
  public Token next;

  /**
   * 此字段用于访问在此令牌之前发生的特殊令牌, 但是在上面常规（非特殊）令牌之后.
   * 如果没有特殊的 tokens, 这个字段设置为 null.
   * 如果有不止一个的特殊 token, 这个字段引用这些特殊token的最后一个, 其specialToken字段引用下一个特殊token, 一直到第一个特殊 token (specialToken 字段是 null).
   * 特殊token的 next 字段引用其它的紧邻的特殊 token  (中间没有常规 token).  如果没有这样的 token, 这个字段是 null.
   */
  public Token specialToken;

  /**
   * Token可选的属性值.
   * 不作为句法糖使用的令牌通常包含有意义的值，这些值将在以后由编译器或解释器使用. 此属性的值往往不同于 image.
   */
  public Object getValue() {
    return null;
  }

  public Token() {}

  public Token(int kind)
  {
    this(kind, null);
  }

  public Token(int kind, String image)
  {
    this.kind = kind;
    this.image = image;
  }

  /**
   * 返回 image.
   */
  public String toString()
  {
    return image;
  }

  /**
   * Returns a new Token object, by default. However, if you want, you
   * can create and return subclass objects based on the value of ofKind.
   * Simply add the cases to the switch for all those special cases.
   * For example, if you have a subclass of Token called IDToken that
   * you want to create if ofKind is ID, simply add something like :
   *
   *    case MyParserConstants.ID : return new IDToken(ofKind, image);
   *
   * to the following switch statement. Then you can cast matchedToken
   * variable to the appropriate type and use sit in your lexical actions.
   */
  public static Token newToken(int ofKind, String image)
  {
    switch(ofKind)
    {
      default : return new Token(ofKind, image);
    }
  }

  public static Token newToken(int ofKind)
  {
    return newToken(ofKind, null);
  }

}
/* JavaCC - OriginalChecksum=3fc97649fffa8b13e1e03af022020b2f (do not edit this line) */
