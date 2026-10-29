package com.kiosk.app

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
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

data class AppItem(val nome: String, val comp: ComponentName, val icone: Drawable)

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("kiosk", MODE_PRIVATE) }
    private val M = ViewGroup.LayoutParams.MATCH_PARENT
    private val W = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var raiz: LinearLayout
    private lateinit var barra: LinearLayout
    private lateinit var seletor: LinearLayout
    private lateinit var listaWidgets: LinearLayout
    private lateinit var rolagem: ScrollView
    private lateinit var grade: GridView
    private lateinit var mgr: AppWidgetManager
    private lateinit var host: AppWidgetHost
    private var pendente = -1
    private var apps: List<AppItem> = emptyList()

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
        rolagem = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            addView(listaWidgets, ViewGroup.LayoutParams(M, M))
            setOnLongClickListener { menuTela(); true }
        }

        grade = GridView(this).apply {
            numColumns = 4
            verticalSpacing = 24
            selector = null
            isVerticalScrollBarEnabled = false
            adapter = object : BaseAdapter() {
                override fun getCount() = apps.size
                override fun getItem(position: Int) = apps[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    return celulaApp(apps[position])
                }
            }
            setOnItemLongClickListener { _, _, _, _ -> menuTela(); true }
        }

        val centro = FrameLayout(this)
        centro.addView(rolagem, FrameLayout.LayoutParams(M, M))
        centro.addView(grade, FrameLayout.LayoutParams(M, M))

        seletor = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        barra = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        raiz.addView(hora)
        raiz.addView(data)
        raiz.addView(centro, LinearLayout.LayoutParams(M, 0, 1f).apply {
            topMargin = 24; bottomMargin = 12
        })
        raiz.addView(seletor, LinearLayout.LayoutParams(M, W).apply {
            leftMargin = 120; rightMargin = 120; bottomMargin = 16
        })
        raiz.addView(barra, LinearLayout.LayoutParams(M, W).apply { topMargin = 8 })

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

    // ---------- Modo FOCO / NORMAL ----------
    private fun modoNormal() = prefs.getString("modo", "foco") == "normal"

    private fun montarSeletor() {
        seletor.removeAllViews()
        val atual = prefs.getString("modo", "foco")
        for ((id, nome) in listOf("foco" to "FOCO", "normal" to "NORMAL")) {
            val fundo = GradientDrawable().apply {
                cornerRadius = 40f
                setColor(Color.parseColor(if (atual == id) "#55FFFFFF" else "#15FFFFFF"))
            }
            val t = TextView(this).apply {
                text = nome
                textSize = 13f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 22, 0, 22)
                background = fundo
                layoutParams = LinearLayout.LayoutParams(0, W, 1f).apply { setMargins(8, 0, 8, 0) }
                setOnClickListener {
                    prefs.edit().putString("modo", id).apply()
                    atualizarModo()
                }
            }
            seletor.addView(t)
        }
    }

    private fun atualizarModo() {
        montarSeletor()
        if (modoNormal()) {
            rolagem.visibility = View.GONE
            grade.visibility = View.VISIBLE
            carregarApps()
        } else {
            grade.visibility = View.GONE
            rolagem.visibility = View.VISIBLE
            mostrarWidgets()
        }
    }

    private fun carregarApps() {
        Thread {
            val filtro = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val l = packageManager.queryIntentActivities(filtro, 0)
                .filter { it.activityInfo.name != MainActivity::class.java.name }
                .map {
                    AppItem(
                        it.loadLabel(packageManager).toString(),
                        ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                        it.loadIcon(packageManager)
                    )
                }
                .sortedBy { it.nome.lowercase() }
            runOnUiThread {
                apps = l
                (grade.adapter as? BaseAdapter)?.notifyDataSetChanged()
            }
        }.start()
    }

    private fun celulaApp(a: AppItem): View {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
            setOnClickListener { abrirComp(a.comp) }
            setOnLongClickListener { menuTela(); true }
        }
        c.addView(ImageView(this).apply {
            setImageDrawable(a.icone)
            layoutParams = LinearLayout.LayoutParams(130, 130)
        })
        c.addView(TextView(this).apply {
            text = a.nome
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(M, W).apply { topMargin = 6 }
        })
        return c
    }

    private fun abrirComp(comp: ComponentName) {
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(comp)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Não consegui abrir", Toast.LENGTH_SHORT).show()
        }
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
            layoutParams = LinearLayout.LayoutParams(0, W, 1f)
            setOnClickListener { acao() }
            setOnLongClickListener { menuTela(); true }
        }
        if (icone != null) {
            c.addView(ImageView(this).apply {
                setImageDrawable(icone)
                layoutParams = LinearLayout.LayoutParams(140, 140)
            })
        } else {
            val circulo = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#33FFFFFF"))
            }
            c.addView(TextView(this).apply {
                text = emoji
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = circulo
                layoutParams = LinearLayout.LayoutParams(140, 140)
            })
        }
        c.addView(TextView(this).apply {
            text = rotulo
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(W, W).apply { topMargin = 8 }
        })
        return c
    }

    private fun montarBarra() {
        barra.removeAllViews()
        barra.addView(celula("Música", null, "🎵") { abrirMusica() })
        barra.addView(celula("Arquivos", iconeApp("arquivos", CX), "📁") { abrirArquivos() })
        barra.addView(celula("Leitura", null, "📖") {
            Toast.makeText(this, "Modo leitura em breve", Toast.LENGTH_SHORT).show()
        })
        barra.addView(celula("Configurar", null, "⚙") { abrirConfig() })
    }

    private fun aplicarCor() {
        raiz.setBackgroundColor(Color.parseColor(prefs.getString("cor", "#000000")))
    }

    // ---------- Widgets ----------
    private fun ids(): MutableList<Int> =
        (prefs.getString("widgets", "") ?: "")
            .split(",").mapNotNull { it.toIntOrNull() }.toMutableList()

    private fun salvarIds
