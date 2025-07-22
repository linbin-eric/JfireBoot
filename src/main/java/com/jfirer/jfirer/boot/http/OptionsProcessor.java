package com.jfirer.jfirer.boot.http;

import com.jfirer.jnet.common.api.ReadProcessor;
import com.jfirer.jnet.common.api.ReadProcessorNode;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import com.jfirer.jnet.extend.http.dto.FullHttpResp;

public class OptionsProcessor implements ReadProcessor<HttpRequest>
{
    @Override
    public void read(HttpRequest request, ReadProcessorNode next)
    {
        if (request.getMethod().equalsIgnoreCase("options"))
        {
            FullHttpResp response = new FullHttpResp();
            next.pipeline().fireWrite(response);
        }
        else
        {
            String url = request.getUrl();
            if (url.equals("/favicon.ico") || url.equals("/robots.txt"))
            {
                FullHttpResp response = new FullHttpResp();
                response.getHead().setResponseCode(404);
                next.pipeline().fireWrite(response);
            }
            else
            {
                next.fireRead(request);
            }
        }
    }
}
