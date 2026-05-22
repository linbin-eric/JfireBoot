package cc.jfire.boot.forward.path;

import cc.jfire.boot.http.HttpRequestExtend;

import java.util.function.Function;

public interface RequestInterceptor
{
    RequestInterceptor NO_OP = new RequestInterceptor()
    {
    };

    default Object intercept(HttpRequestExtend request, Function<HttpRequestExtend, Object> targetMethod)
    {
        return targetMethod.apply(request);
    }
}
