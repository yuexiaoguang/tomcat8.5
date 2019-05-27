package org.apache.catalina.ha.deploy;
import java.io.File;

public interface FileChangeListener {
    public void fileModified(File f);
    public void fileRemoved(File f);
}
