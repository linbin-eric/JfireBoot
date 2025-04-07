package com.jfirer.jfirer.boot.http;

import com.jfirer.jnet.common.buffer.buffer.IoBuffer;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class BinaryPart
{
    private String   fileName;
    private String   fieldName;
    private IoBuffer data;
}
