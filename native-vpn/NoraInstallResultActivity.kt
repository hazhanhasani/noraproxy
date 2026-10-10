package com.v2ray.ang.ui.main

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast

/**
 * Explicit, non-exported system installation status callback.
 *
 * With PendingIntent.getActivity the OEM returns to a foreground Activity;
 * a STATUS_PENDING_USER_ACTION intent can then launch Android's trusted
 * confirmation UI. We never install without user consent.
 */
class NoraInstallResultActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleStatus(intent)
        finish()
    }

    private fun handleStatus(result: Intent?) {
        if (result == null) return
        when (val status = result.getIntExtra(
            PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE
        )) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirmation = if (Build.VERSION.SDK_INT >= 33) {
                    result.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    result.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                if (confirmation == null) {
                    showError("تأیید نصب از طرف اندروید دریافت نشد.")
                    return
                }
                try {
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(confirmation)
                } catch (error: Exception) {
                    Log.e("NoraProxyInstall", "Unable to open Android installer", error)
                    showError("صفحه تأیید نصب باز نشد. از تنظیمات اجازه نصب بدهید.")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(this, "NoraProxy با موفقیت به‌روزرسانی شد.", Toast.LENGTH_LONG).show()
            }
            else -> {
                val reason = result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    .orEmpty().take(130)
                Log.e("NoraProxyInstall", "Android install failure status=$status: $reason")
                showError(
                    if (reason.isBlank()) "خطای نصب اندروید (کد $status)"
                    else "خطای نصب اندروید: $reason"
                )
            }
        }
    }

    private fun showError(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }
}
