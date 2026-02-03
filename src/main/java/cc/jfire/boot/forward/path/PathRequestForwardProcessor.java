package cc.jfire.boot.forward.path;

import cc.jfire.boot.common.TraceId;
import cc.jfire.boot.http.HttpRequestExtend;
import cc.jfire.jnet.common.api.ReadProcessor;
import cc.jfire.jnet.common.api.ReadProcessorNode;
import cc.jfire.jnet.extend.http.dto.HttpRequest;
import cc.jfire.jnet.extend.http.dto.HttpResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class PathRequestForwardProcessor implements ReadProcessor<Object>
{
    private Map<String, PathRequest> requestMap;
    private PathRequest[]            restfulRequests;

    public PathRequestForwardProcessor(Map<String, PathRequest> requestMap)
    {
        this.requestMap = requestMap;
        restfulRequests = requestMap.values().stream().filter(request -> request.getRestfulMatch() != null).toArray(PathRequest[]::new);
    }

    @TraceId
    @Override
    public void read(Object data, ReadProcessorNode next)
    {
        if (data instanceof HttpRequest httpRequest)
        {
            handleHttpRequest(httpRequest, next);
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
            String      routeKey    = path + ":" + requestMethod;
            PathRequest pathRequest = requestMap.get(routeKey);
            if (pathRequest != null)
            {
                Object value = pathRequest.invoke(requestExtend);
                if (value != null)
                {
                    next.pipeline().fireWrite(value);
                }
                return;
            }
            // 精确路径未匹配，尝试 RESTful 路由
            Map<String, Object> paramMap          = requestExtend.getNotNullParamMap();
            Map<String, Object> originalParamMap  = new HashMap<>(paramMap);
            boolean             restfulPathExists = false;
            for (PathRequest restfulRequest : restfulRequests)
            {
                paramMap.clear();
                paramMap.putAll(originalParamMap);
                if (restfulRequest.getRestfulMatch().match(path, paramMap))
                {
                    restfulPathExists = true;
                    if (restfulRequest.matchesMethod(requestMethod))
                    {
                        Object value = restfulRequest.invoke(requestExtend);
                        if (value != null)
                        {
                            next.pipeline().fireWrite(value);
                        }
                        return;
                    }
                }
            }
            // 检查路径是否存在（用于判断返回 404 还是 405）
            String  pathPrefix = path + ":";
            boolean pathExists = requestMap.keySet().stream().anyMatch(key -> key.startsWith(pathPrefix));
            // 路径存在但方法不匹配，返回 405
            if (pathExists || restfulPathExists)
            {
                HttpResponse response = new HttpResponse();
                response.getHead().setStatusCode(405);
                response.getHead().setReasonPhrase("Method Not Allowed");
                response.setBodyText("Method Not Allowed");
                next.pipeline().fireWrite(response);
                return;
            }
            // 路径不存在，传递给下一个处理器（404）
            next.fireRead(requestExtend);
        }
        catch (Throwable e)
        {
            log.error("请求出现异常,当前请求路径:{}", path, e);
            HttpResponse response = new HttpResponse();
            response.setBodyText("error:" + e.toString());
            next.pipeline().fireWrite(response);
        }
    }
}
