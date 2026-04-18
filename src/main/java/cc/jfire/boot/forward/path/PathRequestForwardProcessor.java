package cc.jfire.boot.forward.path;

import cc.jfire.boot.common.TraceId;
import cc.jfire.boot.http.HttpRequestExtend;
import cc.jfire.jnet.common.api.ReadProcessor;
import cc.jfire.jnet.common.api.ReadProcessorNode;
import cc.jfire.jnet.common.buffer.buffer.IoBuffer;
import cc.jfire.jnet.extend.http.dto.HttpRequest;
import cc.jfire.jnet.extend.http.dto.HttpResponse;
import cc.jfire.jnet.extend.websocket.dto.WebSocketFrame;
import cc.jfire.jnet.extend.websocket.util.WebSocketHandshakeUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class PathRequestForwardProcessor implements ReadProcessor<Object>
{
    private Map<String, PathRequest> specificRequestMap;
    private PathRequest[]            restfulRequests;

    public PathRequestForwardProcessor(List<PathRequest> pathRequests)
    {
        specificRequestMap = new HashMap<>();
        for (PathRequest pathRequest : pathRequests)
        {
            if (pathRequest.getRestfulMatch() == null)
            {
                specificRequestMap.put(pathRequest.getRouteKey(), pathRequest);
            }
        }
        restfulRequests = pathRequests.stream().filter(request -> request.getRestfulMatch() != null).toArray(PathRequest[]::new);
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

    private void handleHttpRequest(HttpRequest data, ReadProcessorNode next)
    {
        String path = "";
        try (HttpRequestExtend requestExtend = HttpRequestExtend.from(data, next.pipeline()))
        {
            path = requestExtend.getPath();
            String requestMethod = requestExtend.getMethod();
            // 尝试精确匹配：路径 + HTTP 方法（非 RESTful 路径的 ALL 已在注册时展开）
            PathRequest pathRequest = specificRequestMap.get(path);
            if (pathRequest == null)
            {
                // 精确路径未匹配，尝试 RESTful 路由
                Map<String, Object> paramMap         = requestExtend.getNotNullParamMap();
                Map<String, Object> originalParamMap = new HashMap<>(paramMap);
                for (PathRequest restfulRequest : restfulRequests)
                {
                    paramMap.clear();
                    paramMap.putAll(originalParamMap);
                    if (restfulRequest.getRestfulMatch().match(path, paramMap))
                    {
                        pathRequest = restfulRequest;
                    }
                }
            }
            if (pathRequest != null)
            {
                if (pathRequest.matchesMethod(requestMethod))
                {
                    if (pathRequest.isWs())
                    {
                        //此时首先自动回复 101响应
                        IoBuffer buffer = WebSocketHandshakeUtil.buildUpgradeResponse(getSecWebSocketKey(requestExtend), next.pipeline().allocator());
                        next.pipeline().fireWrite(buffer);
                        //执行的时候会将 wsconnection绑定到当前的 pipeline
                        pathRequest.invoke(requestExtend);
                    }
                    else
                    {
                        Object value = pathRequest.invoke(requestExtend);
                        if (value != null)
                        {
                            next.pipeline().fireWrite(value);
                        }
                    }
                }
                else
                {
                    HttpResponse response = new HttpResponse();
                    response.getHead().setStatusCode(405);
                    response.getHead().setReasonPhrase("Method Not Allowed");
                    response.setBodyText("Method Not Allowed.for request:" + path, next.pipeline().allocator());
                    next.pipeline().fireWrite(response);
                }
            }
            else
            {
                // 路径不存在，传递给下一个处理器（404）
                next.fireRead(requestExtend);
            }
        }
        catch (Throwable e)
        {
            log.error("请求出现异常,当前请求路径:{}", path, e);
            HttpResponse response = new HttpResponse();
            response.setBodyText("error:" + e.toString(), next.pipeline().allocator());
            next.pipeline().fireWrite(response);
        }
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
