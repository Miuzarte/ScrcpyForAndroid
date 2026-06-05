# mTLS 部署指南

通过 nginx stream 模块实现 mTLS (双向 TLS) 鉴权，将内网 ADB 设备安全暴露到公网。

## 架构

```
手机 App ──mTLS──▶ nginx (stream) ──TCP──▶ 内网 ADB 设备
```

- nginx 在 TLS 层验证客户端证书，验证通过后将 TCP 流量原样转发到内网 ADB
- ADB 协议本身不受影响，mTLS 仅作用于传输层

## 1. 安装 nginx stream 模块

```bash
sudo apt install libnginx-mod-stream
```

## 2. 生成证书

```bash
sudo mkdir -p /etc/nginx/ssl/mTLS

# CA 证书 (自签名根证书)
sudo openssl req -x509 -newkey rsa:4096 \
    -keyout /etc/nginx/ssl/mTLS/ca.key \
    -out /etc/nginx/ssl/mTLS/ca.crt -days 3650 -nodes \
    -subj "/CN=ADB mTLS CA/O=ScrcpyForAndroid"

# 服务器证书 (由 CA 签发，CN 填你的域名)
sudo openssl req -newkey rsa:2048 \
    -keyout /etc/nginx/ssl/mTLS/server.key \
    -out /etc/nginx/ssl/mTLS/server.csr -nodes \
    -subj "/CN=your.domain.com"
sudo openssl x509 -req -in /etc/nginx/ssl/mTLS/server.csr \
    -CA /etc/nginx/ssl/mTLS/ca.crt -CAkey /etc/nginx/ssl/mTLS/ca.key \
    -CAcreateserial -out /etc/nginx/ssl/mTLS/server.crt -days 365

# 客户端证书 (由 CA 签发，供 App 导入)
sudo openssl req -newkey rsa:2048 \
    -keyout /etc/nginx/ssl/mTLS/client.key \
    -out /etc/nginx/ssl/mTLS/client.csr -nodes \
    -subj "/CN=adb-client"
sudo openssl x509 -req -in /etc/nginx/ssl/mTLS/client.csr \
    -CA /etc/nginx/ssl/mTLS/ca.crt -CAkey /etc/nginx/ssl/mTLS/ca.key \
    -CAcreateserial -out /etc/nginx/ssl/mTLS/client.crt -days 365

# 打包为 PKCS12 (供 App 导入，密码可自定义)
sudo openssl pkcs12 -export \
    -in /etc/nginx/ssl/mTLS/client.crt \
    -inkey /etc/nginx/ssl/mTLS/client.key \
    -out /etc/nginx/ssl/mTLS/client.p12 \
    -name "adb-client" -legacy

# 清理临时文件 & 设置权限
sudo rm /etc/nginx/ssl/mTLS/server.csr /etc/nginx/ssl/mTLS/client.csr
sudo chmod 600 /etc/nginx/ssl/mTLS/*.key
sudo chmod 644 /etc/nginx/ssl/mTLS/*.crt /etc/nginx/ssl/mTLS/*.p12
```

## 3. nginx 配置

### 加载 stream 模块

确认 `/etc/nginx/modules-enabled/` 下已有 `50-mod-stream.conf`：

```
load_module modules/ngx_stream_module.so;
```

### 创建 stream 配置

`/etc/nginx/stream.conf`：

```nginx
stream {
    upstream adb_backend {
        server 192.168.3.15:5555;  # 内网 ADB 设备地址
    }

    server {
        listen 5555 ssl;  # 公网监听端口

        ssl_certificate         /etc/nginx/ssl/mTLS/server.crt;
        ssl_certificate_key     /etc/nginx/ssl/mTLS/server.key;
        ssl_client_certificate  /etc/nginx/ssl/mTLS/ca.crt;
        ssl_verify_client       on;  # 强制验证客户端证书

        ssl_protocols TLSv1.2 TLSv1.3;
        ssl_ciphers HIGH:!aNULL:!MD5;

        proxy_pass adb_backend;
        proxy_connect_timeout 10s;
        proxy_timeout 300s;
    }
}
```

### nginx.conf 顶层 include

在 `nginx.conf` 的 `events {}` 块之后、`http {}` 块之前添加：

```nginx
include /etc/nginx/stream.conf;
```

> **注意**：stream 块的 `listen` 端口不能与 http 块的端口冲突。

### 验证 & 重载

```bash
sudo nginx -t && sudo systemctl reload nginx
```

## 4. App 端配置

1. 将 `ca.crt` 和 `client.p12` 传输到手机
2. 打开 App → 设置 → mTLS 认证
3. 启用 mTLS 开关
4. 导入 CA 证书 (`ca.crt`)
5. 导入客户端证书 (`client.p12`，PKCS12 格式)
6. 连接地址填写 `your.domain.com:5555`

## 5. 防火墙

确保公网端口（示例中为 5555）已放行。

## 安全说明

- CA 私钥 (`ca.key`) 请妥善保管，不要泄露
- 客户端证书可按需吊销或更换
- 服务器证书到期后需重新签发
- 建议定期轮换证书
