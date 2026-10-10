package com.v2ray.ang.ui.main

import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** All text/QR/gallery/clipboard/file imports share the same automatic routing. */
internal object NoraImportCoordinator {
    fun accept(activity: MainActivity, viewModel: MainViewModel, payload: String) {
        fun message(text: String) {
            Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
        }
        when (val input = NoraImportRouter.classify(payload)) {
            is NoraImportPayload.Invalid -> message(input.reason)
            is NoraImportPayload.Raw -> {
                activity.lifecycleScope.launch {
                    val count = withContext(Dispatchers.IO) {
                        val defaultId = NoraImportRouter.ensureRawDefaultGroup()
                        // Explicitly target Default, never the currently selected group.
                        AngConfigManager.importBatchConfig(input.text, defaultId, true).first
                    }
                    if (count > 0) {
                        viewModel.setupGroupTab(forceRefresh = true).join()
                        message("تعداد $count کانفیگ خام به گروه Default اضافه شد")
                    } else {
                        message("کانفیگ معتبری دریافت نشد؛ اطلاعات قبلی حفظ شدند")
                    }
                }
            }
            is NoraImportPayload.Subscriptions -> {
                activity.lifecycleScope.launch {
                    val report = withContext(Dispatchers.IO) {
                        var created = 0
                        var updated = 0
                        var failed = 0
                        var lastId: String? = null
                        input.urls.forEach { url ->
                            try {
                                val resolved = NoraImportRouter.createOrFindSubscription(url)
                                if (resolved.created) created++
                                val current = MmkvManager.decodeSubscription(resolved.guid)
                                if (current == null) {
                                    failed++
                                } else {
                                    // Fetch only this URL's group, never all subscriptions.
                                    val result = AngConfigManager.updateConfigViaSub(
                                        SubscriptionCache(resolved.guid, current)
                                    )
                                    if (result.successCount > 0) updated++ else failed++
                                    lastId = resolved.guid
                                }
                            } catch (_: Exception) {
                                failed++
                            }
                        }
                        Triple(created, updated, Pair(failed, lastId))
                    }

                    viewModel.setupGroupTab(forceRefresh = true).join()
                    val newSelection = report.third.second
                    // Switching groups during an active VPN session is unsafe.
                    if (!viewModel.uiState.value.isRunning && newSelection != null) {
                        viewModel.subscriptionIdChanged(newSelection)
                    }
                    message(
                        "گروه جدید: ${report.first} · دریافت موفق: ${report.second}" +
                            if (report.third.first > 0)
                                " · دریافت ناموفق: ${report.third.first} (امکان تلاش مجدد)"
                            else ""
                    )
                }
            }
        }
    }
}
