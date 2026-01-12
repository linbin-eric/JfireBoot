package cc.jfire.boot.http;

import cc.jfire.dson.Dson;
import cc.jfire.jnet.common.api.WriteProcessor;
import cc.jfire.jnet.common.api.WriteProcessorNode;
import cc.jfire.jnet.common.buffer.buffer.IoBuffer;
import cc.jfire.jnet.common.util.DataIgnore;
import cc.jfire.jnet.extend.http.dto.HttpResponse;
import cc.jfire.jnet.extend.http.dto.HttpResponsePart;
import cc.jfire.jnet.extend.websocket.dto.WebSocketFrame;

public class DataJsonToRespEncoder implements WriteProcessor<Object>
{
    @Override
    public void write(Object data, WriteProcessorNode next)
    {
        if (data instanceof HttpResponsePart || data instanceof HttpResponse || data instanceof IoBuffer || data instanceof DataIgnore || data instanceof WebSocketFrame)
        {
            next.fireWrite(data);
        }
        else
        {
            HttpResponse httpResponse = new HttpResponse();
            httpResponse.setBodyText(Dson.toJson(data));
            next.fireWrite(httpResponse);
        }
    }
}
