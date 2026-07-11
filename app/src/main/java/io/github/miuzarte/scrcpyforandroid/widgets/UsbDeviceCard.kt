package io.github.miuzarte.scrcpyforandroid.widgets

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.nativecore.UsbAdbDeviceWatcher
import io.github.miuzarte.scrcpyforandroid.nativecore.UsbDeviceInfo
import io.github.miuzarte.scrcpyforandroid.nativecore.UsbDeviceEvent
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * USB设备卡片组件（Miuix风格 / 紧凑边距 / 参无线配对卡片）
 *
 * 三种状态：
 *   无设备    → 图标 + "未连接" / "使用 OTG 线连接设备"
 *   设备已插入（无权限）→ 图标 + "检测到设备" / "点击允许APP访问…"（可点击）
 *   设备已授权            → 图标 + 设备名 / "点击卡片连接设备"（可点击连接）
 */
@Composable
fun UsbDeviceCard(
    modifier: Modifier = Modifier,
    onDeviceClick: (UsbDeviceInfo) -> Unit
) {
    val context = LocalContext.current
    val usbWatcher = remember { UsbAdbDeviceWatcher(context) }
    val devices by usbWatcher.devicesFlow.collectAsState()
    val events by usbWatcher.eventsFlow.collectAsState()

    // 设备插拔 Snackbar 提示（OTG 两段枚举会触发多次广播，按事件+设备 500ms 去重）
    val lastEventSnackbarKey = remember { mutableStateOf<Pair<String, Long>?>(null) }
    LaunchedEffect(events) {
        val evt = events ?: return@LaunchedEffect
        val dev = when (evt) {
            is UsbDeviceEvent.Attached -> evt.device
            is UsbDeviceEvent.Detached -> evt.device
            else -> return@LaunchedEffect
        }
        val key = "${evt::class.simpleName}:${dev.vendorId}:${dev.productId}:${dev.deviceId}"
        val now = SystemClock.elapsedRealtime()
        val last = lastEventSnackbarKey.value
        if (last?.first != key || now - last.second > 500L) {
            lastEventSnackbarKey.value = key to now
            if (evt is UsbDeviceEvent.Attached) AppRuntime.snackbar(R.string.usb_device_detected)
            else AppRuntime.snackbar(R.string.usb_device_disconnected)
        }
    }

    LaunchedEffect(Unit) { usbWatcher.startWatching() }
    DisposableEffect(Unit) { onDispose { usbWatcher.stopWatching() } }

    // 外层 Card 自身已带内边距，不再额外包裹 Column(padding)
    Card(modifier = modifier.fillMaxWidth()) {
        when {
            devices.isEmpty() -> {
                UsbPlaceholderItem()
            }
            else -> {
                devices.forEach { deviceInfo ->
                    UsbDeviceItem(
                        deviceInfo = deviceInfo,
                        onClick = {
                            if (!deviceInfo.hasPermission) {
                                AppRuntime.snackbar(R.string.usb_permission_request_hint)
                                usbWatcher.requestUsbPermission(deviceInfo.device)
                            } else {
                                onDeviceClick(deviceInfo)
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * USB卡片图标（统一28dp尺寸）
 */
@Composable
private fun UsbCardIcon(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_usb),
        contentDescription = null,
        modifier = modifier.size(28.dp),
        tint = colorScheme.onSurfaceVariantSummary,
    )
}

/**
 * USB设备项（已插入状态）
 *
 *   无权限 → "检测到设备" / "点击允许APP访问 {设备型号}"
 *   已授权 → 设备名 / "点击卡片连接设备"
 *
 * 边距参照无线配对卡片：仅保留与 Card 内边距一致的左右边距
 */
@Composable
private fun UsbDeviceItem(
    deviceInfo: UsbDeviceInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        showIndication = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiSpacing.Large, vertical = UiSpacing.PageItem),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UsbCardIcon()
            Spacer(Modifier.width(UiSpacing.Medium))

            Column(modifier = Modifier.weight(1f)) {
                if (deviceInfo.hasPermission) {
                    Text(
                        text = deviceInfo.getDisplayName(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.usb_card_tap_to_connect),
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.usb_card_device_detected),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(R.string.usb_card_tap_for_permission, deviceInfo.getDisplayName()),
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * 未连接占位项（无设备时显示）
 *
 * 边距与 UsbDeviceItem 一致，确保视觉统一
 */
@Composable
private fun UsbPlaceholderItem(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = UiSpacing.Large, vertical = UiSpacing.PageItem),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UsbCardIcon()
        Spacer(Modifier.width(UiSpacing.Medium))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.usb_card_not_connected),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.usb_card_hint_subtitle),
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
        }
    }
}
