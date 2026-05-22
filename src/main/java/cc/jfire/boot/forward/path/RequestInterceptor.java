package cc.jfire.boot.forward.path;

import cc.jfire.boot.http.HttpRequestExtend;

public interface RequestInterceptor
{
    default Object intercept(HttpRequestExtend request, PathRequest pathRequest)
    {
        return pathRequest.invoke(request);
    }
}
