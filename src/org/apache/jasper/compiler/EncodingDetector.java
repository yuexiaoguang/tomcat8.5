package org.apache.jasper.compiler;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/*
 * BoM检测源自:
 * http://svn.us.apache.org/viewvc/tomcat/trunk/java/org/apache/jasper/xmlparser/XMLEncodingDetector.java?annotate=1742248
 *
 * prolog始终至少与BOM一样具体，因此prolog中指定的任何编码都应优先于BOM.
 */
class EncodingDetector {

    private static final XMLInputFactory XML_INPUT_FACTORY;
    static {
        XML_INPUT_FACTORY = XMLInputFactory.newInstance();
    }

    private final String encoding;
    private final int skip;
    private final boolean encodingSpecifiedInProlog;


    /*
     * TODO: 重构Jasper InputStream的创建和处理，以便传递给此方法的InputStream被缓冲，因此可以节省多次打开和重新打开同一个文件的时间.
     */
    EncodingDetector(InputStream is) throws IOException {
        // 这里将缓冲区大小保持在最低限度. BoM将不超过4个字节，因此这是我们需要缓冲的最大值
        BufferedInputStream bis = new BufferedInputStream(is, 4);
        bis.mark(4);

        BomResult bomResult = processBom(bis);

        // 将流重置回到开始位置, 以允许XML prolog检测工作. 跳过发现的任何BoM.
        bis.reset();
        for (int i = 0; i < bomResult.skip; i++) {
            bis.read();
        }

        String prologEncoding = getPrologEncoding(bis);
        if (prologEncoding == null) {
            encodingSpecifiedInProlog = false;
            encoding = bomResult.encoding;
        } else {
            encodingSpecifiedInProlog = true;
            encoding = prologEncoding;
        }
        skip = bomResult.skip;
    }


    String getEncoding() {
        return encoding;
    }


    int getSkip() {
        return skip;
    }


    boolean isEncodingSpecifiedInProlog() {
        return encodingSpecifiedInProlog;
    }


    private String getPrologEncoding(InputStream stream) {
        String encoding = null;
        try {
            XMLStreamReader xmlStreamReader = XML_INPUT_FACTORY.createXMLStreamReader(stream);
            encoding = xmlStreamReader.getCharacterEncodingScheme();
        } catch (XMLStreamException e) {
            // Ignore
        }
        return encoding;
    }


    private BomResult processBom(InputStream stream) {
        // 读取前四个字节（或尽可能多的字节）并确定编码
        try {
            final byte[] b4 = new byte[4];
            int count = 0;
            int singleByteRead;
            while (count < 4) {
                singleByteRead = stream.read();
                if (singleByteRead == -1) {
                    break;
                }
                b4[count] = (byte) singleByteRead;
                count++;
            }

            return parseBom(b4, count);
        } catch (IOException ioe) {
            // Failed.
            return new BomResult("UTF-8", 0);
        }
    }


    private BomResult parseBom(byte[] b4, int count) {

        if (count < 2) {
            return new BomResult("UTF-8", 0);
        }

        // UTF-16, with BOM
        int b0 = b4[0] & 0xFF;
        int b1 = b4[1] & 0xFF;
        if (b0 == 0xFE && b1 == 0xFF) {
            // UTF-16, big-endian
            return new BomResult("UTF-16BE", 2);
        }
        if (b0 == 0xFF && b1 == 0xFE) {
            // UTF-16, little-endian
            return new BomResult("UTF-16LE", 2);
        }

        // 默认 UTF-8, 如果我们没有足够的字节来很好地确定编码
        if (count < 3) {
            return new BomResult("UTF-8", 0);
        }

        // UTF-8 with a BOM
        int b2 = b4[2] & 0xFF;
        if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) {
            return new BomResult("UTF-8", 3);
        }

        // 默认 UTF-8, 如果我们没有足够的字节来很好地确定编码
        if (count < 4) {
            return new BomResult("UTF-8", 0);
        }

        // 其它编码. No BOM. Try and ID encoding.
        int b3 = b4[3] & 0xFF;
        if (b0 == 0x00 && b1 == 0x00 && b2 == 0x00 && b3 == 0x3C) {
            // UCS-4, big endian (1234)
            return new BomResult("ISO-10646-UCS-4", 0);
        }
        if (b0 == 0x3C && b1 == 0x00 && b2 == 0x00 && b3 == 0x00) {
            // UCS-4, little endian (4321)
            return new BomResult("ISO-10646-UCS-4", 0);
        }
        if (b0 == 0x00 && b1 == 0x00 && b2 == 0x3C && b3 == 0x00) {
            // UCS-4, unusual octet order (2143)
            // REVISIT: What should this be?
            return new BomResult("ISO-10646-UCS-4", 0);
        }
        if (b0 == 0x00 && b1 == 0x3C && b2 == 0x00 && b3 == 0x00) {
            // UCS-4, unusual octect order (3412)
            // REVISIT: What should this be?
            return new BomResult("ISO-10646-UCS-4", 0);
        }
        if (b0 == 0x00 && b1 == 0x3C && b2 == 0x00 && b3 == 0x3F) {
            // UTF-16, big-endian, no BOM
            // (or could turn out to be UCS-2...
            // REVISIT: What should this be?
            return new BomResult("UTF-16BE", 0);
        }
        if (b0 == 0x3C && b1 == 0x00 && b2 == 0x3F && b3 == 0x00) {
            // UTF-16, little-endian, no BOM
            // (or could turn out to be UCS-2...
            return new BomResult("UTF-16LE", 0);
        }
        if (b0 == 0x4C && b1 == 0x6F && b2 == 0xA7 && b3 == 0x94) {
            // EBCDIC
            // a la xerces1, return CP037 instead of EBCDIC here
            return new BomResult("CP037", 0);
        }

        // default encoding
        return new BomResult("UTF-8", 0);
    }


    private static class BomResult {

        public final String encoding;
        public final int skip;

        public BomResult(String encoding,  int skip) {
            this.encoding = encoding;
            this.skip = skip;
        }
    }
}
