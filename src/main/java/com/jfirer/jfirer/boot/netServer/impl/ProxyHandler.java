package com.jfirer.jfirer.boot.netServer.impl;

import com.jfirer.jfirer.boot.netServer.TransferHandler;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.common.buffer.buffer.IoBuffer;
import com.jfirer.jnet.extend.http.client.HttpClient;
import com.jfirer.jnet.extend.http.client.HttpReceiveResponse;
import com.jfirer.jnet.extend.http.client.HttpSendRequest;
import com.jfirer.jnet.extend.http.client.PartOfBody;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import com.jfirer.jnet.extend.http.decode.HttpResponse;

public class ProxyHandler implements TransferHandler
{
    private String url;
    private String proxyPass;

    public ProxyHandler(String url, String proxyPass)
    {
        this.url       = url;
        this.proxyPass = proxyPass;
    }

    @Override
    public Boolean apply(HttpRequest request, Pipeline pipeline)
    {
        if (request.getUrl().startsWith(url))
        {
            String          post            = request.getUrl().substring(url.length());
            String          backendUrl      = proxyPass + post;
            HttpSendRequest httpSendRequest = new HttpSendRequest();
            httpSendRequest.setUrl(backendUrl).setMethod(request.getMethod()).setContentType(request.getContentType());
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
            httpSendRequest.setMethod(request.getMethod());
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
        else
        {
            return false;
        }
    }
}
