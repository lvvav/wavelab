package app.soundlife

import android.content.Context
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/** Адрес сайта: оттуда берутся и каталог, и музыка, и обложки. */
const val SITE = "https://lvvav.github.io/wavelab/"

data class Track(
    val key: String,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val year: String,
    val no: Int,
    val disc: Int,
    val dur: Int,
    val genre: String,
    val url: String,
    val cover: String,
)

data class Album(
    val key: String,
    val title: String,
    val artist: String,
    val year: String,
    val cover: String,
    val tracks: List<Track>,
)

data class Artist(
    val key: String,
    val name: String,
    val cover: String,
    val tracks: List<Track>,
    val albums: List<Album>,
)

class Library(
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
) {
    val byKey: Map<String, Track> = tracks.associateBy { it.key }
    val albumByKey: Map<String, Album> = albums.associateBy { it.key }
    val artistByKey: Map<String, Artist> = artists.associateBy { it.key }
}

object LibraryLoader {
    private val http = OkHttpClient()

    private fun fileUrl(path: String) = SITE + Uri.encode(path, "/")
    private fun coverUrl(hash: String) = if (hash.isBlank()) "" else SITE + "tags/c/" + hash + ".jpg"

    /** Загружает каталог с сайта; без сети берёт последнюю сохранённую копию. */
    fun load(ctx: Context): Library {
        val cache = File(ctx.cacheDir, "index.json")
        val text = try {
            val req = Request.Builder().url(SITE + "tags/index.json?v=" + System.currentTimeMillis() / 600000).build()
            val body = http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw RuntimeException("Сайт ответил кодом " + r.code)
                r.body!!.string()
            }
            cache.writeText(body)
            body
        } catch (e: Exception) {
            if (cache.exists()) cache.readText() else throw e
        }
        return parse(text)
    }

    /** Основной исполнитель: «урал гайсин ft. ulysse» → «урал гайсин». */
    private fun primary(artist: String): String =
        artist.split(Regex("\\s+(ft\\.?|feat\\.?|featuring)\\s+", RegexOption.IGNORE_CASE)).first().trim()

    fun parse(text: String): Library {
        val t = JSONObject(text).optJSONObject("t") ?: return Library(emptyList(), emptyList(), emptyList())
        val list = ArrayList<Track>()
        val keys = t.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val path = k.substringBeforeLast('|')
            val arr = t.optJSONArray(k) ?: continue
            val m = arr.optJSONObject(0) ?: JSONObject()
            val cover = coverUrl(arr.optString(1, ""))
            val name = path.substringAfterLast('/').substringBeforeLast('.')
            var title = m.optString("t", "").trim()
            var artist = m.optString("a", "").trim()
            if (title.isEmpty()) {
                // для файлов без тегов: имя вида «Артист - Название»
                val i = name.indexOf(" - ")
                if (i > 0) {
                    if (artist.isEmpty()) artist = name.substring(0, i).trim()
                    title = name.substring(i + 3).trim()
                } else {
                    title = name
                }
            }
            if (artist.isEmpty()) artist = "—"
            list.add(
                Track(
                    key = path,
                    title = title,
                    artist = artist,
                    albumArtist = m.optString("aa", "").trim(),
                    album = m.optString("al", "").trim(),
                    year = m.optString("y", "").trim(),
                    no = m.optInt("n", 0),
                    disc = m.optInt("d", 1).coerceAtLeast(1),
                    dur = m.optInt("dur", 0),
                    genre = m.optString("g", "").trim(),
                    url = fileUrl(path),
                    cover = cover,
                )
            )
        }
        val tracks = list.sortedWith(
            compareBy<Track>({ primary(it.artist).lowercase() }, { it.album.lowercase() }, { it.disc }, { if (it.no > 0) it.no else 999 }, { it.title.lowercase() })
        )

        // альбомы: артист альбома + название
        val albums = tracks.filter { it.album.isNotBlank() }
            .groupBy { (it.albumArtist.ifBlank { primary(it.artist) } + "|" + it.album).lowercase() }
            .map { (key, ts) ->
                val sorted = ts.sortedWith(compareBy<Track>({ it.disc }, { if (it.no > 0) it.no else 999 }, { it.title.lowercase() }))
                Album(
                    key = key,
                    title = sorted.first().album,
                    artist = sorted.first().albumArtist.ifBlank { primary(sorted.first().artist) },
                    year = sorted.firstOrNull { it.year.isNotBlank() }?.year ?: "",
                    cover = sorted.firstOrNull { it.cover.isNotBlank() }?.cover ?: "",
                    tracks = sorted,
                )
            }
            .sortedWith(compareBy<Album>({ it.artist.lowercase() }, { it.year }, { it.title.lowercase() }))

        // артисты: по основному исполнителю трека
        val artists = tracks.groupBy { primary(it.artist).lowercase() }
            .map { (key, ts) ->
                val name = primary(ts.first().artist)
                val his = albums.filter { a -> a.tracks.any { primary(it.artist).lowercase() == key } }
                    .sortedByDescending { it.year }
                Artist(
                    key = key,
                    name = name,
                    cover = ts.firstOrNull { it.cover.isNotBlank() }?.cover ?: "",
                    tracks = ts,
                    albums = his,
                )
            }
            .sortedBy { it.name.lowercase() }

        return Library(tracks, albums, artists)
    }
}
