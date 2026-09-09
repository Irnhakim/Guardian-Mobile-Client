package id.irnhakim.guardian.data.remote.dto

data class BrowsingHistoryRequest(
    val url: String,
    val title: String? = null,
    val browser: String? = null
)
