
package com.dlovel.plankton.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Dataset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val metadata: SampleMetadata = SampleMetadata(),
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
    val created_at: Long = System.currentTimeMillis()
)

@Serializable
data class Species(
    val id: String = UUID.randomUUID().toString(),
    val name_cn: String? = null,
    val name_latin: String? = null,
    val category: String = "浮游动物",
    val source: String? = null,
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
    val telemetryEnabled: Boolean = false
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
    val pendingSyncOperations: List<SyncQueueOperation> = emptyList()
)
