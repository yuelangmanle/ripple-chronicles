package com.dlovel.plankton.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 可回滚的元数据镜像层。JSON 仍是 LocalAppStore 的事实来源，图片量达到阈值后
 * 额外写入 SQLite，为后续分页查询和迁移到 Room 保留稳定边界。
 */
object SqliteMetadataStore {
    const val IMAGE_MIRROR_THRESHOLD = 1_000

    fun shouldMirror(imageCount: Int): Boolean = imageCount >= IMAGE_MIRROR_THRESHOLD

    fun mirrorIfNeeded(context: Context, state: AppState) {
        if (!shouldMirror(state.images.size)) return
        MetadataDb(context.applicationContext).use { helper ->
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                db.delete("datasets", null, null)
                db.delete("images", null, null)
                db.delete("species", null, null)
                state.datasets.forEach { dataset ->
                    db.insertOrThrow("datasets", null, ContentValues().apply {
                        put("id", dataset.id)
                        put("name", dataset.name)
                        put("version", dataset.version)
                        put("updated_at", dataset.updated_at)
                    })
                }
                state.images.forEach { image ->
                    db.insertOrThrow("images", null, ContentValues().apply {
                        put("id", image.id)
                        put("dataset_id", image.dataset_id)
                        put("custom_name", image.custom_name)
                        put("species_id", image.species_id)
                    })
                }
                state.species.forEach { species ->
                    db.insertOrThrow("species", null, ContentValues().apply {
                        put("id", species.id)
                        put("name_cn", species.name_cn)
                        put("name_latin", species.name_latin)
                    })
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun readCounts(context: Context): MetadataCounts {
        MetadataDb(context.applicationContext).use { helper ->
            val db = helper.readableDatabase
            return MetadataCounts(
                datasets = count(db, "datasets"),
                images = count(db, "images"),
                species = count(db, "species")
            )
        }
    }

    private fun count(db: SQLiteDatabase, table: String): Int =
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    data class MetadataCounts(val datasets: Int, val images: Int, val species: Int)

    private class MetadataDb(context: Context) : SQLiteOpenHelper(
        context,
        "ripple_metadata.db",
        null,
        1
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE datasets (id TEXT PRIMARY KEY, name TEXT NOT NULL, version INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE images (id TEXT PRIMARY KEY, dataset_id TEXT NOT NULL, custom_name TEXT, species_id TEXT)")
            db.execSQL("CREATE TABLE species (id TEXT PRIMARY KEY, name_cn TEXT, name_latin TEXT)")
            db.execSQL("CREATE INDEX images_dataset_index ON images(dataset_id)")
            db.execSQL("CREATE INDEX images_species_index ON images(species_id)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
