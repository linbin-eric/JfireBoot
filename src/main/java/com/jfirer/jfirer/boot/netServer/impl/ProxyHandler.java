package com.jfirer.jfirer.boot.netServer.impl;

import com.jfirer.baseutil.StringUtil;
import com.jfirer.jfirer.boot.netServer.TransferHandler;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.common.buffer.buffer.IoBuffer;
import com.jfirer.jnet.extend.http.client.HttpClient;
import com.jfirer.jnet.extend.http.client.HttpReceiveResponse;
import com.jfirer.jnet.extend.http.client.HttpSendRequest;
import com.jfirer.jnet.extend.http.client.PartOfBody;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import com.jfirer.jnet.extend.http.decode.HttpResponse;

import java.util.LinkedList;
import java.util.List;

public class ProxyHandler implements TransferHandler
{
    private              int        type;
    private static final int        PURE  = 1;
    private static final int        REST  = 2;
    private static final int        RANGE = 3;
    private              String     url;
    private              String     proxyPass;
    private              RestfulUrl restfulUrl;
    private              int        rangeBegin;

    /**
     * url 有两种形式：
     * 一、路径里没有*、[]，是纯粹的地址
     * 这种形式，实际请求的路径，与 url 进行前缀匹配。如果匹配成功的情况下，则将实际请求中除开 url 的部分，拼接到 proxyPass 的后面，作为后端的请求地址。
     * 二、路径中存在*
     * 这种形式，将 url 中的*看成是任意长度的字符串，使用整体的 url，对实际的请求地址进行匹配。如果匹配成功，将整个请求地址拼接到 proxyPass 的后面，作为后端的请求地址。
     * 例子：
     * url：/js/*\/diagnose.js 可以匹配请求为/js/H12232/diagnose.js的请求路径。
     * 三、路径中存在*，并且存在[]
     * 这是形式二的加强方式。首先以去掉[]的形式，对实际请求进行匹配。如果匹配成功，将可以用[]包围起来的部分拼接到 proxyPass 后面。
     * 对于[]的作用范围有一个限制，在一个 url 中只能出现1 次，其包围的范围不能涵盖 url 的开头且必须包括 url 的结尾。
     * 例子：
     * url: /js[/*\/diagnose.js] 可以匹配到请求为 /js/H12232/diagnose.js的路径，并且请求转发的路径是/H12232/diagnose.js
     */
    public ProxyHandler(String url, String proxyPass)
    {
        this.url = url;
        type     = url.contains("*") ? url.contains("[") ? RANGE : REST : PURE;
        switch (type)
        {
            case RANGE ->
            {
                restfulUrl = new RestfulUrl(url.replace("[", "").replace("]", ""));
                rangeBegin = url.indexOf('[');
            }
            case REST -> restfulUrl = new RestfulUrl(url.replace("[", "").replace("]", ""));
            case PURE -> {}
        }
        this.proxyPass = proxyPass;
    }

    @Override
    public Boolean apply(HttpRequest request, Pipeline pipeline)
    {
        String backendUrl = null;
        switch (type)
        {
            case PURE ->
            {
                if (request.getUrl().startsWith(url))
                {
                    backendUrl = proxyPass + request.getUrl().substring(url.length());
                }
                else
                {
                    return false;
                }
            }
            case REST ->
            {
                if (restfulUrl.match(request.getUrl()))
                {
                    backendUrl = proxyPass + request.getUrl();
                }
                else
                {
                    return false;
                }
            }
            case RANGE ->
            {
                if (restfulUrl.match(request.getUrl()))
                {
                    backendUrl = proxyPass + request.getUrl().substring(rangeBegin);
                }
                else
                {
                    return false;
                }
            }
        }
        HttpSendRequest httpSendRequest = new HttpSendRequest();
        httpSendRequest.setUrl(backendUrl).setMethod(request.getMethod());
        request.getHeaders().forEach((name, value) -> httpSendRequest.putHeader(name, value));
        if (request.getBody() == null)
        {
            ;
        }
        else
        {
            IoBuffer copyed = HttpClient.ALLOCATOR.ioBuffer(request.getBody().remainRead());
            copyed.put(request.getBody());
            httpSendRequest.setBody(copyed);
            request.close();
        }
        try (HttpReceiveResponse httpReceiveResponse = HttpClient.newCall(httpSendRequest))
        {
            httpReceiveResponse.waitForReceiveFinish();
            IoBuffer   buffer = HttpClient.ALLOCATOR.ioBuffer(httpReceiveResponse.getContentLength() > 0 ? httpReceiveResponse.getContentLength() : 1024);
            PartOfBody partOfBody;
            while ((partOfBody = httpReceiveResponse.pollChunk()) != null && !partOfBody.isEndOrTerminateOfBody())
            {
                buffer.put(partOfBody.getFullOriginData());
                partOfBody.freeBuffer();
            }
            HttpResponse httpResponse = new HttpResponse();
            httpResponse.setAutoSetContentLength(false);
            httpResponse.setAutoSetContentType(false);
            httpResponse.setBodyBuffer(buffer);
            httpReceiveResponse.getHeaders().forEach((name, value) -> httpResponse.getHeaders().put(name, value));
            pipeline.fireWrite(httpResponse);
            return true;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    class RestfulUrl
    {
        MatchNode[] matchNodes;

        public RestfulUrl(String url)
        {
            List<MatchNode> list = new LinkedList<>();
            do
            {
                int index = url.indexOf("*");
                if (index == 0)
                {
                    url = url.substring(1);
                    list.add(new MatchNode(false, "*"));
                }
                else if (index > 0)
                {
                    list.add(new MatchNode(true, url.substring(0, index)));
                    url = url.substring(index);
                }
                else
                {
                    if (StringUtil.isNotBlank(url))
                    {
                        list.add(new MatchNode(true, url));
                    }
                    break;
                }
            } while (true);
            matchNodes = list.toArray(MatchNode[]::new);
        }

        record MatchNode(boolean literal, String fragment)
        {
        }

        public boolean match(String url)
        {
            int index = 0;
            for (MatchNode each : matchNodes)
            {
                if (each.literal)
                {
                    index = url.indexOf(each.fragment, index);
                    if (index == 0)
                    {
                        index += each.fragment.length();
                    }
                    else
                    {
                        return false;
                    }
                }
                else
                {
                    ;
                }
            }
            return true;
        }
    }
}
