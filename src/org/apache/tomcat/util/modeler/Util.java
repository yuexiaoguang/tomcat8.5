package org.apache.tomcat.util.modeler;

public class Util {

    private Util() {
        // Utility class. Hide default constructor.
    }

    public static boolean objectNameValueNeedsQuote(String input) {
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == ',' || ch == '=' || ch == ':' || ch == '*' || ch == '?') {
                return true;
            }
        }
        return false;
    }
}
