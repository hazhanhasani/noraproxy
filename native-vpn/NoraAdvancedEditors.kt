package com.v2ray.ang.ui.main

import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
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

    val themeColors = LocalNoraPalette.current
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
        val pale = themeColors.light
        val secondary = themeColors.muted
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = themeColors.surface2,
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
                        Text(validation, color = themeColors.error)
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
                ) { Text("ذخیره", color = themeColors.cyan) }
            },
            dismissButton = {
                Row {
                    if (current != null && current.locked != true && onDelete != null) {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("حذف", color = themeColors.error)
                        }
                    }
                    TextButton(onClick = onDismiss) { Text("انصراف", color = secondary) }
                }
            }
        )
        if (confirmDelete && onDelete != null) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                containerColor = themeColors.surface2,
                title = { Text("حذف قانون", color = pale) },
                text = { Text("این قانون حذف شود؟ تغییرات بعد از اتصال مجدد اعمال می‌شوند.", color = secondary) },
                confirmButton = {
                    TextButton(onClick = { confirmDelete = false; onDelete() }) {
                        Text("حذف قطعی", color = themeColors.error)
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

    val themeColors = LocalNoraPalette.current
    var remote by rememberSaveable { mutableStateOf(currentRemote) }
    var direct by rememberSaveable { mutableStateOf(currentDirect) }
    var vpn by rememberSaveable { mutableStateOf(currentVpn) }
    val error = NoraAdvancedValidation.validateDnsCsv(remote, false)
        ?: NoraAdvancedValidation.validateDnsCsv(direct, false)
        ?: NoraAdvancedValidation.validateDnsCsv(vpn, true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = themeColors.surface2,
        title = { Text("سرورهای DNS", color = themeColors.light,
            fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("IP یا آدرس DoH/TCP/QUIC معتبر وارد کنید. جداکننده: ویرگول. خالی یعنی بازگشت به پیش‌فرض Xray.",
                    color = themeColors.muted)
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
                if (error != null) Text(error, color = themeColors.error)
                Text("تنظیمات ذخیره می‌شوند اما اتصال فعال ممکن است به قطع و وصل مجدد نیاز داشته باشد.",
                    color = themeColors.muted)
            }
        },
        confirmButton = {
            TextButton(enabled = error == null, onClick = {
                if (error == null) onSave(remote.trim(), direct.trim(), vpn.trim())
            }) { Text("ذخیره", color = themeColors.cyan) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف", color = themeColors.muted) }
        }
    )
}


/** Safe MTU editing: the service reads the same upstream MMKV field. */
@Composable
internal fun NoraVpnMtuEditor(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val colors = LocalNoraPalette.current
    var value by rememberSaveable(current) { mutableStateOf(current) }
    val error = NoraAdvancedValidation.validateVpnMtu(value)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface2,
        title = {
            Text("اندازه بسته VPN (MTU)", color = colors.light,
                fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "مقدار پیش‌فرض ۱۵۰۰ است. در شبکه‌هایی با مشکل قطع اتصال یا باز نشدن بعضی سایت‌ها می‌توانید مقدار ۱۲۸۰ تا ۱۵۰۰ را آزمایش کنید.",
                    color = colors.muted
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(5) },
                    label = { Text("MTU") },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) Text(error, color = colors.error)
                Text("اعمال مقدار جدید ممکن است به قطع و وصل مجدد VPN نیاز داشته باشد.",
                    color = colors.muted)
            }
        },
        confirmButton = {
            TextButton(enabled = error == null, onClick = {
                if (error == null) onSave(value.ifBlank { AppConfig.VPN_MTU.toString() })
            }) { Text("ذخیره", color = colors.cyan) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { value = AppConfig.VPN_MTU.toString() }) {
                    Text("پیش‌فرض", color = colors.cyan)
                }
                TextButton(onClick = onDismiss) {
                    Text("انصراف", color = colors.muted)
                }
            }
        }
    )
}
