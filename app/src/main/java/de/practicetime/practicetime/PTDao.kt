package de.practicetime.practicetime

import androidx.room.*
import de.practicetime.practicetime.entities.*
import java.util.*

@Dao
interface PTDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PracticeSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSection(section: PracticeSection)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: Goal): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoalCategoryCrossRef(crossRef: GoalCategoryCrossRef): Long

    @Transaction
    suspend fun insertSessionAndSectionsInTransaction(
        session: PracticeSession,
        sections: List<PracticeSection>,
    ) {
        val newSessionId = insertSession(session)

        // add the new sessionId to every section...
        for (section in sections) {
            section.practice_session_id = newSessionId.toInt()
            // and insert them into the database
            insertSection(section)
        }
    }

    @Delete
    suspend fun deleteSession(session: PracticeSession)

    @Delete
    suspend fun deleteSection(section: PracticeSection)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("DELETE FROM Category WHERE id = :categoryId")
    suspend fun deleteCategoryById(categoryId: Int)

    @Update
    suspend fun updateCategory(category: Category)

    @Transaction
    suspend fun archiveCategory(categoryId: Int) {
        getCategory(categoryId).also { c ->
            c.archived = true
            updateCategory(c)
        }
    }


    @Query("SELECT * FROM PracticeSession")
    suspend fun getAllSessions(): List<PracticeSession>

    @Query("SELECT * FROM PracticeSection")
    suspend fun getAllSections(): List<PracticeSection>

    @Query("SELECT * FROM Category WHERE id=:id")
    suspend fun getCategory(id: Int): Category

    @Query("SELECT * FROM Category")
    suspend fun getAllCategories(): List<Category>

    @Query("SELECT * FROM Category WHERE NOT archived")
    suspend fun getActiveCategories(): List<Category>

    @Query("SELECT * FROM Goal")
    suspend fun getAllGoals(): List<Goal>

    @Query("SELECT * FROM Goal WHERE startTimestamp < :now AND startTimestamp + period > :now")
    suspend fun getActiveGoalsWithCategories(now : Long = Date().time / 1000L) : List<GoalWithCategories>



    @Transaction
    @Query("SELECT * FROM PracticeSession")
    suspend fun getSessionsWithSections(): List<SessionWithSections>

    @Transaction
    @Query("SELECT * FROM PracticeSession")
    suspend fun getSessionsWithSectionsWithCategories(): List<SessionWithSectionsWithCategories>
}