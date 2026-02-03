# JfireBoot

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
[![Java Version](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

基于 **Jnet(AIO/HTTP/WebSocket)** + **Jfire(IOC/AOP)** + **Dson(JSON)** 的轻量级 Web 应用框架：

- 路由：在 Bean 方法上标注 `@Path` 即可暴露为 HTTP 接口（当前仅按“路径”匹配，不区分 GET/POST）。
- 解析：根据 **Content-Type** 自动解析 `application/json` / `application/x-www-form-urlencoded` / `multipart/form-data`，支持 RESTful `${}` 路径变量。
- 返回：默认把返回值 JSON 化写回；如需状态码/响应头/二进制/流式输出，可直接返回/写入 Jnet 的对象（`HttpResponse` / `IoBuffer` / `HttpResponsePart*`）。

> 版本：本文以仓库当前 `pom.xml` 为准（`1.0.1-SNAPSHOT`）。

## 目录

- [快速开始](#快速开始)
- [路由](#路由)
- [参数绑定](#参数绑定)
- [响应与返回值](#响应与返回值)
- [中间件（BeforeProcessor）](#中间件beforeprocessor)
- [静态资源与 404](#静态资源与-404)
- [WebSocket](#websocket)
- [SSE / 流式响应](#sse--流式响应)
- [ServiceId 网关（可选）](#serviceid-网关可选)
- [IOC / 配置 / AOP（Jfire）](#ioc--配置--aopjfire)
- [持久层（Jsql + Starter）](#持久层jsql--starter)
- [构建](#构建)

## 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>cc.jfire</groupId>
    <artifactId>JfireBoot</artifactId>
    <version>1.0.1-SNAPSHOT</version>
</dependency>
```

### 最小可运行示例

1）定义启动配置类（`ApplicationContext.boot(...)` 的启动类 **必须** 标注 `@Configuration`）：

```java
import cc.jfire.jfire.core.prepare.annotation.ComponentScan;
import cc.jfire.jfire.core.prepare.annotation.EnableAutoConfiguration;
import cc.jfire.jfire.core.prepare.annotation.configuration.Configuration;

@Configuration
@EnableAutoConfiguration
@ComponentScan("com.example")
public class AppConfig {}
```

2）定义一个路由 Bean：

```java
import cc.jfire.baseutil.Resource;
import cc.jfire.boot.forward.path.Path;

import java.util.Map;

@Resource
public class HelloController {
    @Path("/hello/${name}")
    public Map<String, Object> hello(String name) {
        return Map.of("ok", true, "name", name);
    }
}
```

3）启动服务器（注意两点：`RuntimeJVM.registerMainClass` 与“主线程保活”）：

```java
import cc.jfire.baseutil.RuntimeJVM;
import cc.jfire.boot.http.HttpAppServer;
import cc.jfire.jfire.core.ApplicationContext;

import java.util.concurrent.locks.LockSupport;

public class Application {
    public static void main(String[] args) {
        RuntimeJVM.registerMainClass(args);

        ApplicationContext context = ApplicationContext.boot(AppConfig.class);

        // webDir：classpath 前缀（见“静态资源与 404”），同时也会启用 404 处理器
        HttpAppServer.start(8080, context, "web");

        // Jnet 默认使用虚拟线程 ChannelGroup，JVM 可能会直接退出；需要保活
        LockSupport.park();
    }
}
```

验证：

```bash
curl 'http://localhost:8080/hello/world'
```

## 路由

### `@Path` 路由规则

- `@Path` 标注在 **方法** 上（`@Path("/user/list")`）。
- 当前路由匹配仅按“路径”做匹配：不会区分 GET/POST/PUT/DELETE。
  - 如需按方法区分：在业务方法里通过 `HttpRequestExtend.getMethod()` 自行判断，或在中间件中做拦截。
- RESTful 路径变量：使用 `${name}`，匹配后会把变量放入参数 Map 中用于后续绑定：
  - 示例：`@Path("/user/${id}")`，访问 `/user/123` 会把 `id=123` 放入参数 Map。

## 参数绑定

### 支持的参数类型（HTTP 路由）

由 `cc.jfire.boot.forward.path.PathRequest` 负责把 `HttpRequestExtend` 绑定为方法入参，目前支持：

**简单类型**

- `int/Integer`、`long/Long`、`boolean/Boolean`、`byte/Byte`、`short/Short`、`float/Float`、`double/Double`
- `String`、`BigDecimal`
- `Enum`（按名称 `Enum.valueOf`）

**特殊类型**

- `cc.jfire.boot.http.HttpRequestExtend`：拿到请求对象（含 method/path/header/body/paramMap/fileParts/pipeline）
- `cc.jfire.jnet.common.api.Pipeline`：用于流式响应（SSE 等）
- `List<cc.jfire.boot.http.FilePart>`：multipart 文件列表

**POJO（限制说明见下文）**

- 当参数是普通 Java 类（且有无参构造）时，可从 JSON 或表单参数填充。

> 注意：`char/Character` 不支持；数组/Map/多层嵌套对象也不是“开箱即用”的绑定目标，建议改用 `HttpRequestExtend` 自行解析。

### 参数来源与覆盖顺序（重要）

JfireBoot 在不同 Content-Type 下，对参数 Map 的写入时机不同；如果同名 key 同时出现在多个位置，最终值以“后写入者”为准：

- QueryString（`?a=1`）最先解析。
- RESTful `${}` 路径变量在路由匹配时写入（可能覆盖同名 query 参数）。
- Body 的解析：
  - `multipart/form-data`：在 `HttpRequestExtend.from(...)` 阶段已解析并写入参数 Map（随后 RESTful 变量还会再覆盖一次）。
  - `application/json` / `application/x-www-form-urlencoded`：在调用业务方法前解析并写入参数 Map（会覆盖同名 query/RESTful）。

实践建议：不要让同名参数同时出现在 path/query/body；必要时自行约定优先级并在业务里处理。

### JSON 绑定规则与限制（重要）

当请求 `Content-Type: application/json` 时有两种策略：

1）**仅一个 POJO 入参（且方法不包含任何“简单类型”入参）**  
框架会把整个 JSON body 直接反序列化为该 POJO（使用 Dson）。

2）**方法入参包含“简单类型”（或混合多个参数）**  
框架会先把 JSON body 反序列化为 `Map` 放进参数 Map，再按字段名从 Map 里取值填充 POJO：

- 仅支持把 POJO 的字段填充为：基本数值/布尔/String/BigDecimal/Enum。
- POJO 字段如果还是对象/char 等类型，会抛异常（不支持）。

如果你希望在“多参数”情况下仍能拿到完整对象，推荐使用 `@JsonAttribute`：

```java
import cc.jfire.baseutil.Resource;
import cc.jfire.boot.forward.openapi.JsonAttribute;
import cc.jfire.boot.forward.path.Path;

@Resource
public class OrderController {
    @Path("/order/create")
    public Object create(@JsonAttribute("order") Order order,
                         @JsonAttribute("operator") String operator) {
        // body: {"order":{...},"operator":"alice"}
        return "ok";
    }
}
```

### 表单与文件上传（不再依赖 `@UrlFormPost/@MultiPartPost`）

`PathRequest` 当前不会读取 `@UrlFormPost` / `@MultiPartPost` 来决定解析方式；解析完全由 **Content-Type** 决定：

- `application/x-www-form-urlencoded`：会把 body 解析成参数 Map（支持 URLDecode）。
- `multipart/form-data`：会解析出：
  - 文本字段：写入参数 Map
  - 文件字段：放入 `List<FilePart>`（`fileName/fieldName/ioBuffer`）

文件上传示例：

```java
import cc.jfire.baseutil.Resource;
import cc.jfire.boot.forward.path.Path;
import cc.jfire.boot.http.FilePart;

import java.util.List;

@Resource
public class UploadController {
    @Path("/upload")
    public Object upload(List<FilePart> files, String remark) {
        // files 中每个 FilePart 都带 fieldName/fileName/ioBuffer
        // 注意：FilePart 的 ioBuffer 会在请求结束时被释放，务必在本方法内消费/落盘
        return files.size();
    }
}
```

## 响应与返回值

写回响应由 `cc.jfire.boot.http.DataJsonToRespEncoder` 完成，规则如下：

- 如果返回值是以下类型之一，会 **原样写出**：
  - `cc.jfire.jnet.extend.http.dto.HttpResponse`
  - `cc.jfire.jnet.extend.http.dto.HttpResponsePart` / `HttpResponsePartHead` 等
  - `cc.jfire.jnet.common.buffer.buffer.IoBuffer`
  - `cc.jfire.jnet.common.util.DataIgnore`
  - `cc.jfire.jnet.extend.websocket.dto.WebSocketFrame`
- 其他任意对象：会被 `Dson.toJson(...)` 序列化为 JSON，并包装成 `HttpResponse` 返回。
- 如果业务方法返回 `null`（或 `void`），框架不会自动写回响应：此时你需要通过 `Pipeline` 自行 `fireWrite(...)`。

自定义状态码示例（返回 `HttpResponse`）：

```java
import cc.jfire.baseutil.Resource;
import cc.jfire.boot.forward.path.Path;
import cc.jfire.jnet.extend.http.dto.HttpResponse;

@Resource
public class AuthController {
    @Path("/deny")
    public HttpResponse deny() {
        HttpResponse resp = new HttpResponse();
        resp.getHead().setStatusCode(401);
        resp.getHead().setReasonPhrase("Unauthorized");
        resp.setBodyText("Unauthorized");
        return resp;
    }
}
```

## 中间件（BeforeProcessor）

`HttpAppServer.start(..., beforeProcessors...)` 支持插入一组前置 `ReadProcessor<Object>`。

注意：这些 beforeProcessor 运行时拿到的通常是 **`HttpRequest`（聚合后的请求）**，而不是 `HttpRequestExtend`（`HttpRequestExtend` 仅在路由转发阶段才会创建）。

示例：简单鉴权 + 日志（遇到拦截时记得 `request.close()` 释放资源）：

```java
import cc.jfire.jnet.common.api.ReadProcessor;
import cc.jfire.jnet.extend.http.dto.HttpRequest;
import cc.jfire.jnet.extend.http.dto.HttpResponse;

ReadProcessor<Object> logging = (data, next) -> {
    if (data instanceof HttpRequest req) {
        System.out.println("HTTP " + req.getHead().getMethod() + " " + req.getHead().getPath());
    }
    next.fireRead(data);
};

ReadProcessor<Object> auth = (data, next) -> {
    if (data instanceof HttpRequest req) {
        String token = req.getHead().getHeaders().get("Authorization");
        if (token == null || token.isBlank()) {
            req.close();
            HttpResponse resp = new HttpResponse();
            resp.getHead().setStatusCode(401);
            resp.getHead().setReasonPhrase("Unauthorized");
            resp.setBodyText("Unauthorized");
            next.pipeline().fireWrite(resp);
            return;
        }
    }
    next.fireRead(data);
};
```

## 静态资源与 404

`HttpAppServer.start(port, context, webDir, ...)` 里的 `webDir` **不是文件系统路径**，而是 **classpath 资源前缀**：

- 例如把静态资源放到 `src/main/resources/web/index.html`
- 启动时传入 `"web"`，请求 `/` 会自动映射为 `/index.html`，最终读取 `web/index.html`

另外：只有配置了 `webDir`，管道中才会加入 `NotFoundUrlProcessor` 来输出 404（并启用 404 结果缓存屏障 `NotFoundBarrier`）。

> 如果你是纯 API 服务，也建议传一个固定前缀（例如 `"web"`）来开启统一的 404 返回。

## WebSocket

启用 WebSocket：在启动时传入 `webSocketProcessor`（非 null 即可）。

注意事项：

- 当前 `WebSocketUpgradeDecoder` 会自动完成 101 握手并进入 WebSocket 模式，但握手请求不会向后传递，因此应用层无法基于“升级路径”做路由或鉴权（如需请自行扩展 Jnet 解码器）。
- `WebSocketFrame` 没有 `getPayloadAsText()/setPayloadText()` 这类方法，需要自己编码/解码 `IoBuffer`。

示例：Echo（处理 TEXT / PING / CLOSE）：

```java
import cc.jfire.jnet.common.api.ReadProcessor;
import cc.jfire.jnet.extend.websocket.dto.WebSocketFrame;

import java.nio.charset.StandardCharsets;

ReadProcessor<Object> ws = (data, next) -> {
    if (!(data instanceof WebSocketFrame frame)) {
        next.fireRead(data);
        return;
    }

    try {
        if (frame.getOpcode() == WebSocketFrame.OPCODE_TEXT) {
            String msg = StandardCharsets.UTF_8.decode(frame.getPayload().readableByteBuffer()).toString();
            byte[] bytes = ("Echo: " + msg).getBytes(StandardCharsets.UTF_8);

            var payload = next.pipeline().allocator().allocate(bytes.length);
            payload.put(bytes);

            WebSocketFrame resp = new WebSocketFrame();
            resp.setOpcode(WebSocketFrame.OPCODE_TEXT);
            resp.setPayload(payload);

            next.pipeline().fireWrite(resp);
            resp.free();
        } else if (frame.getOpcode() == WebSocketFrame.OPCODE_PING) {
            WebSocketFrame pong = WebSocketFrame.createPong(frame.getPayload());
            frame.setPayload(null); // 交给 pong
            next.pipeline().fireWrite(pong);
            pong.free();
        } else if (frame.getOpcode() == WebSocketFrame.OPCODE_CLOSE) {
            next.pipeline().fireWrite(WebSocketFrame.createClose(1000, "bye"));
        }
    } finally {
        frame.free(); // 释放入站 payload
    }
};
```

## SSE / 流式响应

流式输出建议使用 `Pipeline` 参数，并手动发送 `HttpResponsePartHead` + 连续的 `IoBuffer`：

```java
import cc.jfire.baseutil.Resource;
import cc.jfire.boot.forward.path.Path;
import cc.jfire.jnet.common.api.Pipeline;
import cc.jfire.jnet.common.buffer.buffer.IoBuffer;
import cc.jfire.jnet.extend.http.dto.HttpResponsePartHead;

import java.nio.charset.StandardCharsets;

@Resource
public class SseController {
    @Path("/sse")
    public void sse(Pipeline pipeline) {
        Thread.startVirtualThread(() -> {
            HttpResponsePartHead head = new HttpResponsePartHead();
            head.setVersion("HTTP/1.1");
            head.setStatusCode(200);
            head.setReasonPhrase("OK");
            head.addHeader("Content-Type", "text/event-stream");
            head.addHeader("Cache-Control", "no-cache");
            head.addHeader("Connection", "keep-alive");
            pipeline.fireWrite(head);

            for (int i = 0; i < 5; i++) {
                if (!pipeline.isOpen()) {
                    return;
                }
                String data = "data: tick-" + i + "\n\n";
                IoBuffer buf = pipeline.allocator().allocate(data.getBytes(StandardCharsets.UTF_8).length);
                buf.put(data.getBytes(StandardCharsets.UTF_8));
                pipeline.fireWrite(buf);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
        });
    }
}
```

## ServiceId 网关（可选）

`cc.jfire.boot.forward.openapi` 提供了一套“按 serviceId 路由到方法”的能力（不在 `HttpAppServer` 中默认接入，需要你自己写一条 `@Path` 入口）。

核心点：

- 在业务方法上标注 `@ServiceId("xxx")`
- 提供一个 `OpenApiForward` Bean（它没有无参构造，需要你用 `@Bean` 显式创建）
- 在统一入口里：解析 serviceId → 找到 `ServiceRequest` → `invoke(...)`

示例（把 body 当作参数对象，dataExtracter 直接返回原 body）：

```java
import cc.jfire.baseutil.Resource;
import cc.jfire.boot.forward.openapi.OpenApiForward;
import cc.jfire.boot.forward.openapi.ServiceRequest;
import cc.jfire.boot.forward.path.Path;
import cc.jfire.boot.http.HttpRequestExtend;
import cc.jfire.jnet.extend.http.dto.HttpResponse;
import cc.jfire.jfire.core.prepare.annotation.configuration.Bean;
import cc.jfire.jfire.core.prepare.annotation.configuration.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenApiForward openApiForward() {
        return new OpenApiForward(HttpRequestExtend::getUtf8StrBody);
    }
}

@Resource
public class OpenApiGateway {
    @Resource
    private OpenApiForward openApiForward;

    @Path("/openapi")
    public Object openapi(HttpRequestExtend req) throws Exception {
        ServiceRequest service = openApiForward.route(req);
        if (service == null) {
            HttpResponse resp = new HttpResponse();
            resp.getHead().setStatusCode(404);
            resp.getHead().setReasonPhrase("Not Found");
            resp.setBodyText("serviceId not found");
            return resp;
        }
        return service.invoke(req);
    }
}
```

请求示例：

```json
{ "serviceId": "user.search", "name": "alice", "status": "ACTIVE" }
```

## IOC / 配置 / AOP（Jfire）

JfireBoot 使用 Jfire 作为 IOC/AOP 容器。以下仅列出与本框架结合最常用的部分（更完整内容建议直接阅读 Jfire 项目 README）。

### 启动类与组件扫描

- `ApplicationContext.boot(bootClass)` 要求 `bootClass` 标注 `cc.jfire.jfire.core.prepare.annotation.configuration.Configuration`。
- `@ComponentScan("com.example")` 会扫描指定包下标注 `cc.jfire.baseutil.Resource` 的类并注册为 Bean。
- `@EnableAutoConfiguration` 会读取 classpath 下 `META-INF/autoconfig/` 目录中的配置类（文件名即类名），并自动 `register(...)`。

### 配置文件读取（`@PropertyPath`）

Jfire 读取 yml/yaml 需要显式声明路径前缀（不支持裸 `application.yml`）：

```java
import cc.jfire.jfire.core.prepare.annotation.PropertyPath;
import cc.jfire.jfire.core.prepare.annotation.configuration.Configuration;

@Configuration
@PropertyPath("classpath:application.yml")
public class AppConfig {}
```

也支持从 jar 同级目录读取：

```java
@PropertyPath("file:conf/application.yml")
```

### 条件注解（签名修正）

```java
import cc.jfire.jfire.core.prepare.annotation.condition.provide.ConditionOnClass;
import cc.jfire.jfire.core.prepare.annotation.condition.provide.ConditionOnMissBeanType;
import cc.jfire.jfire.core.prepare.annotation.condition.provide.ConditionOnProperty;
import cc.jfire.jfire.core.prepare.annotation.configuration.Bean;
import cc.jfire.jfire.core.prepare.annotation.configuration.Configuration;

@Configuration
public class DataSourceConfig {
    @Bean
    @ConditionOnProperty("db.type=mysql")
    public Object onlyWhenMysql() { return new Object(); }

    @Bean
    @ConditionOnClass({com.mysql.cj.jdbc.Driver.class})
    public Object onlyWhenDriverExists() { return new Object(); }

    @Bean
    @ConditionOnMissBeanType(javax.sql.DataSource.class)
    public Object onlyWhenNoDataSource() { return new Object(); }
}
```

### 注册外部单例 Bean

如果你有一个已经创建好的对象实例（例如第三方库创建的对象、手动 new 的对象等），可以通过 `registerBeanRegisterInfo` 方法将其注册到容器中：

```java
import cc.jfire.jfire.core.ApplicationContext;
import cc.jfire.jfire.core.bean.impl.register.OutterBeanRegisterInfo;

ApplicationContext context = ApplicationContext.boot(AppConfig.class);

// 注册外部单例对象到容器
MyService externalService = new MyService();
context.registerBeanRegisterInfo(new OutterBeanRegisterInfo(externalService, "myService"));

// 之后可以通过容器获取
MyService service = context.getBean("myService");
MyService serviceByType = context.getBean(MyService.class);
```

这在以下场景非常有用：

- 集成第三方库创建的对象（如连接池、客户端实例等）
- 需要在容器启动前手动创建并配置的对象
- 将非 Jfire 管理的对象纳入 IOC 容器统一管理

### AOP 方法匹配表达式（签名修正）

Jfire 的 AOP 方法匹配表达式要求包含括号：

- `"save*(*)"`：方法名匹配 `save*`，参数任意
- `"query*()"`：无参方法
- `"update*(String,int)"`：参数类型精确匹配（支持 simpleName 或全限定名）

示例：

```java
import cc.jfire.jfire.core.aop.ProceedPoint;
import cc.jfire.jfire.core.aop.notated.After;
import cc.jfire.jfire.core.aop.notated.Before;
import cc.jfire.jfire.core.aop.notated.EnhanceClass;

@EnhanceClass(value = "com.example.service.*", order = 100)
public class LoggingAspect {
    @Before("save*(*)")
    public void before(ProceedPoint p) {
        System.out.println(p.getMethod().methodName());
    }

    @After("*(*)")
    public void after(ProceedPoint p) {}
}
```

## 持久层（Jsql + Starter）

JfireBoot 依赖了 `cc.jfire:jfire-jsql-starter`，配合 `@EnableAutoConfiguration` 可自动装配 Jsql 相关 Bean。

### 必备：提供 `DataSource` Bean

```java
import cc.jfire.jfire.core.prepare.annotation.EnableAutoConfiguration;
import cc.jfire.jfire.core.prepare.annotation.configuration.Bean;
import cc.jfire.jfire.core.prepare.annotation.configuration.Configuration;

import javax.sql.DataSource;

@Configuration
@EnableAutoConfiguration
public class AppConfig {
    @Bean
    public DataSource dataSource() {
        // 自行选择实现（Hikari/DBCP/驱动自带 DataSource 等）
        throw new UnsupportedOperationException("provide your DataSource");
    }
}
```

### 自动装配的 Bean（以 Starter 代码为准）

- `sessionFactory`：`cc.jfire.jsql.SessionFactory`
- `transactionManager`：`cc.jfire.jfire.core.aop.impl.support.transaction.JdbcTransactionManager`
- `sqlSession`：`cc.jfire.jsql.session.SqlSession`（实际实现为 `cc.jfire.starter.jsql.SqlSessionProxy`，且标注了 `@Primary`）
- `readOnlySession`：`cc.jfire.starter.jsql.ReadOnlySession`（只读实现）
- `mapperFactory`：`cc.jfire.starter.jsql.MapperFactory`

### 事务要求（重要）

`SqlSessionProxy` 会检查 `JdbcTransactionManager.CONTEXT`：未开启事务会直接抛 `RuntimeException("请先开启事务")`。  
因此凡是使用 `SqlSession/Mapper` 访问数据库的方法，都必须标注 `@Transactional`（来自 Jfire AOP）。

### Mapper：使用 `@AutoMapper`

```java
import cc.jfire.starter.jsql.AutoMapper;
import cc.jfire.jsql.annotation.Sql;
import cc.jfire.jsql.mapper.Repository;

import java.util.List;

@AutoMapper
public interface UserMapper extends Repository<User> {
    @Sql(sql = "SELECT * FROM users WHERE status = ${status}", paramNames = "status")
    List<User> findByStatus(String status);
}
```

## 构建

```bash
mvn clean package
```

## 许可证

本项目采用 [GNU Affero General Public License v3.0](https://www.gnu.org/licenses/agpl-3.0) 许可证。

## 作者

- jfirer (495561397@qq.com)
