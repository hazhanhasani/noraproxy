package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.RulesetItem

/**
 * A full Nora-native routing rule editor. Edits are committed by the parent
 * through the pinned SettingsManager; no configuration is persisted while typing.
 */
@Composable
internal fun NoraRoutingRuleEditor(
    current: RulesetItem?,
    onDismiss: () -> Unit,
    onSave: (RulesetItem) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    key(current?.id ?: "nora-new-routing-rule") {
        var remarks by rememberSaveable { mutableStateOf(current?.remarks.orEmpty()) }
        var domains by rememberSaveable { mutableStateOf(current?.domain?.joinToString(",").orEmpty()) }
        var ips by rememberSaveable { mutableStateOf(current?.ip?.joinToString(",").orEmpty()) }
        var processes by rememberSaveable { mutableStateOf(current?.process?.joinToString(",").orEmpty()) }
        var protocols by rememberSaveable { mutableStateOf(current?.protocol?.joinToString(",").orEmpty()) }
        var ports by rememberSaveable { mutableStateOf(current?.port.orEmpty()) }
        var network by rememberSaveable { mutableStateOf(current?.network.orEmpty()) }
        var outbound by rememberSaveable {
            mutableStateOf(current?.outboundTag?.ifBlank { AppConfig.TAG_PROXY } ?: AppConfig.TAG_PROXY)
        }
        var confirmDelete by rememberSaveable { mutableStateOf(false) }
        val validation = NoraAdvancedValidation.validateRule(
            remarks, domains, ips, processes, protocols, ports, network, outbound
        )
        val pale = Color(0xFFF7F9FF)
        val secondary = Color(0xFFACB8C9)
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Color(0xFF181A22),
            title = {
                Text(if (current == null) "قانون مسیر‌یابی جدید" else "ویرایش قانون",
                    color = pale, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 490.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "تنها پس از «ذخیره» تنظیمات در موتور Xray اعمال می‌شوند. شرط‌ها را با ویرگول جدا کنید.",
                        color = secondary
                    )
                    OutlinedTextField(
                        value = remarks, onValueChange = { remarks = it.take(64) },
                        label = { Text("نام قانون") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = domains, onValueChange = { domains = it.take(4096) },
                        label = { Text("دامنه‌ها (domain/geosite)") },
                        minLines = 2, maxLines = 3, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ips, onValueChange = { ips = it.take(4096) },
                        label = { Text("IP/CIDR/geoip") },
                        minLines = 2, maxLines = 3, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = processes, onValueChange = { processes = it.take(4096) },
                        label = { Text("برنامه یا Process") },
                        maxLines = 3, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ports, onValueChange = { ports = it.take(256) },
                        label = { Text("پورت (443 یا 80,443 یا 1000-2000)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = protocols, onValueChange = { protocols = it.take(4096) },
                        label = { Text("پروتکل‌های قابل شناسایی") },
                        maxLines = 3, modifier = Modifier.fillMaxWidth()
                    )
                    Text("شبکه", color = pale, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("" to "همه", "tcp" to "TCP", "udp" to "UDP").forEach { (value, label) ->
                            FilterChip(
                                selected = network == value || (network == "tcp,udp" && value == ""),
                                onClick = { network = value },
                                label = { Text(label) }
                            )
                        }
                    }
                    Text("خروجی", color = pale, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(
                            AppConfig.TAG_PROXY to "VPN",
                            AppConfig.TAG_DIRECT to "مستقیم",
                            AppConfig.TAG_BLOCKED to "مسدود"
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = outbound == value,
                                onClick = { outbound = value },
                                label = { Text(label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = outbound, onValueChange = { outbound = it.take(128) },
                        label = { Text("شناسه خروجی سفارشی") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    if (validation != null) {
                        Text(validation, color = Color(0xFFFFB5A9))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = validation == null,
                    onClick = {
                        if (validation == null) {
                            onSave(NoraAdvancedValidation.buildRule(
                                current, remarks, domains, ips, processes,
                                protocols, ports, network, outbound
                            ))
                        }
                    }
                ) { Text("ذخیره", color = Color(0xFF86B5FF)) }
            },
            dismissButton = {
                Row {
                    if (current != null && current.locked != true && onDelete != null) {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("حذف", color = Color(0xFFFFB5A9))
                        }
                    }
                    TextButton(onClick = onDismiss) { Text("انصراف", color = secondary) }
                }
            }
        )
        if (confirmDelete && onDelete != null) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                containerColor = Color(0xFF181A22),
                title = { Text("حذف قانون", color = pale) },
                text = { Text("این قانون حذف شود؟ تغییرات بعد از اتصال مجدد اعمال می‌شوند.", color = secondary) },
                confirmButton = {
                    TextButton(onClick = { confirmDelete = false; onDelete() }) {
                        Text("حذف قطعی", color = Color(0xFFFFB5A9))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) {
                        Text("انصراف", color = secondary)
                    }
                }
            )
        }
    }
}

@Composable
internal fun NoraDnsServersEditor(
    currentRemote: String,
    currentDirect: String,
    currentVpn: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var remote by rememberSaveable { mutableStateOf(currentRemote) }
    var direct by rememberSaveable { mutableStateOf(currentDirect) }
    var vpn by rememberSaveable { mutableStateOf(currentVpn) }
    val error = NoraAdvancedValidation.validateDnsCsv(remote, false)
        ?: NoraAdvancedValidation.validateDnsCsv(direct, false)
        ?: NoraAdvancedValidation.validateDnsCsv(vpn, true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181A22),
        title = { Text("سرورهای DNS", color = Color(0xFFF7F9FF),
            fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("IP یا آدرس DoH/TCP/QUIC معتبر وارد کنید. جداکننده: ویرگول. خالی یعنی بازگشت به پیش‌فرض Xray.",
                    color = Color(0xFFACB8C9))
                OutlinedTextField(
                    value = remote, onValueChange = { remote = it.take(4096) },
                    label = { Text("DNS پروکسی (Remote)") },
                    minLines = 2, maxLines = 3, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = direct, onValueChange = { direct = it.take(4096) },
                    label = { Text("DNS مستقیم (Direct)") },
                    minLines = 2, maxLines = 3, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = vpn, onValueChange = { vpn = it.take(4096) },
                    label = { Text("DNS تونل VPN — فقط IP") },
                    minLines = 2, maxLines = 3, modifier = Modifier.fillMaxWidth()
                )
                if (error != null) Text(error, color = Color(0xFFFFB5A9))
                Text("تنظیمات ذخیره می‌شوند اما اتصال فعال ممکن است به قطع و وصل مجدد نیاز داشته باشد.",
                    color = Color(0xFFACB8C9))
            }
        },
        confirmButton = {
            TextButton(enabled = error == null, onClick = {
                if (error == null) onSave(remote.trim(), direct.trim(), vpn.trim())
            }) { Text("ذخیره", color = Color(0xFF86B5FF)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف", color = Color(0xFFACB8C9)) }
        }
    )
}
