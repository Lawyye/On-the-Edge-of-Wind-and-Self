package com.nutritiontracker.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Есть ли сейчас подключение к интернету. Используется только для подсказок в интерфейсе. */
class ConnectivityMonitor(context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _online = MutableStateFlow(currentlyOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { _online.value = currentlyOnline() }
        override fun onLost(network: Network) { _online.value = currentlyOnline() }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { _online.value = currentlyOnline() }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)
        } catch (_: SecurityException) {
            // Без разрешения ACCESS_NETWORK_STATE считаем, что сеть есть: ошибки запроса покажутся отдельно.
            _online.value = true
        }
    }

    private fun currentlyOnline(): Boolean = try {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        // VALIDATED: сеть действительно выходит в интернет (не страница авторизации Wi-Fi в кафе).
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (_: SecurityException) {
        true
    }
}
