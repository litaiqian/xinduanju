package top.ipla.drama.data

import retrofit2.http.*

interface ApiService {
    @GET("api/drama/list")
    suspend fun getDramaList(
        @Query("category") category: String = ""
    ): DramaListResponse

    @GET("api/drama/detail/{id}")
    suspend fun getDramaDetail(
        @Path("id") id: Int
    ): DramaDetailResponse

    @POST("api/drama/user/register")
    suspend fun register(
        @Body body: LoginRequest
    ): LoginResponse

    @POST("api/drama/user/login")
    suspend fun login(
        @Body body: LoginRequest
    ): LoginResponse

    @GET("api/drama/user/info")
    suspend fun getUserInfo(
        @Header("Authorization") token: String
    ): LoginResponse

    @POST("api/drama/user/checkin")
    suspend fun checkIn(
        @Header("Authorization") token: String
    ): CheckInResponse

    @GET("api/history/list")
    suspend fun getHistory(
        @Header("Authorization") token: String = ""
    ): DramaListResponse

    @POST("api/history/save")
    suspend fun saveHistory(
        @Header("Authorization") token: String = "",
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): LoginResponse
}
