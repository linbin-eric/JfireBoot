package com.jfirer.jfirer.boot.http;

import com.jfirer.jnet.common.api.ReadProcessor;
import com.jfirer.jnet.common.api.ReadProcessorNode;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import com.jfirer.jnet.extend.http.decode.HttpResponse;

public class NotFoundUrlProcessor implements ReadProcessor<HttpRequest>
{
    private final ResourceProcessor resourceProcessor;

    public NotFoundUrlProcessor(ResourceProcessor resourceProcessor) {this.resourceProcessor = resourceProcessor;}

    @Override
    public void read(HttpRequest data, ReadProcessorNode next)
    {
        resourceProcessor.setNotFound(data.getUrl(), data);
        HttpResponse response = new HttpResponse();
        response.setBody("notAvailable path:" + data.getUrl());
        next.pipeline().fireWrite(response);
    }
}
