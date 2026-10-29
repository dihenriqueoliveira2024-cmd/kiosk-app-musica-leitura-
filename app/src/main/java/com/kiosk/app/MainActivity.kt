package com.kiosk.app

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("kiosk", MODE_PRIVATE) }
    private lateinit var raiz: LinearLayout
    private lateinit var barra: LinearLayout
    private lateinit var caixaWidget: FrameLayout
    private lateinit var mgr: AppWidgetManager
    private lateinit var host: AppWidgetHost

    private val MUSICOLET = "in.krosbits.musicolet"
    private val CX = "com.cxinventor.file.explorer"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mgr = AppWidgetManager.getInstance(this)
        host = AppWidgetHost(this, 1024)

        raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 80, 32, 48)
        }
        val hora = TextClock(this).apply {
            format24Hour = "HH:mm"
            format12Hour = "HH:mm"
            textSize = 64f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val data = TextClock(this).apply {
            format24Hour = "EEEE, d 'de' MMMM"
            format12Hour = "EEEE, d 'de' MMMM"
            textSize = 16f
            setTextColor(Color.parseColor("#CCCCCC"))
            gravity = Gravity.CENTER
        }
        caixaWidget = FrameLayout(this)
        barra = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        raiz.addView(hora)
        raiz.addView(data)
        raiz.addView(caixaWidget, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { topMargin = 32; bottomMargin = 16 })
        raiz.addView(barra, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 16 })

        setContentView(raiz)
    }

    override fun onStart() {
        super.onStart()
        host.startListening()
    }

    override fun onStop() {
        super.onStop()
        host.stopListening()
    }

    // ---------- Barra 4x1 ----------
    private fun iconeApp(chave: String, padrao: String): Drawable? {
        val pkg = prefs.getString(chave, padrao) ?: padrao
        return try {
            packageManager.getApplicationIcon(pkg)
        } catch (e: Exception) {
            null
        }
    }

    private fun celula(
        rotulo: String,
        icone: Drawable?,
        emoji: String,
        acao: () -> Unit
    ): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            setOnClickListener { acao() }
        }
        if (icone != null) {
            val iv = ImageView(this).apply {
                setImageDrawable(icone)
                layoutParams = LinearLayout.LayoutParams(140, 140)
            }
            c.addView(iv)
        } else {
            val circulo = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#33FFFFFF"))
            }
            val tv = TextView(this).apply {
                text = emoji
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = circulo
                layoutParams = LinearLayout.LayoutParams(140, 140)
            }
            c.addView(tv)
        }
        val nome = TextView(this).apply {
            text = rotulo
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }
        c.addView(nome)
        return c
    }

    private fun montarBarra() {
        barra.removeAllViews()
        barra.addView(celula("Música", iconeApp("musica", MUSICOLET), "♪") { abrirMusica() })
        barra.addView(celula("Arquivos", iconeApp("arquivos", CX), "📁") { abrirArquivos() })
        barra.addView(celula("Leitura", null, "📖") {
            Toast.makeText(this, "Modo leitura em breve", Toast.LENGTH_SHORT).show()
        })
        barra.addView(celula("Configurar", null, "⚙") { abrirConfig() })
    }

    private fun aplicarCor() {
        raiz.setBackgroundColor(Color.parseColor(prefs.getString("cor", "#000000")))
    }

    // ---------- Widget ----------
    private fun dica(texto: String) {
        val t = TextView(this).apply {
            text = texto
            textSize = 14f
            setTextColor(Color.parseColor("#999999"))
            gravity = Gravity.CENTER
        }
        caixaWidget.addView(t, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    private fun mostrarWidget() {
        caixaWidget.removeAllViews()
        val id = prefs.getInt("widgetId", -1)
        if (id == -1) {
            dica("Sem widget.\nToque em Configurar → Widget da música.")
            return
        }
        val info = mgr.getAppWidgetInfo(id)
        if (info == null) {
            prefs.edit().remove("widgetId").apply()
            dica("O widget sumiu.\nEscolha de novo em Configurar.")
            return
        }
        val v = host.createView(this, id, info)
        v.setAppWidget(id, info)
        caixaWidget.addView(v, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        caixaWidget.post {
            val d = resources.displayMetrics.density
            val w = (caixaWidget.width / d).toInt()
            val h = (caixaWidget.height / d).toInt()
            v.updateAppWidgetSize(null, w, h, w, h)
        }
    }

    // ---------- Apps ----------
    private fun abrirConfig() {
        startActivity(Intent(this, ConfigActivity::class.java))
    }

    private fun abrirPacote(pkg: String): Boolean {
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        startActivity(i)
        return true
    }

    private fun abrirMusica() {
        val pkg = prefs.getString("musica", MUSICOLET)!!
        if (!abrirPacote(pkg)) {
            Toast.makeText(this, "Escolha o app de música", Toast.LENGTH_SHORT).show()
            abrirConfig()
        }
    }

    private fun abrirArquivos() {
        val pkg = prefs.getString("arquivos", CX)!!
        if (!abrirPacote(pkg)) {
            Toast.makeText(this, "Escolha o app de arquivos", Toast.LENGTH_SHORT).show()
            abrirConfig()
        }
    }

    override fun onResume() {
        super.onResume()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
        aplicarCor()
        montarBarra()
        mostrarWidget()
    }
}
