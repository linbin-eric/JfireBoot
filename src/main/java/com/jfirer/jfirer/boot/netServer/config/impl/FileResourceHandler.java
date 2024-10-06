package com.jfirer.jfirer.boot.netServer.config.impl;

import com.jfirer.baseutil.CodeLocation;
import com.jfirer.baseutil.IoUtil;
import com.jfirer.baseutil.STR;
import com.jfirer.jfirer.boot.netServer.ContentTypeDist;
import com.jfirer.jfirer.boot.netServer.config.ResourceHandler;
import com.jfirer.jfirer.boot.netServer.impl.FileHandler;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import com.jfirer.jnet.extend.http.decode.HttpResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class FileResourceHandler implements ResourceHandler
{
    private String  matchUrl;
    private int     prefixLength;
    private boolean absolutePath = false;
    private File    dir;

    public FileResourceHandler(String matchUrl, String path)
    {
        this.matchUrl = matchUrl;
        prefixLength  = matchUrl.length();
        String originPath = path;
        path         = path.substring("file:".length());
        absolutePath = isAbsolutePath(path);
        if (absolutePath)
        {
            dir = new File(path);
        }
        else
        {
            File tmp = CodeLocation.getFilePathOfMainMethodClass();
            while (path.startsWith("../"))
            {
                tmp  = tmp.getParentFile();
                path = path.substring(3);
            }
            dir = new File(tmp, path);
        }
        if (!dir.isDirectory())
        {
            throw new IllegalArgumentException(STR.format("路径:{}应该是一个文件夹，而不是文件", originPath));
        }
    }

    private static boolean isAbsolutePath(String path)
    {
        char c = path.charAt(0);
        //这个地址是绝对路径
        return c == '/' || (c >= 'a' && c <= 'z' && path.charAt(1) == ':') || (c >= 'A' && c <= 'Z' && path.charAt(1) == ':');
    }

    @Override
    public boolean process(HttpRequest httpRequest, Pipeline pipeline)
    {
        String requestUrl = httpRequest.getUrl();
        if (requestUrl.equalsIgnoreCase("/"))
        {
            requestUrl = "/index.html";
        }
        else if (requestUrl.contains("#/"))
        {
            requestUrl = requestUrl.substring(0, requestUrl.indexOf("#/"));
        }
        if (requestUrl.startsWith(matchUrl))
        {
            byte[] bytes;
            String contentType;
            int    i = requestUrl.lastIndexOf(".");
            if (i == -1)
            {
                contentType = "text/html";
            }
            else
            {
                contentType = ContentTypeDist.getOrDefault(requestUrl.substring(i), "text/html");
            }
            File resourceFile = new File(dir, requestUrl.substring(prefixLength));
            if (resourceFile.exists())
            {
                try (InputStream inputStream = new FileInputStream(resourceFile))
                {
                    bytes = IoUtil.readAllBytes(inputStream);
                    return new FileHandler.ResourceContent(bytes, contentType);
                }
                catch (IOException e)
                {
                    throw new RuntimeException("读取文件地址:" + resourceFile.getAbsolutePath() + "出现异常", e);
                }
            }
            else
            {
                return new FileHandler.ResourceContent(("not available path:" + str + ",not find in " + resourceFile.getAbsolutePath()).getBytes(StandardCharsets.UTF_8), "text/html;charset=utf-8");
            }
        }
        else
        {
            return false;
        }
    }
}
