package com.jfirer.jfirer.boot.http;

import com.jfirer.dson.Dson;
import com.jfirer.jnet.common.api.WriteProcessor;
import com.jfirer.jnet.common.api.WriteProcessorNode;
import com.jfirer.jnet.common.buffer.buffer.IoBuffer;
import com.jfirer.jnet.common.util.DataIgnore;
import com.jfirer.jnet.extend.http.dto.FullHttpResp;
import com.jfirer.jnet.extend.http.dto.HttpRespPart;

public class DataJsonToRespEncoder implements WriteProcessor<Object>
{
    @Override
    public void write(Object data, WriteProcessorNode next)
    {
        if (data instanceof HttpRespPart || data instanceof IoBuffer || data instanceof DataIgnore)
        {
            next.fireWrite(data);
        }
        else
        {
            FullHttpResp fullHttpResp = new FullHttpResp();
            fullHttpResp.getBody().setBodyText(Dson.toJson(data));
            next.fireWrite(fullHttpResp);
        }
    }
}
