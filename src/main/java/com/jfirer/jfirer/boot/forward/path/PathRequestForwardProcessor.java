package com.jfirer.jfirer.boot.forward.path;

import com.jfirer.baseutil.StringUtil;
import com.jfirer.jfire.core.ApplicationContext;
import com.jfirer.jfire.core.bean.BeanRegisterInfo;
import com.jfirer.jfirer.boot.common.TraceId;
import com.jfirer.jfirer.boot.forward.html.AliasHandler;
import com.jfirer.jfirer.boot.forward.html.NetAgent;
import com.jfirer.jfirer.boot.forward.html.ProxyHandler;
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
    private ApplicationContext                    applicationContext;
    private Map<String, PathRequest>              requestMap;
    private NetAgent                              netAgent;
    private Class                                 rootClass;
    private Function<HttpRequest, HttpResponse>[] handlers;

    @PostConstruct
    public void init()
    {
        requestMap = applicationContext.getAllBeanRegisterInfos().stream().flatMap(beanRegisterInfo -> Arrays.stream(beanRegisterInfo.getType().getDeclaredMethods()))//
                                       .filter(method -> method.isAnnotationPresent(Path.class))//
                                       .map(method -> new PathRequest(method, applicationContext.getBeanRegisterInfo(method.getDeclaringClass()).get()))//
                                       .collect(Collectors.toMap(PathRequest::getPath, Function.identity()));
        netAgent   = applicationContext.getAllBeanRegisterInfos().stream().filter(beanRegisterInfo -> beanRegisterInfo.getType().isAnnotationPresent(NetAgent.class))//
                                       .map(beanRegisterInfo -> beanRegisterInfo.getType().getAnnotation(NetAgent.class))//
                                       .findFirst().get();
        rootClass  = applicationContext.getAllBeanRegisterInfos().stream().filter(beanRegisterInfo -> beanRegisterInfo.getType().isAnnotationPresent(NetAgent.class))//
                                       .map(BeanRegisterInfo::getType)//
                                       .findFirst().get();
        handlers   = Arrays.stream(netAgent.LOCATIONS())//
                           .map(location ->
                           {
                               if (StringUtil.isNotBlank(location.alias()))
                               {
                                   return new AliasHandler(location.url(), location.alias(), rootClass);
                               }
                               else
                               {
                                   return new ProxyHandler(location.url(), location.proxyPass());
                               }
                           }).toArray(Function[]::new);
    }

    @TraceId
    @Override
    public void read(HttpRequest data, ReadProcessorNode next)
    {
        for (Function<HttpRequest, HttpResponse> handler : handlers)
        {
            HttpResponse response = handler.apply(data);
            if (response != null)
            {
                next.pipeline().fireWrite(response);
                data.close();
                return;
            }
        }
        try (HttpRequestExtend requestExtend = HttpRequestExtend.from(data, next.pipeline()))
        {
            PathRequest pathRequest = requestMap.get(requestExtend.getPath());
            if (pathRequest == null)
            {
                HttpResponse response = new HttpResponse();
                response.setBody("notAvailable path:" + requestExtend.getPath());
                next.pipeline().fireWrite(response);
                return;
            }
            Object value = pathRequest.invoke(requestExtend);
            if (value != null)
            {
                next.pipeline().fireWrite(value);
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
