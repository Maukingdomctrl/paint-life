package com.mauforge.paintlife.core.database

import androidx.room.migration.Migration
import java.time.ZoneId

/**
 * Every schema change after version 1 gets an explicit, tested migration here. There is
 * deliberately no destructive fallback: a missing migration must fail loudly in tests
 * rather than wipe the user's data.
 *
 * [zone] supplies the zone recorded for rows written before zone ids were stored (the
 * device zone when the migration runs).
 */
fun allMigrations(zone: () -> ZoneId): Array<Migration> = arrayOf(
    Migration1To2(zone),
    Migration2To3(zone),
    Migration3To4(),
    Migration4To5(zone),
    Migration5To6(),
    Migration6To7(),
)
