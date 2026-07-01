package com.decovision.app.`data`.db

import android.database.Cursor
import androidx.room.CoroutinesRoom
import androidx.room.EntityDeletionOrUpdateAdapter
import androidx.room.EntityInsertionAdapter
import androidx.room.RoomDatabase
import androidx.room.RoomSQLiteQuery
import androidx.room.RoomSQLiteQuery.Companion.acquire
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.query
import androidx.sqlite.db.SupportSQLiteStatement
import com.decovision.app.`data`.model.Design
import java.lang.Class
import java.util.ArrayList
import java.util.concurrent.Callable
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.jvm.JvmStatic
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION"])
public class DesignDao_Impl(
  __db: RoomDatabase,
) : DesignDao {
  private val __db: RoomDatabase

  private val __insertionAdapterOfDesign: EntityInsertionAdapter<Design>

  private val __deletionAdapterOfDesign: EntityDeletionOrUpdateAdapter<Design>
  init {
    this.__db = __db
    this.__insertionAdapterOfDesign = object : EntityInsertionAdapter<Design>(__db) {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `designs` (`id`,`name`,`thumbnailPath`,`furnitureJson`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?)"

      protected override fun bind(statement: SupportSQLiteStatement, entity: Design) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindString(2, entity.name)
        statement.bindString(3, entity.thumbnailPath)
        statement.bindString(4, entity.furnitureJson)
        statement.bindLong(5, entity.createdAt)
      }
    }
    this.__deletionAdapterOfDesign = object : EntityDeletionOrUpdateAdapter<Design>(__db) {
      protected override fun createQuery(): String = "DELETE FROM `designs` WHERE `id` = ?"

      protected override fun bind(statement: SupportSQLiteStatement, entity: Design) {
        statement.bindLong(1, entity.id.toLong())
      }
    }
  }

  public override suspend fun insertDesign(design: Design): Unit = CoroutinesRoom.execute(__db,
      true, object : Callable<Unit> {
    public override fun call() {
      __db.beginTransaction()
      try {
        __insertionAdapterOfDesign.insert(design)
        __db.setTransactionSuccessful()
      } finally {
        __db.endTransaction()
      }
    }
  })

  public override suspend fun deleteDesign(design: Design): Unit = CoroutinesRoom.execute(__db,
      true, object : Callable<Unit> {
    public override fun call() {
      __db.beginTransaction()
      try {
        __deletionAdapterOfDesign.handle(design)
        __db.setTransactionSuccessful()
      } finally {
        __db.endTransaction()
      }
    }
  })

  public override fun getAllDesigns(): Flow<List<Design>> {
    val _sql: String = "SELECT * FROM designs ORDER BY createdAt DESC"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("designs"), object :
        Callable<List<Design>> {
      public override fun call(): List<Design> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfName: Int = getColumnIndexOrThrow(_cursor, "name")
          val _cursorIndexOfThumbnailPath: Int = getColumnIndexOrThrow(_cursor, "thumbnailPath")
          val _cursorIndexOfFurnitureJson: Int = getColumnIndexOrThrow(_cursor, "furnitureJson")
          val _cursorIndexOfCreatedAt: Int = getColumnIndexOrThrow(_cursor, "createdAt")
          val _result: MutableList<Design> = ArrayList<Design>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: Design
            val _tmpId: Int
            _tmpId = _cursor.getInt(_cursorIndexOfId)
            val _tmpName: String
            _tmpName = _cursor.getString(_cursorIndexOfName)
            val _tmpThumbnailPath: String
            _tmpThumbnailPath = _cursor.getString(_cursorIndexOfThumbnailPath)
            val _tmpFurnitureJson: String
            _tmpFurnitureJson = _cursor.getString(_cursorIndexOfFurnitureJson)
            val _tmpCreatedAt: Long
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt)
            _item = Design(_tmpId,_tmpName,_tmpThumbnailPath,_tmpFurnitureJson,_tmpCreatedAt)
            _result.add(_item)
          }
          return _result
        } finally {
          _cursor.close()
        }
      }

      protected fun finalize() {
        _statement.release()
      }
    })
  }

  public override fun getRecentDesigns(): Flow<List<Design>> {
    val _sql: String = "SELECT * FROM designs ORDER BY createdAt DESC LIMIT 3"
    val _statement: RoomSQLiteQuery = acquire(_sql, 0)
    return CoroutinesRoom.createFlow(__db, false, arrayOf("designs"), object :
        Callable<List<Design>> {
      public override fun call(): List<Design> {
        val _cursor: Cursor = query(__db, _statement, false, null)
        try {
          val _cursorIndexOfId: Int = getColumnIndexOrThrow(_cursor, "id")
          val _cursorIndexOfName: Int = getColumnIndexOrThrow(_cursor, "name")
          val _cursorIndexOfThumbnailPath: Int = getColumnIndexOrThrow(_cursor, "thumbnailPath")
          val _cursorIndexOfFurnitureJson: Int = getColumnIndexOrThrow(_cursor, "furnitureJson")
          val _cursorIndexOfCreatedAt: Int = getColumnIndexOrThrow(_cursor, "createdAt")
          val _result: MutableList<Design> = ArrayList<Design>(_cursor.getCount())
          while (_cursor.moveToNext()) {
            val _item: Design
            val _tmpId: Int
            _tmpId = _cursor.getInt(_cursorIndexOfId)
            val _tmpName: String
            _tmpName = _cursor.getString(_cursorIndexOfName)
            val _tmpThumbnailPath: String
            _tmpThumbnailPath = _cursor.getString(_cursorIndexOfThumbnailPath)
            val _tmpFurnitureJson: String
            _tmpFurnitureJson = _cursor.getString(_cursorIndexOfFurnitureJson)
            val _tmpCreatedAt: Long
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt)
            _item = Design(_tmpId,_tmpName,_tmpThumbnailPath,_tmpFurnitureJson,_tmpCreatedAt)
            _result.add(_item)
          }
          return _result
        } finally {
          _cursor.close()
        }
      }

      protected fun finalize() {
        _statement.release()
      }
    })
  }

  public companion object {
    @JvmStatic
    public fun getRequiredConverters(): List<Class<*>> = emptyList()
  }
}
