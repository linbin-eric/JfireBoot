package cc.jfire.boot.forward.path;

import cc.jfire.jnet.common.api.Pipeline;
import cc.jfire.jnet.extend.websocket.coder.WebSocketFrameDecoder;
import cc.jfire.jnet.extend.websocket.dto.WebSocketFrame;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.function.BiConsumer;

@Data
@Accessors(chain = true)
public class WsConnection
{
    private Pipeline                             pipeline;
    /**
     * 控制类的报文底层都有处理了。比如：
     * 1、客户端发送的 ping 报文，协议层会响应 pong 报文。
     * 2、客户端发送的 close 报文，协议层会响应 close 报文。
     * 3、客户端发送的 pong 报文，协议层不会自动处理。
     * 以上三种报文，都会在 biConsumer 也收到，但是程序是可以忽略的。
     */
    private BiConsumer<WebSocketFrame, Pipeline> biConsumer;

    public void accept(WebSocketFrame frame)
    {
        biConsumer.accept(frame, pipeline);
    }

    public void send(WebSocketFrame frame)
    {
        pipeline.fireWrite(frame);
    }

    public void sendClose(String reason)
    {
        WebSocketFrame close = WebSocketFrame.createClose(1000, reason);
        pipeline.putPersistenceStore(WebSocketFrameDecoder.ACTIVE_SEND_CLOSE_KEY, true);
        pipeline.fireWrite(close);
    }

    public void sendClose()
    {
        sendClose("close");
    }
}
