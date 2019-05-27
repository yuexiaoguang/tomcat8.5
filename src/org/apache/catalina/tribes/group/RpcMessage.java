package org.apache.catalina.tribes.group;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;

import org.apache.catalina.tribes.util.Arrays;

public class RpcMessage implements Externalizable {

    protected Serializable message;
    protected byte[] uuid;
    protected byte[] rpcId;
    protected boolean reply = false;

    public RpcMessage() {
        //for serialization
    }

    public RpcMessage(byte[] rpcId, byte[] uuid, Serializable message) {
        this.rpcId = rpcId;
        this.uuid = uuid;
        this.message = message;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException,ClassNotFoundException {
        reply = in.readBoolean();
        int length = in.readInt();
        uuid = new byte[length];
        in.readFully(uuid);
        length = in.readInt();
        rpcId = new byte[length];
        in.readFully(rpcId);
        message = (Serializable)in.readObject();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeBoolean(reply);
        out.writeInt(uuid.length);
        out.write(uuid, 0, uuid.length);
        out.writeInt(rpcId.length);
        out.write(rpcId, 0, rpcId.length);
        out.writeObject(message);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("RpcMessage[");
        buf.append(super.toString());
        buf.append("] rpcId=");
        buf.append(Arrays.toString(rpcId));
        buf.append("; uuid=");
        buf.append(Arrays.toString(uuid));
        buf.append("; msg=");
        buf.append(message);
        return buf.toString();
    }

    public static class NoRpcChannelReply extends RpcMessage {
        public NoRpcChannelReply() {

        }

        public NoRpcChannelReply(byte[] rpcid, byte[] uuid) {
            super(rpcid,uuid,null);
            reply = true;
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            reply = true;
            int length = in.readInt();
            uuid = new byte[length];
            in.readFully(uuid);
            length = in.readInt();
            rpcId = new byte[length];
            in.readFully(rpcId);
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeInt(uuid.length);
            out.write(uuid, 0, uuid.length);
            out.writeInt(rpcId.length);
            out.write(rpcId, 0, rpcId.length);
        }
    }


}
