package com.vapurstudio.hyperkit

import android.app.Notification
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Feeds the lock screen, edge lighting and the notification history (NotiStar). */
class NotiService : NotificationListenerService() {
    companion object {
        @Volatile var instance: NotiService? = null
            private set
    }

    override fun onListenerConnected() {
        instance = this
        KitService.instance?.onNotificationsChanged(null)
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        val fresh = !sbn.isOngoing && (n.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
            (n.flags and Notification.FLAG_ONLY_ALERT_ONCE) == 0 && sbn.packageName != packageName
        if (fresh && Prefs.get(this).getBoolean(Prefs.NOTI_LOG, true)) NotiDb.get(this).log(this, sbn)
        KitService.instance?.onNotificationsChanged(if (fresh) sbn else null)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        KitService.instance?.onNotificationsChanged(null)
    }
}

data class LoggedNoti(val time: Long, val pkg: String, val app: String, val title: String, val text: String)

class NotiDb private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "noti.db", null, 1) {
    companion object {
        @Volatile private var db: NotiDb? = null
        fun get(ctx: Context) = db ?: synchronized(this) { db ?: NotiDb(ctx.applicationContext).also { db = it } }
        private const val MAX = 3000
    }

    override fun onCreate(d: SQLiteDatabase) {
        d.execSQL("CREATE TABLE log(id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER, pkg TEXT, app TEXT, title TEXT, body TEXT)")
        d.execSQL("CREATE INDEX log_time ON log(time)")
    }

    override fun onUpgrade(d: SQLiteDatabase, o: Int, n: Int) {}

    fun log(ctx: Context, sbn: StatusBarNotification) {
        val ex = sbn.notification.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val body = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
        if (title.isBlank() && body.isBlank()) return
        val w = writableDatabase
        // skip exact repeats (apps re-post the same notification on updates)
        w.rawQuery("SELECT title, body FROM log WHERE pkg=? ORDER BY id DESC LIMIT 1", arrayOf(sbn.packageName)).use {
            if (it.moveToFirst() && it.getString(0) == title && it.getString(1) == body) return
        }
        val pm = ctx.packageManager
        val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
        w.insert("log", null, ContentValues().apply {
            put("time", sbn.postTime); put("pkg", sbn.packageName); put("app", app); put("title", title); put("body", body)
        })
        w.execSQL("DELETE FROM log WHERE id <= (SELECT MAX(id) FROM log) - $MAX")
    }

    fun query(search: String, pkg: String?, limit: Int = 300): List<LoggedNoti> {
        val where = StringBuilder("1=1")
        val args = mutableListOf<String>()
        if (search.isNotBlank()) {
            where.append(" AND (title LIKE ? OR body LIKE ? OR app LIKE ?)")
            repeat(3) { args.add("%$search%") }
        }
        if (pkg != null) { where.append(" AND pkg=?"); args.add(pkg) }
        val out = mutableListOf<LoggedNoti>()
        readableDatabase.rawQuery("SELECT time,pkg,app,title,body FROM log WHERE $where ORDER BY time DESC LIMIT $limit", args.toTypedArray()).use {
            while (it.moveToNext()) out.add(LoggedNoti(it.getLong(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4)))
        }
        return out
    }

    fun apps(): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        readableDatabase.rawQuery("SELECT pkg, app, COUNT(*) c FROM log GROUP BY pkg ORDER BY c DESC", null).use {
            while (it.moveToNext()) out.add(it.getString(0) to "${it.getString(1)} (${it.getInt(2)})")
        }
        return out
    }

    fun clear() = writableDatabase.execSQL("DELETE FROM log")
}
