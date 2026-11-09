package com.mauforge.paintlife.core.database

import android.database.Cursor
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL

/** The few SQL operations a migration needs, over either of Room's two database APIs. */
internal interface MigrationSql {
    fun exec(sql: String, args: List<Any?> = emptyList())

    /** Rows of text columns. Only for small result sets. */
    fun query(sql: String): List<List<String?>>
}

internal class SupportSql(private val db: SupportSQLiteDatabase) : MigrationSql {
    override fun exec(sql: String, args: List<Any?>) {
        if (args.isEmpty()) db.execSQL(sql) else db.execSQL(sql, args.toTypedArray())
    }

    override fun query(sql: String): List<List<String?>> = db.query(sql).use { cursor -> cursor.textRows() }

    private fun Cursor.textRows(): List<List<String?>> {
        val rows = mutableListOf<List<String?>>()
        while (moveToNext()) rows += (0 until columnCount).map { if (isNull(it)) null else getString(it) }
        return rows
    }
}

internal class ConnectionSql(private val connection: SQLiteConnection) : MigrationSql {
    override fun exec(sql: String, args: List<Any?>) {
        if (args.isEmpty()) {
            connection.execSQL(sql)
            return
        }
        connection.prepare(sql).use { statement ->
            args.forEachIndexed { index, value ->
                val position = index + 1
                when (value) {
                    null -> statement.bindNull(position)
                    is Long -> statement.bindLong(position, value)
                    is Int -> statement.bindLong(position, value.toLong())
                    is String -> statement.bindText(position, value)
                    else -> error("Unsupported migration argument type: ${value::class}")
                }
            }
            statement.step()
        }
    }

    override fun query(sql: String): List<List<String?>> = connection.prepare(sql).use { statement ->
        val rows = mutableListOf<List<String?>>()
        while (statement.step()) {
            rows += (0 until statement.getColumnCount()).map { if (statement.isNull(it)) null else statement.getText(it) }
        }
        rows
    }
}
