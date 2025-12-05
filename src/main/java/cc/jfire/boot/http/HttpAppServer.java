package cc.jfire.boot.http;

import cc.jfire.baseutil.RuntimeJVM;
import cc.jfire.baseutil.StringUtil;
import cc.jfire.jfire.core.ApplicationContext;
import cc.jfire.jnet.common.api.ReadProcessor;
import cc.jfire.jnet.common.util.ChannelConfig;
import cc.jfire.jnet.extend.http.coder.*;
import cc.jfire.jnet.extend.http.dto.HttpRequest;
import cc.jfire.jnet.server.AioServer;
import cc.jfire.boot.forward.path.Path;
import cc.jfire.boot.forward.path.PathRequest;
import cc.jfire.boot.forward.path.PathRequestForwardProcessor;
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
        private ChannelConfig      channelConfig;
        private ApplicationContext context;
        private String             webDir;
        private ReadProcessor<HttpRequest>[] beforeProcessor;
    }

    public static AioServer start(StartParam param)
    {
        ChannelConfig            channelConfig = param.getChannelConfig();
        Map<String, PathRequest> requestMap    = parseFromApplication(param.getContext());
        AioServer aioServer = AioServer.newAioServer(channelConfig, pipeline -> {
            pipeline.addReadProcessor(new HttpRequestDecoder());
            pipeline.addReadProcessor(new OptionsProcessor());
            String                               webDir          = param.getWebDir();
            NotFoundUrlProcessor.NotFoundBarrier notFoundBarrier = null;
            if (StringUtil.isNotBlank(webDir))
            {
                notFoundBarrier = new NotFoundUrlProcessor.NotFoundBarrier();
                pipeline.addReadProcessor(notFoundBarrier);
                pipeline.addReadProcessor(new ResourceProcessor(webDir, RuntimeJVM.detectRunningInJar()==false));
            }
            if (param.getBeforeProcessor() != null)
            {
                for (ReadProcessor<HttpRequest> processor : param.getBeforeProcessor())
                {
                    pipeline.addReadProcessor(processor);
                }
            }
            pipeline.addReadProcessor(new PathRequestForwardProcessor(requestMap));
            if (StringUtil.isNotBlank(webDir))
            {
                pipeline.addReadProcessor(new NotFoundUrlProcessor(notFoundBarrier));
            }
            pipeline.addWriteProcessor(new DataJsonToRespEncoder());
            pipeline.addWriteProcessor(new HttpRespEncoder(pipeline.allocator()));
        });
        aioServer.start();
        return aioServer;
    }

    public static AioServer start(int port, ApplicationContext context)
    {
        return start(new StartParam().setChannelConfig(new ChannelConfig().setPort(port).setChannelGroup(ChannelConfig.DEFAULT_CHANNEL_GROUP))//
                                     .setContext(context));
    }

    public static AioServer start(int port, ApplicationContext context, ReadProcessor<HttpRequest>... before)
    {
        return start(new StartParam().setChannelConfig(new ChannelConfig().setPort(port).setChannelGroup(ChannelConfig.DEFAULT_CHANNEL_GROUP))//
                                     .setContext(context)//
                                     .setBeforeProcessor(before));
    }

    public static AioServer start(int port, ApplicationContext context, String webDir, ReadProcessor<HttpRequest>... before)
    {
        return start(new StartParam().setContext(context).setChannelConfig(new ChannelConfig().setPort(port).setChannelGroup(ChannelConfig.DEFAULT_CHANNEL_GROUP))//
                                     .setWebDir(webDir)//
                                     .setBeforeProcessor(before));
    }

    private static Map<String, PathRequest> parseFromApplication(ApplicationContext context)
    {
        context.makeAvailable();
        return context.getAllBeanRegisterInfos().stream()//
                      .flatMap(beanRegisterInfo -> Arrays.stream(beanRegisterInfo.getType().getDeclaredMethods()))//
                      .filter(method -> method.isAnnotationPresent(Path.class))//
                      .map(method -> new PathRequest(method, context.getBeanRegisterInfo(method.getDeclaringClass()).get().getBean()))//
                      .collect(Collectors.toMap(PathRequest::getPath, Function.identity()));
    }
}
