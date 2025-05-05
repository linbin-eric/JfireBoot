package com.jfirer.jfirer.boot.netServer.config.impl.proxy;

import com.jfirer.baseutil.IoUtil;
import com.jfirer.baseutil.RuntimeJVM;
import com.jfirer.baseutil.STR;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import com.jfirer.jnet.extend.http.dto.FullHttpResp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class FileResourceHandler extends AbstractIOResourceHandler
{
    private File dir;

    public FileResourceHandler(String prefixMatch, String originPath)
    {
        super(prefixMatch, originPath);
        if (isAbsolutePath(path))
        {
            dir = new File(path);
        }
        else
        {
            File tmp = RuntimeJVM.getDirOfMainClass();
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
                FullHttpResp response = new FullHttpResp();
                response.getHead().addHeader("Content-Type", contentType);
                response.getBody().setBodyBytes(bytes);
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
            FullHttpResp response = new FullHttpResp();
            response.getHead().addHeader("Content-Type", "text/html;charset=utf-8");
            response.getBody().setBodyText(STR.format("not available path:{},not find in :{}", httpRequest.getUrl(), resourceFile.getAbsolutePath()));
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
