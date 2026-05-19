import cc.jfire.boot.http.HttpRequestExtend;
import cc.jfire.jnet.common.buffer.allocator.impl.UnPoolBufferAllocator;
import cc.jfire.jnet.common.buffer.buffer.IoBuffer;
import cc.jfire.jnet.extend.http.dto.HttpRequest;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

/**
 * 当前类用于复现 问题思考文档.md 中记录的 multipart/form-data 解析问题。
 *
 * 类名不匹配 Surefire 默认 *Test 规则，普通 mvn test 不会执行。
 * 需要单独复现时运行：
 * mvn -Dtest=MultipartProblemReproducer test
 */
public class MultipartProblemReproducer
{
    @Test
    public void baselineValidTextPartCanBeParsed()
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
    public void baselineValidFilePartCanBeParsed()
    {
        HttpRequestExtend extend = parse(
                "multipart/form-data; boundary=abc",
                "--abc\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.txt\"\r\nContent-Type: text/plain\r\n\r\nhello\r\n--abc--\r\n"
        );
        try
        {
            Assert.assertEquals(1, extend.getFileParts().size());
            Assert.assertEquals("file", extend.getFileParts().get(0).getFieldName());
            Assert.assertEquals("a.txt", extend.getFileParts().get(0).getFileName());
            Assert.assertEquals("hello", StandardCharsets.UTF_8.decode(extend.getFileParts().get(0).getIoBuffer().readableByteBuffer()).toString());
        }
        finally
        {
            extend.close();
        }
    }

    @Test
    public void quotedBoundaryCurrentlyRejected()
    {
        RuntimeException e = assertThrows(RuntimeException.class, () -> parse(
                "multipart/form-data; boundary=\"abc\"",
                "--abc\r\nContent-Disposition: form-data; name=\"field\"\r\n\r\nvalue\r\n--abc--\r\n"
        ));

        Assert.assertTrue(e.toString().contains("Invalid multipart data"));
    }

    @Test
    public void boundaryNotLastParamCurrentlyRejected()
    {
        RuntimeException e = assertThrows(RuntimeException.class, () -> parse(
                "multipart/form-data; boundary=abc; charset=UTF-8",
                "--abc\r\nContent-Disposition: form-data; name=\"field\"\r\n\r\nvalue\r\n--abc--\r\n"
        ));

        Assert.assertTrue(e.toString().contains("Invalid multipart data"));
    }

    @Test
    public void missingBoundaryParamCurrentlyRejectedByInvalidBoundary()
    {
        RuntimeException e = assertThrows(RuntimeException.class, () -> parse(
                "multipart/form-data",
                "--abc\r\nContent-Disposition: form-data; name=\"field\"\r\n\r\nvalue\r\n--abc--\r\n"
        ));

        Assert.assertTrue(e.toString().contains("Invalid multipart data"));
    }

    @Test
    public void initialBoundaryMismatchCurrentlyRejected()
    {
        RuntimeException e = assertThrows(RuntimeException.class, () -> parse(
                "multipart/form-data; boundary=abc",
                "--def\r\nContent-Disposition: form-data; name=\"field\"\r\n\r\nvalue\r\n--def--\r\n"
        ));

        Assert.assertTrue(e.toString().contains("Invalid multipart data"));
    }

    @Test
    public void emptyHeadersLoneCrCurrentlyReadsPastBufferEnd()
    {
        RuntimeException e = assertThrows(RuntimeException.class, () -> parse(
                "multipart/form-data; boundary=abc",
                "--abc\r\n\r\r\n--abc--\r\n"
        ));

        Assert.assertTrue(e.toString().contains("尝试读取的内容过长"));
    }

    @Test
    public void emptyHeaderBlockCurrentlyCausesNullPointerException()
    {
        assertThrows(NullPointerException.class, () -> parse(
                "multipart/form-data; boundary=abc",
                "--abc\r\n\r\nvalue\r\n--abc--\r\n"
        ));
    }

    @Test
    public void noContentDispositionCurrentlyCausesNullPointerException()
    {
        assertThrows(NullPointerException.class, () -> parse(
                "multipart/form-data; boundary=abc",
                "--abc\r\nContent-Type: text/plain\r\n\r\nvalue\r\n--abc--\r\n"
        ));
    }

    @Test
    public void headerWithoutColonCurrentlyCausesNullPointerException()
    {
        assertThrows(NullPointerException.class, () -> parse(
                "multipart/form-data; boundary=abc",
                "--abc\r\nBrokenHeader\r\n\r\nvalue\r\n--abc--\r\n"
        ));
    }

    @Test
    public void dispositionMissingNameCurrentlyCausesStringIndexOutOfBounds()
    {
        assertThrows(StringIndexOutOfBoundsException.class, () -> parse(
                "multipart/form-data; boundary=abc",
                "--abc\r\nContent-Disposition: form-data\r\n\r\nvalue\r\n--abc--\r\n"
        ));
    }

    @Test
    public void dispositionUnquotedNameCurrentlyCausesStringIndexOutOfBounds()
    {
        assertThrows(StringIndexOutOfBoundsException.class, () -> parse(
                "multipart/form-data; boundary=abc",
                "--abc\r\nContent-Disposition: form-data; name=field\r\n\r\nvalue\r\n--abc--\r\n"
        ));
    }

    @Test
    public void filenameMissingQuoteCurrentlyCausesStringIndexOutOfBounds()
    {
        assertThrows(StringIndexOutOfBoundsException.class, () -> parse(
                "multipart/form-data; boundary=abc",
                "--abc\r\nContent-Disposition: form-data; name=\"file\"; filename=\"x\r\n\r\nvalue\r\n--abc--\r\n"
        ));
    }

    @Test
    public void finalBoundaryOnlyCurrentlyTolerated()
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
    public void missingCrlfAfterBoundaryCurrentlyFailsWithUncontrolledException()
    {
        assertThrows(RuntimeException.class, () -> parse(
                "multipart/form-data; boundary=abc",
                "--abcContent-Disposition: form-data; name=\"field\"\r\n\r\nvalue\r\n--abc--\r\n"
        ));
    }

    @Ignore("当前实现会进入不推进读指针的循环；修复后应改为 400 Bad Request 断言。")
    @Test
    public void lfOnlyPartCurrentlyDoesNotReturn()
    {
        parse(
                "multipart/form-data; boundary=abc",
                "--abc\nContent-Disposition: form-data; name=\"field\"\n\nvalue\n--abc--\n"
        );
    }

    private HttpRequestExtend parse(String contentType, String body)
    {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        IoBuffer buffer = UnPoolBufferAllocator.DEFAULT.allocate(bytes.length);
        buffer.put(bytes);
        HttpRequest request = new HttpRequest()
                .setUrl("http://127.0.0.1/__multipart_reproducer__")
                .post()
                .setContentType(contentType)
                .setBody(buffer);
        request.getHead().setVersion("HTTP/1.1");
        request.getHead().setContentLength(bytes.length);
        return HttpRequestExtend.from(request, null);
    }

    private static <T extends Throwable> T assertThrows(Class<T> expectedType, ThrowingRunnable runnable)
    {
        try
        {
            runnable.run();
        }
        catch (Throwable actual)
        {
            if (expectedType.isInstance(actual))
            {
                return expectedType.cast(actual);
            }
            throw new AssertionError("Expected " + expectedType.getName() + " but got " + actual.getClass().getName(), actual);
        }
        throw new AssertionError("Expected " + expectedType.getName() + " to be thrown");
    }

    private interface ThrowingRunnable
    {
        void run() throws Throwable;
    }
}
