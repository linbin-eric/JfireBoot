package com.jfirer.jfirer.boot.netServer.config.impl;

import com.jfirer.baseutil.CodeLocation;
import com.jfirer.baseutil.IoUtil;
import com.jfirer.baseutil.STR;
import com.jfirer.jfirer.boot.netServer.ContentTypeDist;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import com.jfirer.jnet.extend.http.decode.HttpResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class FileResourceHandler extends AbstractIOResourceHandler
{
    private File dir;

    /**
     * 通过matchUrl进行前缀匹配。
     * 匹配成功的情况下，截取地址中非matchUrl的部分，拼接在path后，作为完整的资源地址进行读取
     *
     * @param matchUrl
     * @param originPath
     */
    public FileResourceHandler(String matchUrl, String originPath)
    {
        super(matchUrl, originPath);
        if (isAbsolutePath(path))
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

    @Override
    protected void process(HttpRequest httpRequest, Pipeline pipeline, String requestUrl, String contentType)
    {
        File resourceFile = new File(dir, requestUrl);
        if (resourceFile.exists())
        {
            try (InputStream inputStream = new FileInputStream(resourceFile))
            {
                byte[] bytes = IoUtil.readAllBytes(inputStream);
                httpRequest.close();
                HttpResponse response = new HttpResponse();
                response.setContentType(contentType);
                response.setBytes_body(bytes);
                pipeline.fireWrite(response);
            }
            catch (IOException e)
            {
                throw new RuntimeException("读取文件地址:" + resourceFile.getAbsolutePath() + "出现异常", e);
            }
        }
        else
        {
            httpRequest.close();
            HttpResponse response = new HttpResponse();
            response.setBytes_body(STR.format("not available path:{},not find in :{}", httpRequest.getUrl(), resourceFile.getAbsolutePath()).getBytes(StandardCharsets.UTF_8));
            response.setContentType("text/html;charset=utf-8");
            pipeline.fireWrite(response);
        }
    }

    private static boolean isAbsolutePath(String path)
    {
        char c = path.charAt(0);
        //这个地址是绝对路径
        return c == '/' || (c >= 'a' && c <= 'z' && path.charAt(1) == ':') || (c >= 'A' && c <= 'Z' && path.charAt(1) == ':');
    }


}
