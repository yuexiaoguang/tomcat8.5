package org.apache.el.parser;

import java.io.StringReader;
import javax.el.ELException;

@SuppressWarnings("all")
public class ELParser implements ELParserTreeConstants, ELParserConstants {/*@bgen(jjtree)*/
  protected JJTELParserState jjtree = new JJTELParserState();
    public static Node parse(String ref) throws ELException {
        try {
            return (new ELParser(new StringReader(ref))).CompositeExpression();
        } catch (ParseException pe) {
            throw new ELException(pe.getMessage());
        }
    }

/*
 * CompositeExpression
 * 允许最灵活的分析, 通过检查返回的节点类型限制
 */
  final public AstCompositeExpression CompositeExpression() throws ParseException {
  AstCompositeExpression jjtn000 = new AstCompositeExpression(JJTCOMPOSITEEXPRESSION);
  boolean jjtc000 = true;
  jjtree.openNodeScope(jjtn000);
    try {
      label_1:
      while (true) {
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case LITERAL_EXPRESSION:
        case START_DYNAMIC_EXPRESSION:
        case START_DEFERRED_EXPRESSION:
          ;
          break;
        default:
          jj_la1[0] = jj_gen;
          break label_1;
        }
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case START_DEFERRED_EXPRESSION:
          DeferredExpression();
          break;
        case START_DYNAMIC_EXPRESSION:
          DynamicExpression();
          break;
        case LITERAL_EXPRESSION:
          LiteralExpression();
          break;
        default:
          jj_la1[1] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
      }
      jj_consume_token(0);
                                   jjtree.closeNodeScope(jjtn000, true);
                                   jjtc000 = false;
                                   {if (true) return jjtn000;}
    } catch (Throwable jjte000) {
      if (jjtc000) {
        jjtree.clearNodeScope(jjtn000);
        jjtc000 = false;
      } else {
        jjtree.popNode();
      }
      if (jjte000 instanceof RuntimeException) {
        {if (true) throw (RuntimeException)jjte000;}
      }
      if (jjte000 instanceof ParseException) {
        {if (true) throw (ParseException)jjte000;}
      }
      {if (true) throw (Error)jjte000;}
    } finally {
      if (jjtc000) {
        jjtree.closeNodeScope(jjtn000, true);
      }
    }
    throw new Error("Missing return statement in function");
  }

/*
 * LiteralExpression
 * 非EL表达式块
 */
  final public void LiteralExpression() throws ParseException {
    AstLiteralExpression jjtn000 = new AstLiteralExpression(JJTLITERALEXPRESSION);
    boolean jjtc000 = true;
    jjtree.openNodeScope(jjtn000);Token t = null;
    try {
      t = jj_consume_token(LITERAL_EXPRESSION);
                             jjtree.closeNodeScope(jjtn000, true);
                             jjtc000 = false;
                             jjtn000.setImage(t.image);
    } finally {
      if (jjtc000) {
        jjtree.closeNodeScope(jjtn000, true);
      }
    }
  }

/*
 * DeferredExpression
 * #{...} Expressions
 */
  final public void DeferredExpression() throws ParseException {
                                                 /*@bgen(jjtree) DeferredExpression */
  AstDeferredExpression jjtn000 = new AstDeferredExpression(JJTDEFERREDEXPRESSION);
  boolean jjtc000 = true;
  jjtree.openNodeScope(jjtn000);
    try {
      jj_consume_token(START_DEFERRED_EXPRESSION);
      Expression();
      jj_consume_token(RBRACE);
    } catch (Throwable jjte000) {
      if (jjtc000) {
        jjtree.clearNodeScope(jjtn000);
        jjtc000 = false;
      } else {
        jjtree.popNode();
      }
      if (jjte000 instanceof RuntimeException) {
        {if (true) throw (RuntimeException)jjte000;}
      }
      if (jjte000 instanceof ParseException) {
        {if (true) throw (ParseException)jjte000;}
      }
      {if (true) throw (Error)jjte000;}
    } finally {
      if (jjtc000) {
        jjtree.closeNodeScope(jjtn000, true);
      }
    }
  }

/*
 * DynamicExpression
 * ${...} Expressions
 */
  final public void DynamicExpression() throws ParseException {
  AstDynamicExpression jjtn000 = new AstDynamicExpression(JJTDYNAMICEXPRESSION);
  boolean jjtc000 = true;
  jjtree.openNodeScope(jjtn000);
    try {
      jj_consume_token(START_DYNAMIC_EXPRESSION);
      Expression();
      jj_consume_token(RBRACE);
    } catch (Throwable jjte000) {
      if (jjtc000) {
        jjtree.clearNodeScope(jjtn000);
        jjtc000 = false;
      } else {
        jjtree.popNode();
      }
      if (jjte000 instanceof RuntimeException) {
        {if (true) throw (RuntimeException)jjte000;}
      }
      if (jjte000 instanceof ParseException) {
        {if (true) throw (ParseException)jjte000;}
      }
      {if (true) throw (Error)jjte000;}
    } finally {
      if (jjtc000) {
        jjtree.closeNodeScope(jjtn000, true);
      }
    }
  }

/*
 * Expression
 * EL Expression Language Root
 */
  final public void Expression() throws ParseException {
    Semicolon();
  }

/*
 * Semicolon
 */
  final public void Semicolon() throws ParseException {
    Assignment();
    label_2:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case SEMICOLON:
        ;
        break;
      default:
        jj_la1[2] = jj_gen;
        break label_2;
      }
      jj_consume_token(SEMICOLON);
                                 AstSemicolon jjtn001 = new AstSemicolon(JJTSEMICOLON);
                                 boolean jjtc001 = true;
                                 jjtree.openNodeScope(jjtn001);
      try {
        Assignment();
      } catch (Throwable jjte001) {
                                 if (jjtc001) {
                                   jjtree.clearNodeScope(jjtn001);
                                   jjtc001 = false;
                                 } else {
                                   jjtree.popNode();
                                 }
                                 if (jjte001 instanceof RuntimeException) {
                                   {if (true) throw (RuntimeException)jjte001;}
                                 }
                                 if (jjte001 instanceof ParseException) {
                                   {if (true) throw (ParseException)jjte001;}
                                 }
                                 {if (true) throw (Error)jjte001;}
      } finally {
                                 if (jjtc001) {
                                   jjtree.closeNodeScope(jjtn001,  2);
                                 }
      }
    }
  }

/*
 * Assignment
 */
  final public void Assignment() throws ParseException {
    if (jj_2_2(4)) {
      LambdaExpression();
    } else {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case START_SET_OR_MAP:
      case INTEGER_LITERAL:
      case FLOATING_POINT_LITERAL:
      case STRING_LITERAL:
      case TRUE:
      case FALSE:
      case NULL:
      case LPAREN:
      case LBRACK:
      case NOT0:
      case NOT1:
      case EMPTY:
      case MINUS:
      case IDENTIFIER:
        Choice();
        label_3:
        while (true) {
          if (jj_2_1(2)) {
            ;
          } else {
            break label_3;
          }
          jj_consume_token(ASSIGN);
                                       AstAssign jjtn001 = new AstAssign(JJTASSIGN);
                                       boolean jjtc001 = true;
                                       jjtree.openNodeScope(jjtn001);
          try {
            Assignment();
          } catch (Throwable jjte001) {
                                       if (jjtc001) {
                                         jjtree.clearNodeScope(jjtn001);
                                         jjtc001 = false;
                                       } else {
                                         jjtree.popNode();
                                       }
                                       if (jjte001 instanceof RuntimeException) {
                                         {if (true) throw (RuntimeException)jjte001;}
                                       }
                                       if (jjte001 instanceof ParseException) {
                                         {if (true) throw (ParseException)jjte001;}
                                       }
                                       {if (true) throw (Error)jjte001;}
          } finally {
                                       if (jjtc001) {
                                         jjtree.closeNodeScope(jjtn001,  2);
                                       }
          }
        }
        break;
      default:
        jj_la1[3] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
  }

/*
 * Lambda expression
 */
  final public void LambdaExpression() throws ParseException {
  AstLambdaExpression jjtn000 = new AstLambdaExpression(JJTLAMBDAEXPRESSION);
  boolean jjtc000 = true;
  jjtree.openNodeScope(jjtn000);
    try {
      LambdaParameters();
      jj_consume_token(ARROW);
      if (jj_2_3(3)) {
        LambdaExpression();
      } else {
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case START_SET_OR_MAP:
        case INTEGER_LITERAL:
        case FLOATING_POINT_LITERAL:
        case STRING_LITERAL:
        case TRUE:
        case FALSE:
        case NULL:
        case LPAREN:
        case LBRACK:
        case NOT0:
        case NOT1:
        case EMPTY:
        case MINUS:
        case IDENTIFIER:
          Choice();
          break;
        default:
          jj_la1[4] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
      }
    } catch (Throwable jjte000) {
      if (jjtc000) {
        jjtree.clearNodeScope(jjtn000);
        jjtc000 = false;
      } else {
        jjtree.popNode();
      }
      if (jjte000 instanceof RuntimeException) {
        {if (true) throw (RuntimeException)jjte000;}
      }
      if (jjte000 instanceof ParseException) {
        {if (true) throw (ParseException)jjte000;}
      }
      {if (true) throw (Error)jjte000;}
    } finally {
      if (jjtc000) {
        jjtree.closeNodeScope(jjtn000, true);
      }
    }
  }

/*
 * Lambda parameters
 */
  final public void LambdaParameters() throws ParseException {
  AstLambdaParameters jjtn000 = new AstLambdaParameters(JJTLAMBDAPARAMETERS);
  boolean jjtc000 = true;
  jjtree.openNodeScope(jjtn000);
    try {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case IDENTIFIER:
        Identifier();
        break;
      case LPAREN:
        jj_consume_token(LPAREN);
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case IDENTIFIER:
          Identifier();
          label_4:
          while (true) {
            switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
            case COMMA:
              ;
              break;
            default:
              jj_la1[5] = jj_gen;
              break label_4;
            }
            jj_consume_token(COMMA);
            Identifier();
          }
          break;
        default:
          jj_la1[6] = jj_gen;
          ;
        }
        jj_consume_token(RPAREN);
        break;
      default:
        jj_la1[7] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    } catch (Throwable jjte000) {
      if (jjtc000) {
        jjtree.clearNodeScope(jjtn000);
        jjtc000 = false;
      } else {
        jjtree.popNode();
      }
      if (jjte000 instanceof RuntimeException) {
        {if (true) throw (RuntimeException)jjte000;}
      }
      if (jjte000 instanceof ParseException) {
        {if (true) throw (ParseException)jjte000;}
      }
      {if (true) throw (Error)jjte000;}
    } finally {
      if (jjtc000) {
        jjtree.closeNodeScope(jjtn000, true);
      }
    }
  }

/*
 * lambda表达式的可能调用. 调用必须是括号，但括号并不意味着它是一个调用.
 */
  final public void LambdaExpressionOrInvocation() throws ParseException {
  AstLambdaExpression jjtn000 = new AstLambdaExpression(JJTLAMBDAEXPRESSION);
  boolean jjtc000 = true;
  jjtree.openNodeScope(jjtn000);
    try {
      jj_consume_token(LPAREN);
      LambdaParameters();
      jj_consume_token(ARROW);
      if (jj_2_4(3)) {
        LambdaExpression();
      } else {
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case START_SET_OR_MAP:
        case INTEGER_LITERAL:
        case FLOATING_POINT_LITERAL:
        case STRING_LITERAL:
        case TRUE:
        case FALSE:
        case NULL:
        case LPAREN:
        case LBRACK:
        case NOT0:
        case NOT1:
        case EMPTY:
        case MINUS:
        case IDENTIFIER:
          Choice();
          break;
        default:
          jj_la1[8] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
      }
      jj_consume_token(RPAREN);
      label_5:
      while (true) {
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case LPAREN:
          ;
          break;
        default:
          jj_la1[9] = jj_gen;
          break label_5;
        }
        MethodParameters();
      }
    } catch (Throwable jjte000) {
      if (jjtc000) {
        jjtree.clearNodeScope(jjtn000);
        jjtc000 = false;
      } else {
        jjtree.popNode();
      }
      if (jjte000 instanceof RuntimeException) {
        {if (true) throw (RuntimeException)jjte000;}
      }
      if (jjte000 instanceof ParseException) {
        {if (true) throw (ParseException)jjte000;}
      }
      {if (true) throw (Error)jjte000;}
    } finally {
      if (jjtc000) {
        jjtree.closeNodeScope(jjtn000, true);
      }
    }
  }

/*
 * Choice
 * For Choice markup a ? b : c, then Or
 */
  final public void Choice() throws ParseException {
    Or();
    label_6:
    while (true) {
      if (jj_2_5(3)) {
        ;
      } else {
        break label_6;
      }
      jj_consume_token(QUESTIONMARK);
      Choice();
      jj_consume_token(COLON);
                                                         AstChoice jjtn001 = new AstChoice(JJTCHOICE);
                                                         boolean jjtc001 = true;
                                                         jjtree.openNodeScope(jjtn001);
      try {
        Choice();
      } catch (Throwable jjte001) {
                                                         if (jjtc001) {
                                                           jjtree.clearNodeScope(jjtn001);
                                                           jjtc001 = false;
                                                         } else {
                                                           jjtree.popNode();
                                                         }
                                                         if (jjte001 instanceof RuntimeException) {
                                                           {if (true) throw (RuntimeException)jjte001;}
                                                         }
                                                         if (jjte001 instanceof ParseException) {
                                                           {if (true) throw (ParseException)jjte001;}
                                                         }
                                                         {if (true) throw (Error)jjte001;}
      } finally {
                                                         if (jjtc001) {
                                                           jjtree.closeNodeScope(jjtn001,  3);
                                                         }
      }
    }
  }

/*
 * Or
 * For 'or' '||', then And
 */
  final public void Or() throws ParseException {
    And();
    label_7:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case OR0:
      case OR1:
        ;
        break;
      default:
        jj_la1[10] = jj_gen;
        break label_7;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case OR0:
        jj_consume_token(OR0);
        break;
      case OR1:
        jj_consume_token(OR1);
        break;
      default:
        jj_la1[11] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
                           AstOr jjtn001 = new AstOr(JJTOR);
                           boolean jjtc001 = true;
                           jjtree.openNodeScope(jjtn001);
      try {
        And();
      } catch (Throwable jjte001) {
                           if (jjtc001) {
                             jjtree.clearNodeScope(jjtn001);
                             jjtc001 = false;
                           } else {
                             jjtree.popNode();
                           }
                           if (jjte001 instanceof RuntimeException) {
                             {if (true) throw (RuntimeException)jjte001;}
                           }
                           if (jjte001 instanceof ParseException) {
                             {if (true) throw (ParseException)jjte001;}
                           }
                           {if (true) throw (Error)jjte001;}
      } finally {
                           if (jjtc001) {
                             jjtree.closeNodeScope(jjtn001,  2);
                           }
      }
    }
  }

/*
 * And
 * For 'and' '&&', then Equality
 */
  final public void And() throws ParseException {
    Equality();
    label_8:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case AND0:
      case AND1:
        ;
        break;
      default:
        jj_la1[12] = jj_gen;
        break label_8;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case AND0:
        jj_consume_token(AND0);
        break;
      case AND1:
        jj_consume_token(AND1);
        break;
      default:
        jj_la1[13] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
                                  AstAnd jjtn001 = new AstAnd(JJTAND);
                                  boolean jjtc001 = true;
                                  jjtree.openNodeScope(jjtn001);
      try {
        Equality();
      } catch (Throwable jjte001) {
                                  if (jjtc001) {
                                    jjtree.clearNodeScope(jjtn001);
                                    jjtc001 = false;
                                  } else {
                                    jjtree.popNode();
                                  }
                                  if (jjte001 instanceof RuntimeException) {
                                    {if (true) throw (RuntimeException)jjte001;}
                                  }
                                  if (jjte001 instanceof ParseException) {
                                    {if (true) throw (ParseException)jjte001;}
                                  }
                                  {if (true) throw (Error)jjte001;}
      } finally {
                                  if (jjtc001) {
                                    jjtree.closeNodeScope(jjtn001,  2);
                                  }
      }
    }
  }

/*
 * Equality
 * For '==' 'eq' '!=' 'ne', then Compare
 */
  final public void Equality() throws ParseException {
    Compare();
    label_9:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case EQ0:
      case EQ1:
      case NE0:
      case NE1:
        ;
        break;
      default:
        jj_la1[14] = jj_gen;
        break label_9;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case EQ0:
      case EQ1:
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case EQ0:
          jj_consume_token(EQ0);
          break;
        case EQ1:
          jj_consume_token(EQ1);
          break;
        default:
          jj_la1[15] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
                         AstEqual jjtn001 = new AstEqual(JJTEQUAL);
                         boolean jjtc001 = true;
                         jjtree.openNodeScope(jjtn001);
        try {
          Compare();
        } catch (Throwable jjte001) {
                         if (jjtc001) {
                           jjtree.clearNodeScope(jjtn001);
                           jjtc001 = false;
                         } else {
                           jjtree.popNode();
                         }
                         if (jjte001 instanceof RuntimeException) {
                           {if (true) throw (RuntimeException)jjte001;}
                         }
                         if (jjte001 instanceof ParseException) {
                           {if (true) throw (ParseException)jjte001;}
                         }
                         {if (true) throw (Error)jjte001;}
        } finally {
                         if (jjtc001) {
                           jjtree.closeNodeScope(jjtn001,  2);
                         }
        }
        break;
      case NE0:
      case NE1:
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case NE0:
          jj_consume_token(NE0);
          break;
        case NE1:
          jj_consume_token(NE1);
          break;
        default:
          jj_la1[16] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
                         AstNotEqual jjtn002 = new AstNotEqual(JJTNOTEQUAL);
                         boolean jjtc002 = true;
                         jjtree.openNodeScope(jjtn002);
        try {
          Compare();
        } catch (Throwable jjte002) {
                         if (jjtc002) {
                           jjtree.clearNodeScope(jjtn002);
                           jjtc002 = false;
                         } else {
                           jjtree.popNode();
                         }
                         if (jjte002 instanceof RuntimeException) {
                           {if (true) throw (RuntimeException)jjte002;}
                         }
                         if (jjte002 instanceof ParseException) {
                           {if (true) throw (ParseException)jjte002;}
                         }
                         {if (true) throw (Error)jjte002;}
        } finally {
                         if (jjtc002) {
                           jjtree.closeNodeScope(jjtn002,  2);
                         }
        }
        break;
      default:
        jj_la1[17] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
  }

/*
 * Compare
 * For a bunch of them, then +=
 */
  final public void Compare() throws ParseException {
    Concatenation();
    label_10:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case GT0:
      case GT1:
      case LT0:
      case LT1:
      case GE0:
      case GE1:
      case LE0:
      case LE1:
        ;
        break;
      default:
        jj_la1[18] = jj_gen;
        break label_10;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case LT0:
      case LT1:
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case LT0:
          jj_consume_token(LT0);
          break;
        case LT1:
          jj_consume_token(LT1);
          break;
        default:
          jj_la1[19] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
                         AstLessThan jjtn001 = new AstLessThan(JJTLESSTHAN);
                         boolean jjtc001 = true;
                         jjtree.openNodeScope(jjtn001);
        try {
          Concatenation();
        } catch (Throwable jjte001) {
                         if (jjtc001) {
                           jjtree.clearNodeScope(jjtn001);
                           jjtc001 = false;
                         } else {
                           jjtree.popNode();
                         }
                         if (jjte001 instanceof RuntimeException) {
                           {if (true) throw (RuntimeException)jjte001;}
                         }
                         if (jjte001 instanceof ParseException) {
                           {if (true) throw (ParseException)jjte001;}
                         }
                         {if (true) throw (Error)jjte001;}
        } finally {
                         if (jjtc001) {
                           jjtree.closeNodeScope(jjtn001,  2);
                         }
        }
        break;
      case GT0:
      case GT1:
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case GT0:
          jj_consume_token(GT0);
          break;
        case GT1:
          jj_consume_token(GT1);
          break;
        default:
          jj_la1[20] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
                         AstGreaterThan jjtn002 = new AstGreaterThan(JJTGREATERTHAN);
                         boolean jjtc002 = true;
                         jjtree.openNodeScope(jjtn002);
        try {
          Concatenation();
        } catch (Throwable jjte002) {
                         if (jjtc002) {
                           jjtree.clearNodeScope(jjtn002);
                           jjtc002 = false;
                         } else {
                           jjtree.popNode();
                         }
                         if (jjte002 instanceof RuntimeException) {
                           {if (true) throw (RuntimeException)jjte002;}
                         }
                         if (jjte002 instanceof ParseException) {
                           {if (true) throw (ParseException)jjte002;}
                         }
                         {if (true) throw (Error)jjte002;}
        } finally {
                         if (jjtc002) {
                           jjtree.closeNodeScope(jjtn002,  2);
                         }
        }
        break;
      case LE0:
      case LE1:
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case LE0:
          jj_consume_token(LE0);
          break;
        case LE1:
          jj_consume_token(LE1);
          break;
        default:
          jj_la1[21] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
                         AstLessThanEqual jjtn003 = new AstLessThanEqual(JJTLESSTHANEQUAL);
                         boolean jjtc003 = true;
                         jjtree.openNodeScope(jjtn003);
        try {
          Concatenation();
        } catch (Throwable jjte003) {
                         if (jjtc003) {
                           jjtree.clearNodeScope(jjtn003);
                           jjtc003 = false;
                         } else {
                           jjtree.popNode();
                         }
                         if (jjte003 instanceof RuntimeException) {
                           {if (true) throw (RuntimeException)jjte003;}
                         }
                         if (jjte003 instanceof ParseException) {
                           {if (true) throw (ParseException)jjte003;}
                         }
                         {if (true) throw (Error)jjte003;}
        } finally {
                         if (jjtc003) {
                           jjtree.closeNodeScope(jjtn003,  2);
                         }
        }
        break;
      case GE0:
      case GE1:
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case GE0:
          jj_consume_token(GE0);
          break;
        case GE1:
          jj_consume_token(GE1);
          break;
        default:
          jj_la1[22] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
                         AstGreaterThanEqual jjtn004 = new AstGreaterThanEqual(JJTGREATERTHANEQUAL);
                         boolean jjtc004 = true;
                         jjtree.openNodeScope(jjtn004);
        try {
          Concatenation();
        } catch (Throwable jjte004) {
                         if (jjtc004) {
                           jjtree.clearNodeScope(jjtn004);
                           jjtc004 = false;
                         } else {
                           jjtree.popNode();
                         }
                         if (jjte004 instanceof RuntimeException) {
                           {if (true) throw (RuntimeException)jjte004;}
                         }
                         if (jjte004 instanceof ParseException) {
                           {if (true) throw (ParseException)jjte004;}
                         }
                         {if (true) throw (Error)jjte004;}
        } finally {
                         if (jjtc004) {
                           jjtree.closeNodeScope(jjtn004,  2);
                         }
        }
        break;
      default:
        jj_la1[23] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
  }

/*
 * Concatenation
 * For +=, then Math
 *
 */
  final public void Concatenation() throws ParseException {
    Math();
    label_11:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case CONCAT:
        ;
        break;
      default:
        jj_la1[24] = jj_gen;
        break label_11;
      }
      jj_consume_token(CONCAT);
                    AstConcatenation jjtn001 = new AstConcatenation(JJTCONCATENATION);
                    boolean jjtc001 = true;
                    jjtree.openNodeScope(jjtn001);
      try {
        Math();
      } catch (Throwable jjte001) {
                    if (jjtc001) {
                      jjtree.clearNodeScope(jjtn001);
                      jjtc001 = false;
                    } else {
                      jjtree.popNode();
                    }
                    if (jjte001 instanceof RuntimeException) {
                      {if (true) throw (RuntimeException)jjte001;}
                    }
                    if (jjte001 instanceof ParseException) {
                      {if (true) throw (ParseException)jjte001;}
                    }
                    {if (true) throw (Error)jjte001;}
      } finally {
                    if (jjtc001) {
                      jjtree.closeNodeScope(jjtn001,  2);
                    }
      }
    }
  }

/*
 * Math
 * For '+' '-', then Multiplication
 */
  final public void Math() throws ParseException {
    Multiplication();
    label_12:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case PLUS:
      case MINUS:
        ;
        break;
      default:
        jj_la1[25] = jj_gen;
        break label_12;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case PLUS:
        jj_consume_token(PLUS);
                  AstPlus jjtn001 = new AstPlus(JJTPLUS);
                  boolean jjtc001 = true;
                  jjtree.openNodeScope(jjtn001);
        try {
          Multiplication();
        } catch (Throwable jjte001) {
                  if (jjtc001) {
                    jjtree.clearNodeScope(jjtn001);
                    jjtc001 = false;
                  } else {
                    jjtree.popNode();
                  }
                  if (jjte001 instanceof RuntimeException) {
                    {if (true) throw (RuntimeException)jjte001;}
                  }
                  if (jjte001 instanceof ParseException) {
                    {if (true) throw (ParseException)jjte001;}
                  }
                  {if (true) throw (Error)jjte001;}
        } finally {
                  if (jjtc001) {
                    jjtree.closeNodeScope(jjtn001,  2);
                  }
        }
        break;
      case MINUS:
        jj_consume_token(MINUS);
                   AstMinus jjtn002 = new AstMinus(JJTMINUS);
                   boolean jjtc002 = true;
                   jjtree.openNodeScope(jjtn002);
        try {
          Multiplication();
        } catch (Throwable jjte002) {
                   if (jjtc002) {
                     jjtree.clearNodeScope(jjtn002);
                     jjtc002 = false;
                   } else {
                     jjtree.popNode();
                   }
                   if (jjte002 instanceof RuntimeException) {
                     {if (true) throw (RuntimeException)jjte002;}
                   }
                   if (jjte002 instanceof ParseException) {
                     {if (true) throw (ParseException)jjte002;}
                   }
                   {if (true) throw (Error)jjte002;}
        } finally {
                   if (jjtc002) {
                     jjtree.closeNodeScope(jjtn002,  2);
                   }
        }
        break;
      default:
        jj_la1[26] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
  }

/*
 * Multiplication
 * For a bunch of them, then Unary
 */
  final public void Multiplication() throws ParseException {
    Unary();
    label_13:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case MULT:
      case DIV0:
      case DIV1:
      case MOD0:
      case MOD1:
        ;
        break;
      default:
        jj_la1[27] = jj_gen;
        break label_13;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case MULT:
        jj_consume_token(MULT);
                  AstMult jjtn001 = new AstMult(JJTMULT);
                  boolean jjtc001 = true;
                  jjtree.openNodeScope(jjtn001);
        try {
          Unary();
        } catch (Throwable jjte001) {
                  if (jjtc001) {
                    jjtree.clearNodeScope(jjtn001);
                    jjtc001 = false;
                  } else {
                    jjtree.popNode();
                  }
                  if (jjte001 instanceof RuntimeException) {
                    {if (true) throw (RuntimeException)jjte001;}
                  }
                  if (jjte001 instanceof ParseException) {
                    {if (true) throw (ParseException)jjte001;}
                  }
                  {if (true) throw (Error)jjte001;}
        } finally {
                  if (jjtc001) {
                    jjtree.closeNodeScope(jjtn001,  2);
                  }
        }
        break;
      case DIV0:
      case DIV1:
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case DIV0:
          jj_consume_token(DIV0);
          break;
        case DIV1:
          jj_consume_token(DIV1);
          break;
        default:
          jj_la1[28] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
                           AstDiv jjtn002 = new AstDiv(JJTDIV);
                           boolean jjtc002 = true;
                           jjtree.openNodeScope(jjtn002);
        try {
          Unary();
        } catch (Throwable jjte002) {
                           if (jjtc002) {
                             jjtree.clearNodeScope(jjtn002);
                             jjtc002 = false;
                           } else {
                             jjtree.popNode();
                           }
                           if (jjte002 instanceof RuntimeException) {
                             {if (true) throw (RuntimeException)jjte002;}
                           }
                           if (jjte002 instanceof ParseException) {
                             {if (true) throw (ParseException)jjte002;}
                           }
                           {if (true) throw (Error)jjte002;}
        } finally {
                           if (jjtc002) {
                             jjtree.closeNodeScope(jjtn002,  2);
                           }
        }
        break;
      case MOD0:
      case MOD1:
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case MOD0:
          jj_consume_token(MOD0);
          break;
        case MOD1:
          jj_consume_token(MOD1);
          break;
        default:
          jj_la1[29] = jj_gen;
          jj_consume_token(-1);
          throw new ParseException();
        }
                           AstMod jjtn003 = new AstMod(JJTMOD);
                           boolean jjtc003 = true;
                           jjtree.openNodeScope(jjtn003);
        try {
          Unary();
        } catch (Throwable jjte003) {
                           if (jjtc003) {
                             jjtree.clearNodeScope(jjtn003);
                             jjtc003 = false;
                           } else {
                             jjtree.popNode();
                           }
                           if (jjte003 instanceof RuntimeException) {
                             {if (true) throw (RuntimeException)jjte003;}
                           }
                           if (jjte003 instanceof ParseException) {
                             {if (true) throw (ParseException)jjte003;}
                           }
                           {if (true) throw (Error)jjte003;}
        } finally {
                           if (jjtc003) {
                             jjtree.closeNodeScope(jjtn003,  2);
                           }
        }
        break;
      default:
        jj_la1[30] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
  }

/*
 * Unary
 * For '-' '!' 'not' 'empty', then Value
 */
  final public void Unary() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case MINUS:
      jj_consume_token(MINUS);
                  AstNegative jjtn001 = new AstNegative(JJTNEGATIVE);
                  boolean jjtc001 = true;
                  jjtree.openNodeScope(jjtn001);
      try {
        Unary();
      } catch (Throwable jjte001) {
                  if (jjtc001) {
                    jjtree.clearNodeScope(jjtn001);
                    jjtc001 = false;
                  } else {
                    jjtree.popNode();
                  }
                  if (jjte001 instanceof RuntimeException) {
                    {if (true) throw (RuntimeException)jjte001;}
                  }
                  if (jjte001 instanceof ParseException) {
                    {if (true) throw (ParseException)jjte001;}
                  }
                  {if (true) throw (Error)jjte001;}
      } finally {
                  if (jjtc001) {
                    jjtree.closeNodeScope(jjtn001, true);
                  }
      }
      break;
    case NOT0:
    case NOT1:
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case NOT0:
        jj_consume_token(NOT0);
        break;
      case NOT1:
        jj_consume_token(NOT1);
        break;
      default:
        jj_la1[31] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
                          AstNot jjtn002 = new AstNot(JJTNOT);
                          boolean jjtc002 = true;
                          jjtree.openNodeScope(jjtn002);
      try {
        Unary();
      } catch (Throwable jjte002) {
                          if (jjtc002) {
                            jjtree.clearNodeScope(jjtn002);
                            jjtc002 = false;
                          } else {
                            jjtree.popNode();
                          }
                          if (jjte002 instanceof RuntimeException) {
                            {if (true) throw (RuntimeException)jjte002;}
                          }
                          if (jjte002 instanceof ParseException) {
                            {if (true) throw (ParseException)jjte002;}
                          }
                          {if (true) throw (Error)jjte002;}
      } finally {
                          if (jjtc002) {
                            jjtree.closeNodeScope(jjtn002, true);
                          }
      }
      break;
    case EMPTY:
      jj_consume_token(EMPTY);
                  AstEmpty jjtn003 = new AstEmpty(JJTEMPTY);
                  boolean jjtc003 = true;
                  jjtree.openNodeScope(jjtn003);
      try {
        Unary();
      } catch (Throwable jjte003) {
                  if (jjtc003) {
                    jjtree.clearNodeScope(jjtn003);
                    jjtc003 = false;
                  } else {
                    jjtree.popNode();
                  }
                  if (jjte003 instanceof RuntimeException) {
                    {if (true) throw (RuntimeException)jjte003;}
                  }
                  if (jjte003 instanceof ParseException) {
                    {if (true) throw (ParseException)jjte003;}
                  }
                  {if (true) throw (Error)jjte003;}
      } finally {
                  if (jjtc003) {
                    jjtree.closeNodeScope(jjtn003, true);
                  }
      }
      break;
    case START_SET_OR_MAP:
    case INTEGER_LITERAL:
    case FLOATING_POINT_LITERAL:
    case STRING_LITERAL:
    case TRUE:
    case FALSE:
    case NULL:
    case LPAREN:
    case LBRACK:
    case IDENTIFIER:
      Value();
      break;
    default:
      jj_la1[32] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

/*
 * Value
 * Defines Prefix plus zero or more Suffixes
 */
  final public void Value() throws ParseException {
      AstValue jjtn001 = new AstValue(JJTVALUE);
      boolean jjtc001 = true;
      jjtree.openNodeScope(jjtn001);
    try {
      ValuePrefix();
      label_14:
      while (true) {
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case DOT:
        case LBRACK:
          ;
          break;
        default:
          jj_la1[33] = jj_gen;
          break label_14;
        }
        ValueSuffix();
      }
    } catch (Throwable jjte001) {
      if (jjtc001) {
        jjtree.clearNodeScope(jjtn001);
        jjtc001 = false;
      } else {
        jjtree.popNode();
      }
      if (jjte001 instanceof RuntimeException) {
        {if (true) throw (RuntimeException)jjte001;}
      }
      if (jjte001 instanceof ParseException) {
        {if (true) throw (ParseException)jjte001;}
      }
      {if (true) throw (Error)jjte001;}
    } finally {
      if (jjtc001) {
        jjtree.closeNodeScope(jjtn001, jjtree.nodeArity() > 1);
      }
    }
  }

/*
 * ValuePrefix
 * For Literals, Variables, and Functions
 */
  final public void ValuePrefix() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case INTEGER_LITERAL:
    case FLOATING_POINT_LITERAL:
    case STRING_LITERAL:
    case TRUE:
    case FALSE:
    case NULL:
      Literal();
      break;
    case START_SET_OR_MAP:
    case LPAREN:
    case LBRACK:
    case IDENTIFIER:
      NonLiteral();
      break;
    default:
      jj_la1[34] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

/*
 * ValueSuffix
 * Either dot or bracket notation
 */
  final public void ValueSuffix() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case DOT:
      DotSuffix();
      break;
    case LBRACK:
      BracketSuffix();
      break;
    default:
      jj_la1[35] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case LPAREN:
      MethodParameters();
      break;
    default:
      jj_la1[36] = jj_gen;
      ;
    }
  }

/*
 * DotSuffix
 * Dot Property
 */
  final public void DotSuffix() throws ParseException {
                               /*@bgen(jjtree) DotSuffix */
                                AstDotSuffix jjtn000 = new AstDotSuffix(JJTDOTSUFFIX);
                                boolean jjtc000 = true;
                                jjtree.openNodeScope(jjtn000);Token t = null;
    try {
      jj_consume_token(DOT);
      t = jj_consume_token(IDENTIFIER);
                           jjtree.closeNodeScope(jjtn000, true);
                           jjtc000 = false;
                           jjtn000.setImage(t.image);
    } finally {
      if (jjtc000) {
        jjtree.closeNodeScope(jjtn000, true);
      }
    }
  }

/*
 * BracketSuffix
 * Sub Expression Suffix
 */
  final public void BracketSuffix() throws ParseException {
                                       /*@bgen(jjtree) BracketSuffix */
  AstBracketSuffix jjtn000 = new AstBracketSuffix(JJTBRACKETSUFFIX);
  boolean jjtc000 = true;
  jjtree.openNodeScope(jjtn000);
    try {
      jj_consume_token(LBRACK);
      Expression();
      jj_consume_token(RBRACK);
    } catch (Throwable jjte000) {
      if (jjtc000) {
        jjtree.clearNodeScope(jjtn000);
        jjtc000 = false;
      } else {
        jjtree.popNode();
      }
      if (jjte000 instanceof RuntimeException) {
        {if (true) throw (RuntimeException)jjte000;}
      }
      if (jjte000 instanceof ParseException) {
        {if (true) throw (ParseException)jjte000;}
      }
      {if (true) throw (Error)jjte000;}
    } finally {
      if (jjtc000) {
        jjtree.closeNodeScope(jjtn000, true);
      }
    }
  }

/*
 * MethodParameters
 */
  final public void MethodParameters() throws ParseException {
                                             /*@bgen(jjtree) MethodParameters */
  AstMethodParameters jjtn000 = new AstMethodParameters(JJTMETHODPARAMETERS);
  boolean jjtc000 = true;
  jjtree.openNodeScope(jjtn000);
    try {
      jj_consume_token(LPAREN);
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case START_SET_OR_MAP:
      case INTEGER_LITERAL:
      case FLOATING_POINT_LITERAL:
      case STRING_LITERAL:
      case TRUE:
      case FALSE:
      case NULL:
      case LPAREN:
      case LBRACK:
      case NOT0:
      case NOT1:
      case EMPTY:
      case MINUS:
      case IDENTIFIER:
        Expression();
        label_15:
        while (true) {
          switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
          case COMMA:
            ;
            break;
          default:
            jj_la1[37] = jj_gen;
            break label_15;
          }
          jj_consume_token(COMMA);
          Expression();
        }
        break;
      default:
        jj_la1[38] = jj_gen;
        ;
      }
      jj_consume_token(RPAREN);
    } catch (Throwable jjte000) {
      if (jjtc000) {
        jjtree.clearNodeScope(jjtn000);
        jjtc000 = false;
      } else {
        jjtree.popNode();
      }
      if (jjte000 instanceof RuntimeException) {
        {if (true) throw (RuntimeException)jjte000;}
      }
      if (jjte000 instanceof ParseException) {
        {if (true) throw (ParseException)jjte000;}
      }
      {if (true) throw (Error)jjte000;}
    } finally {
      if (jjtc000) {
        jjtree.closeNodeScope(jjtn000, true);
      }
    }
  }

/*
 * NonLiteral
 * For Grouped Operations, Identifiers, and Functions
 */
  final public void NonLiteral() throws ParseException {
    if (jj_2_6(5)) {
      LambdaExpressionOrInvocation();
    } else {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case LPAREN:
        jj_consume_token(LPAREN);
        Expression();
        jj_consume_token(RPAREN);
        break;
      default:
        jj_la1[39] = jj_gen;
        if (jj_2_7(2147483647)) {
          Function();
        } else {
          switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
          case IDENTIFIER:
            Identifier();
            break;
          default:
            jj_la1[40] = jj_gen;
            if (jj_2_8(3)) {
              SetData();
            } else {
              switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
              case LBRACK:
                ListData();
                break;
              case START_SET_OR_MAP:
                MapData();
                break;
              default:
                jj_la1[41] = jj_gen;
                jj_consume_token(-1);
                throw new ParseException();
              }
            }
          }
        }
      }
    }
  }

/*
 * Note that both an empty Set and an empty Map are represented by {}. The
 * parser will always parse {} as an empty Set and special handling is required
 * to convert it to an empty Map when appropriate.
 */
  final public void SetData() throws ParseException {
                          /*@bgen(jjtree) SetData */
  AstSetData jjtn000 = new AstSetData(JJTSETDATA);
  boolean jjtc000 = true;
  jjtree.openNodeScope(jjtn000);
    try {
      jj_consume_token(START_SET_OR_MAP);
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case START_SET_OR_MAP:
      case INTEGER_LITERAL:
      case FLOATING_POINT_LITERAL:
      case STRING_LITERAL:
      case TRUE:
      case FALSE:
      case NULL:
      case LPAREN:
      case LBRACK:
      case NOT0:
      case NOT1:
      case EMPTY:
      case MINUS:
      case IDENTIFIER:
        Expression();
        label_16:
        while (true) {
          switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
          case COMMA:
            ;
            break;
          default:
            jj_la1[42] = jj_gen;
            break label_16;
          }
          jj_consume_token(COMMA);
          Expression();
        }
        break;
      default:
        jj_la1[43] = jj_gen;
        ;
      }
      jj_consume_token(RBRACE);
    } catch (Throwable jjte000) {
      if (jjtc000) {
        jjtree.clearNodeScope(jjtn000);
        jjtc000 = false;
      } else {
        jjtree.popNode();
      }
      if (jjte000 instanceof RuntimeException) {
        {if (true) throw (RuntimeException)jjte000;}
      }
      if (jjte000 instanceof ParseException) {
        {if (true) throw (ParseException)jjte000;}
      }
      {if (true) throw (Error)jjte000;}
    } finally {
      if (jjtc000) {
        jjtree.closeNodeScope(jjtn000, true);
      }
    }
  }

  final public void ListData() throws ParseException {
                            /*@bgen(jjtree) ListData */
  AstListData jjtn000 = new AstListData(JJTLISTDATA);
  boolean jjtc000 = true;
  jjtree.openNodeScope(jjtn000);
    try {
      jj_consume_token(LBRACK);
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case START_SET_OR_MAP:
      case INTEGER_LITERAL:
      case FLOATING_POINT_LITERAL:
      case STRING_LITERAL:
      case TRUE:
      case FALSE:
      case NULL:
      case LPAREN:
      case LBRACK:
      case NOT0:
      case NOT1:
      case EMPTY:
      case MINUS:
      case IDENTIFIER:
        Expression();
        label_17:
        while (true) {
          switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
          case COMMA:
            ;
            break;
          default:
            jj_la1[44] = jj_gen;
            break label_17;
          }
          jj_consume_token(COMMA);
          Expression();
        }
        break;
      default:
        jj_la1[45] = jj_gen;
        ;
      }
      jj_consume_token(RBRACK);
    } catch (Throwable jjte000) {
      if (jjtc000) {
        jjtree.clearNodeScope(jjtn000);
        jjtc000 = false;
      } else {
        jjtree.popNode();
      }
      if (jjte000 instanceof RuntimeException) {
        {if (true) throw (RuntimeException)jjte000;}
      }
      if (jjte000 instanceof ParseException) {
        {if (true) throw (ParseException)jjte000;}
      }
      {if (true) throw (Error)jjte000;}
    } finally {
      if (jjtc000) {
        jjtree.closeNodeScope(jjtn000, true);
      }
    }
  }

/*
 * Note that both an empty Set and an empty Map are represented by {}. The
 * parser will always parse {} as an empty Set and special handling is required
 * to convert it to an empty Map when appropriate.
 */
  final public void MapData() throws ParseException {
                          /*@bgen(jjtree) MapData */
  AstMapData jjtn000 = new AstMapData(JJTMAPDATA);
  boolean jjtc000 = true;
  jjtree.openNodeScope(jjtn000);
    try {
      jj_consume_token(START_SET_OR_MAP);
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case START_SET_OR_MAP:
      case INTEGER_LITERAL:
      case FLOATING_POINT_LITERAL:
      case STRING_LITERAL:
      case TRUE:
      case FALSE:
      case NULL:
      case LPAREN:
      case LBRACK:
      case NOT0:
      case NOT1:
      case EMPTY:
      case MINUS:
      case IDENTIFIER:
        MapEntry();
        label_18:
        while (true) {
          switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
          case COMMA:
            ;
            break;
          default:
            jj_la1[46] = jj_gen;
            break label_18;
          }
          jj_consume_token(COMMA);
          MapEntry();
        }
        break;
      default:
        jj_la1[47] = jj_gen;
        ;
      }
      jj_consume_token(RBRACE);
    } catch (Throwable jjte000) {
      if (jjtc000) {
        jjtree.clearNodeScope(jjtn000);
        jjtc000 = false;
      } else {
        jjtree.popNode();
      }
      if (jjte000 instanceof RuntimeException) {
        {if (true) throw (RuntimeException)jjte000;}
      }
      if (jjte000 instanceof ParseException) {
        {if (true) throw (ParseException)jjte000;}
      }
      {if (true) throw (Error)jjte000;}
    } finally {
      if (jjtc000) {
        jjtree.closeNodeScope(jjtn000, true);
      }
    }
  }

  final public void MapEntry() throws ParseException {
                            /*@bgen(jjtree) MapEntry */
  AstMapEntry jjtn000 = new AstMapEntry(JJTMAPENTRY);
  boolean jjtc000 = true;
  jjtree.openNodeScope(jjtn000);
    try {
      Expression();
      jj_consume_token(COLON);
      Expression();
    } catch (Throwable jjte000) {
      if (jjtc000) {
        jjtree.clearNodeScope(jjtn000);
        jjtc000 = false;
      } else {
        jjtree.popNode();
      }
      if (jjte000 instanceof RuntimeException) {
        {if (true) throw (RuntimeException)jjte000;}
      }
      if (jjte000 instanceof ParseException) {
        {if (true) throw (ParseException)jjte000;}
      }
      {if (true) throw (Error)jjte000;}
    } finally {
      if (jjtc000) {
        jjtree.closeNodeScope(jjtn000, true);
      }
    }
  }

/*
 * Identifier
 * Java Language Identifier
 */
  final public void Identifier() throws ParseException {
                                 /*@bgen(jjtree) Identifier */
                                  AstIdentifier jjtn000 = new AstIdentifier(JJTIDENTIFIER);
                                  boolean jjtc000 = true;
                                  jjtree.openNodeScope(jjtn000);Token t = null;
    try {
      t = jj_consume_token(IDENTIFIER);
                     jjtree.closeNodeScope(jjtn000, true);
                     jjtc000 = false;
                     jjtn000.setImage(t.image);
    } finally {
      if (jjtc000) {
        jjtree.closeNodeScope(jjtn000, true);
      }
    }
  }

/*
 * Function
 * Namespace:Name(a,b,c)
 */
  final public void Function() throws ParseException {
 /*@bgen(jjtree) Function */
    AstFunction jjtn000 = new AstFunction(JJTFUNCTION);
    boolean jjtc000 = true;
    jjtree.openNodeScope(jjtn000);Token t0 = null;
    Token t1 = null;
    try {
      t0 = jj_consume_token(IDENTIFIER);
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case COLON:
        jj_consume_token(COLON);
        t1 = jj_consume_token(IDENTIFIER);
        break;
      default:
        jj_la1[48] = jj_gen;
        ;
      }
        if (t1 != null) {
            jjtn000.setPrefix(t0.image);
            jjtn000.setLocalName(t1.image);
        } else {
            jjtn000.setLocalName(t0.image);
        }
      label_19:
      while (true) {
        MethodParameters();
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case LPAREN:
          ;
          break;
        default:
          jj_la1[49] = jj_gen;
          break label_19;
        }
      }
    } catch (Throwable jjte000) {
      if (jjtc000) {
        jjtree.clearNodeScope(jjtn000);
        jjtc000 = false;
      } else {
        jjtree.popNode();
      }
      if (jjte000 instanceof RuntimeException) {
        {if (true) throw (RuntimeException)jjte000;}
      }
      if (jjte000 instanceof ParseException) {
        {if (true) throw (ParseException)jjte000;}
      }
      {if (true) throw (Error)jjte000;}
    } finally {
      if (jjtc000) {
        jjtree.closeNodeScope(jjtn000, true);
      }
    }
  }

/*
 * Literal
 * Reserved Keywords
 */
  final public void Literal() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case TRUE:
    case FALSE:
      Boolean();
      break;
    case FLOATING_POINT_LITERAL:
      FloatingPoint();
      break;
    case INTEGER_LITERAL:
      Integer();
      break;
    case STRING_LITERAL:
      String();
      break;
    case NULL:
      Null();
      break;
    default:
      jj_la1[50] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

/*
 * Boolean
 * For 'true' 'false'
 */
  final public void Boolean() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case TRUE:
      AstTrue jjtn001 = new AstTrue(JJTTRUE);
      boolean jjtc001 = true;
      jjtree.openNodeScope(jjtn001);
      try {
        jj_consume_token(TRUE);
      } finally {
      if (jjtc001) {
        jjtree.closeNodeScope(jjtn001, true);
      }
      }
      break;
    case FALSE:
        AstFalse jjtn002 = new AstFalse(JJTFALSE);
        boolean jjtc002 = true;
        jjtree.openNodeScope(jjtn002);
      try {
        jj_consume_token(FALSE);
      } finally {
        if (jjtc002) {
          jjtree.closeNodeScope(jjtn002, true);
        }
      }
      break;
    default:
      jj_la1[51] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

/*
 * FloatingPoint
 * For Decimal and Floating Point Literals
 */
  final public void FloatingPoint() throws ParseException {
                                       /*@bgen(jjtree) FloatingPoint */
                                        AstFloatingPoint jjtn000 = new AstFloatingPoint(JJTFLOATINGPOINT);
                                        boolean jjtc000 = true;
                                        jjtree.openNodeScope(jjtn000);Token t = null;
    try {
      t = jj_consume_token(FLOATING_POINT_LITERAL);
                                 jjtree.closeNodeScope(jjtn000, true);
                                 jjtc000 = false;
                                 jjtn000.setImage(t.image);
    } finally {
      if (jjtc000) {
        jjtree.closeNodeScope(jjtn000, true);
      }
    }
  }

/*
 * Integer
 * For Simple Numeric Literals
 */
  final public void Integer() throws ParseException {
                           /*@bgen(jjtree) Integer */
                            AstInteger jjtn000 = new AstInteger(JJTINTEGER);
                            boolean jjtc000 = true;
                            jjtree.openNodeScope(jjtn000);Token t = null;
    try {
      t = jj_consume_token(INTEGER_LITERAL);
                          jjtree.closeNodeScope(jjtn000, true);
                          jjtc000 = false;
                          jjtn000.setImage(t.image);
    } finally {
      if (jjtc000) {
        jjtree.closeNodeScope(jjtn000, true);
      }
    }
  }

/*
 * String
 * For Quoted Literals
 */
  final public void String() throws ParseException {
                         /*@bgen(jjtree) String */
                          AstString jjtn000 = new AstString(JJTSTRING);
                          boolean jjtc000 = true;
                          jjtree.openNodeScope(jjtn000);Token t = null;
    try {
      t = jj_consume_token(STRING_LITERAL);
                         jjtree.closeNodeScope(jjtn000, true);
                         jjtc000 = false;
                         jjtn000.setImage(t.image);
    } finally {
      if (jjtc000) {
        jjtree.closeNodeScope(jjtn000, true);
      }
    }
  }

/*
 * Null
 * For 'null'
 */
  final public void Null() throws ParseException {
                     /*@bgen(jjtree) Null */
  AstNull jjtn000 = new AstNull(JJTNULL);
  boolean jjtc000 = true;
  jjtree.openNodeScope(jjtn000);
    try {
      jj_consume_token(NULL);
    } finally {
      if (jjtc000) {
        jjtree.closeNodeScope(jjtn000, true);
      }
    }
  }

  private boolean jj_2_1(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_1(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(0, xla); }
  }

  private boolean jj_2_2(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_2(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(1, xla); }
  }

  private boolean jj_2_3(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_3(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(2, xla); }
  }

  private boolean jj_2_4(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_4(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(3, xla); }
  }

  private boolean jj_2_5(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_5(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(4, xla); }
  }

  private boolean jj_2_6(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_6(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(5, xla); }
  }

  private boolean jj_2_7(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_7(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(6, xla); }
  }

  private boolean jj_2_8(int xla) {
    jj_la = xla; jj_lastpos = jj_scanpos = token;
    try { return !jj_3_8(); }
    catch(LookaheadSuccess ls) { return true; }
    finally { jj_save(7, xla); }
  }

  private boolean jj_3R_41() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_scan_token(39)) {
    jj_scanpos = xsp;
    if (jj_scan_token(40)) return true;
    }
    return false;
  }

  private boolean jj_3R_30() {
    if (jj_3R_22()) return true;
    return false;
  }

  private boolean jj_3R_40() {
    if (jj_3R_44()) return true;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_45()) { jj_scanpos = xsp; break; }
    }
    return false;
  }

  private boolean jj_3R_107() {
    if (jj_3R_36()) return true;
    return false;
  }

  private boolean jj_3R_105() {
    if (jj_3R_107()) return true;
    return false;
  }

  private boolean jj_3R_43() {
    if (jj_scan_token(COMMA)) return true;
    if (jj_3R_38()) return true;
    return false;
  }

  private boolean jj_3R_34() {
    if (jj_3R_40()) return true;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_41()) { jj_scanpos = xsp; break; }
    }
    return false;
  }

  private boolean jj_3R_37() {
    if (jj_scan_token(COMMA)) return true;
    return false;
  }

  private boolean jj_3R_35() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_scan_token(41)) {
    jj_scanpos = xsp;
    if (jj_scan_token(42)) return true;
    }
    return false;
  }

  private boolean jj_3R_99() {
    if (jj_scan_token(START_SET_OR_MAP)) return true;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_105()) jj_scanpos = xsp;
    if (jj_scan_token(RBRACE)) return true;
    return false;
  }

  private boolean jj_3R_104() {
    if (jj_3R_36()) return true;
    return false;
  }

  private boolean jj_3R_29() {
    if (jj_3R_34()) return true;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_35()) { jj_scanpos = xsp; break; }
    }
    return false;
  }

  private boolean jj_3_5() {
    if (jj_scan_token(QUESTIONMARK)) return true;
    if (jj_3R_22()) return true;
    if (jj_scan_token(COLON)) return true;
    return false;
  }

  private boolean jj_3R_98() {
    if (jj_scan_token(LBRACK)) return true;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_104()) jj_scanpos = xsp;
    if (jj_scan_token(RBRACK)) return true;
    return false;
  }

  private boolean jj_3R_39() {
    if (jj_3R_38()) return true;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_43()) { jj_scanpos = xsp; break; }
    }
    return false;
  }

  private boolean jj_3R_31() {
    if (jj_3R_36()) return true;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_37()) { jj_scanpos = xsp; break; }
    }
    return false;
  }

  private boolean jj_3R_22() {
    if (jj_3R_29()) return true;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_5()) { jj_scanpos = xsp; break; }
    }
    return false;
  }

  private boolean jj_3_3() {
    if (jj_3R_21()) return true;
    return false;
  }

  private boolean jj_3R_25() {
    if (jj_scan_token(START_SET_OR_MAP)) return true;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_31()) jj_scanpos = xsp;
    if (jj_scan_token(RBRACE)) return true;
    return false;
  }

  private boolean jj_3_4() {
    if (jj_3R_21()) return true;
    return false;
  }

  private boolean jj_3R_24() {
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_scan_token(COLON)) return true;
    return false;
  }

  private boolean jj_3_7() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_24()) jj_scanpos = xsp;
    if (jj_scan_token(IDENTIFIER)) return true;
    if (jj_scan_token(LPAREN)) return true;
    return false;
  }

  private boolean jj_3R_33() {
    if (jj_scan_token(LPAREN)) return true;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_39()) jj_scanpos = xsp;
    if (jj_scan_token(RPAREN)) return true;
    return false;
  }

  private boolean jj_3R_89() {
    if (jj_3R_99()) return true;
    return false;
  }

  private boolean jj_3R_88() {
    if (jj_3R_98()) return true;
    return false;
  }

  private boolean jj_3R_23() {
    if (jj_scan_token(LPAREN)) return true;
    if (jj_3R_27()) return true;
    if (jj_scan_token(ARROW)) return true;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3_4()) {
    jj_scanpos = xsp;
    if (jj_3R_30()) return true;
    }
    if (jj_scan_token(RPAREN)) return true;
    return false;
  }

  private boolean jj_3_8() {
    if (jj_3R_25()) return true;
    return false;
  }

  private boolean jj_3R_87() {
    if (jj_3R_38()) return true;
    return false;
  }

  private boolean jj_3R_86() {
    if (jj_3R_97()) return true;
    return false;
  }

  private boolean jj_3R_85() {
    if (jj_scan_token(LPAREN)) return true;
    if (jj_3R_36()) return true;
    return false;
  }

  private boolean jj_3_6() {
    if (jj_3R_23()) return true;
    return false;
  }

  private boolean jj_3R_77() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3_6()) {
    jj_scanpos = xsp;
    if (jj_3R_85()) {
    jj_scanpos = xsp;
    if (jj_3R_86()) {
    jj_scanpos = xsp;
    if (jj_3R_87()) {
    jj_scanpos = xsp;
    if (jj_3_8()) {
    jj_scanpos = xsp;
    if (jj_3R_88()) {
    jj_scanpos = xsp;
    if (jj_3R_89()) return true;
    }
    }
    }
    }
    }
    }
    return false;
  }

  private boolean jj_3R_32() {
    if (jj_3R_38()) return true;
    return false;
  }

  private boolean jj_3R_27() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_32()) {
    jj_scanpos = xsp;
    if (jj_3R_33()) return true;
    }
    return false;
  }

  private boolean jj_3_1() {
    if (jj_scan_token(ASSIGN)) return true;
    if (jj_3R_20()) return true;
    return false;
  }

  private boolean jj_3R_106() {
    if (jj_scan_token(LPAREN)) return true;
    return false;
  }

  private boolean jj_3R_21() {
    if (jj_3R_27()) return true;
    if (jj_scan_token(ARROW)) return true;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3_3()) {
    jj_scanpos = xsp;
    if (jj_3R_28()) return true;
    }
    return false;
  }

  private boolean jj_3R_46() {
    if (jj_scan_token(SEMICOLON)) return true;
    return false;
  }

  private boolean jj_3R_91() {
    if (jj_scan_token(LBRACK)) return true;
    return false;
  }

  private boolean jj_3R_26() {
    if (jj_3R_22()) return true;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3_1()) { jj_scanpos = xsp; break; }
    }
    return false;
  }

  private boolean jj_3R_79() {
    if (jj_3R_91()) return true;
    return false;
  }

  private boolean jj_3_2() {
    if (jj_3R_21()) return true;
    return false;
  }

  private boolean jj_3R_20() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3_2()) {
    jj_scanpos = xsp;
    if (jj_3R_26()) return true;
    }
    return false;
  }

  private boolean jj_3R_90() {
    if (jj_scan_token(DOT)) return true;
    return false;
  }

  private boolean jj_3R_42() {
    if (jj_3R_20()) return true;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_46()) { jj_scanpos = xsp; break; }
    }
    return false;
  }

  private boolean jj_3R_78() {
    if (jj_3R_90()) return true;
    return false;
  }

  private boolean jj_3R_75() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_78()) {
    jj_scanpos = xsp;
    if (jj_3R_79()) return true;
    }
    return false;
  }

  private boolean jj_3R_36() {
    if (jj_3R_42()) return true;
    return false;
  }

  private boolean jj_3R_72() {
    if (jj_3R_75()) return true;
    return false;
  }

  private boolean jj_3R_74() {
    if (jj_3R_77()) return true;
    return false;
  }

  private boolean jj_3R_71() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_73()) {
    jj_scanpos = xsp;
    if (jj_3R_74()) return true;
    }
    return false;
  }

  private boolean jj_3R_73() {
    if (jj_3R_76()) return true;
    return false;
  }

  private boolean jj_3R_70() {
    if (jj_3R_71()) return true;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_72()) { jj_scanpos = xsp; break; }
    }
    return false;
  }

  private boolean jj_3R_96() {
    if (jj_scan_token(NULL)) return true;
    return false;
  }

  private boolean jj_3R_66() {
    if (jj_3R_70()) return true;
    return false;
  }

  private boolean jj_3R_65() {
    if (jj_scan_token(EMPTY)) return true;
    if (jj_3R_59()) return true;
    return false;
  }

  private boolean jj_3R_64() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_scan_token(37)) {
    jj_scanpos = xsp;
    if (jj_scan_token(38)) return true;
    }
    if (jj_3R_59()) return true;
    return false;
  }

  private boolean jj_3R_59() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_63()) {
    jj_scanpos = xsp;
    if (jj_3R_64()) {
    jj_scanpos = xsp;
    if (jj_3R_65()) {
    jj_scanpos = xsp;
    if (jj_3R_66()) return true;
    }
    }
    }
    return false;
  }

  private boolean jj_3R_63() {
    if (jj_scan_token(MINUS)) return true;
    if (jj_3R_59()) return true;
    return false;
  }

  private boolean jj_3R_95() {
    if (jj_scan_token(STRING_LITERAL)) return true;
    return false;
  }

  private boolean jj_3R_69() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_scan_token(51)) {
    jj_scanpos = xsp;
    if (jj_scan_token(52)) return true;
    }
    return false;
  }

  private boolean jj_3R_94() {
    if (jj_scan_token(INTEGER_LITERAL)) return true;
    return false;
  }

  private boolean jj_3R_68() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_scan_token(49)) {
    jj_scanpos = xsp;
    if (jj_scan_token(50)) return true;
    }
    return false;
  }

  private boolean jj_3R_60() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_67()) {
    jj_scanpos = xsp;
    if (jj_3R_68()) {
    jj_scanpos = xsp;
    if (jj_3R_69()) return true;
    }
    }
    return false;
  }

  private boolean jj_3R_67() {
    if (jj_scan_token(MULT)) return true;
    return false;
  }

  private boolean jj_3R_57() {
    if (jj_3R_59()) return true;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_60()) { jj_scanpos = xsp; break; }
    }
    return false;
  }

  private boolean jj_3R_93() {
    if (jj_scan_token(FLOATING_POINT_LITERAL)) return true;
    return false;
  }

  private boolean jj_3R_62() {
    if (jj_scan_token(MINUS)) return true;
    return false;
  }

  private boolean jj_3R_101() {
    if (jj_scan_token(FALSE)) return true;
    return false;
  }

  private boolean jj_3R_58() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_61()) {
    jj_scanpos = xsp;
    if (jj_3R_62()) return true;
    }
    return false;
  }

  private boolean jj_3R_61() {
    if (jj_scan_token(PLUS)) return true;
    return false;
  }

  private boolean jj_3R_100() {
    if (jj_scan_token(TRUE)) return true;
    return false;
  }

  private boolean jj_3R_92() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_100()) {
    jj_scanpos = xsp;
    if (jj_3R_101()) return true;
    }
    return false;
  }

  private boolean jj_3R_51() {
    if (jj_3R_57()) return true;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_58()) { jj_scanpos = xsp; break; }
    }
    return false;
  }

  private boolean jj_3R_84() {
    if (jj_3R_96()) return true;
    return false;
  }

  private boolean jj_3R_83() {
    if (jj_3R_95()) return true;
    return false;
  }

  private boolean jj_3R_52() {
    if (jj_scan_token(CONCAT)) return true;
    return false;
  }

  private boolean jj_3R_82() {
    if (jj_3R_94()) return true;
    return false;
  }

  private boolean jj_3R_81() {
    if (jj_3R_93()) return true;
    return false;
  }

  private boolean jj_3R_102() {
    if (jj_scan_token(COLON)) return true;
    return false;
  }

  private boolean jj_3R_76() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_80()) {
    jj_scanpos = xsp;
    if (jj_3R_81()) {
    jj_scanpos = xsp;
    if (jj_3R_82()) {
    jj_scanpos = xsp;
    if (jj_3R_83()) {
    jj_scanpos = xsp;
    if (jj_3R_84()) return true;
    }
    }
    }
    }
    return false;
  }

  private boolean jj_3R_80() {
    if (jj_3R_92()) return true;
    return false;
  }

  private boolean jj_3R_47() {
    if (jj_3R_51()) return true;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_52()) { jj_scanpos = xsp; break; }
    }
    return false;
  }

  private boolean jj_3R_103() {
    if (jj_3R_106()) return true;
    return false;
  }

  private boolean jj_3R_56() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_scan_token(29)) {
    jj_scanpos = xsp;
    if (jj_scan_token(30)) return true;
    }
    return false;
  }

  private boolean jj_3R_55() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_scan_token(31)) {
    jj_scanpos = xsp;
    if (jj_scan_token(32)) return true;
    }
    return false;
  }

  private boolean jj_3R_54() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_scan_token(25)) {
    jj_scanpos = xsp;
    if (jj_scan_token(26)) return true;
    }
    return false;
  }

  private boolean jj_3R_48() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_53()) {
    jj_scanpos = xsp;
    if (jj_3R_54()) {
    jj_scanpos = xsp;
    if (jj_3R_55()) {
    jj_scanpos = xsp;
    if (jj_3R_56()) return true;
    }
    }
    }
    return false;
  }

  private boolean jj_3R_53() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_scan_token(27)) {
    jj_scanpos = xsp;
    if (jj_scan_token(28)) return true;
    }
    return false;
  }

  private boolean jj_3R_97() {
    if (jj_scan_token(IDENTIFIER)) return true;
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_102()) jj_scanpos = xsp;
    if (jj_3R_103()) return true;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_103()) { jj_scanpos = xsp; break; }
    }
    return false;
  }

  private boolean jj_3R_44() {
    if (jj_3R_47()) return true;
    Token xsp;
    while (true) {
      xsp = jj_scanpos;
      if (jj_3R_48()) { jj_scanpos = xsp; break; }
    }
    return false;
  }

  private boolean jj_3R_50() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_scan_token(35)) {
    jj_scanpos = xsp;
    if (jj_scan_token(36)) return true;
    }
    return false;
  }

  private boolean jj_3R_45() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_3R_49()) {
    jj_scanpos = xsp;
    if (jj_3R_50()) return true;
    }
    return false;
  }

  private boolean jj_3R_49() {
    Token xsp;
    xsp = jj_scanpos;
    if (jj_scan_token(33)) {
    jj_scanpos = xsp;
    if (jj_scan_token(34)) return true;
    }
    return false;
  }

  private boolean jj_3R_28() {
    if (jj_3R_22()) return true;
    return false;
  }

  private boolean jj_3R_38() {
    if (jj_scan_token(IDENTIFIER)) return true;
    return false;
  }

  /** Generated Token Manager. */
  public ELParserTokenManager token_source;
  SimpleCharStream jj_input_stream;
  /** Current token. */
  public Token token;
  /** Next token. */
  public Token jj_nt;
  private int jj_ntk;
  private Token jj_scanpos, jj_lastpos;
  private int jj_la;
  private int jj_gen;
  final private int[] jj_la1 = new int[52];
  static private int[] jj_la1_0;
  static private int[] jj_la1_1;
  static {
      jj_la1_init_0();
      jj_la1_init_1();
   }
   private static void jj_la1_init_0() {
      jj_la1_0 = new int[] {0xe,0xe,0x800000,0x15ed00,0x15ed00,0x1000000,0x0,0x40000,0x15ed00,0x40000,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0xfe000000,0x18000000,0x6000000,0x80000000,0x60000000,0xfe000000,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x15ed00,0x120000,0x15ed00,0x120000,0x40000,0x1000000,0x15ed00,0x40000,0x0,0x100100,0x1000000,0x15ed00,0x1000000,0x15ed00,0x1000000,0x15ed00,0x400000,0x40000,0x1ec00,0xc000,};
   }
   private static void jj_la1_init_1() {
      jj_la1_1 = new int[] {0x0,0x0,0x0,0x1008860,0x1008860,0x0,0x1000000,0x1000000,0x1008860,0x0,0x600,0x600,0x180,0x180,0x1e,0x6,0x18,0x1e,0x1,0x0,0x0,0x1,0x0,0x1,0x200000,0xc000,0xc000,0x1e2000,0x60000,0x180000,0x1e2000,0x60,0x1008860,0x0,0x1000000,0x0,0x0,0x0,0x1008860,0x0,0x1000000,0x0,0x0,0x1008860,0x0,0x1008860,0x0,0x1008860,0x0,0x0,0x0,0x0,};
   }
  final private JJCalls[] jj_2_rtns = new JJCalls[8];
  private boolean jj_rescan = false;
  private int jj_gc = 0;

  /** Constructor with InputStream. */
  public ELParser(java.io.InputStream stream) {
     this(stream, null);
  }
  /** Constructor with InputStream and supplied encoding */
  public ELParser(java.io.InputStream stream, String encoding) {
    try { jj_input_stream = new SimpleCharStream(stream, encoding, 1, 1); } catch(java.io.UnsupportedEncodingException e) { throw new RuntimeException(e); }
    token_source = new ELParserTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 52; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  /** Reinitialise. */
  public void ReInit(java.io.InputStream stream) {
     ReInit(stream, null);
  }
  /** Reinitialise. */
  public void ReInit(java.io.InputStream stream, String encoding) {
    try { jj_input_stream.ReInit(stream, encoding, 1, 1); } catch(java.io.UnsupportedEncodingException e) { throw new RuntimeException(e); }
    token_source.ReInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jjtree.reset();
    jj_gen = 0;
    for (int i = 0; i < 52; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  /** Constructor. */
  public ELParser(java.io.Reader stream) {
    jj_input_stream = new SimpleCharStream(stream, 1, 1);
    token_source = new ELParserTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 52; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  /** Reinitialise. */
  public void ReInit(java.io.Reader stream) {
    jj_input_stream.ReInit(stream, 1, 1);
    token_source.ReInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jjtree.reset();
    jj_gen = 0;
    for (int i = 0; i < 52; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  /** Constructor with generated Token Manager. */
  public ELParser(ELParserTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 52; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  /** Reinitialise. */
  public void ReInit(ELParserTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jjtree.reset();
    jj_gen = 0;
    for (int i = 0; i < 52; i++) jj_la1[i] = -1;
    for (int i = 0; i < jj_2_rtns.length; i++) jj_2_rtns[i] = new JJCalls();
  }

  private Token jj_consume_token(int kind) throws ParseException {
    Token oldToken;
    if ((oldToken = token).next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    if (token.kind == kind) {
      jj_gen++;
      if (++jj_gc > 100) {
        jj_gc = 0;
        for (int i = 0; i < jj_2_rtns.length; i++) {
          JJCalls c = jj_2_rtns[i];
          while (c != null) {
            if (c.gen < jj_gen) c.first = null;
            c = c.next;
          }
        }
      }
      return token;
    }
    token = oldToken;
    jj_kind = kind;
    throw generateParseException();
  }

  static private final class LookaheadSuccess extends java.lang.Error {
      /*
       * Over-ridden to avoid memory leak via Throwable.backtrace
       * https://java.net/jira/browse/JAVACC-293
       */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
  }
  final private LookaheadSuccess jj_ls = new LookaheadSuccess();
  private boolean jj_scan_token(int kind) {
    if (jj_scanpos == jj_lastpos) {
      jj_la--;
      if (jj_scanpos.next == null) {
        jj_lastpos = jj_scanpos = jj_scanpos.next = token_source.getNextToken();
      } else {
        jj_lastpos = jj_scanpos = jj_scanpos.next;
      }
    } else {
      jj_scanpos = jj_scanpos.next;
    }
    if (jj_rescan) {
      int i = 0; Token tok = token;
      while (tok != null && tok != jj_scanpos) { i++; tok = tok.next; }
      if (tok != null) jj_add_error_token(kind, i);
    }
    if (jj_scanpos.kind != kind) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) throw jj_ls;
    return false;
  }


/** Get the next Token. */
  final public Token getNextToken() {
    if (token.next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    jj_gen++;
    return token;
  }

/** Get the specific Token. */
  final public Token getToken(int index) {
    Token t = token;
    for (int i = 0; i < index; i++) {
      if (t.next != null) t = t.next;
      else t = t.next = token_source.getNextToken();
    }
    return t;
  }

  private int jj_ntk() {
    if ((jj_nt=token.next) == null)
      return (jj_ntk = (token.next=token_source.getNextToken()).kind);
    else
      return (jj_ntk = jj_nt.kind);
  }

  private java.util.List<int[]> jj_expentries = new java.util.ArrayList<int[]>();
  private int[] jj_expentry;
  private int jj_kind = -1;
  private int[] jj_lasttokens = new int[100];
  private int jj_endpos;

  private void jj_add_error_token(int kind, int pos) {
    if (pos >= 100) return;
    if (pos == jj_endpos + 1) {
      jj_lasttokens[jj_endpos++] = kind;
    } else if (jj_endpos != 0) {
      jj_expentry = new int[jj_endpos];
      for (int i = 0; i < jj_endpos; i++) {
        jj_expentry[i] = jj_lasttokens[i];
      }
      jj_entries_loop: for (java.util.Iterator<?> it = jj_expentries.iterator(); it.hasNext();) {
        int[] oldentry = (int[])(it.next());
        if (oldentry.length == jj_expentry.length) {
          for (int i = 0; i < jj_expentry.length; i++) {
            if (oldentry[i] != jj_expentry[i]) {
              continue jj_entries_loop;
            }
          }
          jj_expentries.add(jj_expentry);
          break jj_entries_loop;
        }
      }
      if (pos != 0) jj_lasttokens[(jj_endpos = pos) - 1] = kind;
    }
  }

  /** Generate ParseException. */
  public ParseException generateParseException() {
    jj_expentries.clear();
    boolean[] la1tokens = new boolean[62];
    if (jj_kind >= 0) {
      la1tokens[jj_kind] = true;
      jj_kind = -1;
    }
    for (int i = 0; i < 52; i++) {
      if (jj_la1[i] == jj_gen) {
        for (int j = 0; j < 32; j++) {
          if ((jj_la1_0[i] & (1<<j)) != 0) {
            la1tokens[j] = true;
          }
          if ((jj_la1_1[i] & (1<<j)) != 0) {
            la1tokens[32+j] = true;
          }
        }
      }
    }
    for (int i = 0; i < 62; i++) {
      if (la1tokens[i]) {
        jj_expentry = new int[1];
        jj_expentry[0] = i;
        jj_expentries.add(jj_expentry);
      }
    }
    jj_endpos = 0;
    jj_rescan_token();
    jj_add_error_token(0, 0);
    int[][] exptokseq = new int[jj_expentries.size()][];
    for (int i = 0; i < jj_expentries.size(); i++) {
      exptokseq[i] = jj_expentries.get(i);
    }
    return new ParseException(token, exptokseq, tokenImage);
  }

  /** Enable tracing. */
  final public void enable_tracing() {
  }

  /** Disable tracing. */
  final public void disable_tracing() {
  }

  private void jj_rescan_token() {
    jj_rescan = true;
    for (int i = 0; i < 8; i++) {
    try {
      JJCalls p = jj_2_rtns[i];
      do {
        if (p.gen > jj_gen) {
          jj_la = p.arg; jj_lastpos = jj_scanpos = p.first;
          switch (i) {
            case 0: jj_3_1(); break;
            case 1: jj_3_2(); break;
            case 2: jj_3_3(); break;
            case 3: jj_3_4(); break;
            case 4: jj_3_5(); break;
            case 5: jj_3_6(); break;
            case 6: jj_3_7(); break;
            case 7: jj_3_8(); break;
          }
        }
        p = p.next;
      } while (p != null);
      } catch(LookaheadSuccess ls) { }
    }
    jj_rescan = false;
  }

  private void jj_save(int index, int xla) {
    JJCalls p = jj_2_rtns[index];
    while (p.gen > jj_gen) {
      if (p.next == null) { p = p.next = new JJCalls(); break; }
      p = p.next;
    }
    p.gen = jj_gen + xla - jj_la; p.first = token; p.arg = xla;
  }

  static final class JJCalls {
    int gen;
    Token first;
    int arg;
    JJCalls next;
  }

}
