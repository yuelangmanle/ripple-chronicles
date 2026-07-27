package com.dlovel.plankton.data

import android.content.Context
import androidx.annotation.RawRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID

object LocalAppStore {
    private const val DATA_FILE = "app_state.json"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    private val mutex = Mutex()
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    suspend fun load(context: Context) {
        val file = File(context.filesDir, DATA_FILE)
        val loaded = withContext(Dispatchers.IO) {
            if (file.exists()) {
                runCatching { json.decodeFromString(AppState.serializer(), file.readText()) }.getOrNull()
            } else {
                null
            }
        }
        val base = loaded ?: AppState()
        val builtIn = loadBuiltInSpecies(context, com.dlovel.plankton.R.raw.species_plankton)
        val builtInIds = builtIn.map { it.id }.toSet()
        val builtInKeys = builtIn.map { speciesKey(it) }.toSet()
        val baseById = base.species.associateBy { it.id }
        val baseByKey = base.species.associateBy { speciesKey(it) }
        val mergedBuiltIn = builtIn.map { species ->
            baseById[species.id] ?: baseByKey[speciesKey(species)] ?: species
        }
        val extraSpecies = base.species.filterNot { species ->
            builtInIds.contains(species.id) || builtInKeys.contains(speciesKey(species))
        }
        val normalizedSpecies = (mergedBuiltIn + extraSpecies)
            .filterNot { species -> species.name_cn == "9）安徽似铃壳虫" }
            .map { species ->
                if (species.name_cn == "无节幼体" && species.name_latin.isNullOrBlank()) {
                    species.copy(name_latin = "Nauplii")
                } else {
                    species
                }
            }
        val withSpecies = base.copy(species = normalizedSpecies)
        _state.value = withSpecies
        if (!file.exists() || withSpecies != base) {
            save(context, withSpecies)
        }
    }

    suspend fun update(context: Context, block: (AppState) -> AppState) {
        mutex.withLock {
            val current = _state.value
            val next = block(current)
            _state.value = next
            save(context, next)
        }
    }

    suspend fun addDataset(context: Context, name: String, description: String): Dataset {
        val dataset = Dataset(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            created_at = System.currentTimeMillis()
        )
        update(context) { it.copy(datasets = it.datasets + dataset) }
        return dataset
    }

    suspend fun addImages(context: Context, newImages: List<PlanktonImage>) {
        update(context) { it.copy(images = it.images + newImages) }
    }

    suspend fun updateImage(context: Context, imageId: String, updater: (PlanktonImage) -> PlanktonImage) {
        update(context) {
            val updated = it.images.map { img ->
                if (img.id == imageId) updater(img) else img
            }
            it.copy(images = updated)
        }
    }

    suspend fun deleteImage(context: Context, imageId: String) {
        update(context) { it.copy(images = it.images.filterNot { img -> img.id == imageId }) }
    }

    suspend fun deleteDataset(context: Context, datasetId: String) {
        update(context) {
            val remainingDatasets = it.datasets.filterNot { ds -> ds.id == datasetId }
            val remainingImages = it.images.filterNot { img -> img.dataset_id == datasetId }
            it.copy(datasets = remainingDatasets, images = remainingImages)
        }
    }

    suspend fun addSpecies(context: Context, newSpecies: List<Species>) {
        update(context) {
            val existing = it.species.associateBy { species -> speciesKey(species) }
            val merged = it.species + newSpecies.filterNot { species ->
                existing.containsKey(speciesKey(species))
            }
            it.copy(species = merged)
        }
    }

    suspend fun updateSpecies(context: Context, speciesId: String, updater: (Species) -> Species) {
        update(context) {
            val updated = it.species.map { sp ->
                if (sp.id == speciesId) updater(sp) else sp
            }
            it.copy(species = updated)
        }
    }

    suspend fun deleteSpecies(context: Context, speciesId: String) {
        update(context) {
            it.copy(species = it.species.filterNot { sp -> sp.id == speciesId })
        }
    }

    suspend fun updateSettings(context: Context, settings: AppSettings) {
        update(context) { it.copy(settings = settings) }
    }

    private suspend fun save(context: Context, state: AppState) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, DATA_FILE)
        file.writeText(json.encodeToString(AppState.serializer(), state))
    }

    private suspend fun loadBuiltInSpecies(context: Context, @RawRes resId: Int): List<Species> {
        return withContext(Dispatchers.IO) {
            val text = context.resources.openRawResource(resId)
                .bufferedReader()
                .use { it.readText() }
            runCatching {
                json.decodeFromString(ListSerializer(Species.serializer()), text)
            }.getOrElse {
                text.lines()
                    .map { line -> line.trim() }
                    .filter { line -> line.isNotEmpty() }
                    .map { name ->
                        Species(
                            id = UUID.randomUUID().toString(),
                            name_cn = name,
                            category = "浮游动物"
                        )
                    }
            }
        }
    }

    private fun speciesKey(species: Species): String {
        return "${species.name_cn ?: ""}:${species.name_latin ?: ""}:${species.category}"
    }
}
