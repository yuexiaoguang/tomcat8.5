package org.apache.tomcat.util.http;

public class RequestUtil {

    private RequestUtil() {
        // Hide default constructor as this is a utility class
    }


    /**
     * Normalize a relative URI path that may have relative values ("/./",
     * "/../", and so on ) it it.  <strong>WARNING</strong> - This method is
     * useful only for normalizing application-generated paths.  It does not
     * try to perform security checks for malicious input.
     *
     * @param path Relative path to be normalized
     *
     * @return The normalized path or <code>null</code> if the path cannot be
     *         normalized
     */
    public static String normalize(String path) {
        return normalize(path, true);
    }


    /**
     * Normalize a relative URI path that may have relative values ("/./",
     * "/../", and so on ) it it.  <strong>WARNING</strong> - This method is
     * useful only for normalizing application-generated paths.  It does not
     * try to perform security checks for malicious input.
     *
     * @param path Relative path to be normalized
     * @param replaceBackSlash Should '\\' be replaced with '/'
     *
     * @return The normalized path or <code>null</code> if the path cannot be
     *         normalized
     */
    public static String normalize(String path, boolean replaceBackSlash) {

        if (path == null) {
            return null;
        }

        // Create a place for the normalized path
        String normalized = path;

        if (replaceBackSlash && normalized.indexOf('\\') >= 0)
            normalized = normalized.replace('\\', '/');

        // Add a leading "/" if necessary
        if (!normalized.startsWith("/"))
            normalized = "/" + normalized;

        boolean addedTrailingSlash = false;
        if (normalized.endsWith("/.") || normalized.endsWith("/..")) {
            normalized = normalized + "/";
            addedTrailingSlash = true;
        }

        // Resolve occurrences of "//" in the normalized path
        while (true) {
            int index = normalized.indexOf("//");
            if (index < 0) {
                break;
            }
            normalized = normalized.substring(0, index) + normalized.substring(index + 1);
        }

        // Resolve occurrences of "/./" in the normalized path
        while (true) {
            int index = normalized.indexOf("/./");
            if (index < 0) {
                break;
            }
            normalized = normalized.substring(0, index) + normalized.substring(index + 2);
        }

        // Resolve occurrences of "/../" in the normalized path
        while (true) {
            int index = normalized.indexOf("/../");
            if (index < 0) {
                break;
            }
            if (index == 0) {
                return null;  // Trying to go outside our context
            }
            int index2 = normalized.lastIndexOf('/', index - 1);
            normalized = normalized.substring(0, index2) + normalized.substring(index + 3);
        }

        if (normalized.length() > 1 && addedTrailingSlash) {
            // Remove the trailing '/' we added to that input and output are
            // consistent w.r.t. to the presence of the trailing '/'.
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        // Return the normalized path that we have completed
        return normalized;
    }
}
