package org.apache.tomcat.util.buf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * UTF-8编码器的高效实现.
 * 这个类不是线程安全的 - 每个线程需要一个编码器.
 * 编码器将保存并回收内部对象, 避免垃圾.
 *
 * 可以添加要保留的额外字符, 例如，在编码URL时，您可以添加“/”.
 */
public final class UEncoder {

    public enum SafeCharsSet {
        WITH_SLASH("/"), DEFAULT("");
        private final BitSet safeChars;

        private BitSet getSafeChars() {
            return this.safeChars;
        }

        private SafeCharsSet(String additionalSafeChars) {
            safeChars = initialSafeChars();
            for (char c : additionalSafeChars.toCharArray()) {
                safeChars.set(c);
            }
        }
    }

    // Not static - 设置可能有所不同 (比添加一个额外的检查 "/", "+" 更好, 等)
    private BitSet safeChars=null;
    private C2BConverter c2b=null;
    private ByteChunk bb=null;
    private CharChunk cb=null;
    private CharChunk output=null;

    /**
     * 创建具有不可修改的安全字符集的UEncoder.
     *
     * @param safeCharsSet 此编码器的安全字符
     */
    public UEncoder(SafeCharsSet safeCharsSet) {
        this.safeChars = safeCharsSet.getSafeChars();
    }

   /**
    * URL使用指定的编码对代码进行编码.
    *
    * @param s 要编码的字符串
    * @param start 开始位置
    * @param end 结束位置
    *
    * @return 包含URL编码的字符串的CharChunk
    *
    * @throws IOException 如果发生I/O错误
    */
   public CharChunk encodeURL(String s, int start, int end)
       throws IOException {
       if (c2b == null) {
           bb = new ByteChunk(8); // small enough.
           cb = new CharChunk(2); // small enough.
           output = new CharChunk(64); // small enough.
           c2b = new C2BConverter(StandardCharsets.UTF_8);
       } else {
           bb.recycle();
           cb.recycle();
           output.recycle();
       }

       for (int i = start; i < end; i++) {
           char c = s.charAt(i);
           if (safeChars.get(c)) {
               output.append(c);
           } else {
               cb.append(c);
               c2b.convert(cb, bb);

               // "surrogate" - UTF is _not_ 16 bit, but 21 !!!!
               // ( while UCS is 31 ). Amazing...
               if (c >= 0xD800 && c <= 0xDBFF) {
                   if ((i+1) < end) {
                       char d = s.charAt(i+1);
                       if (d >= 0xDC00 && d <= 0xDFFF) {
                           cb.append(d);
                           c2b.convert(cb, bb);
                           i++;
                       }
                   }
               }

               urlEncode(output, bb);
               cb.recycle();
               bb.recycle();
           }
       }

       return output;
   }

   protected void urlEncode(CharChunk out, ByteChunk bb)
       throws IOException {
       byte[] bytes = bb.getBuffer();
       for (int j = bb.getStart(); j < bb.getEnd(); j++) {
           out.append('%');
           char ch = Character.forDigit((bytes[j] >> 4) & 0xF, 16);
           out.append(ch);
           ch = Character.forDigit(bytes[j] & 0xF, 16);
           out.append(ch);
       }
   }

    // -------------------- Internal implementation --------------------

    private static BitSet initialSafeChars() {
        BitSet initialSafeChars=new BitSet(128);
        int i;
        for (i = 'a'; i <= 'z'; i++) {
            initialSafeChars.set(i);
        }
        for (i = 'A'; i <= 'Z'; i++) {
            initialSafeChars.set(i);
        }
        for (i = '0'; i <= '9'; i++) {
            initialSafeChars.set(i);
        }
        //safe
        initialSafeChars.set('$');
        initialSafeChars.set('-');
        initialSafeChars.set('_');
        initialSafeChars.set('.');

        // Dangerous: someone may treat this as " "
        // RFC1738 does allow it, it's not reserved
        //    initialSafeChars.set('+');
        //extra
        initialSafeChars.set('!');
        initialSafeChars.set('*');
        initialSafeChars.set('\'');
        initialSafeChars.set('(');
        initialSafeChars.set(')');
        initialSafeChars.set(',');
        return initialSafeChars;
    }
}
