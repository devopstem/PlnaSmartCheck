package com.plna.smartcheck.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Локальная сессия: 16-значный ID + имя (без email/телефона).
 * Сначала Room, затем синхронизация в Firestore `accounts/{accountId}`.
 */
@Entity(tableName = "user_session")
data class UserSessionEntity(
    @PrimaryKey val accountId: String,
    val displayName: String,
    val languageCode: String = "ru",
    val firebaseUid: String? = null,
    val synced: Boolean = false,
    val loggedIn: Boolean = true,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

@Dao
interface UserSessionDao {
    @Query("SELECT * FROM user_session WHERE loggedIn = 1 LIMIT 1")
    fun observeLoggedIn(): Flow<UserSessionEntity?>

    @Query("SELECT * FROM user_session WHERE loggedIn = 1 LIMIT 1")
    suspend fun getLoggedIn(): UserSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserSessionEntity)

    @Query("UPDATE user_session SET loggedIn = 0")
    suspend fun clearLoggedIn()

    @Query("DELETE FROM user_session")
    suspend fun clearAll()
}
