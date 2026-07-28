
package com.dlovel.plankton.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val recordedAt: Long = System.currentTimeMillis()
)

@Serializable
data class SamplingEvent(
    val id: String = UUID.randomUUID().toString(),
    val site: String? = null,
    val startedAt: String? = null,
    val endedAt: String? = null,
    val track: List<GeoPoint> = emptyList(),
    val weather: String? = null,
    val tide: String? = null,
    val waterTemperatureCelsius: Double? = null,
    val ph: Double? = null,
    val salinityPsu: Double? = null,
    val qrCode: String? = null,
    val chainOfCustody: List<ChainOfCustodyEntry> = emptyList()
)

@Serializable
data class ChainOfCustodyEntry(
    val id: String = UUID.randomUUID().toString(),
    val operator: String,
    val action: String,
    val happenedAt: Long = System.currentTimeMillis(),
    val note: String? = null
)

@Serializable
data class Dataset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val metadata: SampleMetadata = SampleMetadata(),
    val samplingEvents: List<SamplingEvent> = emptyList(),
    val version: Int = 1,
    val updated_at: Long = System.currentTimeMillis(),
    val created_at: Long = System.currentTimeMillis()
)

@Serializable
data class SampleMetadata(
    val samplingSite: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val sampledAt: String? = null,
    val waterDepthMeters: Double? = null,
    val waterTemperatureCelsius: Double? = null,
    val ph: Double? = null,
    val salinityPsu: Double? = null,
    val sampleCode: String? = null
)

@Serializable
data class PlanktonImage(
    val id: String = UUID.randomUUID().toString(),
    val dataset_id: String = "",
    val image_url: String = "",
    val custom_name: String? = null,
    val species_id: String? = null,
    val isFavorite: Boolean = false,
    val identificationConfidence: Int? = null,
    val reviewStatus: String = "UNREVIEWED",
    val reviewNote: String? = null,
    val reviewedAt: Long? = null,
    val candidateSpeciesIds: List<String> = emptyList(),
    val annotations: List<ImageAnnotation> = emptyList(),
    val scaleCalibration: ScaleCalibration? = null,
    val quality: ImageQuality = ImageQuality(),
    val identificationHistory: List<IdentificationHistory> = emptyList(),
    val created_at: Long = System.currentTimeMillis()
)

@Serializable
enum class AnnotationType {
    POINT,
    ARROW,
    RECTANGLE,
    MEASUREMENT,
    TEXT
}

@Serializable
data class ImageAnnotation(
    val id: String = UUID.randomUUID().toString(),
    val type: AnnotationType = AnnotationType.POINT,
    val x: Float = 0f,
    val y: Float = 0f,
    val endX: Float? = null,
    val endY: Float? = null,
    val text: String? = null,
    val value: Double? = null,
    val unit: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class ScaleCalibration(
    val pixelLength: Double,
    val realLength: Double,
    val unit: String = "μm"
)

@Serializable
data class ImageQuality(
    val focusScore: Int? = null,
    val exposure: String? = null,
    val whiteBalance: String? = null,
    val passed: Boolean? = null,
    val note: String? = null
)

@Serializable
data class IdentificationHistory(
    val id: String = UUID.randomUUID().toString(),
    val speciesId: String? = null,
    val confidence: Int? = null,
    val status: String = "UNREVIEWED",
    val operator: String? = null,
    val note: String? = null,
    val happenedAt: Long = System.currentTimeMillis()
)

@Serializable
data class Species(
    val id: String = UUID.randomUUID().toString(),
    val name_cn: String? = null,
    val name_latin: String? = null,
    val category: String = "浮游动物",
    val source: String? = null,
    val synonyms: List<String> = emptyList(),
    val taxonomyVersion: String? = null,
    val reference: String? = null,
    val distribution: String? = null,
    val isUserDefined: Boolean = false,
    val created_at: Long = System.currentTimeMillis()
)

@Serializable
data class AppSettings(
    val storageMode: StorageMode = StorageMode.INTERNAL,
    val customRootUri: String? = null,
    val saveToAlbum: Boolean = false,
    val exportQuality: Int = 85,
    val homeUserName: String = "邓",
    val enableExtensions: Boolean = true,
    val extensionMode: String = "AUTO",
    val forceExtensions: Boolean = false,
    val themeMode: String = "SYSTEM",
    val animationScale: Float = 1f,
    val telemetryEnabled: Boolean = false,
    val reviewConfidenceThreshold: Int = 70,
    val autoBackupEnabled: Boolean = false,
    val autoBackupIntervalHours: Int = 24,
    val reportTemplateId: String = "default"
)

@Serializable
data class LocalUsageMetrics(
    val captures: Int = 0,
    val imports: Int = 0,
    val exports: Int = 0,
    val updatedAt: Long? = null
)

@Serializable
data class SyncQueueOperation(
    val id: String = UUID.randomUUID().toString(),
    val entityType: String,
    val entityId: String,
    val action: String,
    val payload: String,
    val queuedAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val conflictState: String = "PENDING"
)

@Serializable
data class OperationRecord(
    val id: String = UUID.randomUUID().toString(),
    val operation: String,
    val entityType: String? = null,
    val entityId: String? = null,
    val happenedAt: Long = System.currentTimeMillis(),
    val summary: String? = null
)

@Serializable
data class ReportTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val includeImages: Boolean = true,
    val includeMetadata: Boolean = true,
    val includeReviewHistory: Boolean = true,
    val includeStatistics: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

enum class UsageEvent {
    CAPTURE,
    IMPORT,
    EXPORT
}

@Serializable
enum class StorageMode {
    INTERNAL,
    CUSTOM
}

@Serializable
data class AppState(
    val schemaVersion: Int = 2,
    val datasets: List<Dataset> = emptyList(),
    val images: List<PlanktonImage> = emptyList(),
    val species: List<Species> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val usageMetrics: LocalUsageMetrics = LocalUsageMetrics(),
    val pendingSyncOperations: List<SyncQueueOperation> = emptyList(),
    val operationHistory: List<OperationRecord> = emptyList(),
    val reportTemplates: List<ReportTemplate> = listOf(
        ReportTemplate(id = "default", name = "标准鉴定报告")
    )
)
