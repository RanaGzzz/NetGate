package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Single clipboard helper shared by the DNS and VPN Guard sheets (previously two private copies).
 */
internal fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}
