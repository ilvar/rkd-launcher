package pw.rkd.launcher.ui.widgets

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import pw.rkd.launcher.ui.components.Label
import pw.rkd.launcher.ui.components.FocusDialog
import pw.rkd.launcher.ui.components.T
import pw.rkd.launcher.ui.components.UnderlinedField
import pw.rkd.launcher.ui.theme.LocalFocusColors
import kotlin.math.roundToInt

private data class WidgetChoice(val info: AppWidgetProviderInfo, val app: String, val widget: String, val size: String)

@Composable
fun WidgetPage(controller: WidgetController) {
    val colors = LocalFocusColors.current
    val context = LocalContext.current
    val slots = controller.slots
    LazyColumn(
        Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label("Widgets", Modifier.weight(1f))
                T("+ Add widget", Modifier.clickable { controller.pickWidget() }.padding(12.dp), size = 15.sp)
            }
        }
        if (slots.isEmpty()) {
            item { T("Add a widget to fill this page. Each widget uses the full width.", size = 15.sp, color = colors.dim) }
        }
        itemsIndexed(slots, key = { _, slot -> slot.id }) { index, slot ->
            val info = controller.manager.getAppWidgetInfo(slot.id)
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T(info?.loadLabel(context.packageManager) ?: "Widget unavailable", Modifier.weight(1f), size = 14.sp, color = colors.dim, maxLines = 1)
                    if (index > 0) T("↑", Modifier.clickable { controller.move(slot.id, -1) }.padding(9.dp), size = 18.sp)
                    if (index < slots.lastIndex) T("↓", Modifier.clickable { controller.move(slot.id, 1) }.padding(9.dp), size = 18.sp)
                    T("−", Modifier.clickable { controller.resize(slot.id, -80) }.padding(9.dp), size = 18.sp)
                    T("+", Modifier.clickable { controller.resize(slot.id, 80) }.padding(9.dp), size = 18.sp)
                    T("Remove", Modifier.clickable { controller.remove(slot.id) }.padding(9.dp), size = 13.sp, color = colors.dim)
                }
                if (info != null) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val widthDp = maxWidth.value.toInt()
                        AndroidView(
                            factory = { controller.host.createView(it, slot.id, info) },
                            modifier = Modifier.fillMaxWidth().height(slot.heightDp.dp),
                            update = { view ->
                                view.updateAppWidgetSize(null, widthDp, slot.heightDp, widthDp, slot.heightDp)
                            },
                        )
                    }
                }
            }
        }
    }
    if (controller.pickerOpen) {
        val widgets = remember(controller.pickerOpen) {
            val pm = context.packageManager
            val density = context.resources.displayMetrics.density
            controller.availableWidgets.map { info ->
                val packageName = info.provider.packageName
                val appName = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                }.getOrDefault(packageName)
                val width = (info.minWidth / density).roundToInt().coerceAtLeast(0)
                val height = (info.minHeight / density).roundToInt().coerceAtLeast(0)
                WidgetChoice(info, appName, info.loadLabel(pm), "${width} × ${height} dp")
            }.sortedWith(compareBy<WidgetChoice> { it.app.lowercase() }
                .thenBy { it.widget.lowercase() })
        }
        var query by remember(controller.pickerOpen) { mutableStateOf(TextFieldValue("")) }
        val shown = remember(widgets, query.text) {
            val term = query.text.trim()
            if (term.isEmpty()) widgets else widgets.filter {
                it.app.contains(term, ignoreCase = true) || it.widget.contains(term, ignoreCase = true)
            }
        }
        FocusDialog(controller::dismissPicker, title = "Add widget", tall = true) {
            UnderlinedField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search apps and widgets",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                imeAction = ImeAction.Search,
            )
            LazyColumn(Modifier.weight(1f)) {
                if (shown.isEmpty()) item { T("No matching widgets", Modifier.padding(24.dp), color = colors.dim) }
                items(shown, key = { "${it.info.provider.flattenToString()}:${it.info.profile.hashCode()}" }) { choice ->
                    Column(Modifier.fillMaxWidth().clickable { controller.addWidget(choice.info) }
                        .padding(horizontal = 24.dp, vertical = 12.dp)) {
                        T(choice.app, size = 17.sp, maxLines = 1)
                        T("${choice.widget} · min ${choice.size}", size = 14.sp, color = colors.dim, maxLines = 2)
                    }
                }
            }
        }
    }
}
