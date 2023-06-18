package com.jfirer.jfirer.boot.http;

import com.jfirer.jnet.common.api.ReadProcessor;
import com.jfirer.jnet.common.api.ReadProcessorNode;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import com.jfirer.jnet.extend.http.decode.HttpResponse;

public class OptionsProcessor implements ReadProcessor<HttpRequest>
{
    @Override
    public void read(HttpRequest request, ReadProcessorNode next)
    {
        if (request.getMethod().equalsIgnoreCase("options"))
        {
            HttpResponse response = new HttpResponse();
            next.pipeline().fireWrite(response);
        }
        else
        {
            next.fireRead(request);
        }
    }
}
