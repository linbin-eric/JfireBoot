package com.jfirer.jfirer.boot.netServer.config.impl.proxy;

import com.jfirer.jfirer.boot.netServer.config.ResourceHandler;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.common.buffer.buffer.IoBuffer;
import com.jfirer.jnet.extend.http.client.HttpClient;
import com.jfirer.jnet.extend.http.client.HttpReceiveResponse;
import com.jfirer.jnet.extend.http.client.HttpSendRequest;
import com.jfirer.jnet.extend.http.client.PartOfBody;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import com.jfirer.jnet.extend.http.dto.FullHttpResp;

public sealed abstract class ProxyHttpHandler implements ResourceHandler permits PrefixMatchProxyHttpHandler, FullMatchProxyHttpHandler
{
    protected void proxyBackendUrl(HttpRequest request, Pipeline pipeline, String backendUrl)
    {
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
            FullHttpResp httpResponse = new FullHttpResp();
            httpResponse.getHead().setResponseCode(httpReceiveResponse.getHttpCode());
            if (buffer.remainRead() > 0)
            {
                httpResponse.getBody().setBodyBuffer(buffer);
            }
            httpReceiveResponse.getHeaders().forEach((name, value) -> httpResponse.getHead().addHeader(name, value));
            pipeline.fireWrite(httpResponse);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
