package com.jfirer.jfirer.boot.forward.path;

import com.jfirer.jfirer.boot.common.TraceId;
import com.jfirer.jfirer.boot.http.HttpRequestExtend;
import com.jfirer.jnet.common.api.ReadProcessor;
import com.jfirer.jnet.common.api.ReadProcessorNode;
import com.jfirer.jnet.extend.http.dto.FullHttpResp;
import com.jfirer.jnet.extend.http.dto.HttpRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class PathRequestForwardProcessor implements ReadProcessor<HttpRequest>
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
    public void read(HttpRequest data, ReadProcessorNode next)
    {
        String path = "";
        try (HttpRequestExtend requestExtend = HttpRequestExtend.from(data, next.pipeline()))
        {
            path = requestExtend.getPath();
            PathRequest pathRequest = requestMap.get(path);
            if (pathRequest == null)
            {
                Map<String, Object> paramMap = requestExtend.getNotNullParamMap();
                for (PathRequest restfulRequest : restfulRequests)
                {
                    if (restfulRequest.getRestfulMatch().match(path, paramMap))
                    {
                        Object value = restfulRequest.invoke(requestExtend);
                        if (value != null)
                        {
                            next.pipeline().fireWrite(value);
                        }
                        return;
                    }
                }
                next.fireRead(requestExtend);
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
        catch (Throwable e)
        {
            log.error("请求出现异常,当前请求路径:{}", path, e);
            FullHttpResp response = new FullHttpResp();
            response.getBody().setBodyText("error:" + e.toString());
            next.pipeline().fireWrite(response);
        }
    }
}
