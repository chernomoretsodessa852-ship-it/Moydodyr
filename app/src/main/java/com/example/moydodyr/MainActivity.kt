package com.example.moydodyr

import android.Manifest
import android.app.*
import android.os.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import android.text.InputType
import android.provider.Settings
import android.net.Uri
import java.security.MessageDigest
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import org.vosk.android.SpeechService
import org.vosk.android.RecognitionListener
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Calendar
import kotlin.math.max

object Verses {
    // Supply legally usable text in assets/verses.txt, one stanza per blank-line-separated block.
    fun all(c: Context): List<String> = try {
        val text = c.openFileInput("verses.txt").bufferedReader().use { it.readText() }
        text.lines().map { it.trim() }.filter { it.isNotBlank() }.chunked(4)
            .filter { it.size == 4 }.map { it.joinToString("\n") }
    } catch (_: Exception) { emptyList() }
    fun current(c: Context): String {
        val a = all(c); if (a.isEmpty()) return "Добавьте четверостишия в assets/verses.txt"
        val slot = when { LocalTime.now().hour >= 20 -> 2; LocalTime.now().hour >= 15 -> 1; LocalTime.now().hour >= 10 -> 0; else -> 2 }
        val day = if (LocalTime.now().hour < 10) LocalDate.now().minusDays(1) else LocalDate.now()
        val n = (day.toEpochDay() - LocalDate.of(2026,11,2).toEpochDay()) * 3 + slot
        return a[Math.floorMod(n, a.size.toLong()).toInt()]
    }
}

object Schedule {
    val hours = intArrayOf(10, 15, 20)
    fun next(c: Context) {
        val alarm = c.getSystemService(AlarmManager::class.java)
        val now = Calendar.getInstance()
        val next = hours.map { h -> Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,h); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0); if (!after(now)) add(Calendar.DAY_OF_YEAR,1) } }.minBy { it.timeInMillis }
        val pi = PendingIntent.getBroadcast(c, 100, Intent(c,AlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (Build.VERSION.SDK_INT >= 31 && !alarm.canScheduleExactAlarms()) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.timeInMillis,pi)
        else alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.timeInMillis,pi)
    }
}
class BootReceiver: android.content.BroadcastReceiver() { override fun onReceive(c: Context,i: Intent) { Schedule.next(c); LockOverlay.showIfNeeded(c) } }
class AlarmReceiver: android.content.BroadcastReceiver() { override fun onReceive(c: Context,i: Intent) {
    c.getSharedPreferences("state",0).edit().putBoolean("pending",true).apply()
    Schedule.next(c)
    LockOverlay.showIfNeeded(c)
    // Android background activity launch restrictions prevent guaranteed immediate foreground display.
    val nm = c.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel("read","Чтение",NotificationManager.IMPORTANCE_HIGH))
    val pi = PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    nm.notify(7, Notification.Builder(c,"read").setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("Время читать Мойдодыра").setContentText("Открой задание").setContentIntent(pi).setAutoCancel(true).build())
} }
class MainActivity: Activity(), RecognitionListener {
    private lateinit var status: TextView
    private var service: SpeechService? = null
    private var model: Model? = null
    private val recognized = StringBuilder()
    private var verseView: TextView? = null
    private var currentSlot = ""
    private val importCode = 77
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != importCode || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val text = contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val count = text.lines().count { it.isNotBlank() } / 4
            if (count == 0) { Toast.makeText(this,"Нужно хотя бы четыре строки",Toast.LENGTH_LONG).show();return }
            openFileOutput("verses.txt",MODE_PRIVATE).bufferedWriter().use { it.write(text) }
            verseView?.text = Verses.current(this)
            Toast.makeText(this,"Загружено четверостиший: $count",Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(this,"Ошибка импорта: ${e.message}",Toast.LENGTH_LONG).show() }
    }
    private fun slot(): String = Slot.current()
    private fun hash(pin: String): String = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun pinDialog(setup: Boolean = false) {
        val entry = EditText(this).apply { hint = if(setup) "Новый PIN (не менее 6 цифр)" else "Родительский PIN"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        AlertDialog.Builder(this).setTitle(if(setup) "Установить родительский PIN" else "Родительский доступ")
            .setView(entry).setNegativeButton("Отмена",null).setPositiveButton("OK") { _, _ ->
                val input=entry.text.toString()
                val prefs=getSharedPreferences("state",0)
                if(setup) {
                    if(input.length >= 6 && input.all { it.isDigit() }) { prefs.edit().putString("pin_hash",hash(input)).apply(); Toast.makeText(this,"PIN установлен",Toast.LENGTH_LONG).show() }
                    else { Toast.makeText(this,"Нужно минимум 6 цифр",Toast.LENGTH_LONG).show(); pinDialog(true) }
                } else if(hash(input)==prefs.getString("pin_hash",null)) { prefs.edit().putString("completed",slot()).putBoolean("pending",false).apply(); stopService(Intent(this,LockOverlay::class.java)); finish() }
                else Toast.makeText(this,"Неверный PIN",Toast.LENGTH_LONG).show()
            }.show()
    }
    override fun onCreate(b: Bundle?) { super.onCreate(b)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Schedule.next(this)
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this).setTitle("Разрешение поверх других окон")
                .setMessage("Разрешите Мойдодыру показываться поверх других приложений. Без этого автоматическая блокировка не сработает.")
                .setPositiveButton("Открыть настройки") { _, _ -> startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
                .setNegativeButton("Позже",null).show()
        }
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(36,60,36,30); gravity=Gravity.CENTER; setBackgroundColor(Color.WHITE) }
        val title=TextView(this).apply { text="Мойдодыр — прочитай вслух"; textSize=26f; setTextColor(Color.BLACK); gravity=Gravity.CENTER }
        val verse=TextView(this).apply { text=Verses.current(this@MainActivity); textSize=25f; setTextColor(Color.BLACK); gravity=Gravity.CENTER; setPadding(0,35,0,35) }
        status=TextView(this).apply { text="Нажми кнопку один раз"; textSize=18f; gravity=Gravity.CENTER; setTextColor(Color.DKGRAY) }
        val start=Button(this).apply { text="Начать чтение"; setOnClickListener { startReading() } }
        verseView=verse
        val import=Button(this).apply { text="Загрузить TXT со стихотворением"; setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE);type="text/plain" },importCode)
        } }
        val parent=Button(this).apply { text="Для родителей"; setOnClickListener { pinDialog() } }
        val help=TextView(this).apply { text="Чтение без интернета. Для работы необходима локальная модель русского языка. Отключите ограничение батареи для приложения."; textSize=13f; setTextColor(Color.GRAY) }
        if(getSharedPreferences("state",0).getString("pin_hash",null)==null) {
            root.post { pinDialog(true) }
        }
        root.addView(title);root.addView(verse);root.addView(start);root.addView(status);root.addView(parent);root.addView(import);root.addView(help);setContentView(root)
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),1)
    }
    private fun startReading() {
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),1); return }
        recognized.clear(); status.text="Подготовка автономного распознавания…"
        if(model != null) { startService(); return }
        StorageService.unpack(this,"model-ru","model",{ m -> model=m; startService() }, { e -> status.text="Модель не установлена: ${e.message}" })
    }
    private fun startService() {
        service?.shutdown()
        val m=model ?: return
        service=SpeechService(Recognizer(m,16000f),16000f).also { it.startListening(this) }
        status.text="Слушаю тебя… читай все четыре строки"
    }
    private fun evaluate() {
        val expected=words(Verses.current(this))
        val actual=words(recognized.toString())
        // LCS measures word matches in reading order, including repeated words.
        val dp=IntArray(actual.size+1)
        for(word in expected) {
            var previous=0
            for(j in 1..actual.size) {
                val old=dp[j]
                dp[j]=if(word==actual[j-1]) previous+1 else maxOf(dp[j],dp[j-1])
                previous=old
            }
        }
        val ratio=if(expected.isEmpty()) 0.0 else dp.last().toDouble()/expected.size
        if(ratio>=0.8) {
            getSharedPreferences("state",0).edit().putString("completed",slot()).putBoolean("pending",false).apply()
            stopService(Intent(this,LockOverlay::class.java))
            status.text="Молодец! Телефон разблокирован"
            service?.shutdown();service=null
            finish()
        } else status.text="Слушаю тебя… продолжай читать (распознано ${(ratio*100).toInt()}%)"
    }
    private fun words(text:String):List<String> = text.lowercase().split(Regex("[^а-яё]+" )).filter { it.isNotBlank() }
    override fun onPartialResult(hypothesis: String?) {}
    override fun onResult(hypothesis: String?) { append(hypothesis); evaluate() }
    override fun onFinalResult(hypothesis: String?) { append(hypothesis); evaluate() }
    private fun append(h: String?) { if(h!=null) try { recognized.append(' ').append(JSONObject(h).optString("text")) } catch (_:Exception){} }
    override fun onError(exception: Exception?) { status.text="Ошибка распознавания: ${exception?.message}" }
    override fun onTimeout() { status.text="Время прослушивания истекло. Нажми «Начать чтение» ещё раз"; service?.shutdown(); service=null }
    override fun onResume() { super.onResume(); if(getSharedPreferences("state",0).getString("completed","")==slot()) finish() }
    override fun onDestroy() { service?.shutdown();model?.close();super.onDestroy() }
}
