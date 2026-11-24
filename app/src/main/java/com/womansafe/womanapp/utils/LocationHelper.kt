package com.womansafe.womanapp.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.womansafe.womanapp.data.MapLocation

class LocationHelper(private val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(onLocationResult: (MapLocation?) -> Unit) {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    onLocationResult(MapLocation(location.latitude, location.longitude))
                } else {
                    onLocationResult(null)
                }
            }
            .addOnFailureListener {
                onLocationResult(null)
            }
    }
}
