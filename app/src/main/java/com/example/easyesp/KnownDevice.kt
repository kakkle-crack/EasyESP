package com.example.easyesp

import java.io.Serializable

data class KnownDevice(
    var deviceName: String,
    var ipAddress: String
) : Serializable