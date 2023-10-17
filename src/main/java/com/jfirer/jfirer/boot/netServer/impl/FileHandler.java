package com.jfirer.jfirer.boot.netServer.impl;

import com.jfirer.baseutil.CodeLocation;
import com.jfirer.baseutil.IoUtil;
import com.jfirer.jfirer.boot.netServer.ContentTypeDist;
import com.jfirer.jfirer.boot.netServer.TransferHandler;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import com.jfirer.jnet.extend.http.decode.HttpResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public class FileHandler implements TransferHandler
{
    private String                                 matchUrl;
    private String                                 location;
    private int                                    postFixIndex;
    private ConcurrentMap<String, ResourceContent> map             = new ConcurrentHashMap<>();
    private boolean                                cachable;
    private Function<String, ResourceContent>      resourceHandler = str -> {
        byte[] bytes;
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
                File dir = CodeLocation.getFilePathOfMainMethodClass();
                while (sub.startsWith("../"))
                {
                    dir = dir.getParentFile();
                    sub = sub.substring(3);
                }
                dir = new File(dir, sub);
                File resourceFile = new File(dir, str);
                if (resourceFile.exists())
                {
                    try (InputStream inputStream = new FileInputStream(resourceFile))
                    {
                        bytes = IoUtil.readAllBytes(inputStream);
                        return new ResourceContent(bytes, contentType);
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException("读取文件地址:" + resourceFile.getAbsolutePath() + "出现异常", e);
                    }
                }
                else
                {
                    return new ResourceContent(("not available path:" + str + ",not find in " + resourceFile.getAbsolutePath()).getBytes(StandardCharsets.UTF_8), "text/html;charset=utf-8");
                }
            }
        }
    };

    public FileHandler(String matchUrl, String location)
    {
        this.matchUrl = matchUrl;
        this.location = location;
        postFixIndex  = matchUrl.length();
        cachable      = location.startsWith("classpath");
    }

    @Override
    public Boolean apply(HttpRequest httpRequest, Pipeline pipeline)
    {
        String requestUrl = httpRequest.getUrl();
        if (requestUrl.contains("#/"))
        {
            requestUrl = requestUrl.substring(0, requestUrl.indexOf("#/"));
        }
        if (requestUrl.equalsIgnoreCase("/"))
        {
            requestUrl = "/index.html";
        }
        if (requestUrl.startsWith(matchUrl))
        {
            String          postPath = requestUrl.substring(postFixIndex);
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

    record ResourceContent(byte[] bytes, String contentType)
    {
    }
}
