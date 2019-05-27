package org.apache.tomcat.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import javax.websocket.Extension;
import javax.websocket.Extension.Parameter;
import javax.websocket.SendHandler;

import org.apache.tomcat.util.res.StringManager;

public class PerMessageDeflate implements Transformation {

    private static final StringManager sm = StringManager.getManager(PerMessageDeflate.class);

    private static final String SERVER_NO_CONTEXT_TAKEOVER = "server_no_context_takeover";
    private static final String CLIENT_NO_CONTEXT_TAKEOVER = "client_no_context_takeover";
    private static final String SERVER_MAX_WINDOW_BITS = "server_max_window_bits";
    private static final String CLIENT_MAX_WINDOW_BITS = "client_max_window_bits";

    private static final int RSV_BITMASK = 0b100;
    private static final byte[] EOM_BYTES = new byte[] {0, 0, -1, -1};

    public static final String NAME = "permessage-deflate";

    private final boolean serverContextTakeover;
    private final int serverMaxWindowBits;
    private final boolean clientContextTakeover;
    private final int clientMaxWindowBits;
    private final boolean isServer;
    private final Inflater inflater = new Inflater(true);
    private final ByteBuffer readBuffer = ByteBuffer.allocate(Constants.DEFAULT_BUFFER_SIZE);
    private final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    private final byte[] EOM_BUFFER = new byte[EOM_BYTES.length + 1];

    private volatile Transformation next;
    private volatile boolean skipDecompression = false;
    private volatile ByteBuffer writeBuffer = ByteBuffer.allocate(Constants.DEFAULT_BUFFER_SIZE);
    private volatile boolean firstCompressedFrameWritten = false;

    static PerMessageDeflate negotiate(List<List<Parameter>> preferences, boolean isServer) {
        // 接受端点能够支持的首选项
        for (List<Parameter> preference : preferences) {
            boolean ok = true;
            boolean serverContextTakeover = true;
            int serverMaxWindowBits = -1;
            boolean clientContextTakeover = true;
            int clientMaxWindowBits = -1;

            for (Parameter param : preference) {
                if (SERVER_NO_CONTEXT_TAKEOVER.equals(param.getName())) {
                    if (serverContextTakeover) {
                        serverContextTakeover = false;
                    } else {
                        // 重复定义
                        throw new IllegalArgumentException(sm.getString(
                                "perMessageDeflate.duplicateParameter",
                                SERVER_NO_CONTEXT_TAKEOVER ));
                    }
                } else if (CLIENT_NO_CONTEXT_TAKEOVER.equals(param.getName())) {
                    if (clientContextTakeover) {
                        clientContextTakeover = false;
                    } else {
                        // 重复定义
                        throw new IllegalArgumentException(sm.getString(
                                "perMessageDeflate.duplicateParameter",
                                CLIENT_NO_CONTEXT_TAKEOVER ));
                    }
                } else if (SERVER_MAX_WINDOW_BITS.equals(param.getName())) {
                    if (serverMaxWindowBits == -1) {
                        serverMaxWindowBits = Integer.parseInt(param.getValue());
                        if (serverMaxWindowBits < 8 || serverMaxWindowBits > 15) {
                            throw new IllegalArgumentException(sm.getString(
                                    "perMessageDeflate.invalidWindowSize",
                                    SERVER_MAX_WINDOW_BITS,
                                    Integer.valueOf(serverMaxWindowBits)));
                        }
                        // Java SE API (as of Java 8) 不公开API来控制窗口大小. 它有效地硬编码为15
                        if (isServer && serverMaxWindowBits != 15) {
                            ok = false;
                            break;
                            // 注意，服务器窗口大小对于客户端来说不是问题，因为客户端将假定为15，如果服务器使用较小的窗口，则一切仍然有效
                        }
                    } else {
                        // 重复定义
                        throw new IllegalArgumentException(sm.getString(
                                "perMessageDeflate.duplicateParameter",
                                SERVER_MAX_WINDOW_BITS ));
                    }
                } else if (CLIENT_MAX_WINDOW_BITS.equals(param.getName())) {
                    if (clientMaxWindowBits == -1) {
                        if (param.getValue() == null) {
                            // 提示服务器客户端支持此选项. Java SE API (as of Java 8)不公开API来控制窗口大小. 它有效地硬编码为15
                            clientMaxWindowBits = 15;
                        } else {
                            clientMaxWindowBits = Integer.parseInt(param.getValue());
                            if (clientMaxWindowBits < 8 || clientMaxWindowBits > 15) {
                                throw new IllegalArgumentException(sm.getString(
                                        "perMessageDeflate.invalidWindowSize",
                                        CLIENT_MAX_WINDOW_BITS,
                                        Integer.valueOf(clientMaxWindowBits)));
                            }
                        }
                        // Java SE API (as of Java 8)不公开API来控制窗口大小. 它有效地硬编码为15
                        if (!isServer && clientMaxWindowBits != 15) {
                            ok = false;
                            break;
                            // 注意，服务器窗口大小对于客户端来说不是问题，因为客户端将假定为15，如果服务器使用较小的窗口，则一切仍然有效
                        }
                    } else {
                        // 重复定义
                        throw new IllegalArgumentException(sm.getString(
                                "perMessageDeflate.duplicateParameter",
                                CLIENT_MAX_WINDOW_BITS ));
                    }
                } else {
                    // 未知参数
                    throw new IllegalArgumentException(sm.getString(
                            "perMessageDeflate.unknownParameter", param.getName()));
                }
            }
            if (ok) {
                return new PerMessageDeflate(serverContextTakeover, serverMaxWindowBits,
                        clientContextTakeover, clientMaxWindowBits, isServer);
            }
        }
        // 协商条款不成功
        return null;
    }


    private PerMessageDeflate(boolean serverContextTakeover, int serverMaxWindowBits,
            boolean clientContextTakeover, int clientMaxWindowBits, boolean isServer) {
        this.serverContextTakeover = serverContextTakeover;
        this.serverMaxWindowBits = serverMaxWindowBits;
        this.clientContextTakeover = clientContextTakeover;
        this.clientMaxWindowBits = clientMaxWindowBits;
        this.isServer = isServer;
    }


    @Override
    public TransformationResult getMoreData(byte opCode, boolean fin, int rsv, ByteBuffer dest)
            throws IOException {
        // 控制帧从不压缩，可能出现在WebSocket方法的中间. 直接传递它们.
        if (Util.isControl(opCode)) {
            return next.getMoreData(opCode, fin, rsv, dest);
        }

        if (!Util.isContinuation(opCode)) {
            // 新消息中的第一帧
            skipDecompression = (rsv & RSV_BITMASK) == 0;
        }

        // 直接传递未压缩的帧.
        if (skipDecompression) {
            return next.getMoreData(opCode, fin, rsv, dest);
        }

        int written;
        boolean usedEomBytes = false;

        while (dest.remaining() > 0) {
            // 目的地可用空间. 试着填满它.
            try {
                written = inflater.inflate(
                        dest.array(), dest.arrayOffset() + dest.position(), dest.remaining());
            } catch (DataFormatException e) {
                throw new IOException(sm.getString("perMessageDeflate.deflateFailed"), e);
            }
            dest.position(dest.position() + written);

            if (inflater.needsInput() && !usedEomBytes ) {
                if (dest.hasRemaining()) {
                    readBuffer.clear();
                    TransformationResult nextResult =
                            next.getMoreData(opCode, fin, (rsv ^ RSV_BITMASK), readBuffer);
                    inflater.setInput(
                            readBuffer.array(), readBuffer.arrayOffset(), readBuffer.position());
                    if (TransformationResult.UNDERFLOW.equals(nextResult)) {
                        return nextResult;
                    } else if (TransformationResult.END_OF_FRAME.equals(nextResult) &&
                            readBuffer.position() == 0) {
                        if (fin) {
                            inflater.setInput(EOM_BYTES);
                            usedEomBytes = true;
                        } else {
                            return TransformationResult.END_OF_FRAME;
                        }
                    }
                }
            } else if (written == 0) {
                if (fin && (isServer && !clientContextTakeover ||
                        !isServer && !serverContextTakeover)) {
                    inflater.reset();
                }
                return TransformationResult.END_OF_FRAME;
            }
        }

        return TransformationResult.OVERFLOW;
    }


    @Override
    public boolean validateRsv(int rsv, byte opCode) {
        if (Util.isControl(opCode)) {
            if ((rsv & RSV_BITMASK) != 0) {
                return false;
            } else {
                if (next == null) {
                    return true;
                } else {
                    return next.validateRsv(rsv, opCode);
                }
            }
        } else {
            int rsvNext = rsv;
            if ((rsv & RSV_BITMASK) != 0) {
                rsvNext = rsv ^ RSV_BITMASK;
            }
            if (next == null) {
                return true;
            } else {
                return next.validateRsv(rsvNext, opCode);
            }
        }
    }


    @Override
    public Extension getExtensionResponse() {
        Extension result = new WsExtension(NAME);

        List<Extension.Parameter> params = result.getParameters();

        if (!serverContextTakeover) {
            params.add(new WsExtensionParameter(SERVER_NO_CONTEXT_TAKEOVER, null));
        }
        if (serverMaxWindowBits != -1) {
            params.add(new WsExtensionParameter(SERVER_MAX_WINDOW_BITS,
                    Integer.toString(serverMaxWindowBits)));
        }
        if (!clientContextTakeover) {
            params.add(new WsExtensionParameter(CLIENT_NO_CONTEXT_TAKEOVER, null));
        }
        if (clientMaxWindowBits != -1) {
            params.add(new WsExtensionParameter(CLIENT_MAX_WINDOW_BITS,
                    Integer.toString(clientMaxWindowBits)));
        }

        return result;
    }


    @Override
    public void setNext(Transformation t) {
        if (next == null) {
            this.next = t;
        } else {
            next.setNext(t);
        }
    }


    @Override
    public boolean validateRsvBits(int i) {
        if ((i & RSV_BITMASK) != 0) {
            return false;
        }
        if (next == null) {
            return true;
        } else {
            return next.validateRsvBits(i | RSV_BITMASK);
        }
    }


    @Override
    public List<MessagePart> sendMessagePart(List<MessagePart> uncompressedParts) {
        List<MessagePart> allCompressedParts = new ArrayList<>();

        // 如果消息完全为空，则标记为跟踪
        boolean emptyMessage = true;

        for (MessagePart uncompressedPart : uncompressedParts) {
            byte opCode = uncompressedPart.getOpCode();
            boolean emptyPart = uncompressedPart.getPayload().limit() == 0;
            emptyMessage = emptyMessage && emptyPart;
            if (Util.isControl(opCode)) {
                // 控制消息可以出现在其他消息的中间，并且不能被压缩. 直接传递它
                allCompressedParts.add(uncompressedPart);
            } else if (emptyMessage && uncompressedPart.isFin()) {
                // 零长度消息不能被压缩，所以直接传递.
                allCompressedParts.add(uncompressedPart);
            } else {
                List<MessagePart> compressedParts = new ArrayList<>();
                ByteBuffer uncompressedPayload = uncompressedPart.getPayload();
                SendHandler uncompressedIntermediateHandler =
                        uncompressedPart.getIntermediateHandler();

                deflater.setInput(uncompressedPayload.array(),
                        uncompressedPayload.arrayOffset() + uncompressedPayload.position(),
                        uncompressedPayload.remaining());

                int flush = (uncompressedPart.isFin() ? Deflater.SYNC_FLUSH : Deflater.NO_FLUSH);
                boolean deflateRequired = true;

                while(deflateRequired) {
                    ByteBuffer compressedPayload = writeBuffer;

                    int written = deflater.deflate(compressedPayload.array(),
                            compressedPayload.arrayOffset() + compressedPayload.position(),
                            compressedPayload.remaining(), flush);
                    compressedPayload.position(compressedPayload.position() + written);

                    if (!uncompressedPart.isFin() && compressedPayload.hasRemaining() && deflater.needsInput()) {
                        // 此消息部分已由deflater完全处理. 为这个消息部分触发发送处理程序，然后转到下一个消息部分.
                        break;
                    }

                    // 如果到这里, 将创建一个新的压缩消息部分...
                    MessagePart compressedPart;

                    // .. 并且需要一个新的 writeBuffer.
                    writeBuffer = ByteBuffer.allocate(Constants.DEFAULT_BUFFER_SIZE);

                    // 翻转压缩有效载荷, 准备写入
                    compressedPayload.flip();

                    boolean fin = uncompressedPart.isFin();
                    boolean full = compressedPayload.limit() == compressedPayload.capacity();
                    boolean needsInput = deflater.needsInput();
                    long blockingWriteTimeoutExpiry = uncompressedPart.getBlockingWriteTimeoutExpiry();

                    if (fin && !full && needsInput) {
                        // 压缩消息结束. 删除 EOM 字节并输出.
                        compressedPayload.limit(compressedPayload.limit() - EOM_BYTES.length);
                        compressedPart = new MessagePart(true, getRsv(uncompressedPart),
                                opCode, compressedPayload, uncompressedIntermediateHandler,
                                uncompressedIntermediateHandler, blockingWriteTimeoutExpiry);
                        deflateRequired = false;
                        startNewMessage();
                    } else if (full && !needsInput) {
                        // 写入缓冲区满，输入信息未完全读取.
                        // 输出并启动新的压缩部分.
                        compressedPart = new MessagePart(false, getRsv(uncompressedPart),
                                opCode, compressedPayload, uncompressedIntermediateHandler,
                                uncompressedIntermediateHandler, blockingWriteTimeoutExpiry);
                    } else if (!fin && full && needsInput) {
                        // 写入缓冲区满，输入信息未完全读取.
                        // 输出并获取更多数据.
                        compressedPart = new MessagePart(false, getRsv(uncompressedPart),
                                opCode, compressedPayload, uncompressedIntermediateHandler,
                                uncompressedIntermediateHandler, blockingWriteTimeoutExpiry);
                        deflateRequired = false;
                    } else if (fin && full && needsInput) {
                        // 写入缓冲区满. 输入完全读取. Deflater 可能处于四种状态之一:
                        // - 输出完成 (恰好与缓冲区结束对齐
                        // - 在EOM字节的中间
                        // - 即将写入EOM字节
                        // - 更多的数据写入
                        int eomBufferWritten = deflater.deflate(EOM_BUFFER, 0, EOM_BUFFER.length, Deflater.SYNC_FLUSH);
                        if (eomBufferWritten < EOM_BUFFER.length) {
                            // EOM刚刚完成
                            compressedPayload.limit(compressedPayload.limit() - EOM_BYTES.length + eomBufferWritten);
                            compressedPart = new MessagePart(true,
                                    getRsv(uncompressedPart), opCode, compressedPayload,
                                    uncompressedIntermediateHandler, uncompressedIntermediateHandler,
                                    blockingWriteTimeoutExpiry);
                            deflateRequired = false;
                            startNewMessage();
                        } else {
                            // 更多的数据写入
                            // 将字节复制到新的写入缓冲区
                            writeBuffer.put(EOM_BUFFER, 0, eomBufferWritten);
                            compressedPart = new MessagePart(false,
                                    getRsv(uncompressedPart), opCode, compressedPayload,
                                    uncompressedIntermediateHandler, uncompressedIntermediateHandler,
                                    blockingWriteTimeoutExpiry);
                        }
                    } else {
                        throw new IllegalStateException("Should never happen");
                    }

                    // 将新创建的压缩部分添加到一组部件，以传递到下一个转换.
                    compressedParts.add(compressedPart);
                }

                SendHandler uncompressedEndHandler = uncompressedPart.getEndHandler();
                int size = compressedParts.size();
                if (size > 0) {
                    compressedParts.get(size - 1).setEndHandler(uncompressedEndHandler);
                }

                allCompressedParts.addAll(compressedParts);
            }
        }

        if (next == null) {
            return allCompressedParts;
        } else {
            return next.sendMessagePart(allCompressedParts);
        }
    }


    private void startNewMessage() {
        firstCompressedFrameWritten = false;
        if (isServer && !serverContextTakeover || !isServer && !clientContextTakeover) {
            deflater.reset();
        }
    }


    private int getRsv(MessagePart uncompressedMessagePart) {
        int result = uncompressedMessagePart.getRsv();
        if (!firstCompressedFrameWritten) {
            result += RSV_BITMASK;
            firstCompressedFrameWritten = true;
        }
        return result;
    }


    @Override
    public void close() {
        // 总会有下一个转变
        next.close();
        inflater.end();
        deflater.end();
    }
}
