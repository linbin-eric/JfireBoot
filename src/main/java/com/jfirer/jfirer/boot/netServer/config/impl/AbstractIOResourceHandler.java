package com.jfirer.jfirer.boot.netServer.config.impl;

import com.jfirer.jfirer.boot.netServer.ContentTypeDist;
import com.jfirer.jfirer.boot.netServer.config.ResourceHandler;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.extend.http.decode.HttpRequest;

public abstract class AbstractIOResourceHandler implements ResourceHandler
{
    protected String matchUrl;
    protected int    matchUrlLength;
    protected String path;

    /**
     * 通过matchUrl进行前缀匹配。
     * 匹配成功的情况下，截取地址中非matchUrl的部分，拼接在path后，作为完整的资源地址进行读取
     *
     * @param matchUrl
     * @param path
     */
    public AbstractIOResourceHandler(String matchUrl, String path)
    {
        this.matchUrl  = matchUrl;
        matchUrlLength = matchUrl.length();
        this.path      = path.substring("file:".length());
    }

    @Override
    public boolean process(HttpRequest httpRequest, Pipeline pipeline)
    {
        String requestUrl = httpRequest.getUrl();
        if (requestUrl.equalsIgnoreCase("/"))
        {
            requestUrl = "/index.html";
        }
        else if (requestUrl.contains("#/"))
        {
            requestUrl = requestUrl.substring(0, requestUrl.indexOf("#/"));
        }
        if (requestUrl.startsWith(matchUrl))
        {
            String contentType;
            int    i = requestUrl.lastIndexOf(".");
            if (i == -1)
            {
                contentType = "text/html";
            }
            else
            {
                contentType = ContentTypeDist.getOrDefault(requestUrl.substring(i), "text/html");
            }
            process(httpRequest, pipeline, requestUrl.substring(matchUrlLength), contentType);
            return true;
        }
        else
        {
            return false;
        }
    }

    protected abstract void process(HttpRequest httpRequest, Pipeline pipeline, String requestUrl, String contentType);
}
