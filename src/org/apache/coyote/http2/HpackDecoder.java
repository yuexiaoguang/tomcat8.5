package org.apache.coyote.http2;

import java.nio.ByteBuffer;

import org.apache.tomcat.util.res.StringManager;

/**
 * HPACK的解码器.
 */
public class HpackDecoder {

    protected static final StringManager sm = StringManager.getManager(HpackDecoder.class);

    private static final int DEFAULT_RING_BUFFER_SIZE = 10;

    /**
     * 接收从这个解码器发出的header的对象
     */
    private HeaderEmitter headerEmitter;

    /**
     * header 表格
     */
    private Hpack.HeaderField[] headerTable;

    /**
     * header 表格的当前 HEAD 的位置. 使用一个环形缓冲区类型构造, 在数组中移动条目是愚蠢的.
     */
    private int firstSlotPosition = 0;

    /**
     * 当前表大小, 按索引 (又叫做填充的索引的位置)
     */
    private int filledTableSlots = 0;

    /**
     * 当前计算内存大小, 按照 HPACK 算法
     */
    private int currentMemorySize = 0;

    /**
     * 容器允许的最大内存大小.
     */
    private int maxMemorySizeHard;
    /**
     * 当前使用的最大内存大小. 可能小于硬极限.
     */
    private int maxMemorySizeSoft;

    private int maxHeaderCount = Constants.DEFAULT_MAX_HEADER_COUNT;
    private int maxHeaderSize = Constants.DEFAULT_MAX_HEADER_SIZE;

    private volatile int headerCount = 0;
    private volatile boolean countedCookie;
    private volatile int headerSize = 0;

    private final StringBuilder stringBuilder = new StringBuilder();

    public HpackDecoder(int maxMemorySize) {
        this.maxMemorySizeHard = maxMemorySize;
        this.maxMemorySizeSoft = maxMemorySize;
        headerTable = new Hpack.HeaderField[DEFAULT_RING_BUFFER_SIZE];
    }

    public HpackDecoder() {
        this(Hpack.DEFAULT_TABLE_SIZE);
    }

    /**
     * 解码提供的数据. 如果此方法在缓冲区中留下数据，则应压缩此缓冲区，以便保存该数据, 除非没有更多的数据，在这种情况下，这应该算是一个协议错误.
     *
     * @param buffer The buffer
     *
     * @throws HpackException 如果打包的数据无效
     */
    public void decode(ByteBuffer buffer) throws HpackException {
        while (buffer.hasRemaining()) {
            int originalPos = buffer.position();
            byte b = buffer.get();
            if ((b & 0b10000000) != 0) {
                //如果设置了第一个位，则为索引的header字段
                buffer.position(buffer.position() - 1); //unget the byte
                int index = Hpack.decodeInteger(buffer, 7); //prefix is 7
                if (index == -1) {
                    buffer.position(originalPos);
                    return;
                } else if(index == 0) {
                    throw new HpackException(
                            sm.getString("hpackdecoder.zeroNotValidHeaderTableIndex"));
                }
                handleIndex(index);
            } else if ((b & 0b01000000) != 0) {
                //Literal Header Field with Incremental Indexing
                String headerName = readHeaderName(buffer, 6);
                if (headerName == null) {
                    buffer.position(originalPos);
                    return;
                }
                String headerValue = readHpackString(buffer);
                if (headerValue == null) {
                    buffer.position(originalPos);
                    return;
                }
                emitHeader(headerName, headerValue);
                addEntryToHeaderTable(new Hpack.HeaderField(headerName, headerValue));
            } else if ((b & 0b11110000) == 0) {
                //Literal Header Field without Indexing
                String headerName = readHeaderName(buffer, 4);
                if (headerName == null) {
                    buffer.position(originalPos);
                    return;
                }
                String headerValue = readHpackString(buffer);
                if (headerValue == null) {
                    buffer.position(originalPos);
                    return;
                }
                emitHeader(headerName, headerValue);
            } else if ((b & 0b11110000) == 0b00010000) {
                //Literal Header Field never indexed
                String headerName = readHeaderName(buffer, 4);
                if (headerName == null) {
                    buffer.position(originalPos);
                    return;
                }
                String headerValue = readHpackString(buffer);
                if (headerValue == null) {
                    buffer.position(originalPos);
                    return;
                }
                emitHeader(headerName, headerValue);
            } else if ((b & 0b11100000) == 0b00100000) {
                // 上下文更新最大表大小变化
                if (!handleMaxMemorySizeChange(buffer, originalPos)) {
                    return;
                }
            } else {
                throw new RuntimeException("Not yet implemented");
            }
        }
    }

    private boolean handleMaxMemorySizeChange(ByteBuffer buffer, int originalPos) throws HpackException {
        if (headerCount != 0) {
            throw new HpackException(sm.getString("hpackdecoder.tableSizeUpdateNotAtStart"));
        }
        buffer.position(buffer.position() - 1); //unget the byte
        int size = Hpack.decodeInteger(buffer, 5);
        if (size == -1) {
            buffer.position(originalPos);
            return false;
        }
        if (size > maxMemorySizeHard) {
            throw new HpackException();
        }
        maxMemorySizeSoft = size;
        if (currentMemorySize > maxMemorySizeSoft) {
            int newTableSlots = filledTableSlots;
            int tableLength = headerTable.length;
            int newSize = currentMemorySize;
            while (newSize > maxMemorySizeSoft) {
                int clearIndex = firstSlotPosition;
                firstSlotPosition++;
                if (firstSlotPosition == tableLength) {
                    firstSlotPosition = 0;
                }
                Hpack.HeaderField oldData = headerTable[clearIndex];
                headerTable[clearIndex] = null;
                newSize -= oldData.size;
                newTableSlots--;
            }
            this.filledTableSlots = newTableSlots;
            currentMemorySize = newSize;
        }
        return true;
    }

    private String readHeaderName(ByteBuffer buffer, int prefixLength) throws HpackException {
        buffer.position(buffer.position() - 1); //unget the byte
        int index = Hpack.decodeInteger(buffer, prefixLength);
        if (index == -1) {
            return null;
        } else if (index != 0) {
            return handleIndexedHeaderName(index);
        } else {
            return readHpackString(buffer);
        }
    }

    private String readHpackString(ByteBuffer buffer) throws HpackException {
        if (!buffer.hasRemaining()) {
            return null;
        }
        byte data = buffer.get(buffer.position());

        int length = Hpack.decodeInteger(buffer, 7);
        if (buffer.remaining() < length) {
            return null;
        }
        boolean huffman = (data & 0b10000000) != 0;
        if (huffman) {
            return readHuffmanString(length, buffer);
        }
        for (int i = 0; i < length; ++i) {
            stringBuilder.append((char) buffer.get());
        }
        String ret = stringBuilder.toString();
        stringBuilder.setLength(0);
        return ret;
    }

    private String readHuffmanString(int length, ByteBuffer buffer) throws HpackException {
        HPackHuffman.decode(buffer, length, stringBuilder);
        String ret = stringBuilder.toString();
        stringBuilder.setLength(0);
        return ret;
    }

    private String handleIndexedHeaderName(int index) throws HpackException {
        if (index <= Hpack.STATIC_TABLE_LENGTH) {
            return Hpack.STATIC_TABLE[index].name;
        } else {
            // index is 1 based
            if (index > Hpack.STATIC_TABLE_LENGTH + filledTableSlots) {
                throw new HpackException(sm.getString("hpackdecoder.headerTableIndexInvalid",
                        Integer.valueOf(index), Integer.valueOf(Hpack.STATIC_TABLE_LENGTH),
                        Integer.valueOf(filledTableSlots)));
            }
            int adjustedIndex = getRealIndex(index - Hpack.STATIC_TABLE_LENGTH);
            Hpack.HeaderField res = headerTable[adjustedIndex];
            if (res == null) {
                throw new HpackException();
            }
            return res.name;
        }
    }

    /**
     * 处理一个索引的 header
     *
     * @param index The index
     * @throws HpackException
     */
    private void handleIndex(int index) throws HpackException {
        if (index <= Hpack.STATIC_TABLE_LENGTH) {
            addStaticTableEntry(index);
        } else {
            int adjustedIndex = getRealIndex(index - Hpack.STATIC_TABLE_LENGTH);
            Hpack.HeaderField headerField = headerTable[adjustedIndex];
            emitHeader(headerField.name, headerField.value);
        }
    }

    /**
     * 因为使用环形缓冲区类型构造, 不要在数组中移动条目, 需要弄清楚实际使用的索引.
     * <p/>
     * 单元测试专用包
     *
     * @param index hpack的索引
     * @return 数组中真实的索引
     */
    int getRealIndex(int index) {
        //索引是基于一的, 但是表是基于零的, 因此 -1, 也因为环形缓冲区设置的指标是相反的
        //index = 1 is at position firstSlotPosition + filledSlots
        return (firstSlotPosition + (filledTableSlots - index)) % headerTable.length;
    }

    private void addStaticTableEntry(int index) throws HpackException {
        //从静态表添加一个条目.
        //必须是一个有值的条目
        Hpack.HeaderField entry = Hpack.STATIC_TABLE[index];
        if (entry.value == null) {
            throw new HpackException();
        }
        emitHeader(entry.name, entry.value);
    }

    private void addEntryToHeaderTable(Hpack.HeaderField entry) {
        if (entry.size > maxMemorySizeSoft) {
            //它太大了, 因此完全清空表.
            while (filledTableSlots > 0) {
                headerTable[firstSlotPosition] = null;
                firstSlotPosition++;
                if (firstSlotPosition == headerTable.length) {
                    firstSlotPosition = 0;
                }
                filledTableSlots--;
            }
            currentMemorySize = 0;
            return;
        }
        resizeIfRequired();
        int newTableSlots = filledTableSlots + 1;
        int tableLength = headerTable.length;
        int index = (firstSlotPosition + filledTableSlots) % tableLength;
        headerTable[index] = entry;
        int newSize = currentMemorySize + entry.size;
        while (newSize > maxMemorySizeSoft) {
            int clearIndex = firstSlotPosition;
            firstSlotPosition++;
            if (firstSlotPosition == tableLength) {
                firstSlotPosition = 0;
            }
            Hpack.HeaderField oldData = headerTable[clearIndex];
            headerTable[clearIndex] = null;
            newSize -= oldData.size;
            newTableSlots--;
        }
        this.filledTableSlots = newTableSlots;
        currentMemorySize = newSize;
    }

    private void resizeIfRequired() {
        if(filledTableSlots == headerTable.length) {
            Hpack.HeaderField[] newArray = new Hpack.HeaderField[headerTable.length + 10]; //we only grow slowly
            for(int i = 0; i < headerTable.length; ++i) {
                newArray[i] = headerTable[(firstSlotPosition + i) % headerTable.length];
            }
            firstSlotPosition = 0;
            headerTable = newArray;
        }
    }


    /**
     * 由header的接收者实现.
     */
    interface HeaderEmitter {
        /**
         * 传递单个 header 到接收者.
         *
         * @param name  Header name
         * @param value Header value
         * @throws HpackException 如果接收到的Header不符合HTTP／2规范
         */
        void emitHeader(String name, String value) throws HpackException;

        /**
         * 通知header的接收者需要使用给定消息触发流错误, 当调用 {@link #validateHeaders()}时.
         * 当Parser意识到对接收者不可见的错误时, 使用它.
         *
         * @param streamException 重置流时使用的异常
         */
        void setHeaderException(StreamException streamException);

        /**
         * header是否有效地传递给接收方?
         * 解码器需要处理所有的  header保持状态, 即使有问题.
         * 此外, 接收者很容易跟踪header的完整集合是否有效, 自从开始在初始的header的解析和trailer header的解析之间保持状态. 接收者是保存状态的最好的地方.
         *
         * @throws StreamException 如果接收到的header无效
         */
        void validateHeaders() throws StreamException;
    }


    public HeaderEmitter getHeaderEmitter() {
        return headerEmitter;
    }


    void setHeaderEmitter(HeaderEmitter headerEmitter) {
        this.headerEmitter = headerEmitter;
        // Reset limit tracking
        headerCount = 0;
        countedCookie = false;
        headerSize = 0;
    }


    void setMaxHeaderCount(int maxHeaderCount) {
        this.maxHeaderCount = maxHeaderCount;
    }


    void setMaxHeaderSize(int maxHeaderSize) {
        this.maxHeaderSize = maxHeaderSize;
    }


    private void emitHeader(String name, String value) throws HpackException {
        // Header 名称强制小写
        if ("cookie".equals(name)) {
            // 只需计算cookie头一次，因为HTTP/2将其分割成多个header来帮助压缩
            if (!countedCookie) {
                headerCount ++;
                countedCookie = true;
            }
        } else {
            headerCount ++;
        }
        // 开销会有所不同. 主要关注的是， 很多小 header正确的触发限制机制.
        // 因此, 使用开销估计为3，这是小header的最坏情况.
        int inc = 3 + name.length() + value.length();
        headerSize += inc;
        if (!isHeaderCountExceeded() && !isHeaderSizeExceeded(0)) {
            headerEmitter.emitHeader(name, value);
        }
    }


    boolean isHeaderCountExceeded() {
        if (maxHeaderCount < 0) {
            return false;
        }
        return headerCount > maxHeaderCount;
    }


    boolean isHeaderSizeExceeded(int unreadSize) {
        if (maxHeaderSize < 0) {
            return false;
        }
        return (headerSize + unreadSize) > maxHeaderSize;
    }


    boolean isHeaderSwallowSizeExceeded(int unreadSize) {
        if (maxHeaderSize < 0) {
            return false;
        }
        // 在关闭连接之前, 忽略相同的.
        return (headerSize + unreadSize) > (2 * maxHeaderSize);
    }


    //打包私有字段用于测试

    int getFirstSlotPosition() {
        return firstSlotPosition;
    }

    Hpack.HeaderField[] getHeaderTable() {
        return headerTable;
    }

    int getFilledTableSlots() {
        return filledTableSlots;
    }

    int getCurrentMemorySize() {
        return currentMemorySize;
    }

    int getMaxMemorySizeSoft() {
        return maxMemorySizeSoft;
    }
}
