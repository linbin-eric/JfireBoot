package com.jfirer.jfirer.boot.netServer.config.impl;

import com.jfirer.jfirer.boot.netServer.config.ResourceConfig;
import com.jfirer.jfirer.boot.netServer.config.ResourceHandler;

public class IOResourceConfig implements ResourceConfig
{
    private final String matchUrl;
    private final String path;

    public IOResourceConfig(String matchUrl, String path)
    {
        this.matchUrl = matchUrl;
        this.path     = path;
    }

    @Override
    public ResourceHandler parse()
    {
        if (path.startsWith("classpath:"))
        {
            return new ClassResourceHandler(matchUrl, path);
        }
        else if (path.startsWith("file:"))
        {
            return new FileResourceHandler(matchUrl, path);
        }
        else
        {
            throw new IllegalArgumentException();
        }
    }
}
