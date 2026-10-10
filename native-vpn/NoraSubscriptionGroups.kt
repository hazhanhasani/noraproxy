package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.Utils

/** Keep subscription management inside NoraProxy; never open the upstream light-themed UI. */
internal object NoraGroupRepository {
    fun validateName(name: String): String? =
        if (name.isBlank() || name.length > 64)
            "نام گروه باید بین ۱ تا ۶۴ نویسه باشد"
        else null

    /** Renames without changing the URL, auto-update policy, or existing nodes. */
    fun save(existing: SubscriptionCache?, label: String): String {
        val name = label.trim()
        require(validateName(name) == null) { "Invalid subscription group name" }
        val guid = existing?.guid ?: Utils.getUuid()
        val item = (MmkvManager.decodeSubscription(guid) ?: SubscriptionItem()).copy()
        item.remarks = name
        MmkvManager.encodeSubscription(guid, item)
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
    onSave: (String) -> Unit
) {
    key(existing?.guid) {
        var name by remember { mutableStateOf(existing?.subscription?.remarks.orEmpty()) }
        val error = NoraGroupRepository.validateName(name.trim())

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
                    Text("اینجا فقط نام گروه را مشخص کنید. افزودن لینک یا کانفیگ در بخش «افزودن به گروه» انجام می‌شود.",
                        color = Color(0xFFB8CBD8))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(64) },
                        label = { Text("نام گروه") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (error != null && name.isNotBlank()) {
                        Text(error, color = Color(0xFFFFADAE))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onSave(name.trim()) },
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
