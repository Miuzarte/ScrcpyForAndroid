<!-- markdownlint-disable MD033 -->

# Scrcpy For Android

<a href="https://github.com/Genymobile/scrcpy/blob/master/app/data/icon.svg" title="Modified from the original version">
  <img src="app/src/main/assets/icon/icon.svg" width="128" height="128" alt="scrcpy" align="right" />
</a>

[Scrcpy](https://github.com/Genymobile/scrcpy) android client

从通过
[ADB Wireless](https://developer.android.com/tools/adb?hl=zh-cn#connect-to-a-device-over-wi-fi)
连接的 Android 设备镜像视频与音频，并允许使用触摸屏与键盘鼠标进行控制

不需要 root 权限，也无需在设备上安装应用程序

## 截图

<p align="center">
  <img src="https://github.com/user-attachments/assets/00bf5e7c-2e37-4e99-976e-06a56232a628" height="300" alt="Devices" />
  <img src="https://github.com/user-attachments/assets/a423cf0e-5514-423e-8daf-818add2b5558" height="300" alt="Recent Tasks" />
  <img src="https://github.com/user-attachments/assets/f1271cc7-ce45-46d2-8a01-0f4a367c0e4a" height="300" alt="Streaming" />
  <img src="https://github.com/user-attachments/assets/220fc973-f3b7-4bec-8733-95bbddf95a7a" height="300" alt="Scrcpy All Options" />
  <img src="https://github.com/user-attachments/assets/6d4cff1b-1277-44e4-bcfa-18201738a703" height="300" alt="Terminal" />
  <img src="https://github.com/user-attachments/assets/886ba6db-d6a7-4528-96fb-bb0ce4deb953" height="300" alt="Files" />
  <img src="https://github.com/user-attachments/assets/d3b29861-6e86-4301-9b1d-1f7836ad9b7e" height="300" alt="Settings" />
  <img src="https://github.com/user-attachments/assets/f513b7ba-0389-4176-8382-c1a08c4eba99" height="300" alt="Multi Touch Test" />
  <img src="https://github.com/user-attachments/assets/7a50bd1f-8095-4269-8e58-88316d86e3d8" height="300" alt="Virtual Buttons Reorder" />
  <img src="https://github.com/user-attachments/assets/448481df-c15b-4580-af96-141e9b56c41f" height="300" alt="About" />
</p>
<p align="center">
  <img src="https://github.com/user-attachments/assets/558bd1b1-15d2-47f8-bdc5-aac9cca689f5" height="180" alt="PiP" />
  <img src="https://github.com/user-attachments/assets/1b52a662-108d-49cb-a86e-eccc8ac12d64" height="180" alt="Gaming" />
</p>

## Features

- 控制时可拉起本机输入法，且支持输入中文
- 剪贴板同步
- 低延迟音频链路 (默认未启用)
  - 受控设备播放 `USAGE_MEDIA` 流时 ([namidaco/namida](https://github.com/namidaco/namida)) ，两设备的音频延迟只差半拍 (没有具体测量能力)
  - 受控设备播放 `USAGE_GAME` 流时 (明日方舟 Bilibili 服) ，仍存在 100~200ms 的有感延迟
- 带生物认证的锁屏密码自动填充 (入口位于虚拟按钮中)
- 多配置切换，设备绑定配置，连接后直接进入全屏
- 可替换 scrcpy-server
- 利用 mDNS 服务实现自动连接启用无线调试的设备、自动发现等待配对设备的IP与端口
- 自动横竖屏切换（算吗
- 画中画
- 双向文件传输
- 流式 adb 终端
- 内置录制

## 已知问题

- 刚开始串流时有概率丢失关键帧导致花屏，等待一段时间后重新接收关键帧即可
- 因为没有设备用于 (也懒得) 测试，应用可能无法正常运行在安卓版本较低的设备上，特别是画中画功能，非常取决于国产 ROM 的实现
- 关闭画中画后不会停止 scrcpy 串流，仍然需要回到应用中点击停止
- 跨设备输入中文
  - 实现方式为利用剪贴板同步，会导致受控机剪贴板历史被填充输入历史
  - 不知道为什么有时候会上屏失败
- 虚拟按键的截图实现方式为发送
`keycode 120`，安卓官方([keycodes.h#349](https://android.googlesource.com/platform/frameworks/native/+/master/include/android/keycodes.h#349))的定义为
`System Request / Print Screen key.`，不同的厂商有不同的实现，在某些类原生(`AxionOS`) 上的行为是软重启

## TODO

\> [TODO.md](TODO.md)

## NOT-TODO

- 低版本安卓适配
  - 项目的 `minSdk` 为 26 / Android 8，98.4% 的设备都能成功安装上，只是出问题不管
- 更多的文件操作，包括不限于 `删除`, `重命名` 等
  - 可以长按复制路径去终端用 `rm`, `mv`
- 录制的进一步优化
  - `MediaCodec` 限制太大，再继续做要引入 `ffmpeg`，ofc no way
- ADB 安装应用 / adb install
  - 需要大改 JNI 的实现因此不做
  - 可以推送文件之后使用终端安装或手动控制安装
- 有线控制 / fastboot
  - 左转甲壳虫

## Change Log

\> [CHANGELOG.md](CHANGELOG.md)

## 建议搭配模块

- 密码锁屏无法捕获: [LSPosed/DisableFlagSecure](https://github.com/LSPosed/DisableFlagSecure)
- 开机自动启用 adb: [gist/906291](https://gist.github.com/Miuzarte/9062915f1615d5eebd363c759fda496c)

## FAQ

0. 控制不了
   - [Genymobile/scrcpy/FAQ.md#control-issues](https://github.com/Genymobile/scrcpy/blob/master/FAQ.md#control-issues)

1. 切到后台后 ADB 断连
   - 将国产 ROM 中的 `省电策略` 调整至 `无限制`
   - 将安卓原生设置中的 `允许后台使用` 启用并设置为 `无限制` (应用设置页中有入口)

2. 虚拟屏不显示输入法 / 输入法显示在主屏幕
   - 将 `--display-ime-policy` 设置为 `local`
   - 自行在悬浮球中拉起本机输入法

3. 码率只能拉到 40Mbps 嫌低
   - 每个 Slider 选项的标题都可以点开自己输入值

4. 录制/下载的文件在哪
   - /sdcard/Movies/Scrcpy/
   - /sdcard/Download/Scrcpy/

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

- [Genymobile/scrcpy](https://github.com/Genymobile/scrcpy) (包括图标)
- JNI ADB 实现: [rikkaapps/shizuku](https://github.com/rikkaapps/shizuku), [vvb2060/ndk.boringssl](https://github.com/vvb2060), [lsposed/libcxx](https://github.com/lsposed/libcxx)
- 界面组件: [YuKongA/miuix](https://github.com/compose-miuix-ui/miuix)
- 界面设计参考: [tiann/KernelSU/manager](https://github.com/tiann/KernelSU/tree/main/manager), [miuix/example](https://github.com/compose-miuix-ui/miuix/tree/main/example)
- 画中画实现参考: [ClassicOldSong/moonlight-android](https://github.com/ClassicOldSong/moonlight-android)
- 原生应用设置页跳转: [YifePlayte/WOMMO](https://github.com/YifePlayte/WOMMO)
- 终端实现: [reapercanuk39/termux-kotlin-app](https://github.com/reapercanuk39/termux-kotlin-app) (仅 Apache 2.0 部分)

## License

[Apache License 2.0](LICENSE)
