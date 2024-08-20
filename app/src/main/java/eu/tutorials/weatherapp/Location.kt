package eu.tutorials.weatherapp

import android.content.Context
import android.location.LocationManager

 fun isLocationEnabled(context: Context): Boolean {
    // This provides access to the system location services.
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}
