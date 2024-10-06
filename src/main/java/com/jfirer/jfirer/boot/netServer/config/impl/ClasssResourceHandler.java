package com.jfirer.jfirer.boot.netServer.config.impl;

import com.jfirer.jfirer.boot.netServer.config.ResourceHandler;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.extend.http.decode.HttpRequest;

public class ClasssResourceHandler implements ResourceHandler
{
    private String matchUrl;
    private String path;
    @Override
    public boolean process(HttpRequest request, Pipeline pipeline)
    {
        return false;
    }
}
