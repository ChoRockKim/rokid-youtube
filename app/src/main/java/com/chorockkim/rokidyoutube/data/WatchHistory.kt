package com.chorockkim.rokidyoutube.data

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import java.io.File

/**
 * What was watched on these glasses, kept only on the device (like Herald's "Based on what you
 * watch here"). Each entry keeps the video's related videos, which [recommendations] mixes into
 * the "추천" feed, and the playback position for "이어보기".
 */
class WatchHistory(private val file: File?, private val now: () -> Long = System::currentTimeMillis) {
    data class Entry(
        val video: VideoItem,
        val related: List<VideoItem>,
        val positionMs: Long,
        val durationMs: Long,
        val watchedAt: Long
    ) {
        val inProgress: Boolean
            get() = positionMs >= RESUME_MIN_MS && (durationMs <= 0 || positionMs < durationMs - RESUME_TAIL_MS)
    }

    private val entries = mutableListOf<Entry>()

    init {
        load()
    }

    val all: List<Entry> get() = entries.toList()

    fun position(url: String): Long = entries.firstOrNull { it.video.url == url }?.positionMs ?: 0L

    /** Called when a video starts playing; moves it to the front. */
    @Synchronized
    fun recordWatch(video: VideoItem, related: List<VideoItem>) {
        val old = entries.firstOrNull { it.video.url == video.url }
        entries.removeAll { it.video.url == video.url }
        entries.add(
            0,
            Entry(
                video = video,
                related = related.take(MAX_RELATED).ifEmpty { old?.related.orEmpty() },
                positionMs = old?.positionMs ?: 0L,
                durationMs = old?.durationMs ?: video.durationSeconds * 1000,
                watchedAt = now()
            )
        )
        while (entries.size > MAX_ENTRIES) entries.removeAt(entries.lastIndex)
        save()
    }

    @Synchronized
    fun updatePosition(url: String, positionMs: Long, durationMs: Long) {
        val index = entries.indexOfFirst { it.video.url == url }
        if (index < 0) return
        entries[index] = entries[index].copy(
            positionMs = positionMs,
            durationMs = if (durationMs > 0) durationMs else entries[index].durationMs
        )
        save()
    }

    @Synchronized
    fun clear() {
        entries.clear()
        save()
    }

    fun continueWatching(): List<VideoItem> = entries.filter { it.inProgress }.map { it.video }

    /**
     * Related videos of the most recently watched entries, interleaved so the newest interests
     * come first, minus anything already watched.
     */
    fun recommendations(limit: Int = 60): List<VideoItem> {
        val watched = entries.map { it.video.url }.toSet()
        val lists = entries.take(SEED_ENTRIES).map { entry -> entry.related.filter { it.url !in watched } }
        return YouTubeRepository.interleave(lists).distinctBy { it.url }.take(limit)
    }

    // region persistence (nanojson, so this also runs in plain JVM unit tests)

    private fun load() {
        val f = file ?: return
        if (!f.exists()) return
        runCatching {
            JsonParser.array().from(f.readText()).filterIsInstance<JsonObject>().mapTo(entries) { o ->
                Entry(
                    video = videoFrom(o.getObject("video")),
                    related = o.getArray("related").filterIsInstance<JsonObject>().map(::videoFrom),
                    positionMs = o.getLong("position"),
                    durationMs = o.getLong("duration"),
                    watchedAt = o.getLong("watchedAt")
                )
            }
        }.onFailure { entries.clear() }
    }

    private fun save() {
        val f = file ?: return
        val json = JsonWriter.string().array().apply {
            entries.forEach { e ->
                `object`()
                    .value("position", e.positionMs)
                    .value("duration", e.durationMs)
                    .value("watchedAt", e.watchedAt)
                writeVideo("video", e.video)
                array("related")
                e.related.forEach { writeVideo(null, it) }
                end()
                end()
            }
        }.end().done()
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp")
        tmp.writeText(json)
        tmp.renameTo(f)
    }

    private fun com.grack.nanojson.JsonStringWriter.writeVideo(key: String?, v: VideoItem) {
        if (key == null) `object`() else `object`(key)
        value("url", v.url)
        value("title", v.title)
        value("uploader", v.uploader)
        value("duration", v.durationSeconds)
        value("views", v.viewCount)
        if (v.thumbnailUrl != null) value("thumb", v.thumbnailUrl)
        value("live", v.isLive)
        end()
    }

    private fun videoFrom(o: JsonObject) = VideoItem(
        url = o.getString("url"),
        title = o.getString("title", ""),
        uploader = o.getString("uploader", ""),
        durationSeconds = o.getLong("duration"),
        viewCount = o.getLong("views", -1),
        thumbnailUrl = o.getString("thumb", null),
        isLive = o.getBoolean("live", false)
    )

    // endregion

    companion object {
        const val MAX_ENTRIES = 50
        const val MAX_RELATED = 20
        const val SEED_ENTRIES = 10
        const val RESUME_MIN_MS = 30_000L
        const val RESUME_TAIL_MS = 30_000L
    }
}
