package org.apache.tomcat.util.descriptor.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;


/**
 * 表示WebService的处理程序引用, 作为部署描述符中<code>&lt;handler&gt;</code>元素的表示.
 */
public class ContextHandler extends ResourceBase {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------- Properties


    /**
     * Handler引用类.
     */
    private String handlerclass = null;

    public String getHandlerclass() {
        return (this.handlerclass);
    }

    public void setHandlerclass(String handlerclass) {
        this.handlerclass = handlerclass;
    }

    /**
     * 一个QName列表, 指定处理程序将处理的SOAP标头.
     * -必须在WSDL中找到namespace和localpart值.
     *
     * service-qname 由 namespaceURI 和 localpart 组成.
     *
     * soapHeader[0] : namespaceURI
     * soapHeader[1] : localpart
     */
    private final HashMap<String, String> soapHeaders = new HashMap<>();

    public Iterator<String> getLocalparts() {
        return soapHeaders.keySet().iterator();
    }

    public String getNamespaceuri(String localpart) {
        return soapHeaders.get(localpart);
    }

    public void addSoapHeaders(String localpart, String namespaceuri) {
        soapHeaders.put(localpart, namespaceuri);
    }

    /**
     * 设置已配置的属性.
     * 
     * @param name 属性名
     * @param value 属性值
     */
    public void setProperty(String name, String value) {
        this.setProperty(name, (Object) value);
    }

    /**
     * The soapRole.
     */
    private final ArrayList<String> soapRoles = new ArrayList<>();

    public String getSoapRole(int i) {
        return this.soapRoles.get(i);
    }

    public int getSoapRolesSize() {
        return this.soapRoles.size();
    }

    public void addSoapRole(String soapRole) {
        this.soapRoles.add(soapRole);
    }

    /**
     * The portName.
     */
    private final ArrayList<String> portNames = new ArrayList<>();

    public String getPortName(int i) {
        return this.portNames.get(i);
    }

    public int getPortNamesSize() {
        return this.portNames.size();
    }

    public void addPortName(String portName) {
        this.portNames.add(portName);
    }

    // --------------------------------------------------------- Public Methods


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("ContextHandler[");
        sb.append("name=");
        sb.append(getName());
        if (handlerclass != null) {
            sb.append(", class=");
            sb.append(handlerclass);
        }
        if (this.soapHeaders != null) {
            sb.append(", soap-headers=");
            sb.append(this.soapHeaders);
        }
        if (this.getSoapRolesSize() > 0) {
            sb.append(", soap-roles=");
            sb.append(soapRoles);
        }
        if (this.getPortNamesSize() > 0) {
            sb.append(", port-name=");
            sb.append(portNames);
        }
        if (this.listProperties() != null) {
            sb.append(", init-param=");
            sb.append(this.listProperties());
        }
        sb.append("]");
        return (sb.toString());
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result +
                ((handlerclass == null) ? 0 : handlerclass.hashCode());
        result = prime * result +
                ((portNames == null) ? 0 : portNames.hashCode());
        result = prime * result +
                ((soapHeaders == null) ? 0 : soapHeaders.hashCode());
        result = prime * result +
                ((soapRoles == null) ? 0 : soapRoles.hashCode());
        return result;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ContextHandler other = (ContextHandler) obj;
        if (handlerclass == null) {
            if (other.handlerclass != null) {
                return false;
            }
        } else if (!handlerclass.equals(other.handlerclass)) {
            return false;
        }
        if (portNames == null) {
            if (other.portNames != null) {
                return false;
            }
        } else if (!portNames.equals(other.portNames)) {
            return false;
        }
        if (soapHeaders == null) {
            if (other.soapHeaders != null) {
                return false;
            }
        } else if (!soapHeaders.equals(other.soapHeaders)) {
            return false;
        }
        if (soapRoles == null) {
            if (other.soapRoles != null) {
                return false;
            }
        } else if (!soapRoles.equals(other.soapRoles)) {
            return false;
        }
        return true;
    }
}
