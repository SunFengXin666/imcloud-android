package com.imcloud.app.util

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ApkInstaller {
    fun installApk(context: android.content.Context, apkPath: String) {
        try {
            val file = File(apkPath)
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
