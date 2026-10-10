package pw.rkd.launcher.ui.widgets

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import pw.rkd.launcher.ui.components.Label
import pw.rkd.launcher.ui.components.T
import pw.rkd.launcher.ui.theme.LocalFocusColors

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
}
