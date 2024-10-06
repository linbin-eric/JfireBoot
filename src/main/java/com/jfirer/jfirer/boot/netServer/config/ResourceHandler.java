package com.jfirer.jfirer.boot.netServer.config;

import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.extend.http.decode.HttpRequest;

public interface ResourceHandler
{
    boolean process(HttpRequest request, Pipeline pipeline);
}
