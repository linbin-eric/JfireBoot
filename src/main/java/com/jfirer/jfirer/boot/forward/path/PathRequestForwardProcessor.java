package com.jfirer.jfirer.boot.forward.path;

import com.jfirer.jfire.core.ApplicationContext;
import com.jfirer.jfirer.boot.common.TraceId;
import com.jfirer.jfirer.boot.http.HttpRequestExtend;
import com.jfirer.jnet.common.api.ReadProcessor;
import com.jfirer.jnet.common.api.ReadProcessorNode;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import com.jfirer.jnet.extend.http.decode.HttpResponse;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Resource
@Slf4j
public class PathRequestForwardProcessor implements ReadProcessor<HttpRequest>
{
    @Resource
    private ApplicationContext       applicationContext;
    private Map<String, PathRequest> requestMap;
    private PathRequest[]            restfulRequests;

    @PostConstruct
    public void init()
    {
        requestMap      = applicationContext.getAllBeanRegisterInfos().stream()//
                                            .flatMap(beanRegisterInfo -> Arrays.stream(beanRegisterInfo.getType().getDeclaredMethods()))//
                                            .filter(method -> method.isAnnotationPresent(Path.class))//
                                            .map(method -> new PathRequest(method, applicationContext.getBeanRegisterInfo(method.getDeclaringClass()).get()))//
                                            .collect(Collectors.toMap(PathRequest::getPath, Function.identity()));
        restfulRequests = requestMap.values().stream().filter(request -> request.getRestfulMatch() != null).toArray(PathRequest[]::new);
    }

    @TraceId
    @Override
    public void read(HttpRequest data, ReadProcessorNode next)
    {
        try (HttpRequestExtend requestExtend = HttpRequestExtend.from(data, next.pipeline()))
        {
            String      path        = requestExtend.getPath();
            PathRequest pathRequest = requestMap.get(path);
            if (pathRequest == null)
            {
                Map<String, Object> paramMap = requestExtend.getParamMap();
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
                HttpResponse response = new HttpResponse();
                response.setBody("notAvailable path:" + requestExtend.getPath());
                next.pipeline().fireWrite(response);
                return;
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
            log.error("请求出现异常", e);
            HttpResponse response = new HttpResponse();
            response.setBody("error:" + e.toString());
            next.pipeline().fireWrite(response);
        }
    }
}
