package com.jfirer.jfirer.boot.netServer;

import com.jfirer.baseutil.StringUtil;
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
    private TransferHandler[] handlers;

    public TransferProcessor( Location[] locations)
    {
        handlers       = Arrays.stream(locations).map(location ->
        {
            if (StringUtil.isNotBlank(location.file))
            {
                return new FileHandler(location.url, location.file);
            }
            else
            {
                return new ProxyHandler(location.url, location.proxy);
            }
        }).toArray(TransferHandler[]::new);
    }

    @Override
    public void read(HttpRequest request, ReadProcessorNode next)
    {
        for (TransferHandler handler : handlers)
        {
            if (handler.apply(request, next.pipeline()))
            {
                return;
            }
        }
        HttpResponse response = new HttpResponse();
        response.setBody("not found address:" + request.getUrl());
        next.pipeline().fireWrite(response);
    }

    @Data
    @Builder
    public static class Location
    {
        private String url;
        private String file;
        private String proxy;
    }
}
