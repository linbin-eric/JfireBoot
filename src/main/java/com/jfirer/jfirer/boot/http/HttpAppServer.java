package com.jfirer.jfirer.boot.http;

import com.jfirer.baseutil.StringUtil;
import com.jfirer.jfire.core.ApplicationContext;
import com.jfirer.jfirer.boot.forward.path.Path;
import com.jfirer.jfirer.boot.forward.path.PathRequest;
import com.jfirer.jfirer.boot.forward.path.PathRequestForwardProcessor;
import com.jfirer.jnet.common.api.ReadProcessor;
import com.jfirer.jnet.common.util.ChannelConfig;
import com.jfirer.jnet.extend.http.coder.*;
import com.jfirer.jnet.extend.http.dto.HttpRequest;
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
        private ApplicationContext           context;
        private String                       webDir;
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
                pipeline.addReadProcessor(new ResourceProcessor(webDir));
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
