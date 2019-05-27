package org.apache.catalina.ha;

import javax.servlet.http.HttpSession;

import org.apache.catalina.Session;

public interface ClusterSession extends Session, HttpSession {
   /**
    * 返回true， 如果此会话是主会话, 如果是这样的话, 管理器可以让其过期.
    * @return True 如果这个会话是主要的
    */
   public boolean isPrimarySession();

   /**
    * 设置这是否是主会话.
    * 
    * @param primarySession Flag value
    */
   public void setPrimarySession(boolean primarySession);


}
