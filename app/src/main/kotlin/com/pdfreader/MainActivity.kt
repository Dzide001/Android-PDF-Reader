package com.pdfreader

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.LruCache
import android.view.MotionEvent
import org.json.JSONArray
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlin.math.abs
import kotlin.math.hypot

private data class HighlightRegion(
    val leftFrac: Float,
    val topFrac: Float,
    val rightFrac: Float,
    val bottomFrac: Float
)

private data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float,  // Range: 0.0 - 1.0; 0 for touch, pressure-sensitive for stylus
    val size: Float       // Calculated stroke width based on pressure
)

private data class Stroke(
    val points: List<StrokePoint>,
    val color: Long = 0xFF000000,  // ARGB; default black
    val isErasing: Boolean = false,
    val pageIndex: Int
)

private data class TextCharBox(
    val text: String,
    val leftFrac: Float,
    val topFrac: Float,
    val rightFrac: Float,
    val bottomFrac: Float
)

private enum class ShapeType {
    RECTANGLE,
    ELLIPSE,
    ARROW
}

private data class ShapeAnnotation(
    val type: ShapeType,
    val leftFrac: Float,
    val topFrac: Float,
    val rightFrac: Float,
    val bottomFrac: Float,
    val color: Long = 0xFFE53935,
    val strokeWidthPx: Float = 3f
)

private enum class TextHandleDrag {
    START,
    END
}

private data class RecentDocument(
    val id: String,
    val displayName: String,
    val uri: String,
    val pageCount: Int,
    val lastOpenedAt: Long,
    val isFavorite: Boolean
)

private class PageBitmapCache(maxEntries: Int) {
    private val cache = object : LruCache<Int, Bitmap>(maxEntries) {
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && !oldValue.isRecycled) {
                oldValue.recycle()
            }
        }
    }

    fun get(page: Int): Bitmap? = cache.get(page)

    fun put(page: Int, bitmap: Bitmap) {
        cache.put(page, bitmap)
    }

    fun clear() {
        val snapshots = cache.snapshot().values
        cache.evictAll()
        snapshots.forEach { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PdfReaderScreen()
            }
        }
    }
}

private class PdfSession(
    private val pfd: ParcelFileDescriptor,
    val renderer: PdfRenderer
) {
    fun close() {
        renderer.close()
        pfd.close()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PdfReaderScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var pdfSession by remember { mutableStateOf<PdfSession?>(null) }
    var currentPdfUri by remember { mutableStateOf<Uri?>(null) }
    val pageCache = remember(currentPdfUri) { PageBitmapCache(maxEntries = 8) }
    var recentDocuments by remember { mutableStateOf(loadRecentDocuments(context)) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isExtractingText by remember { mutableStateOf(false) }
    var isNightMode by remember { mutableStateOf(loadNightModePreference(context)) }
    var isContinuousMode by remember { mutableStateOf(loadContinuousModePreference(context)) }
    var isHighlightMode by remember { mutableStateOf(false) }
    var highlightsByPage by remember { mutableStateOf<Map<Int, List<HighlightRegion>>>(emptyMap()) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var viewerSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var lastRenderMs by remember { mutableStateOf<Long?>(null) }
    val renderMutex = remember(currentPdfUri) { Mutex() }
    var isToolbarDockedLeft by remember { mutableStateOf(loadToolbarDockPreference(context)) }

    // Stylus drawing state
    var isDrawingMode by remember { mutableStateOf(false) }
    var strokesByPage by remember { mutableStateOf<Map<Int, List<Stroke>>>(emptyMap()) }
    var currentStrokePoints by remember { mutableStateOf<List<StrokePoint>>(emptyList()) }
    var drawingColor by remember { mutableStateOf(0xFF000000) }  // Black
    var isEraserMode by remember { mutableStateOf(false) }
    var isStylusActive by remember { mutableStateOf(false) }
    var isPalmRejectionEnabled by remember { mutableStateOf(true) }
    var isShapeMode by remember { mutableStateOf(false) }
    var selectedShapeType by remember { mutableStateOf(ShapeType.RECTANGLE) }
    var shapesByPage by remember { mutableStateOf<Map<Int, List<ShapeAnnotation>>>(emptyMap()) }
    var shapeDragStart by remember { mutableStateOf<Offset?>(null) }
    var shapeDragCurrent by remember { mutableStateOf<Offset?>(null) }

    // True on-page text selection state (Adobe-style handles)
    var isTextSelectionMode by remember { mutableStateOf(false) }
    var textBoxesByPage by remember { mutableStateOf<Map<Int, List<TextCharBox>>>(emptyMap()) }
    var selectedTextRange by remember { mutableStateOf<IntRange?>(null) }
    var activeTextHandle by remember { mutableStateOf<TextHandleDrag?>(null) }
    var pageOrder by remember(currentPdfUri) { mutableStateOf<List<Int>>(emptyList()) }
    var showPageOrganizer by remember { mutableStateOf(false) }
    var draggingOrganizerIndex by remember { mutableIntStateOf(-1) }
    var organizerDragDy by remember { mutableFloatStateOf(0f) }

    val sessionPageCount = pdfSession?.renderer?.pageCount ?: 0
    val effectivePageOrder = if (pageOrder.isNotEmpty()) pageOrder else (0 until sessionPageCount).toList()
    val currentOrderPosition = effectivePageOrder.indexOf(currentPage).let { idx ->
        if (idx >= 0) idx else 0
    }
    val hasPreviousPage = effectivePageOrder.isNotEmpty() && currentOrderPosition > 0
    val hasNextPage = effectivePageOrder.isNotEmpty() && currentOrderPosition < effectivePageOrder.lastIndex
    val pageIndicatorText = if (pdfSession == null) {
        "No document selected"
    } else {
        val visibleIndex = if (effectivePageOrder.isEmpty()) 0 else (currentOrderPosition + 1)
        val visibleTotal = effectivePageOrder.size
        if (isContinuousMode) {
            "Continuous • Page $visibleIndex / $visibleTotal"
        } else {
            "Page $visibleIndex / $visibleTotal"
        }
    }

    val goToPreviousPage = {
        if (effectivePageOrder.isEmpty()) {
            Unit
        } else {
            val pos = effectivePageOrder.indexOf(currentPage)
            if (pos > 0) currentPage = effectivePageOrder[pos - 1]
        }
    }
    val goToNextPage = {
        if (effectivePageOrder.isEmpty()) {
            Unit
        } else {
            val pos = effectivePageOrder.indexOf(currentPage)
            if (pos in 0 until effectivePageOrder.lastIndex) currentPage = effectivePageOrder[pos + 1]
        }
    }
    val undoLastAnnotation = {
        when {
            isDrawingMode && (strokesByPage[currentPage]?.isNotEmpty() == true) -> {
                strokesByPage = strokesByPage.toMutableMap().apply { put(currentPage, getValue(currentPage).dropLast(1)) }
            }

            isShapeMode && (shapesByPage[currentPage]?.isNotEmpty() == true) -> {
                shapesByPage = shapesByPage.toMutableMap().apply { put(currentPage, getValue(currentPage).dropLast(1)) }
            }

            isHighlightMode && (highlightsByPage[currentPage]?.isNotEmpty() == true) -> {
                highlightsByPage = highlightsByPage.toMutableMap().apply { put(currentPage, getValue(currentPage).dropLast(1)) }
            }

            (shapesByPage[currentPage]?.isNotEmpty() == true) -> {
                shapesByPage = shapesByPage.toMutableMap().apply { put(currentPage, getValue(currentPage).dropLast(1)) }
            }

            (strokesByPage[currentPage]?.isNotEmpty() == true) -> {
                strokesByPage = strokesByPage.toMutableMap().apply { put(currentPage, getValue(currentPage).dropLast(1)) }
            }

            (highlightsByPage[currentPage]?.isNotEmpty() == true) -> {
                highlightsByPage = highlightsByPage.toMutableMap().apply { put(currentPage, getValue(currentPage).dropLast(1)) }
            }
        }
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val targetZoom = (zoom * zoomChange).coerceIn(1f, 4f)
        val (softX, softY) = applyPanResistance(
            rawOffsetX = offsetX + panChange.x,
            rawOffsetY = offsetY + panChange.y,
            zoom = targetZoom,
            viewport = viewerSize
        )

        zoom = targetZoom
        offsetX = softX
        offsetY = softY
    }

    LaunchedEffect(transformState.isTransformInProgress) {
        if (transformState.isTransformInProgress) return@LaunchedEffect

        val targetZoom = if (zoom <= 1.01f) 1f else zoom
        val (targetX, targetY) = if (targetZoom <= 1f) {
            0f to 0f
        } else {
            clampPanOffset(
                rawOffsetX = offsetX,
                rawOffsetY = offsetY,
                zoom = targetZoom,
                viewport = viewerSize
            )
        }

        val duration = 180

        if (abs(zoom - targetZoom) > 0.001f) {
            animate(
                initialValue = zoom,
                targetValue = targetZoom,
                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
            ) { value, _ ->
                zoom = value
            }
        }

        if (abs(offsetX - targetX) > 0.5f) {
            animate(
                initialValue = offsetX,
                targetValue = targetX,
                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
            ) { value, _ ->
                offsetX = value
            }
        }

        if (abs(offsetY - targetY) > 0.5f) {
            animate(
                initialValue = offsetY,
                targetValue = targetY,
                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
            ) { value, _ ->
                offsetY = value
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri: Uri? ->
        errorMessage = null
        if (uri == null) return@rememberLauncherForActivityResult

        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some providers do not support persisted permissions.
        }

        try {
            pdfSession?.close()
            pageCache.clear()
            currentPage = 0

            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd == null) {
                errorMessage = "Unable to open selected file."
                return@rememberLauncherForActivityResult
            }

            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount == 0) {
                renderer.close()
                pfd.close()
                errorMessage = "PDF has no pages."
                return@rememberLauncherForActivityResult
            }

            pdfSession = PdfSession(pfd, renderer)
            currentPdfUri = uri
            pageOrder = (0 until renderer.pageCount).toList()
            showPageOrganizer = false

            recentDocuments = upsertRecentDocument(
                existing = recentDocuments,
                uri = uri,
                displayName = resolveDisplayName(context, uri),
                pageCount = renderer.pageCount
            )
            saveRecentDocuments(context, recentDocuments)
        } catch (e: Exception) {
            errorMessage = e.message ?: "Failed to open PDF"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pageCache.clear()
            pdfSession?.close()
        }
    }

    LaunchedEffect(pdfSession, currentPage, isContinuousMode) {
        val session = pdfSession ?: return@LaunchedEffect
        if (pageOrder.size != session.renderer.pageCount) {
            pageOrder = (0 until session.renderer.pageCount).toList()
        }
        if (isContinuousMode) {
            pageBitmap = null
            lastRenderMs = null
            return@LaunchedEffect
        }

        val cached = pageCache.get(currentPage)
        if (cached != null && !cached.isRecycled) {
            pageBitmap = cached
            lastRenderMs = 0L
        } else {
            val started = SystemClock.elapsedRealtime()
            val rendered = renderPageBitmapSafely(session.renderer, currentPage, renderMutex)
            pageCache.put(currentPage, rendered)
            pageBitmap = rendered
            lastRenderMs = SystemClock.elapsedRealtime() - started
        }

        // Prefetch adjacent pages for faster navigation.
        val pageCount = session.renderer.pageCount
        val neighbors = listOf(currentPage - 1, currentPage + 1)
            .filter { it in 0 until pageCount }
            .filter { pageCache.get(it) == null }

        neighbors.forEach { neighborPage ->
            scope.launch(Dispatchers.Default) {
                runCatching {
                    val neighborBitmap = renderPageBitmapSafely(session.renderer, neighborPage, renderMutex)
                    withContext(Dispatchers.Main) {
                        pageCache.put(neighborPage, neighborBitmap)
                    }
                }
            }
        }

        zoom = 1f
        offsetX = 0f
        offsetY = 0f
        selectedTextRange = null
        activeTextHandle = null
        shapeDragStart = null
        shapeDragCurrent = null
    }

    @Composable
    fun RecentLibrarySection() {
        if (recentDocuments.isNotEmpty()) {
            Text("Recent PDFs", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                items(recentDocuments) { doc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = doc.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    openDocumentFromUriString(
                                        context = context,
                                        uriString = doc.uri,
                                        onOpened = { session, uri ->
                                            pdfSession?.close()
                                            pageCache.clear()
                                            currentPage = 0
                                            pdfSession = session
                                            currentPdfUri = uri
                                            errorMessage = null

                                            recentDocuments = upsertRecentDocument(
                                                existing = recentDocuments,
                                                uri = uri,
                                                displayName = doc.displayName,
                                                pageCount = session.renderer.pageCount,
                                                keepFavorite = doc.isFavorite
                                            )
                                            saveRecentDocuments(context, recentDocuments)
                                        },
                                        onError = { msg -> errorMessage = msg }
                                    )
                                }
                            }
                        ) {
                            Text("Open")
                        }
                        Button(
                            onClick = {
                                recentDocuments = recentDocuments.map {
                                    if (it.id == doc.id) it.copy(isFavorite = !it.isFavorite) else it
                                }
                                saveRecentDocuments(context, recentDocuments)
                            }
                        ) {
                            Text(if (doc.isFavorite) "★" else "☆")
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ActionControlsSection(vertical: Boolean = false, showDockToggle: Boolean = false) {
        val palette = listOf(
            0xFF000000,
            0xFFE53935,
            0xFF1E88E5,
            0xFF43A047,
            0xFF8E24AA
        )

        @Composable
        fun ButtonsContent() {
            Button(onClick = { openDocumentLauncher.launch(arrayOf("application/pdf")) }) { Text("📂") }

            Button(
                onClick = goToPreviousPage,
                enabled = !isContinuousMode && pdfSession != null && hasPreviousPage
            ) { Text("⬅") }

            Button(
                onClick = goToNextPage,
                enabled = !isContinuousMode && pdfSession != null && hasNextPage
            ) { Text("➡") }

            Button(
                onClick = {
                    if (isTextSelectionMode) {
                        isTextSelectionMode = false
                        selectedTextRange = null
                        activeTextHandle = null
                        return@Button
                    }

                    val uri = currentPdfUri ?: return@Button
                    isExtractingText = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val boxes = extractPageCharBoxes(context, uri, currentPage)
                            textBoxesByPage = textBoxesByPage.toMutableMap().apply { put(currentPage, boxes) }
                            isTextSelectionMode = true
                            isHighlightMode = false
                            isDrawingMode = false
                            isShapeMode = false
                            selectedTextRange = null
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Failed to prepare page text selection"
                        } finally {
                            isExtractingText = false
                        }
                    }
                },
                enabled = pdfSession != null && !isExtractingText
            ) {
                Text(if (isTextSelectionMode) "T✓" else "T")
            }

            Button(
                onClick = {
                    if (!isContinuousMode) {
                        isHighlightMode = !isHighlightMode
                        if (isHighlightMode) {
                            isDrawingMode = false
                            isTextSelectionMode = false
                            isShapeMode = false
                        }
                    }
                },
                enabled = pdfSession != null && !isContinuousMode
            ) { Text(if (isHighlightMode) "🖍✓" else "🖍") }

            Button(
                onClick = {
                    if (!isContinuousMode) {
                        isDrawingMode = !isDrawingMode
                        if (isDrawingMode) {
                            isHighlightMode = false
                            isTextSelectionMode = false
                            isShapeMode = false
                        }
                    }
                },
                enabled = pdfSession != null && !isContinuousMode
            ) { Text(if (isDrawingMode) "✍✓" else "✍") }

            Button(
                onClick = {
                    if (!isContinuousMode) {
                        isShapeMode = !isShapeMode
                        if (isShapeMode) {
                            isDrawingMode = false
                            isHighlightMode = false
                            isTextSelectionMode = false
                        }
                    }
                },
                enabled = pdfSession != null && !isContinuousMode
            ) { Text(if (isShapeMode) "▭✓" else "▭") }

            Button(
                onClick = {
                    selectedShapeType = when (selectedShapeType) {
                        ShapeType.RECTANGLE -> ShapeType.ELLIPSE
                        ShapeType.ELLIPSE -> ShapeType.ARROW
                        ShapeType.ARROW -> ShapeType.RECTANGLE
                    }
                },
                enabled = pdfSession != null && isShapeMode && !isContinuousMode
            ) {
                Text(
                    when (selectedShapeType) {
                        ShapeType.RECTANGLE -> "▭"
                        ShapeType.ELLIPSE -> "◯"
                        ShapeType.ARROW -> "↗"
                    }
                )
            }

            Button(
                onClick = { isEraserMode = !isEraserMode },
                enabled = pdfSession != null && isDrawingMode && !isContinuousMode
            ) { Text(if (isEraserMode) "🧽✓" else "🧽") }

            Button(
                onClick = { showPageOrganizer = !showPageOrganizer },
                enabled = pdfSession != null
            ) { Text(if (showPageOrganizer) "🗂✓" else "🗂") }

            Button(
                onClick = undoLastAnnotation,
                enabled = !isContinuousMode && (
                    (highlightsByPage[currentPage]?.isNotEmpty() == true) ||
                        (strokesByPage[currentPage]?.isNotEmpty() == true) ||
                        (shapesByPage[currentPage]?.isNotEmpty() == true)
                    )
            ) { Text("↶") }

            Button(enabled = false, onClick = {}) { Text("↷") }

            Button(
                onClick = { isPalmRejectionEnabled = !isPalmRejectionEnabled },
                enabled = pdfSession != null && isDrawingMode && !isContinuousMode
            ) { Text(if (isPalmRejectionEnabled) "✋✓" else "✋") }

            if (isDrawingMode && !isContinuousMode) {
                palette.forEach { colorValue ->
                    Button(
                        onClick = { drawingColor = colorValue },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ComposeColor(colorValue)
                        )
                    ) {
                        Text(if (drawingColor == colorValue) "●" else "○", color = ComposeColor.White)
                    }
                }
            }

            Button(
                onClick = {
                    highlightsByPage = highlightsByPage.toMutableMap().apply { remove(currentPage) }
                },
                enabled = !isContinuousMode && (highlightsByPage[currentPage]?.isNotEmpty() == true)
            ) { Text("CLR-H") }

            Button(
                onClick = {
                    strokesByPage = strokesByPage.toMutableMap().apply { remove(currentPage) }
                },
                enabled = !isContinuousMode && (strokesByPage[currentPage]?.isNotEmpty() == true)
            ) { Text("CLR-D") }

            Button(
                onClick = {
                    shapesByPage = shapesByPage.toMutableMap().apply { remove(currentPage) }
                },
                enabled = !isContinuousMode && (shapesByPage[currentPage]?.isNotEmpty() == true)
            ) { Text("CLR-S") }

            Button(
                onClick = {
                    isContinuousMode = !isContinuousMode
                    saveContinuousModePreference(context, isContinuousMode)
                    if (isContinuousMode) {
                        isHighlightMode = false
                        isDrawingMode = false
                        isShapeMode = false
                        isTextSelectionMode = false
                        zoom = 1f
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            ) { Text(if (isContinuousMode) "📄" else "📜") }

            Button(
                onClick = {
                    isNightMode = !isNightMode
                    saveNightModePreference(context, isNightMode)
                }
            ) { Text(if (isNightMode) "☀" else "🌙") }

            if (showDockToggle) {
                Button(
                    onClick = {
                        isToolbarDockedLeft = !isToolbarDockedLeft
                        saveToolbarDockPreference(context, isToolbarDockedLeft)
                    }
                ) {
                    Text(if (isToolbarDockedLeft) "📌" else "🪟")
                }
            }
        }

        if (vertical) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ButtonsContent()
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ButtonsContent()
            }
        }
    }

    @Composable
    fun ViewerSection(modifier: Modifier = Modifier) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = pageBitmap
            if (bitmap == null) {
                Text("Open a PDF to begin")
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged {
                            viewerSize = it
                            val (clampedX, clampedY) = clampPanOffset(
                                rawOffsetX = offsetX,
                                rawOffsetY = offsetY,
                                zoom = zoom,
                                viewport = viewerSize
                            )
                            offsetX = clampedX
                            offsetY = clampedY
                        }
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = offsetX
                            translationY = offsetY
                        }
                        .transformable(
                            state = transformState,
                            enabled = !isHighlightMode && !isShapeMode && !isTextSelectionMode &&
                                (!isDrawingMode || isPalmRejectionEnabled)
                        )
                        .pointerInput(isHighlightMode, isDrawingMode, isShapeMode, isTextSelectionMode, zoom) {
                            if (isHighlightMode || isDrawingMode || isShapeMode || isTextSelectionMode || zoom > 1.05f) return@pointerInput
                            var dragDistance = 0f
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, dragAmount ->
                                    dragDistance += dragAmount
                                    change.consume()
                                },
                                onDragEnd = {
                                    when {
                                        dragDistance <= -120f -> goToNextPage()
                                        dragDistance >= 120f -> goToPreviousPage()
                                    }
                                    dragDistance = 0f
                                },
                                onDragCancel = {
                                    dragDistance = 0f
                                }
                            )
                        }
                        .pointerInput(isHighlightMode, isDrawingMode, isShapeMode, isTextSelectionMode, zoom) {
                            if (isHighlightMode || isDrawingMode || isShapeMode || isTextSelectionMode || zoom > 1.05f) return@pointerInput
                            detectTapGestures { tapOffset ->
                                val width = viewerSize.width.toFloat().coerceAtLeast(1f)
                                val leftZone = width * 0.25f
                                val rightZone = width * 0.75f
                                when {
                                    tapOffset.x <= leftZone -> goToPreviousPage()
                                    tapOffset.x >= rightZone -> goToNextPage()
                                }
                            }
                        }
                        .pointerInput(isHighlightMode, isDrawingMode, isShapeMode, isTextSelectionMode, currentPage, viewerSize) {
                            if (!isHighlightMode || isDrawingMode || isShapeMode || isTextSelectionMode) return@pointerInput
                            detectDragGestures(
                                onDragStart = { offset ->
                                    dragStart = offset
                                    dragCurrent = offset
                                },
                                onDragEnd = {
                                    val start = dragStart
                                    val end = dragCurrent
                                    val width = viewerSize.width.toFloat()
                                    val height = viewerSize.height.toFloat()

                                    if (start != null && end != null && width > 0f && height > 0f) {
                                        val left = minOf(start.x, end.x).coerceIn(0f, width)
                                        val top = minOf(start.y, end.y).coerceIn(0f, height)
                                        val right = maxOf(start.x, end.x).coerceIn(0f, width)
                                        val bottom = maxOf(start.y, end.y).coerceIn(0f, height)

                                        if ((right - left) > 8f && (bottom - top) > 8f) {
                                            val region = HighlightRegion(
                                                leftFrac = left / width,
                                                topFrac = top / height,
                                                rightFrac = right / width,
                                                bottomFrac = bottom / height
                                            )
                                            val pageRegions = highlightsByPage[currentPage].orEmpty()
                                            highlightsByPage = highlightsByPage.toMutableMap().apply {
                                                put(currentPage, pageRegions + region)
                                            }
                                        }
                                    }
                                    dragStart = null
                                    dragCurrent = null
                                },
                                onDragCancel = {
                                    dragStart = null
                                    dragCurrent = null
                                },
                                onDrag = { change, _ ->
                                    dragCurrent = change.position
                                    change.consume()
                                }
                            )
                        }
                        .pointerInput(isShapeMode, currentPage, viewerSize, selectedShapeType) {
                            if (!isShapeMode) return@pointerInput
                            detectDragGestures(
                                onDragStart = { offset ->
                                    shapeDragStart = offset
                                    shapeDragCurrent = offset
                                },
                                onDragCancel = {
                                    shapeDragStart = null
                                    shapeDragCurrent = null
                                },
                                onDragEnd = {
                                    val start = shapeDragStart
                                    val end = shapeDragCurrent
                                    val width = viewerSize.width.toFloat().coerceAtLeast(1f)
                                    val height = viewerSize.height.toFloat().coerceAtLeast(1f)

                                    if (start != null && end != null) {
                                        val left = minOf(start.x, end.x).coerceIn(0f, width)
                                        val top = minOf(start.y, end.y).coerceIn(0f, height)
                                        val right = maxOf(start.x, end.x).coerceIn(0f, width)
                                        val bottom = maxOf(start.y, end.y).coerceIn(0f, height)

                                        if ((right - left) > 8f && (bottom - top) > 8f) {
                                            val annotation = ShapeAnnotation(
                                                type = selectedShapeType,
                                                leftFrac = left / width,
                                                topFrac = top / height,
                                                rightFrac = right / width,
                                                bottomFrac = bottom / height
                                            )
                                            val existing = shapesByPage[currentPage].orEmpty()
                                            shapesByPage = shapesByPage.toMutableMap().apply {
                                                put(currentPage, existing + annotation)
                                            }
                                        }
                                    }

                                    shapeDragStart = null
                                    shapeDragCurrent = null
                                },
                                onDrag = { change, _ ->
                                    shapeDragCurrent = change.position
                                    change.consume()
                                }
                            )
                        }
                        .pointerInput(isTextSelectionMode, currentPage, viewerSize, textBoxesByPage, selectedTextRange) {
                            if (!isTextSelectionMode) return@pointerInput
                            val boxes = textBoxesByPage[currentPage].orEmpty()
                            detectTapGestures(
                                onLongPress = { pressOffset ->
                                    if (boxes.isEmpty()) return@detectTapGestures
                                    val xFrac = (pressOffset.x / viewerSize.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                    val yFrac = (pressOffset.y / viewerSize.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                    val index = findNearestTextIndex(boxes, xFrac, yFrac) ?: return@detectTapGestures
                                    selectedTextRange = expandToWordRange(boxes, index)
                                },
                                onTap = {
                                    activeTextHandle = null
                                }
                            )
                        }
                        .pointerInput(isTextSelectionMode, currentPage, viewerSize, textBoxesByPage, selectedTextRange) {
                            if (!isTextSelectionMode) return@pointerInput
                            val boxes = textBoxesByPage[currentPage].orEmpty()
                            if (boxes.isEmpty()) return@pointerInput

                            detectDragGestures(
                                onDragStart = { offset ->
                                    val range = selectedTextRange ?: return@detectDragGestures
                                    val handles = computeTextHandleOffsets(range, boxes, viewerSize)
                                    val startDist = offset.minus(handles.first).getDistance()
                                    val endDist = offset.minus(handles.second).getDistance()
                                    activeTextHandle = when {
                                        startDist <= 36f -> TextHandleDrag.START
                                        endDist <= 36f -> TextHandleDrag.END
                                        else -> null
                                    }
                                },
                                onDragCancel = {
                                    activeTextHandle = null
                                },
                                onDragEnd = {
                                    activeTextHandle = null
                                },
                                onDrag = { change, _ ->
                                    val handle = activeTextHandle ?: return@detectDragGestures
                                    val currentRange = selectedTextRange ?: return@detectDragGestures
                                    val xFrac = (change.position.x / viewerSize.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                    val yFrac = (change.position.y / viewerSize.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                    val nearest = findNearestTextIndex(boxes, xFrac, yFrac) ?: return@detectDragGestures

                                    selectedTextRange = when (handle) {
                                        TextHandleDrag.START -> {
                                            val start = nearest.coerceAtMost(currentRange.last)
                                            start..currentRange.last
                                        }

                                        TextHandleDrag.END -> {
                                            val end = nearest.coerceAtLeast(currentRange.first)
                                            currentRange.first..end
                                        }
                                    }
                                    change.consume()
                                }
                            )
                        }
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Rendered PDF page",
                        contentScale = ContentScale.Fit,
                        colorFilter = if (isNightMode) {
                            ColorFilter.colorMatrix(
                                ColorMatrix(
                                    floatArrayOf(
                                        -1f, 0f, 0f, 0f, 255f,
                                        0f, -1f, 0f, 0f, 255f,
                                        0f, 0f, -1f, 0f, 255f,
                                        0f, 0f, 0f, 1f, 0f
                                    )
                                )
                            )
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    val pageStrokes = strokesByPage[currentPage].orEmpty()
                    if (pageStrokes.isNotEmpty()) {
                        val strokesBitmap = remember(pageStrokes, viewerSize) {
                            renderStrokesToBitmap(viewerSize.width, viewerSize.height, pageStrokes)
                        }
                        Image(
                            bitmap = strokesBitmap.asImageBitmap(),
                            contentDescription = "Stroke annotations",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        highlightsByPage[currentPage].orEmpty().forEach { region ->
                            val left = region.leftFrac * size.width
                            val top = region.topFrac * size.height
                            val width = (region.rightFrac - region.leftFrac) * size.width
                            val height = (region.bottomFrac - region.topFrac) * size.height
                            drawRect(
                                color = ComposeColor.Yellow.copy(alpha = 0.35f),
                                topLeft = Offset(left, top),
                                size = Size(width, height)
                            )
                        }

                        shapesByPage[currentPage].orEmpty().forEach { shape ->
                            drawShapeAnnotation(shape)
                        }

                        val start = dragStart
                        val end = dragCurrent
                        if (start != null && end != null) {
                            val left = minOf(start.x, end.x)
                            val top = minOf(start.y, end.y)
                            val width = kotlin.math.abs(end.x - start.x)
                            val height = kotlin.math.abs(end.y - start.y)
                            drawRect(
                                color = ComposeColor(0xFFFFC107).copy(alpha = 0.25f),
                                topLeft = Offset(left, top),
                                size = Size(width, height)
                            )
                        }

                        val shapeStart = shapeDragStart
                        val shapeEnd = shapeDragCurrent
                        if (isShapeMode && shapeStart != null && shapeEnd != null) {
                            val preview = ShapeAnnotation(
                                type = selectedShapeType,
                                leftFrac = (minOf(shapeStart.x, shapeEnd.x) / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
                                topFrac = (minOf(shapeStart.y, shapeEnd.y) / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f),
                                rightFrac = (maxOf(shapeStart.x, shapeEnd.x) / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
                                bottomFrac = (maxOf(shapeStart.y, shapeEnd.y) / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f),
                                color = 0xFF1E88E5,
                                strokeWidthPx = 2.5f
                            )
                            drawShapeAnnotation(preview)
                        }

                        if (isDrawingMode && !isEraserMode && currentStrokePoints.isNotEmpty()) {
                            val previewPath = buildSmoothComposePath(currentStrokePoints)
                            if (!previewPath.isEmpty) {
                                drawPath(
                                    path = previewPath,
                                    color = ComposeColor(drawingColor),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = currentStrokePoints.lastOrNull()?.size ?: 4f,
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        if (isTextSelectionMode) {
                            val boxes = textBoxesByPage[currentPage].orEmpty()
                            val range = selectedTextRange
                            if (range != null) {
                                boxes.slice(range).forEach { box ->
                                    val left = box.leftFrac * size.width
                                    val top = box.topFrac * size.height
                                    val width = (box.rightFrac - box.leftFrac) * size.width
                                    val height = (box.bottomFrac - box.topFrac) * size.height
                                    drawRect(
                                        color = ComposeColor(0xFF64B5F6).copy(alpha = 0.32f),
                                        topLeft = Offset(left, top),
                                        size = Size(width, height)
                                    )
                                }

                                val (startHandle, endHandle) = computeTextHandleOffsets(range, boxes, viewerSize)
                                drawCircle(
                                    color = ComposeColor(0xFF1E88E5),
                                    radius = 10f,
                                    center = startHandle
                                )
                                drawCircle(
                                    color = ComposeColor(0xFF1E88E5),
                                    radius = 10f,
                                    center = endHandle
                                )
                            }
                        }
                    }

                    if (isTextSelectionMode) {
                        val boxes = textBoxesByPage[currentPage].orEmpty()
                        val selectedText = selectedTextRange?.let { range -> buildSelectedText(boxes, range) }.orEmpty()

                        Card(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (selectedTextRange == null) {
                                        "Long-press text, then drag handles"
                                    } else {
                                        "${selectedText.length} chars selected"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                                TextButton(
                                    enabled = selectedText.isNotBlank(),
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(selectedText))
                                    }
                                ) {
                                    Text("Copy")
                                }
                                TextButton(onClick = {
                                    isTextSelectionMode = false
                                    selectedTextRange = null
                                    activeTextHandle = null
                                }) {
                                    Text("Done")
                                }
                            }
                        }
                    }

                    if (showPageOrganizer) {
                        val organizerPageCount = pdfSession?.renderer?.pageCount ?: 0
                        val orderedPages = if (pageOrder.isNotEmpty()) {
                            pageOrder
                        } else {
                            (0 until organizerPageCount).toList()
                        }
                        val missingPages = (0 until organizerPageCount).filterNot { orderedPages.contains(it) }

                        Card(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(10.dp)
                                .fillMaxWidth(0.95f)
                                .fillMaxHeight(0.42f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Page Organizer", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Long-press and drag a row to reorder",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            if (missingPages.isNotEmpty()) {
                                                val mutable = orderedPages.toMutableList()
                                                val anchorIndex = mutable.indexOf(currentPage).let { idx ->
                                                    if (idx >= 0) idx else mutable.lastIndex
                                                }
                                                val insertAt = (anchorIndex + 1).coerceIn(0, mutable.size)
                                                mutable.add(insertAt, missingPages.first())
                                                pageOrder = mutable
                                            }
                                        },
                                        enabled = missingPages.isNotEmpty()
                                    ) { Text("Insert") }
                                    if (missingPages.isNotEmpty()) {
                                        Text(
                                            text = "Hidden pages: ${missingPages.size}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    items(orderedPages.indices.toList()) { idx ->
                                        val pageNum = orderedPages[idx] + 1
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    if (idx == draggingOrganizerIndex) {
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                    } else {
                                                        ComposeColor.Transparent
                                                    }
                                                )
                                                .pointerInput(orderedPages, idx) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = {
                                                            draggingOrganizerIndex = idx
                                                            organizerDragDy = 0f
                                                        },
                                                        onDragCancel = {
                                                            draggingOrganizerIndex = -1
                                                            organizerDragDy = 0f
                                                        },
                                                        onDragEnd = {
                                                            draggingOrganizerIndex = -1
                                                            organizerDragDy = 0f
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            val currentDragIndex = draggingOrganizerIndex
                                                            if (currentDragIndex !in orderedPages.indices) return@detectDragGesturesAfterLongPress

                                                            organizerDragDy += dragAmount.y
                                                            val threshold = 36f
                                                            if (organizerDragDy > threshold && currentDragIndex < orderedPages.lastIndex) {
                                                                val mutable = orderedPages.toMutableList()
                                                                val temp = mutable[currentDragIndex + 1]
                                                                mutable[currentDragIndex + 1] = mutable[currentDragIndex]
                                                                mutable[currentDragIndex] = temp
                                                                pageOrder = mutable
                                                                draggingOrganizerIndex = currentDragIndex + 1
                                                                organizerDragDy = 0f
                                                            } else if (organizerDragDy < -threshold && currentDragIndex > 0) {
                                                                val mutable = orderedPages.toMutableList()
                                                                val temp = mutable[currentDragIndex - 1]
                                                                mutable[currentDragIndex - 1] = mutable[currentDragIndex]
                                                                mutable[currentDragIndex] = temp
                                                                pageOrder = mutable
                                                                draggingOrganizerIndex = currentDragIndex - 1
                                                                organizerDragDy = 0f
                                                            }
                                                        }
                                                    )
                                                },
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("☰  Slot ${idx + 1}: Page $pageNum")
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Button(
                                                    onClick = {
                                                        if (idx > 0) {
                                                            val mutable = orderedPages.toMutableList()
                                                            val temp = mutable[idx - 1]
                                                            mutable[idx - 1] = mutable[idx]
                                                            mutable[idx] = temp
                                                            pageOrder = mutable
                                                        }
                                                    },
                                                    enabled = idx > 0
                                                ) { Text("↑") }
                                                Button(
                                                    onClick = {
                                                        if (idx < orderedPages.lastIndex) {
                                                            val mutable = orderedPages.toMutableList()
                                                            val temp = mutable[idx + 1]
                                                            mutable[idx + 1] = mutable[idx]
                                                            mutable[idx] = temp
                                                            pageOrder = mutable
                                                        }
                                                    },
                                                    enabled = idx < orderedPages.lastIndex
                                                ) { Text("↓") }
                                                Button(
                                                    onClick = {
                                                        if (orderedPages.size > 1) {
                                                            val mutable = orderedPages.toMutableList()
                                                            val removedPage = mutable.removeAt(idx)
                                                            pageOrder = mutable

                                                            if (currentPage == removedPage) {
                                                                val fallbackIndex = idx.coerceAtMost(mutable.lastIndex)
                                                                if (fallbackIndex >= 0) {
                                                                    currentPage = mutable[fallbackIndex]
                                                                }
                                                            } else if (!mutable.contains(currentPage) && mutable.isNotEmpty()) {
                                                                currentPage = mutable.first()
                                                            }
                                                        }
                                                    },
                                                    enabled = orderedPages.size > 1
                                                ) { Text("✕") }
                                            }
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            pageOrder = (0 until organizerPageCount).toList()
                                        }
                                    ) { Text("Reset") }
                                    Button(onClick = { showPageOrganizer = false }) { Text("Done") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun DrawingOverlaySection(modifier: Modifier = Modifier) {
        if (!isDrawingMode) return

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ComposeColor.Transparent)
                .pointerInteropFilter { event ->
                    if (!isDrawingMode) return@pointerInteropFilter false

                    val stylusIndex = findStylusPointerIndex(event)
                    val shouldPassToViewer = isPalmRejectionEnabled && stylusIndex == -1
                    if (shouldPassToViewer) {
                        return@pointerInteropFilter false
                    }

                    val activePointerIndex = when {
                        stylusIndex != -1 -> stylusIndex
                        else -> event.actionIndex.coerceIn(0, event.pointerCount - 1)
                    }

                    fun eraseAt(pointerIndex: Int, historyIndex: Int? = null) {
                        val rawX = if (historyIndex == null) event.getX(pointerIndex) else event.getHistoricalX(pointerIndex, historyIndex)
                        val rawY = if (historyIndex == null) event.getY(pointerIndex) else event.getHistoricalY(pointerIndex, historyIndex)
                        val docPoint = mapViewPointToDocument(
                            point = Offset(rawX, rawY),
                            zoom = zoom,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            viewport = viewerSize
                        )

                        strokesByPage = strokesByPage.toMutableMap().apply {
                            val remaining = getOrDefault(currentPage, emptyList()).filterNot { stroke ->
                                isStrokeHit(stroke, docPoint, thresholdPx = 22f)
                            }
                            put(currentPage, remaining)
                        }

                        shapesByPage = shapesByPage.toMutableMap().apply {
                            val remaining = getOrDefault(currentPage, emptyList()).filterNot { shape ->
                                isShapeHit(shape, docPoint, viewerSize, thresholdPx = 24f)
                            }
                            put(currentPage, remaining)
                        }
                    }

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_POINTER_DOWN -> {
                            if (stylusIndex != -1) {
                                isStylusActive = true
                            }
                            if (isEraserMode) {
                                eraseAt(activePointerIndex)
                                currentStrokePoints = emptyList()
                            } else {
                                currentStrokePoints = listOf(
                                    strokePointFromMotionEvent(
                                        event = event,
                                        pointerIndex = activePointerIndex,
                                        isEraser = false,
                                        zoom = zoom,
                                        offsetX = offsetX,
                                        offsetY = offsetY,
                                        viewport = viewerSize
                                    )
                                )
                            }
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (isEraserMode) {
                                val historySize = event.historySize
                                for (h in 0 until historySize) {
                                    eraseAt(activePointerIndex, h)
                                }
                                eraseAt(activePointerIndex)
                                return@pointerInteropFilter true
                            }

                            val historySize = event.historySize
                            for (h in 0 until historySize) {
                                val historical = strokePointFromMotionEvent(
                                    event = event,
                                    pointerIndex = activePointerIndex,
                                    isEraser = false,
                                    historyIndex = h,
                                    zoom = zoom,
                                    offsetX = offsetX,
                                    offsetY = offsetY,
                                    viewport = viewerSize
                                )
                                appendStrokePoint(
                                    existing = currentStrokePoints,
                                    next = historical,
                                    maxPoints = 1600
                                )?.let { currentStrokePoints = it }
                            }

                            val current = strokePointFromMotionEvent(
                                event = event,
                                pointerIndex = activePointerIndex,
                                isEraser = false,
                                zoom = zoom,
                                offsetX = offsetX,
                                offsetY = offsetY,
                                viewport = viewerSize
                            )
                            appendStrokePoint(
                                existing = currentStrokePoints,
                                next = current,
                                maxPoints = 1600
                            )?.let { currentStrokePoints = it }
                        }

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_POINTER_UP,
                        MotionEvent.ACTION_CANCEL -> {
                            val actionToolType = event.getToolType(event.actionIndex)
                            if (actionToolType == MotionEvent.TOOL_TYPE_STYLUS ||
                                actionToolType == MotionEvent.TOOL_TYPE_ERASER ||
                                event.actionMasked == MotionEvent.ACTION_CANCEL ||
                                event.actionMasked == MotionEvent.ACTION_UP
                            ) {
                                isStylusActive = false
                            }

                            if (currentStrokePoints.isNotEmpty()) {
                                val stroke = Stroke(
                                    points = currentStrokePoints,
                                    color = drawingColor,
                                    isErasing = false,
                                    pageIndex = currentPage
                                )
                                strokesByPage = strokesByPage.toMutableMap().apply {
                                    val existing = getOrDefault(currentPage, emptyList())
                                    put(currentPage, existing + stroke)
                                }
                            }
                            currentStrokePoints = emptyList()
                        }
                    }

                    true
                }
        )
    }

    @Composable
    fun ContinuousViewerSection(modifier: Modifier = Modifier) {
        val session = pdfSession
        if (session == null) {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("Open a PDF to begin")
            }
            return
        }

        val totalPages = session.renderer.pageCount
        val orderedPages = if (pageOrder.size == totalPages) pageOrder else (0 until totalPages).toList()
        val initialIndex = orderedPages.indexOf(currentPage).let { idx ->
            if (idx >= 0) idx else 0
        }.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

        LaunchedEffect(listState.firstVisibleItemIndex, totalPages, orderedPages) {
            if (totalPages > 0) {
                val visiblePos = listState.firstVisibleItemIndex.coerceIn(0, totalPages - 1)
                currentPage = orderedPages[visiblePos]
            }
        }

        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(orderedPages) { pageIndex ->
                val pageBitmapState = produceState<Bitmap?>(
                    initialValue = pageCache.get(pageIndex),
                    key1 = session,
                    key2 = pageIndex
                ) {
                    val cached = pageCache.get(pageIndex)
                    if (cached != null && !cached.isRecycled) {
                        value = cached
                        return@produceState
                    }

                    val rendered = renderPageBitmapSafely(session.renderer, pageIndex, renderMutex)
                    pageCache.put(pageIndex, rendered)
                    value = rendered
                }.value

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (pageBitmapState == null) {
                        Text("Rendering page ${pageIndex + 1}…")
                    } else {
                        Image(
                            bitmap = pageBitmapState.asImageBitmap(),
                            contentDescription = "Rendered PDF page ${pageIndex + 1}",
                            contentScale = ContentScale.Fit,
                            colorFilter = if (isNightMode) {
                                ColorFilter.colorMatrix(
                                    ColorMatrix(
                                        floatArrayOf(
                                            -1f, 0f, 0f, 0f, 255f,
                                            0f, -1f, 0f, 0f, 255f,
                                            0f, 0f, -1f, 0f, 255f,
                                            0f, 0f, 0f, 1f, 0f
                                        )
                                    )
                                )
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PDF Reader") })
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(12.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                    when {
                        event.isCtrlPressed && event.key == Key.Z -> {
                            undoLastAnnotation()
                            true
                        }

                        event.key == Key.DirectionLeft -> {
                            if (!isContinuousMode) goToPreviousPage()
                            true
                        }

                        event.key == Key.DirectionRight -> {
                            if (!isContinuousMode) goToNextPage()
                            true
                        }

                        event.key == Key.Plus || event.key == Key.Equals -> {
                            zoom = (zoom * 1.1f).coerceIn(1f, 4f)
                            true
                        }

                        event.key == Key.Minus -> {
                            zoom = (zoom / 1.1f).coerceIn(1f, 4f)
                            true
                        }

                        else -> false
                    }
                }
        ) {
            val isTabletLayout = maxWidth >= 900.dp

            if (isTabletLayout) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .width(380.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RecentLibrarySection()
                        if (isToolbarDockedLeft) {
                            ActionControlsSection(vertical = true, showDockToggle = true)
                        } else {
                            ActionControlsSection(vertical = false, showDockToggle = true)
                        }
                        Text(text = pageIndicatorText)

                        when {
                            isHighlightMode -> Text(
                                text = "Highlight mode: drag on page to mark areas",
                                color = MaterialTheme.colorScheme.primary
                            )

                            isDrawingMode -> Text(
                                text = "Draw mode: write with finger or stylus",
                                color = MaterialTheme.colorScheme.primary
                            )

                            isShapeMode -> Text(
                                text = "Shape mode: drag to place ${selectedShapeType.name.lowercase()}",
                                color = MaterialTheme.colorScheme.primary
                            )

                            isTextSelectionMode -> Text(
                                text = "Text mode: long-press then drag handles",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (errorMessage != null) {
                            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
                        }

                        if (lastRenderMs != null) {
                            Text(
                                text = if (lastRenderMs == 0L) {
                                    "Render cache: hit"
                                } else {
                                    "Render time: ${lastRenderMs} ms"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (isContinuousMode) {
                        ContinuousViewerSection(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    } else {
                        Box(modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                        ) {
                            ViewerSection(
                                modifier = Modifier.fillMaxSize()
                            )
                            DrawingOverlaySection(
                                modifier = Modifier.fillMaxSize()
                            )

                            if (!isToolbarDockedLeft) {
                                Card(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                ) {
                                    ActionControlsSection(vertical = false, showDockToggle = true)
                                }
                            }
                        }
                    }
                }
            } else {
                val isLandscape = maxWidth > maxHeight

                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .width(96.dp)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ActionControlsSection(vertical = true, showDockToggle = false)
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RecentLibrarySection()
                            Text(text = pageIndicatorText)

                            when {
                                isHighlightMode -> Text("Highlight mode", color = MaterialTheme.colorScheme.primary)
                                isDrawingMode -> Text("Draw mode", color = MaterialTheme.colorScheme.primary)
                                isShapeMode -> Text("Shape mode", color = MaterialTheme.colorScheme.primary)
                                isTextSelectionMode -> Text("Text mode", color = MaterialTheme.colorScheme.primary)
                            }

                            if (errorMessage != null) {
                                Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
                            }

                            if (isContinuousMode) {
                                ContinuousViewerSection(modifier = Modifier.weight(1f))
                            } else {
                                Box(modifier = Modifier.weight(1f)) {
                                    ViewerSection(modifier = Modifier.fillMaxSize())
                                    DrawingOverlaySection(modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RecentLibrarySection()
                        ActionControlsSection(vertical = false, showDockToggle = false)
                        Text(text = pageIndicatorText)

                        when {
                            isHighlightMode -> Text(
                                text = "Highlight mode: drag on page to mark areas",
                                color = MaterialTheme.colorScheme.primary
                            )

                            isDrawingMode -> Text(
                                text = "Draw mode: write with finger or stylus",
                                color = MaterialTheme.colorScheme.primary
                            )

                            isShapeMode -> Text(
                                text = "Shape mode: drag to place ${selectedShapeType.name.lowercase()}",
                                color = MaterialTheme.colorScheme.primary
                            )

                            isTextSelectionMode -> Text(
                                text = "Text mode: long-press then drag handles",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (errorMessage != null) {
                            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
                        }

                        if (lastRenderMs != null) {
                            Text(
                                text = if (lastRenderMs == 0L) {
                                    "Render cache: hit"
                                } else {
                                    "Render time: ${lastRenderMs} ms"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (isContinuousMode) {
                            ContinuousViewerSection(modifier = Modifier.weight(1f))
                        } else {
                            Box(modifier = Modifier.weight(1f)) {
                                ViewerSection(modifier = Modifier.fillMaxSize())
                                DrawingOverlaySection(modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }

}

private suspend fun renderPageBitmap(renderer: PdfRenderer, pageIndex: Int): Bitmap =
    withContext(Dispatchers.Default) {
        renderer.openPage(pageIndex).use { page ->
            val width = (page.width * 2).coerceAtLeast(1)
            val height = (page.height * 2).coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(AndroidColor.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

private suspend fun renderPageBitmapSafely(
    renderer: PdfRenderer,
    pageIndex: Int,
    mutex: Mutex
): Bitmap = mutex.withLock {
    renderPageBitmap(renderer, pageIndex)
}

private suspend fun extractPageText(context: android.content.Context, uri: Uri, pageIndex: Int): String =
    withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        context.contentResolver.openInputStream(uri)?.use { stream ->
            PDDocument.load(stream).use { document ->
                val stripper = PDFTextStripper().apply {
                    startPage = pageIndex + 1
                    endPage = pageIndex + 1
                    sortByPosition = true
                }
                stripper.getText(document).trim()
            }
        } ?: ""
    }

private suspend fun extractPageCharBoxes(
    context: android.content.Context,
    uri: Uri,
    pageIndex: Int
): List<TextCharBox> = withContext(Dispatchers.IO) {
    PDFBoxResourceLoader.init(context.applicationContext)
    val input = context.contentResolver.openInputStream(uri) ?: return@withContext emptyList()
    input.use { stream ->
        PDDocument.load(stream).use { document ->
            if (pageIndex !in 0 until document.numberOfPages) return@withContext emptyList()

            val page = document.getPage(pageIndex)
            val mediaWidth = page.mediaBox.width.coerceAtLeast(1f)
            val mediaHeight = page.mediaBox.height.coerceAtLeast(1f)
            val boxes = mutableListOf<TextCharBox>()

            val stripper = object : PDFTextStripper() {
                override fun processTextPosition(text: TextPosition) {
                    val unicode = text.unicode ?: return
                    if (unicode.isEmpty()) return

                    val left = text.xDirAdj
                    val top = text.yDirAdj - text.heightDir
                    val right = left + text.widthDirAdj
                    val bottom = top + text.heightDir

                    boxes += TextCharBox(
                        text = unicode,
                        leftFrac = (left / mediaWidth).coerceIn(0f, 1f),
                        topFrac = (top / mediaHeight).coerceIn(0f, 1f),
                        rightFrac = (right / mediaWidth).coerceIn(0f, 1f),
                        bottomFrac = (bottom / mediaHeight).coerceIn(0f, 1f)
                    )
                }
            }.apply {
                startPage = pageIndex + 1
                endPage = pageIndex + 1
                sortByPosition = true
            }

            stripper.getText(document)
            boxes
        }
    }
}

private suspend fun openDocumentFromUriString(
    context: android.content.Context,
    uriString: String,
    onOpened: (PdfSession, Uri) -> Unit,
    onError: (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd == null) {
                withContext(Dispatchers.Main) { onError("Unable to open selected file.") }
                return@withContext
            }
            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount == 0) {
                renderer.close()
                pfd.close()
                withContext(Dispatchers.Main) { onError("PDF has no pages.") }
                return@withContext
            }
            withContext(Dispatchers.Main) {
                onOpened(PdfSession(pfd, renderer), uri)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError(e.message ?: "Failed to open PDF")
            }
        }
    }
}

private fun resolveDisplayName(context: android.content.Context, uri: Uri): String {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && it.moveToFirst()) {
            return it.getString(nameIndex)
        }
    }
    return uri.lastPathSegment ?: uri.toString()
}

private fun loadRecentDocuments(context: android.content.Context): List<RecentDocument> {
    val prefs = context.getSharedPreferences("pdf_reader_prefs", android.content.Context.MODE_PRIVATE)
    val raw = prefs.getString("recent_documents_json", null) ?: return emptyList()
    return try {
        val jsonArray = JSONArray(raw)
        buildList {
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                add(
                    RecentDocument(
                        id = obj.optString("id"),
                        displayName = obj.optString("displayName"),
                        uri = obj.optString("uri"),
                        pageCount = obj.optInt("pageCount"),
                        lastOpenedAt = obj.optLong("lastOpenedAt"),
                        isFavorite = obj.optBoolean("isFavorite")
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveRecentDocuments(context: android.content.Context, docs: List<RecentDocument>) {
    val array = JSONArray()
    docs.take(20).forEach { doc ->
        val obj = JSONObject()
            .put("id", doc.id)
            .put("displayName", doc.displayName)
            .put("uri", doc.uri)
            .put("pageCount", doc.pageCount)
            .put("lastOpenedAt", doc.lastOpenedAt)
            .put("isFavorite", doc.isFavorite)
        array.put(obj)
    }
    val prefs = context.getSharedPreferences("pdf_reader_prefs", android.content.Context.MODE_PRIVATE)
    prefs.edit().putString("recent_documents_json", array.toString()).apply()
}

private fun loadNightModePreference(context: android.content.Context): Boolean {
    val prefs = context.getSharedPreferences("pdf_reader_prefs", android.content.Context.MODE_PRIVATE)
    return prefs.getBoolean("night_mode_enabled", false)
}

private fun saveNightModePreference(context: android.content.Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences("pdf_reader_prefs", android.content.Context.MODE_PRIVATE)
    prefs.edit().putBoolean("night_mode_enabled", enabled).apply()
}

private fun loadContinuousModePreference(context: android.content.Context): Boolean {
    val prefs = context.getSharedPreferences("pdf_reader_prefs", android.content.Context.MODE_PRIVATE)
    return prefs.getBoolean("continuous_mode_enabled", false)
}

private fun saveContinuousModePreference(context: android.content.Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences("pdf_reader_prefs", android.content.Context.MODE_PRIVATE)
    prefs.edit().putBoolean("continuous_mode_enabled", enabled).apply()
}

private fun loadToolbarDockPreference(context: android.content.Context): Boolean {
    val prefs = context.getSharedPreferences("pdf_reader_prefs", android.content.Context.MODE_PRIVATE)
    return prefs.getBoolean("toolbar_docked_left", true)
}

private fun saveToolbarDockPreference(context: android.content.Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences("pdf_reader_prefs", android.content.Context.MODE_PRIVATE)
    prefs.edit().putBoolean("toolbar_docked_left", enabled).apply()
}

private fun upsertRecentDocument(
    existing: List<RecentDocument>,
    uri: Uri,
    displayName: String,
    pageCount: Int,
    keepFavorite: Boolean = false
): List<RecentDocument> {
    val id = uri.toString()
    val previous = existing.firstOrNull { it.id == id }
    val updated = RecentDocument(
        id = id,
        displayName = displayName,
        uri = id,
        pageCount = pageCount,
        lastOpenedAt = System.currentTimeMillis(),
        isFavorite = keepFavorite || (previous?.isFavorite == true)
    )
    return listOf(updated) + existing.filterNot { it.id == id }
}

private fun clampPanOffset(
    rawOffsetX: Float,
    rawOffsetY: Float,
    zoom: Float,
    viewport: IntSize
): Pair<Float, Float> {
    if (zoom <= 1f || viewport.width <= 0 || viewport.height <= 0) {
        return 0f to 0f
    }

    val maxX = (viewport.width * (zoom - 1f)) / 2f
    val maxY = (viewport.height * (zoom - 1f)) / 2f

    return rawOffsetX.coerceIn(-maxX, maxX) to rawOffsetY.coerceIn(-maxY, maxY)
}

private fun applyPanResistance(
    rawOffsetX: Float,
    rawOffsetY: Float,
    zoom: Float,
    viewport: IntSize
): Pair<Float, Float> {
    if (zoom <= 1f || viewport.width <= 0 || viewport.height <= 0) {
        return 0f to 0f
    }

    val (hardX, hardY) = clampPanOffset(
        rawOffsetX = rawOffsetX,
        rawOffsetY = rawOffsetY,
        zoom = zoom,
        viewport = viewport
    )

    // Rubber-band overscroll region (15% of hard pan range) for natural feel.
    val maxX = (viewport.width * (zoom - 1f)) / 2f
    val maxY = (viewport.height * (zoom - 1f)) / 2f
    val overscrollX = rawOffsetX - hardX
    val overscrollY = rawOffsetY - hardY

    val resistedX = hardX + overscrollX * 0.25f
    val resistedY = hardY + overscrollY * 0.25f

    val softLimitX = maxX * 1.15f
    val softLimitY = maxY * 1.15f

    return resistedX.coerceIn(-softLimitX, softLimitX) to resistedY.coerceIn(-softLimitY, softLimitY)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShapeAnnotation(shape: ShapeAnnotation) {
    val left = shape.leftFrac.coerceIn(0f, 1f) * size.width
    val top = shape.topFrac.coerceIn(0f, 1f) * size.height
    val right = shape.rightFrac.coerceIn(0f, 1f) * size.width
    val bottom = shape.bottomFrac.coerceIn(0f, 1f) * size.height
    val width = (right - left).coerceAtLeast(1f)
    val height = (bottom - top).coerceAtLeast(1f)
    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
        width = shape.strokeWidthPx,
        cap = androidx.compose.ui.graphics.StrokeCap.Round,
        join = androidx.compose.ui.graphics.StrokeJoin.Round
    )
    val color = ComposeColor(shape.color)

    when (shape.type) {
        ShapeType.RECTANGLE -> {
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = stroke
            )
        }

        ShapeType.ELLIPSE -> {
            drawOval(
                color = color,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = stroke
            )
        }

        ShapeType.ARROW -> {
            val start = Offset(left, top)
            val end = Offset(right, bottom)
            drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = shape.strokeWidthPx
            )

            val dx = end.x - start.x
            val dy = end.y - start.y
            val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
            val ux = dx / len
            val uy = dy / len
            val headLength = 16f
            val headSpread = 8f

            val leftHead = Offset(
                x = end.x - (ux * headLength) + (-uy * headSpread),
                y = end.y - (uy * headLength) + (ux * headSpread)
            )
            val rightHead = Offset(
                x = end.x - (ux * headLength) - (-uy * headSpread),
                y = end.y - (uy * headLength) - (ux * headSpread)
            )

            drawLine(color = color, start = end, end = leftHead, strokeWidth = shape.strokeWidthPx)
            drawLine(color = color, start = end, end = rightHead, strokeWidth = shape.strokeWidthPx)
        }
    }
}

private fun calculateStrokeWidth(pressure: Float, isEraser: Boolean = false): Float {
    // Stylus pressure: 0.0-1.0; map to 2-8 dp
    // Touch pressure ~0.0; use default 4 dp
    val baseDp = if (pressure > 0.01f) {
        2f + (pressure * 6f)
    } else {
        4f
    }
    return if (isEraser) baseDp * 1.5f else baseDp
}

private fun renderStrokesToBitmap(
    width: Int,
    height: Int,
    strokes: List<Stroke>
): Bitmap {
    if (width <= 0 || height <= 0) {
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(AndroidColor.TRANSPARENT)

    if (strokes.isEmpty()) return bitmap

    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    strokes.forEach { stroke ->
        paint.color = stroke.color.toInt()
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.style = Paint.Style.STROKE

        if (stroke.isErasing) {
            // Eraser: clear pixels with PorterDuff
            paint.xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        } else {
            paint.xfermode = null
        }

        val path = buildSmoothAndroidPath(stroke.points)
        if (!path.isEmpty) {
            paint.strokeWidth = stroke.points.lastOrNull()?.size ?: 4f
            canvas.drawPath(path, paint)
        }
    }

    return bitmap
}

private fun pointDistance(a: StrokePoint, b: StrokePoint): Float {
    return hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
}

private fun appendStrokePoint(
    existing: List<StrokePoint>,
    next: StrokePoint,
    maxPoints: Int
): List<StrokePoint>? {
    val last = existing.lastOrNull()
    val threshold = if (next.pressure > 0.01f) 0.75f else 1.5f
    val shouldAppend = last == null || pointDistance(last, next) >= threshold
    if (!shouldAppend) return null
    return (existing + next).takeLast(maxPoints)
}

private fun findStylusPointerIndex(event: MotionEvent): Int {
    for (i in 0 until event.pointerCount) {
        val toolType = event.getToolType(i)
        if (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER) {
            return i
        }
    }
    return -1
}

private fun strokePointFromMotionEvent(
    event: MotionEvent,
    pointerIndex: Int,
    isEraser: Boolean,
    historyIndex: Int? = null,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
    viewport: IntSize
): StrokePoint {
    val rawX = if (historyIndex == null) {
        event.getX(pointerIndex)
    } else {
        event.getHistoricalX(pointerIndex, historyIndex)
    }
    val rawY = if (historyIndex == null) {
        event.getY(pointerIndex)
    } else {
        event.getHistoricalY(pointerIndex, historyIndex)
    }

    val mapped = mapViewPointToDocument(
        point = Offset(rawX, rawY),
        zoom = zoom,
        offsetX = offsetX,
        offsetY = offsetY,
        viewport = viewport
    )

    val pressure = if (historyIndex == null) {
        event.getPressure(pointerIndex)
    } else {
        event.getHistoricalPressure(pointerIndex, historyIndex)
    }.coerceIn(0f, 1f)

    return StrokePoint(
        x = mapped.x,
        y = mapped.y,
        pressure = pressure,
        size = calculateStrokeWidth(pressure, isEraser)
    )
}

private fun mapViewPointToDocument(
    point: Offset,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
    viewport: IntSize
): Offset {
    if (zoom <= 0f || viewport.width <= 0 || viewport.height <= 0) return point

    val cx = viewport.width / 2f
    val cy = viewport.height / 2f
    val mappedX = ((point.x - cx - offsetX) / zoom) + cx
    val mappedY = ((point.y - cy - offsetY) / zoom) + cy
    return Offset(mappedX, mappedY)
}

private fun isStrokeHit(stroke: Stroke, docPoint: Offset, thresholdPx: Float): Boolean {
    if (stroke.points.isEmpty()) return false
    val t = thresholdPx * thresholdPx
    for (i in 0 until stroke.points.size) {
        val p = stroke.points[i]
        val dx = p.x - docPoint.x
        val dy = p.y - docPoint.y
        if ((dx * dx) + (dy * dy) <= t) return true
    }
    return false
}

private fun isShapeHit(shape: ShapeAnnotation, docPoint: Offset, viewport: IntSize, thresholdPx: Float): Boolean {
    val width = viewport.width.toFloat().coerceAtLeast(1f)
    val height = viewport.height.toFloat().coerceAtLeast(1f)
    val left = shape.leftFrac * width
    val top = shape.topFrac * height
    val right = shape.rightFrac * width
    val bottom = shape.bottomFrac * height

    val expandedLeft = left - thresholdPx
    val expandedTop = top - thresholdPx
    val expandedRight = right + thresholdPx
    val expandedBottom = bottom + thresholdPx

    return docPoint.x in expandedLeft..expandedRight && docPoint.y in expandedTop..expandedBottom
}

private fun findNearestTextIndex(
    boxes: List<TextCharBox>,
    xFrac: Float,
    yFrac: Float
): Int? {
    if (boxes.isEmpty()) return null

    var bestIdx = 0
    var bestDistance = Float.MAX_VALUE
    boxes.forEachIndexed { index, box ->
        val centerX = (box.leftFrac + box.rightFrac) / 2f
        val centerY = (box.topFrac + box.bottomFrac) / 2f
        val dx = centerX - xFrac
        val dy = centerY - yFrac
        val dist = (dx * dx) + (dy * dy)
        if (dist < bestDistance) {
            bestDistance = dist
            bestIdx = index
        }
    }
    return bestIdx
}

private fun expandToWordRange(boxes: List<TextCharBox>, seedIndex: Int): IntRange {
    if (boxes.isEmpty()) return 0..0
    val safeSeed = seedIndex.coerceIn(0, boxes.lastIndex)
    var start = safeSeed
    var end = safeSeed

    while (start > 0 && !boxes[start - 1].text.isBlank()) {
        start--
    }
    while (end < boxes.lastIndex && !boxes[end + 1].text.isBlank()) {
        end++
    }
    return start..end
}

private fun computeTextHandleOffsets(
    range: IntRange,
    boxes: List<TextCharBox>,
    viewport: IntSize
): Pair<Offset, Offset> {
    val width = viewport.width.toFloat().coerceAtLeast(1f)
    val height = viewport.height.toFloat().coerceAtLeast(1f)
    val startBox = boxes[range.first.coerceIn(0, boxes.lastIndex)]
    val endBox = boxes[range.last.coerceIn(0, boxes.lastIndex)]

    val start = Offset(
        x = startBox.leftFrac * width,
        y = startBox.bottomFrac * height
    )
    val end = Offset(
        x = endBox.rightFrac * width,
        y = endBox.bottomFrac * height
    )
    return start to end
}

private fun buildSelectedText(boxes: List<TextCharBox>, range: IntRange): String {
    if (boxes.isEmpty()) return ""
    val safeStart = range.first.coerceIn(0, boxes.lastIndex)
    val safeEnd = range.last.coerceIn(0, boxes.lastIndex)
    if (safeEnd < safeStart) return ""

    val sb = StringBuilder()
    for (i in safeStart..safeEnd) {
        sb.append(boxes[i].text)
    }
    return sb.toString().trim()
}

private fun buildSmoothAndroidPath(points: List<StrokePoint>): Path {
    val path = Path()
    if (points.isEmpty()) return path

    if (points.size == 1) {
        path.moveTo(points.first().x, points.first().y)
        path.lineTo(points.first().x + 0.1f, points.first().y + 0.1f)
        return path
    }

    path.moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val current = points[i]
        val midX = (prev.x + current.x) / 2f
        val midY = (prev.y + current.y) / 2f
        path.quadTo(prev.x, prev.y, midX, midY)
    }
    val last = points.last()
    path.lineTo(last.x, last.y)
    return path
}

private fun buildSmoothComposePath(points: List<StrokePoint>): androidx.compose.ui.graphics.Path {
    val path = androidx.compose.ui.graphics.Path()
    if (points.isEmpty()) return path

    if (points.size == 1) {
        val p = points.first()
        path.moveTo(p.x, p.y)
        path.lineTo(p.x + 0.1f, p.y + 0.1f)
        return path
    }

    path.moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val current = points[i]
        val midX = (prev.x + current.x) / 2f
        val midY = (prev.y + current.y) / 2f
        path.quadraticBezierTo(prev.x, prev.y, midX, midY)
    }
    val last = points.last()
    path.lineTo(last.x, last.y)
    return path
}
