# TODO

## CURRENT

- Refactoring

## WIDGETS

- `SuperTextField` Click to pop a dialog with custom notes/summary

## PARAMS

- orientation locking
- `-r, --record=file.mp4` 投屏时录制到文件 Record screen to file. The format is determined by the --record-format option if set, or by the file extension.
- `--record-format` 录制格式 Force recording format (mp4, mkv, m4a, mka, opus, aac, flac or wav).
- `-t, --show-touches` 显示受控机的物理触控 Enable "show touches" on start, restore the initial value on exit. It only shows physical touches (not clicks from scrcpy).
- `--no-power-on` 开始投屏时不唤醒屏幕 Do not power on the device on start.
- `--power-off-on-close` 结束投屏时息屏 Turn the device screen off when closing scrcpy.
- `--disable-screensaver` 投屏时禁用自动息屏 Disable screensaver while scrcpy is running.
- `--screen-off-timeout=seconds` 投屏过程中的息屏时间 Set the screen off timeout while scrcpy is running (restore the initial value on exit).
- `--require-audio` This option makes scrcpy fail if audio is enabled but does not work.
- `--no-vd-destroy-content` 投屏结束关闭虚拟显示器时不结束进程 Disable virtual display "destroy content on removal" flag. With this option, when the virtual display is closed, the running apps are moved to the main display rather than being destroyed.
- `--no-vd-system-decorations` Disable virtual display system decorations flag.

## FEATURES

- 设置项连接设备后马上启用scrcpy会话

### LOWER PRIORITY

顺序无关

- 多配置切换
- [音频延迟](https://developer.android.com/ndk/guides/audio/audio-latency)
- 横屏布局
- 原生悬浮窗 [Petterpx/FloatingX](https://github.com/Petterpx/FloatingX)
