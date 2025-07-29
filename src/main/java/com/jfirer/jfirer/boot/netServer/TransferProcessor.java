package com.jfirer.jfirer.boot.netServer;

import com.jfirer.jfirer.boot.netServer.config.ResourceConfig;
import com.jfirer.jfirer.boot.netServer.config.ResourceHandler;
import com.jfirer.jnet.common.api.ReadProcessor;
import com.jfirer.jnet.common.api.ReadProcessorNode;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import com.jfirer.jnet.extend.http.dto.FullHttpResp;

import java.util.Arrays;

public class TransferProcessor implements ReadProcessor<HttpRequest>
{
    private ResourceHandler[] handlers;

    public TransferProcessor(ResourceConfig[] configs)
    {
        handlers = Arrays.stream(configs).map(ResourceConfig::parse).toArray(ResourceHandler[]::new);
    }

    @Override
    public void read(HttpRequest request, ReadProcessorNode next)
    {
        for (ResourceHandler handler : handlers)
        {
            if (handler.process(request, next.pipeline()))
            {
                return;
            }
        }
        FullHttpResp response = new FullHttpResp();
        response.getBody().setBodyText("not found address:" + request.getUrl());
        request.close();
        next.pipeline().fireWrite(response);
    }

    @Override
    public void readFailed(Throwable e, ReadProcessorNode next)
    {
        try
        {
            for (ResourceHandler handler : handlers)
            {
                handler.readFailed(e);
            }
        }
        finally
        {
            ;
        }
        next.fireReadFailed(e);
    }
}
