package org.apache.tomcat.dbcp.dbcp2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.List;

/**
 * {@link Statement}的基础代理实现.
 * <p>
 * {@link Statement}接口的所有方法简单检查{@link Statement} 是否是激活的,
 * 并调用构造方法中提供的代理上的对应的方法.
 * <p>
 * 继承 AbandonedTrace 来实现 Statement 跟踪, 并记录创建  Statement的代码. 跟踪Statement 确保创建它的 Connection 在关闭时关闭打开的 Statement.
 */
public class DelegatingStatement extends AbandonedTrace implements Statement {
    /** My delegate. */
    private Statement _stmt = null;
    /** 创建这个对象的连接. **/
    private DelegatingConnection<?> _conn = null;

    /**
     * @param s 要代理的{@link Statement}.
     * @param c 创建这个statement的 {@link DelegatingConnection}.
     */
    public DelegatingStatement(final DelegatingConnection<?> c, final Statement s) {
        super(c);
        _stmt = s;
        _conn = c;
    }

    /**
     * 返回代理的 {@link Statement}.
     */
    public Statement getDelegate() {
        return _stmt;
    }


    /**
     * 如果底层的 {@link Statement} 不是一个{@code DelegatingStatement}, 返回它, 否则以递归方式在代理上调用此方法.
     * <p>
     * 这个方法将返回第一个不是 {@code DelegatingStatement}的代理,
     * 或 {@code null}, 当遍历这个链没有找到非 {@code DelegatingStatement}代理时.
     * <p>
     * 当可能是嵌套的{@code DelegatingStatement}, 但希望确保获得“真正的”{@link Statement}时，此方法很有用.
     */
    public Statement getInnermostDelegate() {
        Statement s = _stmt;
        while(s != null && s instanceof DelegatingStatement) {
            s = ((DelegatingStatement)s).getDelegate();
            if(this == s) {
                return null;
            }
        }
        return s;
    }

    /**
     * 设置代理.
     * @param s The statement
     */
    public void setDelegate(final Statement s) {
        _stmt = s;
    }

    private boolean _closed = false;

    protected boolean isClosedInternal() {
        return _closed;
    }

    protected void setClosedInternal(final boolean closed) {
        this._closed = closed;
    }

    protected void checkOpen() throws SQLException {
        if(isClosed()) {
            throw new SQLException
                (this.getClass().getName() + " with address: \"" +
                this.toString() + "\" is closed.");
        }
    }

    /**
     * 关闭 DelegatingStatement, 并关闭任何未明确关闭的ResultSet.
     */
    @Override
    public void close() throws SQLException {
        if (isClosed()) {
            return;
        }
        try {
            try {
                if (_conn != null) {
                    _conn.removeTrace(this);
                    _conn = null;
                }

                // JDBC规范要求语句在 Statement 时关闭任何打开的ResultSet.
                // FIXME 正在包装的PreparedStatement应该为我们处理这个问题.
                // See bug 17301 for what could happen when ResultSets are closed twice.
                final List<AbandonedTrace> resultSets = getTrace();
                if( resultSets != null) {
                    final ResultSet[] set = resultSets.toArray(new ResultSet[resultSets.size()]);
                    for (final ResultSet element : set) {
                        element.close();
                    }
                    clearTrace();
                }

                if (_stmt != null) {
                    _stmt.close();
                }
            }
            catch (final SQLException e) {
                handleException(e);
            }
        }
        finally {
            _closed = true;
            _stmt = null;
        }
    }

    protected void handleException(final SQLException e) throws SQLException {
        if (_conn != null) {
            _conn.handleException(e);
        }
        else {
            throw e;
        }
    }

    protected void activate() throws SQLException {
        if(_stmt instanceof DelegatingStatement) {
            ((DelegatingStatement)_stmt).activate();
        }
    }

    protected void passivate() throws SQLException {
        if(_stmt instanceof DelegatingStatement) {
            ((DelegatingStatement)_stmt).passivate();
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        checkOpen();
        return getConnectionInternal(); // 返回创建它的代理连接
    }

    protected DelegatingConnection<?> getConnectionInternal() {
        return _conn;
    }

    @Override
    public ResultSet executeQuery(final String sql) throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return DelegatingResultSet.wrapResultSet(this,_stmt.executeQuery(sql));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(this,_stmt.getResultSet());
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public int executeUpdate(final String sql) throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return _stmt.executeUpdate(sql);
        } catch (final SQLException e) {
            handleException(e); return 0;
        }
    }

    @Override
    public int getMaxFieldSize() throws SQLException
    { checkOpen(); try { return _stmt.getMaxFieldSize(); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public void setMaxFieldSize(final int max) throws SQLException
    { checkOpen(); try { _stmt.setMaxFieldSize(max); } catch (final SQLException e) { handleException(e); } }

    @Override
    public int getMaxRows() throws SQLException
    { checkOpen(); try { return _stmt.getMaxRows(); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public void setMaxRows(final int max) throws SQLException
    { checkOpen(); try { _stmt.setMaxRows(max); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setEscapeProcessing(final boolean enable) throws SQLException
    { checkOpen(); try { _stmt.setEscapeProcessing(enable); } catch (final SQLException e) { handleException(e); } }

    @Override
    public int getQueryTimeout() throws SQLException
    { checkOpen(); try { return _stmt.getQueryTimeout(); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public void setQueryTimeout(final int seconds) throws SQLException
    { checkOpen(); try { _stmt.setQueryTimeout(seconds); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void cancel() throws SQLException
    { checkOpen(); try { _stmt.cancel(); } catch (final SQLException e) { handleException(e); } }

    @Override
    public SQLWarning getWarnings() throws SQLException
    { checkOpen(); try { return _stmt.getWarnings(); } catch (final SQLException e) { handleException(e); throw new AssertionError(); } }

    @Override
    public void clearWarnings() throws SQLException
    { checkOpen(); try { _stmt.clearWarnings(); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setCursorName(final String name) throws SQLException
    { checkOpen(); try { _stmt.setCursorName(name); } catch (final SQLException e) { handleException(e); } }

    @Override
    public boolean execute(final String sql) throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return _stmt.execute(sql);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public int getUpdateCount() throws SQLException
    { checkOpen(); try { return _stmt.getUpdateCount(); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public boolean getMoreResults() throws SQLException
    { checkOpen(); try { return _stmt.getMoreResults(); } catch (final SQLException e) { handleException(e); return false; } }

    @Override
    public void setFetchDirection(final int direction) throws SQLException
    { checkOpen(); try { _stmt.setFetchDirection(direction); } catch (final SQLException e) { handleException(e); } }

    @Override
    public int getFetchDirection() throws SQLException
    { checkOpen(); try { return _stmt.getFetchDirection(); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public void setFetchSize(final int rows) throws SQLException
    { checkOpen(); try { _stmt.setFetchSize(rows); } catch (final SQLException e) { handleException(e); } }

    @Override
    public int getFetchSize() throws SQLException
    { checkOpen(); try { return _stmt.getFetchSize(); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public int getResultSetConcurrency() throws SQLException
    { checkOpen(); try { return _stmt.getResultSetConcurrency(); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public int getResultSetType() throws SQLException
    { checkOpen(); try { return _stmt.getResultSetType(); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public void addBatch(final String sql) throws SQLException
    { checkOpen(); try { _stmt.addBatch(sql); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void clearBatch() throws SQLException
    { checkOpen(); try { _stmt.clearBatch(); } catch (final SQLException e) { handleException(e); } }

    @Override
    public int[] executeBatch() throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return _stmt.executeBatch();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public String toString() {
    return _stmt == null ? "NULL" : _stmt.toString();
    }

    @Override
    public boolean getMoreResults(final int current) throws SQLException
    { checkOpen(); try { return _stmt.getMoreResults(current); } catch (final SQLException e) { handleException(e); return false; } }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(this, _stmt.getGeneratedKeys());
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public int executeUpdate(final String sql, final int autoGeneratedKeys) throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return _stmt.executeUpdate(sql, autoGeneratedKeys);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int executeUpdate(final String sql, final int columnIndexes[]) throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return _stmt.executeUpdate(sql, columnIndexes);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int executeUpdate(final String sql, final String columnNames[]) throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return _stmt.executeUpdate(sql, columnNames);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public boolean execute(final String sql, final int autoGeneratedKeys) throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return _stmt.execute(sql, autoGeneratedKeys);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean execute(final String sql, final int columnIndexes[]) throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return _stmt.execute(sql, columnIndexes);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean execute(final String sql, final String columnNames[]) throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return _stmt.execute(sql, columnNames);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public int getResultSetHoldability() throws SQLException
    { checkOpen(); try { return _stmt.getResultSetHoldability(); } catch (final SQLException e) { handleException(e); return 0; } }

    /*
     * Note was protected prior to JDBC 4
     */
    @Override
    public boolean isClosed() throws SQLException {
        return _closed;
    }


    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return true;
        } else if (iface.isAssignableFrom(_stmt.getClass())) {
            return true;
        } else {
            return _stmt.isWrapperFor(iface);
        }
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        } else if (iface.isAssignableFrom(_stmt.getClass())) {
            return iface.cast(_stmt);
        } else {
            return _stmt.unwrap(iface);
        }
    }

    @Override
    public void setPoolable(final boolean poolable) throws SQLException {
        checkOpen();
        try {
            _stmt.setPoolable(poolable);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public boolean isPoolable() throws SQLException {
        checkOpen();
        try {
            return _stmt.isPoolable();
        }
        catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        checkOpen();
        try {
            _stmt.closeOnCompletion();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        checkOpen();
        try {
            return _stmt.isCloseOnCompletion();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        // 因为statement 池需要如此. poolable statement将始终由 statement 池强引用保存.
        // 如果包装poolable statement的代理 statement 没有被强引用保留，那么它们将被垃圾收集，但此时需要将poolable statement返回到池中，否则池中的 statement 将被泄漏.
        // 关闭此statement将关闭所有代理statement, 并将poolable statement返回池.
        close();
        super.finalize();
    }
}
