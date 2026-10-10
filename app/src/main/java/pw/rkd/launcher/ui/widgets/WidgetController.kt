package pw.rkd.launcher.ui.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class WidgetSlot(val id: Int, val heightDp: Int)

/** Widget IDs are Android bindings, so their layout stays on this device rather than in a JSON backup. */
class WidgetController(private val activity: Activity) {
    private val prefs = activity.getSharedPreferences("widget_layout", Context.MODE_PRIVATE)
    val host = AppWidgetHost(activity, 19427)
    val manager = AppWidgetManager.getInstance(activity)
    var slots by mutableStateOf(readSlots())
        private set
    var pickerOpen by mutableStateOf(false)
        private set

    val availableWidgets: List<AppWidgetProviderInfo>
        get() = manager.installedProviders.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) {
            it.loadLabel(activity.packageManager)
        })

    private var pendingId: Int
        get() = prefs.getInt("pending", -1)
        set(value) { prefs.edit().putInt("pending", value).apply() }
    val isPicking: Boolean get() = pickerOpen || pendingId > 0

    fun startListening() = host.startListening()
    fun stopListening() = host.stopListening()

    fun pickWidget() {
        if (availableWidgets.isEmpty()) {
            Toast.makeText(activity, "No widgets available", Toast.LENGTH_SHORT).show()
            return
        }
        pickerOpen = true
    }

    fun dismissPicker() { pickerOpen = false }

    @Suppress("DEPRECATION")
    fun addWidget(info: AppWidgetProviderInfo) {
        pickerOpen = false
        if (pendingId > 0) discard(pendingId)
        val id = host.allocateAppWidgetId()
        pendingId = id
        try {
            if (manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) {
                completeBinding(id)
            } else {
                activity.startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
                }, BIND_REQUEST)
            }
        } catch (_: Exception) {
            discard(id)
            Toast.makeText(activity, "Could not add widget", Toast.LENGTH_SHORT).show()
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != BIND_REQUEST && requestCode != PICK_REQUEST && requestCode != CONFIGURE_REQUEST) return false
        val original = pendingId
        if (original <= 0) return true
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, original) ?: original
        if (id != original && requestCode == PICK_REQUEST) host.deleteAppWidgetId(original)
        if (resultCode != Activity.RESULT_OK || id <= 0) {
            discard(original)
            return true
        }
        if (requestCode == BIND_REQUEST && manager.getAppWidgetInfo(original) == null) {
            discard(original)
            return true
        }
        if (requestCode != CONFIGURE_REQUEST) {
            pendingId = id
            completeBinding(id)
            return true
        }
        finishAdding(id)
        return true
    }

    private fun completeBinding(id: Int) {
        val info = manager.getAppWidgetInfo(id)
        if (info == null) {
            discard(id)
            return
        }
        if (info.configure != null) {
            try {
                host.startAppWidgetConfigureActivityForResult(activity, id, 0, CONFIGURE_REQUEST, null)
            } catch (_: Exception) {
                discard(id)
                Toast.makeText(activity, "Could not configure widget", Toast.LENGTH_SHORT).show()
            }
            return
        }
        finishAdding(id)
    }

    private fun finishAdding(id: Int) {
        val minHeightPx = manager.getAppWidgetInfo(id)?.minHeight ?: 0
        val minHeightDp = (minHeightPx / activity.resources.displayMetrics.density).toInt()
        slots = slots + WidgetSlot(id, minHeightDp.coerceIn(180, 720))
        pendingId = -1
        save()
    }

    fun resize(id: Int, change: Int) {
        slots = slots.map { if (it.id == id) it.copy(heightDp = (it.heightDp + change).coerceIn(120, 720)) else it }
        save()
    }

    fun move(id: Int, direction: Int) {
        val from = slots.indexOfFirst { it.id == id }
        val to = from + direction
        if (from !in slots.indices || to !in slots.indices) return
        slots = slots.toMutableList().apply { add(to, removeAt(from)) }
        save()
    }

    fun remove(id: Int) {
        slots = slots.filterNot { it.id == id }
        save()
        host.deleteAppWidgetId(id)
    }

    private fun discard(id: Int) {
        if (id > 0) host.deleteAppWidgetId(id)
        pendingId = -1
    }

    private fun readSlots(): List<WidgetSlot> = runCatching {
        val array = JSONArray(prefs.getString("slots", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val id = entry.optInt("id", -1)
            if (id <= 0) null else WidgetSlot(id, entry.optInt("height", 180).coerceIn(120, 720))
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    private fun save() {
        val array = JSONArray()
        slots.forEach { array.put(JSONObject().put("id", it.id).put("height", it.heightDp)) }
        prefs.edit().putString("slots", array.toString()).apply()
    }

    companion object {
        private const val PICK_REQUEST = 6101
        private const val CONFIGURE_REQUEST = 6102
        private const val BIND_REQUEST = 6103
    }
}
