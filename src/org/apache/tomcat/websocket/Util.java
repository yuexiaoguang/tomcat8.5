package org.apache.tomcat.websocket;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.websocket.CloseReason.CloseCode;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.Decoder;
import javax.websocket.Decoder.Binary;
import javax.websocket.Decoder.BinaryStream;
import javax.websocket.Decoder.Text;
import javax.websocket.Decoder.TextStream;
import javax.websocket.DeploymentException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import javax.websocket.Session;

import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.pojo.PojoMessageHandlerPartialBinary;
import org.apache.tomcat.websocket.pojo.PojoMessageHandlerWholeBinary;
import org.apache.tomcat.websocket.pojo.PojoMessageHandlerWholeText;

/**
 * {@link org.apache.tomcat.websocket} 包中内部使用的工具类.
 */
public class Util {

    private static final StringManager sm = StringManager.getManager(Util.class);
    private static final Queue<SecureRandom> randoms =
            new ConcurrentLinkedQueue<>();

    private Util() {
        // Hide default constructor
    }


    static boolean isControl(byte opCode) {
        return (opCode & 0x08) != 0;
    }


    static boolean isText(byte opCode) {
        return opCode == Constants.OPCODE_TEXT;
    }


    static boolean isContinuation(byte opCode) {
        return opCode == Constants.OPCODE_CONTINUATION;
    }


    static CloseCode getCloseCode(int code) {
        if (code > 2999 && code < 5000) {
            return CloseCodes.getCloseCode(code);
        }
        switch (code) {
            case 1000:
                return CloseCodes.NORMAL_CLOSURE;
            case 1001:
                return CloseCodes.GOING_AWAY;
            case 1002:
                return CloseCodes.PROTOCOL_ERROR;
            case 1003:
                return CloseCodes.CANNOT_ACCEPT;
            case 1004:
                // 不应该在返回CloseCodes.RESERVED的关闭帧中使用;
                return CloseCodes.PROTOCOL_ERROR;
            case 1005:
                // 不应该在返回CloseCodes.NO_STATUS_CODE的关闭帧中使用;
                return CloseCodes.PROTOCOL_ERROR;
            case 1006:
                // 不应该在返回CloseCodes.CLOSED_ABNORMALLY的关闭帧中使用;
                return CloseCodes.PROTOCOL_ERROR;
            case 1007:
                return CloseCodes.NOT_CONSISTENT;
            case 1008:
                return CloseCodes.VIOLATED_POLICY;
            case 1009:
                return CloseCodes.TOO_BIG;
            case 1010:
                return CloseCodes.NO_EXTENSION;
            case 1011:
                return CloseCodes.UNEXPECTED_CONDITION;
            case 1012:
                // Not in RFC6455
                // return CloseCodes.SERVICE_RESTART;
                return CloseCodes.PROTOCOL_ERROR;
            case 1013:
                // Not in RFC6455
                // return CloseCodes.TRY_AGAIN_LATER;
                return CloseCodes.PROTOCOL_ERROR;
            case 1015:
                // 不应该在返回CloseCodes.TLS_HANDSHAKE_FAILURE的关闭帧中使用;
                return CloseCodes.PROTOCOL_ERROR;
            default:
                return CloseCodes.PROTOCOL_ERROR;
        }
    }


    static byte[] generateMask() {
        // SecureRandom 不是线程安全的, 所以需要确保每次只有一个线程使用它.
        // 理论上，池可以增长到与请求处理线程的数量相同的大小. 事实上，它会小很多.

        // Get a SecureRandom from the pool
        SecureRandom sr = randoms.poll();

        // 如果无效, 重新生成一个
        if (sr == null) {
            try {
                sr = SecureRandom.getInstance("SHA1PRNG");
            } catch (NoSuchAlgorithmException e) {
                // 回到平台默认
                sr = new SecureRandom();
            }
        }

        // 生成掩码
        byte[] result = new byte[4];
        sr.nextBytes(result);

        // 将SecureRandom 放回池
        randoms.add(sr);

        return result;
    }


    static Class<?> getMessageType(MessageHandler listener) {
        return Util.getGenericType(MessageHandler.class,
                listener.getClass()).getClazz();
    }


    private static Class<?> getDecoderType(Class<? extends Decoder> decoder) {
        return Util.getGenericType(Decoder.class, decoder).getClazz();
    }


    static Class<?> getEncoderType(Class<? extends Encoder> encoder) {
        return Util.getGenericType(Encoder.class, encoder).getClazz();
    }


    private static <T> TypeResult getGenericType(Class<T> type,
            Class<? extends T> clazz) {

        // 查看这个类是否实现了感兴趣的接口

        // 获取所有接口
        Type[] interfaces = clazz.getGenericInterfaces();
        for (Type iface : interfaces) {
            // 只需要检查使用泛型的接口
            if (iface instanceof ParameterizedType) {
                ParameterizedType pi = (ParameterizedType) iface;
                // 寻找感兴趣的接口
                if (pi.getRawType() instanceof Class) {
                    if (type.isAssignableFrom((Class<?>) pi.getRawType())) {
                        return getTypeParameter(
                                clazz, pi.getActualTypeArguments()[0]);
                    }
                }
            }
        }

        // 没有在这个类上找到接口. 查看父类.
        @SuppressWarnings("unchecked")
        Class<? extends T> superClazz =
                (Class<? extends T>) clazz.getSuperclass();
        if (superClazz == null) {
            // 都没有找到
            return null;
        }

        TypeResult superClassTypeResult = getGenericType(type, superClazz);
        int dimension = superClassTypeResult.getDimension();
        if (superClassTypeResult.getIndex() == -1 && dimension == 0) {
            // 超类实现接口并为感兴趣的接口定义显式类型
            return superClassTypeResult;
        }

        if (superClassTypeResult.getIndex() > -1) {
            // 超类实现接口并为感兴趣的接口定义未知类型
            // 将未知类型映射到该类中定义的泛型类型
            ParameterizedType superClassType =
                    (ParameterizedType) clazz.getGenericSuperclass();
            TypeResult result = getTypeParameter(clazz,
                    superClassType.getActualTypeArguments()[
                            superClassTypeResult.getIndex()]);
            result.incrementDimension(superClassTypeResult.getDimension());
            if (result.getClazz() != null && result.getDimension() > 0) {
                superClassTypeResult = result;
            } else {
                return result;
            }
        }

        if (superClassTypeResult.getDimension() > 0) {
            StringBuilder className = new StringBuilder();
            for (int i = 0; i < dimension; i++) {
                className.append('[');
            }
            className.append('L');
            className.append(superClassTypeResult.getClazz().getCanonicalName());
            className.append(';');

            Class<?> arrayClazz;
            try {
                arrayClazz = Class.forName(className.toString());
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(e);
            }

            return new TypeResult(arrayClazz, -1, 0);
        }

        // 错误将被进一步记录在调用堆栈上
        return null;
    }


    /*
     * 对于泛型参数, 返回所使用的类或如果类型未知, 类定义中的类型索引
     */
    private static TypeResult getTypeParameter(Class<?> clazz, Type argType) {
        if (argType instanceof Class<?>) {
            return new TypeResult((Class<?>) argType, -1, 0);
        } else if (argType instanceof ParameterizedType) {
            return new TypeResult((Class<?>)((ParameterizedType) argType).getRawType(), -1, 0);
        } else if (argType instanceof GenericArrayType) {
            Type arrayElementType = ((GenericArrayType) argType).getGenericComponentType();
            TypeResult result = getTypeParameter(clazz, arrayElementType);
            result.incrementDimension(1);
            return result;
        } else {
            TypeVariable<?>[] tvs = clazz.getTypeParameters();
            for (int i = 0; i < tvs.length; i++) {
                if (tvs[i].equals(argType)) {
                    return new TypeResult(null, i, 0);
                }
            }
            return null;
        }
    }


    public static boolean isPrimitive(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            return true;
        } else if(clazz.equals(Boolean.class) ||
                clazz.equals(Byte.class) ||
                clazz.equals(Character.class) ||
                clazz.equals(Double.class) ||
                clazz.equals(Float.class) ||
                clazz.equals(Integer.class) ||
                clazz.equals(Long.class) ||
                clazz.equals(Short.class)) {
            return true;
        }
        return false;
    }


    public static Object coerceToType(Class<?> type, String value) {
        if (type.equals(String.class)) {
            return value;
        } else if (type.equals(boolean.class) || type.equals(Boolean.class)) {
            return Boolean.valueOf(value);
        } else if (type.equals(byte.class) || type.equals(Byte.class)) {
            return Byte.valueOf(value);
        } else if (type.equals(char.class) || type.equals(Character.class)) {
            return Character.valueOf(value.charAt(0));
        } else if (type.equals(double.class) || type.equals(Double.class)) {
            return Double.valueOf(value);
        } else if (type.equals(float.class) || type.equals(Float.class)) {
            return Float.valueOf(value);
        } else if (type.equals(int.class) || type.equals(Integer.class)) {
            return Integer.valueOf(value);
        } else if (type.equals(long.class) || type.equals(Long.class)) {
            return Long.valueOf(value);
        } else if (type.equals(short.class) || type.equals(Short.class)) {
            return Short.valueOf(value);
        } else {
            throw new IllegalArgumentException(sm.getString(
                    "util.invalidType", value, type.getName()));
        }
    }


    public static List<DecoderEntry> getDecoders(
            List<Class<? extends Decoder>> decoderClazzes)
                    throws DeploymentException{

        List<DecoderEntry> result = new ArrayList<>();
        if (decoderClazzes != null) {
            for (Class<? extends Decoder> decoderClazz : decoderClazzes) {
                // 需要实例化解码器以确保它是有效的，并且如果没有部署，则可能失败
                @SuppressWarnings("unused")
                Decoder instance;
                try {
                    instance = decoderClazz.getConstructor().newInstance();
                } catch (ReflectiveOperationException e) {
                    throw new DeploymentException(
                            sm.getString("pojoMethodMapping.invalidDecoder",
                                    decoderClazz.getName()), e);
                }
                DecoderEntry entry = new DecoderEntry(
                        Util.getDecoderType(decoderClazz), decoderClazz);
                result.add(entry);
            }
        }

        return result;
    }


    static Set<MessageHandlerResult> getMessageHandlers(Class<?> target,
            MessageHandler listener, EndpointConfig endpointConfig,
            Session session) {

        // 永远不会超过2个字节
        Set<MessageHandlerResult> results = new HashSet<>(2);

        // Simple cases - 处理程序已经接受了帧处理代码所期望的类型之一
        if (String.class.isAssignableFrom(target)) {
            MessageHandlerResult result =
                    new MessageHandlerResult(listener,
                            MessageHandlerResultType.TEXT);
            results.add(result);
        } else if (ByteBuffer.class.isAssignableFrom(target)) {
            MessageHandlerResult result =
                    new MessageHandlerResult(listener,
                            MessageHandlerResultType.BINARY);
            results.add(result);
        } else if (PongMessage.class.isAssignableFrom(target)) {
            MessageHandlerResult result =
                    new MessageHandlerResult(listener,
                            MessageHandlerResultType.PONG);
            results.add(result);
        // 处理程序需要打包和可选的解码器，以将其转换为帧处理代码所期望的类型之一
        } else if (byte[].class.isAssignableFrom(target)) {
            boolean whole = MessageHandler.Whole.class.isAssignableFrom(listener.getClass());
            MessageHandlerResult result = new MessageHandlerResult(
                    whole ? new PojoMessageHandlerWholeBinary(listener,
                                    getOnMessageMethod(listener), session,
                                    endpointConfig, matchDecoders(target, endpointConfig, true),
                                    new Object[1], 0, true, -1, false, -1) :
                            new PojoMessageHandlerPartialBinary(listener,
                                    getOnMessagePartialMethod(listener), session,
                                    new Object[2], 0, true, 1, -1, -1),
                    MessageHandlerResultType.BINARY);
            results.add(result);
        } else if (InputStream.class.isAssignableFrom(target)) {
            MessageHandlerResult result = new MessageHandlerResult(
                    new PojoMessageHandlerWholeBinary(listener,
                            getOnMessageMethod(listener), session,
                            endpointConfig, matchDecoders(target, endpointConfig, true),
                            new Object[1], 0, true, -1, true, -1),
                    MessageHandlerResultType.BINARY);
            results.add(result);
        } else if (Reader.class.isAssignableFrom(target)) {
            MessageHandlerResult result = new MessageHandlerResult(
                    new PojoMessageHandlerWholeText(listener,
                            getOnMessageMethod(listener), session,
                            endpointConfig, matchDecoders(target, endpointConfig, false),
                            new Object[1], 0, true, -1, -1),
                    MessageHandlerResultType.TEXT);
            results.add(result);
        } else {
            // 处理程序需要打包和可选的解码器，以将其转换为帧处理代码所期望的类型之一
            DecoderMatch decoderMatch = matchDecoders(target, endpointConfig);
            Method m = getOnMessageMethod(listener);
            if (decoderMatch.getBinaryDecoders().size() > 0) {
                MessageHandlerResult result = new MessageHandlerResult(
                        new PojoMessageHandlerWholeBinary(listener, m, session,
                                endpointConfig,
                                decoderMatch.getBinaryDecoders(), new Object[1],
                                0, false, -1, false, -1),
                                MessageHandlerResultType.BINARY);
                results.add(result);
            }
            if (decoderMatch.getTextDecoders().size() > 0) {
                MessageHandlerResult result = new MessageHandlerResult(
                        new PojoMessageHandlerWholeText(listener, m, session,
                                endpointConfig,
                                decoderMatch.getTextDecoders(), new Object[1],
                                0, false, -1, -1),
                                MessageHandlerResultType.TEXT);
                results.add(result);
            }
        }

        if (results.size() == 0) {
            throw new IllegalArgumentException(
                    sm.getString("wsSession.unknownHandler", listener, target));
        }

        return results;
    }

    private static List<Class<? extends Decoder>> matchDecoders(Class<?> target,
            EndpointConfig endpointConfig, boolean binary) {
        DecoderMatch decoderMatch = matchDecoders(target, endpointConfig);
        if (binary) {
            if (decoderMatch.getBinaryDecoders().size() > 0) {
                return decoderMatch.getBinaryDecoders();
            }
        } else if (decoderMatch.getTextDecoders().size() > 0) {
            return decoderMatch.getTextDecoders();
        }
        return null;
    }

    private static DecoderMatch matchDecoders(Class<?> target,
            EndpointConfig endpointConfig) {
        DecoderMatch decoderMatch;
        try {
            List<Class<? extends Decoder>> decoders =
                    endpointConfig.getDecoders();
            List<DecoderEntry> decoderEntries = getDecoders(decoders);
            decoderMatch = new DecoderMatch(target, decoderEntries);
        } catch (DeploymentException e) {
            throw new IllegalArgumentException(e);
        }
        return decoderMatch;
    }

    public static void parseExtensionHeader(List<Extension> extensions,
            String header) {
        // The relevant ABNF for the Sec-WebSocket-Extensions is as follows:
        //      extension-list = 1#extension
        //      extension = extension-token *( ";" extension-param )
        //      extension-token = registered-token
        //      registered-token = token
        //      extension-param = token [ "=" (token | quoted-string) ]
        //             ; When using the quoted-string syntax variant, the value
        //             ; after quoted-string unescaping MUST conform to the
        //             ; 'token' ABNF.
        //
        // 将参数值限制为token或“引号token”使header的解析显著简化，并允许采用许多快捷方式.

        // 第一步, 使用','分隔符将header分割成单独的扩展名
        String unparsedExtensions[] = header.split(",");
        for (String unparsedExtension : unparsedExtensions) {
            // 第二步, 使用';'作为分隔符, 将扩展名分割为注册名称和参数/值对
            String unparsedParameters[] = unparsedExtension.split(";");
            WsExtension extension = new WsExtension(unparsedParameters[0].trim());

            for (int i = 1; i < unparsedParameters.length; i++) {
                int equalsPos = unparsedParameters[i].indexOf('=');
                String name;
                String value;
                if (equalsPos == -1) {
                    name = unparsedParameters[i].trim();
                    value = null;
                } else {
                    name = unparsedParameters[i].substring(0, equalsPos).trim();
                    value = unparsedParameters[i].substring(equalsPos + 1).trim();
                    int len = value.length();
                    if (len > 1) {
                        if (value.charAt(0) == '\"' && value.charAt(len - 1) == '\"') {
                            value = value.substring(1, value.length() - 1);
                        }
                    }
                }
                // 确保值不包含任何分隔符，因为这将指示出出错的地方
                if (containsDelims(name) || containsDelims(value)) {
                    throw new IllegalArgumentException(sm.getString(
                            "util.notToken", name, value));
                }
                if (value != null &&
                        (value.indexOf(',') > -1 || value.indexOf(';') > -1 ||
                        value.indexOf('\"') > -1 || value.indexOf('=') > -1)) {
                    throw new IllegalArgumentException(sm.getString("", value));
                }
                extension.addParameter(new WsExtensionParameter(name, value));
            }
            extensions.add(extension);
        }
    }


    private static boolean containsDelims(String input) {
        if (input == null || input.length() == 0) {
            return false;
        }
        for (char c : input.toCharArray()) {
            switch (c) {
                case ',':
                case ';':
                case '\"':
                case '=':
                    return true;
                default:
                    // NO_OP
            }

        }
        return false;
    }

    private static Method getOnMessageMethod(MessageHandler listener) {
        try {
            return listener.getClass().getMethod("onMessage", Object.class);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new IllegalArgumentException(
                    sm.getString("util.invalidMessageHandler"), e);
        }
    }

    private static Method getOnMessagePartialMethod(MessageHandler listener) {
        try {
            return listener.getClass().getMethod("onMessage", Object.class, Boolean.TYPE);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new IllegalArgumentException(
                    sm.getString("util.invalidMessageHandler"), e);
        }
    }


    public static class DecoderMatch {

        private final List<Class<? extends Decoder>> textDecoders =
                new ArrayList<>();
        private final List<Class<? extends Decoder>> binaryDecoders =
                new ArrayList<>();
        private final Class<?> target;

        public DecoderMatch(Class<?> target, List<DecoderEntry> decoderEntries) {
            this.target = target;
            for (DecoderEntry decoderEntry : decoderEntries) {
                if (decoderEntry.getClazz().isAssignableFrom(target)) {
                    if (Binary.class.isAssignableFrom(
                            decoderEntry.getDecoderClazz())) {
                        binaryDecoders.add(decoderEntry.getDecoderClazz());
                        // willDecode() 方法意味着该解码器不确定是否解码消息，因此需要对其他匹配进行检查
                    } else if (BinaryStream.class.isAssignableFrom(
                            decoderEntry.getDecoderClazz())) {
                        binaryDecoders.add(decoderEntry.getDecoderClazz());
                        // 流解码器必须处理消息，这样就不会有更多的解码器匹配
                        break;
                    } else if (Text.class.isAssignableFrom(
                            decoderEntry.getDecoderClazz())) {
                        textDecoders.add(decoderEntry.getDecoderClazz());
                        // willDecode() 方法意味着该解码器不确定是否解码消息，因此需要对其他匹配进行检查
                    } else if (TextStream.class.isAssignableFrom(
                            decoderEntry.getDecoderClazz())) {
                        textDecoders.add(decoderEntry.getDecoderClazz());
                        // 流解码器必须处理消息，这样就不会有更多的解码器匹配
                        break;
                    } else {
                        throw new IllegalArgumentException(
                                sm.getString("util.unknownDecoderType"));
                    }
                }
            }
        }


        public List<Class<? extends Decoder>> getTextDecoders() {
            return textDecoders;
        }


        public List<Class<? extends Decoder>> getBinaryDecoders() {
            return binaryDecoders;
        }


        public Class<?> getTarget() {
            return target;
        }


        public boolean hasMatches() {
            return (textDecoders.size() > 0) || (binaryDecoders.size() > 0);
        }
    }


    private static class TypeResult {
        private final Class<?> clazz;
        private final int index;
        private int dimension;

        public TypeResult(Class<?> clazz, int index, int dimension) {
            this.clazz= clazz;
            this.index = index;
            this.dimension = dimension;
        }

        public Class<?> getClazz() {
            return clazz;
        }

        public int getIndex() {
            return index;
        }

        public int getDimension() {
            return dimension;
        }

        public void incrementDimension(int inc) {
            dimension += inc;
        }
    }
}
