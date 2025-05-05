package com.jfirer.jfirer.boot.http;

import com.jfirer.baseutil.IoUtil;
import com.jfirer.jfirer.boot.netServer.ContentTypeDist;
import com.jfirer.jnet.common.api.ReadProcessor;
import com.jfirer.jnet.common.api.ReadProcessorNode;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import com.jfirer.jnet.extend.http.dto.FullHttpResp;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
public class ResourceProcessor implements ReadProcessor<HttpRequest>
{
    record StaticResource(byte[] content, String contentType)
    {
    }

    private       ConcurrentMap<String, StaticResource> map = new ConcurrentHashMap<>();
    private final String                                prefixPath;

    public ResourceProcessor(String prefixPath) {this.prefixPath = prefixPath;}

    public void setNotFound(String url, HttpRequest httpRequest)
    {
        String purePath = url.contains("?") ? url.substring(0, url.indexOf("?")) : url;
        map.put(purePath, new StaticResource(("unAvailable Path:" + purePath).getBytes(StandardCharsets.UTF_8), "text/html"));
    }

    @Override
    public void read(HttpRequest httpRequest, ReadProcessorNode next)
    {
        String url = httpRequest.getUrl();
        if (httpRequest.getMethod().equalsIgnoreCase("get") == false)
        {
            next.fireRead(httpRequest);
        }
        else
        {
            if (url.equalsIgnoreCase("/"))
            {
                url = "/index.html";
            }
            String purePath = url.contains("?") ? url.substring(0, url.indexOf("?")) : url;
            StaticResource staticResource = map.computeIfAbsent(purePath, str -> {
                String contentType;
                int    i = str.lastIndexOf(".");
                if (i == -1)
                {
                    contentType = "text/html";
                }
                else
                {
                    String suffix = str.substring(i);
                    String s      = ContentTypeDist.get(suffix);
                    contentType = s == null ? "text/html" : s;
                }
                String realClassResourcePath = prefixPath + str;
                log.debug("当前请求路径为:{}", realClassResourcePath);
                try (InputStream resourceAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(realClassResourcePath))
                {
                    if (resourceAsStream != null)
                    {
                        return new StaticResource(IoUtil.readAllBytes(resourceAsStream), contentType);
                    }
                    else
                    {
                        return null;
                    }
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            });
            if (staticResource == null)
            {
                next.fireRead(httpRequest);
            }
            else
            {
                httpRequest.close();
                FullHttpResp response = new FullHttpResp();
                response.getHead().addHeader("Content-Type", staticResource.contentType);
                response.getBody().setBodyBytes(staticResource.content);
                response.getHead().addHeader("Cache-Control", "max-age=3600");
                next.pipeline().fireWrite(response);
            }
        }
    }
}
