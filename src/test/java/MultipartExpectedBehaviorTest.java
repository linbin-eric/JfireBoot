import cc.jfire.boot.forward.path.PathRequestForwardProcessor;
import cc.jfire.boot.http.FilePart;
import cc.jfire.boot.http.HttpRequestExtend;
import cc.jfire.jnet.common.api.Pipeline;
import cc.jfire.jnet.common.api.ReadProcessor;
import cc.jfire.jnet.common.api.ReadProcessorNode;
import cc.jfire.jnet.common.api.WriteListener;
import cc.jfire.jnet.common.api.WriteProcessor;
import cc.jfire.jnet.common.buffer.allocator.BufferAllocator;
import cc.jfire.jnet.common.buffer.allocator.impl.UnPoolBufferAllocator;
import cc.jfire.jnet.common.buffer.buffer.IoBuffer;
import cc.jfire.jnet.common.util.ChannelConfig;
import cc.jfire.jnet.extend.http.dto.HttpRequest;
import cc.jfire.jnet.extend.http.dto.HttpResponse;
import org.junit.Assert;
import org.junit.Test;

import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 修复验收测试：当前未修复状态下应失败，修复后应全部通过。
 */
public class MultipartExpectedBehaviorTest
{
    @Test
    public void standardTextPartShouldBeParsed()
    {
        HttpRequestExtend extend = parse(
                "multipart/form-data; boundary=abc",
                "--abc\r\nContent-Disposition: form-data; name=\"field\"\r\n\r\nvalue\r\n--abc--\r\n"
        );
        try
        {
            Assert.assertEquals("value", extend.getParamMap().get("field"));
        }
        finally
        {
            extend.close();
        }
    }

    @Test
    public void standardFilePartShouldBeParsed()
    {
        HttpRequestExtend extend = parse(
                "multipart/form-data; boundary=abc",
                "--abc\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.txt\"\r\nContent-Type: text/plain\r\n\r\nhello\r\n--abc--\r\n"
        );
        try
        {
            Assert.assertEquals(1, extend.getFileParts().size());
            FilePart filePart = extend.getFileParts().get(0);
            Assert.assertEquals("file", filePart.getFieldName());
            Assert.assertEquals("a.txt", filePart.getFileName());
            Assert.assertEquals("hello", StandardCharsets.UTF_8.decode(filePart.getIoBuffer().readableByteBuffer()).toString());
        }
        finally
        {
            extend.close();
        }
    }

    @Test
    public void quotedBoundaryShouldBeParsed()
    {
        HttpRequestExtend extend = parse(
                "multipart/form-data; boundary=\"abc\"",
                "--abc\r\nContent-Disposition: form-data; name=\"field\"\r\n\r\nvalue\r\n--abc--\r\n"
        );
        try
        {
            Assert.assertEquals("value", extend.getParamMap().get("field"));
        }
        finally
        {
            extend.close();
        }
    }

    @Test
    public void boundaryBeforeOtherParamsShouldBeParsed()
    {
        HttpRequestExtend extend = parse(
                "multipart/form-data; boundary=abc; charset=UTF-8",
                "--abc\r\nContent-Disposition: form-data; name=\"field\"\r\n\r\nvalue\r\n--abc--\r\n"
        );
        try
        {
            Assert.assertEquals("value", extend.getParamMap().get("field"));
        }
        finally
        {
            extend.close();
        }
    }

    @Test
    public void unquotedContentDispositionNameShouldBeParsed()
    {
        HttpRequestExtend extend = parse(
                "multipart/form-data; boundary=abc",
                "--abc\r\nContent-Disposition: form-data; name=field\r\n\r\nvalue\r\n--abc--\r\n"
        );
        try
        {
            Assert.assertEquals("value", extend.getParamMap().get("field"));
        }
        finally
        {
            extend.close();
        }
    }

    @Test
    public void finalBoundaryOnlyShouldBeTolerated()
    {
        HttpRequestExtend extend = parse(
                "multipart/form-data; boundary=abc",
                "--abc--\r\n"
        );
        try
        {
            Assert.assertTrue(extend.getFileParts().isEmpty());
            Assert.assertTrue(extend.getParamMap().isEmpty());
        }
        finally
        {
            extend.close();
        }
    }

    @Test
    public void missingBoundaryShouldReturnBadRequest()
    {
        assertBadRequest("multipart/form-data", "--abc\r\nContent-Disposition: form-data; name=\"field\"\r\n\r\nvalue\r\n--abc--\r\n");
    }

    @Test
    public void initialBoundaryMismatchShouldReturnBadRequest()
    {
        assertBadRequest("multipart/form-data; boundary=abc", "--def\r\nContent-Disposition: form-data; name=\"field\"\r\n\r\nvalue\r\n--def--\r\n");
    }

    @Test
    public void loneCrPartHeaderShouldReturnBadRequestWithoutBufferOverflow()
    {
        assertBadRequest("multipart/form-data; boundary=abc", "--abc\r\n\r\r\n--abc--\r\n");
    }

    @Test
    public void emptyPartHeaderShouldReturnBadRequest()
    {
        assertBadRequest("multipart/form-data; boundary=abc", "--abc\r\n\r\nvalue\r\n--abc--\r\n");
    }

    @Test
    public void partHeaderWithoutColonShouldReturnBadRequestWithoutHanging()
    {
        assertBadRequest("multipart/form-data; boundary=abc", "--abc\r\nBrokenHeader\r\n\r\nvalue\r\n--abc--\r\n");
    }

    @Test
    public void lfOnlyPartShouldReturnBadRequestWithoutHanging()
    {
        assertBadRequest("multipart/form-data; boundary=abc", "--abc\nContent-Disposition: form-data; name=\"field\"\n\nvalue\n--abc--\n");
    }

    @Test
    public void missingContentDispositionShouldReturnBadRequest()
    {
        assertBadRequest("multipart/form-data; boundary=abc", "--abc\r\nContent-Type: text/plain\r\n\r\nvalue\r\n--abc--\r\n");
    }

    @Test
    public void missingContentDispositionNameShouldReturnBadRequest()
    {
        assertBadRequest("multipart/form-data; boundary=abc", "--abc\r\nContent-Disposition: form-data\r\n\r\nvalue\r\n--abc--\r\n");
    }

    @Test
    public void filenameMissingQuoteShouldReturnBadRequest()
    {
        assertBadRequest("multipart/form-data; boundary=abc", "--abc\r\nContent-Disposition: form-data; name=\"file\"; filename=\"x\r\n\r\nvalue\r\n--abc--\r\n");
    }

    @Test
    public void missingCrlfAfterBoundaryShouldReturnBadRequest()
    {
        assertBadRequest("multipart/form-data; boundary=abc", "--abcContent-Disposition: form-data; name=\"field\"\r\n\r\nvalue\r\n--abc--\r\n");
    }

    private void assertBadRequest(String contentType, String body)
    {
        HttpResponse response = handleThroughForwardProcessor(contentType, body);
        try
        {
            Assert.assertEquals("非法 multipart 请求必须返回 400", 400, response.getHead().getStatusCode());
            Assert.assertEquals("Bad Request", response.getHead().getReasonPhrase());
        }
        finally
        {
            response.free();
        }
    }

    private HttpResponse handleThroughForwardProcessor(String contentType, String body)
    {
        return callWithin(500, () -> {
            CapturingPipeline pipeline = new CapturingPipeline();
            CapturingReadNode node = new CapturingReadNode(pipeline);
            PathRequestForwardProcessor processor = new PathRequestForwardProcessor(Collections.emptyList());
            processor.read(request(contentType, body), node);
            Assert.assertNull("非法 multipart 请求不应继续传给后续 404/405 处理", node.forwarded);
            Assert.assertTrue("非法 multipart 请求应写出 HttpResponse，实际：" + pipeline.written, pipeline.written instanceof HttpResponse);
            return (HttpResponse) pipeline.written;
        });
    }

    private HttpRequestExtend parse(String contentType, String body)
    {
        return HttpRequestExtend.from(request(contentType, body), null);
    }

    private HttpRequest request(String contentType, String body)
    {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        IoBuffer buffer = UnPoolBufferAllocator.DEFAULT.allocate(bytes.length);
        buffer.put(bytes);
        HttpRequest request = new HttpRequest()
                .setUrl("http://127.0.0.1/__multipart_expected__")
                .post()
                .setContentType(contentType)
                .setBody(buffer);
        request.getHead().setVersion("HTTP/1.1");
        request.getHead().setContentLength(bytes.length);
        request.addHeader("User-Agent", "MultipartExpectedBehaviorTest");
        return request;
    }

    private static <T> T callWithin(long timeoutMillis, ThrowingSupplier<T> supplier)
    {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try
            {
                value.set(supplier.get());
            }
            catch (Throwable e)
            {
                error.set(e);
            }
            finally
            {
                latch.countDown();
            }
        }, "multipart-expected-behavior-test");
        thread.setDaemon(true);
        thread.start();
        try
        {
            if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS))
            {
                throw new AssertionError("处理未在 " + timeoutMillis + "ms 内返回");
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待处理完成时被中断", e);
        }
        if (error.get() != null)
        {
            throwUnchecked(error.get());
        }
        return value.get();
    }

    private static void throwUnchecked(Throwable e)
    {
        if (e instanceof RuntimeException runtimeException)
        {
            throw runtimeException;
        }
        if (e instanceof Error error)
        {
            throw error;
        }
        throw new AssertionError(e);
    }

    private interface ThrowingSupplier<T>
    {
        T get() throws Throwable;
    }

    private static class CapturingReadNode implements ReadProcessorNode
    {
        private final Pipeline pipeline;
        private Object forwarded;

        private CapturingReadNode(Pipeline pipeline)
        {
            this.pipeline = pipeline;
        }

        @Override
        public void fireRead(Object data)
        {
            forwarded = data;
        }

        @Override
        public void fireReadFailed(Throwable e)
        {
            throwUnchecked(e);
        }

        @Override
        public void fireReadCompleted()
        {
        }

        @Override
        public void firePipelineComplete(Pipeline pipeline)
        {
        }

        @Override
        public ReadProcessorNode getNext()
        {
            return null;
        }

        @Override
        public void setNext(ReadProcessorNode next)
        {
        }

        @Override
        public Pipeline pipeline()
        {
            return pipeline;
        }
    }

    private static class CapturingPipeline implements Pipeline
    {
        private Object written;

        @Override
        public void fireWrite(Object data)
        {
            written = data;
        }

        @Override
        public void directWrite(IoBuffer buffer)
        {
            written = buffer;
        }

        @Override
        public void addReadProcessor(ReadProcessor<?> processor)
        {
        }

        @Override
        public void addWriteProcessor(WriteProcessor<?> processor)
        {
        }

        @Override
        public void shutdownInput()
        {
        }

        @Override
        public AsynchronousSocketChannel socketChannel()
        {
            return null;
        }

        @Override
        public ChannelConfig channelConfig()
        {
            return new ChannelConfig();
        }

        @Override
        public Object getAttach()
        {
            return null;
        }

        @Override
        public void setAttach(Object attach)
        {
        }

        @Override
        public void setWriteListener(WriteListener writeListener)
        {
        }

        @Override
        public boolean isOpen()
        {
            return true;
        }

        @Override
        public BufferAllocator allocator()
        {
            return UnPoolBufferAllocator.DEFAULT;
        }

        @Override
        public String pipelineId()
        {
            return "multipart-expected-behavior-test";
        }

        @Override
        public void putPersistenceStore(String key, Object value)
        {
        }

        @Override
        public Object getPersistenceStore(String key)
        {
            return null;
        }
    }
}
