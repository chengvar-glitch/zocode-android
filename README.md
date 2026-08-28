# zocode-android

ZCode Android 客户端，扫码连接 ZCode 桌面端，提供 WebView 容器、文件上传、毛玻璃模糊、路由栈注入等能力。

## 构建

```bash
./gradlew assembleRelease
```

APK 输出在 `app/build/outputs/apk/release/`。

## 使用

1. 安装 APK 并打开
2. 在 ZCode 桌面端扫码配对
3. 配对成功后自动进入会话界面

## 证书

仓库根目录 `zocode.keystore` 为本地签名密钥，**未**纳入版本控制。自编译请生成自己的签名密钥。

## License

[MIT](LICENSE)
