package com.example.mapper;

import cc.jfire.jsql.annotation.Sql;
import cc.jfire.jsql.mapper.Repository;
import cc.jfire.starter.jsql.AutoMapper;
import com.example.entity.User;

import java.util.List;

/**
 * 用户 Mapper 接口
 * 使用 @AutoMapper 标注，会被自动注册到容器
 * 继承 Repository<T> 获得基础 CRUD 方法
 */
@AutoMapper
public interface UserMapper extends Repository<User> {

    /**
     * 根据状态查询用户列表
     * 使用 @Sql 注解定义 SQL 语句
     * ${status} 会被替换为参数值
     */
    @Sql(sql = "SELECT * FROM users WHERE status = ${status}", paramNames = "status")
    List<User> findByStatus(String status);

    /**
     * 根据邮箱查询用户
     */
    @Sql(sql = "SELECT * FROM users WHERE email = ${email}", paramNames = "email")
    User findByEmail(String email);

    /**
     * 统计用户数量
     */
    @Sql(sql = "SELECT COUNT(*) FROM users WHERE status = ${status}", paramNames = "status")
    Long countByStatus(String status);
}
