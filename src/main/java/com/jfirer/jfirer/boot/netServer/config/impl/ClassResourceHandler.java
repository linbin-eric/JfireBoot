package com.jfirer.jfirer.boot.netServer.config.impl;

import com.jfirer.baseutil.IoUtil;
import com.jfirer.baseutil.STR;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import com.jfirer.jnet.extend.http.decode.HttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ClassResourceHandler extends AbstractIOResourceHandler
{
    /**
     * 通过matchUrl进行前缀匹配。
     * 匹配成功的情况下，截取地址中非matchUrl的部分，拼接在path后，作为完整的资源地址进行读取
     *
     * @param matchUrl
     * @param originPath
     */
    public ClassResourceHandler(String matchUrl, String originPath)
    {
        super(matchUrl, originPath);
    }

    @Override
    protected void process(HttpRequest httpRequest, Pipeline pipeline, String requestUrl, String contentType)
    {
        String realClassResourcePath = path + requestUrl;
        try (InputStream resourceAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(realClassResourcePath))
        {
            if (resourceAsStream != null)
            {
                byte[] bytes = IoUtil.readAllBytes(resourceAsStream);
                httpRequest.close();
                HttpResponse response = new HttpResponse();
                response.setContentType(contentType);
                response.setBytes_body(bytes);
                pipeline.fireWrite(response);
            }
            else
            {
                httpRequest.close();
                HttpResponse response = new HttpResponse();
                response.setBytes_body(STR.format("not available path:{},not find in :{}", httpRequest.getUrl(), realClassResourcePath).getBytes(StandardCharsets.UTF_8));
                response.setContentType("text/html;charset=utf-8");
                pipeline.fireWrite(response);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
