package com.jfirer.jfirer.boot.forward.html;

import com.jfirer.baseutil.IoUtil;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import com.jfirer.jnet.extend.http.decode.HttpResponse;

import java.io.*;
import java.net.URISyntaxException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public class AliasHandler implements Function<HttpRequest, HttpResponse>
{
    private String                                   url;
    private String                                   location;
    private int                                      postFixIndex;
    private Class                                    rootClass;
    private ConcurrentMap<String, ClassPathResource> map = new ConcurrentHashMap<>();

    public AliasHandler(String url, String location, Class rootClass)
    {
        this.url       = url;
        this.location  = location;
        this.rootClass = rootClass;
        postFixIndex   = url.length();
    }


    @Override
    public HttpResponse apply(HttpRequest extend)
    {
        if (extend.getUrl().startsWith(url))
        {
            String postPath = extend.getUrl().substring(postFixIndex);
            ClassPathResource classPathResource = map.computeIfAbsent(postPath, str ->
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
                        return new ClassPathResource(bytes, contentType);
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
                else
                {
                    String sub = location.substring("file:".length());
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
                            return new ClassPathResource(bytes, contentType);
                        }
                        catch (FileNotFoundException e)
                        {
                            throw new RuntimeException(e);
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
            });
            HttpResponse response = new HttpResponse();
            response.setContentType(classPathResource.contentType);
            response.setBytes_body(classPathResource.bytes);
            return response;
        }
        else
        {
            return null;
        }
    }

    record ClassPathResource(byte[] bytes, String contentType) {}
}
