package com.kiosk.app

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
        lista.addView(linha("Widgets", "Volte e segure o dedo na tela inicial") {
            Toast.makeText(
                this,
                "Na tela inicial, segure o dedo para adicionar ou remover widgets",
                Toast.LENGTH_LONG
            ).show()
        })
        lista.addView(linha("App de música", nomeApp("musica", MUSICOLET)) { escolherApp("musica") })
        lista.addView(linha("App de arquivos", nomeApp("arquivos", CX)) { escolherApp("arquivos") })
        lista.addView(linha("Trocar launcher", "Abre as configurações do celular") { abrirLauncher() })
    }

    private fun escolherCor() {
        AlertDialog.Builder(this)
            .setTitle("Cor de fundo")
            .setItems(cores.map { it.first }.toTypedArray()) { _, w ->
                prefs.edit().putString("cor", cores[w].second).apply()
                raiz.setBackgroundColor(Color.parseColor(cores[w].second))
                montarLista()
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
}
