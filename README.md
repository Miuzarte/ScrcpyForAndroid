# Scrcpy For Android

[Scrcpy](https://github.com/Genymobile/scrcpy) android client

## 截图

<!-- markdownlint-disable MD033 -->

<p align="center">
  <img src="https://github.com/user-attachments/assets/64e24f71-0326-407a-a527-070586bbec9a" height="320" alt="Screenshot 1" />
  <img src="https://github.com/user-attachments/assets/74170ada-6dee-4ec7-ab24-c5ef2a231a47" height="320" alt="Screenshot 2" />
  <img src="https://github.com/user-attachments/assets/6301f2fb-624b-4209-b548-6f37b9bcedc8" height="320" alt="Screenshot 3" />
</p>

## Features

- 可替换 scrcpy-server
- 利用 mDNS 服务实现自动连接启用无线调试的设备、自动发现等待配对设备的IP与端口
- 自动横竖屏切换（算吗

## 已知问题 / TODO

- 组件排序动画一坨

## 构建

- JDK 17+
- Android SDK（含 `compileSdk 36` / `buildTools 36.0.0`）
- Android NDK `28.2.13676358`

```bash
./gradlew assembleDebug
```

specific abi:

```bash
./gradlew assembleRelease -PabiList=arm64-v8a
```

## Credits

- [Genymobile/scrcpy](https://github.com/Genymobile/scrcpy)
- JNI ADB 实现: [rikkaapps/shizuku](https://github.com/rikkaapps/shizuku), [vvb2060/ndk.boringssl](https://github.com/vvb2060), [lsposed/libcxx](https://github.com/lsposed/libcxx)
- 界面组件: [YuKongA/miuix](https://github.com/compose-miuix-ui/miuix)
- 界面设计参考: [tiann/KernelSU/manager](https://github.com/tiann/KernelSU/tree/main/manager)

## License

[Apache License 2.0](LICENSE)
