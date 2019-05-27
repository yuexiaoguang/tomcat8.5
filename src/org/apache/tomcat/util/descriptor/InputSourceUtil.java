package org.apache.tomcat.util.descriptor;

import java.io.InputStream;

import org.apache.tomcat.util.ExceptionUtils;
import org.xml.sax.InputSource;

public final class InputSourceUtil {

    private InputSourceUtil() {
        // Utility class. Hide default constructor.
    }


    public static void close(InputSource inputSource) {
        if (inputSource == null) {
            // Nothing to do
            return;
        }

        InputStream is = inputSource.getByteStream();
        if (is != null) {
            try {
                is.close();
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
            }
        }

    }
}
