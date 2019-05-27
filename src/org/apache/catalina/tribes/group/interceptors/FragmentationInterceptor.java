package org.apache.catalina.tribes.group.interceptors;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.group.InterceptorPayload;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 分段拦截器将大消息拆分为较小的消息，并在另一端将它们组装.
 *
 * <br><b>配置选项</b><br>
 * OrderInterceptor.expire=&lt;milliseconds&gt; - 将碎片保存在内存中等待其余的碎片到达的时间<b>default=60,000ms -&gt; 60seconds</b>
 * 这个设置用于避免 OutOfMemoryErrors<br>
 * OrderInterceptor.maxSize=&lt;消息最大大小&gt; - 消息字节数 <b>default=1024*100 (around a tenth of a MB)</b><br>
 */
public class FragmentationInterceptor extends ChannelInterceptorBase {
    private static final Log log = LogFactory.getLog(FragmentationInterceptor.class);
    protected static final StringManager sm = StringManager.getManager(FragmentationInterceptor.class);

    protected final HashMap<FragKey, FragCollection> fragpieces = new HashMap<>();
    private int maxSize = 1024*100;
    private long expire = 1000 * 60; //one minute expiration
    protected final boolean deepclone = true;


    @Override
    public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload) throws ChannelException {
        int size = msg.getMessage().getLength();
        boolean frag = (size>maxSize) && okToProcess(msg.getOptions());
        if ( frag ) {
            frag(destination, msg, payload);
        } else {
            msg.getMessage().append(frag);
            super.sendMessage(destination, msg, payload);
        }
    }

    @Override
    public void messageReceived(ChannelMessage msg) {
        boolean isFrag = XByteBuffer.toBoolean(msg.getMessage().getBytesDirect(),msg.getMessage().getLength()-1);
        msg.getMessage().trim(1);
        if ( isFrag ) {
            defrag(msg);
        } else {
            super.messageReceived(msg);
        }
    }


    public FragCollection getFragCollection(FragKey key, ChannelMessage msg) {
        FragCollection coll = fragpieces.get(key);
        if ( coll == null ) {
            synchronized (fragpieces) {
                coll = fragpieces.get(key);
                if ( coll == null ) {
                    coll = new FragCollection(msg);
                    fragpieces.put(key, coll);
                }
            }
        }
        return coll;
    }

    public void removeFragCollection(FragKey key) {
        fragpieces.remove(key);
    }

    public void defrag(ChannelMessage msg ) {
        FragKey key = new FragKey(msg.getUniqueId());
        FragCollection coll = getFragCollection(key,msg);
        coll.addMessage((ChannelMessage)msg.deepclone());

        if ( coll.complete() ) {
            removeFragCollection(key);
            ChannelMessage complete = coll.assemble();
            super.messageReceived(complete);

        }
    }

    public void frag(Member[] destination, ChannelMessage msg, InterceptorPayload payload) throws ChannelException {
        int size = msg.getMessage().getLength();

        int count = ((size / maxSize )+(size%maxSize==0?0:1));
        ChannelMessage[] messages = new ChannelMessage[count];
        int remaining = size;
        for ( int i=0; i<count; i++ ) {
            ChannelMessage tmp = (ChannelMessage)msg.clone();
            int offset = (i*maxSize);
            int length = Math.min(remaining,maxSize);
            tmp.getMessage().clear();
            tmp.getMessage().append(msg.getMessage().getBytesDirect(),offset,length);
            //add the msg nr
            //tmp.getMessage().append(XByteBuffer.toBytes(i),0,4);
            tmp.getMessage().append(i);
            //add the total nr of messages
            //tmp.getMessage().append(XByteBuffer.toBytes(count),0,4);
            tmp.getMessage().append(count);
            //add true as the frag flag
            //byte[] flag = XByteBuffer.toBytes(true);
            //tmp.getMessage().append(flag,0,flag.length);
            tmp.getMessage().append(true);
            messages[i] = tmp;
            remaining -= length;

        }
        for ( int i=0; i<messages.length; i++ ) {
            super.sendMessage(destination,messages[i],payload);
        }
    }

    @Override
    public void heartbeat() {
        try {
            Set<FragKey> set = fragpieces.keySet();
            Object[] keys = set.toArray();
            for ( int i=0; i<keys.length; i++ ) {
                FragKey key = (FragKey)keys[i];
                if ( key != null && key.expired(getExpire()) )
                    removeFragCollection(key);
            }
        }catch ( Exception x ) {
            if ( log.isErrorEnabled() ) {
                log.error(sm.getString("fragmentationInterceptor.heartbeat.failed"),x);
            }
        }
        super.heartbeat();
    }


    public int getMaxSize() {
        return maxSize;
    }

    public long getExpire() {
        return expire;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public void setExpire(long expire) {
        this.expire = expire;
    }

    public static class FragCollection {
        private final long received = System.currentTimeMillis();
        private final ChannelMessage msg;
        private final XByteBuffer[] frags;
        public FragCollection(ChannelMessage msg) {
            //get the total messages
            int count = XByteBuffer.toInt(msg.getMessage().getBytesDirect(),msg.getMessage().getLength()-4);
            frags = new XByteBuffer[count];
            this.msg = msg;
        }

        public void addMessage(ChannelMessage msg) {
            //remove the total messages
            msg.getMessage().trim(4);
            //get the msg nr
            int nr = XByteBuffer.toInt(msg.getMessage().getBytesDirect(),msg.getMessage().getLength()-4);
            //remove the msg nr
            msg.getMessage().trim(4);
            frags[nr] = msg.getMessage();

        }

        public boolean complete() {
            boolean result = true;
            for ( int i=0; (i<frags.length) && (result); i++ ) result = (frags[i] != null);
            return result;
        }

        public ChannelMessage assemble() {
            if ( !complete() ) throw new IllegalStateException(sm.getString("fragmentationInterceptor.fragments.missing"));
            int buffersize = 0;
            for (int i=0; i<frags.length; i++ ) buffersize += frags[i].getLength();
            XByteBuffer buf = new XByteBuffer(buffersize,false);
            msg.setMessage(buf);
            for ( int i=0; i<frags.length; i++ ) {
                msg.getMessage().append(frags[i].getBytesDirect(),0,frags[i].getLength());
            }
            return msg;
        }

        public boolean expired(long expire) {
            return (System.currentTimeMillis()-received)>expire;
        }
    }

    public static class FragKey {
        private final byte[] uniqueId;
        private final long received = System.currentTimeMillis();
        public FragKey(byte[] id ) {
            this.uniqueId = id;
        }
        @Override
        public int hashCode() {
            return XByteBuffer.toInt(uniqueId,0);
        }

        @Override
        public boolean equals(Object o ) {
            if ( o instanceof FragKey ) {
	            return Arrays.equals(uniqueId,((FragKey)o).uniqueId);
	        } else return false;

        }

        public boolean expired(long expire) {
            return (System.currentTimeMillis()-received)>expire;
        }
    }
}