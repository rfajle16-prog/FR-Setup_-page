package page.frsetup.nethunter

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var outputView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B1220"))
            setPadding(40, 64, 40, 40)
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_app_logo)
            layoutParams = LinearLayout.LayoutParams(180, 180).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        val title = TextView(this).apply {
            text = "fr-setup.page"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 16, 0, 4)
        }

        val subtitle = TextView(this).apply {
            text = "NET HUNTER • Router Auto Tool"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 32)
        }

        val testBtn = Button(this).apply {
            text = "রাউটার ও গেটওয়ে চেক করুন"
            setBackgroundColor(Color.parseColor("#00BCD4"))
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            setOnClickListener { runGatewayProbe() }
        }

        outputView = TextView(this).apply {
            text = "প্রস্তুত। Wi-Fi কানেক্ট করে উপরের বাটনে চাপুন।"
            textSize = 14f
            setTextColor(Color.parseColor("#E2E8F0"))
            setPadding(0, 32, 0, 0)
        }

        val scroll = ScrollView(this).apply {
            addView(outputView)
        }

        root.addView(logo)
        root.addView(title)
        root.addView(subtitle)
        root.addView(testBtn)
        root.addView(scroll)
        setContentView(root)
    }

    @Suppress("DEPRECATION")
    private fun findWifiNetwork(cm: ConnectivityManager): Network? {
        for (net in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(net)
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return net
            }
        }
        return null
    }

    private fun detectGateway(cm: ConnectivityManager, wifiNet: Network): String? {
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

    private fun runGatewayProbe() {
        outputView.text = "Wi-Fi গেটওয়ে খুঁজছে..."
        thread {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val wifiNet = findWifiNetwork(cm)
            if (wifiNet == null) {
                runOnUiThread { outputView.text = "❌ কোনো Wi-Fi নেটওয়ার্ক পাওয়া যায়নি।" }
                return@thread
            }
            val gw = detectGateway(cm, wifiNet)
            if (gw == null) {
                runOnUiThread { outputView.text = "❌ Gateway IP শনাক্ত করা যায়নি।" }
                return@thread
            }
            try {
                val conn = wifiNet.openConnection(URL("http://$gw/")) as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.instanceFollowRedirects = true
                val code = conn.responseCode
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val titleMatch = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(body)
                val pageTitle = titleMatch?.groupValues?.get(1)?.trim() ?: "N/A"
                conn.disconnect()
                runOnUiThread {
                    outputView.text = "✅ Wi-Fi নেটওয়ার্ক পাওয়া গেছে\n" +
                        "🌐 Gateway IP: $gw\n" +
                        "📡 HTTP Status: $code\n" +
                        "🏷️ রাউটার পেজ: $pageTitle"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    outputView.text = "🌐 Gateway IP: $gw\n❌ কানেকশন এরর: ${e.message}"
                }
            }
        }
    }
}
