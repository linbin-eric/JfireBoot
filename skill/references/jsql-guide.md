# Jsql 持久层完整指南

本文档详细介绍 JfireBoot 中 Jsql 持久层的使用方法和最佳实践。

## 目录

- [快速开始](#快速开始)
- [实体映射](#实体映射)
- [Mapper 接口](#mapper-接口)
- [事务管理](#事务管理)
- [高级查询](#高级查询)
- [性能优化](#性能优化)
- [最佳实践](#最佳实践)

## 快速开始

### 1. 配置 DataSource

JfireBoot 的 Jsql Starter 需要一个 `DataSource` Bean：

```java
@Configuration
@EnableAutoConfiguration
public class AppConfig {
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=UTC");
        config.setUsername("root");
        config.setPassword("password");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // 连接池配置
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        return new HikariDataSource(config);
    }
}
```

### 2. 自动装配的 Bean

启用 `@EnableAutoConfiguration` 后，以下 Bean 会自动注册：

- `sessionFactory`: `cc.jfire.jsql.SessionFactory`
- `transactionManager`: `cc.jfire.jfire.core.aop.impl.support.transaction.JdbcTransactionManager`
- `sqlSession`: `cc.jfire.jsql.session.SqlSession`（代理实现，标注 `@Primary`）
- `readOnlySession`: `cc.jfire.starter.jsql.ReadOnlySession`（只读实现）
- `mapperFactory`: `cc.jfire.starter.jsql.MapperFactory`

### 3. 事务要求（重要）

**所有使用 `SqlSession` 或 `Mapper` 访问数据库的方法都必须标注 `@Transactional`**，否则会抛出异常：

```java
@Resource
public class UserService {
    @Resource
    private UserMapper userMapper;

    @Transactional  // 必须
    public User findById(Long id) {
        return userMapper.load(id);
    }
}
```

## 实体映射

### 基本注解

```java
import cc.jfire.jsql.annotation.Column;
import cc.jfire.jsql.annotation.Id;
import cc.jfire.jsql.annotation.Table;

@Table("users")  // 映射到 users 表
public class User {

    @Id  // 主键
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

    // getter/setter 或使用 Lombok @Data
}
```

### 字段类型映射

Jsql 支持以下 Java 类型到数据库类型的映射：

| Java 类型 | 数据库类型 |
|----------|----------|
| `Long/long` | BIGINT |
| `Integer/int` | INT |
| `String` | VARCHAR |
| `Boolean/boolean` | TINYINT(1) |
| `BigDecimal` | DECIMAL |
| `Date` | DATETIME |
| `byte[]` | BLOB |

### 自增主键

```java
@Table("users")
public class User {
    @Id
    @Column("id")
    private Long id;  // 保存后会自动填充生成的 ID

    // 其他字段...
}
```

### 复合主键

```java
@Table("user_roles")
public class UserRole {
    @Id
    @Column("user_id")
    private Long userId;

    @Id
    @Column("role_id")
    private Long roleId;

    // 其他字段...
}
```

### 忽略字段

```java
@Table("users")
public class User {
    @Id
    @Column("id")
    private Long id;

    @Column("name")
    private String name;

    // 不映射到数据库
    private transient String tempField;
}
```

## Mapper 接口

### 基础 CRUD

使用 `@AutoMapper` 标注接口，继承 `Repository<T>` 获得基础 CRUD 方法：

```java
import cc.jfire.jsql.mapper.Repository;
import cc.jfire.starter.jsql.AutoMapper;

@AutoMapper
public interface UserMapper extends Repository<User> {
    // 继承的方法：
    // User load(Long id)                    - 根据主键查询
    // void save(User entity)                - 插入
    // void update(User entity)              - 更新
    // void delete(Long id)                  - 根据主键删除
    // List<User> list()                     - 查询所有
}
```

### 自定义查询

使用 `@Sql` 注解定义自定义 SQL：

```java
@AutoMapper
public interface UserMapper extends Repository<User> {

    // 简单查询
    @Sql(sql = "SELECT * FROM users WHERE status = ${status}", paramNames = "status")
    List<User> findByStatus(String status);

    // 多参数查询
    @Sql(
        sql = "SELECT * FROM users WHERE name LIKE ${name} AND status = ${status}",
        paramNames = {"name", "status"}
    )
    List<User> findByNameAndStatus(String name, String status);

    // 统计查询
    @Sql(sql = "SELECT COUNT(*) FROM users WHERE status = ${status}", paramNames = "status")
    Long countByStatus(String status);

    // 单个结果
    @Sql(sql = "SELECT * FROM users WHERE email = ${email}", paramNames = "email")
    User findByEmail(String email);

    // 更新操作
    @Sql(sql = "UPDATE users SET status = ${status} WHERE id = ${id}", paramNames = {"status", "id"})
    void updateStatus(String status, Long id);

    // 删除操作
    @Sql(sql = "DELETE FROM users WHERE status = ${status}", paramNames = "status")
    void deleteByStatus(String status);
}
```

### 参数绑定

```java
// 位置参数（按顺序）
@Sql(sql = "SELECT * FROM users WHERE name = ${0} AND status = ${1}")
List<User> findByNameAndStatus(String name, String status);

// 命名参数（推荐）
@Sql(sql = "SELECT * FROM users WHERE name = ${name} AND status = ${status}",
     paramNames = {"name", "status"})
List<User> findByNameAndStatus(String name, String status);
```

### 返回类型

```java
// 返回单个实体
@Sql(sql = "SELECT * FROM users WHERE id = ${id}", paramNames = "id")
User findById(Long id);

// 返回列表
@Sql(sql = "SELECT * FROM users WHERE status = ${status}", paramNames = "status")
List<User> findByStatus(String status);

// 返回基本类型
@Sql(sql = "SELECT COUNT(*) FROM users", paramNames = {})
Long count();

@Sql(sql = "SELECT name FROM users WHERE id = ${id}", paramNames = "id")
String findNameById(Long id);

// 返回 Map
@Sql(sql = "SELECT id, name FROM users WHERE id = ${id}", paramNames = "id")
Map<String, Object> findMapById(Long id);

// 返回 Map 列表
@Sql(sql = "SELECT id, name FROM users WHERE status = ${status}", paramNames = "status")
List<Map<String, Object>> findMapsByStatus(String status);
```

## 事务管理

### 基本事务

```java
import cc.jfire.jfire.core.aop.impl.support.transaction.Transactional;

@Resource
public class UserService {
    @Resource
    private UserMapper userMapper;

    @Transactional  // 开启事务
    public void createUser(User user) {
        user.setCreatedAt(System.currentTimeMillis());
        user.setUpdatedAt(System.currentTimeMillis());
        userMapper.save(user);
    }
}
```

### 只读事务

```java
@Transactional(readOnly = true)  // 只读事务，性能更好
public User findById(Long id) {
    return userMapper.load(id);
}

@Transactional(readOnly = true)
public List<User> findAll() {
    return userMapper.list();
}
```

### 事务传播

```java
@Resource
public class UserService {
    @Resource
    private LogService logService;

    @Transactional
    public void createUser(User user) {
        userMapper.save(user);
        // logService.saveLog() 会加入当前事务
        logService.saveLog(new Log("create user"));
    }
}

@Resource
public class LogService {
    @Transactional  // 加入外部事务
    public void saveLog(Log log) {
        logMapper.save(log);
    }
}
```

### 事务回滚

```java
@Transactional
public void createUser(User user) {
    userMapper.save(user);

    if (user.getEmail() == null) {
        // 抛出异常会自动回滚事务
        throw new IllegalArgumentException("Email is required");
    }

    logMapper.saveLog(new Log("create user"));
}
```

### 手动控制事务

```java
@Resource
public class UserService {
    @Resource
    private SqlSession sqlSession;

    public void manualTransaction() {
        try {
            sqlSession.beginTransaction();

            // 执行数据库操作
            userMapper.save(new User());

            sqlSession.commit();
        } catch (Exception e) {
            sqlSession.rollback();
            throw e;
        }
    }
}
```

## 高级查询

### 动态 SQL

```java
@Resource
public class UserService {
    @Resource
    private SqlSession sqlSession;

    @Transactional
    public List<User> search(String name, String status, Integer minAge) {
        StringBuilder sql = new StringBuilder("SELECT * FROM users WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (name != null) {
            sql.append(" AND name LIKE ?");
            params.add("%" + name + "%");
        }

        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status);
        }

        if (minAge != null) {
            sql.append(" AND age >= ?");
            params.add(minAge);
        }

        return sqlSession.query(sql.toString(), User.class, params.toArray());
    }
}
```

### 分页查询

```java
@AutoMapper
public interface UserMapper extends Repository<User> {

    @Sql(
        sql = "SELECT * FROM users WHERE status = ${status} LIMIT ${offset}, ${limit}",
        paramNames = {"status", "offset", "limit"}
    )
    List<User> findByStatusWithPage(String status, int offset, int limit);

    @Sql(sql = "SELECT COUNT(*) FROM users WHERE status = ${status}", paramNames = "status")
    Long countByStatus(String status);
}

// 使用示例
@Resource
public class UserService {
    @Transactional
    public Page<User> findByStatusPage(String status, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        List<User> users = userMapper.findByStatusWithPage(status, offset, pageSize);
        Long total = userMapper.countByStatus(status);

        return new Page<>(users, total, pageNum, pageSize);
    }
}
```

### 批量操作

```java
@Resource
public class UserService {
    @Resource
    private SqlSession sqlSession;

    @Transactional
    public void batchInsert(List<User> users) {
        for (User user : users) {
            userMapper.save(user);
        }
    }

    @Transactional
    public void batchUpdate(List<User> users) {
        for (User user : users) {
            userMapper.update(user);
        }
    }

    @Transactional
    public void batchDelete(List<Long> ids) {
        for (Long id : ids) {
            userMapper.delete(id);
        }
    }
}
```

### 关联查询

```java
// 一对多关联
@AutoMapper
public interface UserMapper extends Repository<User> {

    @Sql(
        sql = "SELECT u.*, o.id as order_id, o.amount as order_amount " +
              "FROM users u LEFT JOIN orders o ON u.id = o.user_id " +
              "WHERE u.id = ${userId}",
        paramNames = "userId"
    )
    List<Map<String, Object>> findUserWithOrders(Long userId);
}

// 手动组装结果
@Resource
public class UserService {
    @Transactional
    public UserWithOrders findUserWithOrders(Long userId) {
        List<Map<String, Object>> rows = userMapper.findUserWithOrders(userId);

        if (rows.isEmpty()) {
            return null;
        }

        // 组装用户信息
        Map<String, Object> firstRow = rows.get(0);
        User user = new User();
        user.setId((Long) firstRow.get("id"));
        user.setName((String) firstRow.get("name"));

        // 组装订单列表
        List<Order> orders = rows.stream()
            .filter(row -> row.get("order_id") != null)
            .map(row -> {
                Order order = new Order();
                order.setId((Long) row.get("order_id"));
                order.setAmount((BigDecimal) row.get("order_amount"));
                return order;
            })
            .collect(Collectors.toList());

        return new UserWithOrders(user, orders);
    }
}
```

## 性能优化

### 1. 使用只读事务

```java
// 查询操作使用只读事务
@Transactional(readOnly = true)
public List<User> findAll() {
    return userMapper.list();
}
```

### 2. 批量操作优化

```java
// 不推荐：逐条插入
@Transactional
public void saveUsers(List<User> users) {
    for (User user : users) {
        userMapper.save(user);  // N 次数据库交互
    }
}

// 推荐：使用批量 SQL
@Transactional
public void saveUsersBatch(List<User> users) {
    StringBuilder sql = new StringBuilder("INSERT INTO users (name, email, status) VALUES ");
    List<Object> params = new ArrayList<>();

    for (int i = 0; i < users.size(); i++) {
        if (i > 0) sql.append(", ");
        sql.append("(?, ?, ?)");
        params.add(users.get(i).getName());
        params.add(users.get(i).getEmail());
        params.add(users.get(i).getStatus());
    }

    sqlSession.execute(sql.toString(), params.toArray());
}
```

### 3. 避免 N+1 查询

```java
// 不推荐：N+1 查询
@Transactional
public List<UserWithOrders> findUsersWithOrders() {
    List<User> users = userMapper.list();  // 1 次查询
    return users.stream()
        .map(user -> {
            List<Order> orders = orderMapper.findByUserId(user.getId());  // N 次查
            return new UserWithOrders(user, orders);
        })
        .collect(Collectors.toList());
}

// 推荐：使用 JOIN 或 IN 查询
@Transactional
public List<UserWithOrders> findUsersWithOrdersOptimized() {
    List<User> users = userMapper.list();  // 1 次查询
    List<Long> userIds = users.stream().map(User::getId).collect(Collectors.toList());

    // 一次性查询所有订单
    List<Order> allOrders = orderMapper.findByUserIds(userIds);  // 1 次查询

    // 内存中组装
    Map<Long, List<Order>> orderMap = allOrders.stream()
        .collect(Collectors.groupingBy(Order::getUserId));

    return users.stream()
        .map(user -> new UserWithOrders(user, orderMap.getOrDefault(user.getId(), List.of())))
        .collect(Collectors.toList());
}
```

### 4. 连接池配置

```java
@Bean
public DataSource dataSource() {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:mysql://localhost:3306/mydb");
    config.setUsername("root");
    config.setPassword("password");

    // 性能优化配置
    config.setMaximumPoolSize(20);  // 最大连接数
    config.setMinimumIdle(5);       // 最小空闲连接
    config.setConnectionTimeout(30000);  // 连接超时
    config.setIdleTimeout(600000);       // 空闲超时
    config.setMaxLifetime(1800000);      // 连接最大生命周期

    // 连接测试
    config.setConnectionTestQuery("SELECT 1");

    return new HikariDataSource(config);
}
```

### 5. 索引优化

```sql
-- 为常用查询字段添加索引
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_created_at ON users(created_at);

-- 复合索引
CREATE INDEX idx_users_status_created ON users(status, created_at);
```

## 最佳实践

### 1. 分层架构

```
Controller -> Service -> Mapper -> Database
```

- **Controller**: 处理 HTTP 请求，不包含业务逻辑
- **Service**: 业务逻辑层，标注 `@Transactional`
- **Mapper**: 数据访问层，定义 SQL

### 2. 事务边界

```java
// 推荐：在 Service 层控制事务
@Resource
public class UserService {
    @Transactional
    public void createUser(User user) {
        userMapper.save(user);
        logMapper.saveLog(new Log("create user"));
    }
}

// 不推荐：在 Controller 层控制事务
@Resource
public class UserController {
    @Path("/user/create")
    @Transactional  // 事务范围过大
    public Object createUser(User user) {
        userService.save(user);
        return "ok";
    }
}
```

### 3. 异常处理

```java
@Resource
public class UserService {
    @Transactional
    public void createUser(User user) {
        try {
            userMapper.save(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("用户已存在");
        } catch (Exception e) {
            throw new BusinessException("创建用户失败", e);
        }
    }
}
```

### 4. 实体设计

```java
// 推荐：使用 Lombok 简化代码
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
```

### 5. Mapper 命名规范

```java
@AutoMapper
public interface UserMapper extends Repository<User> {
    // 查询：find/get/query
    User findById(Long id);
    List<User> findByStatus(String status);

    // 统计：count
    Long countByStatus(String status);

    // 更新：update
    void updateStatus(String status, Long id);

    // 删除：delete/remove
    void deleteByStatus(String status);

    // 保存：save/insert
    void saveUser(User user);
}
```

### 6. SQL 注入防护

```java
// 推荐：使用参数绑定
@Sql(sql = "SELECT * FROM users WHERE name = ${name}", paramNames = "name")
List<User> findByName(String name);

// 不推荐：字符串拼接（SQL 注入风险）
@Sql(sql = "SELECT * FROM users WHERE name = '" + name + "'")
List<User> findByName(String name);
```

### 7. 日志记录

```java
@Resource
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    @Transactional
    public void createUser(User user) {
        log.info("Creating user: {}", user.getName());
        try {
            userMapper.save(user);
            log.info("User created successfully: {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to create user: {}", user.getName(), e);
            throw e;
        }
    }
}
```

### 8. 测试

```java
@RunWith(JUnit4.class)
public class UserServiceTest {
    @Resource
    private UserService userService;

    @Test
    @Transactional  // 测试事务会自动回滚
    public void testCreateUser() {
        User user = new User();
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setStatus("ACTIVE");

        userService.createUser(user);

        assertNotNull(user.getId());
        assertEquals("Test User", user.getName());
    }
}
```
