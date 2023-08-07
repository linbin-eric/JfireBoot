package com.jfirer.jfirer.boot.netServer.impl;

import com.jfirer.baseutil.IoUtil;
import com.jfirer.jfirer.boot.netServer.TransferHandler;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import com.jfirer.jnet.extend.http.decode.HttpResponse;

import java.io.*;
import java.net.URISyntaxException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public class FileHandler implements TransferHandler
{
    private String                                 url;
    private String                                 location;
    private int                                    postFixIndex;
    private Class<?>                               rootClass;
    private ConcurrentMap<String, ResourceContent> map             = new ConcurrentHashMap<>();
    private boolean                                cachable;
    private Function<String, ResourceContent>      resourceHandler = str ->
    {
        byte[] bytes;
        String contentType;
        if (str.endsWith("css"))
        {
            contentType = "text/css";
        }
        else if (str.endsWith("js"))
        {
            contentType = "text/javascript";
        }
        else if (str.endsWith("ico") || str.endsWith("jpg") || str.endsWith("png"))
        {
            contentType = "image/png";
        }
        else
        {
            contentType = "text/html";
        }
        if (location.startsWith("classpath:"))
        {
            String realClassResourcePath = location.substring("classpath:".length()) + str;
            try (InputStream resourceAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(realClassResourcePath))
            {
                bytes = IoUtil.readAllBytes(resourceAsStream);
                return new ResourceContent(bytes, contentType);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
        else
        {
            String sub = location.substring("file:".length());
            char   c   = sub.charAt(0);
            if (c == '/' || (c >= 'a' && c <= 'z' && sub.charAt(1) == ':') || (c >= 'A' && c <= 'Z' && sub.charAt(1) == ':'))//这个地址是绝对路径
            {
                try (InputStream inputStream = new FileInputStream(new File(sub, str)))
                {
                    bytes = IoUtil.readAllBytes(inputStream);
                    return new ResourceContent(bytes, contentType);
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }
            else
            {
                try
                {
                    File dir = new File(rootClass.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getParentFile();
                    while (sub.startsWith("../"))
                    {
                        dir = dir.getParentFile();
                        sub = sub.substring(3);
                    }
                    dir = new File(dir, sub);
                    try (InputStream inputStream = new FileInputStream(new File(dir, str)))
                    {
                        bytes = IoUtil.readAllBytes(inputStream);
                        return new ResourceContent(bytes, contentType);
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
                catch (URISyntaxException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    };

    public FileHandler(String url, String location, Class rootClass)
    {
        this.url       = url;
        this.location  = location;
        this.rootClass = rootClass;
        postFixIndex   = url.length();
        cachable       = location.startsWith("classpath");
    }

    @Override
    public Boolean apply(HttpRequest httpRequest, Pipeline pipeline)
    {
        if (httpRequest.getUrl().startsWith(url))
        {
            String          postPath = httpRequest.getUrl().substring(postFixIndex);
            ResourceContent resourceContent;
            if (cachable)
            {
                resourceContent = map.computeIfAbsent(postPath, resourceHandler);
            }
            else
            {
                resourceContent = resourceHandler.apply(postPath);
            }
            httpRequest.close();
            HttpResponse response = new HttpResponse();
            response.setContentType(resourceContent.contentType);
            response.setBytes_body(resourceContent.bytes);
            pipeline.fireWrite(response);
            return true;
        }
        else
        {
            return false;
        }
    }

    record ResourceContent(byte[] bytes, String contentType) {}
}
