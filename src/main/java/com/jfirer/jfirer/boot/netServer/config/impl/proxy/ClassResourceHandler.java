package com.jfirer.jfirer.boot.netServer.config.impl.proxy;

import com.jfirer.baseutil.IoUtil;
import com.jfirer.baseutil.STR;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import com.jfirer.jnet.extend.http.decode.HttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public final class ClassResourceHandler extends AbstractIOResourceHandler
{
    record Tuper(String contentType, byte[] bytes)
    {
    }

    public ClassResourceHandler(String matchUrl, String originPath)
    {
        super(matchUrl, originPath);
    }

    private ConcurrentHashMap<String, Tuper> map = new ConcurrentHashMap<>();

    @Override
    protected void process(HttpRequest httpRequest, Pipeline pipeline, String requestUrl, String contentType)
    {
        String realClassResourcePath = path + requestUrl;
        Tuper tuper = map.computeIfAbsent(realClassResourcePath, url -> {
            try (InputStream resourceAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(realClassResourcePath))
            {
                if (resourceAsStream != null)
                {
                    byte[] bytes = IoUtil.readAllBytes(resourceAsStream);
                    return new Tuper(contentType, bytes);
                }
                else
                {
                    return new Tuper("text/html;charset=utf-8", STR.format("not available path:{},not find in :{}", httpRequest.getUrl(), realClassResourcePath).getBytes(StandardCharsets.UTF_8));
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        });
        httpRequest.close();
        HttpResponse response = new HttpResponse();
        response.setContentType(tuper.contentType);
        response.setBytes_body(tuper.bytes);
        pipeline.fireWrite(response);
    }
}
