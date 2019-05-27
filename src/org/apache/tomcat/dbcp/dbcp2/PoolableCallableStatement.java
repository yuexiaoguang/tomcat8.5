package org.apache.tomcat.dbcp.dbcp2;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.tomcat.dbcp.pool2.KeyedObjectPool;

/**
 * {@link DelegatingCallableStatement}和 {@link PoolingConnection} 一起实现{@link CallableStatement}的池.
 * <p>
 * {@link #close}方法返回这个 statement 到它的池中. (See {@link PoolingConnection}.)
 */
public class PoolableCallableStatement extends DelegatingCallableStatement {

    /**
     * 从中获取此CallableStatement的{@link KeyedObjectPool}.
     */
    private final KeyedObjectPool<PStmtKey,DelegatingPreparedStatement> _pool;

    /**
     * {@link KeyedObjectPool}中这个statement的Key.
     */
    private final PStmtKey _key;

    /**
     * @param stmt 底层{@link CallableStatement}
     * @param key {@link KeyedObjectPool}中这个statement的Key
     * @param pool 从中获取此CallableStatement的{@link KeyedObjectPool}
     * @param conn 创建这个CallableStatement的{@link DelegatingConnection}
     */
    public PoolableCallableStatement(final CallableStatement stmt, final PStmtKey key,
            final KeyedObjectPool<PStmtKey,DelegatingPreparedStatement> pool,
            final DelegatingConnection<Connection> conn) {
        super(conn, stmt);
        _pool = pool;
        _key = key;

        // 从跟踪中删除，因为activate方法将添加此 statement.
        if(getConnectionInternal() != null) {
            getConnectionInternal().removeTrace(this);
        }
    }

    /**
     * 返回 CallableStatement 到池中.
     */
    @Override
    public void close() throws SQLException {
        // 调用两次 close 无效
        if (!isClosed()) {
            try {
                _pool.returnObject(_key,this);
            } catch(final SQLException e) {
                throw e;
            } catch(final RuntimeException e) {
                throw e;
            } catch(final Exception e) {
                throw new SQLException("Cannot close CallableStatement (return to pool failed)", e);
            }
        }
    }

    /**
     * 从池中检索后激活. 将此CallableStatement的跟踪添加到创建它的Connection.
     */
    @Override
    protected void activate() throws SQLException {
        setClosedInternal(false);
        if( getConnectionInternal() != null ) {
            getConnectionInternal().addTrace( this );
        }
        super.activate();
    }

    /**
     * 准备返回到池. 从创建它的Connection中删除与此CallableStatement关联的跟踪. 也关闭任何关联的ResultSet.
     */
    @Override
    protected void passivate() throws SQLException {
        setClosedInternal(true);
        if( getConnectionInternal() != null ) {
            getConnectionInternal().removeTrace(this);
        }

        // JDBC规范要求statement 在关闭时, 关闭任何打开的ResultSet.
        // FIXME 正在包装的PreparedStatement应该处理这个问题.
        // See DBCP-10 for what could happen when ResultSets are closed twice.
        final List<AbandonedTrace> resultSets = getTrace();
        if(resultSets != null) {
            final ResultSet[] set = resultSets.toArray(new ResultSet[resultSets.size()]);
            for (final ResultSet element : set) {
                element.close();
            }
            clearTrace();
        }

        super.passivate();
    }
}
