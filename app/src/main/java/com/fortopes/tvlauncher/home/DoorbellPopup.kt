package com.fortopes.tvlauncher.home

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.fortopes.tvlauncher.data.HaClient
import com.fortopes.tvlauncher.data.HaEntity
import com.fortopes.tvlauncher.data.HaLive
import com.fortopes.tvlauncher.data.HaStore
import com.fortopes.tvlauncher.data.HttpException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The doorbell pop-up. The Home button service is always running, so it keeps a small live connection to
 * Home Assistant that follows just the chosen doorbell (or person) sensor. When it rings, the camera shows in
 * the top right corner over whatever is playing, for 30 seconds. Back closes it, OK opens the camera full
 * screen in Fortopes Home. An accessibility service may draw over other apps, so no other permission is needed.
 */
class DoorbellPopup(private val context: Context, private val onError: (String, Throwable) -> Unit) {
    private val store = HaStore(context)
    private val client = HaClient(store)
    private val main = Handler(Looper.getMainLooper())
    private val windows = context.getSystemService(WindowManager::class.java)
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watching: Job? = null
    private var showing: Job? = null
    private var view: View? = null
    private var image: ImageView? = null
    private var lastShownAt = 0L

    /** The camera on screen right now, or null when the pop-up isn't showing. */
    @Volatile var cameraShowing: String? = null
        private set

    /** Starts (or restarts, after a settings change) following the doorbell. */
    fun start() {
        watching?.cancel()
        watching = scope.launch {
            var wait = 15_000L
            while (isActive) {
                val sensor = store.doorbellSensor
                if (!store.isSignedIn || sensor == null) return@launch
                val error = runCatching {
                    var last: String? = null
                    HaLive(store) { it == sensor }.updates().collect { states ->
                        wait = 15_000L // connected, so start the back-off again
                        val now = states.firstOrNull { it.id == sensor } ?: return@collect
                        val before = last
                        last = now.state
                        if (before != null && rang(now, before)) show()
                    }
                }.exceptionOrNull()
                if (error is HttpException && error.code == 401) return@launch // token's gone; settings says so
                // Home Assistant restarting, Wi-Fi blip: try again, less often the longer it's away.
                delay(wait)
                wait = (wait * 2).coerceAtMost(5 * 60_000L)
            }
        }
    }

    fun stop() {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        main.post { hide() }
    }

    /** Shows the pop-up now; also the settings' test button. */
    fun show() {
        if (System.currentTimeMillis() - lastShownAt < 20_000L && cameraShowing != null) return // still up
        lastShownAt = System.currentTimeMillis()
        showing?.cancel()
        showing = scope.launch {
            val camera = runCatching { pickCamera() }.getOrNull()
            if (camera == null) {
                onError("Doorbell pop-up", IllegalStateException("No camera to show"))
                return@launch
            }
            withContext(Dispatchers.Main) { addView(camera.second) }
            cameraShowing = camera.first
            try {
                // A fresh picture every 2 seconds for 30 seconds.
                repeat(15) {
                    val bitmap = runCatching {
                        val bytes = client.cameraImage(camera.first, width = 640)
                        val opts = BitmapFactory.Options().apply { inSampleSize = sampleFor(bytes, 640) }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    }.getOrNull()
                    if (bitmap != null) withContext(Dispatchers.Main) { image?.setImageBitmap(bitmap) }
                    delay(2_000)
                }
            } finally {
                cameraShowing = null
                main.post { hide() }
            }
        }
    }

    /** Closes the pop-up (Back). */
    fun dismiss() {
        showing?.cancel()
        cameraShowing = null
        main.post { hide() }
    }

    private fun rang(now: HaEntity, before: String): Boolean = when (now.domain) {
        // An event entity's state is the time of the last press.
        "event" -> now.state != before && !now.unavailable
        else -> now.state == "on" && before != "on"
    }

    /** The chosen camera, or the one whose name starts like the sensor's ("Front Door Person" → "Front Door ..."). */
    private fun pickCamera(): Pair<String, String>? {
        val states = client.states()
        val cameras = states.filter { it.domain == "camera" && !it.unavailable }
        store.doorbellCamera?.let { id -> cameras.firstOrNull { it.id == id }?.let { return it.id to it.name } }
        val sensor = store.doorbellSensor ?: return cameras.firstOrNull()?.let { it.id to it.name }
        val sensorName = states.firstOrNull { it.id == sensor }?.name ?: sensor.substringAfter('.').replace('_', ' ')
        return matchCamera(sensorName, cameras)?.let { it.id to it.name }
    }

    private fun addView(name: String) {
        hide()
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()
        val picture = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(0xFF15161D.toInt()) }
        }
        val title = TextView(context).apply {
            text = "$name · someone's at the door"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            setPadding(0, dp(8), 0, 0)
        }
        val hint = TextView(context).apply {
            text = "OK to watch · Back to close"
            setTextColor(0xB3FFFFFF.toInt())
            textSize = 12f
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(0xEE1A1B26.toInt())
                setStroke(dp(2), 0xFF8B5CF6.toInt())
            }
            addView(picture, LinearLayout.LayoutParams(dp(400), dp(225)))
            addView(title)
            addView(hint)
        }
        val root = FrameLayout(context).apply { addView(card) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Never takes the remote: the app underneath keeps playing and the service handles OK and Back.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(32)
            y = dp(32)
        }
        runCatching { windows.addView(root, params) }
            .onSuccess {
                view = root
                image = picture
            }
            .onFailure { onError("Doorbell pop-up", it) }
    }

    private fun hide() {
        view?.let { runCatching { windows.removeView(it) } }
        view = null
        image = null
    }

    companion object {
        /** The camera that goes with a sensor, by the words their names start with; else the front door one. */
        fun matchCamera(sensorName: String, cameras: List<HaEntity>): HaEntity? {
            val words = sensorName.lowercase().split(' ')
            fun shared(c: HaEntity) = c.name.lowercase().split(' ').zip(words).takeWhile { (a, b) -> a == b }.size
            return cameras.maxByOrNull { shared(it) }?.takeIf { shared(it) > 0 }
                ?: cameras.firstOrNull { c -> listOf("doorbell", "front door", "front").any { c.name.contains(it, true) } }
                ?: cameras.firstOrNull()
        }

        /** Sensors that make sense as a doorbell: doorbell buttons first, then person detection. */
        fun candidates(states: List<HaEntity>): List<HaEntity> {
            val bell = Regex("doorbell|visitor|ding|ring|button|press", RegexOption.IGNORE_CASE)
            fun rank(e: HaEntity): Int = when {
                e.domain == "event" && e.attributes.optString("device_class") == "doorbell" -> 0
                bell.containsMatchIn(e.id) || bell.containsMatchIn(e.name) -> 0
                e.domain == "binary_sensor" && (e.id.contains("person") || e.name.contains("person", true)) -> 1
                else -> 9
            }
            return states.filter { (it.domain == "binary_sensor" || it.domain == "event") && rank(it) < 9 }
                .sortedWith(compareBy<HaEntity> { rank(it) }.thenBy { it.name.lowercase() })
        }

        private fun sampleFor(bytes: ByteArray, width: Int): Int {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= width) sample *= 2
            return sample
        }

        /** The running pop-up, so settings can test it and restart it after a change. Null when the service is off. */
        @Volatile var current: DoorbellPopup? = null
    }
}
