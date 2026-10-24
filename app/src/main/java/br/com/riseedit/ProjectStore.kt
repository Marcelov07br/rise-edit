package br.com.riseedit

import android.content.Context
import android.util.AtomicFile
import br.com.riseedit.core.*
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

class ProjectStore(private val context: Context) {
    private val root = context.filesDir
    private val projects = File(root, "projects").apply { mkdirs() }
    private val versions = File(root, "project-versions").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val maxVersions = 5
    private fun projectFile(id: String): AtomicFile {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        return AtomicFile(File(projects, "$id.json"))
    }
    fun list(): List<Project> {
        migrate()
        return projects.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull {
            runCatching {
                decode(
                    JSONObject(
                        AtomicFile(it).openRead().bufferedReader().use { r ->
                            r.readText()
                        }
                    )
                )
            }.getOrNull()
        }.sortedByDescending { it.modifiedAt }
    }
    private fun migrate() {
        val legacy = AtomicFile(File(root, "project.json"))
        if (legacy.baseFile.exists() && !prefs.getBoolean("migrated", false)) {
            val p = decode(
                JSONObject(
                    legacy.openRead().bufferedReader().use {
                        it.readText()
                    }
                )
            ).copy(id = "legacy-project", name = "Meu primeiro projeto")
            if (!projectFile(p.id).baseFile.exists()) save(p)
            prefs.edit().putBoolean("migrated", true).apply()
        }
    }
    fun load(id: String): Project = decode(
        JSONObject(
            projectFile(id).openRead().bufferedReader().use {
                it.readText()
            }
        )
    )
    fun load(): Project {
        migrate()
        return prefs.getString("last", null)?.let { runCatching { load(it) }.getOrNull() }
            ?: Project()
    }
    fun save(project: Project) {
        val file = projectFile(project.id)
        if (file.baseFile.exists()) snapshot(project.id)
        write(file, encode(project).toString())
        prefs.edit().putString("last", project.id).apply()
    }
    fun delete(id: String) {
        projectFile(id).delete()
        versionDir(id).deleteRecursively()
        if (prefs.getString("last", null) == id) prefs.edit().remove("last").apply()
    }
    private fun versionDir(id: String) = File(versions, id).also { it.mkdirs() }

    /** Keeps the last few project JSON snapshots so a bad save or corrupt open can be recovered. */
    private fun snapshot(id: String) {
        runCatching {
            val dir = versionDir(id)
            val stamp = System.currentTimeMillis()
            projectFile(id).baseFile.copyTo(File(dir, "$stamp.json"), overwrite = true)
            dir.listFiles()?.filter {
                it.extension == "json"
            }?.sortedByDescending { it.name }?.drop(maxVersions)?.forEach { it.delete() }
        }
    }
    fun versions(id: String): List<Pair<Long, File>> =
        versionDir(id).listFiles().orEmpty().filter { it.extension == "json" }
            .mapNotNull { f -> f.nameWithoutExtension.toLongOrNull()?.let { it to f } }
            .sortedByDescending { it.first }

    /** Restores a previous snapshot as a new project (does not overwrite the live one). */
    fun restoreVersion(id: String, stamp: Long, asCopy: Boolean = true): Project? = runCatching {
        val file = versions(id).firstOrNull { it.first == stamp }?.second ?: return null
        val restored = decode(JSONObject(file.readText()))
        val project = if (asCopy) {
            br.com.riseedit.core.ProjectRecovery.restoredCopy(
                restored
            )
        } else {
            restored.copy(id = id)
        }
        save(project.copy(modifiedAt = System.currentTimeMillis()))
        project
    }.getOrNull()
    fun readable(uri: String): Boolean = runCatching {
        val parsed = android.net.Uri.parse(uri)
        when (parsed.scheme) {
            "file" -> java.io.File(parsed.path.orEmpty()).let { it.isFile && it.canRead() }
            "content" -> context.contentResolver.openAssetFileDescriptor(parsed, "r")?.use {
                true
            } ==
                true
            else -> false
        }
    }.getOrDefault(false)

    // detekt: flat JSON field list (one put per clip field).
    @Suppress("LongMethod")
    private fun clip(c: Clip): JSONObject =
        JSONObject().put("id", c.id).put("uri", c.uri).put("name", c.name)
            .put(
                "duration",
                c.sourceDurationMs
            ).put(
                "start",
                c.startMs
            ).put("end", c.endMs).put("volume", c.volume).put("fade", c.fadeMs)
            .put("speed", JSONArray(c.speedPoints.map { JSONArray(listOf(it.sourceMs, it.speed)) }))
            .put("keepPitch", c.keepPitch)
            .put(
                "adjust",
                JSONArray(
                    listOf(
                        c.adjustments.brightness,
                        c.adjustments.contrast,
                        c.adjustments.saturation,
                        c.adjustments.warmth
                    )
                )
            )
            .put("color", JSONObject(ColorAdjust.toMap(c.adjustments)))
            .put("keys", keys(c.keyframes))
            .put("sourceInfo", c.sourceInfo?.let(SourceMetadataJson::encode) ?: JSONObject.NULL)
            .put("proxyUri", c.proxyUri ?: JSONObject.NULL).put("proxyRequired", c.proxyRequired)
            .put("originalUri", c.originalUri ?: JSONObject.NULL)
            .put("muted", c.muted)
            .put(
                "orientation",
                JSONObject()
                    .put("turns", c.orientation.turns)
                    .put("flipH", c.orientation.flipHorizontal)
                    .put("flipV", c.orientation.flipVertical)
                    .put("scale", c.orientation.scaleMode.name)
            )
            .put("tracks", TracksJson.encode(c.tracks))
            .put("grade", GradeJson.encode(c.grade))
            .apply {
                if (!c.colorKeys.isEmpty) {
                    put(
                        "colorKeys",
                        JSONObject(br.com.riseedit.core.color.ColorKeyframes.toMap(c.colorKeys))
                    )
                }
            }
            // schema 10 (0.17): masks {"v":1,"masks":[...]}; absent (≤ 9) → no masks.
            .apply {
                if (c.masks.masks.isNotEmpty()) {
                    put(
                        "masks",
                        JSONObject(br.com.riseedit.core.mask.MaskSet.toMap(c.masks))
                    )
                }
            }
            .put(
                "stabilization",
                JSONObject().put(
                    "enabled",
                    c.stabilization.enabled
                ).put("goal", c.stabilization.goal.name)
                    .put(
                        "strength",
                        c.stabilization.strength
                    ).put(
                        "smooth",
                        c.stabilization.smoothSeconds
                    ).put("zoom", c.stabilization.maxZoom)
                    .put("method", c.stabilization.method.name)
                    .put("rs", c.stabilization.rollingShutter)
                    .put("warp", c.stabilization.warpStrength)
            )
    private fun readClip(c: JSONObject): Clip {
        val s = c.getJSONObject("stabilization")
        val a = c.optJSONArray("adjust")
        return Clip(
            id = c.getString(
                "id"
            ),
            uri = c.getString(
                "uri"
            ),
            name = c.getString(
                "name"
            ),
            sourceDurationMs = c.getLong(
                "duration"
            ),
            startMs = c.getLong("start"), endMs = c.getLong("end"),
            stabilization = Stabilization(
                s.getBoolean("enabled"),
                MotionGoal.valueOf(s.getString("goal")),
                s.getDouble("strength"),
                s.getDouble("smooth"),
                s.getDouble("zoom"),
                // 0.20: optional method/rs — older projects stay rigid without warp.
                StabilizeMethod.valueOf(s.optString("method", StabilizeMethod.RIGID.name)),
                s.optDouble("rs", 0.6),
                // 0.23.0: independent "Força do Warp"; older projects inherit the shared value.
                s.optDouble("warp", s.getDouble("strength")).coerceIn(0.0, 1.0)
            ),
            volume = c.optDouble("volume", 1.0).toFloat(), fadeMs = c.optLong("fade"),
            speedPoints = rows(c.optJSONArray("speed")).map {
                SpeedPoint(it.getLong(0), it.getDouble(1))
            },
            keepPitch = c.optBoolean("keepPitch", true),
            adjustments = c.optJSONObject("color")?.let { o ->
                ColorAdjust.fromMap(GradeJson.plain(o))
            } ?: ColorAdjust.fromLegacy(
                a?.let { arr -> List(arr.length()) { arr.optDouble(it, 0.0) } }
            ),
            keyframes = readKeys(c.optJSONArray("keys")),
            sourceInfo = c.optJSONObject("sourceInfo")?.let(SourceMetadataJson::decode),
            proxyUri = if (c.isNull("proxyUri")) null else c.optString("proxyUri").ifBlank { null },
            proxyRequired = c.optBoolean("proxyRequired"),
            originalUri = c.optString("originalUri").takeIf { it.isNotBlank() && it != "null" },
            muted = c.optBoolean("muted", false),
            orientation = readOrientation(c.optJSONObject("orientation")),
            tracks = TracksJson.decode(c.optJSONObject("tracks")),
            grade = GradeJson.decode(c.optJSONObject("grade")),
            colorKeys = br.com.riseedit.core.color.ColorKeyframes.fromMap(
                c.optJSONObject("colorKeys")?.let(GradeJson::plain)
            ),
            masks = br.com.riseedit.core.mask.MaskSet.fromMap(
                c.optJSONObject("masks")?.let(GradeJson::plain)
            )
        )
    }

    private fun readOrientation(o: JSONObject?): br.com.riseedit.core.Orientation {
        if (o == null) return br.com.riseedit.core.Orientation()
        val mode = runCatching {
            br.com.riseedit.core.ScaleMode.valueOf(o.optString("scale", "FILL"))
        }.getOrDefault(br.com.riseedit.core.ScaleMode.FILL)
        return br.com.riseedit.core.Orientation(
            turns = o.optInt("turns", 0).coerceIn(0, 3),
            flipHorizontal = o.optBoolean("flipH"),
            flipVertical = o.optBoolean("flipV"),
            scaleMode = mode
        )
    }
    private fun encode(p: Project) = JSONObject().put(
        "schema",
        10
    ).put(
        // schema 9 (0.16): adjustment layers; absent in ≤ 8 → none.
        "adjustmentLayers",
        JSONArray(
            p.adjustmentLayers.map {
                JSONObject(
                    br.com.riseedit.core.AdjustmentLayer.toMap(it)
                ).put("grade", GradeJson.encode(it.grade))
            }
        )
    ).put(
        "id",
        p.id
    ).put("name", p.name).put("modified", p.modifiedAt).put("favorite", p.favorite)
        .put(
            "clips",
            JSONArray(p.clips.map(::clip))
        ).put(
            "ratio",
            p.aspectRatio
        ).put("useProxies", p.useProxies).put(
            "bg",
            JSONObject().put("kind", p.background.kind.name).put("color", p.background.color)
                .put("blur", p.background.blur.toDouble()).put("dim", p.background.dim.toDouble())
        ).put(
            "export",
            JSONArray(
                listOf(
                    p.export.height,
                    p.export.fps,
                    p.export.bitrate,
                    p.export.codec.name,
                    p.export.ntsc,
                    p.export.bitDepth,
                    p.export.chroma.name,
                    p.export.prores.name,
                    p.export.movAudio.name
                )
            )
        )
        .put(
            "markers",
            JSONArray(
                p.markers.map {
                    JSONObject().put("id", it.id).put("t", it.timeMs).put("label", it.label)
                        .put("color", it.color)
                }
            )
        )
        .put(
            "audio",
            JSONArray(
                p.audio.map { a ->
                    JSONObject().put(
                        "id",
                        a.id
                    ).put("uri", a.uri).put("name", a.name).put("duration", a.sourceDurationMs)
                        .put(
                            "offset",
                            a.offsetMs
                        ).put(
                            "start",
                            a.startMs
                        ).put(
                            "end",
                            a.endMs
                        ).put(
                            "track",
                            a.track
                        ).put("volume", a.volume).put("fade", a.fadeMs).put("muted", a.muted)
                        .put("linkedClipId", a.linkedClipId ?: JSONObject.NULL)
                        .put("lead", a.leadMs).put("tail", a.tailMs)
                }
            )
        )
        .put(
            "texts",
            JSONArray(
                p.texts.map { t ->
                    JSONObject().put(
                        "id",
                        t.id
                    ).put("text", t.text).put("start", t.startMs).put("end", t.endMs)
                        .put(
                            "x",
                            t.x
                        ).put(
                            "y",
                            t.y
                        ).put(
                            "size",
                            t.size
                        ).put(
                            "color",
                            t.color
                        ).put(
                            "animated",
                            t.animated
                        ).put(
                            "rotation",
                            t.rotation
                        ).put("keys", keys(t.keyframes)).put("style", StyleJson.text(t.style))
                        .put("tracks", TracksJson.encode(t.tracks))
                        .put("hl", t.highlightColor)
                        .put("cg", t.captionGroup ?: JSONObject.NULL)
                        .put("fo", JSONObject().apply { t.fontOverrides.forEach { (k, v) -> put(k.toString(), v) } })
                        .put(
                            "words",
                            JSONArray(t.words.map { w -> JSONArray(listOf(w.startMs, w.endMs, w.text)) })
                        )
                }
            )
        )
        .put(
            "overlays",
            JSONArray(
                p.overlays.map { o ->
                    JSONObject().put(
                        "id",
                        o.id
                    ).put("clip", clip(o.clip)).put("offset", o.offsetMs)
                        .put(
                            "track",
                            o.track
                        ).put(
                            "x",
                            o.x
                        ).put(
                            "y",
                            o.y
                        ).put(
                            "scale",
                            o.scale
                        ).put(
                            "opacity",
                            o.opacity
                        ).put(
                            "rotation",
                            o.rotation
                        ).put("keys", keys(o.keyframes)).put("look", StyleJson.look(o))
                        .put("tracks", TracksJson.encode(o.tracks))
                }
            )
        )
    private fun decode(j: JSONObject): Project {
        require(j.getInt("schema") in 1..10) { "Formato de projeto não reconhecido" }
        val e = j.optJSONArray("export")
        return Project(
            clips = objects(
                j.getJSONArray("clips")
            ).map(
                ::readClip
            ),
            id = j.optString("id", "legacy-project"), name = j.optString("name", "Meu projeto"),
            modifiedAt = j.optLong(
                "modified",
                System.currentTimeMillis()
            ),
            favorite = j.optBoolean(
                "favorite"
            ),
            aspectRatio = j.optDouble("ratio", 0.0).toFloat(),
            background = j.optJSONObject("bg")?.let { b ->
                br.com.riseedit.core.FrameBackground(
                    runCatching { br.com.riseedit.core.BackgroundKind.valueOf(b.optString("kind")) }
                        .getOrDefault(br.com.riseedit.core.BackgroundKind.BLACK),
                    b.optInt("color", 0xFF000000.toInt()),
                    b.optDouble("blur", 0.6).toFloat().coerceIn(0f, 1f),
                    b.optDouble("dim", 0.25).toFloat().coerceIn(0f, 1f)
                )
            } ?: br.com.riseedit.core.FrameBackground(),
            useProxies = j.optBoolean("useProxies", true),
            export = if (e ==
                null
            ) {
                ExportSettings()
            } else {
                ExportSettings(
                    e.getInt(0),
                    e.getInt(1),
                    e.getInt(2).coerceIn(ExportSettings.MIN_BITRATE, ExportSettings.MAX_BITRATE),
                    runCatching {
                        VideoCodec.valueOf(e.optString(3, "H264"))
                    }.getOrDefault(VideoCodec.H264),
                    // 0.19: 5th element = NTSC (absent → integer rate).
                    e.optBoolean(4, false) && e.getInt(1) in ExportSettings.NTSC_BASES,
                    // 0.21: depth/chroma/ProRes/mov audio (absent → 8-bit 4:2:0 as before).
                    e.optInt(5, 8).takeIf { it in ExportSettings.DEPTHS } ?: 8,
                    runCatching { br.com.riseedit.core.Chroma.valueOf(e.optString(6, "YUV420")) }
                        .getOrDefault(br.com.riseedit.core.Chroma.YUV420),
                    runCatching { br.com.riseedit.core.ProresProfile.valueOf(e.optString(7, "HQ")) }
                        .getOrDefault(br.com.riseedit.core.ProresProfile.HQ),
                    runCatching { br.com.riseedit.core.MovAudio.valueOf(e.optString(8, "AAC")) }
                        .getOrDefault(br.com.riseedit.core.MovAudio.AAC)
                )
            },
            markers = objects(j.optJSONArray("markers")).map {
                Marker(
                    it.getString("id"),
                    it.getLong("t"),
                    it.optString("label"),
                    it.optInt("color", br.com.riseedit.core.MARKER_DEFAULT_COLOR)
                )
            },
            adjustmentLayers = objects(j.optJSONArray("adjustmentLayers")).mapNotNull {
                runCatching {
                    br.com.riseedit.core.AdjustmentLayer.fromMap(
                        GradeJson.plain(it),
                        GradeJson.decode(it.optJSONObject("grade"))
                    )
                }.getOrNull()
            },
            audio = objects(j.optJSONArray("audio")).map { a ->
                AudioClip(
                    a.getString(
                        "id"
                    ),
                    a.getString(
                        "uri"
                    ),
                    a.getString(
                        "name"
                    ),
                    a.getLong(
                        "duration"
                    ),
                    a.getLong(
                        "offset"
                    ),
                    a.getLong(
                        "start"
                    ),
                    a.getLong(
                        "end"
                    ),
                    a.getInt(
                        "track"
                    ),
                    a.getDouble(
                        "volume"
                    ).toFloat(),
                    a.getLong("fade"), a.optBoolean("muted", false),
                    linkedClipId = if (a.isNull(
                            "linkedClipId"
                        )
                    ) {
                        null
                    } else {
                        a.optString("linkedClipId").ifBlank {
                            null
                        }
                    },
                    leadMs = a.optLong("lead"),
                    tailMs = a.optLong("tail")
                )
            },
            texts = objects(j.optJSONArray("texts")).map { t ->
                TextLayer(
                    t.getString(
                        "id"
                    ),
                    t.getString(
                        "text"
                    ),
                    t.getLong(
                        "start"
                    ),
                    t.getLong(
                        "end"
                    ),
                    t.getDouble(
                        "x"
                    ).toFloat(),
                    t.getDouble(
                        "y"
                    ).toFloat(),
                    t.getDouble(
                        "size"
                    ).toFloat(),
                    t.getInt(
                        "color"
                    ),
                    t.getBoolean(
                        "animated"
                    ),
                    t.optDouble(
                        "rotation",
                        0.0
                    ).toFloat(),
                    readKeys(
                        t.optJSONArray("keys")
                    ),
                    StyleJson.readText(t.optJSONObject("style"), t.getBoolean("animated"))
                ).copy(
                    tracks = TracksJson.decode(t.optJSONObject("tracks")),
                    highlightColor = t.optInt("hl", 0),
                    captionGroup = t.optString("cg", "").takeIf { it.isNotEmpty() && it != "null" },
                    fontOverrides = t.optJSONObject("fo")?.let { o ->
                        o.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to o.getBoolean(k) } }.toMap()
                    } ?: emptyMap(),
                    words = t.optJSONArray("words")?.let { a ->
                        (0 until a.length()).mapNotNull { i ->
                            val w = a.optJSONArray(i) ?: return@mapNotNull null
                            br.com.riseedit.core.CaptionWord(w.getLong(0), w.getLong(1), w.getString(2))
                        }
                    } ?: emptyList()
                )
            },
            overlays = objects(j.optJSONArray("overlays")).map { o ->
                VideoOverlay(
                    o.getString(
                        "id"
                    ),
                    readClip(
                        o.getJSONObject("clip")
                    ),
                    o.getLong(
                        "offset"
                    ),
                    o.getInt(
                        "track"
                    ),
                    o.getDouble(
                        "x"
                    ).toFloat(),
                    o.getDouble(
                        "y"
                    ).toFloat(),
                    o.getDouble(
                        "scale"
                    ).toFloat(),
                    o.getDouble(
                        "opacity"
                    ).toFloat(),
                    o.optDouble(
                        "rotation",
                        0.0
                    ).toFloat(),
                    readKeys(o.optJSONArray("keys"))
                ).let {
                    StyleJson.withLook(it, o.optJSONObject("look"))
                        .copy(tracks = TracksJson.decode(o.optJSONObject("tracks")))
                }
            }
        )
    }

    /** Schema 4: rotation and keyframes for texts/overlays; older files load with defaults. */
    private fun keys(keys: List<TransformKeyframe>) = JSONArray(
        keys.map {
            JSONArray(listOf(it.sourceMs, it.x, it.y, it.scale, it.rotation, it.opacity))
        }
    )

    private fun readKeys(a: JSONArray?) = rows(a).map {
        TransformKeyframe(
            it.getLong(0),
            it.getDouble(1).toFloat(),
            it.getDouble(2).toFloat(),
            it.getDouble(3).toFloat(),
            it.getDouble(4).toFloat(),
            it.getDouble(5).toFloat()
        )
    }
    private fun objects(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) }
    private fun rows(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getJSONArray(it) }
    private fun analysisFile(uri: String) = AtomicFile(
        File(
            root,
            "motion-v2-${MessageDigest.getInstance(
                "SHA-256"
            ).digest(uri.toByteArray()).joinToString("") {
                "%02x".format(it)
            }}.json"
        )
    )
    fun loadAnalysis(uri: String): MotionAnalysis? = runCatching {
        val file = analysisFile(uri)
        if (!file.baseFile.exists()) return null
        val j = JSONObject(file.openRead().bufferedReader().use { it.readText() })
        MotionAnalysis(
            j.getInt("width"),
            j.getInt("height"),
            rows(j.getJSONArray("samples")).map {
                MotionSample(
                    it.getLong(0),
                    Pose(it.getDouble(1), it.getDouble(2), it.getDouble(3)),
                    it.getInt(4)
                )
            },
            j.getInt("unreliable")
        )
    }.getOrNull()
    fun saveAnalysis(uri: String, a: MotionAnalysis) = write(
        analysisFile(uri),
        JSONObject().put(
            "width",
            a.width
        ).put("height", a.height).put("unreliable", a.unreliableFrames)
            .put(
                "samples",
                JSONArray(
                    a.samples.map {
                        JSONArray(
                            listOf(it.timeMs, it.pose.x, it.pose.y, it.pose.angle, it.segment)
                        )
                    }
                )
            ).toString()
    )
    private fun write(file: AtomicFile, text: String) {
        val stream = file.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (
            e: Exception
        ) {
            file.failWrite(stream)
            throw e
        }
    }
}
