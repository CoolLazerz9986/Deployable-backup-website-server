package app.stash.android

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Remembers which phone files are already safe on the server, so a backup run only touches new/changed files.
 * Key = MediaStore content URI; a row only counts while size + modified time still match.
 * status: "uploaded" (we sent it), "present" (server already had identical bytes), "skipped" (empty / over the size limit).
 */
class BackupDb(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "backup.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE items (
                   uri TEXT PRIMARY KEY, size INTEGER NOT NULL, modified INTEGER NOT NULL,
                   sha256 TEXT NOT NULL, status TEXT NOT NULL, ts INTEGER NOT NULL)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun isDone(uri: String, size: Long, modified: Long): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM items WHERE uri = ? AND size = ? AND modified = ?",
            arrayOf(uri, size.toString(), modified.toString()),
        ).use { it.moveToFirst() }

    fun mark(uri: String, size: Long, modified: Long, sha256: String, status: String) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO items (uri, size, modified, sha256, status, ts) VALUES (?,?,?,?,?,?)",
            arrayOf<Any>(uri, size, modified, sha256, status, System.currentTimeMillis()),
        )
    }

    fun count(): Long =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM items", null).use { if (it.moveToFirst()) it.getLong(0) else 0L }

    fun clear() {
        writableDatabase.execSQL("DELETE FROM items")
    }
}
