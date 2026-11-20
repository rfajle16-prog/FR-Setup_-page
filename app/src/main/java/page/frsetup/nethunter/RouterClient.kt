package page.frsetup.nethunter

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL

data class SysToolsInfo(
    val hasLoginPwd: Boolean,
    val remoteWebEn: Boolean,
    val remoteWebPort: String,
    val remoteWebType: String,
    val rawJson: String
)

class RouterClient(private val context: Context) {

    @Suppress("DEPRECATION")
    fun findWifiNetwork(): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        for (net in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(net)
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return net
            }
        }
        return null
    }

    fun detectGatewayIp(wifiNet: Network): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val lp = cm.getLinkProperties(wifiNet) ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            lp.dhcpServerAddress?.hostAddress?.let { return it }
        }
        for (route in lp.routes) {
            if (route.isDefaultRoute && route.gateway is Inet4Address) {
                return route.gateway?.hostAddress
            }
        }
        for (la in lp.linkAddresses) {
            val addr = la.address
            if (addr is Inet4Address) {
                val parts = addr.hostAddress?.split(".")
                if (parts != null && parts.size == 4) {
                    return "${parts[0]}.${parts[1]}.${parts[2]}.1"
                }
            }
        }
        return null
    }

    fun fetchSysTools(wifiNet: Network, gatewayIp: String): Result<SysToolsInfo> {
        return try {
            val endpoint = "http://$gatewayIp/goform/getSysTools?modules=remoteWeb,loginAuth"
            val conn = wifiNet.openConnection(URL(endpoint)) as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.instanceFollowRedirects = true

            val code = conn.responseCode
            val body = conn.inputStream.bufferedReader().use { it.readText() }.trim()
            conn.disconnect()

            if (code != 200) {
                return Result.failure(Exception("HTTP $code পাওয়া গেছে"))
            }

            if (!body.startsWith("{")) {
                return Result.failure(Exception("রাউটারে পাসওয়ার্ড সেশন সক্রিয় রয়েছে (HTML ফেরত এসেছে)"))
            }

            val json = JSONObject(body)
            val remoteObj = json.optJSONObject("remoteWeb") ?: JSONObject()
            val loginObj = json.optJSONObject("loginAuth") ?: JSONObject()

            val info = SysToolsInfo(
                hasLoginPwd = loginObj.optString("hasLoginPwd") == "true",
                remoteWebEn = remoteObj.optString("remoteWebEn") == "true",
                remoteWebPort = remoteObj.optString("remoteWebPort", "80"),
                remoteWebType = remoteObj.optString("remoteWebType", "any"),
                rawJson = body
            )
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
