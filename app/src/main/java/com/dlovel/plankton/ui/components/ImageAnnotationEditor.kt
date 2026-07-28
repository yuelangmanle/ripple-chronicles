package com.dlovel.plankton.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dlovel.plankton.data.AnnotationType
import com.dlovel.plankton.data.ImageAnnotation
import com.dlovel.plankton.data.ImageQuality
import com.dlovel.plankton.data.PlanktonImage
import com.dlovel.plankton.data.ScaleCalibration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageAnnotationEditor(
    image: PlanktonImage,
    onDismiss: () -> Unit,
    onSave: (PlanktonImage) -> Unit
) {
    var type by remember { mutableStateOf(AnnotationType.TEXT) }
    var text by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var x by remember { mutableStateOf("0.5") }
    var y by remember { mutableStateOf("0.5") }
    var endX by remember { mutableStateOf("0.8") }
    var endY by remember { mutableStateOf("0.8") }
    var pixelLength by remember { mutableStateOf(image.scaleCalibration?.pixelLength?.toString().orEmpty()) }
    var realLength by remember { mutableStateOf(image.scaleCalibration?.realLength?.toString().orEmpty()) }
    var unit by remember { mutableStateOf(image.scaleCalibration?.unit ?: "μm") }
    var focusScore by remember { mutableStateOf(image.quality.focusScore?.toString().orEmpty()) }
    var exposure by remember { mutableStateOf(image.quality.exposure.orEmpty()) }
    var qualityPassed by remember { mutableStateOf(image.quality.passed ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("图片科研标注") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("标注类型", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        AnnotationType.TEXT to "文字",
                        AnnotationType.ARROW to "箭头",
                        AnnotationType.MEASUREMENT to "测量",
                        AnnotationType.RECTANGLE to "区域"
                    ).forEach { (candidate, label) ->
                        FilterChip(
                            selected = type == candidate,
                            onClick = { type = candidate },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("标注文字/说明") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                if (type == AnnotationType.MEASUREMENT) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text("测量值") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text("位置（相对图片 0-1）", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = x, onValueChange = { x = it }, label = { Text("起点 X") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = y, onValueChange = { y = it }, label = { Text("起点 Y") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = endX, onValueChange = { endX = it }, label = { Text("终点 X") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = endY, onValueChange = { endY = it }, label = { Text("终点 Y") }, modifier = Modifier.weight(1f))
                }
                Divider()
                Text("比例尺校准", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pixelLength,
                        onValueChange = { pixelLength = it },
                        label = { Text("像素长度") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = realLength,
                        onValueChange = { realLength = it },
                        label = { Text("实际长度") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("单位") },
                        modifier = Modifier.width(84.dp)
                    )
                }
                Divider()
                Text("图片质量检查", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = focusScore,
                    onValueChange = { focusScore = it.filter(Char::isDigit).take(3) },
                    label = { Text("清晰度评分（0-100）") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = exposure,
                    onValueChange = { exposure = it },
                    label = { Text("曝光检查备注") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row {
                    Checkbox(checked = qualityPassed, onCheckedChange = { qualityPassed = it })
                    Text("通过图片质量检查")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val annotation = ImageAnnotation(
                    type = type,
                    x = x.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.5f,
                    y = y.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.5f,
                    endX = endX.toFloatOrNull()?.coerceIn(0f, 1f),
                    endY = endY.toFloatOrNull()?.coerceIn(0f, 1f),
                    text = text.trim().takeIf { it.isNotBlank() },
                    value = value.toDoubleOrNull(),
                    unit = unit.trim().takeIf { it.isNotBlank() }
                )
                onSave(
                    image.copy(
                        annotations = image.annotations + annotation,
                        scaleCalibration = if (pixelLength.toDoubleOrNull() != null && realLength.toDoubleOrNull() != null) {
                            ScaleCalibration(pixelLength.toDouble(), realLength.toDouble(), unit)
                        } else image.scaleCalibration,
                        quality = ImageQuality(
                            focusScore = focusScore.toIntOrNull()?.coerceIn(0, 100),
                            exposure = exposure.trim().takeIf { it.isNotBlank() },
                            passed = qualityPassed
                        )
                    )
                )
                onDismiss()
            }) { Text("保存标注") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
