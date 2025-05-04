package com.jfirer.jfirer.boot.http;

import com.jfirer.jfire.core.ApplicationContext;
import com.jfirer.jfirer.boot.forward.path.Path;
import com.jfirer.jfirer.boot.forward.path.PathRequest;
import com.jfirer.jfirer.boot.forward.path.PathRequestForwardProcessor;
import com.jfirer.jnet.common.util.ChannelConfig;
import com.jfirer.jnet.extend.http.decode.HttpRequestDecoder;
import com.jfirer.jnet.extend.http.decode.HttpResponseEncoder;
import com.jfirer.jnet.server.AioServer;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HttpAppServer
{
    public void start(ChannelConfig channelConfig, Map<String, PathRequest> requestMap, String webDir)
    {
        AioServer aioServer = AioServer.newAioServer(channelConfig, pipeline -> {
            pipeline.addReadProcessor(new HttpRequestDecoder());
            pipeline.addReadProcessor(new OptionsProcessor());
            ResourceProcessor resourceProcessor = new ResourceProcessor(webDir);
            pipeline.addReadProcessor(resourceProcessor);
            pipeline.addReadProcessor(new PathRequestForwardProcessor(requestMap));
            pipeline.addReadProcessor(new NotFoundUrlProcessor(resourceProcessor));
            pipeline.addWriteProcessor(new ResponseDataToHttpResponse());
            pipeline.addWriteProcessor(new HttpResponseEncoder(channelConfig.getAllocator()));
        });
        aioServer.start();
    }

    public void start(ChannelConfig channelConfig, Map<String, PathRequest> requestMap)
    {
        AioServer aioServer = AioServer.newAioServer(channelConfig, pipeline -> {
            pipeline.addReadProcessor(new HttpRequestDecoder());
            pipeline.addReadProcessor(new OptionsProcessor());
            pipeline.addReadProcessor(new PathRequestForwardProcessor(requestMap));
            pipeline.addWriteProcessor(new ResponseDataToHttpResponse());
            pipeline.addWriteProcessor(new HttpResponseEncoder(channelConfig.getAllocator()));
        });
        aioServer.start();
    }

    public void start(int port, Map<String, PathRequest> requestMap)
    {
        start(new ChannelConfig().setPort(port).setChannelGroup(ChannelConfig.DEFAULT_CHANNEL_GROUP).setWorkerGroup(ChannelConfig.DEFAULT_WORKER_GROUP), requestMap);
    }

    public void start(int port, ApplicationContext context)
    {
        start(port, parseFromApplication(context));
    }

    public void start(int port, Map<String, PathRequest> requestMap, String webDir)
    {
        start(new ChannelConfig().setPort(port).setChannelGroup(ChannelConfig.DEFAULT_CHANNEL_GROUP).setWorkerGroup(ChannelConfig.DEFAULT_WORKER_GROUP), requestMap, webDir);
    }

    public void start(int port, ApplicationContext context, String webDir)
    {
        start(port, parseFromApplication(context), webDir);
    }

    public static Map<String, PathRequest> parseFromApplication(ApplicationContext context)
    {
        return context.getAllBeanRegisterInfos().stream()//
                      .flatMap(beanRegisterInfo -> Arrays.stream(beanRegisterInfo.getType().getDeclaredMethods()))//
                      .filter(method -> method.isAnnotationPresent(Path.class))//
                      .map(method -> new PathRequest(method, context.getBeanRegisterInfo(method.getDeclaringClass()).get().getBean()))//
                      .collect(Collectors.toMap(PathRequest::getPath, Function.identity()));
    }
}
