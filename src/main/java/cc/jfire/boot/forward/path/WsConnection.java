package cc.jfire.boot.forward.path;

import cc.jfire.jnet.common.api.Pipeline;
import cc.jfire.jnet.extend.websocket.dto.WebSocketFrame;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.function.BiConsumer;

@Data
@Accessors(chain = true)
public class WsConnection
{
    private Pipeline                             pipeline;
    //该值需要用户注入
    private BiConsumer<WebSocketFrame, Pipeline> biConsumer;

    public void accept(WebSocketFrame frame)
    {
        biConsumer.accept(frame, pipeline);
    }

    public void send(WebSocketFrame frame)
    {
        pipeline.fireWrite(frame);
    }
}
