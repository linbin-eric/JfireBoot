package com.example.entity;

import cc.jfire.jsql.annotation.Column;
import cc.jfire.jsql.annotation.Id;
import cc.jfire.jsql.annotation.Table;
import lombok.Data;

/**
 * 用户实体类
 * 使用 Jsql 注解映射数据库表
 */
@Data
@Table("users")
public class User {

    @Id
    @Column("id")
    private Long id;

    @Column("name")
    private String name;

    @Column("email")
    private String email;

    @Column("status")
    private String status;

    @Column("created_at")
    private Long createdAt;

    @Column("updated_at")
    private Long updatedAt;
}
