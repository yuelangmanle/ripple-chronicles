package com.dlovel.plankton.service

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
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

object ExportService {
    data class ExportItem(
        val source: String,
        val name: String
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

    suspend fun exportReport(
        context: Context,
        items: List<ExportItem>,
        fileName: String = "鉴定报告.docx",
        quality: Int = 85
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val document = buildReportDocument(context, items, quality)
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
        quality: Int = 85
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val document = buildReportDocument(context, items, quality)
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
        quality: Int
    ): XWPFDocument {
        val document = XWPFDocument()
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
                run.setText(item.name)
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
