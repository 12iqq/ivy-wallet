package com.ivy.data.db.dao.write

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ivy.data.db.entity.UtilityBillEntity

@Dao
interface UtilityBillDao {
    @Upsert
    suspend fun saveAll(values: List<UtilityBillEntity>)

    @Query("SELECT * FROM utility_bills ORDER BY periodEnd DESC")
    suspend fun findAll(): List<UtilityBillEntity>

    @Query("DELETE FROM utility_bills")
    suspend fun deleteAll()
}
