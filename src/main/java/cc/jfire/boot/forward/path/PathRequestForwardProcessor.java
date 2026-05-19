package cc.jfire.boot.forward.path;

import cc.jfire.baseutil.STR;
import cc.jfire.boot.common.TraceId;
import cc.jfire.boot.http.HttpRequestExtend;
import cc.jfire.boot.http.HttpRequestParseException;
import cc.jfire.jnet.common.api.ReadProcessor;
import cc.jfire.jnet.common.api.ReadProcessorNode;
import cc.jfire.jnet.common.buffer.buffer.IoBuffer;
import cc.jfire.jnet.extend.http.dto.HttpRequest;
import cc.jfire.jnet.extend.http.dto.HttpResponse;
import cc.jfire.jnet.extend.websocket.dto.WebSocketFrame;
import cc.jfire.jnet.extend.websocket.util.WebSocketHandshakeUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class PathRequestForwardProcessor implements ReadProcessor<Object>
{
    private Map<String, PathRequest[]> specificRequestMap;
    private PathRequest[]              restfulRequests;

    public PathRequestForwardProcessor(List<PathRequest> pathRequests)
    {
        specificRequestMap = new HashMap<>();
        for (PathRequest pathRequest : pathRequests)
        {
            if (pathRequest.getRestfulMatch() == null)
            {
                if (specificRequestMap.containsKey(pathRequest.getRouteKey()))
                {
                    PathRequest[] old = specificRequestMap.get(pathRequest.getRouteKey());
                    checkValid(old, pathRequest);
                    PathRequest[] newOne = Arrays.copyOf(old, old.length + 1);
                    newOne[newOne.length - 1] = pathRequest;
                    specificRequestMap.put(pathRequest.getRouteKey(), newOne);
                }
                else
                {
                    specificRequestMap.put(pathRequest.getRouteKey(), new PathRequest[]{pathRequest});
                }
            }
        }
        restfulRequests = pathRequests.stream().filter(request -> request.getRestfulMatch() != null).toArray(PathRequest[]::new);
    }

    /**
     * 确认是否合法。
     * 不允许添加的http method 重复.
     *
     *
     * @param old
     * @param pathRequest
     * @return
     */
    private void checkValid(PathRequest[] old, PathRequest pathRequest)
    {
        for (PathRequest each : old)
        {
            for (HttpMethod httpMethod : pathRequest.getHttpMethods())
            {
                if (each.matchesMethod(httpMethod.name()))
                {
                    throw new IllegalArgumentException(STR.format("方法:{}.{}的路径、http 方法和方法:{}.{}有重复", each.getMethod().getDeclaringClass().getName(), each.getMethod().getName(), pathRequest.getMethod().getDeclaringClass().getName(), pathRequest.getMethod().getName()));
                }
            }
        }
    }

    @TraceId
    @Override
    public void read(Object data, ReadProcessorNode next)
    {
        if (data instanceof HttpRequest httpRequest)
        {
            handleHttpRequest(httpRequest, next);
        }
        else if (data instanceof WebSocketFrame webSocketFrame)
        {
            WsConnection connection = (WsConnection) next.pipeline().getPersistenceStore(PathRequest.WS_KEY);
            connection.accept(webSocketFrame);
        }
        else
        {
            next.fireRead(data);
        }
    }

    /**
     * AI 生成：在路由转发前构建扩展请求，并将解析错误映射成明确的 HTTP 错误响应。
     */
    private void handleHttpRequest(HttpRequest data, ReadProcessorNode next)
    {
        String rawMethod = data.getHead() == null ? null : data.getHead().getMethod();
        String rawPath = data.getHead() == null ? null : data.getHead().getPath();
        String rawContentType = findHeader(data, "Content-Type");
        String rawUserAgent = findHeader(data, "User-Agent");
        long   rawContentLength = data.getHead() == null ? -1 : data.getHead().getContentLength();
        String path = rawPath == null ? "" : rawPath;
        try (HttpRequestExtend requestExtend = HttpRequestExtend.from(data, next.pipeline()))
        {
            path = requestExtend.getPath();
            String requestMethod = requestExtend.getMethod();
            PathRequest[] pathRequests = specificRequestMap.get(path);
            PathRequest   selected     = null;
            if (pathRequests == null)
            {
                Map<String, Object> paramMap         = requestExtend.getNotNullParamMap();
                Map<String, Object> originalParamMap = new HashMap<>(paramMap);
                for (PathRequest restfulRequest : restfulRequests)
                {
                    paramMap.clear();
                    paramMap.putAll(originalParamMap);
                    if (restfulRequest.getRestfulMatch().match(path, paramMap) && restfulRequest.matchesMethod(requestMethod))
                    {
                        selected = restfulRequest;
                        break;
                    }
                }
                if (selected == null)
                {
                    next.fireRead(requestExtend);
                    return;
                }
            }
            else
            {
                for (PathRequest pathRequest : pathRequests)
                {
                    if (pathRequest.matchesMethod(requestMethod))
                    {
                        selected = pathRequest;
                        break;
                    }
                }
                if (selected == null)
                {
                    HttpResponse response = new HttpResponse();
                    response.getHead().setStatusCode(405);
                    response.getHead().setReasonPhrase("Method Not Allowed");
                    response.setBodyText("Method Not Allowed.for request:" + path, next.pipeline().allocator());
                    next.pipeline().fireWrite(response);
                    return;
                }
            }
            if (selected.isWs())
            {
                IoBuffer buffer = WebSocketHandshakeUtil.buildUpgradeResponse(getSecWebSocketKey(requestExtend), next.pipeline().allocator());
                next.pipeline().fireWrite(buffer);
                selected.invoke(requestExtend);
            }
            else
            {
                Object value = selected.invoke(requestExtend);
                if (value != null)
                {
                    next.pipeline().fireWrite(value);
                }
            }
        }
        catch (HttpRequestParseException e)
        {
            log.warn("请求格式错误, method:{}, path:{}, Content-Type:{}, Content-Length:{}, User-Agent:{}", rawMethod, path, rawContentType, rawContentLength, rawUserAgent, e);
            writeError(next, e.getStatusCode(), e.getReasonPhrase(), e.getClientMessage());
        }
        catch (Throwable e)
        {
            log.error("请求出现异常, method:{}, path:{}, Content-Type:{}, Content-Length:{}, User-Agent:{}", rawMethod, path, rawContentType, rawContentLength, rawUserAgent, e);
            writeError(next, 500, "Internal Server Error", "Internal Server Error");
        }
    }

    /**
     * AI 生成：统一写出错误响应，避免异常路径返回默认 200。
     */
    private void writeError(ReadProcessorNode next, int statusCode, String reasonPhrase, String message)
    {
        HttpResponse response = new HttpResponse();
        response.getHead().setStatusCode(statusCode);
        response.getHead().setReasonPhrase(reasonPhrase);
        response.setBodyText(message, next.pipeline().allocator());
        next.pipeline().fireWrite(response);
    }

    /**
     * AI 生成：大小写不敏感读取原始请求头，用于错误日志保留请求上下文。
     */
    private String findHeader(HttpRequest request, String name)
    {
        if (request == null || request.getHead() == null || request.getHead().getHeaders() == null)
        {
            return null;
        }
        for (Map.Entry<String, String> entry : request.getHead().getHeaders().entrySet())
        {
            if (entry.getKey().equalsIgnoreCase(name))
            {
                return entry.getValue();
            }
        }
        return null;
    }

    private String getSecWebSocketKey(HttpRequestExtend extend)
    {
        for (Map.Entry<String, String> each : extend.getHeaders().entrySet())
        {
            if (each.getKey().equalsIgnoreCase("Sec-WebSocket-Key"))
            {
                return each.getValue();
            }
        }
        return null;
    }
}
