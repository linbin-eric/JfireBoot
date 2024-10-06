package com.jfirer.jfirer.boot.netServer.config.impl;

import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.extend.http.decode.HttpRequest;

public final class PrefixMatchProxyHttpHandler extends ProxyHttpHandler
{
    private String prefixMatch;
    private int    len;
    private String proxy;

    public PrefixMatchProxyHttpHandler(String prefixMatch, String proxy)
    {
        this.prefixMatch = prefixMatch;
        len              = prefixMatch.length();
        this.proxy       = proxy;
    }

    @Override
    public boolean process(HttpRequest request, Pipeline pipeline)
    {
        String requestUrl = request.getUrl();
        if (requestUrl.contains("#/"))
        {
            requestUrl = requestUrl.substring(0, requestUrl.indexOf("#/"));
        }
        if (requestUrl.startsWith(prefixMatch))
        {
            String backendUrl = proxy + requestUrl.substring(len);
            proxyBackendUrl(request, pipeline, backendUrl);
            return true;
        }
        else
        {
            return false;
        }
    }
}
