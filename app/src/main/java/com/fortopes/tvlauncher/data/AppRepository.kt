package com.fortopes.tvlauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette

/** One app from the TV's app list. [banner] is the wide 16:9 TV banner; apps without one get [icon]. */
data class TvApp(
    val component: ComponentName,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    val color: Color,
) {
    val key: String get() = component.flattenToShortString()
}

/** Lists the apps that show on the Google TV home (the leanback launcher ones) and opens them. */
class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager

    // Banners don't change unless the app updates, so keep them between reloads.
    private val cache = HashMap<String, Pair<Long, TvApp>>()

    fun loadApps(): List<TvApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != appContext.packageName }
            .distinctBy { it.packageName }
            .mapNotNull { info ->
                val component = ComponentName(info.packageName, info.name)
                val key = component.flattenToShortString()
                val updated = try {
                    pm.getPackageInfo(info.packageName, 0).lastUpdateTime
                } catch (e: PackageManager.NameNotFoundException) {
                    return@mapNotNull null
                }
                cache[key]?.takeIf { it.first == updated }?.second ?: run {
                    val banner = info.loadBanner(pm) ?: info.applicationInfo.loadBanner(pm)
                    val bannerBitmap = banner?.toSizedBitmap(maxWidth = 640)
                    val iconBitmap = if (bannerBitmap == null) info.loadIcon(pm).toSizedBitmap(maxWidth = 192) else null
                    val app = TvApp(
                        component = component,
                        label = info.loadLabel(pm).toString(),
                        banner = bannerBitmap?.asImageBitmap(),
                        icon = iconBitmap?.asImageBitmap(),
                        color = (bannerBitmap ?: iconBitmap)?.mainColor() ?: Color(0xFF3B82F6),
                    )
                    cache[key] = updated to app
                    app
                }
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun launch(app: TvApp): Boolean = try {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        appContext.startActivity(intent)
        true
    } catch (e: Exception) {
        // Some apps only accept their own leanback launch intent.
        pm.getLeanbackLaunchIntentForPackage(app.component.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let { runCatching { appContext.startActivity(it) }.isSuccess } ?: false
    }
}

private fun Drawable.toSizedBitmap(maxWidth: Int): Bitmap {
    val w = intrinsicWidth.takeIf { it > 0 } ?: maxWidth
    val h = intrinsicHeight.takeIf { it > 0 } ?: maxWidth
    val scale = if (w > maxWidth) maxWidth.toFloat() / w else 1f
    return toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
}

/** The app's main colour, for tinting the background behind the focused card. */
private fun Bitmap.mainColor(): Color? {
    val palette = Palette.from(this).maximumColorCount(12).generate()
    val swatch = palette.vibrantSwatch ?: palette.dominantSwatch ?: return null
    return Color(swatch.rgb)
}
