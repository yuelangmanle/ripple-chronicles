
package com.dlovel.plankton.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Dataset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val created_at: Long = System.currentTimeMillis()
)

@Serializable
data class PlanktonImage(
    val id: String = UUID.randomUUID().toString(),
    val dataset_id: String = "",
    val image_url: String = "",
    val custom_name: String? = null,
    val species_id: String? = null,
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
    val forceExtensions: Boolean = false
)

@Serializable
enum class StorageMode {
    INTERNAL,
    CUSTOM
}

@Serializable
data class AppState(
    val datasets: List<Dataset> = emptyList(),
    val images: List<PlanktonImage> = emptyList(),
    val species: List<Species> = emptyList(),
    val settings: AppSettings = AppSettings()
)
