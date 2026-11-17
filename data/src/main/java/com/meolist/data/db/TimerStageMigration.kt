package com.meolist.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `activity_subproject` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `typeId` INTEGER NOT NULL, `name` TEXT NOT NULL, `color` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `archived` INTEGER NOT NULL, FOREIGN KEY(`typeId`) REFERENCES `activity_type`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_subproject_typeId` ON `activity_subproject` (`typeId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `timer_stage` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `runningId` INTEGER, `recordId` INTEGER, `position` INTEGER NOT NULL, `subprojectId` INTEGER, `name` TEXT NOT NULL, `color` INTEGER NOT NULL, `startAt` INTEGER, `endAt` INTEGER, FOREIGN KEY(`runningId`) REFERENCES `running_timer`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`recordId`) REFERENCES `record`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_timer_stage_runningId_position` ON `timer_stage` (`runningId`, `position`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_timer_stage_recordId_position` ON `timer_stage` (`recordId`, `position`)")
    }
}
