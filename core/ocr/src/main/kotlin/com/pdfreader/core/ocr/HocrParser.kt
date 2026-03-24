package com.pdfreader.core.ocr

import android.graphics.RectF
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses Tesseract's hOCR (HTML-based OCR) output to structured layout hierarchy
 * hOCR format: https://github.com/kba/hocrjs/wiki/hOCR-1.2-spec
 */
class HocrParser {
    
    fun parse(hocrHtml: String, pageWidth: Float, pageHeight: Float, 
              pageNumber: Int, documentId: String, language: String): OcrPageLayout? {
        return try {
            val doc = parseHtmlToXml(hocrHtml)
            extractLayout(doc, pageWidth, pageHeight, pageNumber, documentId, language)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun parseHtmlToXml(hocrHtml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val builder = factory.newDocumentBuilder()
        
        // Parse HTML - Tesseract's hOCR output is valid XML
        return builder.parse(hocrHtml.byteInputStream())
    }
    
    private fun extractLayout(doc: Document, pageWidth: Float, pageHeight: Float,
                             pageNumber: Int, documentId: String, language: String): OcrPageLayout {
        val blocks = mutableListOf<OcrBlock>()
        
        // Find carea (column area) or page_carea elements
        val carea = doc.getElementsByTagName("div").let { divs ->
            var result: Element? = null
            for (i in 0 until divs.length) {
                val div = divs.item(i) as Element
                val class_ = div.getAttribute("class")
                if (class_.contains("carea") || class_.contains("page_carea")) {
                    result = div
                    break
                }
            }
            result
        }
        
        if (carea != null) {
            // Parse paragraph blocks (par_P_*)
            val pars = carea.getElementsByTagName("p")
            for (i in 0 until pars.length) {
                val par = pars.item(i) as Element
                val blockId = par.getAttribute("id")
                if (blockId.isNotEmpty()) {
                    val block = parseBlock(par, blockId, pageWidth, pageHeight, language)
                    if (block != null) {
                        blocks.add(block)
                    }
                }
            }
        }
        
        // If no blocks found, try parsing lines directly
        if (blocks.isEmpty()) {
            val lines = doc.getElementsByTagName("span").let { spans ->
                mutableListOf<OcrLine>()
            }
            // Fallback: parse any available text
            if (lines.isEmpty()) {
                blocks.add(createFallbackBlock(doc.documentElement?.textContent ?: "", 
                                            pageWidth, pageHeight, language))
            }
        }
        
        return OcrPageLayout(
            pageNumber = pageNumber,
            documentId = documentId,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            blocks = blocks,
            language = language,
            engineVersion = extractEngineVersion(doc)
        )
    }
    
    private fun parseBlock(parElement: Element, blockId: String, pageWidth: Float, 
                          pageHeight: Float, language: String): OcrBlock? {
        val lines = mutableListOf<OcrLine>()
        
        // Parse line elements (line_P_*)
        val spans = parElement.getElementsByTagName("span")
        var currentLineText = StringBuilder()
        var currentLineConfidence = 95
        var currentLineWords = mutableListOf<OcrWord>()
        var currentLineBbox: BoundingBox? = null
        
        for (i in 0 until spans.length) {
            val span = spans.item(i) as Element
            val class_ = span.getAttribute("class")
            
            when {
                class_.contains("ocr_line") || class_.contains("line") -> {
                    // Save previous line if any
                    if (currentLineText.isNotEmpty()) {
                        val lineConfidence = if (currentLineWords.isNotEmpty()) {
                            currentLineWords.map { it.confidence }.average().toInt()
                        } else {
                            95
                        }
                        lines.add(OcrLine(
                            text = currentLineText.toString(),
                            confidence = lineConfidence,
                            bbox = currentLineBbox ?: BoundingBox(0f, 0f, 1f, 1f),
                            words = currentLineWords.toList()
                        ))
                    }
                    // Start new line
                    currentLineText = StringBuilder()
                    currentLineWords = mutableListOf()
                    currentLineBbox = extractBbox(span, pageWidth, pageHeight)
                }
                class_.contains("ocrx_word") || class_.contains("word") -> {
                    val word = parseWord(span, pageWidth, pageHeight)
                    if (word != null) {
                        if (currentLineText.isNotEmpty()) {
                            currentLineText.append(" ")
                        }
                        currentLineText.append(word.text)
                        currentLineWords.add(word)
                        if (currentLineBbox == null) {
                            currentLineBbox = word.bbox
                        } else {
                            currentLineBbox = mergeBboxes(currentLineBbox, word.bbox)
                        }
                    }
                }
            }
        }
        
        // Add final line
        if (currentLineText.isNotEmpty()) {
            val lineConfidence = if (currentLineWords.isNotEmpty()) {
                currentLineWords.map { it.confidence }.average().toInt()
            } else {
                95
            }
            lines.add(OcrLine(
                text = currentLineText.toString(),
                confidence = lineConfidence,
                bbox = currentLineBbox ?: BoundingBox(0f, 0f, 1f, 1f),
                words = currentLineWords.toList()
            ))
        }
        
        if (lines.isEmpty()) return null
        
        val blockText = lines.joinToString("\n") { it.text }
        val blockConfidence = lines.map { it.confidence }.average().toInt()
        val blockBbox = lines.map { it.bbox }.let { bboxes ->
            mergeBboxes(bboxes[0], *bboxes.drop(1).toTypedArray())
        }
        
        return OcrBlock(
            blockId = blockId,
            blockType = "text",
            text = blockText,
            confidence = blockConfidence,
            bbox = blockBbox,
            lines = lines,
            lang = language
        )
    }
    
    private fun parseWord(spanElement: Element, pageWidth: Float, pageHeight: Float): OcrWord? {
        val text = spanElement.textContent.trim()
        if (text.isEmpty()) return null
        
        val bbox = extractBbox(spanElement, pageWidth, pageHeight)
        val confidence = extractConfidence(spanElement)
        
        return OcrWord(
            text = text,
            confidence = confidence,
            bbox = bbox
        )
    }
    
    private fun extractBbox(element: Element, pageWidth: Float, pageHeight: Float): BoundingBox {
        val title = element.getAttribute("title")
        val bboxMatch = Regex("""bbox\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)""").find(title)
        
        return if (bboxMatch != null) {
            val (left, top, right, bottom) = bboxMatch.destructured
            BoundingBox.fromPixels(
                left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(),
                pageWidth, pageHeight
            )
        } else {
            BoundingBox(0f, 0f, 1f, 1f)
        }
    }
    
    private fun extractConfidence(element: Element): Int {
        val title = element.getAttribute("title")
        val confMatch = Regex("""x_confidence\s+([\d.]+)""").find(title)
        return if (confMatch != null) {
            (confMatch.groupValues[1].toFloat() * 100).toInt().coerceIn(0, 100)
        } else {
            // Default confidence if not specified
            95
        }
    }
    
    private fun extractEngineVersion(doc: Document): String? {
        val ocr = doc.getElementsByTagName("html").item(0)
        if (ocr is Element) {
            val version = ocr.getAttribute("ocr-version")
            if (version.isNotEmpty()) return version
        }
        return null
    }
    
    private fun mergeBboxes(vararg bboxes: BoundingBox): BoundingBox {
        if (bboxes.isEmpty()) return BoundingBox(0f, 0f, 1f, 1f)
        
        var minLeft = Float.MAX_VALUE
        var minTop = Float.MAX_VALUE
        var maxRight = Float.MIN_VALUE
        var maxBottom = Float.MIN_VALUE
        
        for (bbox in bboxes) {
            minLeft = minOf(minLeft, bbox.left)
            minTop = minOf(minTop, bbox.top)
            maxRight = maxOf(maxRight, bbox.right)
            maxBottom = maxOf(maxBottom, bbox.bottom)
        }
        
        return BoundingBox(minLeft, minTop, maxRight, maxBottom)
    }
    
    private fun createFallbackBlock(text: String, pageWidth: Float, pageHeight: Float,
                                  language: String): OcrBlock {
        if (text.isEmpty()) {
            return OcrBlock(
                blockId = "block_0_0",
                blockType = "text",
                text = "",
                confidence = 0,
                bbox = BoundingBox(0f, 0f, 1f, 1f),
                lines = emptyList(),
                lang = language
            )
        }
        
        // Split text into lines
        val lines = text.split("\n").mapIndexed { lineIdx, lineText ->
            if (lineText.isNotEmpty()) {
                val words = lineText.split(" ").map { word ->
                    OcrWord(word, 90, BoundingBox(0f, 0f, 1f, 1f))
                }
                OcrLine(lineText, 90, BoundingBox(0f, 0f, 1f, 1f), words)
            } else {
                null
            }
        }.filterNotNull()
        
        return OcrBlock(
            blockId = "block_0_0",
            blockType = "text",
            text = text,
            confidence = 90,
            bbox = BoundingBox(0f, 0f, 1f, 1f),
            lines = lines,
            lang = language
        )
    }
}
