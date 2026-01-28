import cc.jfire.jnet.common.api.ReadProcessor;
import cc.jfire.jnet.common.api.ReadProcessorNode;
import cc.jfire.jnet.common.util.ChannelConfig;
import cc.jfire.jnet.extend.http.coder.*;
import cc.jfire.jnet.extend.http.dto.HttpRequest;
import cc.jfire.jnet.extend.http.dto.HttpResponse;
import cc.jfire.jnet.server.AioServer;
import cc.jfire.boot.http.DataJsonToRespEncoder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.*;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;

@Slf4j
public class SampleHttpServer
{
    @SneakyThrows
    public static void main(String[] args) throws KeyStoreException
    {
        // 1. 加载密钥库
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (InputStream fis = Thread.currentThread().getContextClassLoader().getResourceAsStream("keystore.jks"))
        {
            keyStore.load(fis, "123456".toCharArray());
        }
        // 2. 初始化 KeyManagerFactory（提供服务端身份）
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "123456".toCharArray());
        // 3. 初始化 TrustManagerFactory（即使不验证客户端身份，也需要）
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore); // 使用相同的 keystore，适合开发环境
        // 4. 初始化 SSLContext
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        // 5. 创建并配置 SSLEngine
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(false); // 服务端模式
        sslEngine.setNeedClientAuth(false); // 不要求客户端认证
        sslEngine.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
        ChannelConfig channelConfig = new ChannelConfig().setPort(8082);
        AioServer aioServer = AioServer.newAioServer(channelConfig, pipeline -> {
            SSLDecoder sslRequestDecoder = new SSLDecoder(sslEngine);
            SSLEncoder sslEncoder = new SSLEncoder(sslEngine);
            try
            {
                sslEngine.beginHandshake();
            }
            catch (SSLException e)
            {
                log.error("异常", e);
                throw new RuntimeException(e);
            }
            pipeline.addReadProcessor(sslRequestDecoder);
            pipeline.addReadProcessor(new HttpRequestPartDecoder());
            pipeline.addReadProcessor(new HttpRequestAggregator());
            pipeline.addReadProcessor(new OptionsProcessor());
            pipeline.addReadProcessor(new ReadProcessor<HttpRequest>()
            {
                @Override
                public void read(HttpRequest data, ReadProcessorNode next)
                {
                    data.close();
                    HttpResponse resp = new HttpResponse();
                    resp.getHead().addHeader("content-type", "text/html");
                    resp.setBodyText("hello y");
                    next.pipeline().fireWrite(resp);
                }
            });
            pipeline.addWriteProcessor(new DataJsonToRespEncoder());
            pipeline.addWriteProcessor(new HttpRespEncoder(pipeline.allocator()));
            pipeline.addWriteProcessor(sslEncoder);
        });
        aioServer.start();
    }
}
