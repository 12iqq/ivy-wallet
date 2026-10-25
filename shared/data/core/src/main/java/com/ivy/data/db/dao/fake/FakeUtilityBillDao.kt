package com.ivy.data.db.dao.fake

import com.ivy.data.db.dao.write.UtilityBillDao
import com.ivy.data.db.entity.UtilityBillEntity
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
class FakeUtilityBillDao : UtilityBillDao {
    private val items = mutableMapOf<String, UtilityBillEntity>()

    override suspend fun saveAll(values: List<UtilityBillEntity>) {
        values.forEach { items[it.id] = it }
    }

    override suspend fun findAll(): List<UtilityBillEntity> = items.values.sortedByDescending { it.periodEnd }

    override suspend fun deleteAll() {
        items.clear()
    }
}
