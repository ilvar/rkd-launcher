package pw.rkd.launcher.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import pw.rkd.launcher.data.Tip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pw.rkd.launcher.ui.components.FocusDialog
import pw.rkd.launcher.ui.components.MenuRow
import pw.rkd.launcher.ui.components.Hairline
import pw.rkd.launcher.ui.components.TextInputDialog
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import pw.rkd.launcher.Graph
import pw.rkd.launcher.data.AppEntry
import pw.rkd.launcher.data.CalEvent
import pw.rkd.launcher.data.CalendarInfo
import pw.rkd.launcher.data.CalendarRepository
import pw.rkd.launcher.data.ClockStyle
import pw.rkd.launcher.data.DayUsage
import pw.rkd.launcher.data.FontChoice
import pw.rkd.launcher.data.SHORTCUT_CAMERA
import pw.rkd.launcher.data.SHORTCUT_PHONE
import pw.rkd.launcher.data.Settings
import pw.rkd.launcher.data.TodoItem
import pw.rkd.launcher.data.SplitSide
import pw.rkd.launcher.data.TAP_ALARMS
import pw.rkd.launcher.data.TAP_BATTERY
import pw.rkd.launcher.data.TAP_CALENDAR
import pw.rkd.launcher.data.TAP_NOTHING
import pw.rkd.launcher.data.TAP_SCREEN_TIME
import pw.rkd.launcher.data.TimeFormat
import pw.rkd.launcher.data.WeatherNow
import pw.rkd.launcher.data.WeatherRepository
import pw.rkd.launcher.service.FocusAccessibilityService
import pw.rkd.launcher.ui.components.AppPickerDialog
import pw.rkd.launcher.ui.components.ChoiceDialog
import pw.rkd.launcher.ui.components.T
import pw.rkd.launcher.ui.components.VSpace
import pw.rkd.launcher.ui.components.WorkBadge
import pw.rkd.launcher.ui.components.hasColourGlyphs
import pw.rkd.launcher.ui.components.monochrome
import pw.rkd.launcher.ui.components.press
import pw.rkd.launcher.ui.launchOptions
import pw.rkd.launcher.ui.theme.LocalFocusColors
import pw.rkd.launcher.util.Perms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Page one of the launcher. Top to bottom: the clock, today's screen time in plain words under it,
 * the optional calendar section, up to five fast apps, and the two corner shortcuts. Text only.
 *
 * Gestures on empty space: long-press opens settings, swipe down pulls the notification shade,
 * swipe up jumps to search, double-tap locks the phone. Swiping left (handled by the pager that
 * hosts this page) opens the app drawer.
 */
@Composable
fun HomeScreen(
    settings: Settings,
    apps: List<AppEntry>,
    today: DayUsage?,
    usageAccess: Boolean,
    setupIncomplete: Boolean,
    pendingReview: LocalDate?,
    resumeCount: Int,
    onLaunch: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    onOpenDrawer: (focusSearch: Boolean) -> Unit,
    onOpenSettings: (route: String?) -> Unit,
    onOpenReview: (week: LocalDate?) -> Unit,
) {
    val c = LocalFocusColors.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var wallpaperImage by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(settings.showWallpaper, settings.wallpaperUri) {
        wallpaperImage = if (settings.showWallpaper && settings.wallpaperUri.isNotBlank()) withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(settings.wallpaperUri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                        val width = info.size.width
                        val height = info.size.height
                        val scale = (maxOf(width, height).toFloat() / 2048f).coerceAtLeast(1f)
                        decoder.setTargetSize((width / scale).toInt().coerceAtLeast(1), (height / scale).toInt().coerceAtLeast(1))
                    }
                } else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    val sample = generateSequence(1) { it * 2 }.first { maxOf(bounds.outWidth, bounds.outHeight) / it <= 2048 }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                }
            }.getOrNull()
        } else null
    }
    val now by rememberNow()
    // The gesture detectors below outlive recompositions, so they must call the latest callbacks.
    val openDrawer by rememberUpdatedState(onOpenDrawer)
    val openSettings by rememberUpdatedState(onOpenSettings)

    // Calendar section data
    var calendarAccess by remember { mutableStateOf(CalendarRepository.hasAccess(context)) }
    var events by remember { mutableStateOf(emptyList<CalEvent>()) }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { calendarAccess = it }
    var weatherAccess by remember { mutableStateOf(WeatherRepository.hasAccess(context)) }
    var weather by remember { mutableStateOf<WeatherNow?>(null) }
    var weatherRetry by remember { mutableStateOf(0) }
    val askWeather = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        weatherAccess = it
        weatherRetry++
    }
    LaunchedEffect(settings.showWeather, resumeCount, weatherRetry) {
        if (settings.showWeather) {
            weatherAccess = WeatherRepository.hasAccess(context)
            weather = if (weatherAccess) WeatherRepository.current(context) else null
        }
    }
    var shownCalendar by remember { mutableStateOf<CalendarInfo?>(null) }
    LaunchedEffect(settings.showCalendar, settings.calendarKey, settings.calendarKeys, settings.compactCalendar, resumeCount, calendarAccess) {
        if (settings.showCalendar) {
            calendarAccess = CalendarRepository.hasAccess(context)
            val agenda = CalendarRepository.agenda(context, settings.calendarKey, settings.calendarKeys, if (settings.compactCalendar) 8 else 3)
            shownCalendar = agenda.calendar
            events = agenda.events
        }
    }
    // The agenda is cached; the calendar provider tells us when that cache is no longer true.
    // The observer does no work itself, so a sync in the background costs nothing here.
    DisposableEffect(settings.showCalendar, calendarAccess) {
        if (!settings.showCalendar || !calendarAccess) return@DisposableEffect onDispose { }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = CalendarRepository.invalidate()
        }
        val registered = try {
            context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            true
        } catch (_: Exception) {
            false
        }
        onDispose { if (registered) context.contentResolver.unregisterContentObserver(observer) }
    }

    // The music section is there while there is music (and for a minute after it stopped), unless
    // it was asked to stay. Its line and its share of the height come and go with it.
    val music = rememberMusicState(enabled = settings.showMusic, resumeCount = resumeCount)
    val musicVisible = settings.showMusic && (!settings.musicAutoHide || music.playing || music.lingering)

    var editingShortcut by remember { mutableStateOf<Boolean?>(null) } // true = left, false = right
    var choosingClockTap by remember { mutableStateOf(false) }
    val tip by Graph.state.tip.collectAsStateWithLifecycle()
    // A tip about something that is not on the screen teaches nothing: pass over it.
    LaunchedEffect(tip, settings.showMusic, settings.showNote, settings.showShortcuts) {
        val absent = (tip == Tip.SECTION_APPS && !settings.showMusic && !settings.showNote) || (tip == Tip.CORNERS && !settings.showShortcuts)
        if (absent) Graph.state.nextTip()
    }
    var editingNote by remember { mutableStateOf(false) }
    var addingTodo by remember { mutableStateOf(false) }
    var showingTodos by remember { mutableStateOf(false) }
    LaunchedEffect(resumeCount) { Graph.settings.update { it } }
    var choosingMusicApp by remember { mutableStateOf(false) }
    var choosingWeatherApp by remember { mutableStateOf(false) }
    var choosingNoteApp by remember { mutableStateOf(false) }
    var noteMenu by remember { mutableStateOf(false) }
    var editingNoteLink by remember { mutableStateOf(false) }
    val noteApp = remember(settings.noteApp, apps) { apps.firstOrNull { it.key == settings.noteApp } }
    var choosingSplitSide by remember { mutableStateOf(false) }

    // Split clock: the section in its right half is not shown a second time further down. The
    // calendar can only sit there while the calendar section is on; otherwise it is screen time.
    val split = settings.clockStyle == ClockStyle.SPLIT
    val sideCalendar = split && settings.splitSide == SplitSide.CALENDAR && settings.showCalendar
    val sideScreenTime = split && !sideCalendar

    val favorites = remember(settings.favorites, settings.homeAppsCount, apps) { settings.favorites.take(settings.homeAppsCount).mapNotNull { key -> apps.firstOrNull { it.key == key } } }
    // Fast apps whose allowance for today is gone are shown dimmed.
    val spent = remember(favorites, today, settings.appLimits, settings.timersEnabled, settings.socialDefaultMin, settings.gameDefaultMin) {
        favorites.filter { app ->
            val limit = Graph.limits.limitFor(app.packageName, settings) ?: return@filter false
            (today?.perApp?.get(app.packageName) ?: 0L) >= limit.millis && !Graph.limits.hasFreePass(app.packageName)
        }.mapTo(HashSet()) { it.key }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(settings.swipeDownNotifications, settings.swipeUpSearch) {
                val threshold = 64.dp.toPx()
                var dragged = 0f
                var fired = false
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f; fired = false },
                    onVerticalDrag = { _, dy ->
                        dragged += dy
                        if (!fired && dragged > threshold && settings.swipeDownNotifications) {
                            fired = true
                            expandNotifications(context)
                            Graph.state.did(Tip.SWIPE_DOWN)
                        } else if (!fired && dragged < -threshold && settings.swipeUpSearch) {
                            fired = true
                            openDrawer(true)
                            Graph.state.did(Tip.SWIPE_UP)
                        }
                    },
                )
            }
            .pointerInput(settings.doubleTapLock) {
                detectTapGestures(
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        Graph.state.did(Tip.SETTINGS)
                        openSettings(null)
                    },
                    onDoubleTap = if (settings.doubleTapLock) {
                        {
                            Graph.state.did(Tip.DOUBLE_TAP)
                            if (!FocusAccessibilityService.lockScreen()) {
                                Toast.makeText(context, "Turn on the Rkd Launcher timer service to lock with a double tap", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else null,
                )
            },
    ) {

        // A home screen must never scroll or push its corner shortcuts off the edge. Estimate the
        // height each arrangement needs (constants measured on a real screen) and take the
        // roomiest one that fits, whatever the phone, text size, fast apps and enabled sections.
        // Detail is given up before the clock is: first fewer events, and only then a smaller ring.
        val bars = WindowInsets.systemBars.asPaddingValues()
        val available = (maxHeight - bars.calculateTopPadding() - bars.calculateBottomPadding()).value
        val textScale = LocalDensity.current.fontScale * settings.textScale
        val notices = (if (setupIncomplete) 1 else 0) + (if (pendingReview != null) 1 else 0) + (if (tip != null) 3 else 0) // a framed tip is about three notice lines tall
        val rowCount = (if (settings.fastAppColumns == 2) (favorites.size + 1) / 2 else favorites.size).coerceAtLeast(2) // the empty-state hint is two lines tall
        val eventCount = events.size
        val preferredRing = (maxHeight * if (settings.showCalendar) 0.25f else 0.29f).coerceIn(140.dp, 236.dp)

        class Fit(val ring: Dp, val textSp: Float, val padDp: Float, val maxEvents: Int)

        // Split clock: the time is as large as its half allows. Digits are about 0.56 em wide in the
        // sans and serif faces and 0.6 em in the monospace one; "AM" / "PM" takes its share first.
        val use24h = when (settings.timeFormat) {
            TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
            TimeFormat.H24 -> true
            TimeFormat.H12 -> false
        }
        val halfWidth = (maxWidth.value - 36f) / 2f - 30f // column padding, then the half's own padding
        val amPmWidth = if (use24h) 0f else 26f * textScale
        val emsWide = if (settings.font == FontChoice.MONO) 3.1f else 2.75f
        val splitTimeSp = ((halfWidth - amPmWidth) / emsWide / textScale).coerceIn(30f, 56f)

        fun heightOf(fit: Fit): Float {
            val splitClock = (splitTimeSp * 1.2f + 42f) * textScale + 16f
            val splitSide = if (sideCalendar) eventCount.coerceIn(1, if (settings.compactCalendar) 3 else 2) * (if (settings.compactCalendar) 34f else 44f) * textScale + 16f else 86f * textScale + 16f
            val clock = when (settings.clockStyle) {
                ClockStyle.RING -> fit.ring.value
                ClockStyle.SPLIT -> maxOf(splitClock, splitSide)
                ClockStyle.PLAIN -> 100f * textScale
            }
            val noticeLines = if (notices > 0) 14f + notices * (19f * textScale + 12f) else 0f
            val shownEvents = fit.maxEvents.coerceAtMost(eventCount).coerceAtLeast(1)
            val strip = if (settings.showWeekStrip) 46f + 14f * textScale else 0f
            val calendar = if (settings.showCalendar && !sideCalendar) 22f + 14f * textScale + strip + shownEvents * (if (settings.compactCalendar) 15f * textScale + 2f else 18f * textScale + 6f) else 0f
            // Title row as tall as its 48dp buttons, then the song.
            val musicHeight = if (musicVisible) 12f + 48f + 24f * textScale else 0f
            val noteLines = if (settings.note.isBlank()) 1 else fit.maxEvents
            val note = if (settings.showNote) 20f + 14f * textScale + 8f + noteLines * 20f * textScale else 0f
            val todoLines = settings.todos.count { it.checkedAt == null }.coerceIn(1, fit.maxEvents)
            val todo = if (settings.showTodo) 42f + todoLines * 24f * textScale + 8f else 0f
            val sectionCount = listOf(settings.showCalendar && !sideCalendar, musicVisible, settings.showNote, settings.showTodo).count { it }
            val lines = if (sectionCount > 0) (sectionCount + 1) * 1f else 0f
            val shortcuts = if (settings.showShortcuts) 28f + 20f * textScale else 20f
            val rows = rowCount * (fit.textSp * 1.2f * textScale + fit.padDp * 2)
            val screenTime = if (sideScreenTime) 0f else 12f + 18f * textScale
            val weatherHeight = if (settings.showWeather) 24f * textScale + 4f else 0f
            return clock + screenTime + weatherHeight + noticeLines + calendar + musicHeight + note + todo + lines + rows + shortcuts + 6f + 36f // 36 = air
        }

        val options = listOf(
            Fit(preferredRing, 26f, 10f, maxEvents = 3),
            Fit(preferredRing, 24f, 8f, maxEvents = 3),
            Fit(preferredRing, 24f, 7f, maxEvents = 2),
            Fit(preferredRing * 0.88f, 22f, 6f, maxEvents = 2),
            Fit(132.dp, 22f, 4f, maxEvents = 1),
            Fit(132.dp, 18f, 2f, maxEvents = 1),
        ).map { if (settings.compactCalendar) Fit(it.ring, it.textSp, it.padDp, when (it.maxEvents) { 3 -> 8; 2 -> 5; else -> 3 }) else it }
        val fit = options.firstOrNull { heightOf(it) <= available } ?: options.last()
        val ring = fit.ring.coerceAtLeast(132.dp)
        val favoriteSize = fit.textSp.sp
        val favoritePadding = fit.padDp.dp

        if (settings.showWallpaper) {
            wallpaperImage?.let { image ->
                Image(image.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (wallpaperImage == null) 1f else 1f - settings.wallpaperBrightness / 100f)))
        }

        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 18.dp),
            horizontalAlignment = settings.homeAlign.horizontal(),
        ) {
            // The split clock is a header: it sits near the top. The ring floats lower, as a centrepiece.
            Spacer(Modifier.weight(if (split) 0.35f else 0.9f))

            val clockTap: () -> Unit = { performClockTap(context, settings.clockTap, apps, onLaunch) { onOpenReview(null) } }
            val openScreenTime: () -> Unit = {
                Graph.state.did(Tip.SCREEN_TIME)
                if (usageAccess) onOpenReview(null) else Perms.openUsageAccess(context)
            }
            if (split) {
                SplitClockRow(
                    settings = settings,
                    now = now,
                    timeSize = splitTimeSp.sp,
                    onTap = clockTap,
                    onLongPress = { Graph.state.did(Tip.CLOCK); choosingClockTap = true },
                    highlight = tip == Tip.CLOCK,
                ) {
                    if (sideCalendar) {
                        SplitCalendar(
                            today = now.toLocalDate(),
                            events = events,
                            hasAccess = calendarAccess,
                            use24h = use24h,
                            onClick = { openCalendarApp(context) },
                            onLongPress = { choosingSplitSide = true },
                            onRequestAccess = { askCalendar.launch(Manifest.permission.READ_CALENDAR) },
                            maxEvents = if (settings.compactCalendar) 3 else 2,
                            compact = settings.compactCalendar,
                        )
                    } else {
                        SplitScreenTime(today, usageAccess, onClick = openScreenTime, onLongPress = { choosingSplitSide = true }, highlight = tip == Tip.SCREEN_TIME)
                    }
                }
            } else {
                HomeClock(
                    settings = settings,
                    now = now,
                    ringSize = ring,
                    onTap = clockTap,
                    onLongPress = { Graph.state.did(Tip.CLOCK); choosingClockTap = true },
                    modifier = (if (settings.clockStyle == ClockStyle.RING) Modifier.align(Alignment.CenterHorizontally) else Modifier).tipTarget(tip == Tip.CLOCK, c.fg),
                )
            }

            if (!sideScreenTime) {
                VSpace(10.dp)
                ScreenTimeLine(
                    today = today,
                    hasAccess = usageAccess,
                    onClick = openScreenTime,
                    modifier = Modifier.tipTarget(tip == Tip.SCREEN_TIME, c.fg),
                )
            }
            if (settings.showWallpaper && wallpaperImage == null) {
                T("Choose wallpaper image  →", Modifier.fillMaxWidth().clickable { onOpenSettings("appearance") }.padding(vertical = 4.dp),
                    size = 13.sp, color = c.dim, align = TextAlign.Center, maxLines = 1)
            }
            if (settings.showWeather) {
                T(
                    when {
                        !weatherAccess -> "Weather  ·  Allow location  →"
                        weather != null -> "${weather!!.temperature}°  ·  ${weather!!.description}"
                        else -> "Weather unavailable  ·  Retry  →"
                    },
                    Modifier.fillMaxWidth().press(onLongClick = { choosingWeatherApp = true }) {
                        if (!weatherAccess) askWeather.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        else if (weather != null && settings.weatherApp.isNotBlank()) {
                            apps.firstOrNull { it.key == settings.weatherApp }?.let(onLaunch) ?: run { weatherRetry++ }
                        } else weatherRetry++
                    }.padding(horizontal = 12.dp, vertical = 3.dp),
                    size = 13.sp, color = c.dim, align = TextAlign.Center, maxLines = 1,
                )
            }

            // One-line notices. They disappear as soon as they have been dealt with.
            if (setupIncomplete || pendingReview != null || tip != null) {
                VSpace(14.dp)
                if (pendingReview != null) {
                    Notice("Your weekly review is ready  →", strong = true) { onOpenReview(pendingReview) }
                }
                if (setupIncomplete) {
                    Notice("Finish setting up Rkd Launcher  →", strong = false) { onOpenSettings("setup") }
                }
                // One thing a new user could not guess, until they have done it once. A tap moves on.
                tip?.let { current ->
                    TipLine(current) {
                        Graph.state.nextTip()
                        if (current == Tip.SECTIONS) onOpenSettings("home")
                    }
                }
            }

            Spacer(Modifier.weight(0.8f))

            // The sections, one under the other as in the owner's sketch: calendar, music, note. No
            // boxes: a line above each and one below the last, the same line the split clock uses.
            val calendarSection = settings.showCalendar && !sideCalendar
            val anySection = calendarSection || musicVisible || settings.showNote || settings.showTodo
            if (calendarSection) {
                Hairline(Modifier.padding(horizontal = 12.dp), c.faint)
                CalendarWidget(
                    today = now.toLocalDate(),
                    mondayStart = settings.weekStartsMonday,
                    events = events,
                    hasAccess = calendarAccess,
                    use24h = use24h,
                    onClick = { openCalendarApp(context) },
                    onRequestAccess = { askCalendar.launch(Manifest.permission.READ_CALENDAR) },
                    maxEvents = fit.maxEvents,
                    compact = settings.compactCalendar,
                    calendarName = shownCalendar?.shortName,
                    showWeekStrip = settings.showWeekStrip,
                )
            }
            if (musicVisible) {
                Hairline(Modifier.padding(horizontal = 12.dp), c.faint)
                MusicSection(
                    music,
                    // Nothing playing: the music app of the user's choice; the first tap asks which.
                    onOpenDefault = { apps.firstOrNull { it.key == settings.musicApp }?.let(onLaunch) ?: run { choosingMusicApp = true } },
                    onChoose = { Graph.state.did(Tip.SECTION_APPS); choosingMusicApp = true },
                    modifier = Modifier.tipTarget(tip == Tip.SECTION_APPS, c.fg),
                )
            }
            if (settings.showNote) {
                Hairline(Modifier.padding(horizontal = 12.dp), c.faint)
                NoteSection(
                    note = settings.note,
                    appLabel = noteApp?.label,
                    maxLines = fit.maxEvents,
                    onEdit = { editingNote = true },
                    // One page of the app, if a link to it was given; the app itself otherwise.
                    onOpenApp = { noteApp?.let { app -> if (settings.noteLink.isBlank() || !openLink(context, settings.noteLink, app.packageName)) onLaunch(app) } },
                    onLongClick = { Graph.state.did(Tip.SECTION_APPS); noteMenu = true },
                    modifier = Modifier.tipTarget(tip == Tip.SECTION_APPS, c.fg),
                )
            }
            if (settings.showTodo) {
                Hairline(Modifier.padding(horizontal = 12.dp), c.faint)
                TodoSection(settings.todos, fit.maxEvents,
                    onCheck = { id -> Graph.settings.update { s -> s.copy(todos = s.todos.map { if (it.id == id) it.copy(checkedAt = System.currentTimeMillis()) else it }) } },
                    onAdd = { addingTodo = true }, onShowChecked = { showingTodos = true })
            }
            if (anySection) Hairline(Modifier.padding(horizontal = 12.dp), c.faint)

            Spacer(Modifier.weight(if (anySection) 0.9f else 0.6f))

            // Fast apps
            if (favorites.isEmpty()) {
                T(
                    "Swipe left for your apps.\nLong-press one to pin it here.",
                    Modifier.fillMaxWidth().clickable { onOpenDrawer(false) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    size = 15.sp, color = c.dim, align = settings.homeAlign.text(), lineHeight = 23.sp,
                )
            } else {
                val columns = settings.fastAppColumns.coerceIn(1, 2)
                for (appsInRow in favorites.chunked(columns)) {
                    Row(if (columns == 2) Modifier.fillMaxWidth() else Modifier, verticalAlignment = Alignment.CenterVertically) {
                        for (app in appsInRow) {
                            Row(
                                Modifier
                                    .then(if (columns == 2) Modifier.weight(1f) else Modifier)
                                    .press(onLongClick = { onAppMenu(app) }) { onLaunch(app) }
                                    .padding(vertical = favoritePadding, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                T(
                                    app.label,
                                    Modifier.weight(1f, fill = false).then(if (hasColourGlyphs(app.label)) Modifier.monochrome() else Modifier),
                                    size = favoriteSize,
                                    color = if (app.key in spent) c.faint else c.fg,
                                    weight = FontWeight.Normal,
                                    maxLines = 1,
                                )
                                if (app.isWorkProfile) WorkBadge(Modifier.padding(start = 6.dp), side = (favoriteSize.value * 0.62f).dp)
                            }
                        }
                        if (appsInRow.size < columns) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Corner shortcuts
            if (settings.showShortcuts) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (left in listOf(true, false)) {
                        val spec = if (left) settings.leftShortcut else settings.rightShortcut
                        T(
                            shortcutLabel(spec, apps),
                            Modifier
                                .tipTarget(tip == Tip.CORNERS, c.fg)
                                .press(onLongClick = { Graph.state.did(Tip.CORNERS); editingShortcut = left }) { launchShortcut(context, spec, apps, onLaunch) }
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            size = 15.sp, color = c.dim, maxLines = 1,
                        )
                    }
                }
            } else {
                VSpace(20.dp)
            }
            VSpace(6.dp)
        }
    }

    if (choosingClockTap) ClockTapDialog(settings, apps) { choosingClockTap = false }

    val visibleApps = remember(apps, settings.hidden) { apps.filter { it.key !in settings.hidden } }
    if (choosingWeatherApp) {
        AppPickerDialog(
            title = "Weather app",
            subtitle = "Opens when you tap the weather line. Without an app, tapping refreshes the weather.",
            apps = visibleApps,
            onDismiss = { choosingWeatherApp = false },
            leading = listOf("None (tap to refresh)" to { Graph.settings.update { it.copy(weatherApp = "") } }),
            onPick = { app -> Graph.settings.update { it.copy(weatherApp = app.key) } },
        )
    }
    if (choosingMusicApp) {
        MusicAppPicker(
            apps = visibleApps,
            subtitle = "Opens when you tap the music section and nothing is playing. Long-press the section to change it.",
            onDismiss = { choosingMusicApp = false },
            onPick = { app ->
                Graph.settings.update { it.copy(musicApp = app.key) }
                onLaunch(app)
            },
        )
    }
    if (choosingNoteApp) {
        AppPickerDialog(
            title = "Notes app",
            subtitle = "Shown as a word next to the note's title; a tap on it opens the app. Your own lines stay where they are.",
            apps = visibleApps,
            onDismiss = { choosingNoteApp = false },
            leading = listOf("None" to { Graph.settings.update { it.copy(noteApp = "", noteLink = "") } }),
            onPick = { app -> Graph.settings.update { it.copy(noteApp = app.key, noteLink = "") } },
        )
    }
    if (noteMenu) {
        FocusDialog({ noteMenu = false }, title = "Note") {
            MenuRow("Notes app", detail = noteApp?.label ?: "none") {
                noteMenu = false
                choosingNoteApp = true
            }
            if (noteApp != null) {
                MenuRow("Open one page instead of the app", detail = if (settings.noteLink.isBlank()) null else "set") {
                    noteMenu = false
                    editingNoteLink = true
                }
            }
        }
    }

    if (editingNoteLink) {
        TextInputDialog(
            title = "Link to the page",
            initial = settings.noteLink,
            placeholder = "https://…",
            subtitle = "In ${noteApp?.label ?: "your notes app"}, open the page, choose Share or Copy link, and paste it here. Leave it empty to open the app itself.",
            onDismiss = { editingNoteLink = false },
        ) { link -> Graph.settings.update { it.copy(noteLink = link) } }
    }

    if (editingNote) {
        TextInputDialog(
            title = "Note",
            initial = settings.note,
            placeholder = "Write something",
            onDismiss = { editingNote = false },
            multiline = true,
        ) { text -> Graph.settings.update { it.copy(note = text) } }
    }
    if (showingTodos) {
        FocusDialog({ showingTodos = false }, title = "To-do", subtitle = "Tap a task to check or uncheck it. Checked tasks disappear after seven days.", tall = true) {
            MenuRow("+ Add task") { showingTodos = false; addingTodo = true }
            LazyColumn(Modifier.weight(1f)) {
                val active = settings.todos.filter { it.checkedAt == null }
                val completed = settings.todos.filter { it.checkedAt != null }
                items(active.size) { index ->
                    val item = active[index]
                    MenuRow(item.text, detail = "□") {
                        Graph.settings.update { s -> s.copy(todos = s.todos.map { if (it.id == item.id) it.copy(checkedAt = System.currentTimeMillis()) else it }) }
                    }
                }
                if (completed.isNotEmpty()) item { MenuRow("Checked", detail = "${completed.size}") { } }
                items(completed.size) { index ->
                    val item = completed[index]
                    MenuRow(item.text, detail = "✓") {
                        Graph.settings.update { s -> s.copy(todos = s.todos.map { if (it.id == item.id) it.copy(checkedAt = null) else it }) }
                    }
                }
            }
        }
    }
    if (addingTodo) {
        TextInputDialog(title = "Add task", initial = "", placeholder = "One task", confirmLabel = "Add",
            onDismiss = { addingTodo = false }) { text ->
            if (text.isNotBlank()) Graph.settings.update { it.copy(todos = it.todos + TodoItem.create(text)) }
        }
    }
    if (choosingSplitSide) {
        ChoiceDialog(
            "Next to the clock",
            SplitSide.entries.map { it to it.label },
            if (sideCalendar) SplitSide.CALENDAR else SplitSide.SCREEN_TIME,
            { choosingSplitSide = false },
        ) { side ->
            // Choosing the calendar is also the moment to switch its section on and to ask for access.
            Graph.settings.update { it.copy(splitSide = side, showCalendar = it.showCalendar || side == SplitSide.CALENDAR) }
            if (side == SplitSide.CALENDAR && !CalendarRepository.hasAccess(context)) askCalendar.launch(Manifest.permission.READ_CALENDAR)
        }
    }

    editingShortcut?.let { left ->
        val visible = remember(apps, settings.hidden) { apps.filter { it.key !in settings.hidden } }
        AppPickerDialog(
            title = if (left) "Left shortcut" else "Right shortcut",
            subtitle = "Opens from the bottom corner of the home screen.",
            apps = visible,
            onDismiss = { editingShortcut = null },
            leading = listOf(
                "Phone" to { setShortcut(left, SHORTCUT_PHONE) },
                "Camera" to { setShortcut(left, SHORTCUT_CAMERA) },
            ),
            onPick = { setShortcut(left, it.key) },
        )
    }
}

/**
 * A tip, framed so that it cannot be mistaken for part of the home screen: the gesture in full brightness, what it does next to it, quieter. Short enough for one
 * line; if a large text size makes it longer it wraps, it is never cut off. A tap skips it.
 */
@Composable
private fun TipLine(tip: Tip, onClick: () -> Unit) {
    val c = LocalFocusColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .border(1.dp, c.fg, RoundedCornerShape(12.dp))
            .press(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        T(
            "TIP ${tip.ordinal + 1} / ${Tip.entries.size}",
            Modifier.background(c.fg).padding(horizontal = 6.dp, vertical = 1.dp),
            size = 10.sp, color = c.bg, weight = FontWeight.Medium, letterSpacing = 1.2.sp, maxLines = 1,
        )
        VSpace(5.dp)
        Row(verticalAlignment = Alignment.Top) {
            T(tip.gesture, size = 16.sp, weight = FontWeight.Medium, maxLines = 1)
            T("  →  ", size = 16.sp, color = c.dim, maxLines = 1)
            T(tip.result, Modifier.weight(1f, fill = false), size = 16.sp, color = c.dim)
        }
    }
}

@Composable
private fun Notice(text: String, strong: Boolean, lines: Int = 1, onClick: () -> Unit) {
    val c = LocalFocusColors.current
    T(
        text,
        Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        size = 14.sp,
        color = if (strong) c.fg else c.dim,
        weight = if (strong) FontWeight.Medium else FontWeight.Normal,
        maxLines = lines,
        lineHeight = 20.sp,
    )
}

private fun setShortcut(left: Boolean, spec: String) {
    Graph.settings.update { if (left) it.copy(leftShortcut = spec) else it.copy(rightShortcut = spec) }
}

fun shortcutLabel(spec: String, apps: List<AppEntry>): String = when (spec) {
    SHORTCUT_PHONE -> "Phone"
    SHORTCUT_CAMERA -> "Camera"
    else -> apps.firstOrNull { it.key == spec }?.label ?: "Not set"
}

private fun launchShortcut(context: Context, spec: String, apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit) {
    when (spec) {
        SHORTCUT_PHONE -> Perms.start(context, Intent(Intent.ACTION_DIAL), options = launchOptions(context))
        SHORTCUT_CAMERA -> Perms.start(
            context,
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            options = launchOptions(context),
        )
        else -> apps.firstOrNull { it.key == spec }?.let(onLaunch)
    }
}

/** Opens [link] in [packageName] if that app takes it, else in whatever does. False when nothing could. */
private fun openLink(context: Context, link: String, packageName: String): Boolean {
    val uri = link.trim().toUri()
    if (uri.scheme.isNullOrEmpty()) return false
    return Perms.start(
        context,
        Intent(Intent.ACTION_VIEW, uri).setPackage(packageName),
        Intent(Intent.ACTION_VIEW, uri),
        options = launchOptions(context),
    )
}

private fun openCalendarApp(context: Context) {
    Perms.start(
        context,
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
        // Calendars that do not register the category still open on "show me this moment".
        Intent(Intent.ACTION_VIEW, "content://com.android.calendar/time/${System.currentTimeMillis()}".toUri()),
        options = launchOptions(context),
    )
}

/** Name of a clock tap action, for settings. */
fun clockTapLabel(spec: String, apps: List<AppEntry>): String = when (spec) {
    TAP_ALARMS -> "Alarms"
    TAP_CALENDAR -> "Calendar"
    TAP_SCREEN_TIME -> "Screen time"
    TAP_BATTERY -> "Battery"
    TAP_NOTHING -> "Nothing"
    else -> apps.firstOrNull { it.key == spec }?.label ?: "Not set"
}

private fun performClockTap(
    context: Context,
    spec: String,
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onOpenScreenTime: () -> Unit,
) {
    when (spec) {
        TAP_ALARMS -> Perms.start(context, Intent(AlarmClock.ACTION_SHOW_ALARMS), options = launchOptions(context))
        TAP_CALENDAR -> openCalendarApp(context)
        TAP_SCREEN_TIME -> onOpenScreenTime()
        TAP_BATTERY -> Perms.start(
            context,
            Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
            Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS),
            options = launchOptions(context),
        )
        TAP_NOTHING -> Unit
        else -> apps.firstOrNull { it.key == spec }?.let(onLaunch)
    }
}

/** Pulls the notification shade down: via the timer service when it is on, else the status-bar service. */
@SuppressLint("WrongConstant", "PrivateApi")
private fun expandNotifications(context: Context) {
    if (FocusAccessibilityService.openNotifications()) return
    try {
        val statusBar = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(statusBar)
    } catch (_: Exception) {
    }
}
