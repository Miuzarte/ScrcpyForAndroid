package io.github.miuzarte.scrcpyforandroid.pages

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcuts
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.quickDevices
import io.github.miuzarte.scrcpyforandroid.widgets.ReorderableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.extra.SuperBottomSheet

@Composable
fun ReorderDevicesScreen(
    show: Boolean,
    onDismissRequest: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    val qdBundleShared by quickDevices.bundleState.collectAsState()
    val qdBundleSharedLatest by rememberUpdatedState(qdBundleShared)
    var qdBundle by rememberSaveable(qdBundleShared) { mutableStateOf(qdBundleShared) }
    val qdBundleLatest by rememberUpdatedState(qdBundle)
    LaunchedEffect(qdBundleShared) {
        if (qdBundle != qdBundleShared) {
            qdBundle = qdBundleShared
        }
    }
    LaunchedEffect(qdBundle) {
        delay(Settings.BUNDLE_SAVE_DELAY)
        if (qdBundle != qdBundleSharedLatest) {
            quickDevices.saveBundle(qdBundle)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            taskScope.launch {
                quickDevices.saveBundle(qdBundleLatest)
            }
        }
    }

    var savedShortcuts by remember {
        mutableStateOf(DeviceShortcuts.unmarshalFrom(qdBundle.quickDevicesList))
    }
    LaunchedEffect(qdBundle.quickDevicesList) {
        savedShortcuts = DeviceShortcuts.unmarshalFrom(qdBundle.quickDevicesList)
    }
    LaunchedEffect(savedShortcuts) {
        val serialized = savedShortcuts.marshalToString()
        if (serialized != qdBundle.quickDevicesList) {
            qdBundle = qdBundle.copy(quickDevicesList = serialized)
        }
    }

    SuperBottomSheet(
        show = show,
        title = "快速设备排序",
        onDismissRequest = onDismissRequest,
    ) {
        ReorderableList(
            itemsProvider = {
                savedShortcuts.map { device ->
                    ReorderableList.Item(
                        id = device.id,
                        title = device.name.ifBlank { device.host },
                        subtitle = "${device.host}:${device.port}",
                    )
                }
            },
            onSettle = { fromIndex, toIndex ->
                savedShortcuts = savedShortcuts.move(fromIndex, toIndex)
            },
        ).invoke()
        Spacer(Modifier.height(UiSpacing.SheetBottom))
    }
}
