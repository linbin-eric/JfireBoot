package com.jfirer.jfirer.boot.common;

import com.jfirer.dson.Dson;
import com.jfirer.jsql.dialect.Dialect;
import com.jfirer.jsql.executor.impl.NextHolder;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.AnnotatedElement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

@Slf4j
public class SqlLog extends NextHolder
{
    @Override
    public int update(String sql, List<Object> params, Connection connection, Dialect dialect) throws SQLException
    {
        log.trace("执行的sql:{},参数为:{}", sql, Dson.toJson(params));
        return next.update(sql, params, connection, dialect);
    }

    @Override
    public String insertWithReturnKey(String sql, List<Object> params, Connection connection, Dialect dialect) throws SQLException
    {
        log.trace("执行的sql:{},参数：{}", sql, params);
        return next.insertWithReturnKey(sql, params, connection, dialect);
    }

    @Override
    public List<Object> queryList(String sql, AnnotatedElement element, List<Object> params, Connection connection, Dialect dialect) throws SQLException
    {
        long         t0     = System.currentTimeMillis();
        List<Object> result = next.queryList(sql, element, params, connection, dialect);
        long         t1     = System.currentTimeMillis();
        log.trace("sql执行耗时:{},sql为:{},参数为:{}", t1 - t0, sql, params);
        return result;
    }

    @Override
    public Object queryOne(String sql, AnnotatedElement element, List<Object> params, Connection connection, Dialect dialect) throws SQLException
    {
        log.trace("执行的sql:{},参数为:{}", sql, params);
        return next.queryOne(sql, element, params, connection, dialect);
    }

    @Override
    public int order()
    {
        return 2000;
    }
}
