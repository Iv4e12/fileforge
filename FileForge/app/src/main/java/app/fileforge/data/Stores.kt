package app.fileforge.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RecentFile(val uri: String, val name: String, val openedAt: Long)

class RecentFilesStore(context: Context) {
    private val prefs = context.getSharedPreferences("recent_files", Context.MODE_PRIVATE)

    fun load(): List<RecentFile> = try {
        val arr = JSONArray(prefs.getString("items", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            RecentFile(o.getString("uri"), o.getString("name"), o.getLong("t"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun add(uri: String, name: String): List<RecentFile> {
        val list = (listOf(RecentFile(uri, name, System.currentTimeMillis())) + load().filter { it.uri != uri }).take(20)
        save(list)
        return list
    }

    fun remove(uri: String): List<RecentFile> {
        val list = load().filter { it.uri != uri }
        save(list)
        return list
    }

    fun clear() = save(emptyList())

    private fun save(list: List<RecentFile>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("uri", it.uri).put("name", it.name).put("t", it.openedAt)) }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 0 = follow system, 1 = light, 2 = dark (default). */
    var themeMode: Int
        get() = prefs.getInt("theme", 2)
        set(v) { prefs.edit().putInt("theme", v).apply() }

    var fontSize: Int
        get() = prefs.getInt("font", 15)
        set(v) { prefs.edit().putInt("font", v).apply() }

    var wordWrap: Boolean
        get() = prefs.getBoolean("wrap", true)
        set(v) { prefs.edit().putBoolean("wrap", v).apply() }
}
