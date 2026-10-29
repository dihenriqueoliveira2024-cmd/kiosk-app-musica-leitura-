package com.kiosk.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.random.Random

data class Faixa(val titulo: String, val artista: String, val uri: Uri, val duracao: Int)

object Tocador {
    var lista: List<Faixa> = emptyList()
    var indice = -1
    var aleatorio = false
    var repetir = 0
    var aoMudar: (() -> Unit)? = null
    private var mp: MediaPlayer? = null
    private var erros = 0

    private val foco = AudioManager.OnAudioFocusChangeListener { f ->
        if (f == AudioManager.AUDIOFOCUS_LOSS || f == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pausar()
    }

    fun ativo() = mp != null

    fun carregar(ctx: Context) {
        val c = ctx.applicationContext
        val saida = ArrayList<Faixa>()
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION
        )
        try {
            c.contentResolver.query(
                base, proj, MediaStore.Audio.Media.IS_MUSIC + " != 0", null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
            )?.use { q ->
                while (q.moveToNext()) {
                    val art = q.getString(2) ?: ""
                    saida.add(
                        Faixa(
                            q.getString(1) ?: "Sem título",
                            if (art == "<unknown>") "" else art,
                            ContentUris.withAppendedId(base, q.getLong(0)),
                            q.getInt(3)
                        )
                    )
                }
            }
        } catch (e: Exception) {
        }
        lista = saida
        indice = -1
    }

    @Suppress("DEPRECATION")
    private fun pegarFoco(c: Context) {
        (c.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .requestAudioFocus(foco, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
    }

    private fun liberar() {
        try {
            mp?.release()
        } catch (e: Exception) {
        }
        mp = null
    }

    fun tocar(ctx: Context, i: Int) {
        if (i !in lista.indices) return
        val c = ctx.applicationContext
        liberar()
        indice = i
        pegarFoco(c)
        val p = MediaPlayer()
        mp = p
        try {
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build()
            )
            p.setWakeMode(c, PowerManager.PARTIAL_WAKE_LOCK)
            p.setDataSource(c, lista[i].uri)
            p.setOnPreparedListener { erros = 0; it.start(); aoMudar?.invoke() }
            p.setOnCompletionListener { fim(c) }
            p.setOnErrorListener { _, _, _ -> falhou(c); true }
            p.prepareAsync()
        } catch (e: Exception) {
            falhou(c)
        }
        aoMudar?.invoke()
    }

    private fun falhou(c: Context) {
        erros++
        if (erros > 3) {
            liberar()
            erros = 0
            aoMudar?.invoke()
        } else {
            proxima(c, true)
        }
    }

    private fun fim(c: Context) {
        if (repetir == 2) tocar(c, indice) else proxima(c, true)
    }

    fun proxima(c: Context, auto: Boolean = false) {
        if (lista.isEmpty()) return
        val n = when {
            aleatorio && lista.size > 1 -> {
                var r = Random.nextInt(lista.size)
                while (r == indice) r = Random.nextInt(lista.size)
                r
            }
            indice + 1 < lista.size -> indice + 1
            repetir == 1 || !auto -> 0
            else -> -1
        }
        if (n == -1) {
            liberar()
            aoMudar?.invoke()
        } else {
            tocar(c, n)
        }
    }

    fun anterior(c: Context) {
        if (lista.isEmpty()) return
        if (posicao() > 3000) {
            irPara(0)
            return
        }
        tocar(c, if (indice > 0) indice - 1 else lista.size - 1)
    }

    fun alternar(c: Context) {
        val p = mp
        if (p == null) {
            if (lista.isNotEmpty()) {
                tocar(c, if (indice >= 0) indice else if (aleatorio) Random.nextInt(lista.size) else 0)
            }
        } else {
            try {
                if (p.isPlaying) p.pause() else p.start()
            } catch (e: Exception) {
            }
            aoMudar?.invoke()
        }
    }

    fun pausar() {
        try {
            val p = mp
            if (p != null && p.isPlaying) p.pause()
        } catch (e: Exception) {
        }
        aoMudar?.invoke()
    }

    fun tocando(): Boolean = try {
        mp?.isPlaying == true
    } catch (e: Exception) {
        false
    }

    fun posicao(): Int = try {
        mp?.currentPosition ?: 0
    } catch (e: Exception) {
        0
    }

    fun irPara(ms: Int) {
        try {
            mp?.seekTo(ms)
        } catch (e: Exception) {
        }
    }
}

class MusicActivity : AppCompatActivity() {

    private val M = ViewGroup.LayoutParams.MATCH_PARENT
    private val W = ViewGroup.LayoutParams.WRAP_CONTENT
    private val h = Handler(Looper.getMainLooper())
    private val verde = Color.parseColor("#A8C490")
    private val tinta = Color.parseColor("#1B2A12")

    private lateinit var tvStatus: TextView
    private lateinit var tvTitulo: TextView
    private lateinit var tvArtista: TextView
    private lateinit var tvTempo: TextView
    private lateinit var barra: SeekBar
    private lateinit var btnPlay: TextView
    private lateinit var btnAleat: TextView
    private lateinit var btnRepetir: TextView
    private lateinit var painelInfo: LinearLayout
    private lateinit var listaView: ListView
    private var mostrandoLista = false
    private var arrastando = false

    private val tick = object : Runnable {
        override fun run() {
            atualizarTempo()
            h.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#34343A")
        window.navigationBarColor = Color.parseColor("#17171B")

        val corpo = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#34343A"), Color.parseColor("#17171B"))
        )
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = corpo
            setPadding(40, 24, 40, 40)
        }
        val topo = TextView(this).apply {
            text = "‹   MP3 PLAYER"
            textSize = 16f
            setTextColor(Color.parseColor("#9A9AA2"))
            letterSpacing = 0.15f
            setPadding(8, 24, 8, 24)
            setOnClickListener { finish() }
        }

        // ----- Visor LCD -----
        val lcdFundo = GradientDrawable().apply {
            cornerRadius = 28f
            setColor(verde)
            setStroke(8, Color.parseColor("#0E0E10"))
        }
        val lcd = FrameLayout(this).apply {
            background = lcdFundo
            setPadding(32, 28, 32, 28)
        }
        tvStatus = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setTextColor(tinta)
        }
        tvTitulo = TextView(this).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 22f
            setTextColor(tinta)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            setPadding(0, 28, 0, 8)
        }
        tvArtista = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 15f
            setTextColor(tinta)
            setSingleLine(true)
        }
        barra = SeekBar(this).apply {
            progressTintList = ColorStateList.valueOf(tinta)
            thumbTintList = ColorStateList.valueOf(tinta)
            progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#6E8A5A"))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) tvTempo.text = fmt(p) + " / " + fmt(s?.max ?: 0)
                }

                override fun onStartTrackingTouch(s: SeekBar?) {
                    arrastando = true
                }

                override fun onStopTrackingTouch(s: SeekBar?) {
                    arrastando = false
                    Tocador.irPara(s?.progress ?: 0)
                }
            })
        }
        tvTempo = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setTextColor(tinta)
            gravity = Gravity.END
        }
        painelInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(tvStatus)
            addView(tvTitulo)
            addView(tvArtista)
            addView(barra, LinearLayout.LayoutParams(M, W).apply { topMargin = 48 })
            addView(tvTempo)
        }
        listaView = ListView(this).apply {
            divider = null
            dividerHeight = 0
            visibility = View.GONE
            setOnItemClickListener { _, _, pos, _ ->
                Tocador.tocar(this@MusicActivity, pos)
                fecharLista()
            }
        }
        lcd.addView(painelInfo, FrameLayout.LayoutParams(M, M))
        lcd.addView(listaView, FrameLayout.LayoutParams(M, M))

        // ----- Botões -----
        btnPlay = botao("▶", 30f, 200, true) { Tocador.alternar(this@MusicActivity) }
        val transporte = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(botao("◀◀", 18f, 150, true) { Tocador.anterior(this@MusicActivity) })
            addView(btnPlay)
            addView(botao("▶▶", 18f, 150, true) { Tocador.proxima(this@MusicActivity) })
        }
        btnAleat = botao("ALEAT.", 11f, 0, false) {
            Tocador.aleatorio = !Tocador.aleatorio
            atualizar()
        }
        btnRepetir = botao("REP.", 11f, 0, false) {
            Tocador.repetir = (Tocador.repetir + 1) % 3
            atualizar()
        }
        val extras = LinearLayout(this).apply {
            addView(botao("VOL-", 11f, 0, false) { volume(false) })
            addView(botao("LISTA", 11f, 0, false) { abrirLista() })
            addView(botao("VOL+", 11f, 0, false) { volume(true) })
            addView(btnAleat)
            addView(btnRepetir)
        }

        raiz.addView(topo)
        raiz.addView(lcd, LinearLayout.LayoutParams(M, 0, 1f).apply { topMargin = 16 })
        raiz.addView(transporte, LinearLayout.LayoutParams(M, W).apply { topMargin = 40 })
        raiz.addView(extras, LinearLayout.LayoutParams(M, W).apply { topMargin = 32 })
        setContentView(raiz)

        iniciar()
    }

    private fun botao(txt: String, sp: Float, tamanho: Int, redondo: Boolean, acao: () -> Unit): TextView {
        val fundo = GradientDrawable().apply {
            if (redondo) shape = GradientDrawable.OVAL else cornerRadius = 36f
            setColor(Color.parseColor("#3A3A40"))
            setStroke(3, Color.parseColor("#55555C"))
        }
        return TextView(this).apply {
            text = txt
            textSize = sp
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = fundo
            layoutParams = if (tamanho > 0)
                LinearLayout.LayoutParams(tamanho, tamanho).apply { setMargins(20, 0, 20, 0) }
            else
                LinearLayout.LayoutParams(0, 110, 1f).apply { setMargins(6, 0, 6, 0) }
            setOnClickListener { acao() }
        }
    }

    // ----- Permissão e músicas -----
    private fun permissao() =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun iniciar() {
        if (ContextCompat.checkSelfPermission(this, permissao()) == PackageManager.PERMISSION_GRANTED) {
            if (!Tocador.ativo()) Tocador.carregar(this)
            atualizar()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permissao()), 7)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Tocador.carregar(this)
            atualizar()
        } else {
            tvTitulo.text = "Sem permissão de música"
        }
    }

    // ----- Atualização da tela -----
    private fun fmt(ms: Int): String {
        val s = ms / 1000
        return "%02d:%02d".format(s / 60, s % 60)
    }

    private fun atualizar() {
        val f = Tocador.lista.getOrNull(Tocador.indice)
        val total = Tocador.lista.size
        tvTitulo.text = when {
            total == 0 -> "Nenhuma música"
            f == null -> "Toque ▶ para iniciar"
            else -> f.titulo
        }
        tvArtista.text = f?.artista?.ifEmpty { "Artista desconhecido" } ?: ""
        val modo = (if (Tocador.aleatorio) " ALEAT" else "") +
            when (Tocador.repetir) {
                1 -> " REP-TUDO"
                2 -> " REP-1"
                else -> ""
            }
        val pos = if (f == null) 0 else Tocador.indice + 1
        tvStatus.text = (if (Tocador.tocando()) "▶" else "❚❚") + " " + pos + "/" + total + modo
        btnPlay.text = if (Tocador.tocando()) "❚❚" else "▶"
        btnAleat.alpha = if (Tocador.aleatorio) 1f else 0.5f
        btnRepetir.alpha = if (Tocador.repetir != 0) 1f else 0.5f
        atualizarTempo()
    }

    private fun atualizarTempo() {
        val f = Tocador.lista.getOrNull(Tocador.indice)
        val dur = f?.duracao ?: 0
        val pos = if (f == null) 0 else Tocador.posicao()
        barra.max = if (dur > 0) dur else 1
        if (!arrastando) barra.progress = pos
        tvTempo.text = fmt(pos) + " / " + fmt(dur)
    }

    // ----- Lista -----
    private fun abrirLista() {
        if (Tocador.lista.isEmpty()) return
        mostrandoLista = true
        painelInfo.visibility = View.GONE
        listaView.visibility = View.VISIBLE
        val nomes = Tocador.lista.map { it.titulo }
        listaView.adapter = object : ArrayAdapter<String>(
            this, android.R.layout.simple_list_item_1, nomes
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                val atual = position == Tocador.indice
                v.setTextColor(tinta)
                v.typeface = Typeface.MONOSPACE
                v.textSize = 15f
                v.setSingleLine(true)
                v.text = (if (atual) "▶ " else "   ") + getItem(position)
                v.setBackgroundColor(if (atual) Color.parseColor("#8FAE79") else Color.TRANSPARENT)
                return v
            }
        }
        if (Tocador.indice >= 0) listaView.setSelection(Tocador.indice)
    }

    private fun fecharLista() {
        mostrandoLista = false
        listaView.visibility = View.GONE
        painelInfo.visibility = View.VISIBLE
        atualizar()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (mostrandoLista) fecharLista() else super.onBackPressed()
    }

    // ----- Volume -----
    private fun volume(sobe: Boolean) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (sobe) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
    }

    override fun onResume() {
        super.onResume()
        Tocador.aoMudar = { atualizar() }
        atualizar()
        h.post(tick)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacks(tick)
        Tocador.aoMudar = null
    }

    override fun onDestroy() {
        super.onDestroy()
        h.removeCallbacksAndMessages(null)
    }
}
