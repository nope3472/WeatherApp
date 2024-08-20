package eu.tutorials.weatherapp

import android.location.Location
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocationViewModel : ViewModel() {
    private val _weatherData = MutableStateFlow<WeatherResponse?>(null)
    val weatherData: StateFlow<WeatherResponse?> = _weatherData

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location

    fun updateWeatherData(data: WeatherResponse) {
        _weatherData.value = data
        _error.value = null
    }

    fun setError(message: String) {
        _error.value = message
    }

    fun updateLocation(location: Location) {
        _location.value = location
    }
}