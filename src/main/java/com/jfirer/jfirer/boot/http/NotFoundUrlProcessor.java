package com.jfirer.jfirer.boot.http;

import com.jfirer.jnet.common.api.ReadProcessor;
import com.jfirer.jnet.common.api.ReadProcessorNode;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import com.jfirer.jnet.extend.http.dto.FullHttpResp;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class NotFoundUrlProcessor implements ReadProcessor<HttpRequest>
{
    private final NotFoundBarrier barrier;

    @Override
    public void read(HttpRequest data, ReadProcessorNode next)
    {
        String url      = data.getUrl();
        String purePath = url.contains("?") ? url.substring(0, url.indexOf("?")) : url;
        barrier.notAvailablePaths.add(purePath);
        FullHttpResp response = new FullHttpResp();
        response.getHead().setResponseCode(404);
        response.getBody().setBodyText("notAvailable path:" + purePath);
        next.pipeline().fireWrite(response);
    }

    public static class NotFoundBarrier implements ReadProcessor<HttpRequest>
    {
        private Set<String> notAvailablePaths = new HashSet<>();

        @Override
        public void read(HttpRequest request, ReadProcessorNode next)
        {
            String url      = request.getUrl();
            String purePath = url.contains("?") ? url.substring(0, url.indexOf("?")) : url;
            if (notAvailablePaths.contains(purePath))
            {
                FullHttpResp response = new FullHttpResp();
                response.getHead().setResponseCode(404);
                response.getBody().setBodyText("notAvailable path:" + purePath);
                next.pipeline().fireWrite(response);
            }
            else
            {
                next.fireRead(request);
            }
        }
    }
}
