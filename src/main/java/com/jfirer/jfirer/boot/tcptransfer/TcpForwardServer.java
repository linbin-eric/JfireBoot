package com.jfirer.jfirer.boot.tcptransfer;

import com.jfirer.jfireel.expression.format.MinusIndentAndSingleLineToken;
import com.jfirer.jnet.client.ClientChannel;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.common.api.ReadProcessor;
import com.jfirer.jnet.common.api.ReadProcessorNode;
import com.jfirer.jnet.common.buffer.buffer.IoBuffer;
import com.jfirer.jnet.common.util.ChannelConfig;
import com.jfirer.jnet.server.AioServer;

import java.nio.channels.ClosedChannelException;

public class TcpForwardServer
{
    static boolean useVirtualThread = Integer.parseInt(System.getProperty("java.specification.version")) >= 21;

    public static void start(String localIp, int localPort, String destIp, int destPort)
    {
        ChannelConfig channelConfig = new ChannelConfig();
        channelConfig.setIp(localIp);
        channelConfig.setPort(localPort);
        channelConfig.setWorkerGroup(ChannelConfig.DEFAULT_WORKER_GROUP);
        channelConfig.setChannelGroup(ChannelConfig.DEFAULT_CHANNEL_GROUP);
        ChannelConfig remoteConfig = new ChannelConfig();
        remoteConfig.setIp(destIp);
        remoteConfig.setPort(destPort);
        remoteConfig.setChannelGroup(ChannelConfig.DEFAULT_CHANNEL_GROUP);
        remoteConfig.setWorkerGroup(ChannelConfig.DEFAULT_WORKER_GROUP);
        AioServer aioServer = AioServer.newAioServer(channelConfig, channelContext -> {
            Pipeline pipeline = channelContext.pipeline();
            pipeline.addReadProcessor(new TcpForwardHandler(remoteConfig));
        }, useVirtualThread);
        aioServer.start();
    }

    private static class TcpForwardHandler implements ReadProcessor<IoBuffer>
    {
        private ChannelConfig channelConfig;
        private ClientChannel remoteChannel;

        public TcpForwardHandler(ChannelConfig channelConfig)
        {
            this.channelConfig = channelConfig;
        }

        @Override
        public void read(IoBuffer ioBuffer, ReadProcessorNode readProcessorNode)
        {
            try
            {
                remoteChannel.write(ioBuffer);
            }
            catch (ClosedChannelException e)
            {
                readProcessorNode.pipeline().channelContext().close();
            }
        }

        @Override
        public void pipelineComplete(ReadProcessorNode next)
        {
            System.out.println("有链接进入");
            Pipeline localPipeline = next.pipeline();
            remoteChannel = ClientChannel.newClient(channelConfig, channelContext -> {
                channelContext.pipeline().addReadProcessor(new ReadProcessor<IoBuffer>()
                {
                    @Override
                    public void read(IoBuffer data, ReadProcessorNode next)
                    {
                        localPipeline.fireWrite(data);
                    }

                    @Override
                    public void pipelineComplete(ReadProcessorNode next)
                    {
                    }

                    @Override
                    public void channelClose(ReadProcessorNode next, Throwable e)
                    {
                        localPipeline.channelContext().close();
                    }
                });
            }, useVirtualThread);
            try
            {
                if (remoteChannel.connect())
                {
                    ;
                }
                else
                {
                    localPipeline.channelContext().close();
                    System.out.println("客户端链接失败");
                }
            }
            catch (Throwable e)
            {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args)
    {
        TcpForwardServer.start("127.0.0.1", 5001, "eagle.dddd.zone", 443);
    }
}
