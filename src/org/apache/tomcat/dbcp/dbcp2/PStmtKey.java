package org.apache.tomcat.dbcp.dbcp2;

import org.apache.tomcat.dbcp.dbcp2.PoolingConnection.StatementType;

/**
 * 唯一标识{@link java.sql.PreparedStatement PreparedStatement}的Key.
 */
public class PStmtKey {

    /** SQL定义Prepared或Callable语句 */
    private final String _sql;

    /** 结果集类型 */
    private final Integer _resultSetType;

    /** 结果集并发 */
    private final Integer _resultSetConcurrency;

    /** 数据库目录 */
    private final String _catalog;

    /** 自动生成的Key */
    private final Integer _autoGeneratedKeys;

    /** 语句类型 */
    private final StatementType _stmtType;


    public PStmtKey(final String sql) {
        this(sql, null, StatementType.PREPARED_STATEMENT, null);
    }

    public PStmtKey(final String sql, final String catalog) {
        this(sql, catalog, StatementType.PREPARED_STATEMENT, null);
    }

    public PStmtKey(final String sql, final String catalog, final int autoGeneratedKeys) {
        this(sql, catalog, StatementType.PREPARED_STATEMENT, Integer.valueOf(autoGeneratedKeys));
    }

    public PStmtKey(final String sql, final String catalog, final StatementType stmtType, final Integer autoGeneratedKeys) {
        _sql = sql;
        _catalog = catalog;
        _stmtType = stmtType;
        _autoGeneratedKeys = autoGeneratedKeys;
        _resultSetType = null;
        _resultSetConcurrency = null;
    }

    public  PStmtKey(final String sql, final int resultSetType, final int resultSetConcurrency) {
        this(sql, null, resultSetType, resultSetConcurrency, StatementType.PREPARED_STATEMENT);
    }

    public PStmtKey(final String sql, final String catalog, final int resultSetType, final int resultSetConcurrency) {
        this(sql, catalog, resultSetType, resultSetConcurrency, StatementType.PREPARED_STATEMENT);
    }

    public PStmtKey(final String sql, final String catalog, final int resultSetType, final int resultSetConcurrency, final StatementType stmtType) {
        _sql = sql;
        _catalog = catalog;
        _resultSetType = Integer.valueOf(resultSetType);
        _resultSetConcurrency = Integer.valueOf(resultSetConcurrency);
        _stmtType = stmtType;
        _autoGeneratedKeys = null;
    }


    public String getSql() {
        return _sql;
    }

    public Integer getResultSetType() {
        return _resultSetType;
    }

    public Integer getResultSetConcurrency() {
        return _resultSetConcurrency;
    }

    public Integer getAutoGeneratedKeys() {
        return _autoGeneratedKeys;
    }

    public String getCatalog() {
        return _catalog;
    }

    public StatementType getStmtType() {
        return _stmtType;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PStmtKey other = (PStmtKey) obj;
        if (_catalog == null) {
            if (other._catalog != null) {
                return false;
            }
        } else if (!_catalog.equals(other._catalog)) {
            return false;
        }
        if (_resultSetConcurrency == null) {
            if (other._resultSetConcurrency != null) {
                return false;
            }
        } else if (!_resultSetConcurrency.equals(other._resultSetConcurrency)) {
            return false;
        }
        if (_resultSetType == null) {
            if (other._resultSetType != null) {
                return false;
            }
        } else if (!_resultSetType.equals(other._resultSetType)) {
            return false;
        }
        if (_autoGeneratedKeys == null) {
            if (other._autoGeneratedKeys != null) {
                return false;
            }
        } else if (!_autoGeneratedKeys.equals(other._autoGeneratedKeys)) {
            return false;
        }
        if (_sql == null) {
            if (other._sql != null) {
                return false;
            }
        } else if (!_sql.equals(other._sql)) {
            return false;
        }
        if (_stmtType != other._stmtType) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (_catalog == null ? 0 : _catalog.hashCode());
        result = prime * result + (_resultSetConcurrency == null ? 0 : _resultSetConcurrency.hashCode());
        result = prime * result + (_resultSetType == null ? 0 : _resultSetType.hashCode());
        result = prime * result + (_sql == null ? 0 : _sql.hashCode());
        result = prime * result + (_autoGeneratedKeys == null ? 0 : _autoGeneratedKeys.hashCode());
        result = prime * result + _stmtType.hashCode();
        return result;
    }

    @Override
    public String toString() {
        final StringBuffer buf = new StringBuffer();
        buf.append("PStmtKey: sql=");
        buf.append(_sql);
        buf.append(", catalog=");
        buf.append(_catalog);
        buf.append(", resultSetType=");
        buf.append(_resultSetType);
        buf.append(", resultSetConcurrency=");
        buf.append(_resultSetConcurrency);
        buf.append(", autoGeneratedKeys=");
        buf.append(_autoGeneratedKeys);
        buf.append(", statmentType=");
        buf.append(_stmtType);
        return buf.toString();
    }
}
