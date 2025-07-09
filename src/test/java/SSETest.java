import com.jfirer.baseutil.Resource;
import com.jfirer.jfire.core.ApplicationContext;
import com.jfirer.jfire.core.AwareContextInited;
import com.jfirer.jfire.core.prepare.annotation.EnableAutoConfiguration;
import com.jfirer.jfire.core.prepare.annotation.configuration.Configuration;
import com.jfirer.jfirer.boot.forward.path.Path;
import com.jfirer.jfirer.boot.http.HttpAppServer;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.extend.http.dto.HttpRespBody;
import com.jfirer.jnet.extend.http.dto.HttpRespHead;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.locks.LockSupport;

@EnableAutoConfiguration
@Configuration
@Resource
@Slf4j
public class SSETest implements AwareContextInited
{
    @SneakyThrows
    @Path("/sse")
    public void sse(Pipeline pipeline)
    {
        Thread.startVirtualThread(() -> {
            HttpRespHead httpRespHead = new HttpRespHead().addHeader("Content-Type", "text/event-stream");
            httpRespHead.addHeader("Cache-Control", "no-cache");
            httpRespHead.addHeader("Connection", "keep-alive");
            pipeline.fireWrite(httpRespHead);
            String[] array = new String[]{"现在", "正在", "发送", "sse"};
            for (String s : array)
            {
                try
                {
                    Thread.sleep(5000);
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }
                if (pipeline.isOpen() == false)
                {
                    System.out.println("关闭，不需要后续输出");
                    return;
                }
                else
                {
                    System.out.println("输出:" + s);
                    HttpRespBody body = new HttpRespBody();
                    body.setBodyText("data:" + s + "\n\n");
                    pipeline.fireWrite(body);
                }
            }
        });
    }

    @Test
    @Ignore
    public void test()
    {
        ApplicationContext boot = ApplicationContext.boot(SSETest.class);
        boot.getBean(SSETest.class);
        LockSupport.park();
    }

    @Override
    public void aware(ApplicationContext applicationContext)
    {
        new HttpAppServer().start(80, applicationContext, "web");
        log.debug("启动成功");
    }
}
