package com.v2ray.ang.ui.main

import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Single entry point for paste, raw links, camera/gallery QR, clipboard and files. */
internal object NoraImportCoordinator {
    fun accept(activity: MainActivity, viewModel: MainViewModel, payload: String) {
        fun message(value: String) {
            Toast.makeText(activity, value, Toast.LENGTH_LONG).show()
        }

        val selectedId = viewModel.uiState.value.selectedGroupId
        val selectedGroup = MmkvManager.decodeSubscription(selectedId)
        if (selectedId.isBlank() || selectedGroup == null) {
            message("ابتدا یک گروه اشتراک بسازید و انتخاب کنید")
            return
        }

        when (val input = NoraImportRouter.classify(payload)) {
            is NoraImportPayload.Invalid -> message(input.reason)
            is NoraImportPayload.Raw -> {
                // Upstream replaces subscription group servers on refresh. Keep raw nodes
                // in their own local group so periodic updates cannot erase them.
                if (selectedGroup.url.isNotBlank()) {
                    message("برای کانفیگ خام، یک گروه مستقل بدون لینک اشتراک انتخاب کنید")
                } else {
                    viewModel.onAction(MainAction.ImportBatchConfig(input.text))
                }
            }
            is NoraImportPayload.Subscription -> {
                if (viewModel.uiState.value.isRunning && selectedGroup.url != input.url) {
                    message("برای تغییر لینک گروه، ابتدا اتصال VPN را قطع کنید")
                    return
                }
                activity.lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        NoraImportRouter.attachSubscription(selectedId, input.url)
                    }
                    result.fold(
                        onSuccess = {
                            viewModel.onAction(MainAction.RefreshGroups)
                            // Refresh only the currently selected subscription, not every group.
                            viewModel.onAction(MainAction.UpdateSubscriptions)
                            message("لینک در گروه انتخاب‌شده ثبت شد؛ در حال دریافت سرورها")
                        },
                        onFailure = { error ->
                            message(error.message ?: "ثبت لینک اشتراک ناموفق بود")
                        }
                    )
                }
            }
        }
    }
}
