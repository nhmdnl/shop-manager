package com.archsolutions.shopmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.archsolutions.shopmanager.data.db.entities.CustomerEntity
import kotlinx.coroutines.flow.Flow

data class CustomerWithOwed(
    @Embedded val customer: CustomerEntity,
    val owed: Long,
)

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customer ORDER BY name ASC")
    fun getAll(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customer WHERE id = :id LIMIT 1")
    fun getById(id: Long): Flow<CustomerEntity?>

    @Query("SELECT * FROM customer WHERE id = :id LIMIT 1")
    suspend fun getByIdDirect(id: Long): CustomerEntity?

    @Query("SELECT * FROM customer WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customer: CustomerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(customers: List<CustomerEntity>)

    @Update
    suspend fun update(customer: CustomerEntity)

    @Query("""
        SELECT (
            COALESCE((SELECT SUM(total) FROM sale WHERE customerId = :customerId AND status = 'DONE' AND method = 'CREDIT'), 0)
            -
            COALESCE((SELECT SUM(amount) FROM money_entry WHERE customerId = :customerId AND type = 'CREDIT_REPAYMENT'), 0)
        )
    """)
    fun getCustomerBalanceOwed(customerId: Long): Flow<Long>

    @Query("""
        SELECT (
            COALESCE((SELECT SUM(total) FROM sale WHERE customerId = :customerId AND status = 'DONE' AND method = 'CREDIT'), 0)
            -
            COALESCE((SELECT SUM(amount) FROM money_entry WHERE customerId = :customerId AND type = 'CREDIT_REPAYMENT'), 0)
        )
    """)
    suspend fun getCustomerBalanceOwedDirect(customerId: Long): Long

    // owed = Σ DONE credit sales − Σ credit repayments; negative means credit in the customer's favour.
    @Query("""
        SELECT c.*, (
            COALESCE((SELECT SUM(s.total) FROM sale s WHERE s.customerId = c.id AND s.status = 'DONE' AND s.method = 'CREDIT'), 0)
            -
            COALESCE((SELECT SUM(m.amount) FROM money_entry m WHERE m.customerId = c.id AND m.type = 'CREDIT_REPAYMENT'), 0)
        ) AS owed
        FROM customer c
        ORDER BY c.name COLLATE NOCASE ASC
    """)
    fun getAllWithOwed(): Flow<List<CustomerWithOwed>>

    @Query("""
        SELECT (
            COALESCE((SELECT SUM(total) FROM sale WHERE status = 'DONE' AND method = 'CREDIT'), 0)
            -
            COALESCE((SELECT SUM(amount) FROM money_entry WHERE type = 'CREDIT_REPAYMENT'), 0)
        )
    """)
    fun getTotalOwedToShop(): Flow<Long>
}
