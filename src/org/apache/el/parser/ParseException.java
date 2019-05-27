package org.apache.el.parser;

/**
 * 遇到解析错误.
 * 可以显式创建此异常类型的对象, 通过调用生成的解析器中的 generateParseException 方法.
 *
 * 只要保留公共字段，就可以修改此类以自定义错误报告机制.
 */
@SuppressWarnings("all")
public class ParseException extends Exception {

  private static final long serialVersionUID = 1L;

  public ParseException(Token currentTokenVal,
                        int[][] expectedTokenSequencesVal,
                        String[] tokenImageVal
                       )
  {
    super(initialise(currentTokenVal, expectedTokenSequencesVal, tokenImageVal));
    currentToken = currentTokenVal;
    expectedTokenSequences = expectedTokenSequencesVal;
    tokenImage = tokenImageVal;
  }


  public ParseException() {
    super();
  }

  public ParseException(String message) {
    super(message);
  }


  /**
   * 这是最后被成功消耗的token.  如果这个对象是因为一个解析错误, 这个 token 随后的token将会是第一个错误 token.
   */
  public Token currentToken;

  /**
   * 此数组中的每个条目都是整数数组.  每一个整数数组代表一个token序列 (按序值).
   */
  public int[][] expectedTokenSequences;

  public String[] tokenImage;

  /**
   * 生成一个解析错误信息并返回它.  如果由于解析错误而创建了该对象, 而且没有捕获它 (从解析器抛出) 得到正确的错误信息显示.
   */
  private static String initialise(Token currentToken,
                           int[][] expectedTokenSequences,
                           String[] tokenImage) {
    StringBuffer expected = new StringBuffer();
    int maxSize = 0;
    for (int i = 0; i < expectedTokenSequences.length; i++) {
      if (maxSize < expectedTokenSequences[i].length) {
        maxSize = expectedTokenSequences[i].length;
      }
      for (int j = 0; j < expectedTokenSequences[i].length; j++) {
        expected.append(tokenImage[expectedTokenSequences[i][j]]).append(' ');
      }
      if (expectedTokenSequences[i][expectedTokenSequences[i].length - 1] != 0) {
        expected.append("...");
      }
      expected.append(System.lineSeparator()).append("    ");
    }
    String retval = "Encountered \"";
    Token tok = currentToken.next;
    for (int i = 0; i < maxSize; i++) {
      if (i != 0) retval += " ";
      if (tok.kind == 0) {
        retval += tokenImage[0];
        break;
      }
      retval += " " + tokenImage[tok.kind];
      retval += " \"";
      retval += add_escapes(tok.image);
      retval += " \"";
      tok = tok.next;
    }
    retval += "\" at line " + currentToken.next.beginLine + ", column " + currentToken.next.beginColumn;
    retval += "." + System.lineSeparator();
    if (expectedTokenSequences.length == 1) {
      retval += "Was expecting:" + System.lineSeparator() + "    ";
    } else {
      retval += "Was expecting one of:" + System.lineSeparator() + "    ";
    }
    retval += expected.toString();
    return retval;
  }

  /**
   * 转义字符.
   */
  static String add_escapes(String str) {
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

}
/* JavaCC - OriginalChecksum=87586a39aa89f164889cc59bc6a7e7ad (do not edit this line) */
