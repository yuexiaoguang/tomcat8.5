package org.apache.tomcat.util.http.fileupload.util.mime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.tomcat.util.codec.binary.Base64;

/**
 * 用于解码MIME文本的实用程序类.
 */
public final class MimeUtility {

    /**
     * {@code US-ASCII}字符集标识符常量.
     */
    private static final String US_ASCII_CHARSET = "US-ASCII";

    /**
     * 表示文本使用BASE64算法编码.
     */
    private static final String BASE64_ENCODING_MARKER = "B";

    /**
     * 表示文本使用QuotedPrintable算法编码.
     */
    private static final String QUOTEDPRINTABLE_ENCODING_MARKER = "Q";

    /**
     * 如果文本包含任何编码的token, 这些token将使用 "=?"标记.
     */
    private static final String ENCODED_TOKEN_MARKER = "=?";

    /**
     * 如果文本包含任何编码的token, 这些token将使用  "=?"标记.
     */
    private static final String ENCODED_TOKEN_FINISHER = "?=";

    /**
     * 线性空格字符序列.
     */
    private static final String LINEAR_WHITESPACE = " \t\r\n";

    /**
     * MIME和Java字符集之间的映射.
     */
    private static final Map<String, String> MIME2JAVA = new HashMap<>();

    static {
        MIME2JAVA.put("iso-2022-cn", "ISO2022CN");
        MIME2JAVA.put("iso-2022-kr", "ISO2022KR");
        MIME2JAVA.put("utf-8", "UTF8");
        MIME2JAVA.put("utf8", "UTF8");
        MIME2JAVA.put("ja_jp.iso2022-7", "ISO2022JP");
        MIME2JAVA.put("ja_jp.eucjp", "EUCJIS");
        MIME2JAVA.put("euc-kr", "KSC5601");
        MIME2JAVA.put("euckr", "KSC5601");
        MIME2JAVA.put("us-ascii", "ISO-8859-1");
        MIME2JAVA.put("x-us-ascii", "ISO-8859-1");
    }

    private MimeUtility() {
        // do nothing
    }

    /**
     * 将从邮件header 获取的一串文本解码为正确的格式.
     * 该文本通常由一串token组成, 其中一些可以使用base64编码进行编码.
     *
     * @param text   要解码的文本.
     *
     * @return 解码后的文本字符串.
     * @throws UnsupportedEncodingException 如果不支持输入文本中检测到的编码.
     */
    public static String decodeText(String text) throws UnsupportedEncodingException {
        // 如果文本包含任何编码的token, 这些 token将使用 "=?"标记.
        // 如果源字符串不包含该序列, 不需要解码.
        if (text.indexOf(ENCODED_TOKEN_MARKER) < 0) {
            return text;
        }

        int offset = 0;
        int endOffset = text.length();

        int startWhiteSpace = -1;
        int endWhiteSpace = -1;

        StringBuilder decodedText = new StringBuilder(text.length());

        boolean previousTokenEncoded = false;

        while (offset < endOffset) {
            char ch = text.charAt(offset);

            // 是否是空格字符?
            if (LINEAR_WHITESPACE.indexOf(ch) != -1) { // whitespace found
                startWhiteSpace = offset;
                while (offset < endOffset) {
                    // 跨过空格字符.
                    ch = text.charAt(offset);
                    if (LINEAR_WHITESPACE.indexOf(ch) != -1) { // whitespace found
                        offset++;
                    } else {
                        // 记录第一个非lwsp的位置并下拉以处理token 字符.
                        endWhiteSpace = offset;
                        break;
                    }
                }
            } else {
                // 有一个单词token.  需要扫描单词，然后尝试解析它.
                int wordStart = offset;

                while (offset < endOffset) {
                    // 跨越非空格字符.
                    ch = text.charAt(offset);
                    if (LINEAR_WHITESPACE.indexOf(ch) == -1) { // not white space
                        offset++;
                    } else {
                        break;
                    }

                    //NB:  这些header字符串上的结尾空格将被丢弃.
                }
                // pull out the word token.
                String word = text.substring(wordStart, offset);
                // token 是否编码?  解码单词
                if (word.startsWith(ENCODED_TOKEN_MARKER)) {
                    try {
                        // 如果解析失败, 把它当成一个非编码的单词.
                        String decodedWord = decodeWord(word);

                        // 是否有重要的空格字符?  Append 'em if we've got 'em.
                        if (!previousTokenEncoded && startWhiteSpace != -1) {
                            decodedText.append(text.substring(startWhiteSpace, endWhiteSpace));
                            startWhiteSpace = -1;
                        }
                        // 这绝对是一个解码的 token.
                        previousTokenEncoded = true;
                        // 并将其添加到文本中.
                        decodedText.append(decodedWord);
                        // 继续从这里解析...允许解析错误, 并作为普通文本处理.
                        continue;

                    } catch (ParseException e) {
                        // just ignore it, skip to next word
                    }
                }
                // 这是一个普通的 token, 所以前一个标记是什么并不重要. 如果有的话，添加空格字符.
                if (startWhiteSpace != -1) {
                    decodedText.append(text.substring(startWhiteSpace, endWhiteSpace));
                    startWhiteSpace = -1;
                }
                // 不是解码的 token.
                previousTokenEncoded = false;
                decodedText.append(word);
            }
        }

        return decodedText.toString();
    }

    /**
     * 使用RFC 2047规则为"encoded-word"类型解析字符串.
     * 此编码具有语法:
     *
     * encoded-word = "=?" charset "?" encoding "?" encoded-text "?="
     *
     * @param word   可能编码的单词.
     *
     * @return 解码后的单词.
     * @throws ParseException
     * @throws UnsupportedEncodingException
     */
    private static String decodeWord(String word) throws ParseException, UnsupportedEncodingException {
        // 编码的单词以字符 "=?" 开头.  如果这不是编码的单词, 给调用者抛出一个ParseException.

        if (!word.startsWith(ENCODED_TOKEN_MARKER)) {
            throw new ParseException("Invalid RFC 2047 encoded-word: " + word);
        }

        int charsetPos = word.indexOf('?', 2);
        if (charsetPos == -1) {
            throw new ParseException("Missing charset in RFC 2047 encoded-word: " + word);
        }

        // 拉出字符集信息 (这是此时的MIME名称).
        String charset = word.substring(2, charsetPos).toLowerCase(Locale.ENGLISH);

        // 现在以相同的方式拉出编码 token.
        int encodingPos = word.indexOf('?', charsetPos + 1);
        if (encodingPos == -1) {
            throw new ParseException("Missing encoding in RFC 2047 encoded-word: " + word);
        }

        String encoding = word.substring(charsetPos + 1, encodingPos);

        // 最后编码的文本.
        int encodedTextPos = word.indexOf(ENCODED_TOKEN_FINISHER, encodingPos + 1);
        if (encodedTextPos == -1) {
            throw new ParseException("Missing encoded text in RFC 2047 encoded-word: " + word);
        }

        String encodedText = word.substring(encodingPos + 1, encodedTextPos);

        // 编码空字符串似乎有点傻, 但很容易处理.
        if (encodedText.length() == 0) {
            return "";
        }

        try {
            // 解码器直接写入输出流.
            ByteArrayOutputStream out = new ByteArrayOutputStream(encodedText.length());

            byte[] decodedData;
            // Base64 encoded?
            if (encoding.equals(BASE64_ENCODING_MARKER)) {
                decodedData = Base64.decodeBase64(encodedText);
            } else if (encoding.equals(QUOTEDPRINTABLE_ENCODING_MARKER)) { // maybe quoted printable.
                byte[] encodedData = encodedText.getBytes(US_ASCII_CHARSET);
                QuotedPrintableDecoder.decode(encodedData, out);
                decodedData = out.toByteArray();
            } else {
                throw new UnsupportedEncodingException("Unknown RFC 2047 encoding: " + encoding);
            }
            // 将解码的字节数据转换为字符串.
            return new String(decodedData, javaCharset(charset));
        } catch (IOException e) {
            throw new UnsupportedEncodingException("Invalid RFC 2047 encoding");
        }
    }

    /**
     * 将MIME标准字符集名称转换为Java等效项.
     *
     * @param charset MIME标准名称.
     *
     * @return 此名称的Java等效项.
     */
    private static String javaCharset(String charset) {
        // nothing in, nothing out.
        if (charset == null) {
            return null;
        }

        String mappedCharset = MIME2JAVA.get(charset.toLowerCase(Locale.ENGLISH));
        // 如果没有映射, 然后使用原始名称.  许多MIME字符集名称直接映射回Java.  反过来不一定如此.
        if (mappedCharset == null) {
            return charset;
        }
        return mappedCharset;
    }
}
