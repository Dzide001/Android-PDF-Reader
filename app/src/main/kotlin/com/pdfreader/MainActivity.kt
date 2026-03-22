package com.pdfreader

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    var currentPage by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedPageText by remember { mutableStateOf("") }
    var showTextDialog by remember { mutableStateOf(false) }
    var isExtractingText by remember { mutableStateOf(false) }
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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PDF Reader") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
            }

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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
