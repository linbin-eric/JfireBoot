package com.jfirer.jfirer.boot.netServer.config;

import com.jfirer.jfirer.boot.netServer.config.impl.IOResourceConfig;
import com.jfirer.jfirer.boot.netServer.config.impl.proxy.FullMatchProxyHttpHandler;
import com.jfirer.jfirer.boot.netServer.config.impl.proxy.PrefixMatchProxyHttpHandler;

public interface ResourceConfig
{
    ResourceHandler parse();

    static ResourceConfig io(String prefixMatch, String path)
    {
        return new IOResourceConfig(prefixMatch, path);
    }

    static ResourceConfig fullMatch(String match, String proxy)
    {
        return () -> new FullMatchProxyHttpHandler(match, proxy);
    }

    static ResourceConfig prefixMatch(String prefix, String proxy)
    {
        return () -> new PrefixMatchProxyHttpHandler(prefix, proxy);
    }
}
