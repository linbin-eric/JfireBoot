package cc.jfire.boot.http;

import cc.jfire.baseutil.RuntimeJVM;
import cc.jfire.baseutil.StringUtil;
import cc.jfire.boot.forward.path.Path;
import cc.jfire.boot.forward.path.PathRequest;
import cc.jfire.boot.forward.path.PathRequestForwardProcessor;
import cc.jfire.boot.forward.path.Ws;
import cc.jfire.jfire.core.ApplicationContext;
import cc.jfire.jfire.core.bean.BeanRegisterInfo;
import cc.jfire.jnet.common.api.ReadProcessor;
import cc.jfire.jnet.common.util.ChannelConfig;
import cc.jfire.jnet.extend.http.coder.*;
import cc.jfire.jnet.extend.websocket.coder.WebSocketFrameDecoder;
import cc.jfire.jnet.extend.websocket.coder.WebSocketFrameEncoder;
import cc.jfire.jnet.extend.websocket.coder.WebSocketUpgradeDecoder;
import cc.jfire.jnet.server.AioServer;
import lombok.Data;
import lombok.experimental.Accessors;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class HttpAppServer
{
    @Data
    @Accessors(chain = true)
    public static class StartParam
    {
        private ChannelConfig           channelConfig;
        private ApplicationContext      context;
        private String                  webDir;
        private ReadProcessor<Object>[] beforeProcessor;
    }

    public static AioServer start(StartParam param)
    {
        ChannelConfig     channelConfig = param.getChannelConfig();
        List<PathRequest> parseResult   = parseFromApplication(param.getContext());
        boolean           hasWs         = parseResult.stream().anyMatch(pathRequest -> pathRequest.isWs());
        AioServer aioServer = AioServer.newAioServer(channelConfig, pipeline -> {
            if (hasWs)
            {
                pipeline.addReadProcessor(new WebSocketUpgradeDecoder());
            }
            else
            {
                pipeline.addReadProcessor(new HttpRequestPartDecoder());
            }
            pipeline.addReadProcessor(new HttpRequestAggregator(hasWs));
            if (hasWs)
            {
                pipeline.addReadProcessor(new WebSocketFrameDecoder(true));
            }
            pipeline.addReadProcessor(new OptionsProcessor());
            String                               webDir          = param.getWebDir();
            NotFoundUrlProcessor.NotFoundBarrier notFoundBarrier = null;
            if (StringUtil.isNotBlank(webDir))
            {
                notFoundBarrier = new NotFoundUrlProcessor.NotFoundBarrier();
                pipeline.addReadProcessor(notFoundBarrier);
                pipeline.addReadProcessor(new ResourceProcessor(webDir, RuntimeJVM.detectRunningInJar() == false));
            }
            if (param.getBeforeProcessor() != null)
            {
                for (ReadProcessor<Object> processor : param.getBeforeProcessor())
                {
                    pipeline.addReadProcessor(processor);
                }
            }
            pipeline.addReadProcessor(new PathRequestForwardProcessor(parseResult));
            if (StringUtil.isNotBlank(webDir))
            {
                pipeline.addReadProcessor(new NotFoundUrlProcessor(notFoundBarrier));
            }
            pipeline.addWriteProcessor(new DataJsonToRespEncoder());
            if (hasWs)
            {
                pipeline.addWriteProcessor(new WebSocketFrameEncoder(pipeline.allocator(), false));
            }
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

    public static AioServer start(int port, ApplicationContext context, ReadProcessor<Object>... before)
    {
        return start(new StartParam().setChannelConfig(new ChannelConfig().setPort(port).setChannelGroup(ChannelConfig.DEFAULT_CHANNEL_GROUP))//
                                     .setContext(context)//
                                     .setBeforeProcessor(before));
    }

    public static AioServer start(int port, ApplicationContext context, String webDir, ReadProcessor<Object>... before)
    {
        return start(new StartParam().setContext(context).setChannelConfig(new ChannelConfig().setPort(port).setChannelGroup(ChannelConfig.DEFAULT_CHANNEL_GROUP))//
                                     .setWebDir(webDir)//
                                     .setBeforeProcessor(before));
    }

    private static List<PathRequest> parseFromApplication(ApplicationContext context)
    {
        context.makeAvailable();
        List<PathRequest> pathRequests = new ArrayList<>();
        for (BeanRegisterInfo beanRegisterInfo : context.getAllBeanRegisterInfos())
        {
            for (Method method : beanRegisterInfo.getType().getDeclaredMethods())
            {
                if (method.isAnnotationPresent(Path.class) || method.isAnnotationPresent(Ws.class))
                {
                    Object instance = beanRegisterInfo.get().getBean();
                    pathRequests.add(new PathRequest(method, instance));
                }
                else
                {
                    ;
                }
            }
        }
        return pathRequests;
    }
}
