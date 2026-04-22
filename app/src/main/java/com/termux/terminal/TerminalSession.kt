package com.termux.terminal

class TerminalSession(
    private val shellWriter: (ByteArray, Int, Int) -> Unit,
    private val onScreenUpdated: () -> Unit,
    private val onCopyTextToClipboardRequested: (String) -> Unit,
    private val onPasteTextFromClipboardRequested: () -> Unit,
    private val onBellRequested: () -> Unit,
) : TerminalOutput() {

    var emulator: TerminalEmulator = createEmulator()
        private set

    fun append(data: ByteArray, count: Int) {
        if (count <= 0) return
        emulator.append(data, count)
        onScreenUpdated()
    }

    fun reset() {
        emulator = createEmulator()
        onScreenUpdated()
    }

    fun updateSize(
        columns: Int,
        rows: Int,
        cellWidthPixels: Int,
        cellHeightPixels: Int,
    ) {
        emulator.resize(columns, rows, cellWidthPixels, cellHeightPixels)
        onScreenUpdated()
    }

    override fun write(data: ByteArray, offset: Int, count: Int) {
        shellWriter(data, offset, count)
    }

    fun writeCodePoint(prependEscape: Boolean, codePoint: Int) {
        if (codePoint > 0x10FFFF || codePoint in 0xD800..0xDFFF) {
            throw IllegalArgumentException("Invalid code point: $codePoint")
        }
        val utf8 = StringBuilder()
        if (prependEscape) {
            utf8.append('\u001B')
        }
        utf8.appendCodePoint(codePoint)
        write(utf8.toString())
    }

    override fun titleChanged(oldTitle: String?, newTitle: String?) = Unit

    override fun onCopyTextToClipboard(text: String?) {
        if (!text.isNullOrEmpty()) {
            onCopyTextToClipboardRequested(text)
        }
    }

    override fun onPasteTextFromClipboard() {
        onPasteTextFromClipboardRequested()
    }

    override fun onBell() {
        onBellRequested()
    }

    override fun onColorsChanged() {
        onScreenUpdated()
    }

    private fun createEmulator(): TerminalEmulator {
        return TerminalEmulator(
            mSession = this,
            columns = 80,
            rows = 24,
            cellWidthPixels = 1,
            cellHeightPixels = 1,
            transcriptRows = null,
            client = null,
        )
    }
}
