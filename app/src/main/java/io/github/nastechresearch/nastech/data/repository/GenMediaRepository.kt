package io.github.nastechresearch.nastech.data.repository

import androidx.paging.PagingSource
import io.github.nastechresearch.nastech.data.db.dao.GenMediaDAO
import io.github.nastechresearch.nastech.data.db.entity.GenMediaEntity

class GenMediaRepository(private val dao: GenMediaDAO) {
    fun getAllMedia(): PagingSource<Int, GenMediaEntity> = dao.getAll()

    suspend fun insertMedia(media: GenMediaEntity) = dao.insert(media)

    suspend fun deleteMedia(id: Int) = dao.delete(id)
}
