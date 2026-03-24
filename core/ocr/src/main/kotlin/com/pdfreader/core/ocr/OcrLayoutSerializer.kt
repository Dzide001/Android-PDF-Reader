package com.pdfreader.core.ocr

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes/deserializes OcrPageLayout to/from JSON for database storage
 */
object OcrLayoutSerializer {
    
    fun toJson(layout: OcrPageLayout): String {
        val json = JSONObject()
        json.put("pageNumber", layout.pageNumber)
        json.put("documentId", layout.documentId)
        json.put("pageWidth", layout.pageWidth)
        json.put("pageHeight", layout.pageHeight)
        json.put("language", layout.language)
        json.put("ocrEngine", layout.ocrEngine)
        layout.engineVersion?.let { json.put("engineVersion", it) }
        layout.processingTime?.let { json.put("processingTime", it) }
        
        val blocksArray = JSONArray()
        layout.blocks.forEach { block ->
            blocksArray.put(blockToJson(block))
        }
        json.put("blocks", blocksArray)
        
        return json.toString()
    }
    
    fun fromJson(jsonStr: String, pageNumber: Int, documentId: String): OcrPageLayout? {
        return try {
            val json = JSONObject(jsonStr)
            
            val blocks = mutableListOf<OcrBlock>()
            val blocksArray = json.getJSONArray("blocks")
            for (i in 0 until blocksArray.length()) {
                val blockJson = blocksArray.getJSONObject(i)
                val block = jsonToBlock(blockJson)
                if (block != null) blocks.add(block)
            }
            
            OcrPageLayout(
                pageNumber = pageNumber,
                documentId = documentId,
                pageWidth = json.getDouble("pageWidth").toFloat(),
                pageHeight = json.getDouble("pageHeight").toFloat(),
                blocks = blocks,
                language = json.getString("language"),
                ocrEngine = json.optString("ocrEngine", "tesseract"),
                engineVersion = json.optString("engineVersion", null).takeIf { it.isNotEmpty() },
                processingTime = json.optLong("processingTime", -1).takeIf { it >= 0 }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun blockToJson(block: OcrBlock): JSONObject {
        val json = JSONObject()
        json.put("blockId", block.blockId)
        json.put("blockType", block.blockType)
        json.put("text", block.text)
        json.put("confidence", block.confidence)
        json.put("bbox", bboxToJson(block.bbox))
        block.lang?.let { json.put("lang", it) }
        block.langConfidence?.let { json.put("langConfidence", it) }
        
        val linesArray = JSONArray()
        block.lines.forEach { line ->
            linesArray.put(lineToJson(line))
        }
        json.put("lines", linesArray)
        
        return json
    }
    
    private fun jsonToBlock(json: JSONObject): OcrBlock? {
        return try {
            val lines = mutableListOf<OcrLine>()
            val linesArray = json.getJSONArray("lines")
            for (i in 0 until linesArray.length()) {
                val lineJson = linesArray.getJSONObject(i)
                val line = jsonToLine(lineJson)
                if (line != null) lines.add(line)
            }
            
            OcrBlock(
                blockId = json.getString("blockId"),
                blockType = json.getString("blockType"),
                text = json.getString("text"),
                confidence = json.getInt("confidence"),
                bbox = jsonToBbox(json.getJSONObject("bbox")),
                lines = lines,
                lang = json.optString("lang", null).takeIf { it.isNotEmpty() },
                langConfidence = json.optDouble("langConfidence", -1.0).takeIf { it >= 0 }?.toFloat()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun lineToJson(line: OcrLine): JSONObject {
        val json = JSONObject()
        json.put("text", line.text)
        json.put("confidence", line.confidence)
        json.put("bbox", bboxToJson(line.bbox))
        json.put("writingDirection", line.writingDirection)
        line.baseline?.let { json.put("baseline", it) }
        
        val wordsArray = JSONArray()
        line.words.forEach { word ->
            wordsArray.put(wordToJson(word))
        }
        json.put("words", wordsArray)
        
        return json
    }
    
    private fun jsonToLine(json: JSONObject): OcrLine? {
        return try {
            val words = mutableListOf<OcrWord>()
            val wordsArray = json.getJSONArray("words")
            for (i in 0 until wordsArray.length()) {
                val wordJson = wordsArray.getJSONObject(i)
                val word = jsonToWord(wordJson)
                if (word != null) words.add(word)
            }
            
            OcrLine(
                text = json.getString("text"),
                confidence = json.getInt("confidence"),
                bbox = jsonToBbox(json.getJSONObject("bbox")),
                words = words,
                writingDirection = json.optString("writingDirection", "horizontal"),
                baseline = json.optDouble("baseline", -1.0).takeIf { it >= 0 }?.toFloat()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun wordToJson(word: OcrWord): JSONObject {
        val json = JSONObject()
        json.put("text", word.text)
        json.put("confidence", word.confidence)
        json.put("bbox", bboxToJson(word.bbox))
        word.fontInfo?.let { json.put("fontInfo", it) }
        word.fontSize?.let { json.put("fontSize", it) }
        return json
    }
    
    private fun jsonToWord(json: JSONObject): OcrWord? {
        return try {
            OcrWord(
                text = json.getString("text"),
                confidence = json.getInt("confidence"),
                bbox = jsonToBbox(json.getJSONObject("bbox")),
                    fontInfo = json.optString("fontInfo").takeIf { it.isNotEmpty() },
                fontSize = json.optDouble("fontSize", -1.0).takeIf { it >= 0 }?.toFloat()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun bboxToJson(bbox: BoundingBox): JSONObject {
        val json = JSONObject()
        json.put("left", bbox.left)
        json.put("top", bbox.top)
        json.put("right", bbox.right)
        json.put("bottom", bbox.bottom)
        return json
    }
    
    private fun jsonToBbox(json: JSONObject): BoundingBox {
        return BoundingBox(
            left = json.getDouble("left").toFloat(),
            top = json.getDouble("top").toFloat(),
            right = json.getDouble("right").toFloat(),
            bottom = json.getDouble("bottom").toFloat()
        )
    }
}
