# JfireBoot

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
[![Java Version](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

基于 Jfire 体系的快速 Boot 应用框架。引入 JfireBoot，可以快速构建高性能的 Web Java 应用。

## 特性

- **高性能** - 基于 NIO/AIO 的非阻塞 I/O，轻量级设计，启动快速
- **声明式路由** - 基于 `@Path` 注解的简洁路由定义，支持 RESTful 风格
- **智能参数解析** - 自动识别 JSON、URL-Form、MultiPart 等多种请求格式
- **文件上传** - 原生支持 MultiPart 文件上传和二进制数据处理
- **链路追踪** - MDC 集成日志链路追踪，支持 `@TraceId` 注解
- **OpenAPI 网关** - 基于 ServiceId 的统一 API 入口和服务路由
- **SSL/TLS 支持** - 支持 HTTPS 安全连接

## 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>cc.jfire</groupId>
    <artifactId>JfireBoot</artifactId>
    <version>1.0</version>
</dependency>
```

### 启动服务器

```java
// 创建应用上下文
ApplicationContext context = new ApplicationContext();
// ... 注册 Bean

// 启动服务器
HttpAppServer.start(8080, context);

// 或者指定静态资源目录
HttpAppServer.start(8080, context, "src/main/resources/static");
```

### 定义路由

```java
@Resource
public class UserService {

    // 简单路由
    @Path("/user/list")
    public List<User> list() {
        return userRepository.findAll();
    }

    // RESTful 路由 - 路径参数
    @Path("/user/${id}/detail")
    public User detail(int id) {
        return userRepository.findById(id);
    }

    // JSON 请求体
    @Path("/user/add")
    public Result add(User user) {
        userRepository.save(user);
        return Result.success();
    }

    // 文件上传
    @MultiPartPost
    @Path("/file/upload")
    public Result upload(List<BinaryPart> files) {
        files.forEach(this::saveFile);
        return Result.success();
    }

    // URL Form 表单
    @UrlFormPost
    @Path("/user/login")
    public Result login(String username, String password) {
        return authService.login(username, password);
    }

    // 链路追踪
    @TraceId
    @Path("/order/create")
    public Order createOrder(Order order) {
        return orderService.create(order);
    }
}
```

## 核心模块

```
cc.jfire.boot
├── common/                    # 通用工具模块
│   ├── TraceId.java          # 链路追踪注解
│   ├── EnhanceTraceId.java   # 链路追踪增强
│   └── MdcSupport.java       # MDC 日志支持
│
├── http/                      # HTTP 处理模块
│   ├── HttpAppServer.java    # HTTP 服务器启动类
│   ├── HttpRequestExtend.java# HTTP 请求扩展
│   ├── DataJsonToRespEncoder.java # JSON 响应编码
│   └── BinaryPart.java       # 二进制上传处理
│
└── forward/                   # 请求转发模块
    ├── path/                  # 路径路由
    │   ├── Path.java         # 路径注解
    │   ├── PathRequest.java  # 路径请求处理
    │   ├── RestfulMatch.java # RESTful 匹配
    │   ├── UrlFormPost.java  # URL Form 注解
    │   └── MultiPartPost.java# MultiPart 注解
    │
    └── openapi/               # OpenAPI 网关
        ├── OpenApiForward.java   # 网关转发器
        ├── ServiceId.java        # 服务ID注解
        └── ServiceRequest.java   # 服务请求处理
```

## 注解说明

| 注解 | 目标 | 说明 |
|-----|------|------|
| `@Path` | 方法 | 声明 HTTP 路由路径，支持 `${param}` 占位符 |
| `@TraceId` | 方法 | 启用链路追踪 |
| `@ServiceId` | 方法 | 声明 OpenAPI 服务标识 |
| `@JsonAttribute` | 参数 | 指定 JSON 属性映射名称 |
| `@UrlFormPost` | 方法 | 处理 URL Form 编码请求 |
| `@MultiPartPost` | 方法 | 处理 MultiPart 文件上传 |

## 请求处理流程

```
客户端请求
    ↓
HTTP 协议解析 (HttpRequestDecoder)
    ↓
OPTIONS 请求处理 (OptionsProcessor)
    ↓
静态资源处理 (ResourceProcessor)
    ↓
自定义前置处理器
    ↓
路由转发处理 (PathRequestForwardProcessor)
    ├── 精确匹配 → 调用业务方法
    ├── RESTful 匹配 → 参数提取 → 调用业务方法
    └── 无匹配 → 404 处理
    ↓
JSON 响应编码 (DataJsonToRespEncoder)
    ↓
HTTP 响应编码 (HttpRespEncoder)
    ↓
SSL 加密 (可选)
    ↓
客户端响应
```

## 参数解析规则

JfireBoot 会根据方法参数类型自动选择解析策略：

- **简单类型** (int, String, boolean 等) - 从请求参数或 JSON 属性中提取
- **复杂对象** - JSON 反序列化
- **HttpRequest** - 注入原始请求对象
- **Pipeline** - 注入处理管道
- **List<BinaryPart>** - 文件上传列表
- **Enum** - 自动转换为枚举类型

## 技术栈

- **Java 21**
- **Jnet** - 高性能网络库 (基于 AIO)
- **Dson** - JSON 序列化
- **jfire-core** - 核心容器框架
- **Log4j 2** - 日志框架

## 构建

```bash
mvn clean package
```

## 许可证

本项目采用 [GNU Affero General Public License v3.0](https://www.gnu.org/licenses/agpl-3.0) 许可证。

## 作者

- jfirer (495561397@qq.com)
