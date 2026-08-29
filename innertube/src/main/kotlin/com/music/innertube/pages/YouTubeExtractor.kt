package com.music.innertube.pages

import com.music.innertube.NewPipeExtractor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.Function as RhinoFunction
import org.mozilla.javascript.Scriptable
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Tier 2 Standalone Rhino-based signature and n-parameter transform deobfuscator.
 */
object YouTubeExtractor {
    private val client = OkHttpClient.Builder().build()
    private val initLock = Any()

    private var cachedPlayerJs: String? = null
    private var deobfuscateJsCode: String? = null
    private var deobfuscateFuncName: String? = null
    private var transformNJsCode: String? = null
    private var transformNFuncName: String? = null

    private var sigScope: Scriptable? = null
    private var sigFunction: RhinoFunction? = null
    private var nScope: Scriptable? = null
    private var nFunction: RhinoFunction? = null
    private val rhinoLock = Any()

    var cacheDir: File? = null

    val isReady: Boolean
        get() = deobfuscateJsCode != null && transformNJsCode != null

    fun ensureInitialized() {
        synchronized(initLock) {
            if (isReady) return

            if (cacheDir != null && loadFromDiskCache()) {
                ensureRhinoCompiled()
                if (isReady) return
            }

            fetchAndParsePlayerJs()
            if (isReady) {
                ensureRhinoCompiled()
                saveToDiskCache()
            }
        }
    }

    private fun fetchAndParsePlayerJs() {
        try {
            val iframeUrl = "https://www.youtube.com/iframe_api"
            val req = Request.Builder().url(iframeUrl).build()
            val resp = client.newCall(req).execute()
            val iframeBody = resp.body?.string().orEmpty()

            val playerJsPathMatch = Regex("/s/player/[a-zA-Z0-9_-]+/player_ias\\.vflset/[a-zA-Z_0-9]+/base\\.js")
                .find(iframeBody)
            val playerJsUrl = if (playerJsPathMatch != null) {
                "https://www.youtube.com${playerJsPathMatch.value}"
            } else {
                "https://www.youtube.com/s/player/03b9ad6d/player_ias.vflset/en_US/base.js"
            }

            val jsReq = Request.Builder().url(playerJsUrl).build()
            val jsResp = client.newCall(jsReq).execute()
            val playerJs = jsResp.body?.string().orEmpty()
            if (playerJs.isBlank()) return

            cachedPlayerJs = playerJs
            parseSigHelper(playerJs)
            parseNTransformHelper(playerJs)
        } catch (_: Exception) {}
    }

    private fun parseSigHelper(playerJs: String) {
        val sigFuncNameMatch = Regex("\\b([a-zA-Z0-9_\\$]+)\\s*=\\s*function\\(\\s*a\\s*\\)\\s*\\{\\s*a\\s*=\\s*a\\.split\\(\\s*\"\"\\s*\\)")
            .find(playerJs) ?: Regex("a\\s*=\\s*a\\.split\\(\\s*\"\"\\s*\\);\\s*([a-zA-Z0-9_\\$]+)\\.").find(playerJs)

        val funcName = sigFuncNameMatch?.groupValues?.get(1) ?: return
        deobfuscateFuncName = funcName

        val helperObjMatch = Regex("var\\s+(" + Regex.escape(funcName) + "|[a-zA-Z0-9_\\$]+)\\s*=\\s*\\{\\s*([a-zA-Z0-9_\\$]+\\s*:\\s*function[\\s\\S]*?)\\};")
            .find(playerJs)

        val helperObjCode = helperObjMatch?.value.orEmpty()
        val mainFuncMatch = Regex("function\\s+" + Regex.escape(funcName) + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?return\\s+a\\.join\\(\"\"\\)\\s*\\}")
            .find(playerJs) ?: Regex("var\\s+" + Regex.escape(funcName) + "\\s*=\\s*function\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?return\\s+a\\.join\\(\"\"\\)\\s*\\}")
            .find(playerJs)

        val mainFuncCode = mainFuncMatch?.value.orEmpty()
        deobfuscateJsCode = "$helperObjCode\n$mainFuncCode"
    }

    private fun parseNTransformHelper(playerJs: String) {
        val nFuncNameMatch = Regex("\\b([a-zA-Z0-9_\\$]+)\\s*=\\s*function\\(\\s*a\\s*\\)\\s*\\{\\s*var\\s+b\\s*=\\s*a\\.split\\(\\s*\"\"\\s*\\)")
            .find(playerJs)

        val funcName = nFuncNameMatch?.groupValues?.get(1) ?: return
        transformNFuncName = funcName

        val mainFuncMatch = Regex("function\\s+" + Regex.escape(funcName) + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?return\\s+b\\.join\\(\"\"\\)\\s*\\}")
            .find(playerJs) ?: Regex("var\\s+" + Regex.escape(funcName) + "\\s*=\\s*function\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?return\\s+b\\.join\\(\"\"\\)\\s*\\}")
            .find(playerJs)

        val funcBody = mainFuncMatch?.value ?: return
        transformNJsCode = funcBody
    }

    private fun ensureRhinoCompiled() {
        synchronized(rhinoLock) {
            if (sigFunction != null && nFunction != null) return

            val cx = RhinoContext.enter()
            cx.optimizationLevel = -1
            try {
                if (deobfuscateJsCode != null && deobfuscateFuncName != null) {
                    val scope = cx.initStandardObjects()
                    cx.evaluateString(scope, deobfuscateJsCode, "sigDeobfuscator", 1, null)
                    sigFunction = scope.get(deobfuscateFuncName, scope) as? RhinoFunction
                    sigScope = scope
                }

                if (transformNJsCode != null && transformNFuncName != null) {
                    val scope = cx.initStandardObjects()
                    cx.evaluateString(scope, transformNJsCode, "nTransform", 1, null)
                    nFunction = scope.get(transformNFuncName, scope) as? RhinoFunction
                    nScope = scope
                }
            } finally {
                RhinoContext.exit()
            }
        }
    }

    fun decryptUrl(signatureCipher: String): String {
        ensureInitialized()

        val params = signatureCipher.split("&").mapNotNull { pair ->
            val parts = pair.split("=")
            if (parts.size >= 2) parts[0] to URLDecoder.decode(parts[1], "UTF-8") else null
        }.toMap()

        val url = params["url"] ?: return ""
        val sig = params["s"] ?: params["sig"] ?: return url

        val decryptedSig = executeRhinoSig(sig)
        if (decryptedSig != null) {
            val sp = params["sp"] ?: "sig"
            val separator = if (url.contains("?")) "&" else "?"
            return "$url$separator$sp=${URLEncoder.encode(decryptedSig, "UTF-8")}"
        }

        return NewPipeExtractor.getStreamUrl(
            com.music.innertube.models.response.PlayerResponse.StreamingData.Format(
                itag = 0,
                url = "",
                mimeType = "",
                bitrate = 0,
                width = null,
                height = null,
                contentLength = null,
                quality = "",
                fps = null,
                qualityLabel = null,
                averageBitrate = null,
                audioQuality = null,
                approxDurationMs = null,
                audioSampleRate = null,
                audioChannels = null,
                loudnessDb = null,
                lastModified = null,
                signatureCipher = signatureCipher,
                cipher = signatureCipher,
                audioTrack = null
            ),
            ""
        ) ?: url
    }

    fun deobfuscateUrlNParam(url: String): String {
        val nParamMatch = Regex("[?&]n=([^&]+)").find(url) ?: return url
        val nVal = nParamMatch.groupValues[1]

        ensureInitialized()
        val transformedN = executeRhinoN(nVal) ?: return url

        return url.replace("n=$nVal", "n=$transformedN")
    }

    private fun executeRhinoSig(sig: String): String? {
        val fn = sigFunction ?: return null
        val scope = sigScope ?: return null

        return synchronized(rhinoLock) {
            val cx = RhinoContext.enter()
            try {
                val res = fn.call(cx, scope, scope, arrayOf(sig))
                res?.toString()
            } catch (_: Exception) {
                null
            } finally {
                RhinoContext.exit()
            }
        }
    }

    private fun executeRhinoN(n: String): String? {
        val fn = nFunction ?: return null
        val scope = nScope ?: return null

        return synchronized(rhinoLock) {
            val cx = RhinoContext.enter()
            try {
                val res = fn.call(cx, scope, scope, arrayOf(n))
                res?.toString()
            } catch (_: Exception) {
                null
            } finally {
                RhinoContext.exit()
            }
        }
    }

    private fun loadFromDiskCache(): Boolean {
        val dir = cacheDir ?: return false
        val cacheFile = File(dir, "yt_deobfuscator_cache.json")
        if (!cacheFile.exists()) return false

        if (System.currentTimeMillis() - cacheFile.lastModified() > 6 * 3600 * 1000L) {
            cacheFile.delete()
            return false
        }

        return try {
            val content = cacheFile.readText()
            val sigMatch = Regex(""""sigCode":"([^"]+)"""").find(content)?.groupValues?.get(1)
            val sigNameMatch = Regex(""""sigName":"([^"]+)"""").find(content)?.groupValues?.get(1)
            val nCodeMatch = Regex(""""nCode":"([^"]+)"""").find(content)?.groupValues?.get(1)
            val nNameMatch = Regex(""""nName":"([^"]+)"""").find(content)?.groupValues?.get(1)

            if (sigMatch != null && sigNameMatch != null && nCodeMatch != null && nNameMatch != null) {
                deobfuscateJsCode = String(android.util.Base64.decode(sigMatch, android.util.Base64.DEFAULT))
                deobfuscateFuncName = sigNameMatch
                transformNJsCode = String(android.util.Base64.decode(nCodeMatch, android.util.Base64.DEFAULT))
                transformNFuncName = nNameMatch
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    private fun saveToDiskCache() {
        val dir = cacheDir ?: return
        val cacheFile = File(dir, "yt_deobfuscator_cache.json")
        val sigEnc = android.util.Base64.encodeToString(deobfuscateJsCode.orEmpty().toByteArray(), android.util.Base64.NO_WRAP)
        val nEnc = android.util.Base64.encodeToString(transformNJsCode.orEmpty().toByteArray(), android.util.Base64.NO_WRAP)
        val json = """{"sigCode":"$sigEnc","sigName":"$deobfuscateFuncName","nCode":"$nEnc","nName":"$transformNFuncName"}"""
        try {
            cacheFile.writeText(json)
        } catch (_: Exception) {}
    }
}
