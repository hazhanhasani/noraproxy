package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.util.Utils
import java.net.URI

/** Keep subscription management inside NoraProxy; never open the upstream light-themed UI. */
internal object NoraGroupRepository {
    fun validate(name: String, url: String, currentGuid: String? = null): String? {
        if (name.isBlank() || name.length > 64) return "نام گروه باید بین ۱ تا ۶۴ نویسه باشد"
        if (url.length > 4096) return "لینک اشتراک بیش از حد طولانی است"
        if (url.isNotBlank()) {
            val uri = runCatching { URI(url) }.getOrNull()
            val host = uri?.host?.lowercase()
            if (uri == null || !uri.scheme.equals("https", true) ||
                host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null ||
                host == "localhost" || host.endsWith(".localhost") ||
                host.endsWith(".local") || host == "0.0.0.0") {
                return "لینک اشتراک باید یک آدرس HTTPS معتبر باشد"
            }
            if (MmkvManager.decodeSubscriptions().any {
                    it.guid != currentGuid && it.subscription.url == url
                }) return "این لینک قبلاً به گروه دیگری اضافه شده است"
        }
        return null
    }

    fun save(existing: SubscriptionCache?, label: String, inputUrl: String, automatic: Boolean): String {
        val name = label.trim()
        val url = inputUrl.trim()
        require(validate(name, url, existing?.guid) == null) { "Invalid subscription group" }
        val guid = existing?.guid ?: Utils.getUuid()
        val item = existing?.subscription?.copy() ?: SubscriptionItem()
        item.remarks = name
        item.url = url
        item.enabled = true
        item.autoUpdate = automatic && url.isNotBlank()
        MmkvManager.encodeSubscription(guid, item)
        SubscriptionUpdater.syncOne(subId = guid)
        SettingsChangeManager.makeSetupGroupTab()
        return guid
    }

    fun remove(groupId: String) {
        require(groupId.isNotBlank()) { "Group ID is missing" }
        SettingsManager.removeSubscriptionWithDefault(groupId)
        SettingsChangeManager.makeSetupGroupTab()
    }
}

@Composable
internal fun NoraGroupEditorDialog(
    existing: SubscriptionCache?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit
) {
    key(existing?.guid) {
        var name by remember { mutableStateOf(existing?.subscription?.remarks.orEmpty()) }
        var url by remember { mutableStateOf(existing?.subscription?.url.orEmpty()) }
        var autoUpdate by remember { mutableStateOf(existing?.subscription?.autoUpdate ?: false) }
        val error = NoraGroupRepository.validate(name.trim(), url.trim(), existing?.guid)

        AlertDialog(
            onDismissRequest = { if (!busy) onDismiss() },
            containerColor = Color(0xFF10253C),
            titleContentColor = Color(0xFFF5FAFF),
            textContentColor = Color(0xFFB8CBD8),
            title = {
                Text(if (existing == null) "گروه اشتراک جدید" else "ویرایش گروه اشتراک",
                    fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Text("برای کانفیگ‌های خام، فیلد لینک را خالی بگذارید.",
                        color = Color(0xFFB8CBD8))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(64) },
                        label = { Text("نام گروه") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it.take(4096) },
                        label = { Text("لینک HTTPS (اختیاری)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("به‌روزرسانی خودکار", color = Color(0xFFF5FAFF))
                        Switch(
                            checked = autoUpdate && url.isNotBlank(),
                            onCheckedChange = { autoUpdate = it },
                            enabled = url.isNotBlank() && !busy
                        )
                    }
                    if (error != null && name.isNotBlank()) {
                        Text(error, color = Color(0xFFFFADAE))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onSave(name.trim(), url.trim(), autoUpdate) },
                    enabled = !busy && error == null
                ) { Text(if (busy) "در حال ذخیره..." else "ذخیره", color = Color(0xFF1EE2E9)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text("انصراف", color = Color(0xFFB8CBD8))
                }
            }
        )
    }
}

@Composable
internal fun NoraDeleteGroupDialog(
    name: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Color(0xFF10253C),
        title = { Text("حذف گروه", color = Color.White) },
        text = {
            Text(
                "گروه «$name» و تمام سرورهای آن حذف می‌شوند. این عملیات برگشت‌پذیر نیست.",
                color = Color(0xFFB8CBD8)
            )
        },
        confirmButton = {
            TextButton(onClick = onDelete, enabled = !busy) {
                Text("حذف گروه", color = Color(0xFFFFADAE))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("انصراف", color = Color(0xFFB8CBD8))
            }
        }
    )
}
