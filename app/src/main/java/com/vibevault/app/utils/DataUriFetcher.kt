package com.vibevault.app.utils

import android.util.Base64
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer

/**
 * DataUriFetcher — Custom Coil Fetcher to render `data:image/...;base64,...` URIs.
 */
class DataUriFetcher(
    private val data: String,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val commaIndex = data.indexOf(',')
        if (commaIndex == -1) return null

        val header = data.substring(0, commaIndex)
        val base64Data = data.substring(commaIndex + 1)

        val bytes = try {
            Base64.decode(base64Data, Base64.DEFAULT)
        } catch (e: Exception) {
            return null
        }

        val mimeType = header.substringAfter("data:").substringBefore(";").takeIf { it.isNotBlank() } ?: "image/jpeg"
        val buffer = Buffer().write(bytes)

        return SourceResult(
            source = ImageSource(source = buffer, context = options.context),
            mimeType = mimeType,
            dataSource = DataSource.MEMORY
        )
    }

    class Factory : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.startsWith("data:image/")) {
                return DataUriFetcher(data, options)
            }
            return null
        }
    }
}
