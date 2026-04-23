package com.divyanshgolyan.claune.android.scripting

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Build

interface InstalledAppRegistry {
    fun listLaunchableApps(): List<InstalledAppPayload>

    fun launchPackage(packageName: String): HostCallOutcome
}

object EmptyInstalledAppRegistry : InstalledAppRegistry {
    override fun listLaunchableApps(): List<InstalledAppPayload> = emptyList()

    override fun launchPackage(packageName: String): HostCallOutcome = HostCallOutcome(
        ok = false,
        message = "Installed app registry is unavailable in this runtime.",
    )
}

class AndroidInstalledAppRegistry(context: Context) : InstalledAppRegistry {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    override fun listLaunchableApps(): List<InstalledAppPayload> {
        val launcherIntent =
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
        return packageManager
            .queryIntentActivitiesCompat(launcherIntent)
            .mapNotNull { resolveInfo -> resolveInfo.toInstalledAppPayload() }
            .distinctBy { "${it.packageName}/${it.activityName.orEmpty()}" }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, InstalledAppPayload::label).thenBy { it.packageName })
    }

    override fun launchPackage(packageName: String): HostCallOutcome {
        val trimmedPackage = packageName.trim()
        if (trimmedPackage.isBlank()) {
            return HostCallOutcome(ok = false, message = "Package name is required.")
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(trimmedPackage)
            ?: return HostCallOutcome(ok = false, message = "No launchable app found for package '$trimmedPackage'.")

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            appContext.startActivity(launchIntent)
            HostCallOutcome(ok = true, message = "Launched package '$trimmedPackage'.")
        }.getOrElse { throwable ->
            HostCallOutcome(
                ok = false,
                message = "Could not launch package '$trimmedPackage': ${throwable.message ?: throwable::class.simpleName}.",
            )
        }
    }

    private fun ResolveInfo.toInstalledAppPayload(): InstalledAppPayload? {
        val activityInfo = activityInfo ?: return null
        val packageName = activityInfo.packageName ?: return null
        val label = loadLabel(packageManager).toString().trim().ifBlank { packageName }
        return InstalledAppPayload(
            label = label,
            packageName = packageName,
            activityName = activityInfo.name,
        )
    }

    @Suppress("DEPRECATION")
    private fun android.content.pm.PackageManager.queryIntentActivitiesCompat(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            queryIntentActivities(intent, android.content.pm.PackageManager.ResolveInfoFlags.of(0))
        } else {
            queryIntentActivities(intent, 0)
        }
}
