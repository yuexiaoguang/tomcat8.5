package javax.security.auth.message;

public class MessagePolicy {

    private final TargetPolicy[] targetPolicies;
    private final boolean mandatory;

    public MessagePolicy(TargetPolicy[] targetPolicies, boolean mandatory) {
        if (targetPolicies == null) {
            throw new IllegalArgumentException("targetPolicies is null");
        }
        this.targetPolicies = targetPolicies;
        this.mandatory = mandatory;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public TargetPolicy[] getTargetPolicies() {
        if (targetPolicies.length == 0) {
            return null;
        }
        return targetPolicies;
    }

    public static interface ProtectionPolicy {

        static String AUTHENTICATE_SENDER = "#authenticateSender";
        static String AUTHENTICATE_CONTENT = "#authenticateContent";
        static String AUTHENTICATE_RECIPIENT = "#authenticateRecipient";

        String getID();
    }

    public static interface Target {

        Object get(MessageInfo messageInfo);

        void remove(MessageInfo messageInfo);

        void put(MessageInfo messageInfo, Object data);
    }

    public static class TargetPolicy {

        private final Target[] targets;
        private final ProtectionPolicy protectionPolicy;

        public TargetPolicy(Target[] targets, ProtectionPolicy protectionPolicy) {
            if (protectionPolicy == null) {
                throw new IllegalArgumentException("protectionPolicy is null");
            }
            this.targets = targets;
            this.protectionPolicy = protectionPolicy;
        }

        public Target[] getTargets() {
            if (targets == null || targets.length == 0) {
                return null;
            }
            return targets;
        }

        public ProtectionPolicy getProtectionPolicy() {
            return protectionPolicy;
        }
    }
}
