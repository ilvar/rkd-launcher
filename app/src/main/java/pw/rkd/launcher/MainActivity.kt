package pw.rkd.launcher

import pw.rkd.launcher.data.Tip
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pw.rkd.launcher.data.AppEntry
import pw.rkd.launcher.data.Settings
import pw.rkd.launcher.service.WeeklyReview
import pw.rkd.launcher.ui.drawer.AppMenu
import pw.rkd.launcher.ui.drawer.DrawerScreen
import pw.rkd.launcher.ui.home.HomeScreen
import pw.rkd.launcher.ui.launchApp
import pw.rkd.launcher.ui.openWebSearch
import pw.rkd.launcher.ui.theme.BlackTheme
import pw.rkd.launcher.ui.theme.LocalFocusColors
import pw.rkd.launcher.ui.theme.FocusTheme
import pw.rkd.launcher.ui.theme.applyFocusWindow
import pw.rkd.launcher.ui.widgets.WidgetController
import pw.rkd.launcher.ui.widgets.WidgetPage
import pw.rkd.launcher.util.Perms
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/** Home (page 0), optional widgets (page 1), then apps. */
class MainActivity : ComponentActivity() {
    private val widgets by lazy { WidgetController(this) }
    private val homePresses = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)
    private val homePressState = HomePressState()
    private var pendingHomePress: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.settings.value.let { applyFocusWindow(it.dark || it.showWallpaper, it.hideStatusBar, it.showWallpaper) }
        // First start ever: the introduction, once. Marked as seen here already, so that pressing
        // Home in the middle of it never brings it back.
        if (savedInstanceState == null && !Graph.state.tutorialSeen) {
            Graph.state.tutorialSeen = true
            startActivity(SettingsActivity.intent(this, "welcome"))
        }
        setContent {
            val settings by Graph.settings.flow.collectAsStateWithLifecycle()
            LaunchedEffect(settings.dark, settings.hideStatusBar, settings.showWallpaper) {
                applyFocusWindow(settings.dark || settings.showWallpaper, settings.hideStatusBar, settings.showWallpaper)
            }
            FocusTheme(settings, transparentBackground = settings.showWallpaper) { Launcher(settings, homePresses, widgets) }
        }
    }

    override fun onResume() {
        super.onResume()
        homePressState.onResume()
        // Android delivers a new intent while the Activity is paused, then resumes it. Move the
        // pager only after resume so a lifecycle reset cannot undo the drawer transition.
        pendingHomePress?.let(homePresses::tryEmit)
        pendingHomePress = null
    }

    override fun onStop() {
        widgets.stopListening()
        homePressState.onStop()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        widgets.startListening()
    }

    @Deprecated("Android's AppWidgetHost configuration API uses activity request codes")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        widgets.onActivityResult(requestCode, resultCode, data)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A second Home press opens apps; coming back from another app starts on the home page.
        if (intent.action == Intent.ACTION_MAIN) {
            // The HOME transition can briefly take window focus, even on a repeat press. Only
            // an actual stop means we were returning from another app.
            pendingHomePress = homePressState.onHomeIntent()
        }
    }
}

private var lastBackfill = 0L

/** Repeated Home opens apps from home and returns to home from either secondary page. */
internal fun homeDestination(alreadyInLauncher: Boolean, currentPage: Int, appsPage: Int = 1): Int =
    if (alreadyInLauncher && currentPage == 0) appsPage else 0

@Composable
private fun Launcher(settings: Settings, homePresses: Flow<Boolean>, widgets: WidgetController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val apps by Graph.apps.apps.collectAsStateWithLifecycle()
    val loaded by Graph.apps.loaded.collectAsStateWithLifecycle()
    val today by Graph.usage.today.collectAsStateWithLifecycle()
    val pendingReview by Graph.state.pendingReview.collectAsStateWithLifecycle()
    val tip by Graph.state.tip.collectAsStateWithLifecycle()

    val appsPage = if (settings.showWidgetPage) 2 else 1
    val pager = rememberPagerState { appsPage + 1 }
    LaunchedEffect(settings.showWidgetPage) {
        if (!settings.showWidgetPage && pager.currentPage > 1) pager.scrollToPage(1)
    }
    // The drawer counts as open from the moment a swipe is let go towards it: not halfway through
    // the drag, where the finger can still turn back (the keyboard used to pop up and drop again),
    // and not only once the page has settled (the keyboard would come late). So the keyboard
    // rises while the page glides in, and falls while it glides out.
    val pagerDragged by pager.interactionSource.collectIsDraggedAsState()
    val drawerActive by remember(appsPage) { derivedStateOf { if (pagerDragged) pager.settledPage == appsPage else pager.targetPage == appsPage } }
    var query by remember { mutableStateOf("") }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var wantsSearchFocus by remember { mutableStateOf(false) }
    var resumeCount by remember { mutableIntStateOf(0) }
    var usageAccess by remember { mutableStateOf(Perms.hasUsageAccess()) }
    var setupIncomplete by remember { mutableStateOf(false) }

    // Every return to the launcher: re-read permissions, refresh today's numbers (then keep them
    // ticking once a minute), and see whether a weekly review has come due.
    LifecycleResumeEffect(settings.timersEnabled) {
        resumeCount++
        usageAccess = Perms.hasUsageAccess()
        setupIncomplete = !Perms.isDefaultLauncher(context) || !usageAccess ||
            (settings.timersEnabled && !Perms.isTimerServiceEnabled(context))
        val job = scope.launch {
            WeeklyReview.checkDue()
            val now = System.currentTimeMillis()
            if (usageAccess && now - lastBackfill > 6 * 3_600_000L) {
                lastBackfill = now
                launch { Graph.usage.backfill() }
            }
            while (true) {
                Graph.usage.refreshToday()
                delay(60_000)
            }
        }
        onPauseOrDispose { job.cancel() }
    }

    // Leaving the launcher (an app was opened, the screen went off) always resets it to page 0.
    LifecycleStartEffect(Unit) {
        onStopOrDispose {
            if (!widgets.isPicking) {
                query = ""
                menuApp = null
                wantsSearchFocus = false
                scope.launch { pager.scrollToPage(0) }
            }
        }
    }

    // Arrived in the app list, by whichever way: that tip is learnt.
    LaunchedEffect(pager.settledPage, appsPage) { if (pager.settledPage == appsPage) Graph.state.did(Tip.SWIPE_LEFT) }

    LaunchedEffect(homePresses, appsPage) {
        homePresses.collect { openApps ->
            val destination = homeDestination(openApps, pager.currentPage, appsPage)
            menuApp = null
            query = ""
            wantsSearchFocus = destination == appsPage
            pager.animateScrollToPage(destination)
        }
    }

    // A launcher is never "backed out of": back only returns from the drawer to home.
    BackHandler {
        if (pager.currentPage != 0) scope.launch { pager.animateScrollToPage(0) }
    }

    val launch: (AppEntry) -> Unit = { entry -> launchApp(context, scope, entry) }
    // The widgets swipe remains available when the apps swipe is off. Consume only the
    // forward drag from widgets to apps; swiping back to home still works.
    val blockAppsSwipe = remember(settings.swipeLeftApps, settings.showWidgetPage, pager) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (settings.showWidgetPage && !settings.swipeLeftApps &&
                    pager.settledPage == 1 && available.x < 0f
                ) Offset(available.x, 0f) else Offset.Zero
        }
    }

    HorizontalPager(
        state = pager,
        userScrollEnabled = settings.swipeLeftApps || settings.showWidgetPage || pager.settledPage != 0,
        // There is no page to the left of home, so the pager ignores that swipe. Watch it on the
        // way down (Initial pass, nothing consumed) and open the web search instead, the way the
        // page left of a stock home screen does.
        modifier = Modifier.fillMaxSize().nestedScroll(blockAppsSwipe).pointerInput(settings.swipeRightSearch) {
            if (!settings.swipeRightSearch) return@pointerInput
            val threshold = 72.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (pager.currentPage != 0 || pager.isScrollInProgress) return@awaitEachGesture
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val moved = change.position - down.position
                    if (moved.x > threshold && moved.x > abs(moved.y) * 2) {
                        openWebSearch(context, settings.searchEngine)
                        Graph.state.did(Tip.SWIPE_RIGHT)
                        break
                    }
                }
            }
        },
        beyondViewportPageCount = 1,
        key = { it },
    ) { page ->
        // The page being left fades and sinks back a touch while the other one arrives. Done in
        // graphicsLayer, which runs in the draw phase: the swipe never triggers a recomposition.
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val distance = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                // Text on a flat background never overlaps itself, so each draw call can simply be
                // made more transparent. The default would render the whole page into a full-screen
                // off-screen buffer on every frame of the swipe: more memory and GPU for no gain.
                compositingStrategy = CompositingStrategy.ModulateAlpha
                alpha = (1f - distance * 1.2f).coerceIn(0f, 1f)
                val scale = 1f - 0.05f * distance
                scaleX = scale
                scaleY = scale
            },
        ) {
            if (page == 0) {
                CompositionLocalProvider(LocalFocusColors provides if (settings.showWallpaper) BlackTheme else LocalFocusColors.current) {
                HomeScreen(
                    settings = settings,
                    apps = apps,
                    today = today,
                    usageAccess = usageAccess,
                    setupIncomplete = setupIncomplete,
                    pendingReview = pendingReview,
                    resumeCount = resumeCount,
                    onLaunch = launch,
                    onAppMenu = { Graph.state.did(Tip.APP_MENU); menuApp = it },
                    onOpenDrawer = { focusSearch ->
                        wantsSearchFocus = focusSearch
                        scope.launch { pager.animateScrollToPage(appsPage) }
                    },
                    onOpenSettings = { route -> context.startActivity(SettingsActivity.intent(context, route)) },
                    onOpenReview = { week -> context.startActivity(ReviewActivity.intent(context, week)) },
                )
                }
            } else if (page == appsPage) {
                Box(Modifier.fillMaxSize().background(LocalFocusColors.current.bg)) {
                DrawerScreen(
                    settings = settings,
                    apps = apps,
                    loaded = loaded,
                    today = today,
                    query = query,
                    onQueryChange = { query = it },
                    isActive = drawerActive,
                    wantsSearchFocus = wantsSearchFocus,
                    onSearchFocusHandled = { wantsSearchFocus = false },
                    onLaunch = launch,
                    onAppMenu = { Graph.state.did(Tip.APP_MENU); menuApp = it },
                    hint = tip?.takeIf { it == Tip.APP_MENU }?.let { it.gesture + "  →  " + it.result },
                )
                }
            } else {
                WidgetPage(widgets)
            }
        }
    }

    menuApp?.let { app ->
        AppMenu(
            app = app,
            settings = settings,
            today = today,
            onDismiss = { menuApp = null },
            onOpenSetup = { context.startActivity(SettingsActivity.intent(context, "setup")) },
        )
    }
}
