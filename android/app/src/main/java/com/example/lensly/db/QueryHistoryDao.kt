package com.example.lensly.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QueryHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertQuery(query: QueryHistoryEntity)

    // Keep only the most recent 20 queries, and fetch them in descending order of time
    @Query("SELECT * FROM search_history ORDER BY timestampMs DESC LIMIT 20")
    fun getRecentQueries(): Flow<List<QueryHistoryEntity>>

    // Optional helper to manually clean up old entries if table gets too big,
    // though for V1 we just LIMIT 20 on the select.
    @Query("DELETE FROM search_history WHERE id NOT IN (SELECT id FROM search_history ORDER BY timestampMs DESC LIMIT 20)")
    fun deleteOldQueries()
}
