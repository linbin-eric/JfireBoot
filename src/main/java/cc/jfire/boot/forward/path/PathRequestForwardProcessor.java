package cc.jfire.boot.forward.path;

import cc.jfire.baseutil.STR;
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

    private void handleHttpRequest(HttpRequest data, ReadProcessorNode next)
    {
        String path = "";
        try (HttpRequestExtend requestExtend = HttpRequestExtend.from(data, next.pipeline()))
        {
            path = requestExtend.getPath();
            String requestMethod = requestExtend.getMethod();
            // 尝试精确匹配：路径 + HTTP 方法（非 RESTful 路径的 ALL 已在注册时展开）
            PathRequest[] pathRequests = specificRequestMap.get(path);
            PathRequest   selected     = null;
            if (pathRequests == null)
            {
                // 精确路径未匹配，尝试 RESTful 路由
                Map<String, Object> paramMap         = requestExtend.getNotNullParamMap();
                Map<String, Object> originalParamMap = new HashMap<>(paramMap);
                for (PathRequest restfulRequest : restfulRequests)
                {
                    paramMap.clear();
                    paramMap.putAll(originalParamMap);
                    if (restfulRequest.getRestfulMatch().match(path, paramMap) && restfulRequest.matchesMethod(requestMethod))
                    {
                        selected = restfulRequest;
                    }
                }
                if (selected == null)
                {
                    // 路径不存在，传递给下一个处理器（404）
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
                //此时首先自动回复 101响应
                IoBuffer buffer = WebSocketHandshakeUtil.buildUpgradeResponse(getSecWebSocketKey(requestExtend), next.pipeline().allocator());
                next.pipeline().fireWrite(buffer);
                //执行的时候会将 wsconnection绑定到当前的 pipeline
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
