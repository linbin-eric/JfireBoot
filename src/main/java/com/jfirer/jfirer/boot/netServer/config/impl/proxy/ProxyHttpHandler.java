package com.jfirer.jfirer.boot.netServer.config.impl.proxy;

import com.jfirer.jfirer.boot.netServer.config.ResourceHandler;
import com.jfirer.jnet.client.ClientChannel;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.common.api.ReadProcessor;
import com.jfirer.jnet.common.api.ReadProcessorNode;
import com.jfirer.jnet.common.buffer.buffer.IoBuffer;
import com.jfirer.jnet.common.coder.HeartBeat;
import com.jfirer.jnet.common.util.ChannelConfig;
import com.jfirer.jnet.extend.http.client.HttpClient;
import com.jfirer.jnet.extend.http.client.HttpReceiveResponse;
import com.jfirer.jnet.extend.http.client.HttpSendRequest;
import com.jfirer.jnet.extend.http.client.Part;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import com.jfirer.jnet.extend.http.dto.FullHttpResp;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;

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
            IoBuffer copyed = pipeline.allocator().allocate(request.getBody().remainRead());
            copyed.put(request.getBody());
            httpSendRequest.setBody(copyed);
        }
        request.close();
        try (HttpReceiveResponse httpReceiveResponse = HttpClient.newCall(httpSendRequest))
        {
            httpReceiveResponse.waitForReceiveFinish(1000 * 60 * 5);
            IoBuffer buffer = pipeline.allocator().allocate(httpReceiveResponse.getContentLength() > 0 ? httpReceiveResponse.getContentLength() : 1024);
            Part     part;
            while ((part = httpReceiveResponse.pollChunk(1000 * 60)) != null && !part.endOfBody())
            {
                buffer.put(part.originData());
                part.free();
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

    protected void proxyBackendUrl2(HttpRequest request, Pipeline pipeline, String backendUrl) throws ConnectException
    {
        int index       = 0;
        int domainStart = 0;
        if (backendUrl.startsWith("http://"))
        {
            index       = backendUrl.indexOf("/", 8);
            domainStart = 7;
        }
        else if (backendUrl.startsWith("https://"))
        {
            index       = backendUrl.indexOf("/", 9);
            domainStart = 8;
        }
        if (index == -1)
        {
            index = backendUrl.length();
        }
        int      portStart = backendUrl.indexOf(':', domainStart);
        String   path      = index == backendUrl.length() ? "/" : backendUrl.substring(index);
        int      port      = portStart == -1 ? 80 : Integer.parseInt(backendUrl.substring(portStart + 1, index));
        String   domain    = portStart == -1 ? backendUrl.substring(domainStart, index) : backendUrl.substring(domainStart, portStart);
        IoBuffer buffer    = pipeline.allocator().allocate(1024);
        buffer.put((request.getMethod() + " " + path + " HTTP/1.1\r\n").getBytes(StandardCharsets.US_ASCII));
        IoBuffer originBuffer = request.getWholeRequest();
        originBuffer.addReadPosi(request.getLineLength());
        buffer.put(originBuffer);
        request.close();
        ClientChannel clientChannel = (ClientChannel) pipeline.getAttach();
        if (clientChannel == null || clientChannel.alive() == false)
        {
            clientChannel = ClientChannel.newClient(new ChannelConfig().setIp(domain).setPort(port), backend -> {
                backend.addReadProcessor(new HeartBeat(60 * 300, backend));
                backend.addReadProcessor(new ReadProcessor<IoBuffer>()
                {
                    @Override
                    public void read(IoBuffer data, ReadProcessorNode next)
                    {
                        pipeline.fireWrite(data);
                    }

                    @Override
                    public void readFailed(Throwable e, ReadProcessorNode next)
                    {
                        pipeline.shutdownInput();
                    }
                });
            });
            if (clientChannel.connect())
            {
                ;
            }
            else
            {
                pipeline.shutdownInput();
                throw new ConnectException(backendUrl + "无法联通");
            }
        }
        clientChannel.pipeline().fireWrite(buffer);
    }
}
