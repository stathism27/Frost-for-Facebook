/*
 * Copyright 2018 Allan Wang
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.pitchedapps.frost.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pitchedapps.frost.services.NOTIF_CHANNEL_GENERAL
import com.pitchedapps.frost.services.NOTIF_CHANNEL_MESSAGES
import com.pitchedapps.frost.services.NotificationContent
import com.pitchedapps.frost.utils.L
import com.raizlabs.android.dbflow.annotation.ConflictAction
import com.raizlabs.android.dbflow.annotation.Database
import com.raizlabs.android.dbflow.annotation.Migration
import com.raizlabs.android.dbflow.annotation.PrimaryKey
import com.raizlabs.android.dbflow.annotation.Table
import com.raizlabs.android.dbflow.kotlinextensions.eq
import com.raizlabs.android.dbflow.kotlinextensions.from
import com.raizlabs.android.dbflow.kotlinextensions.select
import com.raizlabs.android.dbflow.kotlinextensions.where
import com.raizlabs.android.dbflow.sql.SQLiteType
import com.raizlabs.android.dbflow.sql.migration.AlterTableMigration
import com.raizlabs.android.dbflow.structure.BaseModel

@Entity(
    tableName = "notifications",
    primaryKeys = ["notif_id", "userId"],
    foreignKeys = [ForeignKey(
        entity = CookieEntity::class,
        parentColumns = ["cookie_id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("notif_id"), Index("userId")]
)
data class NotificationEntity(
    @ColumnInfo(name = "notif_id")
    val id: Long,
    val userId: Long,
    val href: String,
    val title: String?,
    val text: String,
    val timestamp: Long,
    val profileUrl: String?,
    // Type essentially refers to channel
    val type: String,
    val unread: Boolean
) {
    constructor(
        type: String,
        content: NotificationContent
    ) : this(
        content.id,
        content.data.id,
        content.href,
        content.title,
        content.text,
        content.timestamp,
        content.profileUrl,
        type,
        content.unread
    )
}

data class NotificationContentEntity(
    @Embedded
    val cookie: CookieEntity,
    @Embedded
    val notif: NotificationEntity
) {
    fun toNotifContent() = NotificationContent(
        data = cookie,
        id = notif.id,
        href = notif.href,
        title = notif.title,
        text = notif.text,
        timestamp = notif.timestamp,
        profileUrl = notif.profileUrl,
        unread = notif.unread
    )
}

@Dao
interface NotificationDao {

    /**
     * Note that notifications are guaranteed to be ordered by descending timestamp
     */
    @Transaction
    @Query("SELECT * FROM cookies INNER JOIN notifications ON cookie_id = userId WHERE userId = :userId  AND type = :type ORDER BY timestamp DESC")
    fun _selectNotifications(userId: Long, type: String): List<NotificationContentEntity>

    @Query("SELECT timestamp FROM notifications WHERE userId = :userId AND type = :type ORDER BY timestamp DESC LIMIT 1")
    fun _selectEpoch(userId: Long, type: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun _insertNotifications(notifs: List<NotificationEntity>)

    @Query("DELETE FROM notifications WHERE userId = :userId AND type = :type")
    fun _deleteNotifications(userId: Long, type: String)

    @Query("DELETE FROM notifications")
    fun _deleteAll()

    /**
     * It is assumed that the notification batch comes from the same user
     */
    @Transaction
    fun _saveNotifications(type: String, notifs: List<NotificationContent>) {
        val userId = notifs.firstOrNull()?.data?.id ?: return
        val entities = notifs.map { NotificationEntity(type, it) }
        _deleteNotifications(userId, type)
        _insertNotifications(entities)
    }
}

suspend fun NotificationDao.deleteAll() = dao { _deleteAll() }

fun NotificationDao.selectNotificationsSync(userId: Long, type: String): List<NotificationContent> =
    _selectNotifications(userId, type).map { it.toNotifContent() }

suspend fun NotificationDao.selectNotifications(userId: Long, type: String): List<NotificationContent> = dao {
    selectNotificationsSync(userId, type)
}

/**
 * Returns true if successful, given that there are constraints to the insertion
 */
suspend fun NotificationDao.saveNotifications(type: String, notifs: List<NotificationContent>): Boolean = dao {
    try {
        _saveNotifications(type, notifs)
        true
    } catch (e: Exception) {
        L.e(e) { "Notif save failed for $type" }
        false
    }
}

suspend fun NotificationDao.latestEpoch(userId: Long, type: String): Long = dao {
    _selectEpoch(userId, type) ?: lastNotificationTime(userId).let {
        when (type) {
            NOTIF_CHANNEL_GENERAL -> it.epoch
            NOTIF_CHANNEL_MESSAGES -> it.epochIm
            else -> -1L
        }
    }
}

/**
 * Created by Allan Wang on 2017-05-30.
 */

@Database(version = NotificationDb.VERSION)
object NotificationDb {
    const val NAME = "Notifications"
    const val VERSION = 2
}

@Migration(version = 2, database = NotificationDb::class)
class NotificationMigration2(modelClass: Class<NotificationModel>) :
    AlterTableMigration<NotificationModel>(modelClass) {
    override fun onPreMigrate() {
        super.onPreMigrate()
        addColumn(SQLiteType.INTEGER, "epochIm")
        L.d { "Added column" }
    }
}

@Table(database = NotificationDb::class, allFields = true, primaryKeyConflict = ConflictAction.REPLACE)
data class NotificationModel(
    @PrimaryKey var id: Long = -1L,
    var epoch: Long = -1L,
    var epochIm: Long = -1L
) : BaseModel()

internal fun lastNotificationTime(id: Long): NotificationModel =
    (select from NotificationModel::class where (NotificationModel_Table.id eq id)).querySingle()
        ?: NotificationModel(id = id)
