package com.womansafe.womanapp.data

data class User(
    val uid: String = "",
    val email: String = "",
    val name: String = "nothing",
    val emergencyContacts: List<String> = emptyList(),
    val alertStatus: Boolean = false,
    val lastLocation: MapLocation? = null
)

data class MapLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)
