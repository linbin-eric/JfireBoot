# JfireBoot 高级特性

本文档介绍 JfireBoot 的高级特性，包括 WebSocket、SSE、文件上传、中间件、ServiceId 网关等。

## 目录

- [WebSocket](#websocket)
- [SSE 流式响应](#sse-流式响应)
- [文件上传](#文件上传)
- [中间件](#中间件)
- [ServiceId 网关](#serviceid-网关)
- [静态资源](#静态资源)
- [HTTPS/SSL](#httpsssl)

## WebSocket

### 启用 WebSocket

在启动时传入 `webSocketProcessor`：

```java
import cc.jfire.jnet.common.api.ReadProcessor;
import cc.jfire.jnet.extend.websocket.dto.WebSocketFrame;

ReadProcessor<Object> wsProcessor = (data, next) -> {
    if (!(data instanceof WebSocketFrame frame)) {
        next.fireRead(data);
        return;
    }

    // 处理 WebSocket 消息
    handleWebSocketFrame(frame, next);
};

HttpAppServer.start(8080, context, "web", null, wsProcessor);
```

### Echo 服务器示例

```java
import cc.jfire.jnet.common.api.ReadProcessor;
import cc.jfire.jnet.extend.websocket.dto.WebSocketFrame;
import java.nio.charset.StandardCharsets;

ReadProcessor<Object> wsProcessor = (data, next) -> {
    if (!(data instanceof WebSocketFrame frame)) {
        next.fireRead(data);
        return;
    }

    try {
        if (frame.getOpcode() == WebSocketFrame.OPCODE_TEXT) {
            // 读取文本消息
            String msg = StandardCharsets.UTF_8
                .decode(frame.getPayload().readableByteBuffer())
                .toString();

            // 构造响应
            byte[] bytes = ("Echo: " + msg).getBytes(StandardCharsets.UTF_8);
            var payload = next.pipeline().allocator().allocate(bytes.length);
            payload.put(bytes);

            WebSocketFrame resp = new WebSocketFrame();
            resp.setOpcode(WebSocketFrame.OPCODE_TEXT);
            resp.setPayload(payload);

            next.pipeline().fireWrite(resp);
            resp.free();

        } else if (frame.getOpcode() == WebSocketFrame.OPCODE_PING) {
            // 响应 PING
            WebSocketFrame pong = WebSocketFrame.createPong(frame.getPayload());
            frame.setPayload(null);
            next.pipeline().fireWrite(pong);
            pong.free();

        } else if (frame.getOpcode() == WebSocketFrame.OPCODE_CLOSE) {
            // 关闭连接
            next.pipeline().fireWrite(WebSocketFrame.createClose(1000, "bye"));
        }
    } finally {
        frame.free();  // 释放资源
    }
};
```

### 广播消息

```java
import java.util.concurrent.ConcurrentHashMap;
import cc.jfire.jnet.common.api.Pipeline;

public class WebSocketBroadcaster {
    private static final ConcurrentHashMap<String, Pipeline> clients = new ConcurrentHashMap<>();

    public static void addClient(String clientId, Pipeline pipeline) {
        clients.put(clientId, pipeline);
    }

    public static void removeClient(String clientId) {
        clients.remove(clientId);
    }

    public static void broadcast(String message) {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);

        clients.values().forEach(pipeline -> {
            if (pipeline.isOpen()) {
                var payload = pipeline.allocator().allocate(bytes.length);
                payload.put(bytes);

                WebSocketFrame frame = new WebSocketFrame();
                frame.setOpcode(WebSocketFrame.OPCODE_TEXT);
                frame.setPayload(payload);

                pipeline.fireWrite(frame);
                frame.free();
            }
        });
    }
}
```

### 客户端示例

```javascript
const ws = new WebSocket('ws://localhost:8080/ws');

ws.onopen = () => {
    console.log('Connected');
    ws.send('Hello Server');
};

ws.onmessage = (event) => {
    console.log('Received:', event.data);
};

ws.onerror = (error) => {
    console.error('Error:', error);
};

ws.onclose = () => {
    console.log('Disconnected');
};
```

## SSE 流式响应

### 基本 SSE 示例

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
            // 发送 SSE 响应头
            HttpResponsePartHead head = new HttpResponsePartHead();
            head.setVersion("HTTP/1.1");
            head.setStatusCode(200);
            head.setReasonPhrase("OK");
            head.addHeader("Content-Type", "text/event-stream");
            head.addHeader("Cache-Control", "no-cache");
            head.addHeader("Connection", "keep-alive");
            pipeline.fireWrite(head);

            // 发送事件流
            for (int i = 0; i < 10; i++) {
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

### 带事件 ID 和类型

```java
@Path("/sse/events")
public void sseWithEvents(Pipeline pipeline) {
    Thread.startVirtualThread(() -> {
        HttpResponsePartHead head = new HttpResponsePartHead();
        head.setVersion("HTTP/1.1");
        head.setStatusCode(200);
        head.setReasonPhrase("OK");
        head.addHeader("Content-Type", "text/event-stream");
        head.addHeader("Cache-Control", "no-cache");
        head.addHeader("Connection", "keep-alive");
        pipeline.fireWrite(head);

        int eventId = 0;
        while (pipeline.isOpen()) {
            // 构造 SSE 消息
            String message = String.format(
                "id: %d\nevent: update\ndata: {\"time\": %d}\n\n",
                eventId++,
                System.currentTimeMillis()
            );

            IoBuffer buf = pipeline.allocator().allocate(message.getBytes(StandardCharsets.UTF_8).length);
            buf.put(message.getBytes(StandardCharsets.UTF_8));
            pipeline.fireWrite(buf);

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
    });
}
```

### 客户端示例

```javascript
const eventSource = new EventSource('/sse');

eventSource.onmessage = (event) => {
    console.log('Received:', event.data);
};

eventSource.onerror = (error) => {
    console.error('Error:', error);
    eventSource.close();
};

// 监听特定事件类型
eventSource.addEventListener('update', (event) => {
    console.log('Update:', event.data);
});
```

## 文件上传

### 单文件上传

```java
import cc.jfire.baseutil.Resource;
import cc.jfire.boot.forward.path.Path;
import cc.jfire.boot.http.FilePart;
import java.io.FileOutputStream;
import java.util.List;

@Resource
public class UploadController {

    @Path("/upload")
    public Object upload(List<FilePart> files, String remark) {
        if (files == null || files.isEmpty()) {
            return Map.of("ok", false, "message", "No file uploaded");
        }

        FilePart file = files.get(0);

        try {
            // 保存文件
            String savePath = "/tmp/" + file.getFileName();
            try (FileOutputStream fos = new FileOutputStream(savePath)) {
                fos.write(file.getIoBuffer().readableBytes());
            }

            return Map.of(
                "ok", true,
                "fileName", file.getFileName(),
                "fieldName", file.getFieldName(),
                "size", file.getIoBuffer().readableLength(),
                "remark", remark
            );
        } catch (Exception e) {
            return Map.of("ok", false, "message", e.getMessage());
        }
    }
}
```

### 多文件上传

```java
@Path("/upload/multiple")
public Object uploadMultiple(List<FilePart> files) {
    if (files == null || files.isEmpty()) {
        return Map.of("ok", false, "message", "No files uploaded");
    }

    List<Map<String, Object>> results = new ArrayList<>();

    for (FilePart file : files) {
        try {
            String savePath = "/tmp/" + file.getFileName();
            try (FileOutputStream fos = new FileOutputStream(savePath)) {
                fos.write(file.getIoBuffer().readableBytes());
            }

            results.add(Map.of(
                "fileName", file.getFileName(),
                "size", file.getIoBuffer().readableLength(),
                "success", true
            ));
        } catch (Exception e) {
            results.add(Map.of(
                "fileName", file.getFileName(),
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    return Map.of("ok", true, "files", results);
}
```

### 文件上传表单示例

```html
<form action="/upload" method="post" enctype="multipart/form-data">
    <input type="file" name="file" />
    <input type="text" name="remark" placeholder="备注" />
    <button type="submit">上传</button>
</form>
```

### 使用 JavaScript 上传

```javascript
const formData = new FormData();
formData.append('file', fileInput.files[0]);
formData.append('remark', 'My file');

fetch('/upload', {
    method: 'POST',
    body: formData
})
.then(response => response.json())
.then(data => console.log(data));
```

## 中间件

### 日志中间件

```java
import cc.jfire.jnet.common.api.ReadProcessor;
import cc.jfire.jnet.extend.http.dto.HttpRequest;

ReadProcessor<Object> loggingMiddleware = (data, next) -> {
    if (data instanceof HttpRequest req) {
        System.out.println(String.format(
            "[%s] %s %s",
            LocalDateTime.now(),
            req.getHead().getMethod(),
            req.getHead().getPath()
        ));
    }
    next.fireRead(data);
};
```

### 鉴权中间件

```java
ReadProcessor<Object> authMiddleware = (data, next) -> {
    if (data instanceof HttpRequest req) {
        String token = req.getHead().getHeaders().get("Authorization");

        if (token == null || !isValidToken(token)) {
            req.close();  // 释放资源

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

private boolean isValidToken(String token) {
    // 验证 token 逻辑
    return token.startsWith("Bearer ");
}
```

### CORS 中间件

```java
ReadProcessor<Object> corsMiddleware = (data, next) -> {
    if (data instanceof HttpRequest req) {
        // 处理 OPTIONS 预检请求
        if ("OPTIONS".equals(req.getHead().getMethod())) {
            req.close();

            HttpResponse resp = new HttpResponse();
            resp.getHead().setStatusCode(200);
            resp.getHead().addHeader("Access-Control-Allow-Origin", "*");
            resp.getHead().addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            resp.getHead().addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
            resp.setBodyText("");

            next.pipeline().fireWrite(resp);
            return;
        }
    }
    next.fireRead(data);
};
```

### 注册中间件

```java
HttpAppServer.start(
    8080,
    context,
    "web",
    new ReadProcessor[]{loggingMiddleware, authMiddleware, corsMiddleware},
    null
);
```

## ServiceId 网关

### 配置 OpenApiForward

```java
import cc.jfire.boot.forward.openapi.OpenApiForward;
import cc.jfire.boot.http.HttpRequestExtend;
import cc.jfire.jfire.core.prepare.annotation.configuration.Bean;
import cc.jfire.jfire.core.prepare.annotation.configuration.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenApiForward openApiForward() {
        // dataExtracter: 从请求中提取参数对象
        return new OpenApiForward(HttpRequestExtend::getUtf8StrBody);
    }
}
```

### 定义服务

```java
import cc.jfire.baseutil.Resource;
import cc.jfire.boot.forward.openapi.ServiceId;

@Resource
public class UserOpenApiService {

    @ServiceId("user.search")
    public Object search(String name, String status) {
        // 业务逻辑
        return Map.of("users", List.of());
    }

    @ServiceId("user.create")
    public Object create(User user) {
        // 业务逻辑
        return Map.of("ok", true, "userId", user.getId());
    }
}
```

### 网关入口

```java
import cc.jfire.baseutil.Resource;
import cc.jfire.boot.forward.openapi.OpenApiForward;
import cc.jfire.boot.forward.openapi.ServiceRequest;
import cc.jfire.boot.forward.path.Path;
import cc.jfire.boot.http.HttpRequestExtend;
import cc.jfire.jnet.extend.http.dto.HttpResponse;

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

### 请求示例

```bash
curl -X POST http://localhost:8080/openapi \
  -H "Content-Type: application/json" \
  -d '{"serviceId": "user.search", "name": "alice", "status": "ACTIVE"}'
```

## 静态资源

### 配置静态资源目录

```java
// webDir 是 classpath 资源前缀
HttpAppServer.start(8080, context, "web");
```

### 目录结构

```
src/main/resources/
└── web/
    ├── index.html
    ├── css/
    │   └── style.css
    ├── js/
    │   └── app.js
    └── images/
        └── logo.png
```

### 访问路径

- `http://localhost:8080/` → `web/index.html`
- `http://localhost:8080/css/style.css` → `web/css/style.css`
- `http://localhost:8080/images/logo.png` → `web/images/logo.png`

### 自定义 404 页面

```html
<!-- src/main/resources/web/404.html -->
<!DOCTYPE html>
<html>
<head>
    <title>404 Not Found</title>
</head>
<body>
    <h1>404 - Page Not Found</h1>
    <p>The requested page does not exist.</p>
</body>
</html>
```

## HTTPS/SSL

### 生成自签名证书

```bash
keytool -genkeypair -alias myserver -keyalg RSA -keysize 2048 \
  -validity 365 -keystore keystore.jks -storepass 123456 \
  -dname "CN=localhost, OU=Dev, O=MyCompany, L=City, ST=State, C=CN"
```

### 配置 SSL

```java
import javax.net.ssl.*;
import java.io.InputStream;
import java.security.KeyStore;

// 1. 加载密钥库
KeyStore keyStore = KeyStore.getInstance("JKS");
try (InputStream is = getClass().getResourceAsStream("/keystore.jks")) {
    keyStore.load(is, "123456".toCharArray());
}

// 2. 初始化 KeyManagerFactory
KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
kmf.init(keyStore, "123456".toCharArray());

// 3. 初始化 TrustManagerFactory
TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
tmf.init(keyStore);

// 4. 初始化 SSLContext
SSLContext sslContext = SSLContext.getInstance("TLS");
sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

// 5. 创建 SSLEngine
SSLEngine sslEngine = sslContext.createSSLEngine();
sslEngine.setUseClientMode(false);
sslEngine.setNeedClientAuth(false);
sslEngine.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});

// 6. 配置服务器（需要手动配置管道）
ChannelConfig channelConfig = new ChannelConfig().setPort(8443);
AioServer aioServer = AioServer.newAioServer(channelConfig, pipeline -> {
    SSLDecoder sslDecoder = new SSLDecoder(sslEngine);
    SSLEncoder sslEncoder = new SSLEncoder(sslEngine);

    try {
        sslEngine.beginHandshake();
    } catch (SSLException e) {
        throw new RuntimeException(e);
    }

    pipeline.addReadProcessor(sslDecoder);
    pipeline.addReadProcessor(new HttpRequestPartDecoder());
    pipeline.addReadProcessor(new HttpRequestAggregator());
    // ... 其他处理器

    pipeline.addWriteProcessor(new HttpRespEncoder(pipeline.allocator()));
    pipeline.addWriteProcessor(sslEncoder);
});

aioServer.start();
```

### 访问 HTTPS

```bash
curl -k https://localhost:8443/hello/world
```
