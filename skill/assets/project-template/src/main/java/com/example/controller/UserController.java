package com.example.controller;

import cc.jfire.baseutil.Resource;
import cc.jfire.boot.forward.path.Path;
import cc.jfire.boot.http.HttpRequestExtend;
import cc.jfire.jfire.core.aop.impl.support.transaction.Transactional;
import com.example.entity.User;
import com.example.service.UserService;

import java.util.List;
import java.util.Map;

/**
 * 用户控制器示例
 * 使用 @Resource 标注为 Bean，会被 @ComponentScan 扫描到
 */
@Resource
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 简单路由示例
     * 访问: GET http://localhost:8080/hello/world
     */
    @Path("/hello/${name}")
    public Map<String, Object> hello(String name) {
        return Map.of(
            "ok", true,
            "message", "Hello, " + name + "!",
            "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * RESTful 路径变量示例
     * 访问: GET http://localhost:8080/user/123
     */
    @Path("/user/${id}")
    @Transactional  // 访问数据库必须开启事务
    public User getUser(Long id) {
        return userService.findById(id);
    }

    /**
     * 查询列表示例
     * 访问: GET http://localhost:8080/user/list?status=ACTIVE
     */
    @Path("/user/list")
    @Transactional
    public List<User> listUsers(String status) {
        return userService.findByStatus(status);
    }

    /**
     * JSON 请求体示例
     * POST http://localhost:8080/user/create
     * Content-Type: application/json
     * Body: {"name":"Alice","email":"alice@example.com","status":"ACTIVE"}
     */
    @Path("/user/create")
    @Transactional
    public Map<String, Object> createUser(User user) {
        userService.save(user);
        return Map.of("ok", true, "userId", user.getId());
    }

    /**
     * 混合参数示例（使用 @JsonAttribute）
     * POST http://localhost:8080/user/update
     * Body: {"user":{"id":1,"name":"Bob"},"operator":"admin"}
     */
    @Path("/user/update")
    @Transactional
    public Map<String, Object> updateUser(
        cc.jfire.boot.forward.openapi.@JsonAttribute("user") User user,
        cc.jfire.boot.forward.openapi.@JsonAttribute("operator") String operator
    ) {
        userService.update(user);
        return Map.of("ok", true, "operator", operator);
    }

    /**
     * 获取原始请求对象示例
     */
    @Path("/user/raw")
    public Map<String, Object> rawRequest(HttpRequestExtend request) {
        return Map.of(
            "method", request.getMethod(),
            "path", request.getPath(),
            "headers", request.getHeaders(),
            "params", request.getParamMap()
        );
    }
}
