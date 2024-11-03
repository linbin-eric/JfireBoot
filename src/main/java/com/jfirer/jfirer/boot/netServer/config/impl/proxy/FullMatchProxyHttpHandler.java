package com.jfirer.jfirer.boot.netServer.config.impl.proxy;

import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.extend.http.decode.HttpRequest;

/**
 * 请求url完全匹配match，则将请求发向proxy
 */
public final class FullMatchProxyHttpHandler extends ProxyHttpHandler
{
    private String match;
    private String proxy;

    public FullMatchProxyHttpHandler(String match, String proxy)
    {
        this.match = match;
        this.proxy = proxy;
    }

    @Override
    public boolean process(HttpRequest request, Pipeline pipeline)
    {
        String requestUrl = request.getUrl();
        if (requestUrl.contains("#/"))
        {
            requestUrl = requestUrl.substring(0, requestUrl.indexOf("#/"));
        }
        if (requestUrl.equals(match))
        {
            proxyBackendUrl(request, pipeline, proxy);
            return true;
        }
        else
        {
            return false;
        }
    }
}
