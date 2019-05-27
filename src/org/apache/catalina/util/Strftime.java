package org.apache.catalina.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;

/**
 * 将日期转换为字符串使用相同的格式说明符为函数
 *
 * Note: 这不是完全的模仿strftime. 某些函数的命令, 不支持, 并将它们转换为文字.
 *       某些复杂的命令, 就像那些处理一年中的一周一样, 不可能有完全相同的行为函数.
 *       这些限制是由于使用SimpleDateTime. 如果转换是手动完成的, 所有这些限制都可以消除.
 *       这个接口看起来像DateFormat子集. 可能某天将让这个类继承 DateFormat.
 */
public class Strftime {
    protected static final Properties translate;
    protected final SimpleDateFormat simpleDateFormat;

    /**
     * 初始化模式转换
     */
    static {
        translate = new Properties();
        translate.put("a","EEE");
        translate.put("A","EEEE");
        translate.put("b","MMM");
        translate.put("B","MMMM");
        translate.put("c","EEE MMM d HH:mm:ss yyyy");

        //在 SimpleDateFormat中没有方法指明世纪.
        // 我们并不想硬编码20, 因为这对于2000之前的文件将发生错误.
        //translate.put("C", "20");
        translate.put("d","dd");
        translate.put("D","MM/dd/yy");
        translate.put("e","dd"); //将显示 '03', 而不是' 3'
        translate.put("F","yyyy-MM-dd");
        translate.put("g","yy");
        translate.put("G","yyyy");
        translate.put("H","HH");
        translate.put("h","MMM");
        translate.put("I","hh");
        translate.put("j","DDD");
        translate.put("k","HH"); //将显示 '07', 而不是 ' 7'
        translate.put("l","hh"); //将显示 '07', 而不是 ' 7'
        translate.put("m","MM");
        translate.put("M","mm");
        translate.put("n","\n");
        translate.put("p","a");
        translate.put("P","a");  //将显示 pm, 而不是  PM
        translate.put("r","hh:mm:ss a");
        translate.put("R","HH:mm");
        // 使用SimpleDateFormat不能指定这个
        //translate.put("s","seconds since epoch");
        translate.put("S","ss");
        translate.put("t","\t");
        translate.put("T","HH:mm:ss");
        // 使用SimpleDateFormat不能指定这个
        //translate.put("u","day of week ( 1-7 )");

        // 使用SimpleDateFormat不能指定这个
        //translate.put("U","week in year with first Sunday as first day...");

        translate.put("V","ww"); //I'm not sure this is always exactly the same

        // 使用SimpleDateFormat不能指定这个
        //translate.put("W","week in year with first Monday as first day...");

        // 使用SimpleDateFormat不能指定这个
        //translate.put("w","E");
        translate.put("X","HH:mm:ss");
        translate.put("x","MM/dd/yy");
        translate.put("y","yy");
        translate.put("Y","yyyy");
        translate.put("Z","z");
        translate.put("z","Z");
        translate.put("%","%");
    }


    /**
     * @param origFormat 函数的格式字符串
     * @param locale 用于特定于区域的转换
     */
    public Strftime( String origFormat, Locale locale ) {
        String convertedFormat = convertDateFormat( origFormat );
        simpleDateFormat = new SimpleDateFormat( convertedFormat, locale );
    }

    /**
     * 格式的日期根据构造函数中给定的函数样式的字符串.
     *
     * @param date the date to format
     * @return the formatted date
     */
    public String format( Date date ) {
        return simpleDateFormat.format( date );
    }

    /**
     * 用于格式转换时区
     *
     * @return the timezone
     */
    public TimeZone getTimeZone() {
        return simpleDateFormat.getTimeZone();
    }

    /**
     * 更改用于格式化日期的时区
     *
     * @param timeZone The new time zone
     */
    public void setTimeZone( TimeZone timeZone ) {
        simpleDateFormat.setTimeZone( timeZone );
    }

    /**
     * 搜索提供的模式和C标准时间/日期格式，并将其转换为等效的java.
     *
     * @param pattern 要搜索的模式
     * @return The modified pattern
     */
    protected String convertDateFormat( String pattern ) {
        boolean inside = false;
        boolean mark = false;
        boolean modifiedCommand = false;

        StringBuilder buf = new StringBuilder();

        for(int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);

            if ( c=='%' && !mark ) {
                mark=true;
            } else {
                if ( mark ) {
                    if ( modifiedCommand ) {
                        //don't do anything--只是跳过一个 char
                        modifiedCommand = false;
                        mark = false;
                    } else {
                        inside = translateCommand( buf, pattern, i, inside );
                        //它是一个修改代码
                        if ( c=='O' || c=='E' ) {
                            modifiedCommand = true;
                        } else {
                            mark=false;
                        }
                    }
                } else {
                    if ( !inside && c != ' ' ) {
                        //开始文字, 需要引号
                        buf.append("'");
                        inside = true;
                    }

                    buf.append(c);
                }
            }
        }

        if ( buf.length() > 0 ) {
            char lastChar = buf.charAt( buf.length() - 1 );

            if( lastChar!='\'' && inside ) {
                buf.append('\'');
            }
        }
        return buf.toString();
    }

    protected String quote( String str, boolean insideQuotes ) {
        String retVal = str;
        if ( !insideQuotes ) {
            retVal = '\'' + retVal + '\'';
        }
        return retVal;
    }

    /**
     * 尽量获取相关C标准提供的java日期/时间的格式
     *
     * @param buf The buffer
     * @param pattern The date/time pattern
     * @param index The char index
     * @param oldInside Flag value
     * @return True if new is inside buffer
     */
    protected boolean translateCommand( StringBuilder buf, String pattern, int index, boolean oldInside ) {
        char firstChar = pattern.charAt( index );
        boolean newInside = oldInside;

        //O 和 E 是 修饰语, 它们意味着呈现下一个字符的替代表示
        //只是处理下一个字符, 因为这个没有 O 或 E
        if ( firstChar == 'O' || firstChar == 'E' ) {
            if ( index + 1 < pattern.length() ) {
                newInside = translateCommand( buf, pattern, index + 1, oldInside );
            } else {
                buf.append( quote("%" + firstChar, oldInside ) );
            }
        } else {
            String command = translate.getProperty( String.valueOf( firstChar ) );

            //如果找不到格式, 把它当作文字--Apache就是这样做的
            if ( command == null ) {
                buf.append( quote( "%" + firstChar, oldInside ) );
            } else {
                //如果我们在内部引用, 关闭引用
                if ( oldInside ) {
                    buf.append( '\'' );
                }
                buf.append( command );
                newInside = false;
            }
        }
        return newInside;
    }
}
