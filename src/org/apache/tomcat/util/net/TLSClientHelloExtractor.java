package org.apache.tomcat.util.net;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.openssl.ciphers.Cipher;
import org.apache.tomcat.util.res.StringManager;

/**
 * 此类从TLS client-hello消息中提取SNI主机名和ALPN协议.
 */
public class TLSClientHelloExtractor {

    private static final Log log = LogFactory.getLog(TLSClientHelloExtractor.class);
    private static final StringManager sm = StringManager.getManager(TLSClientHelloExtractor.class);

    private final ExtractorResult result;
    private final List<Cipher> clientRequestedCiphers;
    private final String sniValue;
    private final List<String> clientRequestedApplicationProtocols;

    private static final int TLS_RECORD_HEADER_LEN = 5;

    private static final int TLS_EXTENSION_SERVER_NAME = 0;
    private static final int TLS_EXTENSION_ALPN = 16;

    /**
     * 创建解析器的实例并处理提供的缓冲区.
     * 在执行此方法期间将修改缓冲区位置和限制，但在方法退出之前它们将返回到原始值.
     *
     * @param netInBuffer 包含要处理的TLS数据的缓冲区
     */
    public TLSClientHelloExtractor(ByteBuffer netInBuffer) {
        // TODO: 检测在安全连接上使用http并提供简单的错误页面.

        // 此时缓冲区处于写入模式. 记录当前位置，以便在此方法结束时恢复缓冲区状态.
        int pos = netInBuffer.position();
        int limit = netInBuffer.limit();
        ExtractorResult result = ExtractorResult.NOT_PRESENT;
        List<Cipher> clientRequestedCiphers = new ArrayList<>();
        List<String> clientRequestedApplicationProtocols = new ArrayList<>();
        String sniValue = null;
        try {
            // 切换到读取模式.
            netInBuffer.flip();

            // 在弄清楚记录中有多少字节之前，需要一个完整的TLS记录头.
            if (!isAvailable(netInBuffer, TLS_RECORD_HEADER_LEN)) {
                result = handleIncompleteRead(netInBuffer);
                return;
            }

            if (!isTLSHandshake(netInBuffer)) {
                return;
            }

            if (!isAllRecordAvailable(netInBuffer)) {
                result = handleIncompleteRead(netInBuffer);
                return;
            }

            if (!isClientHello(netInBuffer)) {
                return;
            }

            if (!isAllClientHelloAvailable(netInBuffer)) {
                // 客户端问候不适合单个TLS记录.
                // 将此视为不存在.
                log.warn(sm.getString("sniExtractor.clientHelloTooBig"));
                return;
            }

            // 协议版本
            skipBytes(netInBuffer, 2);
            // Random
            skipBytes(netInBuffer, 32);
            // Session ID (单字节长度)
            skipBytes(netInBuffer, (netInBuffer.get() & 0xFF));

            // Cipher Suites
            // (2 bytes for length, each cipher ID is 2 bytes)
            int cipherCount = netInBuffer.getChar() / 2;
            for (int i = 0; i < cipherCount; i++) {
                int cipherId = netInBuffer.getChar();
                clientRequestedCiphers.add(Cipher.valueOf(cipherId));
            }

            // 压缩方法 (single byte for length)
            skipBytes(netInBuffer, (netInBuffer.get() & 0xFF));

            if (!netInBuffer.hasRemaining()) {
                // 没有更多数据意味着没有扩展
                return;
            }

            // 延长长度
            skipBytes(netInBuffer, 2);
            // 读取扩展，直到用完数据或找到需要的数据
            while (netInBuffer.hasRemaining() &&
                    (sniValue == null || clientRequestedApplicationProtocols.size() == 0)) {
                // 扩展类型是两个字节
                char extensionType = netInBuffer.getChar();
                // 扩展大小是另外两个字节
                char extensionDataSize = netInBuffer.getChar();
                switch (extensionType) {
                case TLS_EXTENSION_SERVER_NAME: {
                    sniValue = readSniExtension(netInBuffer);
                    break;
                }
                case TLS_EXTENSION_ALPN:
                    readAlpnExtension(netInBuffer, clientRequestedApplicationProtocols);
                    break;
                default: {
                    skipBytes(netInBuffer, extensionDataSize);
                }
                }
            }
            result = ExtractorResult.COMPLETE;
        } finally {
            this.result = result;
            this.clientRequestedCiphers = clientRequestedCiphers;
            this.clientRequestedApplicationProtocols = clientRequestedApplicationProtocols;
            this.sniValue = sniValue;
            // 无论发生什么, 将缓冲区返回到其原始状态
            netInBuffer.limit(limit);
            netInBuffer.position(pos);
        }
    }


    public ExtractorResult getResult() {
        return result;
    }


    public String getSNIValue() {
        if (result == ExtractorResult.COMPLETE) {
            return sniValue;
        } else {
            throw new IllegalStateException();
        }
    }


    public List<Cipher> getClientRequestedCiphers() {
        if (result == ExtractorResult.COMPLETE || result == ExtractorResult.NOT_PRESENT) {
            return clientRequestedCiphers;
        } else {
            throw new IllegalStateException();
        }
    }


    public List<String> getClientRequestedApplicationProtocols() {
        if (result == ExtractorResult.COMPLETE || result == ExtractorResult.NOT_PRESENT) {
            return clientRequestedApplicationProtocols;
        } else {
            throw new IllegalStateException();
        }
    }

    private static ExtractorResult handleIncompleteRead(ByteBuffer bb) {
        if (bb.limit() == bb.capacity()) {
            // 缓冲区不够大
            return ExtractorResult.UNDERFLOW;
        } else {
            // 需要将更多数据读入缓冲区
            return ExtractorResult.NEED_READ;
        }
    }


    private static boolean isAvailable(ByteBuffer bb, int size) {
        if (bb.remaining() < size) {
            bb.position(bb.limit());
            return false;
        }
        return true;
    }


    private static boolean isTLSHandshake(ByteBuffer bb) {
        // 对于TLS客户端问候，第一个字节必须为22 - handshake
        if (bb.get() != 22) {
            return false;
        }
        // 接下来的两个字节是主要/次要版本. 需要至少是 3.1.
        byte b2 = bb.get();
        byte b3 = bb.get();
        if (b2 < 3 || b2 == 3 && b3 == 0) {
            return false;
        }
        return true;
    }


    private static boolean isAllRecordAvailable(ByteBuffer bb) {
        // 接下来的两个字节（无符号）是记录的大小. 需要所有这一切.
        int size = bb.getChar();
        return isAvailable(bb, size);
    }


    private static boolean isClientHello(ByteBuffer bb) {
        // 客户端问候语是握手类型1
        if (bb.get() == 1) {
            return true;
        }
        return false;
    }


    private static boolean isAllClientHelloAvailable(ByteBuffer bb) {
        // 接下来的三个字节（无符号）是客户端hello的大小. 需要所有这一切.
        int size = ((bb.get() & 0xFF) << 16) + ((bb.get() & 0xFF) << 8) + (bb.get() & 0xFF);
        return isAvailable(bb, size);
    }


    private static void skipBytes(ByteBuffer bb, int size) {
        bb.position(bb.position() + size);
    }


    private static String readSniExtension(ByteBuffer bb) {
        // 前2个字节是服务器名称列表的大小 (只期待一个)
        // 下一个字节是类型 (0表示主机名)
        skipBytes(bb, 3);
        // 接下来的2个字节是主机名的长度
        char serverNameSize = bb.getChar();
        byte[] serverNameBytes = new byte[serverNameSize];
        bb.get(serverNameBytes);
        return new String(serverNameBytes, StandardCharsets.UTF_8);
    }


    private static void readAlpnExtension(ByteBuffer bb, List<String> protocolNames) {
        // 前2个字节是协议列表的大小
        char toRead = bb.getChar();
        byte[] inputBuffer = new byte[255];
        while (toRead > 0) {
            // 每个列表条目的长度为一个字节，后跟该长度的字符串
            int len = bb.get() & 0xFF;
            bb.get(inputBuffer, 0, len);
            protocolNames.add(new String(inputBuffer, 0, len, StandardCharsets.UTF_8));
            toRead--;
            toRead -= len;
        }
    }


    public static enum ExtractorResult {
        COMPLETE,
        NOT_PRESENT,
        UNDERFLOW,
        NEED_READ
    }
}
