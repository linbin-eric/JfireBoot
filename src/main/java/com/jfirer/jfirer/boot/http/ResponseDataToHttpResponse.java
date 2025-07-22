package com.jfirer.jfirer.boot.http;

import com.jfirer.dson.Dson;
import com.jfirer.jnet.common.api.WriteProcessor;
import com.jfirer.jnet.common.api.WriteProcessorNode;
import com.jfirer.jnet.common.buffer.buffer.IoBuffer;
import com.jfirer.jnet.common.util.DataIgnore;
import com.jfirer.jnet.extend.http.dto.FullHttpResp;
import com.jfirer.jnet.extend.http.dto.HttpRespBody;
import com.jfirer.jnet.extend.http.dto.HttpRespHead;

public class ResponseDataToHttpResponse implements WriteProcessor<Object>
{
    @Override
    public void write(Object data, WriteProcessorNode next)
    {
        if (data instanceof HttpRespHead || data instanceof HttpRespBody || data instanceof IoBuffer || data instanceof DataIgnore)
        {
            next.fireWrite(data);
        }
        else if (data instanceof FullHttpResp fullHttpResp)
        {
            HttpRespHead head = fullHttpResp.getHead();
            head.addHeader("Access-Control-Allow-Origin", "*")//
                .addHeader("Access-Control-Allow-Credentials", "true")//
                .addHeader("allow", "GET,PUT,POST,HEAD")//
                .addHeader("access-control-allow-methods", "GET,PUT,POST,HEAD")//
                .addHeader("Access-Control-Max-Age", "86400")//
                .addHeader("Access-Control-Allow-Headers", "*");
            next.fireWrite(data);
        }
        else
        {
            FullHttpResp fullHttpResp = new FullHttpResp();
            HttpRespHead head         = fullHttpResp.getHead();
            head.addHeader("Access-Control-Allow-Origin", "*")//
                .addHeader("Access-Control-Allow-Credentials", "true")//
                .addHeader("allow", "GET,PUT,POST,HEAD")//
                .addHeader("access-control-allow-methods", "GET,PUT,POST,HEAD")//
                .addHeader("Access-Control-Max-Age", "86400")//
                .addHeader("Access-Control-Allow-Headers", "*");
            fullHttpResp.getBody().setBodyText(Dson.toJson(data));
            next.fireWrite(fullHttpResp);
        }
    }
}
