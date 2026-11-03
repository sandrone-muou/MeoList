package com.meolist.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `farm_nutrient_v1` (`sourceKey` TEXT NOT NULL, `plotId` INTEGER, `amount` INTEGER NOT NULL, `earnedAt` INTEGER NOT NULL, PRIMARY KEY(`sourceKey`))""",
        )
        db.execSQL(
            """INSERT INTO `farm_nutrient_v1` (`sourceKey`, `plotId`, `amount`, `earnedAt`) SELECT `sourceKey`, `plotId`, `amount`, `earnedAt` FROM `farm_nutrient`""",
        )
        db.execSQL("DROP TABLE `farm_nutrient`")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `farm_nutrient` (`sourceKey` TEXT NOT NULL, `plotId` INTEGER, `amount` INTEGER NOT NULL, `earnedAt` INTEGER NOT NULL, `sequence` INTEGER NOT NULL, PRIMARY KEY(`sourceKey`))""",
        )
        db.execSQL(
            """INSERT INTO `farm_nutrient` (`sourceKey`, `plotId`, `amount`, `earnedAt`, `sequence`) SELECT `sourceKey`, `plotId`, `amount`, `earnedAt`, 0 FROM `farm_nutrient_v1`""",
        )
        db.execSQL("DROP TABLE `farm_nutrient_v1`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_farm_nutrient_plotId` ON `farm_nutrient` (`plotId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_farm_nutrient_earnedAt` ON `farm_nutrient` (`earnedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_farm_nutrient_sequence` ON `farm_nutrient` (`sequence`)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `farm_state` (`id` INTEGER NOT NULL, `rulesVersion` INTEGER NOT NULL, `nextSequence` INTEGER NOT NULL, PRIMARY KEY(`id`))""",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `farm_slot` (`slot` INTEGER NOT NULL, `sourceCutoff` INTEGER NOT NULL, PRIMARY KEY(`slot`))""",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `farm_legacy_archive` (`id` INTEGER NOT NULL, `archivedAt` INTEGER NOT NULL, `plotCount` INTEGER NOT NULL, `harvestCount` INTEGER NOT NULL, `achievementCount` INTEGER NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`id`))""",
        )
    }
}
