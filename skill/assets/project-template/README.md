# JfireBoot 应用模板

这是一个基于 JfireBoot 框架的 Web 应用模板，集成了 IOC 容器和 Jsql 持久层。

## 快速开始

### 1. 配置数据库

修改 `src/main/java/com/example/AppConfig.java` 中的数据库连接信息：

```java
config.setJdbcUrl("jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=UTC");
config.setUsername("root");
config.setPassword("password");
```

### 2. 创建数据库表

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

### 3. 构建并运行

```bash
mvn clean package
java -jar target/my-jfireboot-app-1.0.0-SNAPSHOT.jar
```

### 4. 访问应用

打开浏览器访问: http://localhost:8080

## 项目结构

```
src/main/java/com/example/
├── Application.java          # 应用启动类
├── AppConfig.java           # 配置类（IOC、数据源）
├── controller/              # 控制器层
│   └── UserController.java
├── service/                 # 服务层
│   └── UserService.java
├── mapper/                  # 数据访问层
│   └── UserMapper.java
└── entity/                  # 实体类
    └── User.java

src/main/resources/
├── application.yml          # 应用配置
├── log4j2.xml              # 日志配置
└── web/                    # 静态资源
    └── index.html
```

## 核心特性

- **IOC/AOP**: 基于 Jfire 的依赖注入和面向切面编程
- **HTTP 路由**: 使用 `@Path` 注解定义 RESTful 接口
- **持久层**: 集成 Jsql，支持 Mapper 和事务管理
- **参数绑定**: 自动解析 JSON、表单、路径变量
- **静态资源**: 支持静态文件服务

## 开发指南

### 添加新的控制器

1. 在 `controller` 包下创建新类
2. 使用 `@Resource` 标注为 Bean
3. 使用 `@Path` 定义路由
4. 访问数据库的方法必须标注 `@Transactional`

### 添加新的 Mapper

1. 在 `mapper` 包下创建接口
2. 使用 `@AutoMapper` 标注
3. 继承 `Repository<T>` 获得基础 CRUD
4. 使用 `@Sql` 定义自定义查询

### 配置 AOP

在 `AppConfig` 或单独的配置类中使用 `@EnhanceClass` 和 `@Before/@After` 注解。

## 许可证

AGPL-3.0
