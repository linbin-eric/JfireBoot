package com.jfirer.jfirer.boot.netServer;

import com.jfirer.baseutil.RuntimeJVM;
import com.jfirer.jfirer.boot.http.OptionsProcessor;
import com.jfirer.jfirer.boot.netServer.config.ResourceConfig;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.common.util.ChannelConfig;
import com.jfirer.jnet.extend.http.decode.HttpRequestDecoder;
import com.jfirer.jnet.extend.http.decode.HttpRespEncoder;
import com.jfirer.jnet.server.AioServer;

import java.util.function.Consumer;

public class HttpProxyServer
{
    private          int              port;
    private volatile ResourceConfig[] configs;

    public HttpProxyServer(int port, ResourceConfig[] configs)
    {
        this.port    = port;
        this.configs = configs;
    }

    public void start()
    {
        if (RuntimeJVM.getDirOfMainClass() == null)
        {
            throw new NullPointerException("Main Class not register");
        }
        ChannelConfig channelConfig = new ChannelConfig();
        channelConfig.setChannelGroup(ChannelConfig.DEFAULT_CHANNEL_GROUP);
        channelConfig.setPort(port);
        Consumer<Pipeline> s = pipeline -> {
            pipeline.addReadProcessor(new HttpRequestDecoder());
            pipeline.addReadProcessor(new OptionsProcessor());
            pipeline.addReadProcessor(new TransferProcessor(configs));
            pipeline.addWriteProcessor(new HttpRespEncoder(pipeline.allocator()));
        };
        AioServer aioServer = AioServer.newAioServer(channelConfig, s::accept);
        aioServer.start();
    }

    public void resetConfigs(ResourceConfig[] configs)
    {
        this.configs = configs;
    }
}
