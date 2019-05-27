package org.apache.catalina.ha.backend;

import java.net.InetAddress;

/*
 * 这个类表示一个front-end httpd服务器.
 */
public class Proxy {

  public InetAddress address = null;
  public int port = 80;
}
