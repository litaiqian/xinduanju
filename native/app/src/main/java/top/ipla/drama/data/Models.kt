package top.ipla.drama.data

import com.google.gson.annotations.SerializedName

data class Drama(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("title") val title: String = "",
    @SerializedName("cover") val cover: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("category") val category: String = "",
    @SerializedName("episode_count") val episodeCount: Int = 0,
    @SerializedName("score") val score: Float = 0f
)

data class Episode(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("drama_id") val dramaId: Int = 0,
    @SerializedName("title") val title: String = "",
    @SerializedName("video_url") val videoUrl: String = "",
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("order") val order: Int = 0
)

data class DramaDetail(
    @SerializedName("drama") val drama: Drama,
    @SerializedName("episodes") val episodes: List<Episode> = emptyList()
)

data class DramaListResponse(
    @SerializedName("status") val status: String = "",
    @SerializedName("data") val data: List<Drama> = emptyList()
)

data class DramaDetailResponse(
    @SerializedName("status") val status: String = "",
    @SerializedName("data") val data: DramaDetail? = null
)

data class UserInfo(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("username") val username: String = "",
    @SerializedName("points") val points: Int = 0,
    @SerializedName("signed_today") val signedToday: Boolean = false
)

data class LoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class LoginResponse(
    @SerializedName("status") val status: String = "",
    @SerializedName("token") val token: String = "",
    @SerializedName("user") val user: UserInfo? = null,
    @SerializedName("message") val message: String = ""
)

data class HistoryItem(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("drama_id") val dramaId: Int = 0,
    @SerializedName("episode_id") val episodeId: Int = 0,
    @SerializedName("drama_title") val dramaTitle: String = "",
    @SerializedName("episode_title") val episodeTitle: String = "",
    @SerializedName("cover") val cover: String = "",
    @SerializedName("progress") val progress: Long = 0,
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("watched_at") val watchedAt: String = ""
)

data class CheckInResponse(
    @SerializedName("status") val status: String = "",
    @SerializedName("points_earned") val pointsEarned: Int = 0,
    @SerializedName("total_points") val totalPoints: Int = 0,
    @SerializedName("message") val message: String = ""
)
