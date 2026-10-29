package com.kiosk.app

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("kiosk", MODE_PRIVATE) }
    private lateinit var raiz: LinearLayout
    private lateinit var barra: LinearLayout
    private lateinit var listaWidgets: LinearLayout
    private lateinit var mgr: AppWidgetManager
    private lateinit var host: AppWidgetHost
    private var pendente = -1

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
            setOnLongClickListener { menuTela(); true }
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
        listaWidgets = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setOnLongClickListener { menuTela(); true }
        }
        val rolagem = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            addView(listaWidgets, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            setOnLongClickListener { menuTela(); true }
        }
        barra = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        raiz.addView(hora)
        raiz.addView(data)
        raiz.addView(rolagem, LinearLayout.LayoutParams(
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
            setOnLongClickListener { menuTela(); true }
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

    // ---------- Widgets (como um launcher normal) ----------
    private fun ids(): MutableList<Int> =
        (prefs.getString("widgets", "") ?: "")
            .split(",").mapNotNull { it.toIntOrNull() }.toMutableList()

    private fun salvarIds(l: List<Int>) {
        prefs.edit().putString("widgets", l.joinToString(",")).apply()
    }

    private fun nomeApp(pkg: String): String = try {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(pkg, 0)
        ).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun mostrarWidgets() {
        listaWidgets.removeAllViews()
        val d = resources.displayMetrics.density
        val validos = ids().filter { mgr.getAppWidgetInfo(it) != null }
        salvarIds(validos)

        for (id in validos) {
            val info = mgr.getAppWidgetInfo(id) ?: continue
            try {
                val v = host.createView(this, id, info)
                v.setAppWidget(id, info)
                var h = info.minHeight
                if (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0) {
                    h = maxOf(h, (340 * d).toInt())
                }
                h = maxOf(h, (110 * d).toInt())
                v.setOnLongClickListener { menuTela(); true }
                listaWidgets.addView(v, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, h
                ).apply { bottomMargin = (12 * d).toInt() })
                v.post {
                    val w = (v.width / d).toInt()
                    val hh = (v.height / d).toInt()
                    v.updateAppWidgetSize(null, w, hh, w, hh)
                }
            } catch (e: Exception) {
                // widget com problema: ignora e segue
            }
        }

        val fundo = GradientDrawable().apply {
            cornerRadius = 32f
            setColor(Color.parseColor("#22FFFFFF"))
        }
        val textoAdd = if (validos.isEmpty())
            "＋  Adicionar widget\n(ou segure o dedo na tela)"
        else "＋  Adicionar widget"
        val add = TextView(this).apply {
            text = textoAdd
            textSize = 15f
            setTextColor(Color.parseColor("#DDDDDD"))
            gravity = Gravity.CENTER
            setPadding(24, 36, 24, 36)
            background = fundo
            setOnClickListener { menuTela() }
        }
        listaWidgets.addView(add, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun menuTela() {
        val itens = arrayOf(
            "Adicionar widget", "Remover widget", "Configurar", "Trocar launcher"
        )
        AlertDialog.Builder(this)
            .setTitle("Tela inicial")
            .setItems(itens) { _, w ->
                when (w) {
                    0 -> escolherWidget()
                    1 -> removerWidget()
                    2 -> abrirConfig()
                    3 -> abrirLauncher()
                }
            }.show()
    }

    private fun escolherWidget() {
        val provs = mgr.installedProviders.sortedBy {
            nomeApp(it.provider.packageName).lowercase() +
                it.loadLabel(packageManager).lowercase()
        }
        if (provs.isEmpty()) {
            Toast.makeText(this, "Nenhum widget encontrado", Toast.LENGTH_SHORT).show()
            return
        }
        val nomes = provs.map {
            nomeApp(it.provider.packageName) + " — " + it.loadLabel(packageManager)
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Escolha o widget")
            .setItems(nomes) { _, w -> adicionar(provs[w]) }
            .show()
    }

    private fun adicionar(info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pendente = id
        if (mgr.bindAppWidgetIdIfAllowed(id, info.provider)) {
            configurar(id)
        } else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            try {
                startActivityForResult(i, 1)
            } catch (e: Exception) {
                host.deleteAppWidgetId(id)
                pendente = -1
                Toast.makeText(this, "O Android não deixou adicionar", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun configurar(id: Int) {
        val info = mgr.getAppWidgetInfo(id)
        if (info == null) {
            host.deleteAppWidgetId(id)
            pendente = -1
            Toast.makeText(this, "Não deu para adicionar esse widget", Toast.LENGTH_LONG).show()
            return
        }
        if (info.configure != null) {
            try {
                host.startAppWidgetConfigureActivityForResult(this, id, 0, 2, null)
            } catch (e: Exception) {
                guardar(id)
            }
        } else {
            guardar(id)
        }
    }

    private fun guardar(id: Int) {
        val l = ids()
        if (!l.contains(id)) l.add(id)
        salvarIds(l)
        pendente = -1
        mostrarWidgets()
    }

    private fun removerWidget() {
        val l = ids().filter { mgr.getAppWidgetInfo(it) != null }
        if (l.isEmpty()) {
            Toast.makeText(this, "Não há widgets para remover", Toast.LENGTH_SHORT).show()
            return
        }
        val nomes = l.map {
            val info = mgr.getAppWidgetInfo(it)
            nomeApp(info.provider.packageName) + " — " + info.loadLabel(packageManager)
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Remover qual widget?")
            .setItems(nomes) { _, w ->
                host.deleteAppWidgetId(l[w])
                salvarIds(ids().filter { it != l[w] })
                mostrarWidgets()
            }.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 || requestCode == 2) {
            val id = pendente
            if (id == -1) return
            if (resultCode == Activity.RESULT_OK) {
                if (requestCode == 1) configurar(id) else guardar(id)
            } else {
                host.deleteAppWidgetId(id)
                pendente = -1
            }
        }
    }

    // ---------- Apps ----------
    private fun abrirConfig() {
        startActivity(Intent(this, ConfigActivity::class.java))
    }

    private fun abrirLauncher() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(this, "Não consegui abrir as configurações", Toast.LENGTH_SHORT).show()
            }
        }
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
        mostrarWidgets()
    }
}
