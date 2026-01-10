import cc.jfire.baseutil.Resource;
import cc.jfire.baseutil.RuntimeJVM;
import cc.jfire.boot.forward.path.Path;
import cc.jfire.boot.http.HttpAppServer;
import cc.jfire.jfire.core.ApplicationContext;
import cc.jfire.jfire.core.AwareContextInited;
import cc.jfire.jfire.core.prepare.annotation.EnableAutoConfiguration;
import cc.jfire.jfire.core.prepare.annotation.configuration.Configuration;
import cc.jfire.jnet.common.api.Pipeline;
import cc.jfire.jnet.common.buffer.buffer.IoBuffer;
import cc.jfire.jnet.extend.http.dto.HttpResponsePartHead;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
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
            HttpResponsePartHead httpRespHead = new HttpResponsePartHead();
            httpRespHead.addHeader("Content-Type", "text/event-stream");
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
                    IoBuffer allocate = pipeline.allocator().allocate(100);
                    allocate.put(("data:" + s + "\n\n").getBytes(StandardCharsets.UTF_8));
                    pipeline.fireWrite(allocate);
                }
            }
        });
    }

//    @Test
//    @Ignore
    public static void main(String[] args)
    {
        RuntimeJVM.registerMainClass();
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
