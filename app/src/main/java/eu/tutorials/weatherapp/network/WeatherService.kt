package eu.tutorials.weatherapp.network


import eu.tutorials.weatherapp.WeatherResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherService {

    @GET("2.5/weather")
    fun getWeather(
        @Query("lat")lat:Double,
        @Query("lon")lon:Double,
        @Query("appid")appid :String?,
        @Query("units")units:String?
    ): Call<WeatherResponse>
}