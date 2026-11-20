package page.frsetup.nethunter

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var statusCard: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var routerClient: RouterClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routerClient = RouterClient(this)

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

        val actionBtn = Button(this).apply {
            text = "রাউটার রিড-ব্যাক ও স্ট্যাটাস চেক করুন"
            setBackgroundColor(Color.parseColor("#00BCD4"))
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            setOnClickListener { checkRouterSysTools() }
        }

        statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#131E32"))
            setPadding(32, 32, 32, 32)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 32, 0, 0)
            }
        }

        statusText = TextView(this).apply {
            text = "প্রস্তুত। Wi-Fi-তে কানেক্ট করে উপরের বাটনে চাপুন।"
            textSize = 14f
            setTextColor(Color.parseColor("#E2E8F0"))
            setLineSpacing(8f, 1f)
        }
        statusCard.addView(statusText)

        val scroll = ScrollView(this).apply {
            addView(statusCard)
        }

        root.addView(logo)
        root.addView(title)
        root.addView(subtitle)
        root.addView(actionBtn)
        root.addView(scroll)
        setContentView(root)
    }

    private fun checkRouterSysTools() {
        statusText.text = "⏳ Wi-Fi নেটওয়ার্ক ও গেটওয়ে খুঁজছে..."
        thread {
            val wifiNet = routerClient.findWifiNetwork()
            if (wifiNet == null) {
                runOnUiThread { statusText.text = "❌ কোনো Wi-Fi নেটওয়ার্ক পাওয়া যায়নি।" }
                return@thread
            }

            val gw = routerClient.detectGatewayIp(wifiNet)
            if (gw == null) {
                runOnUiThread { statusText.text = "❌ Gateway IP শনাক্ত করা যায়নি।" }
                return@thread
            }

            runOnUiThread { statusText.text = "🌐 Gateway: $gw\n⏳ রাউটার কনফিগারেশন রিড করা হচ্ছে (SysTools)..." }

            val result = routerClient.fetchSysTools(wifiNet, gw)
            runOnUiThread {
                result.onSuccess { info ->
                    val remoteStatus = if (info.remoteWebEn) "✅ চালু (Port: ${info.remoteWebPort})" else "❌ বন্ধ (Port: ${info.remoteWebPort})"
                    val pwdStatus = if (info.hasLoginPwd) "🔐 সেট করা আছে" else "⚠️ সেট করা নেই (ডিফল্ট)"

                    val report = StringBuilder().apply {
                        append("━━━━━━ রাউটার রিড-ব্যাক রিপোর্ট ━━━━━━\n\n")
                        append("🏷️ Gateway IP: $gw\n")
                        append("🛠️ Remote Management: $remoteStatus\n")
                        append("🔑 অ্যাডমিন পাসওয়ার্ড: $pwdStatus\n\n")
                        append("--- Raw JSON Output ---\n")
                        append(info.rawJson)
                    }.toString()

                    statusText.text = report
                }.onFailure { err ->
                    statusText.text = "🌐 Gateway: $gw\n❌ ত্রুটি: ${err.message}"
                }
            }
        }
    }
}
