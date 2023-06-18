package com.jfirer.jfirer.boot.forward.openapi;

import com.jfirer.dson.Dson;
import com.jfirer.jfire.core.ApplicationContext;
import com.jfirer.jfirer.boot.http.HttpRequestExtend;
import com.jfirer.jnet.common.util.ReflectUtil;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 该类用于根据RequestData转发请求到对应的方法。
 */
@Resource
@Slf4j
public class OpenApiForward
{
    @Resource
    private       ApplicationContext                  applicationContext;
    private       Map<String, ServiceRequest>         serviceMap;
    private final Function<HttpRequestExtend, String> dataExtracter;

    public OpenApiForward(Function<HttpRequestExtend, String> dataExtracter) {this.dataExtracter = dataExtracter;}

    @PostConstruct
    public void init()
    {
        serviceMap = applicationContext.getAllBeanRegisterInfos().stream().flatMap(beanRegisterInfo -> Arrays.stream(beanRegisterInfo.getType().getDeclaredMethods()))//
                                       .filter(method -> method.isAnnotationPresent(ServiceId.class))//
                                       .map(method -> new ServiceRequest(applicationContext.getBeanRegisterInfo(method.getDeclaringClass()).get(), method, dataExtracter)).collect(Collectors.toMap(ServiceRequest::getServiceId, Function.identity()));
    }

    public ServiceRequest route(HttpRequestExtend data)
    {
        try
        {
            ApiRequest request = Dson.fromString(ApiRequest.class, data.getUtf8StrBody());
            return serviceMap.get(request.serviceId);
        }
        catch (Throwable e)
        {
            log.error("发生异常，当前内容为:{}", data.getUtf8StrBody());
            ReflectUtil.throwException(e);
            return null;
        }
    }

    class ApiRequest
    {
        String serviceId;
    }

}
