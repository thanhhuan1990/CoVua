package com.huanhuynh.covua.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Dữ liệu người dùng (ván, lời thầy, Sổ tay, bài tập, lịch tập). Cài đặt vẫn ở DataStore.
 * exportSchema = true: schema JSON nằm ở app/schemas (được commit) để viết migration v2+.
 */
@Database(
    entities = [
        GameEntity::class, MoveEvalEntity::class, ReviewCardEntity::class, PuzzleAttemptEntity::class,
        PuzzleProgressEntity::class, DailyActivityEntity::class, StreakFreezeEntity::class,
        PlayerStateEntity::class, LegacyStatEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class UserDb : RoomDatabase() {
    abstract fun games(): GameDao
    abstract fun evals(): MoveEvalDao
    abstract fun cards(): ReviewCardDao
    abstract fun puzzles(): PuzzleDao
    abstract fun activity(): ActivityDao
    abstract fun player(): PlayerDao

    companion object {
        const val NAME = "user.db"
        fun build(context: Context): UserDb =
            Room.databaseBuilder(context.applicationContext, UserDb::class.java, NAME).build()
    }
}
