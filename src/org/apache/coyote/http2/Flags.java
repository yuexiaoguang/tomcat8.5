package org.apache.coyote.http2;

public class Flags {

    private Flags() {
        // Utility class. Hide default constructor
    }


    public static boolean isEndOfStream(int flags) {
        return (flags & 0x01) != 0;
    }


    public static boolean isAck(int flags) {
        return (flags & 0x01) != 0;
    }


    public static boolean isEndOfHeaders(int flags) {
        return (flags & 0x04) != 0;
    }


    public static boolean hasPadding(int flags) {
        return (flags & 0x08) != 0;
    }


    public static boolean hasPriority(int flags) {
        return (flags & 0x20) != 0;
    }
}
