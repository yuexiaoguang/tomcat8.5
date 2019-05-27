package org.apache.tomcat.util.descriptor.web;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;


/**
 * 表示Web应用程序的Web服务引用, 作为部署描述符中<code>&lt;service-ref&gt;</code>元素的表示.
 */
public class ContextService extends ResourceBase {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------- Properties


    /**
     * WebService 引用名.
     */
    private String displayname = null;

    public String getDisplayname() {
        return (this.displayname);
    }

    public void setDisplayname(String displayname) {
        this.displayname = displayname;
    }

    /**
     * 此WebService的大图标.
     */
    private String largeIcon = null;

    public String getLargeIcon() {
        return (this.largeIcon);
    }

    public void setLargeIcon(String largeIcon) {
        this.largeIcon = largeIcon;
    }

    /**
     * 此WebService的小图标.
     */
    private String smallIcon = null;

    public String getSmallIcon() {
        return (this.smallIcon);
    }

    public void setSmallIcon(String smallIcon) {
        this.smallIcon = smallIcon;
    }

    /**
     * 客户端所依赖的JAX-WS Service接口的完全限定类名.
     */
    private String serviceInterface = null;

    public String getInterface() {
        return serviceInterface;
    }

    public void setInterface(String serviceInterface) {
        this.serviceInterface = serviceInterface;
    }

    /**
     * 包含Web服务WSDL描述的位置（相对于模块的根）.
     */
    private String wsdlfile = null;

    public String getWsdlfile() {
        return (this.wsdlfile);
    }

    public void setWsdlfile(String wsdlfile) {
        this.wsdlfile = wsdlfile;
    }

    /**
     * 指定WSDL定义与接口的关联的文件 (Service Endpoint Interface, Service Interface).
     */
    private String jaxrpcmappingfile = null;

    public String getJaxrpcmappingfile() {
        return (this.jaxrpcmappingfile);
    }

    public void setJaxrpcmappingfile(String jaxrpcmappingfile) {
        this.jaxrpcmappingfile = jaxrpcmappingfile;
    }

    /**
     * 声明要引用的特定WSDL服务元素.
     * 如果未声明wsdl文件或WSDL仅包含1个服务元素，则不指定.
     *
     * service-qname由namespaceURI和localpart组成. 如果在WSDL中声明了多于1个服务, 则必须定义它.
     *
     * serviceqname[0] : namespaceURI
     * serviceqname[1] : localpart
     */
    private String[] serviceqname = new String[2];

    public String[] getServiceqname() {
        return (this.serviceqname);
    }

    public String getServiceqname(int i) {
        return this.serviceqname[i];
    }

    public String getServiceqnameNamespaceURI() {
        return this.serviceqname[0];
    }

    public String getServiceqnameLocalpart() {
        return this.serviceqname[1];
    }

    public void setServiceqname(String[] serviceqname) {
        this.serviceqname = serviceqname;
    }

    public void setServiceqname(String serviceqname, int i) {
        this.serviceqname[i] = serviceqname;
    }

    public void setServiceqnameNamespaceURI(String namespaceuri) {
        this.serviceqname[0] = namespaceuri;
    }

    public void setServiceqnameLocalpart(String localpart) {
        this.serviceqname[1] = localpart;
    }

    /**
     * 声明客户端对容器的依赖性，以将服务端点接口解析为WSDL端口. 它可选地将服务端点接口与特定端口组件相关联.
     * 
     * @return 端点名称
     */
    public Iterator<String> getServiceendpoints() {
        return this.listProperties();
    }

    public String getPortlink(String serviceendpoint) {
        return (String) this.getProperty(serviceendpoint);
    }

    public void addPortcomponent(String serviceendpoint, String portlink) {
        if (portlink == null)
            portlink = "";
        this.setProperty(serviceendpoint, portlink);
    }

    /**
     * 用于此 service-ref 的处理程序列表.
     *
     * 必须完成处理程序的实例化.
     */
    private final HashMap<String, ContextHandler> handlers = new HashMap<>();

    public Iterator<String> getHandlers() {
        return handlers.keySet().iterator();
    }

    public ContextHandler getHandler(String handlername) {
        return handlers.get(handlername);
    }

    public void addHandler(ContextHandler handler) {
        handlers.put(handler.getName(), handler);
    }


    // --------------------------------------------------------- Public Methods


    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("ContextService[");
        sb.append("name=");
        sb.append(getName());
        if (getDescription() != null) {
            sb.append(", description=");
            sb.append(getDescription());
        }
        if (getType() != null) {
            sb.append(", type=");
            sb.append(getType());
        }
        if (displayname != null) {
            sb.append(", displayname=");
            sb.append(displayname);
        }
        if (largeIcon != null) {
            sb.append(", largeIcon=");
            sb.append(largeIcon);
        }
        if (smallIcon != null) {
            sb.append(", smallIcon=");
            sb.append(smallIcon);
        }
        if (wsdlfile != null) {
            sb.append(", wsdl-file=");
            sb.append(wsdlfile);
        }
        if (jaxrpcmappingfile != null) {
            sb.append(", jaxrpc-mapping-file=");
            sb.append(jaxrpcmappingfile);
        }
        if (serviceqname[0] != null) {
            sb.append(", service-qname/namespaceURI=");
            sb.append(serviceqname[0]);
        }
        if (serviceqname[1] != null) {
            sb.append(", service-qname/localpart=");
            sb.append(serviceqname[1]);
        }
        if (this.getServiceendpoints() != null) {
            sb.append(", port-component/service-endpoint-interface=");
            sb.append(this.getServiceendpoints());
        }
        if (handlers != null) {
            sb.append(", handler=");
            sb.append(handlers);
        }
        sb.append("]");
        return (sb.toString());
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result +
                ((displayname == null) ? 0 : displayname.hashCode());
        result = prime * result +
                ((handlers == null) ? 0 : handlers.hashCode());
        result = prime *
                result +
                ((jaxrpcmappingfile == null) ? 0 : jaxrpcmappingfile.hashCode());
        result = prime * result +
                ((largeIcon == null) ? 0 : largeIcon.hashCode());
        result = prime * result +
                ((serviceInterface == null) ? 0 : serviceInterface.hashCode());
        result = prime * result + Arrays.hashCode(serviceqname);
        result = prime * result +
                ((smallIcon == null) ? 0 : smallIcon.hashCode());
        result = prime * result +
                ((wsdlfile == null) ? 0 : wsdlfile.hashCode());
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
        ContextService other = (ContextService) obj;
        if (displayname == null) {
            if (other.displayname != null) {
                return false;
            }
        } else if (!displayname.equals(other.displayname)) {
            return false;
        }
        if (handlers == null) {
            if (other.handlers != null) {
                return false;
            }
        } else if (!handlers.equals(other.handlers)) {
            return false;
        }
        if (jaxrpcmappingfile == null) {
            if (other.jaxrpcmappingfile != null) {
                return false;
            }
        } else if (!jaxrpcmappingfile.equals(other.jaxrpcmappingfile)) {
            return false;
        }
        if (largeIcon == null) {
            if (other.largeIcon != null) {
                return false;
            }
        } else if (!largeIcon.equals(other.largeIcon)) {
            return false;
        }
        if (serviceInterface == null) {
            if (other.serviceInterface != null) {
                return false;
            }
        } else if (!serviceInterface.equals(other.serviceInterface)) {
            return false;
        }
        if (!Arrays.equals(serviceqname, other.serviceqname)) {
            return false;
        }
        if (smallIcon == null) {
            if (other.smallIcon != null) {
                return false;
            }
        } else if (!smallIcon.equals(other.smallIcon)) {
            return false;
        }
        if (wsdlfile == null) {
            if (other.wsdlfile != null) {
                return false;
            }
        } else if (!wsdlfile.equals(other.wsdlfile)) {
            return false;
        }
        return true;
    }
}
