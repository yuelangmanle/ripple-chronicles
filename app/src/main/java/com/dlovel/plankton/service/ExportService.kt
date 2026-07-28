package com.dlovel.plankton.service

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.math.BigInteger
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.min
import com.dlovel.plankton.util.ReportStatistics

object ExportService {
    data class ExportItem(
        val source: String,
        val name: String,
        val speciesName: String? = null,
        val speciesLatin: String? = null,
        val confidence: Int? = null,
        val reviewStatus: String = "UNREVIEWED",
        val reviewNote: String? = null,
        val createdAt: Long? = null
    )

    data class ExportResult(
        val uri: Uri?,
        val displayName: String,
        val mimeType: String,
        val error: String? = null
    )

    data class BatchExportResult(
        val successCount: Int,
        val error: String? = null
    )

    suspend fun exportCsvToUri(
        context: Context,
        items: List<ExportItem>,
        targetUri: Uri
    ): ExportResult = withContext(Dispatchers.IO) {
        val mime = "text/csv"
        runCatching {
            val csv = buildString {
                appendLine("图片名,物种中文名,拉丁名,置信度,复核状态,备注,创建时间")
                items.forEach { item ->
                    appendLine(listOf(item.name, item.speciesName.orEmpty(), item.speciesLatin.orEmpty(), item.confidence?.toString().orEmpty(), item.reviewStatus, item.reviewNote.orEmpty(), item.createdAt?.toString().orEmpty()).joinToString(",") { csvEscape(it) })
                }
            }
            context.contentResolver.openOutputStream(targetUri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                ?: error("无法写入目标位置")
            ExportResult(targetUri, queryDisplayName(context, targetUri) ?: "鉴定报告.csv", mime)
        }.getOrElse { ExportResult(null, "鉴定报告.csv", mime, it.message) }
    }

    suspend fun exportExcelToUri(
        context: Context,
        items: List<ExportItem>,
        targetUri: Uri,
        statistics: ReportStatistics? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        val mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        runCatching {
            XSSFWorkbook().use { workbook ->
                val sheet = workbook.createSheet("鉴定记录")
                val headers = listOf("图片名", "物种中文名", "拉丁名", "置信度", "复核状态", "备注")
                headers.forEachIndexed { index, header -> sheet.createRow(0).createCell(index).setCellValue(header) }
                items.forEachIndexed { rowIndex, item ->
                    val row = sheet.createRow(rowIndex + 1)
                    listOf(item.name, item.speciesName.orEmpty(), item.speciesLatin.orEmpty(), item.confidence?.toString().orEmpty(), item.reviewStatus, item.reviewNote.orEmpty())
                        .forEachIndexed { index, value -> row.createCell(index).setCellValue(value) }
                }
                statistics?.let { summary ->
                    val summarySheet = workbook.createSheet("统计摘要")
                    listOf(
                        "图片总数" to summary.totalImages.toString(),
                        "已确认" to summary.confirmedImages.toString(),
                        "待复核" to summary.pendingImages.toString(),
                        "已驳回" to summary.rejectedImages.toString(),
                        "平均置信度" to summary.averageConfidence?.let { "%.1f".format(Locale.CHINA, it) }.orEmpty()
                    ).forEachIndexed { index, (label, value) ->
                        summarySheet.createRow(index).apply { createCell(0).setCellValue(label); createCell(1).setCellValue(value) }
                    }
                }
                context.contentResolver.openOutputStream(targetUri)?.use { workbook.write(it) } ?: error("无法写入目标位置")
            }
            ExportResult(targetUri, queryDisplayName(context, targetUri) ?: "鉴定报告.xlsx", mime)
        }.getOrElse { ExportResult(null, "鉴定报告.xlsx", mime, it.message) }
    }

    suspend fun exportPdfToUri(
        context: Context,
        items: List<ExportItem>,
        targetUri: Uri,
        statistics: ReportStatistics? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        val mime = "application/pdf"
        runCatching {
            val document = PdfDocument()
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }
            var pageNumber = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            var canvas = page.canvas
            var y = 40f
            fun line(text: String) {
                if (y > 800f) {
                    document.finishPage(page)
                    pageNumber += 1
                    page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
                    canvas = page.canvas
                    y = 40f
                }
                canvas.drawText(text.take(90), 32f, y, paint)
                y += 22f
            }
            line("溯澜录鉴定报告")
            statistics?.let { line("统计：共 ${it.totalImages} 张，已确认 ${it.confirmedImages} 张，待复核 ${it.pendingImages} 张") }
            items.forEach { item -> line("${item.name} | ${item.speciesName.orEmpty()} | ${item.reviewStatus} | ${item.confidence?.let { "$it%" }.orEmpty()}") }
            document.finishPage(page)
            context.contentResolver.openOutputStream(targetUri)?.use { document.writeTo(it) } ?: error("无法写入目标位置")
            document.close()
            ExportResult(targetUri, queryDisplayName(context, targetUri) ?: "鉴定报告.pdf", mime)
        }.getOrElse { ExportResult(null, "鉴定报告.pdf", mime, it.message) }
    }

    private fun csvEscape(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    suspend fun exportReport(
        context: Context,
        items: List<ExportItem>,
        fileName: String = "鉴定报告.docx",
        quality: Int = 85,
        metadataSummary: String? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val document = buildReportDocument(context, items, quality, metadataSummary)
            val output = ByteArrayOutputStream()
            document.write(output)
            document.close()
            val uri = saveToDownloads(context, fileName, DOCX_MIME, output.toByteArray())
            ExportResult(uri, fileName, DOCX_MIME)
        } catch (e: Exception) {
            Log.e("ExportService", "Export failed", e)
            ExportResult(null, fileName, DOCX_MIME, e.message)
        }
    }

    suspend fun exportReportToUri(
        context: Context,
        items: List<ExportItem>,
        targetUri: Uri,
        quality: Int = 85,
        metadataSummary: String? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val document = buildReportDocument(context, items, quality, metadataSummary)
            val stream = context.contentResolver.openOutputStream(targetUri) ?: return@withContext ExportResult(
                null,
                "鉴定报告.docx",
                DOCX_MIME,
                "无法写入目标位置"
            )
            stream.use { document.write(it) }
            document.close()
            val name = queryDisplayName(context, targetUri) ?: "鉴定报告.docx"
            ExportResult(targetUri, name, DOCX_MIME)
        } catch (e: Exception) {
            Log.e("ExportService", "Export failed", e)
            ExportResult(null, "鉴定报告.docx", DOCX_MIME, e.message)
        }
    }

    suspend fun exportImagesZip(
        context: Context,
        items: List<ExportItem>,
        fileName: String = "图片导出_${timestamp()}.zip",
        quality: Int = 85
    ): ExportResult = withContext(Dispatchers.IO) {
        val mime = "application/zip"
        try {
            val output = createDownloadOutput(context, fileName, mime)
                ?: return@withContext ExportResult(null, fileName, mime, "无法创建导出文件")

            val (uri, stream) = output
            ZipOutputStream(stream).use { zip ->
                items.forEachIndexed { index, item ->
                    val imageBytes = loadImageBytes(context, item.source, quality) ?: return@forEachIndexed
                    val safeName = item.name.ifBlank { "image_${index + 1}" }
                    val entryName = "${safeName}_${index + 1}.${imageBytes.extension}"
                    zip.putNextEntry(ZipEntry(entryName))
                    zip.write(imageBytes.bytes)
                    zip.closeEntry()
                }
            }

            ExportResult(uri, fileName, mime)
        } catch (e: Exception) {
            Log.e("ExportService", "Export images failed", e)
            ExportResult(null, fileName, mime, e.message)
        }
    }

    suspend fun exportImagesZipToUri(
        context: Context,
        items: List<ExportItem>,
        targetUri: Uri,
        quality: Int = 85
    ): ExportResult = withContext(Dispatchers.IO) {
        val mime = "application/zip"
        try {
            val stream = context.contentResolver.openOutputStream(targetUri)
                ?: return@withContext ExportResult(null, "图片导出.zip", mime, "无法写入目标位置")
            ZipOutputStream(stream).use { zip ->
                items.forEachIndexed { index, item ->
                    val imageBytes = loadImageBytes(context, item.source, quality) ?: return@forEachIndexed
                    val safeName = item.name.ifBlank { "image_${index + 1}" }
                    val entryName = "${safeName}_${index + 1}.${imageBytes.extension}"
                    zip.putNextEntry(ZipEntry(entryName))
                    zip.write(imageBytes.bytes)
                    zip.closeEntry()
                }
            }
            val name = queryDisplayName(context, targetUri) ?: "图片导出.zip"
            ExportResult(targetUri, name, mime)
        } catch (e: Exception) {
            Log.e("ExportService", "Export images failed", e)
            ExportResult(null, "图片导出.zip", mime, e.message)
        }
    }

    suspend fun exportImagesToFolderUri(
        context: Context,
        items: List<ExportItem>,
        folderUri: Uri,
        quality: Int = 85
    ): BatchExportResult = withContext(Dispatchers.IO) {
        val folder = DocumentFile.fromTreeUri(context, folderUri)
            ?: return@withContext BatchExportResult(0, "无法打开目标文件夹")
        var success = 0
        items.forEachIndexed { index, item ->
            val imageBytes = loadImageBytes(context, item.source, quality) ?: return@forEachIndexed
            val extension = imageBytes.extension.lowercase(Locale.US)
            val baseName = item.name.ifBlank { "image_${index + 1}" }.trim().ifBlank { "image_${index + 1}" }
            val targetName = ensureUniqueName(folder, "$baseName.$extension")
            val mime = mimeForExtension(extension)
            val target = folder.createFile(mime, targetName) ?: return@forEachIndexed
            context.contentResolver.openOutputStream(target.uri)?.use { output ->
                output.write(imageBytes.bytes)
                success += 1
            }
        }
        BatchExportResult(success)
    }

    private fun openImageStream(context: Context, source: String): InputStream? {
        if (source.isBlank()) return null
        val uri = Uri.parse(source)
        return when (uri.scheme) {
            "content", "file" -> context.contentResolver.openInputStream(uri)
            else -> URL(source).openStream()
        }
    }

    private fun guessExtension(context: Context, source: String): String {
        val uri = Uri.parse(source)
        val name = when (uri.scheme) {
            "content" -> queryDisplayName(context, uri)
            "file" -> uri.lastPathSegment
            else -> source
        } ?: source
        return name.substringAfterLast('.', "jpg").lowercase(Locale.US)
    }

    private fun mimeForExtension(ext: String): String {
        return when (ext.lowercase(Locale.US)) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "image/jpeg"
        }
    }

    private fun ensureUniqueName(folder: DocumentFile, name: String): String {
        if (folder.findFile(name) == null) return name
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var index = 1
        var candidate = name
        while (folder.findFile(candidate) != null) {
            candidate = if (ext.isNotEmpty()) "$base-$index.$ext" else "$base-$index"
            index += 1
        }
        return candidate
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, arrayOf("_display_name"), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return null
    }

    private fun pictureTypeForExtension(ext: String): Int {
        return when (ext.lowercase(Locale.US)) {
            "png" -> XWPFDocument.PICTURE_TYPE_PNG
            "gif" -> XWPFDocument.PICTURE_TYPE_GIF
            "bmp" -> XWPFDocument.PICTURE_TYPE_BMP
            "jpeg", "jpg" -> XWPFDocument.PICTURE_TYPE_JPEG
            else -> XWPFDocument.PICTURE_TYPE_JPEG
        }
    }

    private data class ImageBytes(val bytes: ByteArray, val extension: String)

    private fun loadImageBytes(context: Context, source: String, quality: Int): ImageBytes? {
        val stream = openImageStream(context, source) ?: return null
        val rawBytes = stream.use { it.readBytes() }
        val ext = guessExtension(context, source)
        val safeQuality = quality.coerceIn(0, 100)
        if (safeQuality >= 100 || ext == "png") {
            return ImageBytes(rawBytes, ext)
        }
        val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return ImageBytes(rawBytes, ext)
        val output = ByteArrayOutputStream()
        val format = when (ext) {
            "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
            else -> Bitmap.CompressFormat.JPEG
        }
        bitmap.compress(format, safeQuality, output)
        bitmap.recycle()
        return ImageBytes(output.toByteArray(), "jpg")
    }

    private fun loadWordImageBytes(context: Context, source: String, quality: Int): ImageBytes? {
        val stream = openImageStream(context, source) ?: return null
        val rawBytes = stream.use { it.readBytes() }
        val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return ImageBytes(rawBytes, "jpg")
        val fitted = fitBitmapToRatio(bitmap, WORD_IMAGE_RATIO)
        val output = ByteArrayOutputStream()
        val safeQuality = quality.coerceIn(0, 100)
        fitted.compress(Bitmap.CompressFormat.JPEG, safeQuality, output)
        if (fitted != bitmap) {
            fitted.recycle()
        }
        bitmap.recycle()
        return ImageBytes(output.toByteArray(), "jpg")
    }

    private fun fitBitmapToRatio(source: Bitmap, targetRatio: Float): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return source
        val srcRatio = width.toFloat() / height.toFloat()
        if (abs(srcRatio - targetRatio) < 0.01f) return source
        val targetWidth: Int
        val targetHeight: Int
        if (srcRatio > targetRatio) {
            targetWidth = width
            targetHeight = (width / targetRatio).toInt()
        } else {
            targetHeight = height
            targetWidth = (height * targetRatio).toInt()
        }
        val safeWidth = targetWidth.coerceAtLeast(1)
        val safeHeight = targetHeight.coerceAtLeast(1)
        val output = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(android.graphics.Color.WHITE)
        val scale = min(safeWidth.toFloat() / width.toFloat(), safeHeight.toFloat() / height.toFloat())
        val drawWidth = width * scale
        val drawHeight = height * scale
        val left = (safeWidth - drawWidth) / 2f
        val top = (safeHeight - drawHeight) / 2f
        val dest = RectF(left, top, left + drawWidth, top + drawHeight)
        canvas.drawBitmap(source, null, dest, Paint(Paint.ANTI_ALIAS_FLAG))
        return output
    }

    private fun buildReportDocument(
        context: Context,
        items: List<ExportItem>,
        quality: Int,
        metadataSummary: String?
    ): XWPFDocument {
        val document = XWPFDocument()
        val title = document.createParagraph()
        title.alignment = ParagraphAlignment.CENTER
        title.createRun().apply {
            fontFamily = "宋体"
            fontSize = 16
            isBold = true
            setText("溯澜录鉴定报告")
        }
        if (!metadataSummary.isNullOrBlank()) {
            val metadataParagraph = document.createParagraph()
            metadataParagraph.createRun().apply {
                fontFamily = "宋体"
                fontSize = 10
                setText(metadataSummary)
            }
        }
        val table = document.createTable(1, 2)
        configureTable(table)
        val pairs = items.chunked(2)
        pairs.forEachIndexed { index, pair ->
            val imageRow = if (index == 0) table.getRow(0) else table.createRow()
            configureImageRow(imageRow)
            pair.forEachIndexed { cellIndex, item ->
                val cell = imageRow.getCell(cellIndex) ?: imageRow.createCell()
                clearCell(cell)
                val paragraph = cell.addParagraph()
                paragraph.alignment = ParagraphAlignment.CENTER
                paragraph.spacingAfter = 0
                try {
                    val imageBytes = loadWordImageBytes(context, item.source, quality)
                    if (imageBytes != null) {
                        val pictureType = pictureTypeForExtension(imageBytes.extension)
                        val run = paragraph.createRun()
                        run.addPicture(
                            imageBytes.bytes.inputStream(),
                            pictureType,
                            "image_${System.currentTimeMillis()}.${imageBytes.extension}",
                            IMAGE_WIDTH_EMU,
                            IMAGE_HEIGHT_EMU
                        )
                    } else {
                        paragraph.createRun().setText("[图片加载失败]")
                    }
                } catch (e: Exception) {
                    paragraph.createRun().setText("[图片加载失败]")
                }
            }

            val nameRow = table.createRow()
            pair.forEachIndexed { cellIndex, item ->
                val cell = nameRow.getCell(cellIndex) ?: nameRow.createCell()
                clearCell(cell)
                val paragraph = cell.addParagraph()
                paragraph.alignment = ParagraphAlignment.CENTER
                paragraph.spacingAfter = 0
                val run = paragraph.createRun()
                run.fontFamily = "宋体"
                run.fontSize = 11
                val status = when (item.reviewStatus) {
                    "CONFIRMED" -> "已确认"
                    "REJECTED" -> "已驳回"
                    else -> "待复核"
                }
                val species = item.speciesName?.takeIf { it.isNotBlank() } ?: "未关联物种"
                val latin = item.speciesLatin?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                val confidence = item.confidence?.let { "，置信度 $it%" }.orEmpty()
                val note = item.reviewNote?.takeIf { it.isNotBlank() }?.let { "，备注：$it" }.orEmpty()
                run.setText("${item.name}\n$species$latin\n复核：$status$confidence$note")
            }
        }
        return document
    }

    private fun configureTable(table: XWPFTable) {
        table.setWidth("0")
        table.setCellMargins(0, 0, 0, 0)
        table.setInsideHBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF")
        table.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF")
        table.setTopBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF")
        table.setBottomBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF")
        table.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF")
        table.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF")
        val tblPr = table.ctTbl.tblPr ?: table.ctTbl.addNewTblPr()
        tblPr.addNewTblLayout().type = STTblLayoutType.FIXED
        if (table.ctTbl.tblGrid == null) {
            table.ctTbl.addNewTblGrid()
        }
        val grid = table.ctTbl.tblGrid
        if (grid.gridColList.isEmpty()) {
            val col1 = grid.addNewGridCol()
            col1.w = BigInteger.valueOf(TABLE_COL_WIDTH.toLong())
            val col2 = grid.addNewGridCol()
            col2.w = BigInteger.valueOf(TABLE_COL_WIDTH.toLong())
        }
    }

    private fun configureImageRow(row: org.apache.poi.xwpf.usermodel.XWPFTableRow) {
        row.height = IMAGE_ROW_HEIGHT
        row.tableCells.forEach { cell -> cell.setWidth(TABLE_COL_WIDTH.toString()) }
    }

    private fun clearCell(cell: org.apache.poi.xwpf.usermodel.XWPFTableCell) {
        val count = cell.paragraphs.size
        for (i in count - 1 downTo 0) {
            cell.removeParagraph(i)
        }
    }

    private fun saveToDownloads(
        context: Context,
        displayName: String,
        mimeType: String,
        bytes: ByteArray
    ): Uri? {
        val output = createDownloadOutput(context, displayName, mimeType) ?: return null
        val (uri, stream) = output
        stream.use { it.write(bytes) }
        return uri
    }

    private fun createDownloadOutput(
        context: Context,
        displayName: String,
        mimeType: String
    ): Pair<Uri, java.io.OutputStream>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PlanktonManager")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            val stream = resolver.openOutputStream(uri) ?: return null
            Pair(uri, stream)
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PlanktonManager")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, displayName)
            val stream = FileOutputStream(file)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            Pair(uri, stream)
        }
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
    }

    private const val EMU_PER_TWIP = 635
    private const val TABLE_COL_WIDTH = 4148
    private const val IMAGE_WIDTH_EMU = TABLE_COL_WIDTH * EMU_PER_TWIP
    private const val IMAGE_HEIGHT_EMU = IMAGE_WIDTH_EMU * 3 / 4
    private const val IMAGE_ROW_HEIGHT = IMAGE_HEIGHT_EMU / EMU_PER_TWIP
    private const val WORD_IMAGE_RATIO = 4f / 3f
    private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
}
