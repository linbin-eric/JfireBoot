package com.jfirer.jfirer.boot.netServer.config.impl.proxy;

import com.jfirer.baseutil.STR;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.extend.http.decode.HttpRequest;

public final class PrefixMatchProxyHttpHandler extends ProxyHttpHandler
{
    private String prefixMatch;
    private int    len;
    private String proxy;

    public PrefixMatchProxyHttpHandler(String prefixMatch, String proxy)
    {
        isValidPrefix(prefixMatch);
        this.prefixMatch = prefixMatch.substring(0, prefixMatch.length() - 1);
        len              = this.prefixMatch.length();
        this.proxy       = proxy;
    }

    private void isValidPrefix(String str)
    {
        if (str.endsWith("/*") && str.chars().filter(c -> c == '*').count() == 1)
        {
            ;
        }
        else
        {
            throw new IllegalArgumentException(STR.format("{}不是合规的前缀匹配地址", str));
        }
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
