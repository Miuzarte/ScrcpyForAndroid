package io.github.miuzarte.scrcpyforandroid

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Password
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.fragment.app.FragmentActivity
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.password.BiometricGate
import io.github.miuzarte.scrcpyforandroid.password.PasswordCreatedState
import io.github.miuzarte.scrcpyforandroid.password.PasswordEntry
import io.github.miuzarte.scrcpyforandroid.password.PasswordRepository
import io.github.miuzarte.scrcpyforandroid.password.PasswordSanitizer
import io.github.miuzarte.scrcpyforandroid.scaffolds.LazyColumn
import io.github.miuzarte.scrcpyforandroid.scaffolds.ReorderableList
import io.github.miuzarte.scrcpyforandroid.services.LocalSnackbarController
import io.github.miuzarte.scrcpyforandroid.services.SnackbarController
import io.github.miuzarte.scrcpyforandroid.storage.Settings
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import io.github.miuzarte.scrcpyforandroid.ui.createThemeController
import io.github.miuzarte.scrcpyforandroid.ui.rememberBlurBackdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SpinnerDefaults
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.SpinnerItemImpl
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles

class LockscreenPasswordActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        PasswordRepository.refresh()
        setContent {
            val asBundle by appSettings.bundleState.collectAsState()
            val hostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            val snackbarController = remember(scope, hostState) {
                SnackbarController(scope = scope, hostState = hostState)
            }
            val themeController = remember(
                asBundle.themeBaseIndex,
                asBundle.monet,
                asBundle.monetSeedIndex,
                asBundle.monetPaletteStyle,
                asBundle.monetColorSpec,
            ) {
                asBundle.createThemeController()
            }
            MiuixTheme(
                controller = themeController,
                smoothRounding = asBundle.smoothCorner,
            ) {
                CompositionLocalProvider(
                    LocalSnackbarController provides snackbarController,
                ) {
                    LockscreenPasswordScreen(
                        activity = this,
                        hostState = hostState,
                    )
                }
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, LockscreenPasswordActivity::class.java)
        }
    }
}

private enum class PasswordDialogMode {
    Create,
    Rename,
}

@Composable
private fun LockscreenPasswordScreen(
    activity: LockscreenPasswordActivity,
    hostState: SnackbarHostState,
) {
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()
    val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val scrollBehavior = MiuixScrollBehavior()

    val asBundleShared by appSettings.bundleState.collectAsState()
    val asBundleSharedLatest by rememberUpdatedState(asBundleShared)
    var asBundle by rememberSaveable(asBundleShared) { mutableStateOf(asBundleShared) }
    val asBundleLatest by rememberUpdatedState(asBundle)
    LaunchedEffect(asBundleShared) {
        if (asBundle != asBundleShared) {
            asBundle = asBundleShared
        }
    }
    LaunchedEffect(asBundle) {
        delay(Settings.BUNDLE_SAVE_DELAY)
        if (asBundle != asBundleSharedLatest) {
            appSettings.saveBundle(asBundle)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            taskScope.launch { appSettings.saveBundle(asBundleLatest) }
        }
    }

    val entries by PasswordRepository.entriesState.collectAsState()
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var pendingCreate by rememberSaveable { mutableStateOf(false) }
    var showRiskDialog by rememberSaveable { mutableStateOf(!BiometricGate.isDeviceSecure()) }
    var showDisableDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogMode by rememberSaveable { mutableStateOf<PasswordDialogMode?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorInitialName by rememberSaveable { mutableStateOf("") }

    DisposableEffect(Unit) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (
                event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE ||
                event == androidx.lifecycle.Lifecycle.Event.ON_STOP
            ) {
                editorInitialName = ""
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(pendingCreate, showMenu) {
        if (!pendingCreate || showMenu) return@LaunchedEffect
        if (asBundle.passwordRequireAuth) {
            val ok = BiometricGate.authenticate(
                activity = activity,
                title = "验证以创建新密码",
            )
            if (!ok) {
                snackbar.show("认证失败")
                pendingCreate = false
                return@LaunchedEffect
            }
        }
        dialogMode = PasswordDialogMode.Create
        editingId = null
        editorInitialName = ""
        pendingCreate = false
    }

    val blurBackdrop = rememberBlurBackdrop(asBundle.blur)

    Scaffold(
        topBar = {
            TopAppBar(
                title = "锁屏密码自动填充",
                modifier =
                    if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop)
                    else Modifier,
                color =
                    if (blurBackdrop != null) Color.Transparent
                    else colorScheme.surface,
                navigationIcon = {
                    IconButton(onClick = { activity.onBackPressedDispatcher.onBackPressed() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                        ) {
                            Icon(
                                imageVector = MiuixIcons.More,
                                contentDescription = "更多",
                            )
                        }
                        OverlayListPopup(
                            show = showMenu,
                            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                            alignment = PopupPositionProvider.Align.TopEnd,
                            onDismissRequest = { showMenu = false },
                        ) {
                            ListPopupColumn {
                                SpinnerItemImpl(
                                    entry = SpinnerEntry(title = "创建新密码"),
                                    entryCount = 1,
                                    isSelected = false,
                                    index = 0,
                                    spinnerColors = SpinnerDefaults.spinnerColors(),
                                    dialogMode = false,
                                    onSelectedIndexChange = {
                                        showMenu = false
                                        pendingCreate = true
                                    },
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(hostState) },
    ) { pagePadding ->
        LockscreenPasswordPage(
            contentPadding = pagePadding,
            scrollBehavior = scrollBehavior,
            entries = entries,
            requireAuth = asBundle.passwordRequireAuth,
            canAuthenticate = BiometricGate.canAuthenticate(),
            onToggleRequireAuth = { checked ->
                if (checked) {
                    asBundle = asBundle.copy(passwordRequireAuth = true)
                } else {
                    if (entries.any { it.cipherText != null }) {
                        showDisableDialog = true
                    } else {
                        asBundle = asBundle.copy(passwordRequireAuth = false)
                    }
                }
            },
            onCreate = {
                pendingCreate = true
            },
            onRename = { entry ->
                dialogMode = PasswordDialogMode.Rename
                editingId = entry.id
                editorInitialName = entry.name
            },
            onDelete = { entry ->
                pendingDeleteId = entry.id
            },
            onMove = { fromIndex, toIndex ->
                val reordered = entries.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
                PasswordRepository.updateOrder(reordered.map { it.id })
            },
        )

        if (showRiskDialog) {
            OverlayDialog(
                show = true,
                title = "当前设备未设置锁屏保护",
                summary = "继续使用将允许在无认证保护的情况下保存和填充锁屏密码",
                defaultWindowInsetsPadding = false,
                onDismissRequest = activity::finish,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
                    TextButton(
                        text = "取消",
                        onClick = activity::finish,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "同意",
                        onClick = { showRiskDialog = false },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }

        if (showDisableDialog) {
            OverlayDialog(
                show = true,
                title = "关闭验证后密码将失去保护",
                summary =
                    """
                        关闭后每次填充密码时将不再强制认证
                        同时会熔断当前经认证创建的密码
                    """.trimIndent(),
                defaultWindowInsetsPadding = false,
                onDismissRequest = { showDisableDialog = false },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
                    TextButton(
                        text = "取消",
                        onClick = { showDisableDialog = false },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "继续关闭",
                        onClick = {
                            scope.launch {
                                if (entries.any { it.cipherText != null }) {
                                    val ok = BiometricGate.authenticate(
                                        activity = activity,
                                        title = "验证以禁用“填充密码时需要验证”",
                                    )
                                    if (!ok) {
                                        snackbar.show("认证失败")
                                        showDisableDialog = false
                                        return@launch
                                    }
                                }
                                asBundle = asBundle.copy(passwordRequireAuth = false)
                                entries.forEach { entry ->
                                    PasswordRepository.update(
                                        entry.copy(
                                            cipherText = entry.cipherText?.copyOf(),
                                            createdWithAuth = entry.createdWithAuth
                                                .takeIf { it != PasswordCreatedState.AuthenticatedCreated }
                                                ?: PasswordCreatedState.AuthenticatedCreatedModified
                                        )
                                    )
                                }
                                showDisableDialog = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }

        pendingDeleteId?.let { deleteId ->
            val target = entries.firstOrNull { it.id == deleteId }
            if (target != null) {
                OverlayDialog(
                    show = true,
                    title = "删除密码",
                    summary = "将删除 ${target.name}",
                    defaultWindowInsetsPadding = false,
                    onDismissRequest = { pendingDeleteId = null },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
                        TextButton(
                            text = "取消",
                            onClick = { pendingDeleteId = null },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = "删除",
                            onClick = {
                                PasswordRepository.delete(target.id)
                                pendingDeleteId = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        }

        PasswordEditorSheet(
            show = dialogMode != null,
            mode = dialogMode ?: PasswordDialogMode.Create,
            initialName = editorInitialName,
            onDismissRequest = {
                dialogMode = null
                editingId = null
                editorInitialName = ""
            },
            onConfirm = { nameInput, passwordInput ->
                val sanitizedName = PasswordSanitizer.filterName(nameInput)
                val resolvedName = sanitizedName.ifBlank { "密码 ${entries.size + 1}" }
                when (dialogMode) {
                    PasswordDialogMode.Create -> {
                        val sanitizedPassword = PasswordSanitizer.filterPassword(passwordInput)
                        val passwordChars = sanitizedPassword.toCharArray()
                        if (passwordChars.isEmpty()) {
                            scope.launch { snackbar.show("密码不能为空") }
                            return@PasswordEditorSheet
                        }
                        PasswordRepository.create(
                            name = resolvedName,
                            cipherText = passwordChars,
                            createdWithAuth = if (asBundle.passwordRequireAuth) {
                                PasswordCreatedState.AuthenticatedCreated
                            } else {
                                PasswordCreatedState.UnauthenticatedCreated
                            },
                        )
                    }

                    PasswordDialogMode.Rename -> {
                        val targetId = editingId ?: return@PasswordEditorSheet
                        PasswordRepository.rename(targetId, resolvedName)
                    }

                    null -> return@PasswordEditorSheet
                }

                dialogMode = null
                editingId = null
                editorInitialName = ""
            },
        )

    }
}

@Composable
private fun LockscreenPasswordPage(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    entries: List<PasswordEntry>,
    requireAuth: Boolean,
    canAuthenticate: Boolean,
    onToggleRequireAuth: (Boolean) -> Unit,
    onCreate: () -> Unit,
    onRename: (PasswordEntry) -> Unit,
    onDelete: (PasswordEntry) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    LazyColumn(
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
        bottomInnerPadding = UiSpacing.PageBottom,
    ) {
        item {
            Card {
                SwitchPreference(
                    title = "填充密码时需要验证",
                    summary =
                        if (canAuthenticate)
                            """
                                关闭后将允许直接填充锁屏密码
                                同时会熔断当前经认证创建的密码
                            """.trimIndent()
                        else "当前设备无认证认证能力",
                    checked = requireAuth,
                    enabled = canAuthenticate || requireAuth,
                    onCheckedChange = onToggleRequireAuth,
                )
            }
        }

        item { Spacer(Modifier.height(UiSpacing.Medium)) }

        if (entries.isEmpty()) item {
            Card {
                ArrowPreference(
                    title = "创建新密码",
                    summary = "或在右上角菜单中",
                    onClick = onCreate,
                )
            }
        }
        else item {
            ReorderableList(
                itemsProvider = {
                    entries.map { entry ->
                        ReorderableList.Item(
                            id = entry.id,
                            icon =
                                if (entry.cipherText == null) Icons.Rounded.Block
                                else Icons.Rounded.Password,
                            title = entry.name,
                            subtitle =
                                if (entry.cipherText == null) "已失效"
                                else when (entry.createdWithAuth) {
                                    PasswordCreatedState.AuthenticatedCreated -> "创建时已验证"
                                    PasswordCreatedState.UnauthenticatedCreated -> "创建时未经验证"
                                    PasswordCreatedState.AuthenticatedCreatedModified -> "创建时已验证（熔断）"
                                },
                            onClick = { if (entry.cipherText != null) onRename(entry) },
                            endActions = listOf(
                                ReorderableList.EndAction.Icon(
                                    icon = Icons.Rounded.Edit,
                                    contentDescription = "编辑名称",
                                    onClick = { if (entry.cipherText != null) onRename(entry) },
                                ),
                                ReorderableList.EndAction.Icon(
                                    icon = Icons.Rounded.DeleteOutline,
                                    contentDescription = "删除密码",
                                    onClick = { onDelete(entry) },
                                ),
                            ),
                        )
                    }
                },
                onSettle = onMove,
            ).invoke()
        }

        item {
            Text(
                text =
                    """
                        免责声明
                        0. 无法保证没有 bug
                        1. 本功能的防护边界仅包括加密存储、按需认证和使用后内存清理，不构成绝对安全保证
                        2. 在 root / posed / hook / 调试器 / 恶意输入法 等环境下，密码仍可能泄露
                        3. 本功能不会绕过系统锁屏认证，仅用于你已合法授权控制的设备
                        4. 关闭“填充密码时需要验证”会显著降低安全性，请谨慎选择
                    """.trimIndent(),
                fontSize = textStyles.body2.fontSize,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = UiSpacing.Large)
                    .padding(horizontal = UiSpacing.Large),
            )
        }
    }
}

@Composable
private fun PasswordEditorSheet(
    show: Boolean,
    mode: PasswordDialogMode,
    initialName: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var nameBuffer by rememberSaveable(mode, show, initialName) { mutableStateOf(initialName) }
    var passwordBuffer by rememberSaveable(mode, show) { mutableStateOf("") }
    OverlayBottomSheet(
        show = show,
        title = if (mode == PasswordDialogMode.Create) "创建新密码" else "重命名密码",
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
        startAction = {
            IconButton(onClick = onDismissRequest) {
                Icon(MiuixIcons.Close, contentDescription = "关闭")
            }
        },
        endAction = {
            IconButton(onClick = { onConfirm(nameBuffer, passwordBuffer) }) {
                Icon(MiuixIcons.Ok, contentDescription = "保存")
            }
        },
    ) {
        Column(
            modifier = Modifier.padding(vertical = UiSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
        ) {
            TextField(
                value = nameBuffer,
                onValueChange = { nameBuffer = it },
                label = "名称",
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UiSpacing.Large),
            )
            AnimatedVisibility(mode == PasswordDialogMode.Create) {
                TextField(
                    value = passwordBuffer,
                    onValueChange = { passwordBuffer = it },
                    label = "锁屏密码",
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.Large),
                )
            }
            Spacer(Modifier.height(UiSpacing.SheetBottom))
        }
    }
}
