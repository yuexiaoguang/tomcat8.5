package org.apache.catalina.ha.backend;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/*
 * Sender使用多播套接字代理.
 */
public class MultiCastSender
    implements Sender {

    private static final Log log = LogFactory.getLog(HeartbeatListener.class);

    HeartbeatListener config = null;

    /* for multicasting stuff */
    MulticastSocket s = null;
    InetAddress group = null;

    @Override
    public void init(HeartbeatListener config) throws Exception {
        this.config = config;
    }

    @Override
    public int send(String mess) throws Exception {
        if (s == null) {
            try {
                group = InetAddress.getByName(config.getGroup());
                if (config.getHost() != null) {
                    InetAddress addr =  InetAddress.getByName(config.getHost());
                    InetSocketAddress addrs = new InetSocketAddress(addr, config.getMultiport());
                    s = new MulticastSocket(addrs);
                } else
                    s = new MulticastSocket(config.getMultiport());

                s.setTimeToLive(config.getTtl());
                s.joinGroup(group);
            } catch (Exception ex) {
                log.error("Unable to use multicast: " + ex);
                s = null;
                return -1;
            }
        }

        byte[] buf;
        buf = mess.getBytes(StandardCharsets.US_ASCII);
        DatagramPacket data = new DatagramPacket(buf, buf.length, group, config.getMultiport());
        try {
            s.send(data);
        } catch (Exception ex) {
            log.error("Unable to send collected load information: " + ex);
            s.close();
            s = null;
            return -1;
        }
        return 0;
    }

}
