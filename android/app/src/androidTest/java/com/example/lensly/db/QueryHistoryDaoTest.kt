package com.example.lensly.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueryHistoryDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: QueryHistoryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.queryHistoryDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testInsertAndRetrieveQuery() = runBlocking {
        val query = QueryHistoryEntity(query = "best value milk", timestampMs = 1000L)
        dao.insertQuery(query)

        val recentQueries = dao.getRecentQueries().first()
        assertEquals(1, recentQueries.size)
        assertEquals("best value milk", recentQueries[0].query)
    }

    @Test
    fun testLimit20Queries() = runBlocking {
        // Insert 25 queries
        for (i in 1..25) {
            dao.insertQuery(QueryHistoryEntity(query = "query $i", timestampMs = i.toLong()))
            dao.deleteOldQueries()
        }

        // Fetch recent queries
        val recentQueries = dao.getRecentQueries().first()
        
        // Ensure only 20 are returned and they are the most recent (ids 6 to 25)
        assertEquals(20, recentQueries.size)
        
        // The first item should be the one with the highest timestamp (25)
        assertEquals("query 25", recentQueries[0].query)
        assertEquals("query 6", recentQueries[19].query)
    }
}
