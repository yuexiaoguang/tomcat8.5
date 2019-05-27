package javax.el;

import java.util.EventObject;

public class ELContextEvent extends EventObject {

    private static final long serialVersionUID = 1255131906285426769L;

    /**
     * @param source 该事件的来源是EL上下文
     */
    public ELContextEvent(ELContext source) {
        super(source);
    }

    public ELContext getELContext() {
        return (ELContext) this.getSource();
    }

}
