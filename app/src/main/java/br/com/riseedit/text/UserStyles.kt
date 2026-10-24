package br.com.riseedit.text

import android.content.Context
import br.com.riseedit.StyleJson
import br.com.riseedit.core.TextLook
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** 0.25.0: the user's own looks ("Meus estilos"), a small JSON file in app storage. */
internal object UserStyles {
    private fun file(context: Context) = File(context.filesDir, "text-styles.json")

    fun load(context: Context): List<TextLook> = runCatching {
        val f = file(context)
        if (!f.exists()) return emptyList()
        val array = JSONArray(f.readText())
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            TextLook(
                o.getString("name"),
                o.getInt("color"),
                o.optInt("hl", 0),
                StyleJson.readText(o.optJSONObject("style"), true)
            )
        }
    }.getOrDefault(emptyList())

    fun save(context: Context, looks: List<TextLook>) {
        val array = JSONArray()
        looks.forEach {
            array.put(
                JSONObject().put("name", it.name).put("color", it.color).put("hl", it.highlightColor)
                    .put("style", StyleJson.text(it.style))
            )
        }
        val f = file(context)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(array.toString())
        tmp.renameTo(f)
    }

    fun nextName(looks: List<TextLook>): String {
        var n = looks.size + 1
        while (looks.any { it.name == "Meu estilo $n" }) n++
        return "Meu estilo $n"
    }
}
