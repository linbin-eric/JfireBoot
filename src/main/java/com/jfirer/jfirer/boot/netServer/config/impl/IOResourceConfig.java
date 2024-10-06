package com.jfirer.jfirer.boot.netServer.config.impl;

import com.jfirer.jfirer.boot.netServer.config.ResourceConfig;
import com.jfirer.jfirer.boot.netServer.config.ResourceHandler;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class IOResourceConfig implements ResourceConfig
{
    private final String prefixMatch;
    private final String path;

    public IOResourceConfig(String prefixMatch, String path)
    {
        this.prefixMatch = prefixMatch;
        this.path        = path;
    }

    @Override
    public ResourceHandler parse()
    {
        if (path.startsWith("classpath:"))
        {
            return new ClassResourceHandler(prefixMatch, path);
        }
        else if (path.startsWith("file:"))
        {
            return new FileResourceHandler(prefixMatch, path);
        }
        else
        {
            throw new IllegalArgumentException();
        }
    }
}
