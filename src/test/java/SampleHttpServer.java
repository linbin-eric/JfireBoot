import com.jfirer.jfirer.boot.http.OptionsProcessor;
import com.jfirer.jfirer.boot.http.ResponseDataToHttpResponse;
import com.jfirer.jnet.common.api.ReadProcessor;
import com.jfirer.jnet.common.api.ReadProcessorNode;
import com.jfirer.jnet.common.util.ChannelConfig;
import com.jfirer.jnet.extend.http.decode.*;
import com.jfirer.jnet.extend.http.dto.FullHttpResp;
import com.jfirer.jnet.server.AioServer;
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
        try (InputStream fis = Thread.currentThread().getContextClassLoader().getResourceAsStream("server.keystore"))
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
            SSLEncoder sslEncoder = new SSLEncoder();
            try
            {
                sslEngine.beginHandshake();
            }
            catch (SSLException e)
            {
                log.error("异常", e);
                throw new RuntimeException(e);
            }
            SSLDecoder sslRequestDecoder = new SSLDecoder(sslEngine, sslEncoder);
            pipeline.addReadProcessor(sslRequestDecoder);
            pipeline.addReadProcessor(new HttpRequestDecoder());
            pipeline.addReadProcessor(new OptionsProcessor());
            pipeline.addReadProcessor(new ReadProcessor<HttpRequest>()
            {
                @Override
                public void read(HttpRequest data, ReadProcessorNode next)
                {
                    data.close();
                    FullHttpResp resp = new FullHttpResp();
                    resp.getHead().addHeader("content-type", "text/html");
                    resp.getBody().setBodyText("hello y");
                    next.pipeline().fireWrite(resp);
                }
            });
            pipeline.addWriteProcessor(new ResponseDataToHttpResponse());
            pipeline.addWriteProcessor(new HttpRespEncoder(pipeline.allocator()));
            pipeline.addWriteProcessor(sslEncoder);
        });
        aioServer.start();
    }
}
