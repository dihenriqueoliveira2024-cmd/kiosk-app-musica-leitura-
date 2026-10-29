package com.kiosk.app

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class ConfigActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("kiosk", MODE_PRIVATE) }
    private lateinit var raiz: LinearLayout
    private lateinit var lista: LinearLayout
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
            setPadding(32, 48, 32, 32)
            setBackgroundColor(Color.parseColor(prefs.getString("cor", "#000000")))
        }
        val topo = TextView(this).apply {
            text = "‹  Configurar"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(16, 24, 16, 24)
            setOnClickListener { finish() }
        }
        lista = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val rolagem = ScrollView(this).apply { addView(lista) }

        raiz.addView(topo)
        raiz.addView(rolagem, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(raiz)
        montarLista()
    }

    // ---------- Lista ----------
    private fun linha(titulo: String, sub: String?, acao: () -> Unit): LinearLayout {
        val fundo = GradientDrawable().apply {
            cornerRadius = 32f
            setColor(Color.parseColor("#22FFFFFF"))
        }
        val l = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 36, 40, 36)
            background = fundo
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20 }
            setOnClickListener { acao() }
        }
        l.addView(TextView(this).apply {
            text = titulo
            textSize = 18f
            setTextColor(Color.WHITE)
        })
        if (sub != null) {
            l.addView(TextView(this).apply {
                text = sub
                textSize = 13f
                setTextColor(Color.parseColor("#BBBBBB"))
            })
        }
        return l
    }

    private fun nomeCor(): String {
        val atual = prefs.getString("cor", "#000000")
        return cores.firstOrNull { it.second == atual }?.first ?: "Preto"
    }

    private fun nomeApp(chave: String, padrao: String): String {
        val pkg = prefs.getString(chave, padrao) ?: padrao
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (e: Exception) {
            "Não instalado"
        }
    }

    private fun montarLista() {
        lista.removeAllViews()
        lista.addView(linha("Cor de fundo", nomeCor()) { escolherCor() })
        lista.addView(linha("Widget da música", "Mostrar na tela inicial") { escolherWidget() })
        lista.addView(linha("Remover widget", null) {
            removerWidget()
            Toast.makeText(this, "Widget removido", Toast.LENGTH_SHORT).show()
        })
        lista.addView(linha("App de música", nomeApp("musica", MUSICOLET)) { escolherApp("musica") })
        lista.addView(linha("App de arquivos", nomeApp("arquivos", CX)) { escolherApp("arquivos") })
        lista.addView(linha("Trocar launcher", "Abre as configurações do celular") { abrirLauncher() })
    }

    // ---------- Cor ----------
    private fun escolherCor() {
        AlertDialog.Builder(this)
            .setTitle("Cor de fundo")
            .setItems(cores.map { it.first }.toTypedArray()) { _, w ->
                prefs.edit().putString("cor", cores[w].second).apply()
                raiz.setBackgroundColor(Color.parseColor(cores[w].second))
                montarLista()
            }.show()
    }

    // ---------- Apps ----------
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
                montarLista()
            }.show()
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

    // ---------- Widget ----------
    private fun removerWidget() {
        val id = prefs.getInt("widgetId", -1)
        if (id != -1) host.deleteAppWidgetId(id)
        prefs.edit().remove("widgetId").apply()
    }

    private fun escolherWidget() {
        val pkgMusica = prefs.getString("musica", MUSICOLET)
        var provs = mgr.installedProviders.filter { it.provider.packageName == pkgMusica }
        if (provs.isEmpty()) provs = mgr.installedProviders
        val nomes = provs.map { it.loadLabel(packageManager) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Escolha o widget")
            .setItems(nomes) { _, w ->
                removerWidget()
                val id = host.allocateAppWidgetId()
                pendente = id
                val provider = provs[w].provider
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
        pendente = -1
        Toast.makeText(this, "Widget salvo", Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 || requestCode == 2) {
            if (resultCode == Activity.RESULT_OK && pendente != -1) {
                if (requestCode == 1) configurarOuSalvar(pendente) else salvarWidget(pendente)
            } else if (pendente != -1) {
                host.deleteAppWidgetId(pendente)
                pendente = -1
            }
        }
    }
}
