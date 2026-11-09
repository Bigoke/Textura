package com.textura.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.textura.app.MainActivity
import com.textura.app.R
import com.textura.app.data.Quotes
import com.textura.app.data.Store
import com.textura.app.model.CanvasShape
import com.textura.app.model.Catalogue
import com.textura.app.render.Renderer
import java.time.LocalDate

/** Home-screen widget: today's quote drawn in one of the user's favorite looks. */
class QuoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val store = Store(context)
        val favorites = Catalogue.all.filter { it.id in store.loadFavorites() } + store.loadCustom().filter { it.id in store.loadFavorites() }
        val pool = favorites.ifEmpty { Catalogue.all }
        val day = LocalDate.now().dayOfYear
        val look = pool[day % pool.size]
        val quote = Quotes.ofTheDay()

        val bitmap = runCatching {
            Renderer.render(context, look, quote.asText(), CanvasShape.Square, 640)
        }.getOrNull()

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_textura)
            if (bitmap != null) views.setImageViewBitmap(R.id.widget_image, bitmap)
            views.setOnClickPendingIntent(R.id.widget_image, open)
            manager.updateAppWidget(id, views)
        }
    }
}
