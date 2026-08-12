package io.github.nastechresearch.nastech.data.repository

import io.github.nastechresearch.nastech.data.db.dao.TelegramChatDao
import io.github.nastechresearch.nastech.data.db.entity.TelegramChatEntity

class TelegramChatRepository(private val dao: TelegramChatDao) {
    suspend fun getByChatId(chatId: Long): TelegramChatEntity? = dao.getByChatId(chatId)
    suspend fun getByConversationId(conversationId: String): TelegramChatEntity? =
        dao.getByConversationId(conversationId)
    suspend fun upsert(row: TelegramChatEntity) = dao.upsert(row)
    suspend fun deleteByChatId(chatId: Long) = dao.deleteByChatId(chatId)
    suspend fun touch(chatId: Long, ms: Long) = dao.touch(chatId, ms)
}
