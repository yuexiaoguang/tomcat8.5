package org.apache.catalina.tribes.transport;

import org.apache.catalina.tribes.io.XByteBuffer;

/**
 * Manifest constants for the <code>org.apache.catalina.tribes.transport</code>
 * package.
 */
public class Constants {

    public static final String Package = "org.apache.catalina.tribes.transport";

    /*
     * Do not change any of these values!
     */
    public static final byte[] ACK_DATA = new byte[] {6, 2, 3};
    public static final byte[] FAIL_ACK_DATA = new byte[] {11, 0, 5};
    public static final byte[] ACK_COMMAND = XByteBuffer.createDataPackage(ACK_DATA);
    public static final byte[] FAIL_ACK_COMMAND = XByteBuffer.createDataPackage(FAIL_ACK_DATA);

}
