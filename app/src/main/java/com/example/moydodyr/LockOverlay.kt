package com.example.moydodyr

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.*
import java.time.LocalDate
import java.time.LocalTime

object Slot {
    fun current(): String {
        val now=LocalTime.now()
        val date=if(now.hour<10) LocalDate.now().minusDays(1) else LocalDate.now()
        val hour=when { now.hour>=20 -> 20; now.hour>=15 -> 15; now.hour>=10 -> 10; else -> 20 }
        return "$date-$hour"
    }
}

class LockOverlay: Service() {
    private var overlay: View?=null
    private lateinit var wm: WindowManager
    companion object {
        fun showIfNeeded(c: Context) {
            val p=c.getSharedPreferences("state",0)
            if (!p.getBoolean("pending",false) || p.getString("completed","")==Slot.current()) return
            if (!Settings.canDrawOverlays(c)) return
            try { c.stopService(Intent(c,LockOverlay::class.java)); c.startService(Intent(c,LockOverlay::class.java)) } catch (_:Exception) { }
        }
    }
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onCreate() {
        super.onCreate()
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        val channel="overlay"
        val nm=getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(channel,"Учебная блокировка",NotificationManager.IMPORTANCE_LOW))
        val pi=PendingIntent.getActivity(this,3,Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(11,Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("Мойдодыр: время читать").setContentIntent(pi).build())
        if(!Settings.canDrawOverlays(this)) { stopSelf();return }
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(32,32,32,32);setBackgroundColor(Color.rgb(250,248,240)) }
        val heading=TextView(this).apply { text="Мойдодыр — время читать";textSize=26f;setTextColor(Color.BLACK);gravity=Gravity.CENTER }
        val poem=TextView(this).apply { text=Verses.current(this@LockOverlay);textSize=24f;setTextColor(Color.BLACK);gravity=Gravity.CENTER;setPadding(0,30,0,30) }
        val button=Button(this).apply { text="Начать чтение";setOnClickListener { startActivity(Intent(this@LockOverlay,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); stopSelf() } }
        root.addView(heading);root.addView(poem);root.addView(button)
        val params=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,PixelFormat.TRANSLUCENT)
        try { wm.addView(root,params);overlay=root } catch (_:Exception) { stopSelf() }
    }
    override fun onDestroy() { overlay?.let { try { wm.removeView(it) } catch (_:Exception) {} };overlay=null;super.onDestroy() }
}
