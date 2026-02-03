---
name: jfireboot-webapp
description: 使用 JfireBoot 框架构建基于 IOC 容器的 Web 应用，集成 Jsql 持久层。当用户需要：(1) 创建新的 JfireBoot Web 应用项目，(2) 配置 Jfire IOC/AOP 容器，(3) 定义 HTTP 路由和控制器，(4) 集成 Jsql 数据库访问层，(5) 实现 RESTful API、文件上传、WebSocket、SSE 等功能，(6) 配置数据源和事务管理，或 (7) 学习 JfireBoot 最佳实践时使用此技能。
---

# JfireBoot Web 应用开发

## 概述

JfireBoot 是基于 **Jfire(IOC/AOP)** + **Jnet(AIO/HTTP)** + **Jsql(持久层)** 的轻量级 Web 应用框架。本技能帮助你快速构建生产级的 Web 应用。

**核心特性**：
- **IOC 容器**：依赖注入、组件扫描、自动配置
- **HTTP 路由**：使用 `@Path` 注解定义接口，支持 RESTful 路径变量
- **参数绑定**：自动解析 JSON、表单、文件上传
- **持久层**：Jsql ORM，支持 Mapper 接口和事务管理
- **高级特性**：WebSocket、SSE、中间件、ServiceId 网关

## 快速开始

### 1. 使用项目模板

项目模板位于 `assets/project-template/`，包含完整的项目结构：

```bash
# 复制模板到目标目录
cp -r assets/project-template/* /path/to/your/project/

# 修改 pom.xml 中的 groupId 和 artifactId
# 修改 AppConfig.java 中的数据库连接信息
```

**模板包含**：
- `pom.xml` - Maven 依赖配置
- `Application.java` - 应用启动类
- `AppConfig.java` - IOC 配置和数据源
- `controller/UserController.java` - 控制器示例
- `service/UserService.java` - 服务层示例
- `mapper/UserMapper.java` - Mapper 接口示例
- `entity/User.java` - 实体类示例
- `resources/application.yml` - 应用配置
- `resources/log4j2.xml` - 日志配置
- `resources/web/index.html` - 静态资源示例

### 2. 配置数据库

修改 `AppConfig.java` 中的数据源配置：

```java
@Bean
public DataSource dataSource() {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=UTC");
    config.setUsername("root");
    config.setPassword("password");
    config.setDriverClassName("com.mysql.cj.jdbc.Driver");
    return new HikariDataSource(config);
}
```

### 3. 创建数据库表

```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);
```

### 4. 启动应用

```bash
mvn clean package
java -jar target/my-jfireboot-app-1.0.0-SNAPSHOT.jar
```

访问 http://localhost:8080 查看欢迎页面。

## 核心工作流

### 工作流 1: 创建新的 HTTP 接口

**步骤**：

1. **定义实体类**（如果需要数据库操作）

```java
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
}
```

2. **创建 Mapper 接口**（数据访问层）

```java
@AutoMapper
public interface UserMapper extends Repository<User> {
    @Sql(sql = "SELECT * FROM users WHERE status = ${status}", paramNames = "status")
    List<User> findByStatus(String status);
}
```

提示：可以使用 `scripts/generate_mapper.py` 快速生成 Mapper 模板：

```bash
python scripts/generate_mapper.py User users com.example.mapper
```

3. **创建 Service 类**（业务逻辑层）

```java
@Resource
public class UserService {
    @Resource
    private UserMapper userMapper;

    @Transactional  // 必须标注事务
    public User findById(Long id) {
        return userMapper.load(id);
    }
}
```

4. **创建 Controller 类**（控制器层）

```java
@Resource
public class UserController {
    @Resource
    private UserService userService;

    @Path("/user/${id}")
    @Transactional
    public User getUser(Long id) {
        return userService.findById(id);
    }
}
```

5. **测试接口**

```bash
curl http://localhost:8080/user/1
```

### 工作流 2: 实现文件上传

1. **创建上传接口**

```java
@Resource
public class UploadController {
    @Path("/upload")
    public Object upload(List<FilePart> files, String remark) {
        if (files == null || files.isEmpty()) {
            return Map.of("ok", false, "message", "No file uploaded");
        }

        FilePart file = files.get(0);
        String savePath = "/tmp/" + file.getFileName();

        try (FileOutputStream fos = new FileOutputStream(savePath)) {
            fos.write(file.getIoBuffer().readableBytes());
        }

        return Map.of("ok", true, "fileName", file.getFileName());
    }
}
```

2. **前端表单**

```html
<form action="/upload" method="post" enctype="multipart/form-data">
    <input type="file" name="file" />
    <input type="text" name="remark" />
    <button type="submit">上传</button>
</form>
```

### 工作流 3: 配置 AOP 切面

1. **创建切面类**

```java
@EnhanceClass(value = "com.example.service.*", order = 100)
public class LoggingAspect {
    @Before("save*(*)")
    public void beforeSave(ProceedPoint point) {
        System.out.println("Before save: " + point.getMethod().methodName());
    }

    @After("save*(*)")
    public void afterSave(ProceedPoint point) {
        System.out.println("After save: " + point.getMethod().methodName());
    }
}
```

2. **注册切面**（自动扫描）

确保切面类在 `@ComponentScan` 扫描的包下，或使用 `@Configuration` 手动注册。

### 工作流 4: 实现 SSE 流式响应

```java
@Resource
public class SseController {
    @Path("/sse")
    public void sse(Pipeline pipeline) {
        Thread.startVirtualThread(() -> {
            HttpResponsePartHead head = new HttpResponsePartHead();
            head.setVersion("HTTP/1.1");
            head.setStatusCode(200);
            head.addHeader("Content-Type", "text/event-stream");
            head.addHeader("Cache-Control", "no-cache");
            pipeline.fireWrite(head);

            for (int i = 0; i < 10; i++) {
                if (!pipeline.isOpen()) return;

                String data = "data: tick-" + i + "\n\n";
                IoBuffer buf = pipeline.allocator().allocate(data.getBytes().length);
                buf.put(data.getBytes());
                pipeline.fireWrite(buf);

                Thread.sleep(1000);
            }
        });
    }
}
```

## 常见任务

### 任务 1: 参数绑定

**简单类型参数**：

```java
@Path("/search")
public List<User> search(String name, Integer age, Boolean active) {
    // 自动从 query 参数或 body 中解析
}
```

**POJO 参数**（单个对象）：

```java
@Path("/user/create")
public Object create(User user) {
    // Content-Type: application/json
    // Body: {"name":"Alice","email":"alice@example.com"}
}
```

**混合参数**（使用 `@JsonAttribute`）：

```java
@Path("/user/update")
public Object update(
    @JsonAttribute("user") User user,
    @JsonAttribute("operator") String operator
) {
    // Body: {"user":{...},"operator":"admin"}
}
```

**RESTful 路径变量**：

```java
@Path("/user/${id}/orders/${orderId}")
public Object getOrder(Long id, Long orderId) {
    // 访问: /user/123/orders/456
}
```

### 任务 2: 响应处理

**返回 JSON**（默认）：

```java
@Path("/user/list")
public List<User> list() {
    return userService.findAll();  // 自动序列化为 JSON
}
```

**自定义状态码**：

```java
@Path("/deny")
public HttpResponse deny() {
    HttpResponse resp = new HttpResponse();
    resp.getHead().setStatusCode(401);
    resp.getHead().setReasonPhrase("Unauthorized");
    resp.setBodyText("Unauthorized");
    return resp;
}
```

**返回文件**：

```java
@Path("/download")
public HttpResponse download() {
    HttpResponse resp = new HttpResponse();
    resp.getHead().addHeader("Content-Type", "application/octet-stream");
    resp.getHead().addHeader("Content-Disposition", "attachment; filename=file.txt");
    resp.setBodyText("File content");
    return resp;
}
```

### 任务 3: 事务管理

**基本事务**：

```java
@Transactional  // 所有数据库操作必须在事务中
public void createUser(User user) {
    userMapper.save(user);
}
```

**只读事务**（性能更好）：

```java
@Transactional(readOnly = true)
public User findById(Long id) {
    return userMapper.load(id);
}
```

**事务传播**：

```java
@Transactional
public void createUserWithLog(User user) {
    userMapper.save(user);
    logService.saveLog(new Log("create user"));  // 加入当前事务
}
```

### 任务 4: 中间件配置

**日志中间件**：

```java
ReadProcessor<Object> logging = (data, next) -> {
    if (data instanceof HttpRequest req) {
        System.out.println("HTTP " + req.getHead().getMethod() + " " + req.getHead().getPath());
    }
    next.fireRead(data);
};
```

**鉴权中间件**：

```java
ReadProcessor<Object> auth = (data, next) -> {
    if (data instanceof HttpRequest req) {
        String token = req.getHead().getHeaders().get("Authorization");
        if (token == null) {
            req.close();
            HttpResponse resp = new HttpResponse();
            resp.getHead().setStatusCode(401);
            resp.setBodyText("Unauthorized");
            next.pipeline().fireWrite(resp);
            return;
        }
    }
    next.fireRead(data);
};
```

**注册中间件**：

```java
HttpAppServer.start(8080, context, "web", new ReadProcessor[]{logging, auth}, null);
```

## 详细参考文档

本技能包含详细的参考文档，按需查阅：

### references/ioc-aop.md
IOC 和 AOP 完整指南，包括：
- IOC 容器启动和配置
- 依赖注入的多种方式
- 配置文件读取和管理
- 条件注解的使用
- AOP 切面编程详解
- 最佳实践和性能优化

**何时查阅**：
- 需要深入理解 IOC 容器机制
- 配置复杂的依赖注入
- 实现 AOP 切面逻辑
- 使用条件注解进行动态配置

### references/jsql-guide.md
Jsql 持久层完整指南，包括：
- 实体映射和注解
- Mapper 接口定义
- 事务管理详解
- 高级查询技巧
- 性能优化策略
- 最佳实践

**何时查阅**：
- 需要复杂的数据库查询
- 实现分页、批量操作
- 处理关联查询
- 优化数据库性能
- 解决事务问题

### references/advanced-features.md
高级特性指南，包括：
- WebSocket 实现
- SSE 流式响应
- 文件上传处理
- 中间件开发
- ServiceId 网关
- 静态资源配置
- HTTPS/SSL 配置

**何时查阅**：
- 实现 WebSocket 实时通信
- 需要 SSE 推送功能
- 处理文件上传
- 开发自定义中间件
- 配置 API 网关
- 启用 HTTPS

## 最佳实践

### 1. 项目结构

```
src/main/java/com/example/
├── Application.java          # 启动类
├── AppConfig.java           # 配置类
├── controller/              # 控制器层（HTTP 接口）
├── service/                 # 服务层（业务逻辑 + 事务）
├── mapper/                  # 数据访问层（Mapper 接口）
├── entity/                  # 实体类
└── aspect/                  # AOP 切面
```

### 2. 分层职责

- **Controller**：处理 HTTP 请求，参数验证，不包含业务逻辑
- **Service**：业务逻辑，事务控制（标注 `@Transactional`）
- **Mapper**：数据访问，SQL 定义

### 3. 事务边界

```java
// ✅ 推荐：在 Service 层控制事务
@Resource
public class UserService {
    @Transactional
    public void createUser(User user) {
        userMapper.save(user);
        logMapper.saveLog(new Log("create user"));
    }
}

// ❌ 不推荐：在 Controller 层控制事务
@Path("/user/create")
@Transactional  // 事务范围过大
public Object createUser(User user) {
    userService.save(user);
    return "ok";
}
```

### 4. 异常处理

```java
@Resource
public class UserService {
    @Transactional
    public void createUser(User user) {
        try {
            userMapper.save(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("用户已存在");
        }
    }
}
```

### 5. 参数验证

```java
@Path("/user/create")
public Object create(User user) {
    if (user.getName() == null || user.getName().isBlank()) {
        return Map.of("ok", false, "message", "Name is required");
    }
    if (user.getEmail() == null || !user.getEmail().contains("@")) {
        return Map.of("ok", false, "message", "Invalid email");
    }
    userService.save(user);
    return Map.of("ok", true);
}
```

### 6. 日志记录

```java
@Resource
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    @Transactional
    public void createUser(User user) {
        log.info("Creating user: {}", user.getName());
        try {
            userMapper.save(user);
            log.info("User created: {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to create user", e);
            throw e;
        }
    }
}
```

## 常见问题

### Q1: 为什么抛出 "请先开启事务" 异常？

**原因**：所有使用 `SqlSession` 或 `Mapper` 的方法都必须标注 `@Transactional`。

**解决**：在 Service 方法上添加 `@Transactional` 注解。

### Q2: 如何处理循环依赖？

**解决**：重构设计，提取公共服务，避免 A 依赖 B 且 B 依赖 A 的情况。

### Q3: 参数绑定失败怎么办？

**检查**：
- Content-Type 是否正确
- 参数名是否匹配
- JSON 格式是否正确
- 是否使用了 `@JsonAttribute`

### Q4: 如何调试 SQL？

**方法**：在 `log4j2.xml` 中启用 Jsql 日志：

```xml
<Logger name="cc.jfire.jsql" level="debug" additivity="false">
    <AppenderRef ref="Console"/>
</Logger>
```

### Q5: 如何配置多数据源？

**方法**：创建多个 `DataSource` Bean，使用不同的名称，在 Mapper 中指定数据源。

## 资源说明

### scripts/
- `generate_mapper.py` - 生成 Mapper 接口模板的 Python 脚本

### references/
- `ioc-aop.md` - IOC/AOP 详细指南
- `jsql-guide.md` - Jsql 持久层完整指南
- `advanced-features.md` - 高级特性指南

### assets/
- `project-template/` - 完整的项目模板，包含所有必要文件
