<!-- markdownlint-disable MD024 -->

# TODO

- 新的原创图标而非从 scrcpy 的图标派生

## scrcpy v4.0

### 协议变更 (breaking)

- 会话包 (session packet)：视频流新增 12 字节会话包 (替代旧的 codec meta 中包含的 width/height)，携带帧宽高 + `client_resized` 标志位
  - `Scrcpy.kt:1091-1095` 当前读视频 meta 12 字节 (codec+width+height)，需改为只读 4 字节 codec id
  - `Scrcpy.kt:1157-1166` 视频读取循环需新增分支：bit 63=1 → 解析 session packet (width/height/flags)；bit 63=0 → 媒体包
  - `SessionInfo` 的 width/height 不再从 init meta 获得，需从首个 session packet 或第一帧捕获后填充
  - 音频流无 session packet，`attachAudioConsumer()` 不受影响

- 帧头 (frame header)：PTS 从 `u62` 改为 `u61`，新增 `media_packet_flag` 位 (最高 bit = 1 表示会话包，= 0 表示媒体包)
  - `Scrcpy.kt:1728` `PACKET_FLAG_CONFIG`: `1L shl 63` → `1L shl 62`
  - `Scrcpy.kt:1729` `PACKET_FLAG_KEY_FRAME`: `1L shl 62` → `1L shl 61`
  - `Scrcpy.kt:1730` `PACKET_PTS_MASK`: `(1L shl 62) - 1` → `(1L shl 61) - 1`
  - 新增 `PACKET_FLAG_SESSION = 1L shl 63`
  - 视频读取 `Scrcpy.kt:1168` 和音频读取 `Scrcpy.kt:1215` 的 flag 解析逻辑须同步更新

- 流元数据：`sendCodecMeta` → `sendStreamMeta`，`writeVideoHeader()` 不再发送 width/height (只发送 codec id 4 字节)
  - `ServerParams.kt` 当前未传递此参数，无需改动 (v4.0 server 默认 `sendStreamMeta=true`)
  - `Scrcpy.kt:1091-1095` 改为只读 `vInput.readInt()` (仅 codec id)

- 新增 4 个控制消息类型：
  - `TYPE_CAMERA_SET_TORCH` (18) — 1 字节 bool
    - `Scrcpy.kt:1739` 需新增常量 + `ControlWriter` 新增 `setCameraTorch(on: Boolean)` 方法
  - `TYPE_CAMERA_ZOOM_IN` (19) / `TYPE_CAMERA_ZOOM_OUT` (20) — 无参数
    - 同上，各一个无参方法
  - `TYPE_RESIZE_DISPLAY` (21) — 2×uint16 (width, height)
    - 同上，新增 `resizeDisplay(w: Int, h: Int)` 方法；配合 flex display 功能使用

- 版本号：`Scrcpy.kt:121` `DEFAULT_SERVER_VERSION` 需从 `"3.3.4"` 改为 `"4.0"`，否则 v4.0 server 拒绝连接

### 新参数

- (`-x` / `--flex-display`)：Flex display 虚拟显示器大小连续跟随窗口缩放
- `--keep-active`：每 4s 模拟用户活动防止息屏
- `--camera-zoom`：镜头初始缩放值 (maybeTODO: 虚拟按键调整缩放)
- `--camera-torch`：手电筒初始启用
- `--min-size-alignment`：强制视频尺寸对齐倍数 (power-of-2)

## PARAMS

- ~~orientation locking~~

## FEATURES
