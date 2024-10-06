package com.jfirer.jfirer.boot.netServer;

import com.jfirer.baseutil.StringUtil;
import com.jfirer.jfirer.boot.netServer.config.ResourceConfig;
import com.jfirer.jfirer.boot.netServer.config.ResourceHandler;
import com.jfirer.jfirer.boot.netServer.impl.FileHandler;
import com.jfirer.jfirer.boot.netServer.impl.ProxyHandler;
import com.jfirer.jnet.common.api.ReadProcessor;
import com.jfirer.jnet.common.api.ReadProcessorNode;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import com.jfirer.jnet.extend.http.decode.HttpResponse;
import lombok.Builder;
import lombok.Data;

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
        HttpResponse response = new HttpResponse();
        response.setBody("not found address:" + request.getUrl());
        next.pipeline().fireWrite(response);
    }
}
