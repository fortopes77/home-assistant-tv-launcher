package com.fortopes.tvlauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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

/**
 * Lists the apps that show on the Google TV home (the leanback launcher ones) and opens them. Banners are
 * also kept on disk, so a fresh start shows the row straight away instead of asking every app for its
 * banner again, which is slow on a slow TV.
 */
class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager
    private val prefs = appContext.getSharedPreferences("apps", Context.MODE_PRIVATE)
    private val dir = File(appContext.cacheDir, "banners").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")

    // Banners don't change unless the app updates, so keep them between reloads.
    private val cache = HashMap<String, Pair<Long, TvApp>>()

    /** The apps from the last visit, read from disk without asking any app. Empty the first time. */
    fun loadCached(): List<TvApp> = runCatching {
        val arr = JSONArray(indexFile.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val component = ComponentName.unflattenFromString(o.getString("key")) ?: return@mapNotNull null
            val bitmap = File(dir, fileName(o.getString("key"))).takeIf { it.exists() }
                ?.let { BitmapFactory.decodeFile(it.path) }?.asImageBitmap()
            val isBanner = o.optBoolean("banner")
            val app = TvApp(component, o.getString("label"), if (isBanner) bitmap else null, if (isBanner) null else bitmap, Color(o.getInt("color")))
            cache[app.key] = o.getLong("updated") to app
            app
        }
    }.getOrDefault(emptyList())

    private var packagesSeen = 0

    /**
     * Whether any app was installed, updated or removed since the last [loadApps]. Android keeps count, so
     * this is one quick question instead of asking about every app each time Home is pressed.
     */
    fun appsChanged(): Boolean = runCatching { pm.getChangedPackages(packagesSeen) != null }.getOrDefault(true)

    fun loadApps(): List<TvApp> {
        val seenNow = runCatching { pm.getChangedPackages(packagesSeen)?.sequenceNumber }.getOrNull()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        var changed = false
        val apps = pm.queryIntentActivities(intent, 0)
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
                    (bannerBitmap ?: iconBitmap)?.let { saveBitmap(key, it) }
                    changed = true
                    app
                }
            }
            .sortedBy { it.label.lowercase() }
            .toList()
        val known = apps.map { it.key }.toSet()
        if (cache.keys.retainAll(known)) changed = true // uninstalled apps
        if (changed || !indexFile.exists()) saveIndex(apps)
        if (seenNow != null) packagesSeen = seenNow
        return apps
    }

    private fun fileName(key: String) = key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".png"

    private fun saveBitmap(key: String, bitmap: Bitmap) {
        runCatching { File(dir, fileName(key)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    private fun saveIndex(apps: List<TvApp>) = runCatching {
        val arr = JSONArray()
        apps.forEach { app ->
            arr.put(
                JSONObject()
                    .put("key", app.key)
                    .put("label", app.label)
                    .put("banner", app.banner != null)
                    .put("color", app.color.toArgb())
                    .put("updated", cache[app.key]?.first ?: 0L),
            )
        }
        val tmp = File(dir, "index.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(indexFile)
        // Tidy banners of apps that are gone.
        val keep = apps.map { fileName(it.key) }.toSet() + setOf("index.json")
        dir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
    }

    // ---------- your order and hidden apps ----------

    /** Apps you've moved, in your order. Apps not in it follow, A to Z. */
    private var order: List<String>
        get() = prefs.getString("order", "").orEmpty().split('\n').filter { it.isNotBlank() }
        set(value) = prefs.edit().putString("order", value.joinToString("\n")).apply()

    var hidden: Set<String>
        get() = prefs.getStringSet("hidden", emptySet()).orEmpty().toSet()
        private set(value) = prefs.edit().putStringSet("hidden", value).apply()

    /** The apps as the row shows them: your order first, hidden ones left out. */
    fun arrange(all: List<TvApp>): List<TvApp> {
        val rank = order.withIndex().associate { (i, k) -> k to i }
        val hide = hidden
        return all.filter { it.key !in hide }
            .sortedWith(compareBy<TvApp> { rank[it.key] ?: Int.MAX_VALUE }.thenBy { it.label.lowercase() })
    }

    /** Saves the row's order after moving an app. */
    fun saveOrder(shown: List<TvApp>) {
        order = shown.map { it.key }
    }

    /** Your favourite apps, in the order you added them. They show in their own row, even if hidden from Apps. */
    var favourites: List<String>
        get() = prefs.getString("favourites", "").orEmpty().split('\n').filter { it.isNotBlank() }
        private set(value) = prefs.edit().putString("favourites", value.joinToString("\n")).apply()

    fun toggleFavourite(app: TvApp) {
        favourites = if (app.key in favourites) favourites - app.key else favourites + app.key
    }

    /** Moves a favourite one place left (-1) or right (+1). */
    fun moveFavourite(app: TvApp, by: Int) {
        val list = favourites.toMutableList()
        val i = list.indexOf(app.key)
        val j = i + by
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        favourites = list
    }

    fun hide(app: TvApp) {
        hidden = hidden + app.key
    }

    fun unhide(key: String) {
        hidden = hidden - key
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
