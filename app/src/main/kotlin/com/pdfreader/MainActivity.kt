package com.pdfreader

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

private data class HighlightRegion(
    val leftFrac: Float,
    val topFrac: Float,
    val rightFrac: Float,
    val bottomFrac: Float
)

private data class RecentDocument(
    val id: String,
    val displayName: String,
    val uri: String,
    val pageCount: Int,
    val lastOpenedAt: Long,
    val isFavorite: Boolean
)

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
    var recentDocuments by remember { mutableStateOf(loadRecentDocuments(context)) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedPageText by remember { mutableStateOf("") }
    var showTextDialog by remember { mutableStateOf(false) }
    var isExtractingText by remember { mutableStateOf(false) }
    var isNightMode by remember { mutableStateOf(loadNightModePreference(context)) }
    var isHighlightMode by remember { mutableStateOf(false) }
    var highlightsByPage by remember { mutableStateOf<Map<Int, List<HighlightRegion>>>(emptyMap()) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var viewerSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(1f, 4f)
        offsetX += panChange.x
        offsetY += panChange.y
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
            pageBitmap?.recycle()
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
            pageBitmap?.recycle()
            pdfSession?.close()
        }
    }

    LaunchedEffect(pdfSession, currentPage) {
        val session = pdfSession ?: return@LaunchedEffect
        pageBitmap?.recycle()
        pageBitmap = renderPageBitmap(session.renderer, currentPage)
        zoom = 1f
        offsetX = 0f
        offsetY = 0f
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
                                            pageBitmap?.recycle()
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
    fun ActionControlsSection() {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { openDocumentLauncher.launch(arrayOf("application/pdf")) }) {
                Text("Open PDF")
            }
            Button(
                onClick = { if (currentPage > 0) currentPage-- },
                enabled = currentPage > 0 && pdfSession != null
            ) {
                Text("Previous")
            }
            Button(
                onClick = {
                    val count = pdfSession?.renderer?.pageCount ?: 0
                    if (currentPage < count - 1) currentPage++
                },
                enabled = pdfSession != null && currentPage < ((pdfSession?.renderer?.pageCount ?: 1) - 1)
            ) {
                Text("Next")
            }
            Button(
                onClick = {
                    val uri = currentPdfUri ?: return@Button
                    isExtractingText = true
                    errorMessage = null
                    scope.launch {
                        try {
                            selectedPageText = extractPageText(context, uri, currentPage)
                            showTextDialog = true
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Failed to extract page text"
                        } finally {
                            isExtractingText = false
                        }
                    }
                },
                enabled = pdfSession != null && !isExtractingText
            ) {
                Text(if (isExtractingText) "Extracting..." else "Text")
            }
            Button(
                onClick = { isHighlightMode = !isHighlightMode },
                enabled = pdfSession != null
            ) {
                Text(if (isHighlightMode) "Highlight ON" else "Highlight")
            }
            Button(
                onClick = {
                    highlightsByPage = highlightsByPage.toMutableMap().apply {
                        remove(currentPage)
                    }
                },
                enabled = (highlightsByPage[currentPage]?.isNotEmpty() == true)
            ) {
                Text("Clear")
            }
            Button(
                onClick = {
                    isNightMode = !isNightMode
                    saveNightModePreference(context, isNightMode)
                }
            ) {
                Text(if (isNightMode) "Light" else "Night")
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
                        .onSizeChanged { viewerSize = it }
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = offsetX
                            translationY = offsetY
                        }
                        .transformable(state = transformState, enabled = !isHighlightMode)
                        .pointerInput(isHighlightMode, currentPage, viewerSize) {
                            if (!isHighlightMode) return@pointerInput
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
                        ActionControlsSection()
                        Text(
                            text = if (pdfSession == null) {
                                "No document selected"
                            } else {
                                "Page ${currentPage + 1} / ${pdfSession?.renderer?.pageCount ?: 0}"
                            }
                        )

                        if (isHighlightMode) {
                            Text(
                                text = "Highlight mode: drag on page to mark areas",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (errorMessage != null) {
                            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    ViewerSection(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RecentLibrarySection()
                    ActionControlsSection()
                    Text(
                        text = if (pdfSession == null) {
                            "No document selected"
                        } else {
                            "Page ${currentPage + 1} / ${pdfSession?.renderer?.pageCount ?: 0}"
                        }
                    )

                    if (isHighlightMode) {
                        Text(
                            text = "Highlight mode: drag on page to mark areas",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (errorMessage != null) {
                        Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
                    }

                    ViewerSection(modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Page Text") },
            text = {
                SelectionContainer {
                    Text(
                        text = selectedPageText.ifBlank {
                            "No extractable text found on this page. This may be a scanned/image-only page."
                        },
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(selectedPageText))
                    showTextDialog = false
                }) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) {
                    Text("Close")
                }
            }
        )
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
