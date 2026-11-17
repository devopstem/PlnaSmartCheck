package com.plna.smartcheck.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.plna.smartcheck.data.model.Expense
import com.plna.smartcheck.data.model.ExpenseProduct
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.UUID

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val localId: String = UUID.randomUUID().toString(),
    val firestoreId: String? = null,
    val dateMillis: Long = System.currentTimeMillis(),
    val storeName: String = "",
    val totalAmount: Double = 0.0,
    /** JSON-массив товаров */
    val productsJson: String = "[]",
    /** JSON-объект: УНП, РН СККО, № док, УИ, касса/смена и т.д. */
    val metaJson: String = "{}",
    val rawText: String = "",
    /** QR | TEXT | GALLERY */
    val source: String = "TEXT",
    val synced: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

object ReceiptMetaJsonCodec {
    fun encode(meta: Map<String, String>): String {
        val obj = JSONObject()
        meta.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    fun decode(json: String): Map<String, String> {
        if (json.isBlank() || json == "{}") return emptyMap()
        val obj = JSONObject(json)
        return buildMap {
            obj.keys().forEach { key ->
                put(key, obj.optString(key))
            }
        }
    }
}

object ProductJsonCodec {
    fun encode(products: List<ExpenseProduct>): String {
        val array = JSONArray()
        products.forEach { product ->
            array.put(
                JSONObject()
                    .put("barcode", product.barcode)
                    .put("name", product.name)
                    .put("unitPrice", product.unitPrice)
                    .put("quantity", product.quantity)
                    .put("lineTotal", product.lineTotal)
                    .put("discount", product.discount)
                    .put("healthTag", product.healthTag),
            )
        }
        return array.toString()
    }

    fun decode(json: String): List<ExpenseProduct> {
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    ExpenseProduct(
                        barcode = obj.optString("barcode"),
                        name = obj.optString("name"),
                        unitPrice = obj.optDouble("unitPrice"),
                        quantity = obj.optDouble("quantity"),
                        lineTotal = obj.optDouble("lineTotal"),
                        discount = obj.optDouble("discount"),
                        healthTag = obj.optString("healthTag", "neutral"),
                    ),
                )
            }
        }
    }
}

@Entity(tableName = "pending_cloud_deletes")
data class PendingCloudDeleteEntity(
    @PrimaryKey val firestoreId: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

fun ExpenseEntity.toDomain(): Expense = Expense(
    id = firestoreId ?: localId,
    date = Date(dateMillis),
    storeName = storeName,
    totalAmount = totalAmount,
    products = ProductJsonCodec.decode(productsJson),
)

@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ExpenseEntity)

    @Query("SELECT * FROM expenses ORDER BY dateMillis DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY dateMillis DESC")
    suspend fun getAll(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE synced = 0 ORDER BY createdAtMillis ASC")
    suspend fun getPendingSync(): List<ExpenseEntity>

    @Query("SELECT COUNT(*) FROM expenses WHERE synced = 0")
    fun observePendingCount(): Flow<Int>

    @Query(
        """
        UPDATE expenses
        SET synced = 1, firestoreId = :firestoreId
        WHERE localId = :localId
        """,
    )
    suspend fun markSynced(localId: String, firestoreId: String)

    @Query("DELETE FROM expenses WHERE localId = :localId")
    suspend fun delete(localId: String)

    @Query("SELECT * FROM expenses WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun findByFirestoreId(firestoreId: String): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE localId = :localId LIMIT 1")
    suspend fun getById(localId: String): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE localId = :localId LIMIT 1")
    fun observeById(localId: String): Flow<ExpenseEntity?>
}

@Dao
interface PendingCloudDeleteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingCloudDeleteEntity)

    @Query("SELECT * FROM pending_cloud_deletes ORDER BY createdAtMillis ASC")
    suspend fun getAll(): List<PendingCloudDeleteEntity>

    @Query("DELETE FROM pending_cloud_deletes WHERE firestoreId = :firestoreId")
    suspend fun delete(firestoreId: String)
}

@Database(
    entities = [
        ExpenseEntity::class,
        UserSessionEntity::class,
        PendingCloudDeleteEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun userSessionDao(): UserSessionDao
    abstract fun pendingCloudDeleteDao(): PendingCloudDeleteDao
}
