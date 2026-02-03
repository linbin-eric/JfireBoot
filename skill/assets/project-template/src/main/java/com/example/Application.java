package com.example;

import cc.jfire.baseutil.RuntimeJVM;
import cc.jfire.boot.http.HttpAppServer;
import cc.jfire.jfire.core.ApplicationContext;

import java.util.concurrent.locks.LockSupport;

/**
 * 应用启动类
 */
public class Application {
    public static void main(String[] args) {
        // 注册主类（必须）
        RuntimeJVM.registerMainClass(args);

        // 启动 IOC 容器
        ApplicationContext context = ApplicationContext.boot(AppConfig.class);

        // 启动 HTTP 服务器
        // 参数：端口、IOC容器、静态资源前缀（classpath）
        HttpAppServer.start(8080, context, "web");

        System.out.println("JfireBoot 应用已启动，监听端口: 8080");

        // 保持主线程存活（Jnet 使用虚拟线程，JVM 可能会直接退出）
        LockSupport.park();
    }
}
