package eu.tutorials.weatherapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import eu.tutorials.weatherapp.network.WeatherService
import eu.tutorials.weatherapp.ui.theme.WeatherAppTheme
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val viewModel: LocationViewModel by viewModels()

    private var showPermissionDialog by mutableStateOf(false)

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val locationPermissionGranted = permissions.entries.any {
                it.key == Manifest.permission.ACCESS_FINE_LOCATION && it.value ||
                        it.key == Manifest.permission.ACCESS_COARSE_LOCATION && it.value
            }

            if (locationPermissionGranted) {
                startLocationUpdates()
            } else {
                showPermissionDialog = true
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    startLocationUpdates()
                }
            }
        }

        setContent {
            WeatherAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WeatherDisplay(viewModel)
                    if (showPermissionDialog) {
                        PermissionDialog(
                            onDismiss = { showPermissionDialog = false },
                            onGoToSettings = {
                                showPermissionDialog = false
                                openAppSettings()
                            }
                        )
                    }
                }
            }
        }

        checkLocationPermissions()
    }

    private fun checkLocationPermissions() {
        if (hasLocationPermissions()) {
            startLocationUpdates()
        } else {
            requestLocationPermissions()
        }
    }

    private fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(5000)
            .setMaxUpdateDelayMillis(10000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    Log.d("MainActivity", "New location received: ${location.latitude}, ${location.longitude}")
                    viewModel.updateLocation(location)
                    fetchWeatherData(location.latitude, location.longitude)
                }
            }
        }

        if (hasLocationPermissions()) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    mainLooper
                )
                Log.d("MainActivity", "Location updates requested")
            } catch (unlikely: SecurityException) {
                Log.e("MainActivity", "Lost location permission. Could not request updates. $unlikely")
            }
        }
    }

    private fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            startActivity(this)
        }
    }

    private fun fetchWeatherData(latitude: Double, longitude: Double) {
        if (Constants.isNetworkAvailable(this)) {
            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.Base_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val service: WeatherService = retrofit.create(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude,
                longitude,
                Constants.APP_ID,
                Constants.METRIC_UNIT
            )

            // Log the URL being called
            Log.d("MainActivity", "API URL: ${listCall.request().url}")
            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                    if (response.isSuccessful) {
                        val weatherData = response.body()
                        weatherData?.let {
                            viewModel.updateWeatherData(it)
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        val errorMessage = try {
                            val jsonObject = JSONObject(errorBody)
                            when (jsonObject.getInt("cod")) {
                                401 -> "Invalid API key. Please check your API key and try again."
                                else -> jsonObject.getString("message")
                            }
                        } catch (e: Exception) {
                            "Failed to fetch weather data. Please try again later."
                        }
                        Log.e("MainActivity", "Error: $errorBody")
                        viewModel.setError(errorMessage)
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("MainActivity", "Error in API call", t)
                    viewModel.setError("Network error. Please check your internet connection and try again.")
                }
            })
        } else {
            viewModel.setError("No internet connection available. Please check your network settings.")
        }
    }

    override fun onResume() {
        super.onResume()
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onPause() {
        super.onPause()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}

@Composable
fun WeatherDisplay(viewModel: LocationViewModel) {
    val weatherData by viewModel.weatherData.collectAsState()
    val error by viewModel.error.collectAsState()
    val location by viewModel.location.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        when {
            error != null -> {
                ErrorDisplay(error ?: "An error occurred")
            }
            weatherData != null -> {
                WeatherContent(weatherData!!, location)
            }
            else -> {
                LoadingDisplay()
            }
        }
    }
}

@Composable
fun WeatherContent(data: WeatherResponse, location: Location?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LocationHeader(data.name, location)
        Spacer(modifier = Modifier.height(24.dp))
        MainWeatherInfo(data)
        Spacer(modifier = Modifier.height(24.dp))
        WeatherDetails(data)
    }
}

@Composable
fun LocationHeader(cityName: String, location: Location?) {
    Text(
        text = cityName,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold
    )
    location?.let {
        Text(
            text = "Lat: ${it.latitude.format(2)}, Lon: ${it.longitude.format(2)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MainWeatherInfo(data: WeatherResponse) {
    Text(
        text = "${data.main.temp.roundToInt()}°C",
        style = MaterialTheme.typography.displayLarge,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = data.weather.firstOrNull()?.description?.capitalize() ?: "",
        style = MaterialTheme.typography.headlineSmall
    )
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WeatherInfoCard("Humidity", "${data.main.humidity}%")
        WeatherInfoCard("Wind Speed", "${data.wind.speed} m/s")
    }
}

@Composable
fun WeatherInfoCard(title: String, value: String) {
    Card(
        modifier = Modifier.width(150.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun WeatherDetails(data: WeatherResponse) {
    Text(
        text = "Detailed Forecast",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Pressure: ${data.main.pressure} hPa",
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        text = "Visibility: ${data.visibility / 1000} km",
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
fun ErrorDisplay(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun LoadingDisplay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun PermissionDialog(
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Location Permission Required") },
        text = { Text("This app requires location access to provide weather information. Please grant permission in the app settings.") },
        confirmButton = {
            TextButton(onClick = onGoToSettings) {
                Text("Go to Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)
