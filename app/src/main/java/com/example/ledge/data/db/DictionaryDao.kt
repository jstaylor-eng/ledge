package com.example.ledge.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.ledge.data.model.DictionaryEntry

@Dao
interface DictionaryDao {
    @Query("SELECT * FROM dictionary WHERE simplified = :word OR traditional = :word")
    suspend fun lookup(word: String): List<DictionaryEntry>

    @Query("SELECT * FROM dictionary WHERE simplified LIKE :query || '%' LIMIT 20")
    suspend fun search(query: String): List<DictionaryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DictionaryEntry>)

    @Query("SELECT COUNT(*) FROM dictionary")
    suspend fun getCount(): Int
}
