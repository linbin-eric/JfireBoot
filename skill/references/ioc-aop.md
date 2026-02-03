# IOC 和 AOP 详细指南

本文档详细介绍 JfireBoot 中 Jfire IOC/AOP 容器的使用方法和最佳实践。

## 目录

- [IOC 容器](#ioc-容器)
- [依赖注入](#依赖注入)
- [配置管理](#配置管理)
- [条件注解](#条件注解)
- [AOP 切面编程](#aop-切面编程)
- [最佳实践](#最佳实践)

## IOC 容器

### 启动容器

```java
import cc.jfire.jfire.core.ApplicationContext;

// 启动类必须标注 @Configuration
ApplicationContext context = ApplicationContext.boot(AppConfig.class);
```

### 组件扫描

使用 `@ComponentScan` 扫描指定包下的 Bean：

```java
@Configuration
@ComponentScan("com.example")  // 扫描单个包
@ComponentScan({"com.example.service", "com.example.controller"})  // 扫描多个包
public class AppConfig {}
```

被 `@Resource` 标注的类会被自动注册为 Bean。

### 自动配置

使用 `@EnableAutoConfiguration` 启用自动配置：

```java
@Configuration
@EnableAutoConfiguration
public class AppConfig {}
```

自动配置会读取 `META-INF/autoconfig/` 目录下的配置类。

### 手动注册 Bean

#### 方式 1：使用 @Bean

```java
@Configuration
public class AppConfig {
    @Bean
    public MyService myService() {
        return new MyService();
    }

    @Bean
    public MyRepository myRepository(DataSource dataSource) {
        // 参数会自动注入
        return new MyRepository(dataSource);
    }
}
```

#### 方式 2：注册外部实例

```java
ApplicationContext context = ApplicationContext.boot(AppConfig.class);

// 注册已存在的对象实例
MyService externalService = new MyService();
context.registerBeanRegisterInfo(
    new OutterBeanRegisterInfo(externalService, "myService")
);

// 获取 Bean
MyService service = context.getBean("myService");
MyService serviceByType = context.getBean(MyService.class);
```

适用场景：
- 集成第三方库创建的对象（连接池、客户端等）
- 需要在容器启动前手动配置的对象
- 将非 Jfire 管理的对象纳入 IOC 容器

## 依赖注入

### 字段注入

```java
@Resource
public class UserService {
    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisClient redisClient;
}
```

### 构造器注入（推荐）

```java
@Resource
public class UserService {
    private final UserMapper userMapper;
    private final RedisClient redisClient;

    // 构造器参数会自动注入
    public UserService(UserMapper userMapper, RedisClient redisClient) {
        this.userMapper = userMapper;
        this.redisClient = redisClient;
    }
}
```

### 按名称注入

```java
@Resource
public class UserService {
    @Resource("primaryDataSource")
    private DataSource dataSource;
}
```

### 可选依赖

```java
@Resource
public class UserService {
    @Resource(required = false)
    private CacheService cacheService;  // 如果不存在，不会报错
}
```

## 配置管理

### 读取配置文件

使用 `@PropertyPath` 指定配置文件路径：

```java
// 读取 classpath 下的配置
@Configuration
@PropertyPath("classpath:application.yml")
public class AppConfig {}

// 读取 jar 同级目录的配置
@Configuration
@PropertyPath("file:conf/application.yml")
public class AppConfig {}

// 读取多个配置文件
@Configuration
@PropertyPath({"classpath:application.yml", "file:conf/custom.yml"})
public class AppConfig {}
```

### 注入配置值

```java
@Resource
public class MyService {
    @Value("${app.name}")
    private String appName;

    @Value("${app.port:8080}")  // 默认值
    private int port;

    @Value("${app.enabled:true}")
    private boolean enabled;
}
```

### 配置类

```java
@Configuration
@PropertyPath("classpath:application.yml")
public class AppProperties {
    @Value("${database.url}")
    private String dbUrl;

    @Value("${database.username}")
    private String dbUsername;

    @Value("${database.password}")
    private String dbPassword;

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        return new HikariDataSource(config);
    }
}
```

## 条件注解

### @ConditionOnProperty

根据配置属性决定是否创建 Bean：

```java
@Configuration
public class CacheConfig {
    @Bean
    @ConditionOnProperty("cache.type=redis")
    public RedisCache redisCache() {
        return new RedisCache();
    }

    @Bean
    @ConditionOnProperty("cache.type=memory")
    public MemoryCache memoryCache() {
        return new MemoryCache();
    }
}
```

### @ConditionOnClass

根据类是否存在决定是否创建 Bean：

```java
@Configuration
public class DataSourceConfig {
    @Bean
    @ConditionOnClass({com.mysql.cj.jdbc.Driver.class})
    public DataSource mysqlDataSource() {
        return new HikariDataSource();
    }

    @Bean
    @ConditionOnClass({org.postgresql.Driver.class})
    public DataSource postgresDataSource() {
        return new HikariDataSource();
    }
}
```

### @ConditionOnMissBeanType

当容器中不存在指定类型的 Bean 时才创建：

```java
@Configuration
public class DefaultConfig {
    @Bean
    @ConditionOnMissBeanType(DataSource.class)
    public DataSource defaultDataSource() {
        // 只有当容器中没有 DataSource 时才创建
        return new HikariDataSource();
    }
}
```

### 组合使用

```java
@Bean
@ConditionOnProperty("feature.enabled=true")
@ConditionOnClass({SomeLibrary.class})
@ConditionOnMissBeanType(FeatureService.class)
public FeatureService featureService() {
    return new FeatureService();
}
```

## AOP 切面编程

### 基本概念

Jfire AOP 支持方法拦截，可以在方法执行前后插入自定义逻辑。

### 定义切面

```java
import cc.jfire.jfire.core.aop.ProceedPoint;
import cc.jfire.jfire.core.aop.notated.After;
import cc.jfire.jfire.core.aop.notated.Before;
import cc.jfire.jfire.core.aop.notated.EnhanceClass;

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

### 方法匹配表达式

**重要**：表达式必须包含括号 `()`

```java
// 匹配所有 save 开头的方法，参数任意
@Before("save*(*)")

// 匹配无参方法
@Before("query*()")

// 匹配特定参数类型（支持简单类名或全限定名）
@Before("update*(String,int)")
@Before("update*(java.lang.String,int)")

// 匹配所有方法
@Before("*(*)")
```

### Around 环绕通知

```java
import cc.jfire.jfire.core.aop.notated.Around;

@EnhanceClass("com.example.service.*")
public class PerformanceAspect {

    @Around("*(*)")
    public Object measureTime(ProceedPoint point) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            // 执行原方法
            return point.proceed();
        } finally {
            long duration = System.currentTimeMillis() - start;
            System.out.println(point.getMethod().methodName() + " took " + duration + "ms");
        }
    }
}
```

### 获取方法信息

```java
@Before("*(*)")
public void logMethod(ProceedPoint point) {
    // 方法名
    String methodName = point.getMethod().methodName();

    // 参数
    Object[] args = point.getArgs();

    // 目标对象
    Object target = point.getTarget();

    // 返回值类型
    Class<?> returnType = point.getMethod().getReturnType();
}
```

### 切面执行顺序

使用 `order` 参数控制切面执行顺序（数值越小越先执行）：

```java
@EnhanceClass(value = "com.example.service.*", order = 100)
public class FirstAspect {}

@EnhanceClass(value = "com.example.service.*", order = 200)
public class SecondAspect {}
```

### 事务切面

JfireBoot 内置了事务支持，使用 `@Transactional` 注解：

```java
import cc.jfire.jfire.core.aop.impl.support.transaction.Transactional;

@Resource
public class UserService {

    @Transactional  // 自动开启事务
    public void saveUser(User user) {
        userMapper.save(user);
    }

    @Transactional(readOnly = true)  // 只读事务
    public User findUser(Long id) {
        return userMapper.load(id);
    }
}
```

## 最佳实践

### 1. Bean 命名规范

```java
// 推荐：使用类名首字母小写
@Resource
public class UserService {}  // Bean 名称: userService

// 显式指定名称
@Resource("customUserService")
public class UserService {}
```

### 2. 避免循环依赖

```java
// 不推荐：循环依赖
@Resource
public class ServiceA {
    @Resource
    private ServiceB serviceB;
}

@Resource
public class ServiceB {
    @Resource
    private ServiceA serviceA;  // 循环依赖
}

// 推荐：重构设计，提取公共服务
@Resource
public class CommonService {}

@Resource
public class ServiceA {
    @Resource
    private CommonService commonService;
}

@Resource
public class ServiceB {
    @Resource
    private CommonService commonService;
}
```

### 3. 使用接口编程

```java
// 定义接口
public interface UserService {
    User findById(Long id);
}

// 实现类
@Resource
public class UserServiceImpl implements UserService {
    @Override
    public User findById(Long id) {
        return userMapper.load(id);
    }
}

// 注入时使用接口
@Resource
public class UserController {
    @Resource
    private UserService userService;  // 注入接口
}
```

### 4. 配置类分离

```java
// 数据源配置
@Configuration
public class DataSourceConfig {
    @Bean
    public DataSource dataSource() { ... }
}

// 缓存配置
@Configuration
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() { ... }
}

// 主配置类
@Configuration
@EnableAutoConfiguration
@ComponentScan("com.example")
public class AppConfig {}
```

### 5. AOP 性能优化

```java
// 推荐：精确匹配，避免过度拦截
@EnhanceClass("com.example.service.UserService")
public class UserServiceAspect {
    @Before("save*(*)")  // 只拦截 save 开头的方法
    public void beforeSave(ProceedPoint point) { ... }
}

// 不推荐：拦截所有方法
@EnhanceClass("com.example.*")
public class GlobalAspect {
    @Before("*(*)")  // 性能开销大
    public void beforeAll(ProceedPoint point) { ... }
}
```

### 6. 事务边界控制

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

### 7. 懒加载

```java
@Resource
public class HeavyService {
    private ExpensiveResource resource;

    // 延迟初始化
    public ExpensiveResource getResource() {
        if (resource == null) {
            resource = new ExpensiveResource();
        }
        return resource;
    }
}
```

### 8. 生命周期管理

```java
@Resource
public class MyService {
    private Connection connection;

    // 初始化方法
    @PostConstruct
    public void init() {
        connection = createConnection();
    }

    // 销毁方法
    @PreDestroy
    public void destroy() {
        if (connection != null) {
            connection.close();
        }
    }
}
```
