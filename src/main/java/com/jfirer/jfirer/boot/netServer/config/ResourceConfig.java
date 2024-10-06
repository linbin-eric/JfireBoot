package com.jfirer.jfirer.boot.netServer.config;

import com.jfirer.jfirer.boot.netServer.config.impl.IOResourceConfig;

public interface ResourceConfig
{
    ResourceHandler parse();

    static ResourceConfig io(String prefixMatch, String path)
    {
        return new IOResourceConfig(prefixMatch, path);
    }

}
