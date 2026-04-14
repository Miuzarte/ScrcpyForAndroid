# 低延迟音频_kotlin_side

## 使用“低延迟模式”标志

```kotlin
val audioTrack = AudioTrack.Builder()
    .setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME) // 游戏用途延迟最低
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        // 请求低延迟硬件标志
        .setFlags(AudioAttributes.FLAG_LOW_LATENCY) 
        .build())
    .setAudioFormat(AudioFormat.Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT) // TODO: 查看 scrcpy 官方仓库了解实际 PCM 参数 `B:\Git\scrcpy`
        .setSampleRate(48000) // 匹配硬件原生采样率
        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
        .build())
    // 性能模式设置为 LOW_LATENCY
    .setBufferSizeInBytes(minBufferSize)
    .setTransferMode(AudioTrack.MODE_STREAM)
    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) 
    .build()

// audioTrack.performanceMode 检查结果
```

## 匹配“原生采样率”与“缓冲区步长”

```kotlin
val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

// 获取原生采样率 (通常是 48000)
val sampleRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toInt() ?: 48000

// 获取硬件每秒处理的帧数步长 (Frames Per Burst)
val framesPerBurst = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toInt() ?: 128

val minBuffer = AudioTrack.getMinBufferSize(
    sampleRate,
    AudioFormat.CHANNEL_OUT_STEREO,
    AudioFormat.ENCODING_PCM_16BIT
)

// 16bit 2 bytes
// stereo 2 channels
// 双缓冲 2 个 burst
val targetBuffer = framesPerBurst * 2 * 2 * 2

val bufferSize = max(minBuffer, targetBuffer)
```

## 使用 WRITE_NON_BLOCKING // 已用

## audioTrack 启动预填充静音音频 // 没必要做

## 查看 scrcpy-server 回传的 PCM 具体参数 // 先搁置

如果音频源支持，直接使用 AudioFormat.ENCODING_PCM_FLOAT

## 获取并应用系统“最优缓冲区大小”

```kotlin
// 获取硬件每一跳的帧数 (例如 128, 256 或 512)
val framesPerBurst = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER).toInt()
// 缓冲区设置为 2 倍的 Burst 帧数（双缓冲），这是理论上的延迟平衡点
val optimizedBufferSize = framesPerBurst * 2 * (if (isStereo) 2 else 1) * (if (isFloat) 4 else 2)
```

## 绕过音频效果器

```kotlin
.setAudioAttributes(AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_GAME)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
    // 告诉系统不要过各种 DSP 效果器
    .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE) 
    .build())
```

## 音频线程优先级

```kotlin
// 在负责读取 Socket 并写入 AudioTrack 的线程内执行
// 单独线程 + 无锁队列，跟网络线程分离
android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
```

## 缓冲区复用减少 GC

```kotlin
val buffer = ByteArray(FIXED_SIZE)
```
