package io.github.nastechresearch.nastech.data.favorite

import io.github.nastechresearch.nastech.data.db.entity.FavoriteEntity
import io.github.nastechresearch.nastech.data.model.FavoriteType

interface FavoriteAdapter<T> {
    val type: FavoriteType

    fun buildRefKey(target: T): String

    fun buildFavoriteEntity(
        target: T,
        existing: FavoriteEntity? = null,
        now: Long = System.currentTimeMillis()
    ): FavoriteEntity
}
