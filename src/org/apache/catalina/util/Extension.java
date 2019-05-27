package org.apache.catalina.util;


import java.util.StringTokenizer;


/**
 * 表示JAR文件清单中所描述的可用的“可选包”（以前称为“标准扩展名”）的实用工具类, 或对这种可选包的要求.
 * 它用于支持servlet规范的需求, 2.3版本, 为所有应用程序提供相关共享扩展.
 * <p>
 * 此外, 静态实用工具方法可用于扫描清单，并返回在该清单中记录的可用或必需可选模块的数组.
 * <p>
 * For more information about optional packages, see the document
 * <em>Optional Package Versioning</em> in the documentation bundle for your
 * Java2 Standard Edition package, in file
 * <code>guide/extensions/versioning.html</code>.
 */
public final class Extension {


    // ------------------------------------------------------------- Properties


    /**
     * 可用或所需的可选包的名称.
     */
    private String extensionName = null;


    public String getExtensionName() {
        return (this.extensionName);
    }

    public void setExtensionName(String extensionName) {
        this.extensionName = extensionName;
    }

    /**
     * 如果未安装该可选包的最新版本，则可以获得该URL.
     */
    private String implementationURL = null;

    public String getImplementationURL() {
        return (this.implementationURL);
    }

    public void setImplementationURL(String implementationURL) {
        this.implementationURL = implementationURL;
    }


    /**
     * 提供此可选包的实现的公司或组织的名称.
     */
    private String implementationVendor = null;

    public String getImplementationVendor() {
        return (this.implementationVendor);
    }

    public void setImplementationVendor(String implementationVendor) {
        this.implementationVendor = implementationVendor;
    }


    /**
     * 生成JAR文件中包含的可选包的公司的唯一标识符.
     */
    private String implementationVendorId = null;

    public String getImplementationVendorId() {
        return (this.implementationVendorId);
    }

    public void setImplementationVendorId(String implementationVendorId) {
        this.implementationVendorId = implementationVendorId;
    }


    /**
     * 可选包的实现的版本号（点十进制记数法）.
     */
    private String implementationVersion = null;

    public String getImplementationVersion() {
        return (this.implementationVersion);
    }

    public void setImplementationVersion(String implementationVersion) {
        this.implementationVersion = implementationVersion;
    }


    /**
     * 源于此可选包的规范的公司或组织的名称.
     */
    private String specificationVendor = null;

    public String getSpecificationVendor() {
        return (this.specificationVendor);
    }

    public void setSpecificationVendor(String specificationVendor) {
        this.specificationVendor = specificationVendor;
    }


    /**
     * 这个可选包符合的规范的版本号（虚线十进制记数法）.
     */
    private String specificationVersion = null;

    public String getSpecificationVersion() {
        return (this.specificationVersion);
    }

    public void setSpecificationVersion(String specificationVersion) {
        this.specificationVersion = specificationVersion;
    }


    /**
     * 如果满足所有所需的扩展依赖关系，则实现为true
     */
    private boolean fulfilled = false;

    public void setFulfilled(boolean fulfilled) {
        this.fulfilled = fulfilled;
    }

    public boolean isFulfilled() {
        return fulfilled;
    }

    // --------------------------------------------------------- Public Methods

    /**
     * 返回<code>true</code>, 如果指定的<code>Extension</code> (表示该应用程序所需的可选包)
     * 是满足的(表示已经安装的可选包).  否则返回<code>false</code>.
     *
     * @param required 扩展所需的可选包
     * @return <code>true</code> 如果满足扩展
     */
    public boolean isCompatibleWith(Extension required) {

        // 扩展名必须匹配
        if (extensionName == null)
            return false;
        if (!extensionName.equals(required.getExtensionName()))
            return false;

        // 如果指定, 可用的规范版本必须 >= 所需
        if (required.getSpecificationVersion() != null) {
            if (!isNewer(specificationVersion,
                         required.getSpecificationVersion()))
                return false;
        }

        // If specified, Implementation Vendor ID must match
        if (required.getImplementationVendorId() != null) {
            if (implementationVendorId == null)
                return false;
            if (!implementationVendorId.equals(required
                    .getImplementationVendorId()))
                return false;
        }

        // If specified, Implementation version must be >= required
        if (required.getImplementationVersion() != null) {
            if (!isNewer(implementationVersion,
                         required.getImplementationVersion()))
                return false;
        }

        // 这个可用的可选包满足要求
        return true;

    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("Extension[");
        sb.append(extensionName);
        if (implementationURL != null) {
            sb.append(", implementationURL=");
            sb.append(implementationURL);
        }
        if (implementationVendor != null) {
            sb.append(", implementationVendor=");
            sb.append(implementationVendor);
        }
        if (implementationVendorId != null) {
            sb.append(", implementationVendorId=");
            sb.append(implementationVendorId);
        }
        if (implementationVersion != null) {
            sb.append(", implementationVersion=");
            sb.append(implementationVersion);
        }
        if (specificationVendor != null) {
            sb.append(", specificationVendor=");
            sb.append(specificationVendor);
        }
        if (specificationVersion != null) {
            sb.append(", specificationVersion=");
            sb.append(specificationVersion);
        }
        sb.append("]");
        return (sb.toString());

    }


    // -------------------------------------------------------- Private Methods



    /**
     * 返回<code>true</code>如果第一个版本号大于或等于第二个版本号; 否则返回<code>false</code>.
     *
     * @param first 第一个版本号(dotted decimal)
     * @param second 第二个版本号(dotted decimal)
     *
     * @exception NumberFormatException 在错误的版本号上
     */
    private boolean isNewer(String first, String second)
        throws NumberFormatException {

        if ((first == null) || (second == null))
            return false;
        if (first.equals(second))
            return true;

        StringTokenizer fTok = new StringTokenizer(first, ".", true);
        StringTokenizer sTok = new StringTokenizer(second, ".", true);
        int fVersion = 0;
        int sVersion = 0;
        while (fTok.hasMoreTokens() || sTok.hasMoreTokens()) {
            if (fTok.hasMoreTokens())
                fVersion = Integer.parseInt(fTok.nextToken());
            else
                fVersion = 0;
            if (sTok.hasMoreTokens())
                sVersion = Integer.parseInt(sTok.nextToken());
            else
                sVersion = 0;
            if (fVersion < sVersion)
                return false;
            else if (fVersion > sVersion)
                return true;
            if (fTok.hasMoreTokens())   // Swallow the periods
                fTok.nextToken();
            if (sTok.hasMoreTokens())
                sTok.nextToken();
        }
        return true;  // Exact match
    }
}
