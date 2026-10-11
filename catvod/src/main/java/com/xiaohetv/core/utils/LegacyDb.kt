package com.xiaohetv.core.utils

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.xiaohetv.core.Core
import java.io.File

/**
 * 兼容第三方 spider / Guard 真实代码对宿主数据库的直接读取。
 *
 * 依据日志证据（真实 dex 打印的原始 SQL）：
 *   SELECT url FROM Config WHERE type = 0 ORDER BY time DESC LIMIT 1
 * 即它们的代码里合并了参考项目的宿主逻辑，会去读 SQLite 库 `databases/tv`
 * （参考项目 AppDatabase(NAME="tv") 的 Config 实体：id/type/time/url/json/name/logo/home/parse）。
 *
 * 宿主没有这个库/表时它会抛异常并中断初始化 → 首页返回空。这里按同款结构建表并写入当前配置地址。
 */
object LegacyDb {

    private const val NAME = "tv"
    private const val TABLE = "Config"

    private const val CREATE = "CREATE TABLE IF NOT EXISTS $TABLE (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "type INTEGER, time INTEGER, url TEXT, json TEXT, name TEXT, logo TEXT, home TEXT, parse TEXT)"

    private fun file(): File = Core.context().getDatabasePath(NAME)

    private fun open(): SQLiteDatabase? = runCatching {
        val target = file()
        target.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(target, null)
    }.onFailure { Diag.error("LegacyDb", "打开 databases/$NAME 失败", it) }.getOrNull()

    /** 建库建表（幂等）。 */
    @JvmStatic
    fun ensure() {
        val db = open() ?: return
        runCatching { db.execSQL(CREATE) }
            .onFailure { Diag.error("LegacyDb", "创建 $TABLE 表失败", it) }
        db.close()
    }

    /** 写入/更新一条配置记录（type: 0=点播，1=直播，2=壁纸）。 */
    @JvmStatic
    fun saveConfig(url: String, type: Int, name: String = "", json: String = "") {
        if (url.isEmpty()) return
        val db = open() ?: return
        runCatching {
            db.execSQL(CREATE)
            db.delete(TABLE, "url = ? AND type = ?", arrayOf(url, type.toString()))
            val values = ContentValues().apply {
                put("type", type)
                put("time", System.currentTimeMillis())
                put("url", url)
                put("json", json)
                put("name", name)
            }
            db.insert(TABLE, null, values)
            Diag.log("LegacyDb", "已写入 $TABLE：url=${Diag.safeUrl(url)} type=$type name=$name")
        }.onFailure { Diag.error("LegacyDb", "写入 $TABLE 失败", it) }
        db.close()
    }
}
