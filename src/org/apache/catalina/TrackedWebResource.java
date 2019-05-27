package org.apache.catalina;

import java.io.Closeable;

public interface TrackedWebResource extends Closeable {
    Exception getCreatedBy();
    String getName();
}
