package com.mauforge.paintlife.core.database

import androidx.room.migration.Migration

/**
 * Every schema change after version 1 gets an explicit, tested migration here. There is
 * deliberately no destructive fallback: a missing migration must fail loudly in tests
 * rather than wipe the user's data.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf()
