package org.apache.el.util;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public final class MessageFactory {

    static final ResourceBundle bundle =
            ResourceBundle.getBundle("org.apache.el.Messages");

    public MessageFactory() {
        super();
    }

    public static String get(final String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    public static String get(final String key, final Object... args) {
        String value = get(key);

        MessageFormat mf = new MessageFormat(value);
        return mf.format(args, new StringBuffer(), null).toString();
    }
}
