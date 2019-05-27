package javax.websocket;

public interface MessageHandler {

    interface Partial<T> extends MessageHandler {

        /**
         * 当消息的一部分可以处理时调用.
         *
         * @param messagePart   消息部分
         * @param last          <code>true</code>如果是消息的最后一部分, 否则<code>false</code>
         */
        void onMessage(T messagePart, boolean last);
    }

    interface Whole<T> extends MessageHandler {

        /**
         * 当可以处理整个消息时调用.
         *
         * @param message
         */
        void onMessage(T message);
    }
}
