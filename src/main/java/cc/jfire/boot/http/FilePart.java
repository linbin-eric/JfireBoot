package cc.jfire.boot.http;

import cc.jfire.jnet.common.buffer.buffer.IoBuffer;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class FilePart implements AutoCloseable
{
    private String   fileName;
    private String   fieldName;
    private IoBuffer ioBuffer;

    @Override
    public void close()
    {
        if (ioBuffer != null)
        {
            ioBuffer.free();
            ioBuffer = null;
        }
    }
}
