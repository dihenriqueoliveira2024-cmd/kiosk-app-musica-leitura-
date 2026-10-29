package com.kiosk.app

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("kiosk", MODE_PRIVATE) }
    private lateinit var raiz: LinearLayout
    private lateinit var caixaWidget: FrameLayout
    private lateinit var mgr: AppWidgetManager
    private lateinit var host: AppWidgetHost
    private var pendente = -1

    private val MUSICOLET = "in.krosbits.musicolet"
    private val CX = "com.cxinventor.file.explorer"

    private val cores = listOf(
        "Preto" to "#000000",
        "Azul-noite" to "#0B1B33",
        "Verde-escuro" to "#0F2A1D",
        "Sépia" to "#3B2F22",
        "Cinza" to "#2B2B2B",
        "Roxo" to "#2A1B3D",
        "Vinho" to "#3A1420",
        "Grafite" to "#1C2833"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mgr = AppWidgetManager.getInstance(this)
        host = AppWidgetHost(this, 1024)

        raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 80, 48, 48)
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
        raiz.addView(hora)
        raiz.addView(data)
        raiz.addView(caixaWidget, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { topMargin = 32; bottomMargin = 16 })
        raiz.addView(botao("♪  Música") { abrirMusica() })
        raiz.addView(botao("📁  Arquivos") { abrirArquivos() })
        raiz.addView(botao("⚙  Configurar") { menuConfig() })

        setContentView(raiz)
        aplicarCor()
        mostrarWidget()
    }

    override fun onStart() {
        super.onStart()
        host.startListening()
    }

    override fun onStop() {
        super.onStop()
        host.stopListening()
    }

    private fun botao(texto: String, acao: () -> Unit): Button {
        val fundo = GradientDrawable().apply {
            cornerRadius = 48f
            setColor(Color.parseColor("#33FFFFFF"))
        }
        return Button(this).apply {
            text = texto
            textSize = 18f
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = fundo
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 150
            ).apply { topMargin = 24 }
            setOnClickListener { acao() }
        }
    }

    private fun aplicarCor() {
        raiz.setBackgroundColor(Color.parseColor(prefs.getString("cor", "#000000")))
    }

    // ---------- Widget ----------
    private fun mostrarWidget() {
        caixaWidget.removeAllViews()
        val id = prefs.getInt("widgetId", -1)
        if (id == -1) return
        val info = mgr.getAppWidgetInfo(id) ?: return
        val v = host.createView(this, id, info)
        v.setAppWidget(id, info)
        caixaWidget.addView(v)
    }

    private fun removerWidget() {
        val id = prefs.getInt("widgetId", -1)
        if (id != -1) host.deleteAppWidgetId(id)
        prefs.edit().remove("widgetId").apply()
        mostrarWidget()
    }

    private fun escolherWidget() {
        val pkgMusica = prefs.getString("musica", MUSICOLET)
        var lista = mgr.installedProviders.filter { it.provider.packageName == pkgMusica }
        if (lista.isEmpty()) lista = mgr.installedProviders
        val nomes = lista.map { it.loadLabel(packageManager) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Escolha o widget")
            .setItems(nomes) { _, w ->
                removerWidget()
                val id = host.allocateAppWidgetId()
                pendente = id
                val provider = lista[w].provider
                if (mgr.bindAppWidgetIdIfAllowed(id, provider)) {
                    configurarOuSalvar(id)
                } else {
                    val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                    startActivityForResult(i, 1)
                }
            }.show()
    }

    private fun configurarOuSalvar(id: Int) {
        val info = mgr.getAppWidgetInfo(id)
        if (info?.configure != null) {
            host.startAppWidgetConfigureActivityForResult(this, id, 0, 2, null)
        } else {
            salvarWidget(id)
        }
    }

    private fun salvarWidget(id: Int) {
        prefs.edit().putInt("widgetId", id).apply()
        mostrarWidget()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 || requestCode == 2) {
            if (resultCode == Activity.RESULT_OK && pendente != -1) {
                if (requestCode == 1) configurarOuSalvar(pendente) else salvarWidget(pendente)
            } else if (pendente != -1) {
                host.deleteAppWidgetId(pendente)
            }
        }
    }

    // ---------- Apps ----------
    private fun abrirPacote(pkg: String): Boolean {
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        startActivity(i)
        return true
    }

    private fun abrirMusica() {
        val pkg = prefs.getString("musica", MUSICOLET)!!
        if (!abrirPacote(pkg)) {
            Toast.makeText(this, "Escolha o app de música", Toast.LENGTH_SHORT).show()
            escolherApp("musica")
        }
    }

    private fun abrirArquivos() {
        val pkg = prefs.getString("arquivos", CX)!!
        if (!abrirPacote(pkg)) {
            Toast.makeText(this, "Escolha o app de arquivos", Toast.LENGTH_SHORT).show()
            escolherApp("arquivos")
        }
    }

    private fun menuConfig() {
        val itens = arrayOf(
            "Cor de fundo", "Widget da música", "Remover widget",
            "App de música", "App de arquivos"
        )
        AlertDialog.Builder(this)
            .setTitle("Configurar")
            .setItems(itens) { _, w ->
                when (w) {
                    0 -> escolherCor()
                    1 -> escolherWidget()
                    2 -> removerWidget()
                    3 -> escolherApp("musica")
                    4 -> escolherApp("arquivos")
                }
            }.show()
    }

    private fun escolherCor() {
        AlertDialog.Builder(this)
            .setTitle("Cor de fundo")
            .setItems(cores.map { it.first }.toTypedArray()) { _, w ->
                prefs.edit().putString("cor", cores[w].second).apply()
                aplicarCor()
            }.show()
    }

    private fun escolherApp(chave: String) {
        val filtro = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(filtro, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        val nomes = apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Escolha o app")
            .setItems(nomes) { _, w ->
                prefs.edit().putString(chave, apps[w].activityInfo.packageName).apply()
            }.show()
    }

    override fun onResume() {
        super.onResume()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }
}
