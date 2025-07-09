package com.jfirer.jfirer.boot.http;

import com.jfirer.baseutil.StringUtil;
import com.jfirer.jfire.core.ApplicationContext;
import com.jfirer.jfirer.boot.forward.path.Path;
import com.jfirer.jfirer.boot.forward.path.PathRequest;
import com.jfirer.jfirer.boot.forward.path.PathRequestForwardProcessor;
import com.jfirer.jnet.common.api.ReadProcessor;
import com.jfirer.jnet.common.util.ChannelConfig;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import com.jfirer.jnet.extend.http.decode.HttpRequestDecoder;
import com.jfirer.jnet.extend.http.decode.HttpRespEncoder;
import com.jfirer.jnet.server.AioServer;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HttpAppServer
{
    @Data
    @Accessors(chain = true)
    public static class StartParam
    {
        private ChannelConfig                channelConfig;
        private Map<String, PathRequest>     requestMap;
        private String                       webDir;
        private ReadProcessor<HttpRequest>[] beforeProcessor;
    }

    public void start(StartParam param)
    {
        ChannelConfig channelConfig = param.getChannelConfig();
        AioServer aioServer = AioServer.newAioServer(channelConfig, pipeline -> {
            pipeline.addReadProcessor(new HttpRequestDecoder());
            pipeline.addReadProcessor(new OptionsProcessor());
            String webDir = param.getWebDir();
            if (StringUtil.isNotBlank(webDir))
            {
                pipeline.addReadProcessor(new ResourceProcessor(webDir));
            }
            if (param.getBeforeProcessor() != null)
            {
                for (ReadProcessor<HttpRequest> processor : param.getBeforeProcessor())
                {
                    pipeline.addReadProcessor(processor);
                }
            }
            pipeline.addReadProcessor(new PathRequestForwardProcessor(param.getRequestMap()));
            if (StringUtil.isNotBlank(webDir))
            {
                pipeline.addReadProcessor(new NotFoundUrlProcessor(new ResourceProcessor(webDir)));
            }
            pipeline.addWriteProcessor(new ResponseDataToHttpResponse());
            pipeline.addWriteProcessor(new HttpRespEncoder(pipeline.allocator()));
        });
        aioServer.start();
    }

    public void start(ChannelConfig channelConfig, Map<String, PathRequest> requestMap, String webDir)
    {
        AioServer aioServer = AioServer.newAioServer(channelConfig, pipeline -> {
            pipeline.addReadProcessor(new HttpRequestDecoder());
            pipeline.addReadProcessor(new OptionsProcessor());
            pipeline.addReadProcessor(new ResourceProcessor(webDir));
            pipeline.addReadProcessor(new PathRequestForwardProcessor(requestMap));
            pipeline.addReadProcessor(new NotFoundUrlProcessor(new ResourceProcessor(webDir)));
            pipeline.addWriteProcessor(new ResponseDataToHttpResponse());
            pipeline.addWriteProcessor(new HttpRespEncoder(pipeline.allocator()));
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
            pipeline.addWriteProcessor(new HttpRespEncoder(pipeline.allocator()));
        });
        aioServer.start();
    }

    public void start(int port, ApplicationContext context)
    {
        start(new StartParam().setChannelConfig(new ChannelConfig().setPort(port).setChannelGroup(ChannelConfig.DEFAULT_CHANNEL_GROUP))//
                              .setRequestMap(parseFromApplication(context)));
    }

    public void start(int port, ApplicationContext context, String webDir)
    {
        start(new StartParam().setChannelConfig(new ChannelConfig().setPort(port).setChannelGroup(ChannelConfig.DEFAULT_CHANNEL_GROUP))//
                              .setRequestMap(parseFromApplication(context))//
                              .setWebDir(webDir));
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
