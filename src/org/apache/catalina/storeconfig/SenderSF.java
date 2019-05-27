package org.apache.catalina.storeconfig;

import java.io.PrintWriter;

import org.apache.catalina.tribes.transport.MultiPointSender;
import org.apache.catalina.tribes.transport.ReplicationTransmitter;

/**
 * 生成 Sender Element
 */
public class SenderSF extends StoreFactoryBase {

    /**
     * 保存指定的 Sender 子级.
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent 这个元素缩进的空格数量
     * @param aSender 要保存属性的 Channel
     *
     * @exception Exception 保存期间发生异常
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aSender,
            StoreDescription parentDesc) throws Exception {
        if (aSender instanceof ReplicationTransmitter) {
            ReplicationTransmitter transmitter = (ReplicationTransmitter) aSender;
            // 保存嵌套的 <Transport> 元素
            MultiPointSender transport = transmitter.getTransport();
            if (transport != null) {
                storeElement(aWriter, indent, transport);
            }
       }
    }
}