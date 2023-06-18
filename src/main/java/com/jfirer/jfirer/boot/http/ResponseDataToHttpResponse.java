package com.jfirer.jfirer.boot.http;

import com.jfirer.dson.Dson;
import com.jfirer.jnet.common.api.WriteProcessor;
import com.jfirer.jnet.common.api.WriteProcessorNode;
import com.jfirer.jnet.extend.http.decode.HttpResponse;

public class ResponseDataToHttpResponse implements WriteProcessor<Object>
{
    @Override
    public void write(Object data, WriteProcessorNode next)
    {
        HttpResponse response;
        if (data instanceof HttpResponse)
        {
            response = (HttpResponse) data;
        }
        else
        {
            response = new HttpResponse();
            response.setBody(Dson.toJson(data));
        }
        response.getHeaders().put("Access-Control-Allow-Origin", "*");
        response.getHeaders().put("Access-Control-Allow-Credentials", "true");
        response.getHeaders().put("allow", "GET,PUT,POST,HEAD");
        response.getHeaders().put("access-control-allow-methods", "GET,PUT,POST,HEAD");
        response.getHeaders().put("Access-Control-Max-Age", "86400");
        response.getHeaders().put("Access-Control-Allow-Headers", "authorization,Authorization,DNT,X-CustomHeader,Keep-Alive,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type");
        next.fireWrite(response);
    }
}
