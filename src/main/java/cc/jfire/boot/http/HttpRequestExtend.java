package cc.jfire.boot.http;

import cc.jfire.dson.Dson;
import cc.jfire.jnet.common.api.Pipeline;
import cc.jfire.jnet.common.buffer.buffer.IoBuffer;
import cc.jfire.jnet.common.util.HttpCoderUtil;
import cc.jfire.jnet.extend.http.dto.HttpRequest;
import lombok.Getter;
import lombok.Setter;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Getter
public class HttpRequestExtend implements AutoCloseable
{
    // 从 HttpRequest 复制的关键属性
    private             String              method;
    private             String              url;
    private             String              version;
    private             Map<String, String> headers;
    private             long                contentLength;
    private             String              contentType;
    // 解析后的属性
    private             String              utf8StrBody;
    private             Map<String, Object> paramMap;
    private             String              path;
    @Setter
    private             Pipeline            pipeline;
    protected           List<FilePart>      fileParts        = DUMMY_FILE_PARTS;
    public static final List<FilePart>      DUMMY_FILE_PARTS = new LinkedList<>();
    private static final int                 MAX_BOUNDARY_LENGTH = 70;
    private static final byte[]              HEADER_END = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final int                 MAX_PART_HEADER_BYTES = 32 * 1024;
    private static final int                 MAX_PART_HEADER_LINE_BYTES = 8 * 1024;
    private static final int                 MAX_PART_HEADER_COUNT = 100;

    public static HttpRequestExtend from(HttpRequest request, Pipeline pipeline)
    {
        if (request == null)
        {
            return null;
        }
        HttpRequestExtend extend = new HttpRequestExtend();
        extend.pipeline = pipeline;
        // 复制关键属性
        extend.method        = request.getHead().getMethod();
        extend.url           = request.getHead().getPath();
        extend.version       = request.getHead().getVersion();
        extend.headers       = request.getHead().getHeaders();
        extend.contentLength = request.getHead().getContentLength();
        extend.contentType   = extend.headers != null ? extend.headers.get("Content-Type") : null;
        // 解析 path 和 URL 参数
        extend.parsePath();
        // 根据 Content-Type 解析 body
        IoBuffer body = request.getBody();
        try
        {
            if (body != null && body.remainRead() > 0)
            {
                HeaderValue parsedContentType = extend.contentType == null ? null : parseHeaderValue(extend.contentType);
                if (parsedContentType != null && "multipart/form-data".equalsIgnoreCase(parsedContentType.value()))
                {
                    String boundary = extractBoundary(parsedContentType);
                    extend.parseMultiPart(body, boundary);
                }
                else
                {
                    // application/json 或其他类型，解析为 utf8 字符串
                    extend.utf8StrBody = StandardCharsets.UTF_8.decode(body.readableByteBuffer()).toString();
                    body.free();
                }
            }
        }
        catch (Throwable e)
        {
            extend.close();
            throw e;
        }
        finally
        {
            // 释放 HttpRequest（head 部分）
            request.getHead().close();
        }
        return extend;
    }

    @Override
    public void close()
    {
        fileParts.forEach(FilePart::close);
    }

    public Map<String, Object> getNotNullParamMap()
    {
        if (paramMap == null)
        {
            paramMap = new HashMap<>();
        }
        return paramMap;
    }

    private void parsePath()
    {
        int index = url.indexOf("?");
        if (index == -1)
        {
            path = url;
        }
        else
        {
            path     = url.substring(0, index);
            paramMap = new HashMap<>();
            Arrays.stream(url.substring(index + 1).split("&")).forEach(v -> {
                int paramValueIndex = v.indexOf("=");
                if (paramValueIndex == -1)
                {
                    paramMap.put(v, "");
                }
                else
                {
                    paramMap.put(v.substring(0, paramValueIndex), v.substring(paramValueIndex + 1));
                }
            });
        }
    }

    public void parseJsonBodyToParamMap()
    {
        if (paramMap == null)
        {
            paramMap = new HashMap<>();
        }
        if (utf8StrBody != null)
        {
            Object o = Dson.fromString(utf8StrBody);
            if (o instanceof Map map)
            {
                paramMap.putAll(map);
            }
        }
    }

    public void parseUrlEncodedBodyToParamMap()
    {
        if (paramMap == null)
        {
            paramMap = new HashMap<>();
        }
        if (utf8StrBody != null)
        {
            Arrays.stream(utf8StrBody.split("&")).forEach(v -> {
                int paramValueIndex = v.indexOf("=");
                if (paramValueIndex == -1)
                {
                    paramMap.put(v, "");
                }
                else
                {
                    String key   = URLDecoder.decode(v.substring(0, paramValueIndex), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(v.substring(paramValueIndex + 1), StandardCharsets.UTF_8);
                    paramMap.put(key, value);
                }
            });
        }
    }

    public void ensureParamMapReady(boolean hasSimpleTypeParam)
    {
        if (contentType == null)
        {
            getNotNullParamMap();
            return;
        }
        String lowerContentType = contentType.toLowerCase();
        if (lowerContentType.startsWith("application/json"))
        {
            if (hasSimpleTypeParam)
            {
                parseJsonBodyToParamMap();
            }
        }
        else if (lowerContentType.startsWith("application/x-www-form-urlencoded"))
        {
            parseUrlEncodedBodyToParamMap();
        }
        // multipart/form-data 已在 from() 中解析完成，无需额外操作
    }

    /**
     * AI 生成：保存 header 主值和分号参数，供 Content-Type 与 Content-Disposition 复用。
     */
    private record HeaderValue(String value, Map<String, String> params)
    {
    }

    /**
     * AI 生成：保存 multipart Content-Disposition 中解析出的字段名和文件名。
     */
    private record PartDisposition(String fieldName, String fileName)
    {
    }

    /**
     * AI 生成：解析形如 `value; key=value` 的 header，并统一处理参数名和引号。
     */
    private static HeaderValue parseHeaderValue(String raw)
    {
        if (raw == null)
        {
            return null;
        }
        List<String> segments = splitHeaderSegments(raw);
        if (segments.isEmpty())
        {
            throw HttpRequestParseException.badRequest("Invalid header value");
        }
        String value = segments.get(0).trim();
        if (value.isEmpty())
        {
            throw HttpRequestParseException.badRequest("Invalid header value");
        }
        Map<String, String> params = new HashMap<>();
        for (int i = 1; i < segments.size(); i++)
        {
            String segment = segments.get(i).trim();
            if (segment.isEmpty())
            {
                continue;
            }
            int eq = segment.indexOf('=');
            if (eq <= 0)
            {
                throw HttpRequestParseException.badRequest("Invalid header parameter: " + segment);
            }
            String name = segment.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String paramValue = segment.substring(eq + 1).trim();
            if (name.isEmpty())
            {
                throw HttpRequestParseException.badRequest("Invalid header parameter name");
            }
            params.put(name, unquote(paramValue));
        }
        return new HeaderValue(value, params);
    }

    /**
     * AI 生成：按未被引号包裹的分号拆分 header 段，避免 quoted 参数被误切分。
     */
    private static List<String> splitHeaderSegments(String raw)
    {
        List<String> result = new ArrayList<>();
        StringBuilder segment = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < raw.length(); i++)
        {
            char ch = raw.charAt(i);
            if (escaped)
            {
                segment.append(ch);
                escaped = false;
                continue;
            }
            if (quoted && ch == '\\')
            {
                segment.append(ch);
                escaped = true;
                continue;
            }
            if (ch == '"')
            {
                quoted = !quoted;
                segment.append(ch);
                continue;
            }
            if (ch == ';' && !quoted)
            {
                result.add(segment.toString());
                segment.setLength(0);
                continue;
            }
            segment.append(ch);
        }
        if (quoted)
        {
            throw HttpRequestParseException.badRequest("Invalid quoted header parameter");
        }
        result.add(segment.toString());
        return result;
    }

    /**
     * AI 生成：去除 quoted-string 外层引号并处理反斜杠转义。
     */
    private static String unquote(String value)
    {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"')
        {
            StringBuilder builder = new StringBuilder(value.length() - 2);
            boolean escaped = false;
            for (int i = 1; i < value.length() - 1; i++)
            {
                char ch = value.charAt(i);
                if (escaped)
                {
                    builder.append(ch);
                    escaped = false;
                }
                else if (ch == '\\')
                {
                    escaped = true;
                }
                else
                {
                    builder.append(ch);
                }
            }
            if (escaped)
            {
                throw HttpRequestParseException.badRequest("Invalid quoted header parameter");
            }
            return builder.toString();
        }
        if (value.startsWith("\"") || value.endsWith("\""))
        {
            throw HttpRequestParseException.badRequest("Invalid quoted header parameter");
        }
        return value;
    }

    /**
     * AI 生成：从 Content-Type 参数中提取 multipart boundary 并做基础合法性校验。
     */
    private static String extractBoundary(HeaderValue contentType)
    {
        String boundary = contentType.params().get("boundary");
        if (boundary == null)
        {
            throw HttpRequestParseException.badRequest("Missing multipart boundary");
        }
        if (boundary.isEmpty())
        {
            throw HttpRequestParseException.badRequest("Empty multipart boundary");
        }
        if (boundary.length() > MAX_BOUNDARY_LENGTH)
        {
            throw HttpRequestParseException.badRequest("Multipart boundary is too long");
        }
        for (int i = 0; i < boundary.length(); i++)
        {
            if (!isValidBoundaryChar(boundary.charAt(i)))
            {
                throw HttpRequestParseException.badRequest("Invalid multipart boundary");
            }
        }
        return boundary;
    }

    /**
     * AI 生成：判断 boundary 字符是否处在可打印 ASCII 范围内。
     */
    private static boolean isValidBoundaryChar(char ch)
    {
        return ch > 0x20 && ch < 0x7f && ch != '\r' && ch != '\n';
    }

    /**
     * AI 生成：用显式状态机解析 multipart body，区分起始、分隔和结束 boundary。
     */
    private void parseMultiPart(IoBuffer body, String boundary)
    {
        byte[]   delimiter           = ascii("--" + boundary);
        byte[]   nextDelimiterPrefix = ascii("\r\n--" + boundary);
        IoBuffer currentSlice        = null;
        fileParts = new ArrayList<>();
        getNotNullParamMap();
        try
        {
            if (!startsWith(body, delimiter))
            {
                throw HttpRequestParseException.badRequest("Invalid multipart data: boundary not found at expected position");
            }
            body.addReadPosi(delimiter.length);
            if (consumeFinalBoundarySuffix(body))
            {
                consumeOptionalCrlf(body);
                return;
            }
            consumeRequiredCrlf(body, "Invalid multipart data: missing CRLF after boundary");

            while (true)
            {
                int partStart = body.getReadPosi();
                int boundaryStart = findNextBoundary(body, nextDelimiterPrefix);
                if (boundaryStart == -1)
                {
                    throw HttpRequestParseException.badRequest("Invalid multipart data: next boundary not found");
                }
                currentSlice = body.slice(boundaryStart - partStart);
                IoBuffer partSlice = currentSlice;
                currentSlice = null;
                parseBoundaryPart(partSlice);

                body.addReadPosi(nextDelimiterPrefix.length);
                if (consumeFinalBoundarySuffix(body))
                {
                    consumeOptionalCrlf(body);
                    return;
                }
                consumeRequiredCrlf(body, "Invalid multipart data: missing CRLF after part boundary");
            }
        }
        catch (Throwable e)
        {
            if (currentSlice != null)
            {
                currentSlice.free();
            }
            close();
            throw e;
        }
        finally
        {
            body.free();
        }
    }

    /**
     * AI 生成：把协议固定标记转换为 US-ASCII 字节数组用于 buffer 匹配。
     */
    private static byte[] ascii(String value)
    {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * AI 生成：检查当前读指针处是否匹配指定字节序列。
     */
    private static boolean startsWith(IoBuffer buffer, byte[] bytes)
    {
        if (buffer.remainRead() < bytes.length)
        {
            return false;
        }
        int readPosi = buffer.getReadPosi();
        for (int i = 0; i < bytes.length; i++)
        {
            if (buffer.get(readPosi + i) != bytes[i])
            {
                return false;
            }
        }
        return true;
    }

    /**
     * AI 生成：识别并消费 multipart 结束 boundary 后的 `--` 后缀。
     */
    private static boolean consumeFinalBoundarySuffix(IoBuffer buffer)
    {
        if (buffer.remainRead() >= 2 && buffer.get(buffer.getReadPosi()) == '-' && buffer.get(buffer.getReadPosi() + 1) == '-')
        {
            buffer.addReadPosi(2);
            return true;
        }
        return false;
    }

    /**
     * AI 生成：强制消费 CRLF，缺失时抛出受控请求解析错误。
     */
    private static void consumeRequiredCrlf(IoBuffer buffer, String message)
    {
        if (buffer.remainRead() < 2 || buffer.get(buffer.getReadPosi()) != '\r' || buffer.get(buffer.getReadPosi() + 1) != '\n')
        {
            throw HttpRequestParseException.badRequest(message);
        }
        buffer.addReadPosi(2);
    }

    /**
     * AI 生成：在结束 boundary 后容忍并消费可选 CRLF。
     */
    private static void consumeOptionalCrlf(IoBuffer buffer)
    {
        if (buffer.remainRead() >= 2 && buffer.get(buffer.getReadPosi()) == '\r' && buffer.get(buffer.getReadPosi() + 1) == '\n')
        {
            buffer.addReadPosi(2);
        }
    }

    /**
     * AI 生成：查找下一个合法 part boundary，只接受 CRLF 或 `--` 后缀。
     */
    private static int findNextBoundary(IoBuffer body, byte[] nextDelimiterPrefix)
    {
        int scanStart = body.getReadPosi();
        int writePosi = body.getWritePosi();
        for (int i = scanStart; i + nextDelimiterPrefix.length <= writePosi; i++)
        {
            if (!matchesAt(body, i, nextDelimiterPrefix))
            {
                continue;
            }
            int after = i + nextDelimiterPrefix.length;
            if (after + 1 >= writePosi)
            {
                throw HttpRequestParseException.badRequest("Invalid multipart data: incomplete boundary");
            }
            byte b0 = body.get(after);
            byte b1 = body.get(after + 1);
            if ((b0 == '\r' && b1 == '\n') || (b0 == '-' && b1 == '-'))
            {
                return i;
            }
        }
        return -1;
    }

    /**
     * AI 生成：在指定绝对位置比较字节序列，供 boundary 和 header 标记复用。
     */
    private static boolean matchesAt(IoBuffer buffer, int pos, byte[] bytes)
    {
        for (int i = 0; i < bytes.length; i++)
        {
            if (buffer.get(pos + i) != bytes[i])
            {
                return false;
            }
        }
        return true;
    }

    /**
     * AI 生成：解析单个 multipart part，并按文本字段或文件字段转交 slice 所有权。
     */
    private boolean parseBoundaryPart(IoBuffer slice)
    {
        boolean transferred = false;
        try
        {
            Map<String, String> partHeaders = parsePartHeaders(slice);
            String partContentType = partHeaders.getOrDefault("Content-Type", "text/plain");
            PartDisposition disposition = parseContentDisposition(partHeaders.get("Content-Disposition"));
            boolean fileLike = disposition.fileName() != null || "application/octet-stream".equalsIgnoreCase(partContentType);
            if (fileLike)
            {
                FilePart filePart = new FilePart();
                filePart.setFileName(disposition.fileName());
                filePart.setFieldName(disposition.fieldName());
                filePart.setIoBuffer(slice);
                fileParts.add(filePart);
                transferred = true;
                return true;
            }
            String value = StandardCharsets.UTF_8.decode(slice.readableByteBuffer()).toString();
            paramMap.put(disposition.fieldName(), value);
            return false;
        }
        finally
        {
            if (!transferred)
            {
                slice.free();
            }
        }
    }

    /**
     * AI 生成：解析 multipart part header 区块，并施加总长度、单行长度和数量限制。
     */
    private Map<String, String> parsePartHeaders(IoBuffer slice)
    {
        int headerEnd = findBytes(slice, HEADER_END, MAX_PART_HEADER_BYTES + HEADER_END.length);
        if (headerEnd == -1)
        {
            if (slice.getWritePosi() - slice.getReadPosi() > MAX_PART_HEADER_BYTES)
            {
                throw HttpRequestParseException.payloadTooLarge("Multipart part header is too large");
            }
            throw HttpRequestParseException.badRequest("Invalid multipart part header");
        }
        int headerLength = headerEnd - slice.getReadPosi();
        if (headerLength > MAX_PART_HEADER_BYTES)
        {
            throw HttpRequestParseException.payloadTooLarge("Multipart part header is too large");
        }
        Map<String, String> headers = new HashMap<>();
        int pos = slice.getReadPosi();
        int count = 0;
        while (pos < headerEnd)
        {
            int lineEnd = findCrlfBefore(slice, pos, headerEnd + 2);
            if (lineEnd == -1 || lineEnd > headerEnd)
            {
                throw HttpRequestParseException.badRequest("Invalid multipart part header line");
            }
            int lineLength = lineEnd - pos;
            if (lineLength > MAX_PART_HEADER_LINE_BYTES)
            {
                throw HttpRequestParseException.payloadTooLarge("Multipart part header line is too large");
            }
            int colon = findByte(slice, pos, lineEnd, (byte) ':');
            if (colon <= pos)
            {
                throw HttpRequestParseException.badRequest("Invalid multipart part header line");
            }
            String name = decodeRange(slice, pos, colon).trim();
            if (name.isEmpty())
            {
                throw HttpRequestParseException.badRequest("Invalid multipart part header name");
            }
            int valueStart = colon + 1;
            while (valueStart < lineEnd)
            {
                byte b = slice.get(valueStart);
                if (b != ' ' && b != '\t')
                {
                    break;
                }
                valueStart++;
            }
            String value = decodeRange(slice, valueStart, lineEnd).trim();
            headers.put(HttpCoderUtil.normalizeHeaderName(name), value);
            count++;
            if (count > MAX_PART_HEADER_COUNT)
            {
                throw HttpRequestParseException.payloadTooLarge("Too many multipart part headers");
            }
            pos = lineEnd + 2;
        }
        slice.setReadPosi(headerEnd + HEADER_END.length);
        return headers;
    }

    /**
     * AI 生成：解析并校验 multipart Content-Disposition，得到字段名和优先级正确的文件名。
     */
    private PartDisposition parseContentDisposition(String raw)
    {
        if (raw == null)
        {
            throw HttpRequestParseException.badRequest("Missing Content-Disposition");
        }
        HeaderValue disposition = parseHeaderValue(raw);
        if (!"form-data".equalsIgnoreCase(disposition.value()))
        {
            throw HttpRequestParseException.badRequest("Invalid Content-Disposition");
        }
        String fieldName = disposition.params().get("name");
        if (fieldName == null || fieldName.isEmpty())
        {
            throw HttpRequestParseException.badRequest("Missing multipart field name");
        }
        String fileName = null;
        String encodedFileName = disposition.params().get("filename*");
        if (encodedFileName != null)
        {
            String prefix = "UTF-8''";
            if (!encodedFileName.regionMatches(true, 0, prefix, 0, prefix.length()))
            {
                throw HttpRequestParseException.badRequest("Invalid multipart filename*");
            }
            try
            {
                fileName = URLDecoder.decode(encodedFileName.substring(prefix.length()), StandardCharsets.UTF_8);
            }
            catch (IllegalArgumentException e)
            {
                throw HttpRequestParseException.badRequest("Invalid multipart filename*");
            }
        }
        else
        {
            fileName = disposition.params().get("filename");
        }
        return new PartDisposition(fieldName, fileName);
    }

    /**
     * AI 生成：在限定扫描长度内查找目标字节序列，避免畸形 header 无界扫描。
     */
    private static int findBytes(IoBuffer buffer, byte[] target, int maxScanBytes)
    {
        int start = buffer.getReadPosi();
        int maxEnd = Math.min(buffer.getWritePosi(), start + maxScanBytes);
        for (int i = start; i + target.length <= maxEnd; i++)
        {
            if (matchesAt(buffer, i, target))
            {
                return i;
            }
        }
        return -1;
    }

    /**
     * AI 生成：在指定范围内查找 CRLF，并拒绝孤立 CR。
     */
    private static int findCrlfBefore(IoBuffer buffer, int start, int end)
    {
        for (int i = start; i + 1 < end; i++)
        {
            if (buffer.get(i) == '\r')
            {
                if (buffer.get(i + 1) == '\n')
                {
                    return i;
                }
                throw HttpRequestParseException.badRequest("Invalid multipart part header line");
            }
        }
        return -1;
    }

    /**
     * AI 生成：在限定范围内查找单个字节，用于定位 header 名值分隔符。
     */
    private static int findByte(IoBuffer buffer, int start, int end, byte target)
    {
        for (int i = start; i < end; i++)
        {
            if (buffer.get(i) == target)
            {
                return i;
            }
        }
        return -1;
    }

    /**
     * AI 生成：临时移动读指针解码指定区间，保持调用方的原读指针不变。
     */
    private static String decodeRange(IoBuffer buffer, int start, int end)
    {
        int oldReadPosi = buffer.getReadPosi();
        try
        {
            buffer.setReadPosi(start);
            return StandardCharsets.UTF_8.decode(buffer.readableByteBuffer(end)).toString();
        }
        finally
        {
            buffer.setReadPosi(oldReadPosi);
        }
    }
}
