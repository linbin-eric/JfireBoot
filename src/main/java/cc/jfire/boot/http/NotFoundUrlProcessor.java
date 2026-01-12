package cc.jfire.boot.http;

import cc.jfire.jnet.common.api.ReadProcessor;
import cc.jfire.jnet.common.api.ReadProcessorNode;
import cc.jfire.jnet.extend.http.dto.HttpRequest;
import cc.jfire.jnet.extend.http.dto.HttpResponse;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class NotFoundUrlProcessor implements ReadProcessor<Object>
{
    private final NotFoundBarrier barrier;

    @Override
    public void read(Object data, ReadProcessorNode next)
    {
        if (data instanceof HttpRequestExtend requestExtend)
        {
            String purePath = requestExtend.getPath();
            barrier.notAvailablePaths.add(purePath);
            HttpResponse response = new HttpResponse();
            response.getHead().setStatusCode(404);
            response.getHead().setReasonPhrase("Not Found");
            response.setBodyText("notAvailable path:" + purePath);
            requestExtend.getPipeline().fireWrite(response);
        }
        else
        {
            next.fireRead(data);
        }
    }

    public static class NotFoundBarrier implements ReadProcessor<Object>
    {
        private Set<String> notAvailablePaths = new HashSet<>();

        @Override
        public void read(Object data, ReadProcessorNode next)
        {
            if (data instanceof HttpRequest request)
            {
                String purePath = parsePath(request.getHead().getPath());
                if (notAvailablePaths.contains(purePath))
                {
                    HttpResponse response = new HttpResponse();
                    response.getHead().setStatusCode(404);
                    response.getHead().setReasonPhrase("Not Found");
                    response.setBodyText("notAvailable path:" + purePath);
                    request.close();
                    next.pipeline().fireWrite(response);
                }
                else
                {
                    next.fireRead(request);
                }
            }
            else
            {
                next.fireRead(data);
            }
        }

        private String parsePath(String url)
        {
            int index = url.indexOf("?");
            return index == -1 ? url : url.substring(0, index);
        }
    }
}
