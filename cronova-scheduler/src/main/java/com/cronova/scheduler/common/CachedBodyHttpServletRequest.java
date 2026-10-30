package com.cronova.scheduler.common;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 请求体缓存包装类
 * <p>
 * 用于在 AKSK 签名校验时读取一次请求体（参与签名），同时保证后续 Controller
 * 仍能通过 getInputStream()/getReader() 再次读取同一份请求体，避免数据丢失。
 * 仅用于 Worker 鉴权接口（body 为小型 JSON），大文件上传不经过此类包装。
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    /** 单个 Worker 请求体最大缓存大小，避免大 body 拖垮调度器内存 */
    private static final int MAX_BODY_BYTES = 1024 * 1024;

    /** 缓存的请求体原始字节 */
    private final byte[] body;

    /**
     * 构造时读取并缓存请求体
     * @param request 原始请求
     * @throws IOException 读取失败
     */
    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        try (InputStream input = request.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw new IOException("request body exceeds 1MB");
                }
                output.write(buffer, 0, read);
            }
            this.body = output.toByteArray();
        }
    }

    /** 缓存体的字节流，可每次返回新的可重复读取流 */
    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream bais = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public int read() {
                return bais.read();
            }

            @Override
            public boolean isFinished() {
                return bais.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // 非异步读取，无需监听器
            }
        };
    }

    /** 以 UTF-8 读取缓存体 */
    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    /**
     * 获取请求体原始字符串（用于签名）
     * @return 请求体文本，无 body 时为空字符串
     */
    public String getBody() {
        return new String(body, StandardCharsets.UTF_8);
    }
}
