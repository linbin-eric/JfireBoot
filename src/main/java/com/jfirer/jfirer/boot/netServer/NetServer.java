package com.jfirer.jfirer.boot.netServer;

import com.jfirer.jfirer.boot.http.OptionsProcessor;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.common.internal.DefaultWorkerGroup;
import com.jfirer.jnet.common.util.ChannelConfig;
import com.jfirer.jnet.extend.http.decode.HttpRequestDecoder;
import com.jfirer.jnet.extend.http.decode.HttpResponseEncoder;
import com.jfirer.jnet.server.AioServer;

public class NetServer
{
    private int                          port;
    private Class<?>                     rootClass;
    private TransferProcessor.Location[] locations;

    public NetServer(int port, Class<?> rootClass, TransferProcessor.Location[] locations)
    {
        this.port      = port;
        this.rootClass = rootClass;
        this.locations = locations;
    }

    public void start()
    {
        ChannelConfig channelConfig = new ChannelConfig();
        channelConfig.setChannelGroup(ChannelConfig.DEFAULT_CHANNEL_GROUP);
        channelConfig.setWorkerGroup(new DefaultWorkerGroup(Runtime.getRuntime().availableProcessors(), "netServer-worker-"));
        channelConfig.setPort(port);
        AioServer aioServer = new AioServer(channelConfig, channelContext ->
        {
            Pipeline pipeline = channelContext.pipeline();
            pipeline.addReadProcessor(new HttpRequestDecoder(channelConfig.getAllocator()));
            pipeline.addReadProcessor(new OptionsProcessor());
            pipeline.addReadProcessor(new TransferProcessor(rootClass, locations));
            pipeline.addWriteProcessor(new HttpResponseEncoder(channelConfig.getAllocator()));
        });
        aioServer.start();
    }
}
