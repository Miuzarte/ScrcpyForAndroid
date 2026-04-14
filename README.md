# Scrcpy For Android

[Scrcpy](https://github.com/Genymobile/scrcpy) android client

## 截图

<!-- markdownlint-disable MD033 -->

<p align="center">
  <img src="https://github.com/user-attachments/assets/ff7ea4ff-c868-46c8-83cf-df904e083a8f" height="300" alt="Screenshot 1" />
  <img src="https://github.com/user-attachments/assets/79eb4c43-c298-4510-b75c-8f1fd367706d" height="300" alt="Screenshot 2" />
  <img src="https://github.com/user-attachments/assets/4c2f202d-b114-4e53-8dd6-fd60e55d6e04" height="300" alt="Screenshot 3" />
  <img src="https://github.com/user-attachments/assets/92f1f62b-c4d9-40f5-8613-8495b66eff13" height="300" alt="Screenshot 4" />
  <img src="https://github.com/user-attachments/assets/3831d760-fcdf-4ce2-b999-10fc4d2b143b" height="300" alt="Screenshot 5" />
  <img src="https://github.com/user-attachments/assets/f513b7ba-0389-4176-8382-c1a08c4eba99" height="300" alt="Screenshot 6" />
  <img src="https://github.com/user-attachments/assets/7a50bd1f-8095-4269-8e58-88316d86e3d8" height="300" alt="Screenshot 7" />
  <img src="https://github.com/user-attachments/assets/456cf5c7-27eb-4522-9201-a106d84960f3" height="300" alt="Screenshot 8" />
</p>
<p align="center">
  <img src="https://github.com/user-attachments/assets/558bd1b1-15d2-47f8-bdc5-aac9cca689f5" height="180" alt="Screenshot 9" />
  <img src="https://github.com/user-attachments/assets/1b52a662-108d-49cb-a86e-eccc8ac12d64" height="180" alt="Screenshot 10" />
</p>

## Features

- 低延迟音频链路（默认未启用）
  - 受控设备播放 `USAGE_MEDIA` 流时（[namidaco/namida](https://github.com/namidaco/namida)），两设备的音频延迟只差半拍（没有具体测量能力）
  - 受控设备播放 `USAGE_GAME` 流时（明日方舟 Bilibili 服），仍存在 100~200ms 的有感延迟
- 带生物认证的锁屏密码自动填充 (入口位于虚拟按钮中)
- 多配置切换，设备绑定配置，连接后直接进入全屏
- 可替换 scrcpy-server
- 利用 mDNS 服务实现自动连接启用无线调试的设备、自动发现等待配对设备的IP与端口
- 自动横竖屏切换（算吗
- 画中画

## 已知问题

- 因为没有设备用于（也懒得）测试，应用可能无法正常运行在安卓版本较低的设备上，特别是画中画功能，非常取决于国产 ROM 的实现
- 关闭画中画后不会停止 scrcpy 串流，仍然需要回到应用中点击停止
- 虚拟按键的截图实现方式为发送
`keycode 120`，安卓官方([keycodes.h#349](https://android.googlesource.com/platform/frameworks/native/+/master/include/android/keycodes.h#349))的定义为
`System Request / Print Screen key.`，不同的厂商有不同的实现，在某些类原生(`AxionOS`) 上的行为是软重启

## [TODO](TODO.md)

\> [TODO.md](TODO.md)

## 建议搭配模块

- 密码锁屏无法捕获: [LSPosed/DisableFlagSecure](https://github.com/LSPosed/DisableFlagSecure)
- 开机自动启用 adb: [gist/906291](https://gist.github.com/Miuzarte/9062915f1615d5eebd363c759fda496c)

## 构建

- JDK 17+
- Android SDK (`compileSdk 37` / `buildTools 37.0.0`)
- Android NDK `29.0.14206865`

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
- 界面设计参考: [tiann/KernelSU/manager](https://github.com/tiann/KernelSU/tree/main/manager), [miuix/example](https://github.com/compose-miuix-ui/miuix/tree/main/example)
- 画中画实现参考: [ClassicOldSong/moonlight-android](https://github.com/ClassicOldSong/moonlight-android)

## License

[Apache License 2.0](LICENSE)
