package com.example.pong;

import java.util.concurrent.atomic.AtomicInteger;

public class Test {
    private final AtomicInteger requestCount = new AtomicInteger(0);

    public void handleRequest() {
        // 模拟处理请求
        System.out.println("Handling request...");

        // 增加请求计数
        int currentCount = requestCount.incrementAndGet();
        System.out.println("Total requests handled: " + currentCount);
    }

    public static void main(String[] args) {
        Test app = new Test();

        // 创建多个线程来模拟并发请求
        Thread t1 = new Thread(() -> app.handleRequest());
        Thread t2 = new Thread(() -> app.handleRequest());
        Thread t3 = new Thread(() -> app.handleRequest());
        Thread t4 = new Thread(() -> app.handleRequest());
        Thread t5 = new Thread(() -> app.handleRequest());
        Thread t6 = new Thread(() -> app.handleRequest());
        Thread t7 = new Thread(() -> app.handleRequest());
        Thread t8 = new Thread(() -> app.handleRequest());
        Thread t9 = new Thread(() -> app.handleRequest());
        Thread t10 = new Thread(() -> app.handleRequest());

        t1.start();
        t2.start();
        t3.start();
        t4.start();
        t5.start();
        t6.start();
        t7.start();
        t8.start();
        t9.start();
        t10.start();
    }
}
