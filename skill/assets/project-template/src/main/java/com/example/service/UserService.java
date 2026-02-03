package com.example.service;

import cc.jfire.baseutil.Resource;
import cc.jfire.jfire.core.aop.impl.support.transaction.Transactional;
import com.example.entity.User;
import com.example.mapper.UserMapper;

import java.util.List;

/**
 * 用户服务类
 * 业务逻辑层，使用 @Resource 标注为 Bean
 */
@Resource
public class UserService {

    @Resource
    private UserMapper userMapper;

    /**
     * 根据 ID 查询用户
     * 所有访问数据库的方法都必须标注 @Transactional
     */
    @Transactional
    public User findById(Long id) {
        return userMapper.load(id);
    }

    /**
     * 根据状态查询用户列表
     */
    @Transactional
    public List<User> findByStatus(String status) {
        return userMapper.findByStatus(status);
    }

    /**
     * 保存用户
     */
    @Transactional
    public void save(User user) {
        user.setCreatedAt(System.currentTimeMillis());
        user.setUpdatedAt(System.currentTimeMillis());
        userMapper.save(user);
    }

    /**
     * 更新用户
     */
    @Transactional
    public void update(User user) {
        user.setUpdatedAt(System.currentTimeMillis());
        userMapper.update(user);
    }

    /**
     * 删除用户
     */
    @Transactional
    public void delete(Long id) {
        userMapper.delete(id);
    }
}
