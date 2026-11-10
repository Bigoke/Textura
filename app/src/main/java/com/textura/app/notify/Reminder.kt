package com.textura.app.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.textura.app.MainActivity
import com.textura.app.R
import com.textura.app.data.Quotes
import com.textura.app.data.Store
import java.util.Calendar

/** A once-a-day nudge with the quote of the day; tapping it opens the app with that quote ready to design. */
object Reminder {
    private const val CHANNEL = "daily_quote"
    private const val ACTION = "com.textura.app.DAILY_QUOTE"

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 77, Intent(context, ReminderReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(context: Context, hour: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC, at.timeInMillis, AlarmManager.INTERVAL_DAY, alarmIntent(context))
    }

    fun cancel(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarmIntent(context))
    }

    fun show(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Daily quote", NotificationManager.IMPORTANCE_DEFAULT))
        val quote = Quotes.ofTheDay()
        val open = PendingIntent.getActivity(
            context, 78,
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, quote.asText()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Today's quote")
            .setContentText(quote.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(quote.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(1, n) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val s = Store(context).loadSettings()
        if (!s.dailyReminder) return
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminder.schedule(context, s.dailyHour) else Reminder.show(context)
    }
}
