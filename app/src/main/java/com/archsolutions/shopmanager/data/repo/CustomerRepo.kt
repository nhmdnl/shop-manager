package com.archsolutions.shopmanager.data.repo

import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.dao.CustomerWithOwed
import com.archsolutions.shopmanager.data.db.entities.CustomerEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomerRepo @Inject constructor(
    private val db: AppDatabase,
) {
    private val customerDao = db.customerDao()

    fun getAll(): Flow<List<CustomerEntity>> = customerDao.getAll()

    fun getAllWithOwed(): Flow<List<CustomerWithOwed>> = customerDao.getAllWithOwed()

    fun getById(id: Long): Flow<CustomerEntity?> = customerDao.getById(id)

    suspend fun getByIdDirect(id: Long): CustomerEntity? = customerDao.getByIdDirect(id)

    suspend fun saveCustomer(name: String, phone: String? = null, note: String? = null): Long {
        val existing = customerDao.getByName(name)
        return existing?.id ?: customerDao.insert(CustomerEntity(name = name, phone = phone, note = note))
    }

    fun getCustomerBalanceOwed(customerId: Long): Flow<Long> =
        customerDao.getCustomerBalanceOwed(customerId)

    suspend fun getCustomerBalanceOwedDirect(customerId: Long): Long =
        customerDao.getCustomerBalanceOwedDirect(customerId)

    fun getTotalOwedToShop(): Flow<Long> =
        customerDao.getTotalOwedToShop()
}
