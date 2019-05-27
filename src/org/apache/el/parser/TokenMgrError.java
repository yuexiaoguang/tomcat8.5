package org.apache.el.parser;

/** Token Manager Error. */
@SuppressWarnings("all")
public class TokenMgrError extends Error {

  private static final long serialVersionUID = 1L;


  /**
   * 词汇错误发生.
   */
  static final int LEXICAL_ERROR = 0;

  /**
   * 试图创建一个 static 令牌管理器的第二个实例.
   */
  static final int STATIC_LEXER_ERROR = 1;

  /**
   * 试图更改为无效的词汇状态.
   */
  static final int INVALID_LEXICAL_STATE = 2;

  /**
   * 在令牌管理器中检测到无限循环.
   */
  static final int LOOP_DETECTED = 3;

  /**
   * 上面 4 个值的其中之一.
   */
  int errorCode;

  /**
   * 通过它们的转义字符(或 unicode 转义)替换不可打印的字符
   */
  protected static final String addEscapes(String str) {
    StringBuffer retval = new StringBuffer();
    char ch;
    for (int i = 0; i < str.length(); i++) {
      switch (str.charAt(i))
      {
        case 0 :
          continue;
        case '\b':
          retval.append("\\b");
          continue;
        case '\t':
          retval.append("\\t");
          continue;
        case '\n':
          retval.append("\\n");
          continue;
        case '\f':
          retval.append("\\f");
          continue;
        case '\r':
          retval.append("\\r");
          continue;
        case '\"':
          retval.append("\\\"");
          continue;
        case '\'':
          retval.append("\\\'");
          continue;
        case '\\':
          retval.append("\\\\");
          continue;
        default:
          if ((ch = str.charAt(i)) < 0x20 || ch > 0x7e) {
            String s = "0000" + Integer.toString(ch, 16);
            retval.append("\\u" + s.substring(s.length() - 4, s.length()));
          } else {
            retval.append(ch);
          }
          continue;
      }
    }
    return retval.toString();
  }

  /**
   * 当令牌管理器抛出错误以指示词汇错误时, 返回详细的错误消息.
   * Parameters :
   *    EOFSeen     : 是否是EOF 导致的词汇错误
   *    curLexState : 发生错误的词汇状态
   *    errorLine   : 发生错误的行号
   *    errorColumn : 发生错误的列号
   *    errorAfter  : 在出现此错误之前所看到的前缀
   *    curchar     : 错误的字符
   * Note: 通过修改此方法，可以自定义词汇错误消息.
   */
  protected static String LexicalError(boolean EOFSeen, int lexState, int errorLine, int errorColumn, String errorAfter, char curChar) {
    return("Lexical error at line " +
          errorLine + ", column " +
          errorColumn + ".  Encountered: " +
          (EOFSeen ? "<EOF> " : ("\"" + addEscapes(String.valueOf(curChar)) + "\"") + " (" + (int)curChar + "), ") +
          "after : \"" + addEscapes(errorAfter) + "\"");
  }

  /**
   * 也可以修改这个方法的主体自定义错误消息.
   * 例如, 例如 LOOP_DETECTED 和 INVALID_LEXICAL_STATE的情况不是终端用户关心的问题, 因此可以返回类似以下的消息 :
   *
   *     "Internal Error : Please file a bug report .... "
   */
  public String getMessage() {
    return super.getMessage();
  }


  public TokenMgrError() {
  }

  public TokenMgrError(String message, int reason) {
    super(message);
    errorCode = reason;
  }

  public TokenMgrError(boolean EOFSeen, int lexState, int errorLine, int errorColumn, String errorAfter, char curChar, int reason) {
    this(LexicalError(EOFSeen, lexState, errorLine, errorColumn, errorAfter, curChar), reason);
  }
}
/* JavaCC - OriginalChecksum=de3ff0bacfb0fe749cc8eaf56ae82fea (do not edit this line) */
