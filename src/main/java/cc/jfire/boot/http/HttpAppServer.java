package cc.jfire.boot.http;

import cc.jfire.baseutil.RuntimeJVM;
import cc.jfire.baseutil.StringUtil;
import cc.jfire.boot.forward.path.HttpMethod;
import cc.jfire.boot.forward.path.Path;
import cc.jfire.boot.forward.path.PathRequest;
import cc.jfire.boot.forward.path.PathRequestForwardProcessor;
import cc.jfire.jfire.core.ApplicationContext;
import cc.jfire.jnet.common.api.ReadProcessor;
import cc.jfire.jnet.common.util.ChannelConfig;
import cc.jfire.jnet.extend.http.coder.HttpRequestAggregator;
import cc.jfire.jnet.extend.http.coder.HttpRespEncoder;
import cc.jfire.jnet.extend.http.coder.OptionsProcessor;
import cc.jfire.jnet.extend.http.coder.ResourceProcessor;
import cc.jfire.jnet.extend.websocket.coder.WebSocketFrameDecoder;
import cc.jfire.jnet.extend.websocket.coder.WebSocketFrameEncoder;
import cc.jfire.jnet.extend.websocket.coder.WebSocketUpgradeDecoder;
import cc.jfire.jnet.server.AioServer;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        private ReadProcessor<Object>   webSocketProcessor;
    }

    public static AioServer start(StartParam param)
    {
        ChannelConfig            channelConfig = param.getChannelConfig();
        Map<String, PathRequest> requestMap    = parseFromApplication(param.getContext());
        AioServer aioServer = AioServer.newAioServer(channelConfig, pipeline -> {
            // 替换 HttpRequestPartDecoder 为 WebSocketUpgradeDecoder，支持 WebSocket 升级
            pipeline.addReadProcessor(new WebSocketUpgradeDecoder());
            pipeline.addReadProcessor(new HttpRequestAggregator());
            // 添加 WebSocketFrameDecoder（仅当配置了 WebSocket 处理器时）
            if (param.getWebSocketProcessor() != null)
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
            // 添加用户自定义的 WebSocket 处理器
            if (param.getWebSocketProcessor() != null)
            {
                pipeline.addReadProcessor(param.getWebSocketProcessor());
            }
            pipeline.addReadProcessor(new PathRequestForwardProcessor(requestMap));
            if (StringUtil.isNotBlank(webDir))
            {
                pipeline.addReadProcessor(new NotFoundUrlProcessor(notFoundBarrier));
            }
            pipeline.addWriteProcessor(new DataJsonToRespEncoder());
            // 添加 WebSocketFrameEncoder（仅当配置了 WebSocket 处理器时）
            if (param.getWebSocketProcessor() != null)
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

    public static AioServer start(int port, ApplicationContext context, String webDir, ReadProcessor<Object>[] beforeProcessors, ReadProcessor<Object> webSocketProcessor)
    {
        return start(new StartParam().setContext(context).setChannelConfig(new ChannelConfig().setPort(port).setChannelGroup(ChannelConfig.DEFAULT_CHANNEL_GROUP)).setWebDir(webDir).setBeforeProcessor(beforeProcessors).setWebSocketProcessor(webSocketProcessor));
    }

    private static Map<String, PathRequest> parseFromApplication(ApplicationContext context)
    {
        context.makeAvailable();
        // 除 ALL 外的所有具体 HTTP 方法
        HttpMethod[] allConcreteMethods = {HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.HEAD, HttpMethod.OPTIONS};
        return context.getAllBeanRegisterInfos().stream()//
                      .flatMap(beanRegisterInfo -> Arrays.stream(beanRegisterInfo.getType().getDeclaredMethods()))//
                      .filter(method -> method.isAnnotationPresent(Path.class))//
                      .flatMap(method -> {
                          Object       host        = context.getBeanRegisterInfo(method.getDeclaringClass()).get().getBean();
                          Path         pathAnno    = method.getAnnotation(Path.class);
                          String       pathValue   = pathAnno.value();
                          HttpMethod[] httpMethods = pathAnno.method();
                          boolean      containsAll = Arrays.asList(httpMethods).contains(HttpMethod.ALL);
                          // RESTful 路径（包含 ${）
                          if (pathValue.contains("${"))
                          {
                              if (containsAll)
                              {
                                  // 包含 ALL，只生成一个 PathRequest
                                  return Stream.of(new PathRequest(method, host, HttpMethod.ALL));
                              }
                              // 不包含 ALL，按数组展开
                              return Arrays.stream(httpMethods).map(httpMethod -> new PathRequest(method, host, httpMethod));
                          }
                          // 非 RESTful 路径：展开 method 数组，ALL 展开为所有具体方法
                          return Arrays.stream(httpMethods)
                                       .flatMap(httpMethod -> {
                                           if (httpMethod == HttpMethod.ALL)
                                           {
                                               return Arrays.stream(allConcreteMethods);
                                           }
                                           return Stream.of(httpMethod);
                                       })
                                       .map(httpMethod -> new PathRequest(method, host, httpMethod));
                      })//
                      .collect(Collectors.toMap(PathRequest::getRouteKey, Function.identity()));
    }
}
