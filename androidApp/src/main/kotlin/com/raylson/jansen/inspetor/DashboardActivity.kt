package com.raylson.jansen.inspetor

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.*
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import androidx.core.graphics.PathParser
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.exifinterface.media.ExifInterface
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : AppCompatActivity() {

    data class Estacao(val nome: String, val cor: String)

    data class ItemHm(
        val id: String,
        val titulo: String,
        val cor: String,
        val cardAzulLabel: String,
        val cardAzulSub: String,
        val tipo: String,
        val statusDisponiveis: List<String> = listOf("LIGADA", "DESLIGADA"),
        val statusPadrao: String = "DESLIGADA",
        var statusVazao: String = "SEM VAZÃO",
        var fotoSup: Bitmap? = null,
        var fotoInf: Bitmap? = null,
        var dataHoraSup: String = "",
        var dataHoraInf: String = "",
        // ═══ NOVO: leitura manual (duplo clique no boxHidrometro) ═══
        // Guarda apenas o valor cru digitado pelo usuário (ex: "55.7").
        // O prefixo "+ " (HM) ou sufixo " m³/hr" (HM_VAZAO) é aplicado
        // somente na hora de exibir / desenhar na tarja.
        var leituraManual: String? = null,
        // ═══ NOVO: controla se a leitura entra na tarja final da imagem ═══
        var incluirLeituraNaFoto: Boolean = false,
        // ═══ NOVO: persistência do diálogo LIVRE — precisa sobreviver a
        // fechar/reabrir e a "gerar novamente" dentro da mesma sessão, já
        // que o usuário às vezes precisa sair da tela e voltar. ═══
        var textoLivre: String = "",
        var incluirDataHoraLivre: Boolean = true,
        // ═══ NOVO: marca se fotoSup/fotoInf vieram de dentro do Cofre
        // (Galeria 2), em vez de uma foto nova tirada na câmera/galeria
        // do Android. Quando true, gerarRegistroAssincrono() NÃO chama
        // CofreManager.salvarSeNovo para aquele slot — evita duplicar a
        // mesma foto no Cofre toda vez que o usuário gera de novo. ═══
        var fotoSupVeioDoCofre: Boolean = false,
        var fotoInfVeioDoCofre: Boolean = false
    )

    data class LagoNA(
        val abreviacao: String,
        val nomeCard: String,
        var fotoRegua: Bitmap? = null,
        var dataHora: String = "",
        var valor: String? = null,
        var foraRégua: Boolean = false,
        // ═══ NOVO: mesma regra do ItemHm acima, mas para a foto de régua
        // do N.A. ═══
        var fotoVeioDoCofre: Boolean = false
    )

    private val estacoes = listOf(
        Estacao("DET-01", "#2F5BFF"),
        Estacao("ARB-05", "#EAB308"),
        Estacao("ARB-06", "#22C55E"),
        Estacao("ARB-07", "#F59E0B"),
        Estacao("ARB-08", "#EF4444"),
        Estacao("ARB-09", "#06B6D4"),
        Estacao("LIVRE",  "#8B5CF6"),
        Estacao("SC",     "#8B5C29"),
        Estacao("N.A.",   "#64748B")
    )

    private val itensPorEstacao = mapOf(
        "DET-01" to listOf(
            ItemHm("01",      "HM-01",      "#2F5BFF", "BOMBA-01",       "BOMBA CORRESPONDENTE", "HM",            listOf("LIGADA", "DESLIGADA"),        "DESLIGADA"),
            ItemHm("02",      "HM-02",      "#2F5BFF", "BOMBA-02",       "BOMBA CORRESPONDENTE", "HM",            listOf("LIGADA", "DESLIGADA"),        "DESLIGADA"),
            ItemHm("03",      "HM-03",      "#2F5BFF", "BOMBA-03",       "BOMBA CORRESPONDENTE", "HM",            listOf("LIGADA", "DESLIGADA"),        "DESLIGADA"),
            ItemHm("04",      "HM-04",      "#2F5BFF", "BOMBA-04",       "BOMBA CORRESPONDENTE", "HM",            listOf("LIGADA", "DESLIGADA"),        "DESLIGADA"),
            ItemHm("GAL",     "GALERIA",    "#EF4444", "GALERIA",        "ESTRUTURA",            "SIMPLES",       emptyList(),                          ""),
            ItemHm("SIF-SUP", "SIFÃO SUP.", "#06B6D4", "SIFÃO SUPERIOR", "FLUXOS",              "SIFAO",         listOf("COM VAZÃO", "SEM VAZÃO"),     "SEM VAZÃO"),
            ItemHm("SIF-INF", "SIFÃO INF.", "#0EA5E9", "SIFÃO INFERIOR", "FLUXOS",              "SIFAO",         listOf("COM VAZÃO", "SEM VAZÃO"),     "SEM VAZÃO"),
            ItemHm("CALHA",   "CALHA",      "#F97316", "CALHA PARSHALL", "ESTRUTURA",           "SIMPLES",       emptyList(),                          "")
        ),
        "ARB-05" to listOf(
            ItemHm("ARB-05-FM", "ARB-05", "#EAB308", "FLOWMETER", "ARB-05", "SIMPLES_STATUS", listOf("LIGADO", "ZERADO", "DESLIGADO"), "LIGADO")
        ),
        "ARB-06" to listOf(
            ItemHm("ARB-06-FM", "ARB-06", "#22C55E", "FLOWMETER", "ARB-06", "SIMPLES_STATUS", listOf("LIGADO", "ZERADO", "DESLIGADO"), "LIGADO")
        ),
        "ARB-07" to listOf(
            ItemHm("BA-73", "BA-73", "#F59E0B", "FLOWMETER BA-73", "ARB-07", "HM_VAZAO", listOf("ZERADO", "DESLIGADO", "LIGADO"), "ZERADO"),
            ItemHm("BA-74", "BA-74", "#F59E0B", "FLOWMETER BA-74", "ARB-07", "HM_VAZAO", listOf("ZERADO", "DESLIGADO", "LIGADO"), "ZERADO")
        ),
        "ARB-08" to listOf(
            ItemHm("BA-85", "BA-85", "#EF4444", "FLOWMETER BA-85", "ARB-08", "HM_VAZAO", listOf("LIGADO", "ZERADO", "DESLIGADO"), "LIGADO"),
            ItemHm("BA-86", "BA-86", "#EF4444", "FLOWMETER BA-86", "ARB-08", "HM_VAZAO", listOf("DESLIGADO", "LIGADO", "ZERADO"), "DESLIGADO"),
            ItemHm("BA-87", "BA-87", "#EF4444", "FLOWMETER BA-87", "ARB-08", "HM_VAZAO", listOf("LIGADO", "ZERADO", "DESLIGADO"), "LIGADO")
        ),
        "ARB-09" to listOf(
            ItemHm("9.1", "9.1", "#06B6D4", "FLOWMETER 9.1", "ARB-09", "HM_VAZAO", listOf("LIGADO", "ZERADO", "DESLIGADO"), "LIGADO"),
            ItemHm("9.2", "9.2", "#06B6D4", "FLOWMETER 9.2", "ARB-09", "HM_VAZAO", listOf("LIGADO", "ZERADO", "DESLIGADO"), "LIGADO")
        ),
        // ═══ NOVO: card "LIVRE" — 1 único ponto, foto + texto livre (sem
        // máscara de N.A./Vazão) + status opcional com a 4ª opção "NENHUM"
        // (preta), pra quem não quer marcar status nenhum. ═══
        "LIVRE" to listOf(
            // Primeiro campo: "EVIDÊNCIA" (o que aparece no topo do card)
            // Segundo campo: "GERAL" (o que aparece no nome do item no carrossel)
            ItemHm("LIVRE-01", "GERAL", "#8B5CF6", "GERAL", "ARB'S e LAGOs", "LIVRE", listOf("LIGADO", "DESLIGADO", "ZERADO", "NENHUM"), "NENHUM")
        ),
        // ═══ NOVO: card "SC" — Scanner de Documentos 100% próprio (sem
        // Google). 1 único ponto, sem status. Fluxo: foto normal →
        // mostrarDialogRecorteSC() (recorte/perspectiva) → mostrarDialogResultadoSC(). ═══
        "SC" to listOf(
            ItemHm("SC-01", "DOC", "#8B5C29", "DOCUMENTO", "SCANNER", "SC", emptyList(), "")
        )
    )
    private val lagosNA = listOf(
        LagoNA("ARB-01",  "ARB-01"),
        LagoNA("ARB-08",  "ARB-08"),
        LagoNA("ARB-09",  "ARB-09"),
        LagoNA("ARB-10",  "ARB-10"),
        LagoNA("DET-01",  "LAGOA DE DETENÇÃO 01"),
        LagoNA("DET-02",  "LAGOA DE DETENÇÃO 02"),
        LagoNA("DT2-ex",  "DET-02 EXTRAVASOR"), 
        LagoNA("DET-03",  "LAGOA DE DETENÇÃO 03"),
        LagoNA("L.BRUTA", "LAGOA BRUTA"),
        LagoNA("CP",      "COOLING POND"),
        LagoNA("CP-ex",   "COOLING POND EXTRAVASOR") 
    )

    // ═══ Uma cor fixa para cada um dos 11 lagos de N.A. (mesma ordem da
    // lista acima: ARB-01, ARB-08, ARB-09, ARB-10, DET-01, DET-02, DT2-ex,
    // DET-03, L.BRUTA, CP, CP-ex). ═══
    private val coresNeonNA = listOf(
        "#FF0000", // Vermelho
        "#FF7A00", // Laranja
        "#8D6E63", // Marron
        "#00C853", // Verde
        "#FFB300", // Âmbar
        "#2F5BFF", // Azul
        "#212121", // Preto
        "#F4511E", // Laranja-escuro
        "#EAB308", // Amarelo
        "#9E9E9E", // Cinza
        "#7B1FF2"  // Roxo
        
        
    )

    private var estacaoSelecionada: Estacao = estacoes[0]
    private var itensAtuais: List<ItemHm> = itensPorEstacao["DET-01"] ?: emptyList()
    private var hmSelecionado  = 0
    private var statusBomba    = "DESLIGADA"

    private var captureFase    = ""
    private var lagoNASelecionado = 0
    private var foraDeNA = false

    private var jobLimpeza: Job? = null
    private var tempoUltimoCliqueBomba: Long = 0
    // ═══ NOVO: detecta duplo clique no boxHidrometro ═══
    private var tempoUltimoCliqueHidro: Long = 0
    private var ultimoCliqueLimpar: Long = 0

    private lateinit var tvCardAzulSub: TextView
    private lateinit var tvBombaCorrespondente: TextView
    private lateinit var btnStatus: CardView
    private lateinit var imgStatus: ImageView
    private lateinit var boxHidrometro: LinearLayout
    private lateinit var boxBomba: LinearLayout
    private lateinit var tvLabelHidro: TextView
    private lateinit var tvLabelBomba: TextView
    private lateinit var imgHidroIcon: ImageView
    private lateinit var imgHidroPreview: ImageView
    private lateinit var imgBombaIcon: ImageView
    private lateinit var imgBombaPreview: ImageView
    private lateinit var btnDeletarHidro: CardView
    private lateinit var btnDeletarBomba: CardView
    private lateinit var btnAdd: View
    private lateinit var carrosselHm: RecyclerView
    private lateinit var carrosselEstacoes: RecyclerView
    private lateinit var hmAdapter: HmAdapter
    private lateinit var tvLabelCarrossel: TextView
    private lateinit var btnToggleForaNA: CardView
    private lateinit var tvToggleForaNA: TextView
    private lateinit var btnConfiguracoes: ImageView

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tutorialAutoScrollRecycler: RecyclerView? = null
    private var tutorialAutoScrollRunnable: Runnable? = null

    companion object {
        private const val REQUEST_CAMERA_CAPTURE = 401
        private const val REQUEST_COFRE_SELECAO = 402
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#F4F6FB")
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        setContentView(R.layout.activity_dashboard)

        // ═══ Fonte fixa gravada na foto: usa o mesmo arquivo bundled do
        // app (Roboto Mono) em vez do apelido genérico Typeface.MONOSPACE
        // do Android — garante visual idêntico em qualquer aparelho. Se o
        // arquivo de fonte ainda não foi adicionado ao projeto (ver
        // res/font/app_font_family.xml), cai automaticamente de volta no
        // Typeface.MONOSPACE de sempre, sem quebrar nada. ═══
        try {
            androidx.core.content.res.ResourcesCompat.getFont(this, R.font.app_font_family)?.let {
                ImageHelper.definirFontePersonalizada(it)
            }
        } catch (e: Exception) {
            // Fonte ainda não adicionada ao projeto — segue com o padrão do sistema.
        }

        // ═══ MEMÓRIA PREMIUM: RESGATA O ESTADO SALVO DE ONDE PAROU ═══
        val prefs = SecurePrefs.get(this, "inspetor_prefs")
        val lastStationNome = prefs.getString("last_station", "DET-01") ?: "DET-01"
        estacaoSelecionada = estacoes.find { it.nome == lastStationNome } ?: estacoes[0]

        if (isModoNA()) {
            itensAtuais = emptyList()
            lagoNASelecionado = prefs.getInt("last_lago_na", 0).coerceIn(0, lagosNA.size - 1)
            statusBomba = "DESLIGADA"
        } else {
            itensAtuais = itensPorEstacao[estacaoSelecionada.nome] ?: emptyList()
            hmSelecionado = prefs.getInt("last_hm_${estacaoSelecionada.nome}", 0).coerceIn(0, (itensAtuais.size - 1).coerceAtLeast(0))
            statusBomba = itensAtuais.getOrNull(hmSelecionado)?.statusPadrao ?: "LIGADA"
        }

        bindViews()
        setupCarrosselHm()
        setupCarrosselEstacoes()

        if (isModoNA()) {
            carregarCarrosselNA()
        }

        setupCaixasCamera()
        setupStatus()
        setupBotaoGerar()
        setupBotaoToggleForaNA()
        setupBotaoConfiguracoes()
        setupBotoesDeletarFoto()
        atualizarCabecalhoSaudacao()

                        atualizarCardAzul()
        atualizarLabelsEBloqueio()
        aplicarStatus()
        atualizarUIParaModoNA()
        restaurarPreviews()

        // ═══ NOVO: ViewTreeObserver garante que as posições e medidas estejam calculadas.
        // O postDelayed de 200ms garante que a fonte do texto do seu nome já terminou
        // de ser desenhada, permitindo que o primeiro círculo apareça corretamente. ═══
        val rootLayout = findViewById<View>(android.R.id.content)
        rootLayout.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                rootLayout.viewTreeObserver.removeOnGlobalLayoutListener(this)

                rootLayout.postDelayed({
                    // --- INICIA O TUTORIAL (lógica isolada em TutorialDashboard.kt) ---
                    TutorialDashboard.iniciarSeNecessario(
                        activity = this@DashboardActivity,
                        passos = listOf(
                            TutorialDashboard.Passo(
                                view = findViewById(R.id.tvApelido),
                                titulo = "Bem-vindo ao Inspetor!",
                                descricao = "Este é o seu painel principal. Dê dois toques no seu nome se quiser alterá-lo a qualquer momento.",
                                corDestaque = Color.parseColor("#EF4444"),
                                folgaDp = 8f
                            ),
                            TutorialDashboard.Passo(
                                view = findViewById(R.id.btnConfiguracoes),
                                titulo = "Configurações",
                                descricao = "Toque aqui para acessar as configurações do aplicativo (proporção da foto, opções gerais e mais).",
                                corDestaque = Color.parseColor("#EF4444"),
                                circular = true,
                                folgaDp = 6f
                            ),
                            TutorialDashboard.Passo(
                                view = findViewById(R.id.carrosselEstacoes),
                                titulo = "Selecione a Estação",
                                descricao = "Deslize para os lados ↔️ para alternar entre os blocos DET, ARB, LIVRE, SCAN ou o monitoramento de N.A.",
                                corDestaque = Color.parseColor("#EF4444"),
                                folgaDp = 4f,
                                calcularRetangulo = {
                                    TutorialDashboard.retanguloVisivelDe(findViewById(R.id.carrosselEstacoes))
                                },
                                aoEntrar = {
                                    iniciarAutoScrollTutorial(findViewById(R.id.carrosselEstacoes), deslocamentoPx = 260, intervaloMs = 850L)
                                },
                                aoSair = {
                                    pararAutoScrollTutorial(findViewById(R.id.carrosselEstacoes))
                                }
                            ),
                            TutorialDashboard.Passo(
                                view = findViewById(R.id.carrosselHidrometros),
                                titulo = "Pontos de Inspeção",
                                descricao = "Deslize para esquerda ou direita ↔️ para escolher o Flowmeter / Hidrômetro ou estrutura para registrar.",
                                corDestaque = Color.parseColor("#EF4444"),
                                folgaDp = 4f,
                                calcularRetangulo = {
                                    TutorialDashboard.retanguloVisivelDe(findViewById(R.id.carrosselHidrometros))
                                },
                                aoEntrar = {
                                    iniciarAutoScrollTutorial(findViewById(R.id.carrosselHidrometros), deslocamentoPx = 300, intervaloMs = 850L)
                                },
                                aoSair = {
                                    pararAutoScrollTutorial(findViewById(R.id.carrosselHidrometros))
                                }
                            ),
                            TutorialDashboard.Passo(
                                view = findViewById(R.id.btnStatus),
                                titulo = "Status Operacional",
                                descricao = "Toque aqui para alternar o status atual do ponto selecionado (LIGADO, DESLIGADO ou ZERADO).",
                                corDestaque = Color.parseColor("#EF4444")
                            ),
                            TutorialDashboard.Passo(
                                view = findViewById(R.id.boxHidrometro),
                                titulo = "Primeira Foto",
                                descricao = "Toque aqui para abrir a câmera e capturar a imagem principal.\n\nDica: Segure este botão (ou o botão da Bomba) para escolher uma imagem da sua Galeria ou do Cofre.",
                                corDestaque = Color.parseColor("#EF4444")
                            ),
                            TutorialDashboard.Passo(
                                view = findViewById(R.id.boxBomba),
                                titulo = "Segunda Foto ou Ação",
                                descricao = "Dê dois toques rápidos para informar a vazão, ou um toque para fotografar a Bomba.\n\n(Lembrando: você também pode segurar aqui para usar a Galeria/Cofre).",
                                corDestaque = Color.parseColor("#EF4444")
                            ),
                            TutorialDashboard.Passo(
                                view = findViewById(R.id.btnToggleForaNA),
                                titulo = "Régua ou Fora da Régua",
                                descricao = "No modo N.A., toque aqui para marcar se a leitura está dentro da régua (verde) ou fora dela (vermelho).",
                                corDestaque = Color.parseColor("#EF4444"),
                                folgaDp = 6f
                            ),
                            TutorialDashboard.Passo(
                                view = findViewById(R.id.btnLimparGeral),
                                titulo = "Limpeza Rápida",
                                descricao = "Errou ou quer recomeçar? Dê dois toques rápidos aqui para limpar todas as fotos temporárias da sessão atual.",
                                corDestaque = Color.parseColor("#EF4444"),
                                circular = true,
                                folgaDp = 8f
                            ),
                            TutorialDashboard.Passo(
                                view = findViewById(R.id.btnCofre),
                                titulo = "Galeria 2 (Cofre)",
                                descricao = "Toque aqui para abrir o acervo de fotos já registradas — dá pra rever, escolher ou reaproveitar qualquer imagem salva.",
                                corDestaque = Color.parseColor("#EF4444"),
                                circular = true,
                                folgaDp = 8f
                            ),
                            TutorialDashboard.Passo(
                                view = findViewById(R.id.btnListaNA),
                                titulo = "Controle de N.A.",
                                descricao = "Toque aqui para abrir diretamente a tela de Controle de N.A. e acompanhar o nível consolidado de todos os lagos.",
                                corDestaque = Color.parseColor("#EF4444"),
                                circular = true,
                                folgaDp = 8f
                            ),
                            TutorialDashboard.Passo(
                                view = findViewById(R.id.btnHistorico),
                                titulo = "Consultar Histórico",
                                descricao = "Toque aqui para ver, editar ou compartilhar as medições que já foram salvas.",
                                corDestaque = Color.parseColor("#EF4444"),
                                circular = true,
                                folgaDp = 8f
                            ),
                            TutorialDashboard.Passo(
                                view = findViewById(R.id.btnAdd),
                                titulo = "Gerar e Finalizar",
                                descricao = "Tudo pronto! Com as fotos tiradas e os status definidos, toque aqui para processar a imagem com as informações oficiais.",
                                corDestaque = Color.parseColor("#EF4444"),
                                circular = true,
                                folgaDp = 10f
                            )
                        )
                    )
                }, 200) // Atraso de 200ms para garantir a renderização
            }
        })
        
        val btnLimparGeral = findViewById<View>(R.id.btnLimparGeral)
        btnLimparGeral?.setOnClickListener {
            val agora = System.currentTimeMillis()
            if (agora - ultimoCliqueLimpar < 500) {
                // ═══ RESET DE MEMÓRIA: FORÇA VOLTAR PARA O DET-01 AO LIMPAR TUDO ═══
                val p = SecurePrefs.get(this, "inspetor_prefs")
                p.edit().apply {
                    remove("last_station")
                    estacoes.forEach { remove("last_hm_${it.nome}") }
                    remove("last_lago_na")
                    apply()
                }

                estacaoSelecionada = estacoes[0]
                itensAtuais = itensPorEstacao["DET-01"] ?: emptyList()
                hmSelecionado = 0
                statusBomba = itensAtuais.firstOrNull()?.statusPadrao ?: "LIGADA"

                limparTudo(porTimeout = false)

                hmAdapter.atualizarLista(itensAtuais)
                if (carrosselHm.adapter !is HmAdapter) { carrosselHm.adapter = hmAdapter }
                
                val meio = Int.MAX_VALUE / 2
                carrosselEstacoes.scrollToPosition(meio - (meio % estacoes.size))
                
                val offset = meio - (meio % itensAtuais.size)
                (carrosselHm.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(offset, 0)

                atualizarCardAzul()
                atualizarLabelsEBloqueio()
                aplicarStatus()
                atualizarUIParaModoNA()
                restaurarPreviews()
                hmAdapter.notifyDataSetChanged()
                (carrosselEstacoes.adapter as? EstacaoAdapter)?.notifyDataSetChanged()
            } else {
                ultimoCliqueLimpar = agora
                Toast.makeText(this, "Toque duas vezes rápido para limpar as fotos", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.btnHistorico)?.setOnClickListener {
            startActivity(Intent(this, HistoricoActivity::class.java))
        }
        findViewById<View>(R.id.btnListaNA)?.setOnClickListener {
            startActivity(Intent(this, ListaNAActivity::class.java))
        }
        findViewById<View>(R.id.btnCofre)?.setOnClickListener {
            startActivity(Intent(this, CofreActivity::class.java))
        }
    }

    override fun onDestroy() {
        pararAutoScrollTutorial()
        super.onDestroy()
        jobLimpeza?.cancel()
        scope.cancel()
    }
    private fun iniciarTimerLimpeza() {
        // jobLimpeza?.cancel()
        // jobLimpeza = scope.launch {
        //     delay(30 * 60 * 1000L)
        //     limparTudo(porTimeout = true)
        // }
    }

    private fun limparTudo(porTimeout: Boolean) {
        itensPorEstacao.values.flatten().forEach { item ->
            item.fotoSup?.recycle(); item.fotoSup = null
            item.fotoInf?.recycle(); item.fotoInf = null
            item.dataHoraSup = ""; item.dataHoraInf = ""
            
            // ═══ NOVO: Protege os dados da EVIDÊNCIA (LIVRE-01) para não apagar ═══
            if (item.id != "LIVRE-01") {
                item.leituraManual = null
                item.incluirLeituraNaFoto = false
                item.textoLivre = "" 
            }
        }
        lagosNA.forEach { lago ->
            lago.fotoRegua?.recycle(); lago.fotoRegua = null
            lago.dataHora = ""
        }
        
        // ═══ CORREÇÃO: Contorno para o bug do clear() no SharedPreferences Criptografado ═══
        try {
            val prefsLeitura = SecurePrefs.get(this, "leituras_flw_hidro")
            val editor = prefsLeitura.edit()
            prefsLeitura.all.keys.forEach { chave ->
                editor.remove(chave)
            }
            editor.apply()
        } catch (_: Exception) { /* limpeza é best-effort */ }
        
        restaurarPreviews()
        if (porTimeout) {
            Toast.makeText(this, "Sessão expirada. Fotos não geradas foram limpas (30 min).", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Todas as fotos pendentes foram limpas!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restaurarPreviews() {
        // ═══ Badges de apagar foto: vermelho+funcional só nas ARBs; cinza+bloqueado no DET-01/N.A. ═══
        val isArb = estacaoSelecionada.nome.startsWith("ARB")
        val corAtiva = Color.parseColor("#EF4444")
        val corBloqueada = Color.parseColor("#94A3B8")

        if (isModoNA()) {
            val lago = lagosNA.getOrNull(lagoNASelecionado)
            if (lago?.fotoRegua != null) {
                imgHidroPreview.setImageBitmap(lago.fotoRegua)
                imgHidroPreview.visibility = View.VISIBLE
                imgHidroIcon.visibility = View.GONE
                btnDeletarHidro.visibility = View.VISIBLE
                btnDeletarHidro.setCardBackgroundColor(corBloqueada) // N.A. sempre bloqueado
            } else {
                imgHidroPreview.visibility = View.GONE
                imgHidroIcon.visibility = View.VISIBLE
                btnDeletarHidro.visibility = View.GONE
            }
            imgBombaPreview.visibility = View.GONE
            imgBombaIcon.visibility = View.GONE
            btnDeletarBomba.visibility = View.GONE
            return
        }

        val item = itensAtuais.getOrNull(hmSelecionado) ?: return
        
        if (item.fotoSup != null) {
            imgHidroPreview.setImageBitmap(item.fotoSup)
            imgHidroPreview.visibility = View.VISIBLE
            imgHidroIcon.visibility = View.GONE
            btnDeletarHidro.visibility = View.VISIBLE
            btnDeletarHidro.setCardBackgroundColor(if (isArb) corAtiva else corBloqueada)
        } else {
            imgHidroPreview.visibility = View.GONE
            imgHidroIcon.visibility = View.VISIBLE
            btnDeletarHidro.visibility = View.GONE
        }

        if (item.fotoInf != null) {
            imgBombaPreview.setImageBitmap(item.fotoInf)
            imgBombaPreview.visibility = View.VISIBLE
            imgBombaIcon.visibility = View.GONE
            btnDeletarBomba.visibility = View.VISIBLE
            btnDeletarBomba.setCardBackgroundColor(if (isArb) corAtiva else corBloqueada)
        } else {
            imgBombaPreview.visibility = View.GONE
            imgBombaIcon.visibility = View.VISIBLE
            btnDeletarBomba.visibility = View.GONE
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  BADGES DE APAGAR FOTO INDIVIDUAL (X vermelho no canto superior direito)
    //  - Nas ARBs: apaga só aquela foto (hidro OU bomba), sem precisar limpar tudo.
    //  - No DET-01 e N.A.: aparece cinza/apagado, a animação roda mas a função
    //    de apagar fica bloqueada (não faz nada).
    // ═══════════════════════════════════════════════════════════════════════
    private fun animarRotacaoBadge(view: View) {
        view.animate()
            .rotationBy(360f)
            .setDuration(380)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    private fun apagarFotoIndividual(slot: String) {
        if (isModoNA()) {
            if (slot == "hidro") {
                val lago = lagosNA.getOrNull(lagoNASelecionado) ?: return
                lago.fotoRegua?.recycle(); lago.fotoRegua = null
                lago.dataHora = ""
            }
            restaurarPreviews()
            Toast.makeText(this, "Foto removida", Toast.LENGTH_SHORT).show()
            return
        }

        val item = itensAtuais.getOrNull(hmSelecionado) ?: return
        when (slot) {
            "hidro" -> { item.fotoSup?.recycle(); item.fotoSup = null; item.dataHoraSup = "" }
            "bomba" -> { item.fotoInf?.recycle(); item.fotoInf = null; item.dataHoraInf = "" }
        }
        restaurarPreviews()
        hmAdapter.notifyDataSetChanged()
        Toast.makeText(this, "Foto removida", Toast.LENGTH_SHORT).show()
    }

    private fun mostrarConfirmacaoExcluirFoto(onConfirmar: () -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog_confirmar_exclusao, null)
        val d = android.app.AlertDialog.Builder(this).setView(view).create()
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<Button>(R.id.btnCancelarExclusao).setOnClickListener { d.dismiss() }
        view.findViewById<Button>(R.id.btnConfirmarExclusao).setOnClickListener {
            onConfirmar()
            d.dismiss()
        }
        d.show()
    }

    private fun setupBotoesDeletarFoto() {
        btnDeletarHidro.setOnClickListener {
            animarRotacaoBadge(it)
            if (estacaoSelecionada.nome.startsWith("ARB")) {
                mostrarConfirmacaoExcluirFoto { apagarFotoIndividual("hidro") }
            }
        }
        btnDeletarBomba.setOnClickListener {
            animarRotacaoBadge(it)
            if (estacaoSelecionada.nome.startsWith("ARB")) {
                mostrarConfirmacaoExcluirFoto { apagarFotoIndividual("bomba") }
            }
        }
    }

    private fun bindViews() {
        tvCardAzulSub         = findViewById(R.id.tvCardAzulSub)
        tvBombaCorrespondente = findViewById(R.id.tvBombaCorrespondente)
        btnStatus             = findViewById(R.id.btnStatus)
        imgStatus             = findViewById(R.id.imgStatus)
        boxHidrometro         = findViewById(R.id.boxHidrometro)
        boxBomba              = findViewById(R.id.boxBomba)
        tvLabelHidro          = findViewById(R.id.tvLabelHidro)
        tvLabelBomba          = findViewById(R.id.tvLabelBomba)
        imgHidroIcon          = findViewById(R.id.imgHidroIcon)
        imgHidroPreview       = findViewById(R.id.imgHidroPreview)
        imgBombaIcon          = findViewById(R.id.imgBombaIcon)
        imgBombaPreview       = findViewById(R.id.imgBombaPreview)
        btnDeletarHidro       = findViewById(R.id.btnDeletarHidro)
        btnDeletarBomba       = findViewById(R.id.btnDeletarBomba)
        btnAdd                = findViewById(R.id.btnAdd)
        carrosselHm           = findViewById(R.id.carrosselHidrometros)
        carrosselEstacoes     = findViewById(R.id.carrosselEstacoes)
        tvLabelCarrossel      = findViewById(R.id.tvLabelCarrossel)
        btnToggleForaNA       = findViewById(R.id.btnToggleForaNA)
        tvToggleForaNA        = findViewById(R.id.tvToggleForaNA)
        btnConfiguracoes      = findViewById(R.id.btnConfiguracoes)
    }

    private fun setupBotaoConfiguracoes() {
        btnConfiguracoes.setOnClickListener {
            startActivity(Intent(this, ConfiguracoesActivity::class.java))
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private fun isModoNA() = estacaoSelecionada.nome == "N.A."
    private fun isEstacaoComFormatoConfiguravel(): Boolean {
        // As ARBs, N.A. e LIVRE já aceitam corte nativamente
        if (estacaoSelecionada.nome in setOf("ARB-05", "ARB-06", "ARB-07", "N.A.", "LIVRE")) return true
        
        // Se o item selecionado for a GALERIA, libera a configuração de tela também!
        val itemAtual = itensAtuais.getOrNull(hmSelecionado)
        if (estacaoSelecionada.nome == "DET-01" && itemAtual?.id == "GAL") {
            return true
        }
        
        return false
    }

    private fun setupCarrosselHm() {
        carrosselHm.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        instalarParadaDeAutoScrollAoTocar(carrosselHm)
        hmAdapter = HmAdapter(itensAtuais) { idx ->
            hmSelecionado = idx.coerceIn(0, (itensAtuais.size - 1).coerceAtLeast(0))
            val novoItem = itensAtuais.getOrNull(idx) ?: return@HmAdapter
            if (statusBomba !in novoItem.statusDisponiveis && novoItem.statusDisponiveis.isNotEmpty()) {
                statusBomba = novoItem.statusPadrao
            }
            
            SecurePrefs.get(this, "inspetor_prefs").edit()
                .putInt("last_hm_${estacaoSelecionada.nome}", hmSelecionado)
                .apply()

            atualizarCardAzul()
            atualizarLabelsEBloqueio()
            aplicarStatus()
            restaurarPreviews()
            hmAdapter.notifyDataSetChanged()
        }
        carrosselHm.adapter = hmAdapter

        if (!isModoNA()) {
            if (estacaoSelecionada.nome == "DET-01" && itensAtuais.isNotEmpty()) {
                val meio = Int.MAX_VALUE / 2
                val offset = meio - (meio % itensAtuais.size) + hmSelecionado
                (carrosselHm.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(offset, 0)
            } else {
                (carrosselHm.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(hmSelecionado, 0)
            }
        }
    }

    private fun setupCarrosselEstacoes() {
        carrosselEstacoes.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        instalarParadaDeAutoScrollAoTocar(carrosselEstacoes)
        carrosselEstacoes.adapter = EstacaoAdapter(estacoes) { novaEstacao ->
            if (estacaoSelecionada.nome != novaEstacao.nome) {
                estacaoSelecionada = novaEstacao
                val p = SecurePrefs.get(this, "inspetor_prefs")
                p.edit().putString("last_station", novaEstacao.nome).apply()

                if (isModoNA()) {
                    itensAtuais = emptyList()
                    carregarCarrosselNA()
                } else {
                    itensAtuais = itensPorEstacao[novaEstacao.nome] ?: emptyList()
                    if (carrosselHm.adapter !is HmAdapter) { carrosselHm.adapter = hmAdapter }
                    
                    hmSelecionado = p.getInt("last_hm_${novaEstacao.nome}", 0).coerceIn(0, (itensAtuais.size - 1).coerceAtLeast(0))
                    statusBomba = itensAtuais.getOrNull(hmSelecionado)?.statusPadrao ?: "LIGADA"
                    
                    hmAdapter.atualizarLista(itensAtuais)
                    
                    if (novaEstacao.nome == "DET-01") {
                        val meio = Int.MAX_VALUE / 2
                        val offset = meio - (meio % itensAtuais.size) + hmSelecionado
                        (carrosselHm.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(offset, 0)
                    } else {
                        (carrosselHm.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(hmSelecionado, 0)
                    }
                }
                atualizarCardAzul()
                atualizarLabelsEBloqueio()
                aplicarStatus()
                atualizarUIParaModoNA()
                restaurarPreviews()
            }
        }
        val meio = Int.MAX_VALUE / 2
        val targetEstacaoIdx = estacoes.indexOf(estacaoSelecionada).coerceAtLeast(0)
        carrosselEstacoes.scrollToPosition(meio - (meio % estacoes.size) + targetEstacaoIdx)
    }

    private fun carregarCarrosselNA() {
        instalarParadaDeAutoScrollAoTocar(carrosselHm)
        val lagoAdapter = LagoNAAdapter(lagosNA) { idx ->
            lagoNASelecionado = idx
            SecurePrefs.get(this, "inspetor_prefs").edit()
                .putInt("last_lago_na", lagoNASelecionado)
                .apply()
                
            atualizarCardAzulNA()
            atualizarLabelsEBloqueio() 
            restaurarPreviews()
            (carrosselHm.adapter as? LagoNAAdapter)?.notifyDataSetChanged()
        }
        carrosselHm.adapter = lagoAdapter
        
        val meio = Int.MAX_VALUE / 2
        val offset = meio - (meio % lagosNA.size.coerceAtLeast(1)) + lagoNASelecionado
        (carrosselHm.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(offset, 0)
        
        atualizarCardAzulNA()
        atualizarLabelsEBloqueio() 
    }
    private fun iniciarAutoScrollTutorial(recycler: RecyclerView, deslocamentoPx: Int, intervaloMs: Long) {
        pararAutoScrollTutorial()
        tutorialAutoScrollRecycler = recycler

        val passo = deslocamentoPx.coerceAtLeast(120)
        var direcao = 1

        val loop = object : Runnable {
            override fun run() {
                if (tutorialAutoScrollRecycler !== recycler || !recycler.isAttachedToWindow) return
                recycler.smoothScrollBy(direcao * passo, 0)
                direcao *= -1
                recycler.postDelayed(this, intervaloMs)
            }
        }

        tutorialAutoScrollRunnable = loop
        recycler.removeCallbacks(loop)
        recycler.post(loop)
    }

    private fun pararAutoScrollTutorial(recycler: RecyclerView? = null) {
        val alvo = recycler ?: tutorialAutoScrollRecycler ?: return
        tutorialAutoScrollRunnable?.let { alvo.removeCallbacks(it) }
        if (tutorialAutoScrollRecycler === alvo) tutorialAutoScrollRecycler = null
        tutorialAutoScrollRunnable = null
    }

    private fun instalarParadaDeAutoScrollAoTocar(recycler: RecyclerView) {
        if (recycler.getTag(R.id.carrosselEstacoes) == true) return

        recycler.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_MOVE) {
                    pararAutoScrollTutorial(rv)
                }
                return false
            }
        })

        recycler.setTag(R.id.carrosselEstacoes, true)
    }

    private fun atualizarCardAzul() {
        if (isModoNA()) { atualizarCardAzulNA(); return }
        if (itensAtuais.isEmpty()) return
        val item = itensAtuais[hmSelecionado.coerceIn(0, itensAtuais.size - 1)]
        tvCardAzulSub.text         = item.cardAzulSub
        tvBombaCorrespondente.text = item.cardAzulLabel
    }

    private fun atualizarCardAzulNA() {
        tvCardAzulSub.text         = "ARB'S e LAGOs"
        tvBombaCorrespondente.text = lagosNA.getOrNull(lagoNASelecionado)?.nomeCard ?: "—"
    }

    private fun atualizarLabelsEBloqueio() {
        if (isModoNA()) {
            tvLabelHidro.text = "RÉGUA"
            tvLabelBomba.text = "FORA DA RÉGUA?"
            val lagoAtual = lagosNA.getOrNull(lagoNASelecionado)
            val isExtravasor = lagoAtual?.abreviacao.equals("DT2-ex", ignoreCase = true) || lagoAtual?.abreviacao.equals("CP-ex", ignoreCase = true)
            setBoxBombaAtiva(!isExtravasor)
            btnStatus.visibility = View.INVISIBLE
            if (isExtravasor) { foraDeNA = false; aplicarToggleForaNA() }
            return
        }

        if (itensAtuais.isEmpty()) return
        val item    = itensAtuais[hmSelecionado.coerceIn(0, itensAtuais.size - 1)]
        val isDet01 = estacaoSelecionada.nome == "DET-01"

        when (item.tipo) {
            "HM", "HM_VAZAO" -> {
                tvLabelHidro.text = if (item.tipo == "HM_VAZAO") "FLOWMETER" else if (isDet01) "HIDRÔMETRO" else "ESTRUTURA"
                tvLabelBomba.text = if (item.tipo == "HM_VAZAO") "VAZÃO" else if (isDet01) "BOMBA" else "VAZÃO"
                setBoxBombaAtiva(true)
            }
            "SIFAO" -> {
                tvLabelHidro.text = if (item.id == "SIF-SUP") "SIFÃO SUPERIOR" else "SIFÃO INFERIOR"
                tvLabelBomba.text = "VAZÃO"
                setBoxBombaAtiva(true) // Garante que o botão vai responder ao clique
                boxBomba.alpha = 0.4f  // Mas mantém ele com visual apagadinho
            }
            "SIMPLES", "SIMPLES_STATUS", "SIMPLES_STATUS_ADD" -> {
                tvLabelHidro.text = item.cardAzulLabel
                tvLabelBomba.text = "—"
                setBoxBombaAtiva(false)
            }
            "LIVRE" -> {
                tvLabelHidro.text = "EVIDÊNCIA"
                tvLabelBomba.text = "—"
                setBoxBombaAtiva(false)
            }
            // ═══ NOVO: card "SC" — rótulo do box principal vira "FOTO",
            // já que ali quem abre não é a câmera comum, é o scanner. ═══
            "SC" -> {
                tvLabelHidro.text = "FOTO"
                tvLabelBomba.text = "—"
                setBoxBombaAtiva(false)
            }
        }
        btnStatus.visibility = if (item.statusDisponiveis.isEmpty() || item.tipo == "SIFAO") View.INVISIBLE else View.VISIBLE
    }

    private fun atualizarUIParaModoNA() {
        if (isModoNA()) {
            tvLabelCarrossel.text = "INSPECIONAR"
            btnToggleForaNA.visibility = View.VISIBLE
            aplicarToggleForaNA()
            imgBombaPreview.visibility = View.GONE
            imgBombaIcon.visibility    = View.GONE
        } else {
            // ═══ NOVO: card "SC" troca o rótulo do carrossel pra "DIGITALIZAR" ═══
            tvLabelCarrossel.text = if (estacaoSelecionada.nome == "SC") "DIGITALIZAR" else "INSPECIONAR"
            btnToggleForaNA.visibility = View.GONE
            imgBombaIcon.visibility    = View.VISIBLE
            imgBombaPreview.visibility = View.GONE
        }
    }

    private fun setBoxBombaAtiva(ativa: Boolean) {
        boxBomba.alpha       = if (ativa) 1f else 0.35f
        boxBomba.isClickable = ativa
        boxBomba.isFocusable = ativa
    }

    @Suppress("DEPRECATION")
    private fun abrirCameraComCameraX(fase: String) {
        // ═══ NOVO: card "SC" agora usa a câmera normal do app (igual às
        // outras estações). A foto crua é interceptada logo depois, no
        // onActivityResult, e enviada pra tela de recorte própria do
        // INSPETOR (ver mostrarDialogRecorteSC) — sem Google no meio. ═══
        captureFase = fase
        
        // ═══ FORÇA A PROPORÇÃO 4:5 SEMPRE QUE ABRIR A CÂMERA ═══
        var ratio = ConfiguracoesActivity.PROP_4x5
        SecurePrefs.get(this, ConfiguracoesActivity.PREFS_NAME)
            .edit().putString(ConfiguracoesActivity.PREF_PROPORCAO, ratio).apply()
            
        var aplicarCorte = isEstacaoComFormatoConfiguravel()
        var isExtravasor = false
        var mostrarMira = false 

        // ═══ Scanner de Documentos precisa da resolução nativa da câmera
        // (sem o teto de 1920px aplicado às demais estações) — depois de
        // recortar só a página de dentro da foto inteira, sobra bem menos
        // pixel pra um texto pequeno/manuscrito ficar legível. ═══
        val itemParaEstaCaptura = itensAtuais.getOrNull(hmSelecionado)
        val ehScannerDocumento = fase == "hidro" && itemParaEstaCaptura?.tipo == "SC"

        if (fase == "bomba" && estacaoSelecionada.nome == "DET-01") {
            val itemAtual = itensAtuais.getOrNull(hmSelecionado)
            if (itemAtual != null && itemAtual.tipo == "HM") {
                mostrarMira = true
            }
        }

        // ── REGRA 1: Se for N.A. ──
        if (fase == "na_regua" && isModoNA()) {
            val lago = lagosNA.getOrNull(lagoNASelecionado)
            if (lago?.abreviacao.equals("DT2-ex", ignoreCase = true) || lago?.abreviacao.equals("CP-ex", ignoreCase = true)) {
                aplicarCorte = false
                ratio = "full"
                isExtravasor = true 
            } else {
                if (ratio != ConfiguracoesActivity.PROP_3x4 && ratio != ConfiguracoesActivity.PROP_4x5) {
                    ratio = ConfiguracoesActivity.PROP_4x5
                }
            }
        } 
        // ── REGRA 2: Outros itens (Galeria, Calha, Sifões e LIVRE) ──
        else {
            val itemAtual = itensAtuais.getOrNull(hmSelecionado)
            val ehItemHorizontal = itemAtual?.tipo == "SIMPLES" || itemAtual?.tipo == "SIFAO" || itemAtual?.tipo == "LIVRE"

            if (ehItemHorizontal) {
                aplicarCorte = false
                ratio = "full"
                // Habilita a rotação para evitar que fotos tiradas na horizontal fiquem viradas
                isExtravasor = true 
            }
        }

        startActivityForResult(
            Intent(this, CameraCaptureActivity::class.java).apply {
                putExtra(CameraCaptureActivity.EXTRA_RATIO, ratio)
                putExtra(CameraCaptureActivity.EXTRA_APLICAR_CORTE, aplicarCorte)
                putExtra(CameraCaptureActivity.EXTRA_IS_EXTRAVASOR, isExtravasor)
                putExtra(CameraCaptureActivity.EXTRA_ALTA_RESOLUCAO, ehScannerDocumento)
                putExtra("extra_mostrar_mira", mostrarMira)
                putExtra("extra_is_na", fase == "na_regua" && isModoNA()) 
            },
            REQUEST_CAMERA_CAPTURE
        )
    }


    @Suppress("DEPRECATION")
    override fun onActivityResult(req: Int, result: Int, data: Intent?) {
        super.onActivityResult(req, result, data)

        if (req == REQUEST_CAMERA_CAPTURE && result == RESULT_OK && data != null) {
            val photoPath = data.getStringExtra(CameraCaptureActivity.RESULT_PHOTO_PATH)
            if (!photoPath.isNullOrEmpty()) {
                val bmp = ImageHelper.carregarComExif(photoPath) ?: return
                val agora = SimpleDateFormat("dd.MM.yyyy // HH:mm'h'", Locale.getDefault()).format(Date())

                iniciarTimerLimpeza()

                when (captureFase) {
                    "na_regua" -> {
                        val lago = lagosNA.getOrNull(lagoNASelecionado) ?: return
                        lago.fotoRegua?.recycle()
                        lago.fotoRegua = bmp
                        lago.dataHora = agora
                        lago.fotoVeioDoCofre = false // foto nova da câmera, não é do Cofre
                    }
                    "hidro" -> {
                        val item = itensAtuais.getOrNull(hmSelecionado) ?: return

                        // ═══ NOVO: card "SC" não salva a foto crua direto —
                        // detecta as bordas automaticamente (sem Google/
                        // OpenCV, só Kotlin) e abre a tela de recorte já
                        // com o quadrilátero sugerido (ver mostrarDialogRecorteSC). ═══
                        if (item.tipo == "SC") {
                            captureFase = ""
                            scope.launch {
                                val cantosDetectados = withContext(Dispatchers.Default) {
                                    DetectorBordaEngine.detectarCantos(bmp)
                                }
                                mostrarDialogRecorteSC(bmp, item, cantosDetectados)
                            }
                            return
                        }

                        val eraNulo = item.fotoSup == null 
                        item.fotoSup?.recycle(); item.fotoSup = bmp
                        item.dataHoraSup = agora
                        item.fotoSupVeioDoCofre = false // foto nova da câmera, não é do Cofre
                        
                        if (item.tipo == "SIFAO") {
                            val supTemFoto = itensAtuais.find { it.id == "SIF-SUP" }?.fotoSup != null
                            val infTemFoto = itensAtuais.find { it.id == "SIF-INF" }?.fotoSup != null
                            if (supTemFoto && infTemFoto && eraNulo) {
                                boxHidrometro.postDelayed({ mostrarDialogVazao() }, 500)
                            }
                        } else if (item.tipo == "HM_VAZAO") {
                            val supTemFoto = item.fotoSup != null
                            val infTemFoto = item.fotoInf != null
                            if (supTemFoto && infTemFoto && eraNulo) {
                                boxHidrometro.postDelayed({ mostrarDialogVazao() }, 500)
                            }
                        }
                    }
                    "bomba" -> {
                        val item = itensAtuais.getOrNull(hmSelecionado) ?: return
                        val eraNulo = item.fotoInf == null
                        item.fotoInf?.recycle(); item.fotoInf = bmp
                        item.dataHoraInf = agora
                        item.fotoInfVeioDoCofre = false // foto nova da câmera, não é do Cofre
                        
                        if (item.tipo == "HM_VAZAO") {
                            val supTemFoto = item.fotoSup != null
                            val infTemFoto = item.fotoInf != null
                            if (supTemFoto && infTemFoto && eraNulo) {
                                boxBomba.postDelayed({ mostrarDialogVazao() }, 500)
                            }
                        }
                    }
                }
                captureFase = ""
                restaurarPreviews()
                return
            }
        }

        if (req == 203 && result == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) { }

                var bmp = decodificarBitmapSeguro(uri) ?: return
                var aplicarCorte = isEstacaoComFormatoConfiguravel()
                var ratio = ImageHelper.lerProporcao(this)
                val agora = SimpleDateFormat("dd.MM.yyyy // HH:mm'h'", Locale.getDefault()).format(Date())

                iniciarTimerLimpeza()

                when (galeriaFaseAtual) {
                    "na_regua" -> {
                        val lago = lagosNA.getOrNull(lagoNASelecionado) ?: return
                        val isExtravasor = lago.abreviacao.equals("DT2-ex", ignoreCase = true) ||
                                           lago.abreviacao.equals("CP-ex",  ignoreCase = true)
                        if (isExtravasor) { 
                            aplicarCorte = false
                            ratio = "full" 
                        } else if (ratio != ConfiguracoesActivity.PROP_3x4 && ratio != ConfiguracoesActivity.PROP_4x5) {
                            ratio = ConfiguracoesActivity.PROP_4x5
                        }
                        
                        if (aplicarCorte) bmp = ImageHelper.recortarPorProporcao(bmp, ratio)
                        lago.fotoRegua?.recycle()
                        lago.fotoRegua = bmp
                        lago.dataHora  = agora
                        lago.fotoVeioDoCofre = false // foto nova da galeria, não é do Cofre
                    }
                    "hidro" -> {
                        if (isModoNA()) {
                            val lago = lagosNA.getOrNull(lagoNASelecionado) ?: return
                            lago.fotoRegua?.recycle(); lago.fotoRegua = bmp; lago.dataHora = agora
                            lago.fotoVeioDoCofre = false // foto nova da galeria, não é do Cofre
                        } else {
                            if (isModoNA()) { aplicarCorte = false; ratio = "full" }
                            if (aplicarCorte) bmp = ImageHelper.recortarPorProporcao(bmp, ratio)
                            val item = itensAtuais.getOrNull(hmSelecionado) ?: return
                            val eraNulo = item.fotoSup == null
                            item.fotoSup?.recycle(); item.fotoSup = bmp; item.dataHoraSup = agora
                            item.fotoSupVeioDoCofre = false // foto nova da galeria, não é do Cofre
                            if (item.tipo == "HM_VAZAO" && item.fotoSup != null && item.fotoInf != null && eraNulo) {
                                boxHidrometro.postDelayed({ mostrarDialogVazao() }, 500)
                            }
                        }
                    }
                    "bomba" -> {
                        if (aplicarCorte) bmp = ImageHelper.recortarPorProporcao(bmp, ratio)
                        val item = itensAtuais.getOrNull(hmSelecionado) ?: return
                        val eraNulo = item.fotoInf == null
                        item.fotoInf?.recycle(); item.fotoInf = bmp; item.dataHoraInf = agora
                        item.fotoInfVeioDoCofre = false // foto nova da galeria, não é do Cofre
                        if (item.tipo == "HM_VAZAO" && item.fotoSup != null && item.fotoInf != null && eraNulo) {
                            boxBomba.postDelayed({ mostrarDialogVazao() }, 500)
                        }
                    }
                }

                restaurarPreviews()
            } catch (_: Exception) {}
            return
        }

        if (req == REQUEST_COFRE_SELECAO && result == RESULT_OK) {
            val caminho = data?.getStringExtra(CofreActivity.EXTRA_CAMINHO_SELECIONADO) ?: return
            val arquivo = File(caminho)
            val bmp = CofreManager.carregar(arquivo) ?: return
            val agora = SimpleDateFormat("dd.MM.yyyy // HH:mm'h'", Locale.getDefault()).format(Date())

            iniciarTimerLimpeza()

            // ═══ A foto que volta do Cofre já é um registro finalizado
            // (limpa, HD) — entra direto no mesmo slot que uma foto da
            // Galeria ocuparia, sem reaplicar corte/proporção.
            //
            // IMPORTANTE: marcamos "veio do Cofre" = true em cada slot que
            // recebe essa foto. Isso é lido em gerarRegistroAssincrono() e
            // no fluxo do N.A. pra NÃO chamar CofreManager.salvarSeNovo de
            // novo pra essa foto — senão toda vez que o usuário gerasse o
            // registro de novo, a MESMA foto que já veio do Cofre seria
            // salva ali dentro outra vez, duplicando o acervo. ═══
            when (galeriaFaseAtual) {
                "na_regua" -> {
                    val lago = lagosNA.getOrNull(lagoNASelecionado) ?: return
                    lago.fotoRegua?.recycle()
                    lago.fotoRegua = bmp
                    lago.dataHora = agora
                    lago.fotoVeioDoCofre = true
                }
                "hidro" -> {
                    if (isModoNA()) {
                        val lago = lagosNA.getOrNull(lagoNASelecionado) ?: return
                        lago.fotoRegua?.recycle(); lago.fotoRegua = bmp; lago.dataHora = agora
                        lago.fotoVeioDoCofre = true
                    } else {
                        val item = itensAtuais.getOrNull(hmSelecionado) ?: return
                        val eraNulo = item.fotoSup == null
                        item.fotoSup?.recycle(); item.fotoSup = bmp; item.dataHoraSup = agora
                        item.fotoSupVeioDoCofre = true
                        if (item.tipo == "HM_VAZAO" && item.fotoSup != null && item.fotoInf != null && eraNulo) {
                            boxHidrometro.postDelayed({ mostrarDialogVazao() }, 500)
                        }
                    }
                }
                "bomba" -> {
                    val item = itensAtuais.getOrNull(hmSelecionado) ?: return
                    val eraNulo = item.fotoInf == null
                    item.fotoInf?.recycle(); item.fotoInf = bmp; item.dataHoraInf = agora
                    item.fotoInfVeioDoCofre = true
                    if (item.tipo == "HM_VAZAO" && item.fotoSup != null && item.fotoInf != null && eraNulo) {
                        boxBomba.postDelayed({ mostrarDialogVazao() }, 500)
                    }
                }
            }
            restaurarPreviews()
            return
        }
    }


    // ── Runnable de hold para a galeria (mantido em propriedade para poder cancelar) ──
    private var holdGaleriaRunnable: Runnable? = null
    private var holdGaleriaFase: String = ""

    private fun setupCaixasCamera() {
        boxHidrometro.setOnClickListener { v ->
            val item = itensAtuais.getOrNull(hmSelecionado)
            
            // ── A SUA REGRA DE OURO: Tem que ter foto e ser HM ou ARB ──
            val temFoto = item?.fotoSup != null
            val tipoPermitido = item?.tipo == "HM" || item?.tipo == "HM_VAZAO" || item?.tipo == "SIMPLES_STATUS"
            
            if (!isModoNA() && temFoto && tipoPermitido) {
                // JÁ TEM FOTO: Ativa a escuta do duplo clique para abrir o Diálogo
                val tempoAtual = System.currentTimeMillis()
                if (tempoAtual - tempoUltimoCliqueHidro < 450) {
                    tempoUltimoCliqueHidro = 0
                    mostrarDialogLeituraManual()
                } else {
                    tempoUltimoCliqueHidro = tempoAtual
                    v.postDelayed({
                        if (tempoUltimoCliqueHidro == tempoAtual) {
                            abrirCameraComCameraX("hidro")
                        }
                    }, 450)
                }
            } else {
                // NÃO TEM FOTO (Ou é N.A.): Abre a câmera instantaneamente no primeiro clique!
                if (isModoNA()) abrirCameraComCameraX("na_regua")
                else if (item != null) abrirCameraComCameraX("hidro")
            }
        }

        boxHidrometro.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    animarPressDown(v)
                    holdGaleriaFase = if (isModoNA()) "na_regua" else "hidro"
                    holdGaleriaRunnable = Runnable {
                        vibrarForte()
                        mostrarDialogGaleria(holdGaleriaFase)
                        holdGaleriaRunnable = null
                    }
                    v.postDelayed(holdGaleriaRunnable!!, 800L)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    animarPressUp(v)
                    val holdFoiConsumido = holdGaleriaRunnable == null
                    holdGaleriaRunnable?.let { v.removeCallbacks(it) }
                    holdGaleriaRunnable = null
                    if (!holdFoiConsumido && event.actionMasked == MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                }
            }
            true
        }

        boxBomba.setOnClickListener { v ->
            val item = itensAtuais.getOrNull(hmSelecionado)
            if (isModoNA() || (v.alpha <= 0.5f && item?.tipo != "SIFAO")) return@setOnClickListener
            val tempoAtual = System.currentTimeMillis()

            if (tempoAtual - tempoUltimoCliqueBomba < 450) {
                tempoUltimoCliqueBomba = 0
                if (item?.tipo == "SIFAO") {
                    val supTem = itensAtuais.find { it.id == "SIF-SUP" }?.fotoSup != null
                    val infTem = itensAtuais.find { it.id == "SIF-INF" }?.fotoSup != null
                    if (!supTem || !infTem) {
                        Toast.makeText(this@DashboardActivity, "Para informar a vazão, tire as duas fotos do Sifão primeiro.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    mostrarDialogVazao()
                } else if (item?.tipo == "HM_VAZAO") {
                    val supTem = item.fotoSup != null
                    val infTem = item.fotoInf != null
                    if (!supTem || !infTem) {
                        Toast.makeText(this@DashboardActivity, "Para informar a vazão, tire as duas fotos do Flowmeter primeiro.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    mostrarDialogVazao()
                }
            } else {
                tempoUltimoCliqueBomba = tempoAtual
                if (item?.tipo != "SIFAO") {
                    v.postDelayed({
                        if (tempoUltimoCliqueBomba == tempoAtual) abrirCameraComCameraX("bomba")
                    }, 450)
                }
            }
        }

        boxBomba.setOnTouchListener { v, event ->
            if (!v.isClickable) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    animarPressDown(v)
                    val item = itensAtuais.getOrNull(hmSelecionado)
                    val boxAtiva = !isModoNA() && v.alpha > 0.5f && item?.tipo != "SIFAO"
                    if (boxAtiva) {
                        holdGaleriaFase = "bomba"
                        holdGaleriaRunnable = Runnable {
                            vibrarForte()
                            mostrarDialogGaleria("bomba")
                            holdGaleriaRunnable = null
                        }
                        v.postDelayed(holdGaleriaRunnable!!, 800L)
                    } else {
                        holdGaleriaRunnable = Runnable {}
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    animarPressUp(v)
                    val item = itensAtuais.getOrNull(hmSelecionado)
                    if (item?.tipo == "SIFAO") v.postDelayed({ v.alpha = 0.4f }, 110L) 
                    val naoExecutou = holdGaleriaRunnable != null
                    holdGaleriaRunnable?.let { v.removeCallbacks(it) }
                    holdGaleriaRunnable = null
                    if (naoExecutou && event.actionMasked == MotionEvent.ACTION_UP) v.performClick()
                }
            }
            true
        }
    }
    // ── Nova função para uma vibração mais robusta ──
    private fun vibrarForte() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(80, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(80)
        }
    }
    // ── Fase atual da galeria: "hidro", "bomba" ou "na_regua" ──────────
    private var galeriaFaseAtual: String = "hidro"

    /**
     * Diálogo exibido após segurar 2s em qualquer caixa de foto.
     * Título simples, dois botões: ABRIR GALERIA ou CANCELAR.
     *
     * @param fase "hidro" | "bomba" | "na_regua"
     */
    private fun mostrarDialogGaleria(fase: String) {
        galeriaFaseAtual = fase

        // ═══ Mesmo padrão visual do diálogo de vazão: cartão custom com
        // cantos arredondados em vez do AlertDialog.Builder padrão (texto
        // puro), janela transparente para os cantos do CardView aparecerem. ═══
        val view = layoutInflater.inflate(R.layout.dialog_escolha_origem_foto, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        view.findViewById<View>(R.id.btnOrigemGaleria).setOnClickListener {
            dialog.dismiss()
            abrirGaleria()
        }
        view.findViewById<View>(R.id.btnOrigemCofre).setOnClickListener {
            dialog.dismiss()
            abrirCofreParaSelecao()
        }
        view.findViewById<View>(R.id.btnOrigemCancelar).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    /**
     * Abre o Cofre em modo seleção: o usuário escolhe uma foto .INS já
     * salva e ela volta pro fluxo de edição normal (mesmo destino que uma
     * foto vinda da Galeria: régua N.A., hidrômetro ou bomba, conforme a
     * fase que disparou o Long Press).
     */
    private fun abrirCofreParaSelecao() {
        val intent = Intent(this, CofreActivity::class.java)
            .putExtra(CofreActivity.EXTRA_MODO_SELECAO, true)
        startActivityForResult(intent, REQUEST_COFRE_SELECAO)
    }

    /**
     * Abre o seletor de imagens real do Android.
     *
     * Usa ACTION_OPEN_DOCUMENT com EXTRA_ALLOW_MULTIPLE=false, que acessa
     * o DocumentsUI nativo — permite navegar por Fotos, Downloads, Drive,
     * cartão SD e qualquer provedor instalado (WhatsApp, Telegram etc.).
     * Fallback para ACTION_PICK se o DocumentsUI não estiver disponível.
     */
    @Suppress("DEPRECATION")
    private fun abrirGaleria() {
        try {
            // Tenta abrir o Photo Picker moderno (muito mais rápido e nativo para fotos)
            val intent = Intent(MediaStore.ACTION_PICK_IMAGES)
            intent.type = "image/*"
            startActivityForResult(intent, 203)
        } catch (e: Exception) {
            // Fallback de segurança caso o aparelho seja antigo e não suporte a API nova
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, 203)
        }
    }

    private fun decodificarBitmapSeguro(uri: android.net.Uri): Bitmap? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

            val maxSide = 1920
            var sample = 1
            while (boundsOpts.outWidth / sample > maxSide || boundsOpts.outHeight / sample > maxSide) sample *= 2

            val decOpts = BitmapFactory.Options().apply {
                inSampleSize     = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable         = true
            }
            var bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decOpts) } ?: return null

            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val exif = ExifInterface(input)
                    val ori  = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    val m = Matrix()
                    when (ori) {
                        ExifInterface.ORIENTATION_ROTATE_90  -> m.postRotate(90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                    }
                    if (!m.isIdentity) {
                        val rot = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                        if (rot != bmp) { bmp.recycle(); bmp = rot }
                    }
                }
            } catch (_: Exception) {}
            bmp
        } catch (_: Exception) { null }
    }

    private fun setupBotaoToggleForaNA() {
        btnToggleForaNA.setOnClickListener {
            foraDeNA = !foraDeNA
            aplicarToggleForaNA()
        }
    }

    private fun aplicarToggleForaNA() {
        tvToggleForaNA.text = if (foraDeNA) "SIM" else "NÃO"
        btnToggleForaNA.setCardBackgroundColor(Color.parseColor(if (foraDeNA) "#EF4444" else "#22C55E"))
    }

    private fun mostrarDialogVazao() {
        val item = itensAtuais.getOrNull(hmSelecionado) ?: return
        
        val view = layoutInflater.inflate(R.layout.dialog_vazao, null)
        val builder = AlertDialog.Builder(this).setView(view).setCancelable(false)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        view.findViewById<View>(R.id.btnVazaoNao).setOnClickListener {
            if (item.tipo == "SIFAO") {
                itensAtuais.find { it.id == "SIF-SUP" }?.statusVazao = "SEM VAZÃO"
                itensAtuais.find { it.id == "SIF-INF" }?.statusVazao = "SEM VAZÃO"
            } else {
                item.statusVazao = "SEM VAZÃO"
            }
            dialog.dismiss()
        }
        
        view.findViewById<View>(R.id.btnVazaoSim).setOnClickListener {
            if (item.tipo == "SIFAO") {
                itensAtuais.find { it.id == "SIF-SUP" }?.statusVazao = "COM VAZÃO"
                itensAtuais.find { it.id == "SIF-INF" }?.statusVazao = "COM VAZÃO"
            } else {
                item.statusVazao = "COM VAZÃO"
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    // ═══════════════════════════════════════════════════════════════════
    //  NOVO: Diálogo de LEITURA MANUAL
    //  Aberto via DUPLO CLIQUE no boxHidrometro quando há foto e o item
    //  é do tipo HM, HM_VAZAO, ou a estação é DET-01.
    //  Elementos:
    //   • Foto capturada com zoom (pinch + duplo toque)
    //   • EditText p/ digitar o valor (sem máscara que force zeros)
    //   • Botão único: SALVAR DADOS (#EFF6FF / texto #2563EB)
    //  Ao salvar:
    //   • item.leituraManual = valor cru digitado (ex: "55.7")
    //   • Persiste no SharedPreferences "leituras_flw_hidro" para a
    //     ListaNAActivity exibir no modo "CONTROLE FLW e HIDRO".
    // ═══════════════════════════════════════════════════════════════════
    private fun mostrarDialogLeituraManual() {
        val item = itensAtuais.getOrNull(hmSelecionado) ?: return
        val foto = item.fotoSup ?: run {
            Toast.makeText(this, "Tire a foto primeiro.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = layoutInflater.inflate(R.layout.dialog_leitura_manual, null)
        dialog.setContentView(view)
        
        val tvTitulo  = view.findViewById<TextView>(R.id.tvLMTitulo)
        val tvHint    = view.findViewById<TextView>(R.id.tvLMHint)
        val etValor   = view.findViewById<EditText>(R.id.etLeituraManual)
        val tvPreview = view.findViewById<TextView>(R.id.tvLMPreview)
        val imgFoto   = view.findViewById<ImageView>(R.id.imgLMFoto)
        val tvZoom    = view.findViewById<TextView>(R.id.tvLMZoomLevel)
        val btnFechar = view.findViewById<ImageView>(R.id.btnFecharLM)
        val btnSalvar = view.findViewById<View>(R.id.btnSalvarLM)

        val toggleSim   = view.findViewById<CardView>(R.id.toggleIncluirSimLM)
        val toggleNao   = view.findViewById<CardView>(R.id.toggleIncluirNaoLM)
        val tvToggleSim = view.findViewById<TextView>(R.id.tvToggleIncluirSimLM)
        val tvToggleNao = view.findViewById<TextView>(R.id.tvToggleIncluirNaoLM)
        val tvAvisoIncluir = view.findViewById<TextView>(R.id.tvAvisoIncluirLM)

        tvPreview.visibility = View.GONE

        // ── Tudo que não for Hidrômetro agora é tratado como Vazão (ARBs) ──
        val isHidrometro = item.tipo == "HM" || estacaoSelecionada.nome == "DET-01"
        val isVazao = !isHidrometro

        tvTitulo.text = if (isVazao) "Vazão (m³/hr)" else "Leitura do Hidrômetro"
        tvHint.text = if (isVazao) "Digite o valor lido no flowmeter ex: 37.5." else "Digite o valor lido no hidrômetro ex: 37.5."
        etValor.hint = if (isVazao) "000.0 m³/hr" else "000.00 x1m³/h"

        item.leituraManual?.let {
            etValor.setText(it)
            etValor.setSelection(it.length)
        }

        imgFoto.setImageBitmap(foto)
        setupZoom(imgFoto, tvZoom, null)

        var incluirSelecionado = item.incluirLeituraNaFoto

        fun atualizarHabilitacaoToggleIncluir(habilitado: Boolean) {
            toggleSim.isClickable = habilitado
            toggleSim.isFocusable = habilitado
            toggleNao.isClickable = habilitado
            toggleNao.isFocusable = habilitado
            tvAvisoIncluir.visibility = if (habilitado) View.GONE else View.VISIBLE

            if (!habilitado) {
                toggleSim.alpha = 0.35f
                toggleNao.alpha = 0.35f
            } else {
                toggleSim.alpha = 1f
                toggleNao.alpha = 1f
            }
        }

        fun atualizarVisualToggleIncluir() {
            if (incluirSelecionado) {
                toggleSim.setCardBackgroundColor(Color.parseColor("#2563EB"))
                tvToggleSim.setTextColor(Color.WHITE)
                toggleNao.setCardBackgroundColor(Color.parseColor("#F1F5F9"))
                tvToggleNao.setTextColor(Color.parseColor("#64748B"))
            } else {
                toggleNao.setCardBackgroundColor(Color.parseColor("#EF4444"))
                tvToggleNao.setTextColor(Color.WHITE)
                toggleSim.setCardBackgroundColor(Color.parseColor("#F1F5F9"))
                tvToggleSim.setTextColor(Color.parseColor("#64748B"))
            }
        }

        atualizarVisualToggleIncluir()
        atualizarHabilitacaoToggleIncluir(etValor.text.toString().trim().isNotEmpty())

        toggleSim.setOnClickListener {
            if (!toggleSim.isClickable) return@setOnClickListener
            incluirSelecionado = true
            atualizarVisualToggleIncluir()
        }
        toggleNao.setOnClickListener {
            if (!toggleNao.isClickable) return@setOnClickListener
            incluirSelecionado = false
            atualizarVisualToggleIncluir()
        }

        etValor.addTextChangedListener(object : TextWatcher {
            private var isEditing = false
            private val prefixo = if (isHidrometro) "+ " else ""
            private val sufixo = if (isHidrometro) " x1m³/h" else " m³/hr"

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isEditing || s == null) return
                isEditing = true
                var raw = s.toString()

                if (prefixo.isNotEmpty() && raw.startsWith(prefixo)) raw = raw.substring(prefixo.length)
                if (sufixo.isNotEmpty() && raw.endsWith(sufixo)) raw = raw.substring(0, raw.length - sufixo.length)
                
                raw = raw.replace("x1m³/h", "").replace("x1m³/", "").replace("x1m³", "").replace("x1m", "").replace("x1", "").replace("x", "")
                raw = raw.replace("m³/hr", "").replace("m³/", "").replace("m³", "").replace("m", "").replace("h", "").replace("r", "")
                raw = raw.replace("³", "").replace("/", "").replace("+", "").replace(" ", "")

                raw = raw.filter { it.isDigit() || it == '.' }
                val parts = raw.split('.')
                if (parts.size > 2) raw = parts[0] + "." + parts.drop(1).joinToString("")

                val formatted = if (raw.isEmpty()) "" else "$prefixo$raw$sufixo"

                if (s.toString() != formatted) {
                    etValor.setText(formatted)
                    val cursorPosition = if (formatted.isNotEmpty()) prefixo.length + raw.length else 0
                    etValor.setSelection(cursorPosition.coerceIn(0, formatted.length))
                }
                
                isEditing = false
                if (raw.isEmpty()) {
                    incluirSelecionado = false
                    atualizarVisualToggleIncluir()
                }
                atualizarHabilitacaoToggleIncluir(raw.isNotEmpty())
            }
        })

        btnFechar.setOnClickListener {
            esconderTeclado(etValor)
            dialog.dismiss()
        }

        btnSalvar.setOnClickListener {
            val valor = etValor.text.toString().trim()
            if (valor.isEmpty()) {
                Toast.makeText(this, "Digite um valor antes de salvar.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            item.leituraManual = valor
            item.incluirLeituraNaFoto = incluirSelecionado

            try {
                val prefs = SecurePrefs.get(this, "leituras_flw_hidro")
                val chave = "${estacaoSelecionada.nome}_${item.id}"
                val dh = SimpleDateFormat("dd.MM.yyyy // HH:mm'h'", Locale.getDefault()).format(Date())
                val obj = org.json.JSONObject().apply {
                    put("valor", valor)
                    put("tipo", item.tipo)
                    put("titulo", item.titulo)
                    put("estacao", estacaoSelecionada.nome)
                    put("dataHora", dh)
                }
                prefs.edit().putString(chave, obj.toString()).apply()
            } catch (_: Exception) { /* best-effort */ }

            esconderTeclado(etValor)
            Toast.makeText(this, "✓ Leitura salva", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.apply {
            val params = attributes
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            attributes = params
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        
        etValor.requestFocus()
        etValor.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etValor, InputMethodManager.SHOW_IMPLICIT)
        }, 250)
    }
    private fun setupStatus() {
        btnStatus.setOnClickListener {
            if (isModoNA()) return@setOnClickListener
            if (itensAtuais.isEmpty()) return@setOnClickListener
            val item = itensAtuais[hmSelecionado]
            if (item.statusDisponiveis.isEmpty()) return@setOnClickListener
            val idxAtual   = item.statusDisponiveis.indexOf(statusBomba)
            val proximoIdx = if (idxAtual != -1) (idxAtual + 1) % item.statusDisponiveis.size else 0
            statusBomba    = item.statusDisponiveis[proximoIdx]
            aplicarStatus()
        }
    }

    private fun aplicarStatus() {
        val corHex = when (statusBomba) {
            "LIGADO", "LIGADA", "COM VAZÃO" -> "#22C55E"
            "ZERADO"                        -> "#F59E0B"
            "NENHUM"                        -> "#000000"
            else                            -> "#EF4444"
        }
        btnStatus.setCardBackgroundColor(Color.parseColor(corHex))
    }

    private fun setupBotaoGerar() {
        btnAdd.setOnClickListener {
            if (isModoNA()) {
                val lago = lagosNA.getOrNull(lagoNASelecionado) ?: return@setOnClickListener
                if (lago.fotoRegua == null) {
                    Toast.makeText(this, "Tire a foto da régua antes de gerar.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                mostrarDialogResultadoNA()
                return@setOnClickListener
            }

            if (itensAtuais.isEmpty()) return@setOnClickListener
            val item = itensAtuais[hmSelecionado]

            // ═══ NOVO: LIVRE tem fluxo próprio — foto + texto livre, SEM
            // salvar em Histórico nem na Galeria 2 (Cofre de Imagens).
            // Por isso intercepta ANTES de gerarRegistroAssincrono(), que é
            // quem faz esses dois salvamentos para os outros tipos. ═══
            if (item.tipo == "LIVRE") {
                if (item.fotoSup == null) {
                    Toast.makeText(this, "Tire pelo menos uma foto antes de gerar.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                mostrarDialogResultadoLivre(item)
                return@setOnClickListener
            }

            // ═══ NOVO: SC tem fluxo próprio — documento já vem processado
            // pelo ML Kit, só falta a bainha de edição (luz/brilho/vetorização).
            // Assim como LIVRE, não passa por gerarRegistroAssincrono(). ═══
            if (item.tipo == "SC") {
                if (item.fotoSup == null) {
                    Toast.makeText(this, "Digitalize um documento antes de gerar.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                mostrarDialogResultadoSC(item)
                return@setOnClickListener
            }

            val temFoto = when (item.tipo) {
                "HM", "HM_VAZAO"                    -> item.fotoSup != null
                "SIMPLES", "SIMPLES_STATUS",
                "SIMPLES_STATUS_ADD", "SIFAO"       -> item.fotoSup != null
                else                                -> false
            }
            if (!temFoto) {
                Toast.makeText(this, "Tire pelo menos uma foto antes de gerar.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            gerarRegistroAssincrono()
        }
    }

    private fun gerarRegistroAssincrono() {
        val toast = Toast.makeText(this, "Gerando registro…", Toast.LENGTH_SHORT)
        toast.show()
        val horaCaptura = SimpleDateFormat("dd.MM.yyyy // HH:mm'h'", Locale.getDefault()).format(Date())

        // ═══ OTIMIZAÇÃO MAXIMA E SEPARAÇÃO DE PASTAS ═══
        // Dispara o salvamento no Cofre de forma assíncrona (background real), 
        // separando Bomba de Hidrômetro, sem atrasar o carregamento da tela do usuário.
        if (itensAtuais.isNotEmpty()) {
            val item = itensAtuais[hmSelecionado.coerceIn(0, itensAtuais.size - 1)]
            
            scope.launch(Dispatchers.IO) {
                // ═══ Se a foto já veio de dentro do Cofre (usuário escolheu
                // pela Galeria 2), NÃO regravamos ela lá dentro de novo —
                // senão duplicaria a cada vez que o registro é gerado. ═══
                when (item.tipo) {
                    "HM" -> {
                        if (!item.fotoSupVeioDoCofre) item.fotoSup?.let { CofreManager.salvarSeNovo(this@DashboardActivity, it, estacaoSelecionada.nome, "HIDRÔMETRO-${item.id.padStart(2, '0')}") }
                        if (!item.fotoInfVeioDoCofre) item.fotoInf?.let { CofreManager.salvarSeNovo(this@DashboardActivity, it, estacaoSelecionada.nome, "BOMBA-${item.id.padStart(2, '0')}") }
                    }
                    "HM_VAZAO" -> {
                        if (!item.fotoSupVeioDoCofre) item.fotoSup?.let { CofreManager.salvarSeNovo(this@DashboardActivity, it, estacaoSelecionada.nome, "FLOWMETER ${item.id}") }
                        if (!item.fotoInfVeioDoCofre) item.fotoInf?.let { CofreManager.salvarSeNovo(this@DashboardActivity, it, estacaoSelecionada.nome, "VAZÃO ${item.id}") }
                    }
                    "SIMPLES_STATUS", "SIMPLES_STATUS_ADD" -> {
                        if (!item.fotoSupVeioDoCofre) item.fotoSup?.let { CofreManager.salvarSeNovo(this@DashboardActivity, it, estacaoSelecionada.nome, "FLOWMETER ${item.id}") }
                    }
                    "SIMPLES", "SIFAO" -> {
                        if (!item.fotoSupVeioDoCofre) item.fotoSup?.let { CofreManager.salvarSeNovo(this@DashboardActivity, it, estacaoSelecionada.nome, "ESTRUTURAS") }
                    }
                }
            }
        }

        scope.launch {
            val camadas = withContext(Dispatchers.Default) { gerarParBitmapRegistro(horaCaptura) }
            toast.cancel()

            var supFinal: String? = null
            var infFinal: String? = null

            if (itensAtuais.isNotEmpty()) {
                val item = itensAtuais[hmSelecionado.coerceIn(0, itensAtuais.size - 1)]
                val (sup, inf) = when (item.tipo) {
                    "HM"                                   -> null to statusBomba
                    "HM_VAZAO"                             -> if (deveGerarFlowmeterHibridoComoSimples(item)) statusBomba to null else statusBomba to item.statusVazao
                    "SIMPLES_STATUS", "SIMPLES_STATUS_ADD" -> statusBomba to null
                    "SIFAO"                                -> null to item.statusVazao
                    else                                   -> null to null
                }
                supFinal = sup
                infFinal = inf
            }
            
            mostrarDialogResultado(camadas, horaCaptura, supFinal, infFinal)
        }
    }


private fun mostrarDialogResultado(camadas: Triple<Bitmap, Bitmap, Bitmap>, horaCaptura: String, statusSup: String?, statusInf: String?) {
    var bmpAtualLimpo = camadas.first
    var bmpAtualOverlay = camadas.second
    var bmpAtualFinal = camadas.third

    val horaOriginal = horaCaptura
    var horaAtual = horaCaptura

    val view = layoutInflater.inflate(R.layout.dialog_registro_resultado, null)
    val d = android.app.AlertDialog.Builder(this).setView(view).create()
    d.window?.setBackgroundDrawableResource(android.R.color.transparent)

    val imgLimpa = view.findViewById<ImageView>(R.id.imgResultadoLimpo)
    val imgOverlay = view.findViewById<ImageView>(R.id.imgResultadoOverlay)
    val reguaEdicao = view.findViewById<ReguaVerticalView>(R.id.reguaEdicaoDialog)
    val btnToggleProporcao = view.findViewById<CardView>(R.id.btnToggleProporcaoCard)
    val tvToggleProporcao  = view.findViewById<TextView>(R.id.tvToggleProporcao)

    val ocultaToggleProporcao = estacaoSelecionada.nome in setOf("ARB-05", "ARB-06")
    btnToggleProporcao.visibility = if (ocultaToggleProporcao) View.GONE else View.VISIBLE

    fun atualizarTextoProporcao() {
        val propAtual = ImageHelper.lerProporcao(this@DashboardActivity)
        tvToggleProporcao.text = "◱  $propAtual"
    }
    atualizarTextoProporcao()

    btnToggleProporcao.setOnClickListener {
        val Sawyer = ImageHelper.lerProporcao(this@DashboardActivity)
        val lista = listOf(ConfiguracoesActivity.PROP_4x5, ConfiguracoesActivity.PROP_3x4, ConfiguracoesActivity.PROP_9x16, ConfiguracoesActivity.PROP_1x1, ConfiguracoesActivity.PROP_FULL)
        val novaProp = lista[(lista.indexOf(Sawyer) + 1) % lista.size]
        
        SecurePrefs.get(this, ConfiguracoesActivity.PREFS_NAME)
            .edit().putString(ConfiguracoesActivity.PREF_PROPORCAO, novaProp).apply()
        
        val novasCamadas = gerarParBitmapRegistro(horaAtual)
        bmpAtualLimpo = novasCamadas.first
        bmpAtualOverlay = novasCamadas.second
        bmpAtualFinal = novasCamadas.third
        
        imgLimpa.setImageBitmap(bmpAtualLimpo)
        imgOverlay.setImageBitmap(bmpAtualOverlay)
        atualizarTextoProporcao()
        
        val valorReaplicar = when (reguaEdicao.modoAtual) {
            ReguaVerticalView.Modo.BRILHO      -> reguaEdicao.valorBrilho
            ReguaVerticalView.Modo.NITIDEZ     -> reguaEdicao.valorNitidez
            ReguaVerticalView.Modo.VETORIZACAO -> reguaEdicao.valorVetorizacao
        }
        FiltroImagemHelper.aplicarFiltroAoVivo(imgLimpa, reguaEdicao.modoAtual, valorReaplicar)
    }

    imgLimpa.setImageBitmap(bmpAtualLimpo)
    imgOverlay.setImageBitmap(bmpAtualOverlay)
    
    reguaEdicao.onValorMudou = { modo, valor ->
        FiltroImagemHelper.aplicarFiltroAoVivo(imgLimpa, modo, valor)
    }

    run {
        val layoutBotoesSuperiores = view.findViewById<View>(R.id.layoutBotoesSuperiores)
        val proporcaoVisivel = btnToggleProporcao.visibility == View.VISIBLE
        layoutBotoesSuperiores.visibility = if (proporcaoVisivel) View.VISIBLE else View.GONE
    }
    view.findViewById<Button>(R.id.btnBaixar).setOnClickListener { botao ->
        botao.isEnabled = false
        scope.launch {
            try {
                val valorModoAtivo = when (reguaEdicao.modoAtual) {
                    ReguaVerticalView.Modo.BRILHO      -> reguaEdicao.valorBrilho
                    ReguaVerticalView.Modo.NITIDEZ     -> reguaEdicao.valorNitidez
                    ReguaVerticalView.Modo.VETORIZACAO -> reguaEdicao.valorVetorizacao
                }
                val finalEditado = withContext(Dispatchers.Default) {
                    FiltroImagemHelper.fundirCamadasParaSalvar(
                        bmpAtualLimpo, bmpAtualOverlay,
                        reguaEdicao.modoAtual, valorModoAtivo,
                        reguaEdicao.valorVetorizacao
                    )
                }

                if (itensAtuais.isNotEmpty()) {
                    val item = itensAtuais[hmSelecionado.coerceIn(0, itensAtuais.size - 1)]
                    withContext(Dispatchers.IO) {
                        salvarNoHistoricoGlobal(item.titulo, estacaoSelecionada.nome, statusSup, statusInf, null, finalEditado, bmpAtualLimpo, horaAtual)
                    }
                }

                val salvou = salvarImagemAsync(finalEditado)
                if (salvou) {
                    Toast.makeText(this@DashboardActivity, "✓ Imagem salva em Galeria/INSPETOR", Toast.LENGTH_SHORT).show()
                    d.dismiss()
                } else {
                    Toast.makeText(this@DashboardActivity, "Não foi possível salvar a imagem.", Toast.LENGTH_SHORT).show()
                }
            } finally {
                botao.isEnabled = true
            }
        }
    }

    view.findViewById<Button>(R.id.btnCompartilhar).setOnClickListener { botao ->
        botao.isEnabled = false
        scope.launch {
            try {
                val valorModoAtivo = when (reguaEdicao.modoAtual) {
                    ReguaVerticalView.Modo.BRILHO      -> reguaEdicao.valorBrilho
                    ReguaVerticalView.Modo.NITIDEZ     -> reguaEdicao.valorNitidez
                    ReguaVerticalView.Modo.VETORIZACAO -> reguaEdicao.valorVetorizacao
                }
                val finalEditado = withContext(Dispatchers.Default) {
                    FiltroImagemHelper.fundirCamadasParaSalvar(
                        bmpAtualLimpo, bmpAtualOverlay,
                        reguaEdicao.modoAtual, valorModoAtivo,
                        reguaEdicao.valorVetorizacao
                    )
                }

                if (itensAtuais.isNotEmpty()) {
                    val item = itensAtuais[hmSelecionado.coerceIn(0, itensAtuais.size - 1)]
                    withContext(Dispatchers.IO) {
                        salvarNoHistoricoGlobal(item.titulo, estacaoSelecionada.nome, statusSup, statusInf, null, finalEditado, bmpAtualLimpo, horaAtual)
                    }
                }

                val uri = prepararUriCompartilhamentoAsync(finalEditado)
                if (uri != null) {
                    compartilharUri(uri, "Registro")
                    d.dismiss()
                } else {
                    Toast.makeText(this@DashboardActivity, "Não foi possível compartilhar a imagem.", Toast.LENGTH_SHORT).show()
                }
            } finally {
                botao.isEnabled = true
            }
        }
    }
    view.findViewById<ImageView>(R.id.btnEditarHora).setOnClickListener {
        mostrarDialogEditarDataHora(
            horaAtual = horaAtual,
            horaOriginal = horaOriginal,
            onConfirmar = { novaHora ->
                val novasCamadas = gerarParBitmapRegistro(novaHora)
                bmpAtualOverlay = novasCamadas.second
                bmpAtualFinal = novasCamadas.third
                horaAtual = novaHora
                imgOverlay.setImageBitmap(bmpAtualOverlay)

                // Sincroniza a modificação com o SharedPreferences "Controle FLW e HIDRO"
                val itemAtual = itensAtuais.getOrNull(hmSelecionado.coerceIn(0, itensAtuais.size - 1))
                if (itemAtual != null) {
                    try {
                        val prefsLeitura = SecurePrefs.get(this@DashboardActivity, "leituras_flw_hidro")
                        val chave = "${estacaoSelecionada.nome}_${itemAtual.id}"
                        val jsonStr = prefsLeitura.getString(chave, null)
                        if (jsonStr != null) {
                            val obj = org.json.JSONObject(jsonStr)
                            obj.put("dataHora", novaHora)
                            prefsLeitura.edit().putString(chave, obj.toString()).apply()
                        }
                    } catch (_: Exception) {}
                }
            }
        )
    }

    view.findViewById<ImageView>(R.id.btnFecharDialog).setOnClickListener { d.dismiss() }
    d.show()
    d.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
}

    // ═══════════════════════════════════════════════════════════════════════
    //  Diálogo de edição manual de Data/Hora do carimbo (MISSÃO 2).
    //  horaAtual/horaOriginal seguem o formato "dd.MM.yyyy // HH:mm'h'".
    //  onConfirmar recebe a nova string já pronta nesse mesmo formato.
    // ═══════════════════════════════════════════════════════════════════════
    private fun mostrarDialogEditarDataHora(horaAtual: String, horaOriginal: String, onConfirmar: (String) -> Unit) {
        val view = layoutInflater.inflate(R.layout.dialog_editar_data_hora, null)
        val d = android.app.AlertDialog.Builder(this).setView(view).create()
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etData = view.findViewById<EditText>(R.id.etEditarData)
        val etHora = view.findViewById<EditText>(R.id.etEditarHora)

        // "dd.MM.yyyy // HH:mm'h'" → separa em partes "dd.MM.yyyy" e "HH:mm"
        fun partesDe(texto: String): Pair<String, String> {
            val partes = texto.split("//").map { it.trim() }
            val data = partes.getOrNull(0) ?: ""
            val hora = (partes.getOrNull(1) ?: "").removeSuffix("h").trim()
            return data to hora
        }

        val (dataInicial, horaInicial) = partesDe(horaAtual)
        etData.setText(dataInicial)
        etHora.setText(horaInicial)

        aplicarMascaraData(etData)
        aplicarMascaraHora(etHora)

        view.findViewById<ImageView>(R.id.btnRestaurarHoraOriginal).setOnClickListener {
            val (dataOrig, horaOrig) = partesDe(horaOriginal)
            etData.setText(dataOrig)
            etData.setSelection(etData.text.length)
            etHora.setText(horaOrig)
            etHora.setSelection(etHora.text.length)
            Toast.makeText(this, "Horário original restaurado", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.btnCancelarHora).setOnClickListener { d.dismiss() }

        view.findViewById<Button>(R.id.btnSalvarHora).setOnClickListener {
            val dataDigitada = etData.text.toString().trim()
            val horaDigitada = etHora.text.toString().trim()

            if (dataDigitada.length != 10 || horaDigitada.length != 5) {
                Toast.makeText(this, "Preencha data (DD.MM.AAAA) e hora (HH:mm) completas.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val novaHora = "$dataDigitada // ${horaDigitada}h"
            onConfirmar(novaHora)
            esconderTeclado(view)
            d.dismiss()
        }

        d.show()
    }

    // Máscara DD.MM.AAAA — mesmo estilo de aplicarMascaraNA, adaptado para data.
    private fun aplicarMascaraData(et: EditText) {
        et.addTextChangedListener(object : TextWatcher {
            private var isEditing = false
            override fun beforeTextChanged(s: CharSequence?, st: Int, cnt: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, before: Int, cnt: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (isEditing || s == null) return
                isEditing = true
                val digits = s.toString().filter { it.isDigit() }.take(8)
                val sb = StringBuilder()
                for (i in digits.indices) {
                    sb.append(digits[i])
                    if ((i == 1 || i == 3) && i != digits.lastIndex) sb.append('.')
                }
                val formatted = sb.toString()
                if (formatted != s.toString()) {
                    et.setText(formatted)
                    et.setSelection(formatted.length)
                }
                isEditing = false
            }
        })
    }

    // Máscara HH:mm — usuário digita só números, ':' é inserido automaticamente.
    private fun aplicarMascaraHora(et: EditText) {
        et.addTextChangedListener(object : TextWatcher {
            private var isEditing = false
            override fun beforeTextChanged(s: CharSequence?, st: Int, cnt: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, before: Int, cnt: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (isEditing || s == null) return
                isEditing = true
                val digits = s.toString().filter { it.isDigit() }.take(4)
                val sb = StringBuilder()
                for (i in digits.indices) {
                    sb.append(digits[i])
                    if (i == 1 && i != digits.lastIndex) sb.append(':')
                }
                val formatted = sb.toString()
                if (formatted != s.toString()) {
                    et.setText(formatted)
                    et.setSelection(formatted.length)
                }
                isEditing = false
            }
        })
    }

    private fun registrarFotoLimpaNoCofreSeNecessario(itemNome: String, grupoNome: String, bitmapLimpo: Bitmap) {
        try {
            CofreManager.salvarSeNovo(this, bitmapLimpo, grupoNome, itemNome)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun salvarImagemAsync(bmp: Bitmap): Boolean = withContext(Dispatchers.IO) {
        try {
            val nome = "${estacaoSelecionada.nome.replace(".", "")}_REG_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.jpg"
            val out: OutputStream? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, nome)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/INSPETOR")
                }
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)?.let { contentResolver.openOutputStream(it) }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "INSPETOR")
                dir.mkdirs()
                FileOutputStream(File(dir, nome))
            }
            out?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 100, it) } != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun prepararUriCompartilhamentoAsync(bmp: Bitmap): Uri? = withContext(Dispatchers.IO) {
        try {
            // ═══ Sobre "enviar em HD": do nosso lado a imagem já sai no
            // máximo que dá — resolução nativa da câmera (sem corte/
            // redimensionamento escondido) + JPEG qualidade 100. A partir
            // daqui, quem decide a qualidade final da FOTO enviada como
            // imagem é o próprio WhatsApp (o toggle de qualidade que ele
            // mostra antes de enviar) — isso não é algo que um app
            // terceiro consiga contornar sem deixar de ser reconhecido
            // como imagem (já tentamos: vira "documento genérico" sem
            // preview, ou PDF — nenhum dos dois é o que foi pedido aqui).
            // Então mantemos o de sempre: imagem de verdade, JPEG 100. ═══
            val file = File(cacheDir, "registro_temp.jpg")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            FileProvider.getUriForFile(this@DashboardActivity, "${packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun compartilharUri(uri: Uri, label: String) {
        val intentCompartilhar = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newUri(contentResolver, label, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intentCompartilhar, "Compartilhar registro"))
    }

       private fun salvarNoHistoricoGlobal(itemNome: String, grupoNome: String, statusSup: String?, statusInf: String?, valorNA: String?, bitmap: Bitmap? = null, bitmapLimpo: Bitmap? = null, dataHoraOverride: String? = null) {
        val prefs = SecurePrefs.get(this, "historico_prefs")
        val registrosAtuaisRaw = prefs.getString("registros_json", "[]") ?: "[]"
        try {
            var fotoPath: String? = null
            if (bitmap != null) {
                val dir = File(filesDir, "historico")
                if (!dir.exists()) dir.mkdirs()
                val nomeArquivo = "${grupoNome.replace(".", "").replace(" ", "_")}_${itemNome.replace(".", "").replace(" ", "_")}_${System.currentTimeMillis()}.jpg"
                val arquivo = File(dir, nomeArquivo)
                FileOutputStream(arquivo).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
                fotoPath = arquivo.absolutePath

                if (bitmapLimpo != null) {
                    try {
                        val arquivoClean = File(dir, "$nomeArquivo.clean")
                        FileOutputStream(arquivoClean).use { out ->
                            bitmapLimpo.compress(Bitmap.CompressFormat.JPEG, 100, out)
                        }
                    } catch (_: Exception) { /* clean é opcional; não impede o registro */ }
                }
            }
            val jsonArray = org.json.JSONArray(registrosAtuaisRaw)
            
            // Aplica a hora editada se existir, senão usa a hora atual
            val dhFinal = dataHoraOverride ?: SimpleDateFormat("dd.MM.yyyy // HH:mm'h'", Locale.getDefault()).format(Date())
            
            val novoRegistro = org.json.JSONObject().apply {
                put("id",       java.util.UUID.randomUUID().toString())
                put("grupo",    grupoNome)
                put("subtitulo", itemNome)
                put("dataHora", dhFinal)
                put("statusSuperior", statusSup ?: org.json.JSONObject.NULL)
                put("statusInferior", statusInf ?: org.json.JSONObject.NULL)
                put("valorNA",        valorNA   ?: org.json.JSONObject.NULL)
                put("fotoPath",       fotoPath  ?: org.json.JSONObject.NULL)
            }
            jsonArray.put(novoRegistro)
            prefs.edit().putString("registros_json", jsonArray.toString()).commit()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun aplicarMascaraNA(et: EditText) {
        et.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        et.hint = "00.00m"
        et.addTextChangedListener(object : TextWatcher {
            private var isEditing = false
            private var prevDigitLen = 0
            private var removendoSufixo = false
            override fun beforeTextChanged(s: CharSequence?, st: Int, cnt: Int, after: Int) {
                prevDigitLen = s?.count { it.isDigit() } ?: 0
                removendoSufixo = cnt == 1 && after == 0 && s != null && st in s.indices && (s[st] == '.' || s[st] == 'm' || s[st] == 'M')
            }
            override fun onTextChanged(s: CharSequence?, st: Int, before: Int, cnt: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (isEditing || s == null) return
                isEditing = true
                var digits = s.toString().filter { it.isDigit() }
                val apagando = digits.length < prevDigitLen || removendoSufixo
                if (removendoSufixo && digits.isNotEmpty()) digits = digits.dropLast(1)
                if (digits.length > 4) digits = digits.take(4)
                val formatted = when {
                    digits.length >= 4 -> "${digits[0]}${digits[1]}.${digits[2]}${digits[3]}m"
                    digits.length == 3 -> "${digits[0]}${digits[1]}.${digits[2]}"
                    digits.length == 2 -> if (!apagando) "${digits[0]}${digits[1]}." else "${digits[0]}${digits[1]}"
                    digits.length == 1 -> digits
                    else -> ""
                }
                if (formatted != s.toString()) {
                    et.setText(formatted)
                    et.setSelection(formatted.length)
                }
                isEditing = false
            }
        })
    }

    private fun esconderTeclado(v: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(v.windowToken, 0)
    }

    // ═══ Scroll suave e RÁPIDO ao abrir/fechar o teclado nos diálogos N.A. e
    // Histórico. Substitui o ScrollView.smoothScrollTo() padrão do Android, que
    // tem inércia/duração variável e por isso pode parecer "atrasado" ou frouxo.
    // Aqui a duração é fixa e curta (190ms) com uma curva rápida-mas-suave
    // (fast-out-slow-in), então o movimento começa imediatamente e assenta com
    // suavidade — sem parecer lento nem robótico. ═══
    private var scrollAnimatorAtivo: android.animation.ValueAnimator? = null
    private fun scrollSuaveRapido(scrollView: ScrollView, destinoY: Int) {
        scrollAnimatorAtivo?.cancel()
        val origemY = scrollView.scrollY
        if (origemY == destinoY) return
        scrollAnimatorAtivo = android.animation.ValueAnimator.ofInt(origemY, destinoY).apply {
            duration = 190L
            interpolator = android.view.animation.PathInterpolator(0.3f, 0f, 0.15f, 1f)
            addUpdateListener { anim -> scrollView.scrollTo(0, anim.animatedValue as Int) }
            start()
        }
    }
    // Mantida para compatibilidade — delega ao novo gerarParImagemFinalNA e
    // devolve apenas a versão carimbada.

    // Retorna (limpa, carimbada). A limpa é a foto recortada com fundo preto
    // SEM a tarja desenhada. A carimbada é uma cópia mutable da limpa com a
    // tarja desenhada. Salvar ambas evita o efeito de "tarja sobre tarja"
    // quando o Histórico precisa regenerar a imagem após uma edição.
    // ═══ Calcula as dimensões finais (W,H) da imagem do N.A. a partir da foto da régua.
    //     Extraído para ser reaproveitado pela prévia AO VIVO no diálogo (item 4). ═══
    private fun calcularDimensoesNA(foto: Bitmap): Pair<Int, Int> {
        val isLandscape = foto.width > foto.height
        return if (isLandscape) {
            val w = 2880 // ══ MUDOU AQUI (Era 1440) ══
            w to (w.toFloat() * foto.height / foto.width).toInt()
        } else {
            val w = 2160 // ══ MUDOU AQUI (Era 1080) ══
            w to (w.toFloat() * foto.height / foto.width).toInt().coerceIn(2160, 4320)
        }
    }

    // ═══ Gera SOMENTE a camada de overlay (tarja) do N.A., no tamanho W x H informado.
    //     Extraído para permitir atualização AO VIVO (a cada dígito do valor de N.A.
    //     ou a cada troca de hora) sem precisar re-cortar a foto pesada. ═══
    private fun gerarOverlayNA(lago: LagoNA, valorNA: String?, horaOverride: String, w: Int, h: Int, isPreview: Boolean = false, isGavetaFechada: Boolean = false): Pair<Bitmap, Float> {
        val isExtravasor = lago.abreviacao.equals("DT2-ex", ignoreCase = true) || lago.abreviacao.equals("CP-ex", ignoreCase = true)
        val overlayData = if (isExtravasor) {
            listOf("pin" to lago.nomeCard, "relogio" to horaOverride)
        } else {
            val naStr = when {
                foraDeNA -> "N.A: Fora do nível da régua."
                valorNA.isNullOrBlank() -> "N.A: " 
                else -> "N.A: ${valorNA.trimEnd('m')}m"
            }
            listOf("pin" to lago.nomeCard, "relogio" to horaOverride, "hidro" to naStr)
        }
        val bmpOverlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        
        // Recebe a altura calculada da barra
        val alturaBarra = ImageHelper.drawOverlayKV(Canvas(bmpOverlay), 0f, h.toFloat(), w.toFloat(), overlayData, deslocarDireita = isPreview, isGavetaFechada = isGavetaFechada)
        
        return bmpOverlay to alturaBarra // Retorna o par (Imagem, Altura)
    }



    private fun gerarParImagemFinalNA(foto: Bitmap, lago: LagoNA, valorNA: String?, horaOverride: String): Triple<Bitmap, Bitmap, Bitmap> {
        val (W, H) = calcularDimensoesNA(foto)

        val bmpLimpo = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cl = Canvas(bmpLimpo); cl.drawColor(Color.BLACK)
        val sc = scaleCrop(foto, W, H)
        cl.drawBitmap(sc, 0f, 0f, null)
        if (sc != foto) sc.recycle()

        // Agora usamos o horaOverride (que vem do diálogo) diretamente na tarja!
        val (bmpOverlay, _) = gerarOverlayNA(lago, valorNA, horaOverride, W, H, isPreview = false, isGavetaFechada = false)

        val bmpFinal = bmpLimpo.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(bmpFinal).drawBitmap(bmpOverlay, 0f, 0f, null)

        return Triple(bmpLimpo, bmpOverlay, bmpFinal)
    }

    // Mantida para compatibilidade. Devolve apenas a versão carimbada.
    private fun gerarBitmapRegistro(): Bitmap {
        val camadas = gerarParBitmapRegistro()
        return camadas.third
    }
    private fun isFlowmeterVazaoHibrido(item: ItemHm): Boolean {
        // Agora todos os itens de Vazão das ARBs aceitam gerar apenas a foto do Flowmeter
        return item.tipo == "HM_VAZAO"
    }


    private fun deveGerarFlowmeterHibridoComoSimples(item: ItemHm): Boolean {
        return isFlowmeterVazaoHibrido(item) && item.fotoSup != null && item.fotoInf == null
    }

    private fun gerarParBitmapRegistroSimplesStatus(item: ItemHm, horaOverride: String? = null): Triple<Bitmap, Bitmap, Bitmap> {
        val isLandscape = item.fotoSup?.let { it.width > it.height } ?: false
        val W = if (isLandscape) 2880 else 2160 // ══ MUDOU AQUI: Resolução gigante para HD na horizontal ══
        
        val now = Date()
        val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(now)
        val timeStr = SimpleDateFormat("HH:mm'h'", Locale.getDefault()).format(now)
        val statusCor = when (statusBomba) {
            "LIGADO", "LIGADA", "COM VAZÃO" -> "#00e676"
            "ZERADO" -> "#F59E0B"
            "NENHUM" -> "#000000"
            else -> "#ff3b3b"
        }

        val bmpFoto = item.fotoSup ?: Bitmap.createBitmap(W, 100, Bitmap.Config.ARGB_8888)
        val proporcao = ImageHelper.lerProporcao(this)
        
        val alturaFinal = if (proporcao == ConfiguracoesActivity.PROP_FULL) {
            val ratio = bmpFoto.width.toFloat() / bmpFoto.height.toFloat()
            (W / ratio).toInt().coerceIn(600, 4320)
        } else {
            when (proporcao) {
                ConfiguracoesActivity.PROP_4x5  -> (W * 5) / 4
                ConfiguracoesActivity.PROP_3x4  -> (W * 4) / 3
                ConfiguracoesActivity.PROP_9x16 -> (W * 16) / 9
                ConfiguracoesActivity.PROP_1x1  -> W
                else -> (W * 5) / 4
            }
        }

        val bmpLimpo = Bitmap.createBitmap(W, alturaFinal, Bitmap.Config.ARGB_8888)
        val cl = Canvas(bmpLimpo); cl.drawColor(Color.BLACK)
        val sc = scaleCrop(bmpFoto, W, alturaFinal)
        cl.drawBitmap(sc, 0f, 0f, null)
        if (sc != bmpFoto) sc.recycle()
        
        val horaUsar = horaOverride ?: item.dataHoraSup.ifEmpty { "$dateStr // $timeStr" }
        val labelHidro = montarTituloOverlay(item)

        val statusComLeitura = if (item.incluirLeituraNaFoto && !item.leituraManual.isNullOrBlank())
            "$statusBomba // VAZ: ${item.leituraManual}"
        else
            statusBomba

        val bmpOverlay = Bitmap.createBitmap(W, alturaFinal, Bitmap.Config.ARGB_8888)
        ImageHelper.drawOverlayKV(Canvas(bmpOverlay), 0f, alturaFinal.toFloat(), W.toFloat(), listOf("hidro" to labelHidro, "relogio" to horaUsar, "status" to statusComLeitura), mapOf("status" to statusCor))
        
        val bmpFinal = bmpLimpo.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(bmpFinal).drawBitmap(bmpOverlay, 0f, 0f, null)
        
        return Triple(bmpLimpo, bmpOverlay, bmpFinal)
    }


    // Retorna Triple(limpa, overlay, final).
    // limpa   = foto(s) montadas no canvas preto, SEM tarja.
    // overlay = bitmap transparente com APENAS as tarjas.
    // final   = fusão de limpa + overlay (o que o usuário vê/salva).
    // horaOverride: quando informado, TODAS as tarjas "relogio" geradas usam esse texto
    // exato em vez de Date()/dataHoraSup automático — usado pelo diálogo de edição manual
    // de data/hora para re-carimbar a imagem sem re-cortar as fotos originais pesadas.
    private fun gerarParBitmapRegistro(horaOverride: String? = null): Triple<Bitmap, Bitmap, Bitmap> {
        if (itensAtuais.isEmpty()) {
            val empty = Bitmap.createBitmap(1080, 100, Bitmap.Config.ARGB_8888)
            return Triple(empty, empty, empty)
        }
        val item = itensAtuais[hmSelecionado.coerceIn(0, itensAtuais.size - 1)]

        if (deveGerarFlowmeterHibridoComoSimples(item)) {
            return gerarParBitmapRegistroSimplesStatus(item, horaOverride)
        }

        return gerarParBitmapRegistroEmpilhado(item, horaOverride)
    }

    // ── Calcula a altura (H) da imagem empilhada dinamicamente, baseado na
    //    proporção escolhida pelo usuário nas Configurações.
    //    Largura fixa W = 1080 px. Cada slot (photoH) = H / 2.
    //    Tabela de H por proporção:
    //      4:5  → 1350   (W * 5/4)
    //      3:4  → 1440   (W * 4/3)
    //      9:16 → 1920   (W * 16/9)
    //      1:1  → 1080   (W * 1)
    //      full → 2160   (fallback original)
    private fun calcularAlturasEmpilhadas(fotoSup: Bitmap?, fotoInf: Bitmap?, canvasW: Int): Pair<Int, Int> {
        if (ImageHelper.lerProporcao(this) == ConfiguracoesActivity.PROP_FULL) {
            fun alturaPorFoto(foto: Bitmap?): Int {
                val f = foto ?: return (canvasW * 5 / 4) / 2
                val ratio = f.width.toFloat() / f.height.toFloat()
                return (canvasW / ratio).toInt().coerceIn(300, 4320)
            }
            return alturaPorFoto(fotoSup) to alturaPorFoto(fotoInf)
        }
        val meia = when (ImageHelper.lerProporcao(this)) {
            ConfiguracoesActivity.PROP_4x5  -> (canvasW * 5) / 4 / 2
            ConfiguracoesActivity.PROP_3x4  -> (canvasW * 4) / 3 / 2
            ConfiguracoesActivity.PROP_9x16 -> (canvasW * 16) / 9 / 2
            ConfiguracoesActivity.PROP_1x1  -> canvasW / 2
            else -> canvasW / 2
        }
        return meia to meia
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CORREÇÃO (BUG DE PROPORÇÃO): calcularAlturaEmpilhada() sempre devolvia
    //  uma altura FIXA de 2160px para a opção "FULL", o que na prática forçava
    //  um corte extremo (1080x2160 ≈ proporção 1:2), MAIS agressivo que o
    //  próprio 9:16 (1080x1920). Por isso "FULL" parecia cortado e "9:16"
    //  parecia ser o verdadeiro "full".
    //
    //  Esta função corrige isso: quando a proporção salva é FULL, cada foto
    //  (superior e inferior) usa a SUA PRÓPRIA proporção original — sem
    //  esticar nem cortar artificialmente — exatamente como já acontecia
    //  para os tipos "SIMPLES" e "SIMPLES_STATUS". Para as demais proporções
    //  (4:5, 3:4, 9:16, 1:1) o comportamento antigo é mantido (metade/metade).
    // ═══════════════════════════════════════════════════════════════════════
    

    // Lógica ORIGINAL (empilhado). Retorna Triple(limpa, overlay, final).
    private fun gerarParBitmapRegistroEmpilhado(item: ItemHm, horaOverride: String? = null): Triple<Bitmap, Bitmap, Bitmap> {
        val fotoPrincipal = if (item.tipo == "SIFAO") itensAtuais.find { it.id == "SIF-SUP" }?.fotoSup else item.fotoSup
        val isLandscape = fotoPrincipal?.let { it.width > it.height } ?: false
        val W = if (isLandscape) 2880 else 2160 // ══ MUDOU AQUI: Resolução gigante para HD na horizontal ══
        
        val now     = Date()
        val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(now)
        val timeStr = SimpleDateFormat("HH:mm'h'",   Locale.getDefault()).format(now)
        val horaAutomatica = "$dateStr // $timeStr"
        val horaFinal = horaOverride ?: horaAutomatica
        val statusCor = when (statusBomba) {
            "LIGADO", "LIGADA", "COM VAZÃO" -> "#00e676"
            "ZERADO"                        -> "#F59E0B"
            "NENHUM"                        -> "#000000"
            else                            -> "#ff3b3b"
        }

        fun finalizar(bmpLimpo: Bitmap, desenharOverlay: (Canvas) -> Unit): Triple<Bitmap, Bitmap, Bitmap> {
            val bmpOverlay = Bitmap.createBitmap(bmpLimpo.width, bmpLimpo.height, Bitmap.Config.ARGB_8888)
            desenharOverlay(Canvas(bmpOverlay))
            val bmpFinal = bmpLimpo.copy(Bitmap.Config.ARGB_8888, true)
            Canvas(bmpFinal).drawBitmap(bmpOverlay, 0f, 0f, null)
            return Triple(bmpLimpo, bmpOverlay, bmpFinal)
        }

        return when (item.tipo) {
            "HM" -> {
                val (photoHsup, photoHinf) = calcularAlturasEmpilhadas(item.fotoSup, item.fotoInf, W)
                val H = photoHsup + photoHinf
                val bmpLimpo = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
                val cl = Canvas(bmpLimpo); cl.drawColor(Color.BLACK)
                val temSup = item.fotoSup?.let {
                    val sc = scaleCrop(it, W, photoHsup)
                    cl.drawBitmap(sc, 0f, 0f, null)
                    if (sc != it) sc.recycle()
                    true
                } ?: run { drawPlaceholder(cl, 0f, 0f, W.toFloat(), photoHsup.toFloat()); false }
                Paint().apply { color = Color.parseColor("#00d4ff") }.also { cl.drawRect(0f, photoHsup - 2f, W.toFloat(), photoHsup + 2f, it) }
                val temInf = item.fotoInf?.let {
                    val sc = scaleCrop(it, W, photoHinf)
                    cl.drawBitmap(sc, 0f, photoHsup.toFloat(), null)
                    if (sc != it) sc.recycle()
                    true
                } ?: run { drawPlaceholder(cl, 0f, photoHsup.toFloat(), W.toFloat(), photoHinf.toFloat()); false }
                
                val statusComLeitura = if (item.incluirLeituraNaFoto && !item.leituraManual.isNullOrBlank())
                    "$statusBomba // VAZ: ${item.leituraManual}"
                else
                    statusBomba
                    
                finalizar(bmpLimpo) { c ->
                    if (temSup) ImageHelper.drawOverlayKV(c, 0f, photoHsup.toFloat(), W.toFloat(), listOf("pin" to estacaoSelecionada.nome, "hidro" to montarTituloOverlay(item)))
                    if (temInf) ImageHelper.drawOverlayKV(c, 0f, H.toFloat(), W.toFloat(), listOf("raio" to "BOMBA-${item.id.padStart(2, '0')}", "relogio" to horaFinal, "status" to statusComLeitura), mapOf("status" to statusCor))
                }
            }
            "HM_VAZAO" -> {
                if (deveGerarFlowmeterHibridoComoSimples(item)) {
                    return gerarParBitmapRegistroSimplesStatus(item, horaOverride)
                }
                val (photoHsup, photoHinf) = calcularAlturasEmpilhadas(item.fotoSup, item.fotoInf, W)
                val H = photoHsup + photoHinf
                val corFlowmeter = when (statusBomba) { "LIGADO" -> "#00e676"; "ZERADO" -> "#F59E0B"; "NENHUM" -> "#000000"; else -> "#ff3b3b" }
                val corVazao = if (item.statusVazao == "COM VAZÃO") "#00e676" else "#ff3b3b"
                val bmpLimpo = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
                val cl = Canvas(bmpLimpo); cl.drawColor(Color.BLACK)
                val temSup = item.fotoSup?.let {
                    val sc = scaleCrop(it, W, photoHsup)
                    cl.drawBitmap(sc, 0f, 0f, null)
                    if (sc != it) sc.recycle()
                    true
                } ?: run { drawPlaceholder(cl, 0f, 0f, W.toFloat(), photoHsup.toFloat()); false }
                Paint().apply { color = Color.parseColor("#00d4ff") }.also { cl.drawRect(0f, photoHsup - 2f, W.toFloat(), photoHsup + 2f, it) }
                val temInf = item.fotoInf?.let {
                    val sc = scaleCrop(it, W, photoHinf)
                    cl.drawBitmap(sc, 0f, photoHsup.toFloat(), null)
                    if (sc != it) sc.recycle()
                    true
                } ?: run { drawPlaceholder(cl, 0f, photoHsup.toFloat(), W.toFloat(), photoHinf.toFloat()); false }
                
                val statusComLeitura = if (item.incluirLeituraNaFoto && !item.leituraManual.isNullOrBlank())
                    "$statusBomba // VAZ: ${item.leituraManual}"
                else
                    statusBomba
                    
                finalizar(bmpLimpo) { c ->
                    if (temSup) ImageHelper.drawOverlayKV(c, 0f, photoHsup.toFloat(), W.toFloat(), listOf("hidro" to montarTituloOverlay(item), "relogio" to horaFinal, "status" to statusComLeitura), mapOf("status" to corFlowmeter))
                    if (temInf) ImageHelper.drawOverlayKV(c, 0f, H.toFloat(), W.toFloat(), listOf("hidro" to item.id, "status" to item.statusVazao), mapOf("status" to corVazao))
                }
            }
            "SIMPLES_STATUS", "SIMPLES_STATUS_ADD" -> {
                val bmpFoto = item.fotoSup ?: Bitmap.createBitmap(W, 100, Bitmap.Config.ARGB_8888)
                val ratio = bmpFoto.width.toFloat() / bmpFoto.height.toFloat()
                val alturaFinal = (W / ratio).toInt().coerceAtLeast(1200)
                val bmpLimpo = Bitmap.createBitmap(W, alturaFinal, Bitmap.Config.ARGB_8888)
                val cl = Canvas(bmpLimpo); cl.drawColor(Color.BLACK)
                val sc = scaleCrop(bmpFoto, W, alturaFinal)
                cl.drawBitmap(sc, 0f, 0f, null)
                if (sc != bmpFoto) sc.recycle()
                val horaUsar = horaOverride ?: item.dataHoraSup.ifEmpty { horaAutomatica }
                val labelHidro = montarTituloOverlay(item)
                
                val statusComLeitura = if (item.incluirLeituraNaFoto && !item.leituraManual.isNullOrBlank())
                    "$statusBomba // VAZ: ${item.leituraManual}"
                else
                    statusBomba

                finalizar(bmpLimpo) { c ->
                    ImageHelper.drawOverlayKV(c, 0f, alturaFinal.toFloat(), W.toFloat(), listOf("hidro" to labelHidro, "relogio" to horaUsar, "status" to statusComLeitura), mapOf("status" to statusCor))
                }
            }
            "SIFAO" -> {
                val supItem = itensAtuais.find { it.id == "SIF-SUP" }
                val infItem = itensAtuais.find { it.id == "SIF-INF" }
                val (photoHsup, photoHinf) = calcularAlturasEmpilhadas(supItem?.fotoSup, infItem?.fotoSup, W)
                val H = photoHsup + photoHinf
                val bmpLimpo = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
                val cl = Canvas(bmpLimpo); cl.drawColor(Color.BLACK)
                supItem?.fotoSup?.let {
                    val sc = scaleCrop(it, W, photoHsup)
                    cl.drawBitmap(sc, 0f, 0f, null)
                    if (sc != it) sc.recycle()
                } ?: drawPlaceholder(cl, 0f, 0f, W.toFloat(), photoHsup.toFloat())
                Paint().apply { color = Color.parseColor("#00d4ff") }.also { cl.drawRect(0f, photoHsup - 2f, W.toFloat(), photoHsup + 2f, it) }
                val infFoto = infItem?.fotoSup
                if (infFoto != null) {
                    val sc = scaleCrop(infFoto, W, photoHinf)
                    cl.drawBitmap(sc, 0f, photoHsup.toFloat(), null)
                    if (sc != infFoto) sc.recycle()
                } else {
                    drawPlaceholder(cl, 0f, photoHsup.toFloat(), W.toFloat(), photoHinf.toFloat())
                }
                finalizar(bmpLimpo) { c ->
                    if (infItem != null && infFoto != null) {
                        val horaInf = horaOverride ?: infItem.dataHoraSup.ifEmpty { horaAutomatica }
                        val corStatusInf = if (infItem.statusVazao == "COM VAZÃO") "#00e676" else "#ff3b3b"
                        ImageHelper.drawOverlayKV(c, 0f, H.toFloat(), W.toFloat(),
                            listOf("pin" to estacaoSelecionada.nome, "hidro" to "SIFÕES", "relogio" to horaInf, "status" to infItem.statusVazao),
                            mapOf("status" to corStatusInf)
                        )
                    }
                }
            }
            "SIMPLES" -> {
                val bmpFoto = item.fotoSup ?: Bitmap.createBitmap(W, 2160, Bitmap.Config.ARGB_8888)
                val imgRatio = bmpFoto.width.toFloat() / bmpFoto.height.toFloat()
                val alturaFinal = (W / imgRatio).toInt().coerceIn(1200, 4320)

                val bmpLimpo = Bitmap.createBitmap(W, alturaFinal, Bitmap.Config.ARGB_8888)
                val cl = Canvas(bmpLimpo); cl.drawColor(Color.BLACK)
                val temFoto = item.fotoSup?.let {
                    val sc = scaleCrop(it, W, alturaFinal)
                    cl.drawBitmap(sc, 0f, 0f, null)
                    if (sc != it) sc.recycle()
                    true
                } ?: run { drawPlaceholder(cl, 0f, 0f, W.toFloat(), alturaFinal.toFloat()); false }
                
                finalizar(bmpLimpo) { c ->
                    if (temFoto) ImageHelper.drawOverlayKV(c, 0f, alturaFinal.toFloat(), W.toFloat(), listOf("pin" to estacaoSelecionada.nome, "hidro" to item.cardAzulLabel, "relogio" to horaFinal))
                }
            }
            else -> {
                val empty = Bitmap.createBitmap(W, 100, Bitmap.Config.ARGB_8888)
                Triple(empty, empty, empty)
            }
        }
    }

    private fun montarTituloOverlay(item: ItemHm): String {
        return when (estacaoSelecionada.nome) {
            "DET-01" -> "HIDRÔMETRO-${item.id.padStart(2, '0')}"
            "ARB-05" -> "FLOWMETER ARB-05"
            "ARB-06" -> "FLOWMETER ARB-06"
            "ARB-07" -> "FLOWMETER ARB-07 ${item.id}"
            "ARB-08" -> "FLOWMETER ARB-08 ${item.id}"
            "ARB-09" -> "FLOWMETER ARB-${item.id}"
            else     -> item.cardAzulLabel
        }
    }

    private fun scaleCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        val scale  = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val sw     = (src.width  * scale).toInt(); val sh     = (src.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(src, sw, sh, true)
        val x      = ((sw - w) / 2).coerceAtLeast(0); val y      = ((sh - h) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(scaled, x, y, w.coerceAtMost(sw), h.coerceAtMost(sh))
    }

    private fun drawPlaceholder(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#111318") }
        c.drawRect(x, y, x + w, y + h, p)
        p.color = Color.parseColor("#5A6478"); p.typeface = Typeface.MONOSPACE; p.textSize = w * 0.035f
        c.drawText("Foto não registrada", x + w * 0.05f, y + h / 2, p)
    }

    private fun setupZoom(iv: ImageView, tvZoomLevel: TextView?, btnResetZoom: View?) {
        iv.scaleType = ImageView.ScaleType.MATRIX
        val matrix       = Matrix()
        val baseMatrix   = Matrix()
        var minScale     = 1f
        val maxScale     = 6f

        fun matrixScale(): Float { val v = FloatArray(9); matrix.getValues(v); return v[Matrix.MSCALE_X] }
        fun baseScale(): Float { val v = FloatArray(9); baseMatrix.getValues(v); return v[Matrix.MSCALE_X] }
        fun atualizarHud() {
            val pct = ((matrixScale() / baseScale()) * 100f).toInt()
            tvZoomLevel?.text = "${pct}%"
            btnResetZoom?.visibility = if (pct > 105) View.VISIBLE else View.GONE
        }

        fun aplicarFitCenter() {
            val d = iv.drawable ?: return
            val vw = iv.width.toFloat(); val vh = iv.height.toFloat()
            if (vw <= 0 || vh <= 0) return
            val iw = d.intrinsicWidth.toFloat(); val ih = d.intrinsicHeight.toFloat()
            if (iw <= 0 || ih <= 0) return
            baseMatrix.reset()
            
            // ── MÁGICA AQUI: maxOf força o CENTER CROP perfeito sem bordas pretas ──
            val s = maxOf(vw / iw, vh / ih)
            
            val dx = (vw - iw * s) / 2f; val dy = (vh - ih * s) / 2f
            baseMatrix.postScale(s, s)
            baseMatrix.postTranslate(dx, dy)
            matrix.set(baseMatrix)
            iv.imageMatrix = matrix
            minScale = s
            atualizarHud()
        }

        fun corrigirLimites() {
            val d = iv.drawable ?: return
            val vw = iv.width.toFloat(); val vh = iv.height.toFloat()
            val rect = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
            matrix.mapRect(rect)
            var dx = 0f; var dy = 0f
            if (rect.width() <= vw) dx = (vw - rect.width()) / 2f - rect.left
            else {
                if (rect.left > 0) dx = -rect.left
                else if (rect.right < vw) dx = vw - rect.right
            }
            if (rect.height() <= vh) dy = (vh - rect.height()) / 2f - rect.top
            else {
                if (rect.top > 0) dy = -rect.top
                else if (rect.bottom < vh) dy = vh - rect.bottom
            }
            if (dx != 0f || dy != 0f) matrix.postTranslate(dx, dy)
        }

        iv.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View?, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or_: Int, ob: Int) {
                if (iv.drawable != null && iv.width > 0 && iv.height > 0) {
                    aplicarFitCenter()
                    iv.removeOnLayoutChangeListener(this)
                }
            }
        })
        iv.post { if (iv.drawable != null && iv.width > 0) aplicarFitCenter() }

        val scaleDetector = android.view.ScaleGestureDetector(this, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: android.view.ScaleGestureDetector): Boolean {
                val current = matrixScale()
                var factor  = d.scaleFactor
                val limMin  = minScale
                val limMax  = minScale * maxScale
                val novo    = (current * factor).coerceIn(limMin, limMax)
                factor      = novo / current
                matrix.postScale(factor, factor, d.focusX, d.focusY)
                corrigirLimites()
                iv.imageMatrix = matrix
                atualizarHud()
                return true
            }
        })

        val gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val current = matrixScale()
                val alvo    = if (current > minScale * 1.2f) minScale else minScale * 3f
                val passos  = 8
                val fator   = Math.pow((alvo / current).toDouble(), 1.0 / passos).toFloat()
                val fx = e.x; val fy = e.y
                var i = 0
                val runnable = object : Runnable {
                    override fun run() {
                        if (i++ >= passos) return
                        matrix.postScale(fator, fator, fx, fy)
                        corrigirLimites()
                        iv.imageMatrix = matrix
                        atualizarHud()
                        iv.postDelayed(this, 16L)
                    }
                }
                iv.post(runnable)
                return true
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distX: Float, distY: Float): Boolean {
                if (e2.pointerCount > 1) return false
                // ═══ CORREÇÃO: a imagem só pode ser arrastada quando estiver de fato
                // ampliada (zoom > 100%). Em repouso (100%) ela fica FIXA, igual às ARBs. ═══
                if (matrixScale() <= minScale * 1.01f) return false
                matrix.postTranslate(-distX, -distY)
                corrigirLimites()
                iv.imageMatrix = matrix
                return true
            }
        })

        btnResetZoom?.setOnClickListener {
            matrix.set(baseMatrix)
            iv.imageMatrix = matrix
            atualizarHud()
        }

        iv.setOnTouchListener { v, event ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            scaleDetector.onTouchEvent(event)
            if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) v.performClick()
            true
        }
    }

private fun mostrarDialogResultadoNA() {
    val lago = lagosNA.getOrNull(lagoNASelecionado) ?: return
    val fotoReg = lago.fotoRegua ?: return

    val view = layoutInflater.inflate(R.layout.dialog_resultado_na, null)
    val d = android.app.AlertDialog.Builder(this).setView(view).create()
    d.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))

    // ═══ Responsividade por tamanho de tela ═══
    run {
        val alturaTelaDp = resources.displayMetrics.heightPixels / resources.displayMetrics.density
        val escala = (alturaTelaDp / 700f).coerceIn(0.78f, 1f)
        fun px(dpValor: Int): Int = (dpValor * escala * resources.displayMetrics.density).toInt()
        fun sp(spValor: Float): Float = spValor * escala

        val topBar = view.findViewById<LinearLayout>(R.id.topBarDialogNA)
        topBar.setPadding(px(18), px(18), px(18), px(10))
        view.findViewById<TextView>(R.id.tvNaLabelDialogNA).setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(12f))
        view.findViewById<TextView>(R.id.tvTituloDialogNA).setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(22f))
        view.findViewById<ImageView>(R.id.btnFecharDialogNA).layoutParams = view.findViewById<ImageView>(R.id.btnFecharDialogNA).layoutParams.apply {
            width = px(40); height = px(40)
        }

        view.findViewById<FrameLayout>(R.id.frameImagemNA).let { frame ->
            (frame.layoutParams as ViewGroup.MarginLayoutParams).setMargins(px(10), px(10), px(10), px(10))
        }
        view.findViewById<ReguaVerticalView>(R.id.reguaEdicaoDialogNA).layoutParams = view.findViewById<ReguaVerticalView>(R.id.reguaEdicaoDialogNA).layoutParams.apply {
            height = px(80)
        }

        val layoutInferior = view.findViewById<LinearLayout>(R.id.layoutBotoesInferioresNA)
        layoutInferior.setPadding(px(18), layoutInferior.paddingTop, px(18), px(18))
        (view.findViewById<View>(R.id.dividerNA).layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = px(12)
        (view.findViewById<LinearLayout>(R.id.blocoValorNA).layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = px(12)

        view.findViewById<TextView>(R.id.tvValorNaLabel).setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(11f))
        view.findViewById<TextView>(R.id.tvZoomLevel).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(10f))
            setPadding(px(8), px(3), px(8), px(3))
        }
        view.findViewById<EditText>(R.id.etValorNA).setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(32f))
        view.findViewById<TextView>(R.id.tvStatusForaNA).setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(12f))

        // ═══════════════════════════════════════════════════════════════════
        // A LINHA DE BOTÕES INFERIORES agora obedece ao padrão unificado do XML (52dp),
        // idêntico a dialog_historico_edicao.xml e dialog_registro_resultado.xml.
        // NÃO aplicamos a escala responsíva aqui — os botões têm altura fixa em todas
        // as telas para manter harmonia visual entre os três diálogos. Só mantemos a
        // margem final (marginEnd) escalada para respeitar o padding lateral do container.
        // ═══════════════════════════════════════════════════════════════════
        view.findViewById<CardView>(R.id.cardBtnHoraNA).let { card ->
            (card.layoutParams as ViewGroup.MarginLayoutParams).marginEnd = px(8)
            card.requestLayout()
        }
    }

    val imgView = view.findViewById<ImageView>(R.id.imgResultadoNA)
    val imgOverlayNA = view.findViewById<ImageView>(R.id.imgResultadoOverlayNA)
    val tvZoomLevel = view.findViewById<TextView?>(R.id.tvZoomLevel)

    val (naW, naH) = calcularDimensoesNA(fotoReg)
    val bmpBasePreviewNA = scaleCrop(fotoReg, naW, naH)
    imgView.setImageBitmap(bmpBasePreviewNA)

    scope.launch(Dispatchers.IO) {
        // ═══ Se a foto de régua já veio de dentro do Cofre, não regrava
        // ela lá dentro de novo (evita duplicar a cada "gerar"). ═══
        if (!lago.fotoVeioDoCofre) {
            CofreManager.salvarSeNovo(
                context = this@DashboardActivity,
                bitmapLimpo = bmpBasePreviewNA,
                grupoOuEstacao = estacaoSelecionada.nome,
                nomeSubpasta = lago.nomeCard
            )
        }
    }


    // ═══ CORREÇÃO: trava de altura responsiva da foto — elimina o scroll
    // residual do diálogo N.A. em telas mais baixas (ex: Moto G30). Antes a
    // foto crescia livre pela largura toda (adjustViewBounds sem limite
    // máximo), então em aparelhos com menos altura disponível a soma
    // cabeçalho + foto + bloco inferior ultrapassava a tela. A escala
    // "escala = alturaTelaDp/700f" só reduzia textos/paddings, nunca a
    // própria foto. Aqui medimos o cabeçalho e o bloco inferior JÁ com a
    // escala responsiva aplicada e travamos a altura máxima da foto
    // exatamente no espaço que sobra — sempre cabe, em qualquer aparelho. ═══
    run {
        val topBarNA = view.findViewById<LinearLayout>(R.id.topBarDialogNA)
        val blocoInferiorNA = view.findViewById<LinearLayout>(R.id.layoutBotoesInferioresNA)
        val larguraDisponivelPx = resources.displayMetrics.widthPixels
        val widthSpec = View.MeasureSpec.makeMeasureSpec(larguraDisponivelPx, View.MeasureSpec.EXACTLY)
        val heightSpecLivre = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        topBarNA.measure(widthSpec, heightSpecLivre)
        blocoInferiorNA.measure(widthSpec, heightSpecLivre)

        val statusBarResId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val alturaStatusBarPx = if (statusBarResId > 0) resources.getDimensionPixelSize(statusBarResId) else 0
        val margemFramePx = (10 * resources.displayMetrics.density * 2).toInt()
        val bufferSegurancaPx = (16 * resources.displayMetrics.density).toInt()

        val alturaMaxFotoPx = resources.displayMetrics.heightPixels -
                alturaStatusBarPx -
                topBarNA.measuredHeight -
                blocoInferiorNA.measuredHeight -
                margemFramePx -
                bufferSegurancaPx

        if (alturaMaxFotoPx > 0) {
            imgView.maxHeight = alturaMaxFotoPx
            imgOverlayNA.maxHeight = alturaMaxFotoPx
        }
    }

        setupZoom(imgView, tvZoomLevel, null)

    // ═══ CORREÇÃO AQUI: A tarja agora acompanha a matriz exata da foto ═══
    imgOverlayNA.scaleType = ImageView.ScaleType.MATRIX
    imgOverlayNA.isClickable = true 
    imgOverlayNA.isFocusable = false

    val etNA = view.findViewById<EditText>(R.id.etValorNA)
    val tvStatus = view.findViewById<TextView>(R.id.tvStatusForaNA)

    val isExtravasor = lago.abreviacao.equals("DT2-ex", ignoreCase = true) || lago.abreviacao.equals("CP-ex", ignoreCase = true)

    if (isExtravasor) {
        etNA.isEnabled = false
        etNA.alpha = 0.45f
        tvStatus.visibility = View.VISIBLE
        tvStatus.text = "EXTRAVASOR — N.A. não aplicável"
    } else if (foraDeNA) {
        etNA.isEnabled = false
        etNA.alpha = 0.45f
        tvStatus.visibility = View.VISIBLE
        tvStatus.text = "N.A. FORA DA RÉGUA — campo inativo"
    } else {
        etNA.isEnabled = true
        etNA.alpha = 1f
        tvStatus.visibility = View.GONE
        aplicarMascaraNA(etNA)
    }

    view.findViewById<ImageView>(R.id.btnFecharDialogNA).setOnClickListener {
        esconderTeclado(etNA)
        d.dismiss()
    }

        val btnEditarHoraNA = view.findViewById<ImageView>(R.id.btnEditarHoraNA)
    val horaOriginal = if (lago.dataHora.isNotEmpty()) lago.dataHora else {
        SimpleDateFormat("dd.MM.yyyy // HH:mm'h'", Locale.getDefault()).format(Date())
    }
    var horaAtual = horaOriginal

    var isGavetaNAFechada = true
    var alturaBarraBitmap = 0f

    imgOverlayNA.scaleType = ImageView.ScaleType.MATRIX
    imgOverlayNA.isClickable = false
    imgOverlayNA.isFocusable = false

    fun calcularOffsetGavetaFechada(): Float {
        val dr = imgOverlayNA.drawable ?: return 0f
        val iw = dr.intrinsicWidth.toFloat().coerceAtLeast(1f)
        val ih = dr.intrinsicHeight.toFloat().coerceAtLeast(1f)
        val vw = imgView.width.toFloat()
        val vh = imgView.height.toFloat()
        if (vw <= 0f || vh <= 0f) return 0f
        val scale = maxOf(vw / iw, vh / ih)
        return alturaBarraBitmap * scale
    }

    fun aplicarPosicaoGavetaTarja(animar: Boolean) {
        val alvo = if (isGavetaNAFechada) calcularOffsetGavetaFechada() else 0f
        if (animar) {
            imgOverlayNA.animate()
                .translationY(alvo)
                .setDuration(300L)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        } else {
            imgOverlayNA.translationY = alvo
        }
    }

fun reajustarTarjaNALive(animar: Boolean = false) {
        val vw = imgView.width.toFloat()
        val vh = imgView.height.toFloat()
        val dr = imgOverlayNA.drawable ?: return
        val iw = dr.intrinsicWidth.toFloat().coerceAtLeast(1f)
        val ih = dr.intrinsicHeight.toFloat().coerceAtLeast(1f)
        if (vw <= 0f || vh <= 0f) return

        val scale = maxOf(vw / iw, vh / ih)
        val dx = (vw - iw * scale) / 2f
        val dy = vh - (ih * scale) 
        
        val matrix = Matrix()
        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)
        
        imgOverlayNA.imageMatrix = matrix
        
        // ══ MUDANÇA: Agora ele respeita se é para animar ou não ══
        aplicarPosicaoGavetaTarja(animar) 
    }

    fun atualizarOverlayPreviewNA(animar: Boolean = false) {
        val valorPreview = if (foraDeNA || isExtravasor) null else etNA.text.toString().trim()
        val (bmp, hBarra) = gerarOverlayNA(lago, valorPreview, horaAtual, naW, naH, isPreview = true, isGavetaFechada = isGavetaNAFechada)
        alturaBarraBitmap = hBarra
        imgOverlayNA.setImageBitmap(bmp)
        
        // ══ MUDANÇA: Repassa a ordem de deslizar a gaveta ══
        reajustarTarjaNALive(animar)
    }
    // Atualiza a posição da tarja apenas se a tela mudar de tamanho
    imgView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        reajustarTarjaNALive()
    }

    atualizarOverlayPreviewNA()
    imgView.post { reajustarTarjaNALive() }

    // ═══ CORREÇÃO: calcula o retângulo EXATO (em coordenadas da view) de
    // onde a abinha é desenhada, usando a MESMA matemática de
    // ImageHelper.drawOverlayKV (padH/abaLargura/abaAltura em função da
    // largura do bitmap). Antes a área de toque era só "30% direita x 30%
    // baixo" da VIEW inteira — em telas menores ou fotos mais altas isso
    // cobria uma faixa enorme, chegando até o meio da imagem. ═══
    fun toqueNaAbaGavetaNA(toqueX: Float, toqueY: Float): Boolean {
        val dr = imgOverlayNA.drawable ?: return false
        val iw = dr.intrinsicWidth.toFloat().coerceAtLeast(1f)
        val ih = dr.intrinsicHeight.toFloat().coerceAtLeast(1f)
        val vw = imgOverlayNA.width.toFloat()
        val vh = imgOverlayNA.height.toFloat()
        if (vw <= 0f || vh <= 0f) return false
        val scale = maxOf(vw / iw, vh / ih)
        val dx = (vw - iw * scale) / 2f
        val dy = vh - (ih * scale)

        // Mesma geometria de ImageHelper.drawOverlayKV (deslocarDireita
        // sempre true aqui, pois é sempre o preview).
        val padH = iw * 0.08f
        val abaLargura = iw * 0.144f
        val abaAltura = iw * 0.055f
        val abaRight = iw - padH
        val abaLeft = abaRight - abaLargura
        val abaBottom = ih - alturaBarraBitmap
        val abaTop = abaBottom - abaAltura

        // Pequena folga (~10dp) só pra não ficar difícil de acertar o dedo.
        val folgaBitmap = (10f * resources.displayMetrics.density) / scale

        val esquerdaView = dx + (abaLeft - folgaBitmap) * scale
        val direitaView  = dx + (abaRight + folgaBitmap) * scale
        val topoView     = dy + (abaTop - folgaBitmap) * scale
        val baixoView    = dy + (abaBottom + folgaBitmap) * scale

        return toqueX in esquerdaView..direitaView && toqueY in topoView..baixoView
    }

    imgOverlayNA.setOnTouchListener { v, event ->
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (toqueNaAbaGavetaNA(event.x, event.y)) {
                isGavetaNAFechada = !isGavetaNAFechada
                atualizarOverlayPreviewNA(animar = true)
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                return@setOnTouchListener true
            }
        }
        false // Se não clicou na aba, deixa o toque passar livre para a foto!
    }

    etNA.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, st: Int, cnt: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, st: Int, before: Int, cnt: Int) = Unit
        override fun afterTextChanged(s: Editable?) { atualizarOverlayPreviewNA() }
    })

    btnEditarHoraNA?.setOnClickListener {
        mostrarDialogEditarDataHora(
            horaAtual = horaAtual,
            horaOriginal = horaOriginal,
            onConfirmar = { novaHora ->
                horaAtual = novaHora
                atualizarOverlayPreviewNA()
                Toast.makeText(this, "✓ Horário atualizado para a foto final", Toast.LENGTH_SHORT).show()
            }
        )
    }

    val reguaNA = view.findViewById<ReguaVerticalView>(R.id.reguaEdicaoDialogNA)
    reguaNA.alinharEsquerda = false 
    reguaNA.onValorMudou = { modo, valor ->
        FiltroImagemHelper.aplicarFiltroAoVivo(imgView, modo, valor)
    }
    view.findViewById<Button>(R.id.btnBaixarNA).setOnClickListener { botao ->
        botao.isEnabled = false
        scope.launch {
            try {
                val valorNA = if (foraDeNA || isExtravasor) null else etNA.text.toString().trim()
                val (bmpLimpo, bmpOverlay, _) = withContext(Dispatchers.Default) {
                    gerarParImagemFinalNA(fotoReg, lago, valorNA, horaAtual)
                }

                val valorModoAtivoNA = when (reguaNA.modoAtual) {
                    ReguaVerticalView.Modo.BRILHO      -> reguaNA.valorBrilho
                    ReguaVerticalView.Modo.NITIDEZ     -> reguaNA.valorNitidez
                    ReguaVerticalView.Modo.VETORIZACAO -> reguaNA.valorVetorizacao
                }
                val finalEditado = withContext(Dispatchers.Default) {
                    FiltroImagemHelper.fundirCamadasParaSalvar(
                        bmpLimpo, bmpOverlay,
                        reguaNA.modoAtual, valorModoAtivoNA,
                        reguaNA.valorVetorizacao
                    )
                }

                withContext(Dispatchers.IO) {
                    salvarNoHistoricoGlobal(lago.nomeCard, estacaoSelecionada.nome, null, null, valorNA, finalEditado, bmpLimpo, horaAtual)
                }

                val salvou = salvarImagemAsync(finalEditado)
                if (salvou) {
                    Toast.makeText(this@DashboardActivity, "✓ Imagem salva em Galeria/INSPETOR", Toast.LENGTH_SHORT).show()
                    if (bmpLimpo != finalEditado) bmpLimpo.recycle()
                    d.dismiss()
                } else {
                    Toast.makeText(this@DashboardActivity, "Não foi possível salvar a imagem.", Toast.LENGTH_SHORT).show()
                    if (bmpLimpo != finalEditado) bmpLimpo.recycle()
                }
            } finally {
                botao.isEnabled = true
            }
        }
    }

    view.findViewById<Button>(R.id.btnCompartilharNA).setOnClickListener { botao ->
        botao.isEnabled = false
        scope.launch {
            try {
                val valorNA = if (foraDeNA || isExtravasor) null else etNA.text.toString().trim()
                val (bmpLimpo, bmpOverlay, _) = withContext(Dispatchers.Default) {
                    gerarParImagemFinalNA(fotoReg, lago, valorNA, horaAtual)
                }

                val valorModoAtivoNA = when (reguaNA.modoAtual) {
                    ReguaVerticalView.Modo.BRILHO      -> reguaNA.valorBrilho
                    ReguaVerticalView.Modo.NITIDEZ     -> reguaNA.valorNitidez
                    ReguaVerticalView.Modo.VETORIZACAO -> reguaNA.valorVetorizacao
                }
                val finalEditado = withContext(Dispatchers.Default) {
                    FiltroImagemHelper.fundirCamadasParaSalvar(
                        bmpLimpo, bmpOverlay,
                        reguaNA.modoAtual, valorModoAtivoNA,
                        reguaNA.valorVetorizacao
                    )
                }

                withContext(Dispatchers.IO) {
                    salvarNoHistoricoGlobal(lago.nomeCard, estacaoSelecionada.nome, null, null, valorNA, finalEditado, bmpLimpo, horaAtual)
                }

                val uri = prepararUriCompartilhamentoAsync(finalEditado)
                if (uri != null) {
                    compartilharUri(uri, "Registro")
                    if (bmpLimpo != finalEditado) bmpLimpo.recycle()
                    d.dismiss()
                } else {
                    Toast.makeText(this@DashboardActivity, "Não foi possível compartilhar a imagem.", Toast.LENGTH_SHORT).show()
                    if (bmpLimpo != finalEditado) bmpLimpo.recycle()
                }
            } finally {
                botao.isEnabled = true
            }
        }
    }
    d.show()

    d.window?.apply {
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    val scrollNA = view.findViewById<ScrollView>(R.id.scrollDialogNA)
    val layoutInferiorNA = view.findViewById<LinearLayout>(R.id.layoutBotoesInferioresNA)
    val paddingBottomBaseNA = layoutInferiorNA.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(scrollNA) { _, insets ->
        val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        layoutInferiorNA.setPadding(
            layoutInferiorNA.paddingLeft,
            layoutInferiorNA.paddingTop,
            layoutInferiorNA.paddingRight,
            paddingBottomBaseNA + barras.bottom
        )
        insets
    }
    ViewCompat.requestApplyInsets(scrollNA)

    var tecladoAbertoNA = false
    var alturaBaseScrollNA = 0
    val limiarTecladoPx = dp(150)
    val globalLayoutListenerNA = ViewTreeObserver.OnGlobalLayoutListener {
        val alturaAtual = scrollNA.height
        if (alturaAtual > 0) {
            if (alturaBaseScrollNA == 0) {
                alturaBaseScrollNA = alturaAtual
            } else {
                val diferenca = alturaBaseScrollNA - alturaAtual
                val agoraAberto = diferenca > limiarTecladoPx
                if (agoraAberto != tecladoAbertoNA) {
                    tecladoAbertoNA = agoraAberto
                    val conteudo = scrollNA.getChildAt(0)
                    if (agoraAberto) {
                        scrollNA.post { scrollSuaveRapido(scrollNA, conteudo.height) }
                    } else {
                        scrollNA.post { scrollSuaveRapido(scrollNA, 0) }
                    }
                } else if (!agoraAberto) {
                    alturaBaseScrollNA = alturaAtual
                }
            }
        }
    }
    scrollNA.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListenerNA)
    d.setOnDismissListener {
        scrollNA.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListenerNA)
    }

    if (!foraDeNA && !isExtravasor) {
        etNA.requestFocus()
        etNA.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etNA, InputMethodManager.SHOW_IMPLICIT)
        }, 250)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  FLUXO LIVRE (card roxo depois do ARB-09)
//
//  Idem N.A. em estrutura (foto no topo, painel inferior com campos, gaveta
//  em tempo real, editar data/hora, baixar/compartilhar) — porém:
//    • O campo não é numérico: teclado QWERTY, multi-linha, sem máscara.
//    • A tarja tem duas linhas típicas: DATA E HORA (opcional, via toggle)
//      + TEXTO LIVRE (com quebra de linha respeitada).
//    • NÃO salva no Histórico e NÃO salva no Cofre (Galeria 2) — o hook em
//      setupBotaoGerar() intercepta antes de gerarRegistroAssincrono, então
//      esses dois salvamentos já estão pulados por construção.
//    • "NENHUM" (4º status, preto) some da tarja — só pinta o botão da UI.
// ═════════════════════════════════════════════════════════════════════════════

// Cálculo de dimensões do canvas LIVRE (idêntico ao N.A.): W ancorado, H
// derivado da proporção da foto original. Retrato → 2160, paisagem → 2880.
private fun calcularDimensoesLivre(foto: Bitmap): Pair<Int, Int> {
    val isLandscape = foto.width > foto.height
    return if (isLandscape) {
        val w = 2880
        w to (w.toFloat() * foto.height / foto.width).toInt()
    } else {
        val w = 2160
        w to (w.toFloat() * foto.height / foto.width).toInt().coerceIn(2160, 4320)
    }
}

// Cor exibida ao lado do status na tarja (quando o usuário escolhe algo diferente
// de NENHUM). Segue o mesmo padrão do gerarParBitmapRegistroSimplesStatus.
private fun corStatusLivre(status: String): String = when (status) {
    "LIGADO", "LIGADA", "COM VAZÃO" -> "#00e676"
    "ZERADO"                        -> "#F59E0B"
    "NENHUM"                        -> "#000000"
    else                            -> "#ff3b3b"
}

// Gera SOMENTE a camada de overlay (tarja) do LIVRE, no tamanho W x H.
// Extraído para permitir atualização AO VIVO no diálogo, sem re-cortar a foto.
// • texto: string do usuário (pode ter '\n' — cada quebra vira uma linha na tarja).
// • incluirDataHora: quando true, a primeira linha da tarja é a data/hora.
// • statusOverlay: null quando NENHUM (não desenha status); caso contrário o texto do status.
private fun gerarOverlayLivre(
    texto: String,
    incluirDataHora: Boolean,
    horaOverride: String,
    statusOverlay: String?,
    w: Int,
    h: Int,
    isPreview: Boolean = false,
    isGavetaFechada: Boolean = false
): Pair<Bitmap, Float> {
    // Monta a lista de linhas da tarja. A ordem é:
    //   1) relógio (opcional)
    //   2..N) texto livre — uma entrada por linha após split('\n'), usando o
    //         mesmo ícone "pin" (com halo de localização) só na primeira, e
    //         ícones "vazios" (usando um alias inexistente) nas continuações —
    //         drawIconSvg ignora nomes desconhecidos, então só aparece o texto.
    //   N+1) status (opcional)
    val linhas = mutableListOf<Pair<String, String>>()
    val cores = mutableMapOf<String, String>()

    if (incluirDataHora) {
        linhas.add("relogio" to horaOverride)
    }

    val textoNorm = texto.trim()
    if (textoNorm.isNotEmpty()) {
        val partes = textoNorm.split('\n')
        partes.forEach { linha ->
            val trimmed = linha.trimEnd()
            // ═══ Nenhuma linha de texto livre tem ícone (nem a primeira) —
            // "__cont__" não existe em drawIconSvg, então nada é desenhado
            // ali, mas o texto começa exatamente no mesmo x das linhas COM
            // ícone (ex.: "relogio"), porque esse recuo é fixo pra todas as
            // linhas dentro de drawOverlayKV. Resultado: tudo alinhado, sem
            // ícone extra e sem espaço vazio "esquisito" reservado à toa. ═══
            linhas.add("__cont__" to trimmed)
        }
    }

    if (!statusOverlay.isNullOrBlank()) {
        linhas.add("status" to statusOverlay)
        cores["status"] = corStatusLivre(statusOverlay)
    }

    // Se nada foi digitado e o usuário não pediu data/hora, ainda assim
    // deixamos uma linha vazia para a tarja não "sumir" durante o preview.
    if (linhas.isEmpty()) {
        linhas.add("__cont__" to " ")
    }

    val bmpOverlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val alturaBarra = ImageHelper.drawOverlayKV(
        Canvas(bmpOverlay),
        0f,
        h.toFloat(),
        w.toFloat(),
        linhas,
        cores,
        deslocarDireita = isPreview,
        isGavetaFechada = isGavetaFechada
    )
    return bmpOverlay to alturaBarra
}

// Gera o TRIPLET final do LIVRE (limpa / overlay / final fundido).
// Usado pelos botões de baixar e compartilhar dentro do diálogo.
private fun gerarParImagemFinalLivre(
    foto: Bitmap,
    texto: String,
    incluirDataHora: Boolean,
    horaOverride: String,
    statusOverlay: String?
): Triple<Bitmap, Bitmap, Bitmap> {
    val (W, H) = calcularDimensoesLivre(foto)

    val bmpLimpo = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val cl = Canvas(bmpLimpo); cl.drawColor(Color.BLACK)
    val sc = scaleCrop(foto, W, H)
    cl.drawBitmap(sc, 0f, 0f, null)
    if (sc != foto) sc.recycle()

    val (bmpOverlay, _) = gerarOverlayLivre(
        texto = texto,
        incluirDataHora = incluirDataHora,
        horaOverride = horaOverride,
        statusOverlay = statusOverlay,
        w = W,
        h = H,
        isPreview = false,
        isGavetaFechada = false
    )

    val bmpFinal = bmpLimpo.copy(Bitmap.Config.ARGB_8888, true)
    Canvas(bmpFinal).drawBitmap(bmpOverlay, 0f, 0f, null)

    return Triple(bmpLimpo, bmpOverlay, bmpFinal)
}

// Diálogo do LIVRE. Estrutura pareada ao mostrarDialogResultadoNA mas com
// campo de texto QWERTY multi-linha + toggle "ADICIONAR DATA E HORA?".
private fun mostrarDialogResultadoLivre(item: ItemHm) {
    val fotoLV = item.fotoSup ?: return

    val view = layoutInflater.inflate(R.layout.dialog_resultado_livre, null)
    val d = android.app.AlertDialog.Builder(this).setView(view).create()
    d.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))

    // ═══ Responsividade por tamanho de tela ═══
    run {
        val alturaTelaDp = resources.displayMetrics.heightPixels / resources.displayMetrics.density
        val escala = (alturaTelaDp / 700f).coerceIn(0.78f, 1f)
        fun px(dpValor: Int): Int = (dpValor * escala * resources.displayMetrics.density).toInt()
        fun sp(spValor: Float): Float = spValor * escala

        val topBar = view.findViewById<LinearLayout>(R.id.topBarDialogLV)
        topBar.setPadding(px(18), px(18), px(18), px(10))
        view.findViewById<TextView>(R.id.tvLVLabelDialogLV).setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(12f))
        // REMOVIDO: view.findViewById<TextView>(R.id.tvTituloDialogLV)
        view.findViewById<ImageView>(R.id.btnFecharDialogLV).layoutParams =
            view.findViewById<ImageView>(R.id.btnFecharDialogLV).layoutParams.apply {
                width = px(40); height = px(40)
            }

        view.findViewById<FrameLayout>(R.id.frameImagemLV).let { frame ->
            (frame.layoutParams as ViewGroup.MarginLayoutParams).setMargins(px(10), px(10), px(10), px(10))
        }
        view.findViewById<ReguaVerticalView>(R.id.reguaEdicaoDialogLV).layoutParams =
            view.findViewById<ReguaVerticalView>(R.id.reguaEdicaoDialogLV).layoutParams.apply {
                height = px(80)
            }

        val layoutInferior = view.findViewById<LinearLayout>(R.id.layoutBotoesInferioresLV)
        layoutInferior.setPadding(px(18), layoutInferior.paddingTop, px(18), px(18))
        (view.findViewById<View>(R.id.dividerLV).layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = px(12)
        (view.findViewById<LinearLayout>(R.id.blocoTextoLV).layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = px(12)

        view.findViewById<TextView>(R.id.tvZoomLevelLV).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(10f))
            setPadding(px(8), px(3), px(8), px(3))
        }
        view.findViewById<EditText>(R.id.etTextoLivre).setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(16f))

        view.findViewById<CardView>(R.id.cardBtnHoraLV).let { card ->
            (card.layoutParams as ViewGroup.MarginLayoutParams).marginEnd = px(8)
            card.requestLayout()
        }
    }

    val imgView = view.findViewById<ImageView>(R.id.imgResultadoLV)
    val imgOverlayLV = view.findViewById<ImageView>(R.id.imgResultadoOverlayLV)
    val tvZoomLevel = view.findViewById<TextView?>(R.id.tvZoomLevelLV)

    val (lvW, lvH) = calcularDimensoesLivre(fotoLV)
    val bmpBasePreviewLV = scaleCrop(fotoLV, lvW, lvH)
    imgView.setImageBitmap(bmpBasePreviewLV)

    // ═══ IMPORTANTE: LIVRE NÃO vai para o Cofre nem para o Histórico.
    //  Não chamamos CofreManager.salvarSeNovo aqui de propósito. ═══

    // ═══ Trava de altura responsível da foto (mesmo cálculo do N.A.) ═══
    run {
        val topBarLV = view.findViewById<LinearLayout>(R.id.topBarDialogLV)
        val blocoInferiorLV = view.findViewById<LinearLayout>(R.id.layoutBotoesInferioresLV)
        val larguraDisponivelPx = resources.displayMetrics.widthPixels
        val widthSpec = View.MeasureSpec.makeMeasureSpec(larguraDisponivelPx, View.MeasureSpec.EXACTLY)
        val heightSpecLivre = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        topBarLV.measure(widthSpec, heightSpecLivre)
        blocoInferiorLV.measure(widthSpec, heightSpecLivre)

        val statusBarResId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val alturaStatusBarPx = if (statusBarResId > 0) resources.getDimensionPixelSize(statusBarResId) else 0
        val margemFramePx = (10 * resources.displayMetrics.density * 2).toInt()
        val bufferSegurancaPx = (16 * resources.displayMetrics.density).toInt()

        val alturaMaxFotoPx = resources.displayMetrics.heightPixels -
                alturaStatusBarPx -
                topBarLV.measuredHeight -
                blocoInferiorLV.measuredHeight -
                margemFramePx -
                bufferSegurancaPx

        if (alturaMaxFotoPx > 0) {
            imgView.maxHeight = alturaMaxFotoPx
            imgOverlayLV.maxHeight = alturaMaxFotoPx
        }
    }

    setupZoom(imgView, tvZoomLevel, null)

    // Tarja acompanha a matriz exata da foto (mesmo padrão do N.A.)
    imgOverlayLV.scaleType = ImageView.ScaleType.MATRIX
    imgOverlayLV.isClickable = false
    imgOverlayLV.isFocusable = false

    val etTexto = view.findViewById<EditText>(R.id.etTextoLivre)
    val textoPadraoLivre = "VAZÃO / BA 73 74 e 86"

    // Se a memória estiver vazia (primeira vez, ou se o usuário apagou tudo
    // manualmente numa sessão anterior), define o texto automático de novo.
    if (item.textoLivre.isEmpty()) {
        item.textoLivre = textoPadraoLivre
    }

    // Coloca o texto na tela
    etTexto.setText(item.textoLivre)
    etTexto.setSelection(etTexto.text.length)

    view.findViewById<ImageView>(R.id.btnFecharDialogLV).setOnClickListener {
        esconderTeclado(etTexto)
        d.dismiss()
    }

    val btnEditarHoraLV = view.findViewById<ImageView>(R.id.btnEditarHoraLV)
    val horaOriginal = if (item.dataHoraSup.isNotEmpty()) item.dataHoraSup else {
        SimpleDateFormat("dd.MM.yyyy // HH:mm'h'", Locale.getDefault()).format(Date())
    }
    var horaAtual = horaOriginal

    // ═══ Toggle "ADICIONAR DATA E HORA?" (SIM = verde / NÃO = vermelho) ═══
    // Estado inicial: o que foi escolhido da última vez (padrão SIM na
    // primeiríssima vez, já que ItemHm nasce com incluirDataHoraLivre = true).
    var incluirDataHora = item.incluirDataHoraLivre

    val toggleSim = view.findViewById<CardView>(R.id.toggleDataHoraSim)
    val toggleNao = view.findViewById<CardView>(R.id.toggleDataHoraNao)
    val tvToggleSim = view.findViewById<TextView>(R.id.tvToggleDataHoraSim)
    val tvToggleNao = view.findViewById<TextView>(R.id.tvToggleDataHoraNao)

    var isGavetaLVFechada = true
    var alturaBarraBitmap = 0f

    fun calcularOffsetGavetaFechadaLV(): Float {
        val dr = imgOverlayLV.drawable ?: return 0f
        val iw = dr.intrinsicWidth.toFloat().coerceAtLeast(1f)
        val ih = dr.intrinsicHeight.toFloat().coerceAtLeast(1f)
        val vw = imgView.width.toFloat()
        val vh = imgView.height.toFloat()
        if (vw <= 0f || vh <= 0f) return 0f
        val scale = maxOf(vw / iw, vh / ih)
        return alturaBarraBitmap * scale
    }

    fun aplicarPosicaoGavetaTarjaLV(animar: Boolean) {
        val alvo = if (isGavetaLVFechada) calcularOffsetGavetaFechadaLV() else 0f
        if (animar) {
            imgOverlayLV.animate()
                .translationY(alvo)
                .setDuration(300L)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        } else {
            imgOverlayLV.translationY = alvo
        }
    }

    fun reajustarTarjaLVLive(animar: Boolean = false) {
        val vw = imgView.width.toFloat()
        val vh = imgView.height.toFloat()
        val dr = imgOverlayLV.drawable ?: return
        val iw = dr.intrinsicWidth.toFloat().coerceAtLeast(1f)
        val ih = dr.intrinsicHeight.toFloat().coerceAtLeast(1f)
        if (vw <= 0f || vh <= 0f) return

        val scale = maxOf(vw / iw, vh / ih)
        val dx = (vw - iw * scale) / 2f
        val dy = vh - (ih * scale)

        val matrix = Matrix()
        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)

        imgOverlayLV.imageMatrix = matrix
        aplicarPosicaoGavetaTarjaLV(animar)
    }

    // Status escolhido no botão do dashboard. "NENHUM" → não entra na tarja.
    val statusOverlay: String? = if (statusBomba == "NENHUM") null else statusBomba

    fun atualizarOverlayPreviewLV(animar: Boolean = false) {
        val (bmp, hBarra) = gerarOverlayLivre(
            texto = etTexto.text.toString(),
            incluirDataHora = incluirDataHora,
            horaOverride = horaAtual,
            statusOverlay = statusOverlay,
            w = lvW,
            h = lvH,
            isPreview = true,
            isGavetaFechada = isGavetaLVFechada
        )
        alturaBarraBitmap = hBarra
        imgOverlayLV.setImageBitmap(bmp)
        reajustarTarjaLVLive(animar)
    }

    imgView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        reajustarTarjaLVLive()
    }

    fun pintarToggle() {
        // Verde quando SIM ativo, cinza quando SIM inativo.
        // Vermelho quando NÃO ativo, cinza quando NÃO inativo.
        if (incluirDataHora) {
            toggleSim.setCardBackgroundColor(Color.parseColor("#16A34A"))
            tvToggleSim.setTextColor(Color.parseColor("#FFFFFF"))
            toggleNao.setCardBackgroundColor(Color.parseColor("#F1F5F9"))
            tvToggleNao.setTextColor(Color.parseColor("#94A3B8"))
        } else {
            toggleSim.setCardBackgroundColor(Color.parseColor("#F1F5F9"))
            tvToggleSim.setTextColor(Color.parseColor("#94A3B8"))
            toggleNao.setCardBackgroundColor(Color.parseColor("#DC2626"))
            tvToggleNao.setTextColor(Color.parseColor("#FFFFFF"))
        }
    }
    pintarToggle()

    toggleSim.setOnClickListener {
        if (!incluirDataHora) {
            incluirDataHora = true
            item.incluirDataHoraLivre = true
            pintarToggle()
            atualizarOverlayPreviewLV()
        }
    }
    toggleNao.setOnClickListener {
        if (incluirDataHora) {
            incluirDataHora = false
            item.incluirDataHoraLivre = false
            pintarToggle()
            atualizarOverlayPreviewLV()
        }
    }

    atualizarOverlayPreviewLV()
    imgView.post { reajustarTarjaLVLive() }

    // ═══ Mesma correção do N.A.: área de toque exata da abinha, calculada
    // com a mesma matemática de ImageHelper.drawOverlayKV, em vez de uma
    // porcentagem genérica da view (que antes cobria metade da largura e
    // 40% da altura — enorme demais). ═══
    fun toqueNaAbaGavetaLV(toqueX: Float, toqueY: Float): Boolean {
        val dr = imgOverlayLV.drawable ?: return false
        val iw = dr.intrinsicWidth.toFloat().coerceAtLeast(1f)
        val ih = dr.intrinsicHeight.toFloat().coerceAtLeast(1f)
        val vw = imgOverlayLV.width.toFloat()
        val vh = imgOverlayLV.height.toFloat()
        if (vw <= 0f || vh <= 0f) return false
        val scale = maxOf(vw / iw, vh / ih)
        val dx = (vw - iw * scale) / 2f
        val dy = vh - (ih * scale)

        val padH = iw * 0.08f
        val abaLargura = iw * 0.144f
        val abaAltura = iw * 0.055f
        val abaRight = iw - padH
        val abaLeft = abaRight - abaLargura
        val abaBottom = ih - alturaBarraBitmap
        val abaTop = abaBottom - abaAltura

        val folgaBitmap = (10f * resources.displayMetrics.density) / scale

        val esquerdaView = dx + (abaLeft - folgaBitmap) * scale
        val direitaView  = dx + (abaRight + folgaBitmap) * scale
        val topoView     = dy + (abaTop - folgaBitmap) * scale
        val baixoView    = dy + (abaBottom + folgaBitmap) * scale

        return toqueX in esquerdaView..direitaView && toqueY in topoView..baixoView
    }

    imgOverlayLV.setOnTouchListener { v, event ->
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (toqueNaAbaGavetaLV(event.x, event.y)) {
                isGavetaLVFechada = !isGavetaLVFechada
                atualizarOverlayPreviewLV(animar = true)
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                return@setOnTouchListener true
            }
        }
        false
    }

    etTexto.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, st: Int, cnt: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, st: Int, before: Int, cnt: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            item.textoLivre = s?.toString() ?: ""
            atualizarOverlayPreviewLV()
        }
    })

    btnEditarHoraLV?.setOnClickListener {
        mostrarDialogEditarDataHora(
            horaAtual = horaAtual,
            horaOriginal = horaOriginal,
            onConfirmar = { novaHora ->
                horaAtual = novaHora
                // Se o usuário editou a hora manualmente, entende-se que quer incluí-la.
                if (!incluirDataHora) {
                    incluirDataHora = true
                    pintarToggle()
                }
                atualizarOverlayPreviewLV()
                Toast.makeText(this, "✓ Horário atualizado para a foto final", Toast.LENGTH_SHORT).show()
            }
        )
    }

    val reguaLV = view.findViewById<ReguaVerticalView>(R.id.reguaEdicaoDialogLV)
    reguaLV.alinharEsquerda = false
    reguaLV.onValorMudou = { modo, valor ->
        FiltroImagemHelper.aplicarFiltroAoVivo(imgView, modo, valor)
    }

    view.findViewById<Button>(R.id.btnBaixarLV).setOnClickListener { botao ->
        botao.isEnabled = false
        scope.launch {
            try {
                val (bmpLimpo, bmpOverlay, _) = withContext(Dispatchers.Default) {
                    gerarParImagemFinalLivre(
                        fotoLV,
                        etTexto.text.toString(),
                        incluirDataHora,
                        horaAtual,
                        statusOverlay
                    )
                }

                val valorModoAtivoLV = when (reguaLV.modoAtual) {
                    ReguaVerticalView.Modo.BRILHO      -> reguaLV.valorBrilho
                    ReguaVerticalView.Modo.NITIDEZ     -> reguaLV.valorNitidez
                    ReguaVerticalView.Modo.VETORIZACAO -> reguaLV.valorVetorizacao
                }
                val finalEditado = withContext(Dispatchers.Default) {
                    FiltroImagemHelper.fundirCamadasParaSalvar(
                        bmpLimpo, bmpOverlay,
                        reguaLV.modoAtual, valorModoAtivoLV,
                        reguaLV.valorVetorizacao
                    )
                }

                // ══ IMPORTANTE: LIVRE NÃO salva no Histórico Global.
                // (Nenhuma chamada a salvarNoHistoricoGlobal aqui.) ══

                val salvou = salvarImagemAsync(finalEditado)
                if (salvou) {
                    Toast.makeText(this@DashboardActivity, "✓ Imagem salva em Galeria/INSPETOR", Toast.LENGTH_SHORT).show()
                    if (bmpLimpo != finalEditado) bmpLimpo.recycle()
                    d.dismiss()
                } else {
                    Toast.makeText(this@DashboardActivity, "Não foi possível salvar a imagem.", Toast.LENGTH_SHORT).show()
                    if (bmpLimpo != finalEditado) bmpLimpo.recycle()
                }
            } finally {
                botao.isEnabled = true
            }
        }
    }

    view.findViewById<Button>(R.id.btnCompartilharLV).setOnClickListener { botao ->
        botao.isEnabled = false
        scope.launch {
            try {
                val (bmpLimpo, bmpOverlay, _) = withContext(Dispatchers.Default) {
                    gerarParImagemFinalLivre(
                        fotoLV,
                        etTexto.text.toString(),
                        incluirDataHora,
                        horaAtual,
                        statusOverlay
                    )
                }

                val valorModoAtivoLV = when (reguaLV.modoAtual) {
                    ReguaVerticalView.Modo.BRILHO      -> reguaLV.valorBrilho
                    ReguaVerticalView.Modo.NITIDEZ     -> reguaLV.valorNitidez
                    ReguaVerticalView.Modo.VETORIZACAO -> reguaLV.valorVetorizacao
                }
                val finalEditado = withContext(Dispatchers.Default) {
                    FiltroImagemHelper.fundirCamadasParaSalvar(
                        bmpLimpo, bmpOverlay,
                        reguaLV.modoAtual, valorModoAtivoLV,
                        reguaLV.valorVetorizacao
                    )
                }

                val uri = prepararUriCompartilhamentoAsync(finalEditado)
                if (uri != null) {
                    compartilharUri(uri, "Registro")
                    if (bmpLimpo != finalEditado) bmpLimpo.recycle()
                    d.dismiss()
                } else {
                    Toast.makeText(this@DashboardActivity, "Não foi possível compartilhar a imagem.", Toast.LENGTH_SHORT).show()
                    if (bmpLimpo != finalEditado) bmpLimpo.recycle()
                }
            } finally {
                botao.isEnabled = true
            }
        }
    }

    d.show()
    d.window?.apply {
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
    }

    // Ajuste de padding inferior quando o teclado abre (mesmo padrão do N.A.)
    val scrollLV = view.findViewById<ScrollView>(R.id.scrollDialogLV)
    val layoutInferiorLV = view.findViewById<LinearLayout>(R.id.layoutBotoesInferioresLV)
    val paddingBottomBaseLV = layoutInferiorLV.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(scrollLV) { _, insets ->
        val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        layoutInferiorLV.setPadding(
            layoutInferiorLV.paddingLeft,
            layoutInferiorLV.paddingTop,
            layoutInferiorLV.paddingRight,
            paddingBottomBaseLV + barras.bottom
        )
        insets
    }
    ViewCompat.requestApplyInsets(scrollLV)

    var tecladoAbertoLV = false
    var alturaBaseScrollLV = 0
    val limiarTecladoPx = dp(150)
    val globalLayoutListenerLV = ViewTreeObserver.OnGlobalLayoutListener {
        val alturaAtual = scrollLV.height
        if (alturaAtual > 0) {
            if (alturaBaseScrollLV == 0) {
                alturaBaseScrollLV = alturaAtual
            } else {
                val diferenca = alturaBaseScrollLV - alturaAtual
                val agoraAberto = diferenca > limiarTecladoPx
                if (agoraAberto != tecladoAbertoLV) {
                    tecladoAbertoLV = agoraAberto
                    val conteudo = scrollLV.getChildAt(0)
                    if (agoraAberto) {
                        scrollLV.post { scrollSuaveRapido(scrollLV, conteudo.height) }
                    } else {
                        scrollLV.post { scrollSuaveRapido(scrollLV, 0) }
                    }
                } else if (!agoraAberto) {
                    alturaBaseScrollLV = alturaAtual
                }
            }
        }
    }
    scrollLV.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListenerLV)
    d.setOnDismissListener {
        scrollLV.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListenerLV)
    }

    // NÃO abre o teclado automaticamente — o usuário decide quando digitar.
    // (Diferente do N.A., aqui a foto é o protagonista.)
}

// ═══ NOVO: card "SC" — tela de recorte/perspectiva 100% própria do
// INSPETOR (sem NENHUMA dependência do Google/OpenCV). Aparece assim que
// a foto é tirada, já com o DetectorBordaEngine sugerindo os 4 cantos
// automaticamente (se não conseguir detectar com confiança, cai na
// margem fixa de 6%, como antes). Fundo branco, no mesmo estilo do resto
// do app.
//
// Duas ferramentas na mesma tela:
//   • Arrastar os 4 CANTOS (bolinhas marrons) corrige a perspectiva e
//     "achata" o documento (resolve o pedido de "deixar reto e plano").
//   • Arrastar as alças BRANCAS no meio de cada lado corta em linha reta:
//     puxar a de cima/baixo move as duas pontas daquela borda JUNTAS
//     (sempre reto); puxar a da esquerda/direita faz o mesmo na horizontal.
//   • O slider "ENDIREITAR" gira a foto pra corrigir inclinação de câmera
//     antes do corte final.
private fun mostrarDialogRecorteSC(bmpOriginal: Bitmap, item: ItemHm, cantosSugeridos: Array<PointF>?) {
    val view = layoutInflater.inflate(R.layout.dialog_recorte_sc, null)
    val d = android.app.AlertDialog.Builder(this).setView(view).create()
    d.setCancelable(false)
    d.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))

    val cropView = view.findViewById<DocumentCropView>(R.id.cropViewSC)
    val seekGraus = view.findViewById<SeekBar>(R.id.seekEndireitarSC)
    val tvGraus = view.findViewById<TextView>(R.id.tvGrausRecorteSC)
    val btnAplicar = view.findViewById<Button>(R.id.btnAplicarRecorteSC)

    // Bitmap "base" atual (muda quando o usuário mexe no slider de endireitar).
    var bitmapAtual = bmpOriginal
    cropView.setImagem(bitmapAtual, cantosSugeridos)

    // Slider de -15° a +15°, centralizado em 0.
    seekGraus.max = 30
    seekGraus.progress = 15
    seekGraus.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, progresso: Int, fromUser: Boolean) {
            tvGraus.text = "${progresso - 15}°"
            // ═══ CORREÇÃO "trava"/pouco fluido: antes NADA acontecia na
            // tela enquanto o dedo arrastava o slider — a rotação de
            // verdade (pesada, recria o bitmap inteiro) só rodava ao
            // SOLTAR o dedo, em onStopTrackingTouch. Isso dava a sensação
            // de travado, porque não existia nenhum feedback visual
            // durante o arraste. Agora, enquanto arrasta, giramos a
            // PRÓPRIA VIEW (transformação barata, sem recriar bitmap) —
            // fica instantâneo e fluido. Só quando solta o dedo é que
            // fazemos o recorte de verdade, pixel a pixel, no bitmap. ═══
            if (fromUser) cropView.rotation = (progresso - 15).toFloat()
        }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {
            val graus = (sb?.progress ?: 15) - 15
            scope.launch {
                val girado = withContext(Dispatchers.Default) {
                    RecorteDocumentoEngine.rotacionar(bmpOriginal, graus.toFloat())
                }
                bitmapAtual = girado
                cropView.rotation = 0f // desfaz o preview "barato" — a partir daqui o bitmap já está girado de verdade
                // A foto girada muda de tamanho/orientação, então os cantos
                // detectados (calculados na foto original) não valem mais
                // aqui — volta pra margem padrão de 6%, mais segura.
                cropView.setImagem(bitmapAtual)
            }
        }
    })

    view.findViewById<ImageView>(R.id.btnFecharDialogRecorteSC).setOnClickListener {
        d.dismiss()
        captureFase = ""
    }

    // ═══ CORREÇÃO: esse botão existia no layout (ícone "+" no topo) mas
    // não tinha NENHUM código conectando ele — tocar nele não fazia nada.
    // Agora ativa o modo de marcação por toque do DocumentCropView: toque
    // nos 4 cantos do documento na tela (em qualquer ordem) que cada
    // toque "puxa" o canto mais próximo pra aquele lugar. ═══
    cropView.onCantosDefinidos = {
        Toast.makeText(this, "4 cantos marcados! Ajuste se precisar e toque em APLICAR.", Toast.LENGTH_LONG).show()
    }
    view.findViewById<ImageView>(R.id.btnModoToqueSC).setOnClickListener {
        cropView.enableTapMode()
        Toast.makeText(this, "Toque nos 4 cantos do documento, em qualquer ordem", Toast.LENGTH_LONG).show()
    }

    // ═══ NOVO: botão "detectar borda automaticamente" — roda o mesmo
    // detector de sempre, mas sob demanda, em cima da foto ATUAL (já
    // rotacionada, se o usuário tiver usado o ENDIREITAR antes). Útil
    // quando a detecção da hora da captura não saiu boa. ═══
    val btnAutoDetectar = view.findViewById<ImageView>(R.id.btnAutoDetectarBordaSC)
    btnAutoDetectar.setOnClickListener {
        btnAutoDetectar.isEnabled = false
        scope.launch {
            val cantosDetectados = withContext(Dispatchers.Default) {
                DetectorBordaEngine.detectarCantos(bitmapAtual)
            }
            btnAutoDetectar.isEnabled = true
            if (cantosDetectados != null && cropView.aplicarCantosDetectados(cantosDetectados)) {
                Toast.makeText(this@DashboardActivity, "Borda detectada! Ajuste se precisar.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@DashboardActivity, "Não consegui detectar a borda automaticamente — ajuste manualmente ou toque nos 4 cantos.", Toast.LENGTH_LONG).show()
            }
        }
    }

    view.findViewById<Button>(R.id.btnResetarRecorteSC).setOnClickListener {
        val grausAtual = seekGraus.progress - 15
        if (grausAtual == 0 && cantosSugeridos != null) {
            cropView.setImagem(bitmapAtual, cantosSugeridos)
        } else {
            cropView.setImagem(bitmapAtual)
        }
    }

    btnAplicar.setOnClickListener { botao ->
        botao.isEnabled = false
        val cantos = cropView.getCantosNaImagem()
        scope.launch {
            val recortado = withContext(Dispatchers.Default) {
                RecorteDocumentoEngine.recortarEAchatar(bitmapAtual, cantos)
            }
            val agora = SimpleDateFormat("dd.MM.yyyy // HH:mm'h'", Locale.getDefault()).format(Date())
            item.fotoSup?.recycle()
            item.fotoSup = recortado
            item.dataHoraSup = agora
            restaurarPreviews()
            d.dismiss()
        }
    }

    d.show()
    d.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
}

// ═══ NOVO: card "SC" — diálogo de resultado do Scanner de Documentos.
// Bem mais enxuto que o do LIVRE: sem tarja, sem texto, sem status — o
// documento já sai pronto do recorte/perspectiva. Contém a foto (em
// canvas de tamanho FIXO — layout_weight, nunca estica o diálogo nem
// pede scroll, ver dialog_resultado_sc.xml), o seletor de filtro
// (ORIGINAL/MÁGICA/P&B, via FiltroScannerExt.kt — só ColorMatrix, sem
// OpenCV/ML Kit) com sliders AJUSTÁVEIS de intensidade (nada de
// intensidade fixa — o usuário decide o quanto quer de cada efeito), a
// gaveta de edição fina (luz/brilho/nitidez/vetorização) e
// BAIXAR/COMPARTILHAR marrons.
// NÃO salva no Histórico Global nem no Cofre, seguindo o mesmo padrão do LIVRE. ═══
private fun mostrarDialogResultadoSC(item: ItemHm) {
    val fotoOriginalSC = item.fotoSup ?: return

    val view = layoutInflater.inflate(R.layout.dialog_resultado_sc, null)
    val d = android.app.AlertDialog.Builder(this).setView(view).create()
    d.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))

    val imgView = view.findViewById<ImageView>(R.id.imgResultadoSC)
    imgView.setImageBitmap(fotoOriginalSC)

    view.findViewById<ImageView>(R.id.btnFecharDialogSC).setOnClickListener { d.dismiss() }

    // ═══ Filtros de scanner — a foto usada no BAIXAR/COMPARTILHAR e na
    // gaveta de edição é sempre `fotoBaseSC` (resultado do filtro + slider
    // escolhidos), nunca a foto crua diretamente. ═══
    var fotoBaseSC = fotoOriginalSC

    val cardOriginal = view.findViewById<CardView>(R.id.cardFiltroOriginalSC)
    val cardMagico    = view.findViewById<CardView>(R.id.cardFiltroMagicoSC)
    val cardPB        = view.findViewById<CardView>(R.id.cardFiltroPBSC)
    val btnFiltroOriginal = view.findViewById<Button>(R.id.btnFiltroOriginalSC)
    val btnFiltroMagico   = view.findViewById<Button>(R.id.btnFiltroMagicoSC)
    val btnFiltroPB       = view.findViewById<Button>(R.id.btnFiltroPBSC)

    val painelMagico = view.findViewById<LinearLayout>(R.id.painelSliderMagicoSC)
    val painelPB     = view.findViewById<LinearLayout>(R.id.painelSliderPBSC)
    val seekContraste = view.findViewById<SeekBar>(R.id.seekContrasteSC)
    val seekBrilho    = view.findViewById<SeekBar>(R.id.seekBrilhoSC)
    val seekLimiar    = view.findViewById<SeekBar>(R.id.seekLimiarPBSC)
    val tvContraste = view.findViewById<TextView>(R.id.tvContrasteValorSC)
    val tvBrilho    = view.findViewById<TextView>(R.id.tvBrilhoValorSC)
    val tvLimiar    = view.findViewById<TextView>(R.id.tvLimiarValorSC)

    val corSelecionada    = Color.parseColor("#8B5C29")
    val corNaoSelecionada = Color.parseColor("#F3ECE2")

    // progresso (0-100) -> contraste real (1.0 a 2.0). Sliders começam
    // suaves de propósito — foi o excesso fixo que "estourou" a foto antes.
    fun contrasteReal(progresso: Int) = 1.0f + (progresso / 100f) * 1.0f
    // progresso (0-100) -> brilho real (0 a 45)
    fun brilhoReal(progresso: Int) = (progresso / 100f) * 45f

    fun marcarFiltroSelecionado(cardAtivo: CardView) {
        for (card in listOf(cardOriginal, cardMagico, cardPB)) {
            val ehAtivo = card == cardAtivo
            card.setCardBackgroundColor(if (ehAtivo) corSelecionada else corNaoSelecionada)
            val botao = card.getChildAt(0) as? Button
            botao?.setTextColor(if (ehAtivo) Color.WHITE else corSelecionada)
        }
    }

    fun regenerarMagico() {
        scope.launch {
            val resultado = withContext(Dispatchers.Default) {
                fotoOriginalSC.aplicarFiltroMagico(
                    contraste = contrasteReal(seekContraste.progress),
                    brilho = brilhoReal(seekBrilho.progress)
                )
            }
            fotoBaseSC = resultado
            imgView.setImageBitmap(resultado)
        }
    }

    fun regenerarPB() {
        scope.launch {
            val resultado = withContext(Dispatchers.Default) {
                fotoOriginalSC.aplicarFiltroPB(limiar = seekLimiar.progress)
            }
            fotoBaseSC = resultado
            imgView.setImageBitmap(resultado)
        }
    }

    btnFiltroOriginal.setOnClickListener {
        marcarFiltroSelecionado(cardOriginal)
        painelMagico.visibility = View.GONE
        painelPB.visibility = View.GONE
        fotoBaseSC = fotoOriginalSC
        imgView.setImageBitmap(fotoOriginalSC)
    }
    btnFiltroMagico.setOnClickListener {
        marcarFiltroSelecionado(cardMagico)
        painelMagico.visibility = View.VISIBLE
        painelPB.visibility = View.GONE
        regenerarMagico()
    }
    btnFiltroPB.setOnClickListener {
        marcarFiltroSelecionado(cardPB)
        painelMagico.visibility = View.GONE
        painelPB.visibility = View.VISIBLE
        regenerarPB()
    }

    // Texto do "%" atualiza na hora (leve); a foto só é reprocessada
    // quando o dedo solta o slider (pesado — evita reprocessar a cada
    // pixel arrastado).
    seekContraste.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { tvContraste.text = "$p%" }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) { regenerarMagico() }
    })
    seekBrilho.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { tvBrilho.text = "$p%" }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) { regenerarMagico() }
    })
    seekLimiar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { tvLimiar.text = "$p" }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) { regenerarPB() }
    })

    val reguaSC = view.findViewById<ReguaVerticalView>(R.id.reguaEdicaoDialogSC)
    reguaSC.alinharEsquerda = false
    reguaSC.onValorMudou = { modo, valor ->
        FiltroImagemHelper.aplicarFiltroAoVivo(imgView, modo, valor)
    }

    fun valorModoAtivoSC(): Float = when (reguaSC.modoAtual) {
        ReguaVerticalView.Modo.BRILHO      -> reguaSC.valorBrilho
        ReguaVerticalView.Modo.NITIDEZ     -> reguaSC.valorNitidez
        ReguaVerticalView.Modo.VETORIZACAO -> reguaSC.valorVetorizacao
    }

    // A imagem já sai "achatada" (sem camadas separadas de overlay), então
    // usamos fotoBaseSC (já com filtro de scanner + sliders aplicados, se
    // houver) como "limpa" e "overlay" — fundirCamadasParaSalvar aceita
    // isso normalmente.
    view.findViewById<Button>(R.id.btnBaixarSC).setOnClickListener { botao ->
        botao.isEnabled = false
        scope.launch {
            try {
                val finalEditado = withContext(Dispatchers.Default) {
                    FiltroImagemHelper.fundirCamadasParaSalvar(
                        fotoBaseSC, fotoBaseSC,
                        reguaSC.modoAtual, valorModoAtivoSC(),
                        reguaSC.valorVetorizacao
                    )
                }
                val salvou = salvarImagemAsync(finalEditado)
                if (salvou) {
                    Toast.makeText(this@DashboardActivity, "✓ Documento salvo em Galeria/INSPETOR", Toast.LENGTH_SHORT).show()
                    d.dismiss()
                } else {
                    Toast.makeText(this@DashboardActivity, "Não foi possível salvar a imagem.", Toast.LENGTH_SHORT).show()
                }
            } finally {
                botao.isEnabled = true
            }
        }
    }

    view.findViewById<Button>(R.id.btnCompartilharSC).setOnClickListener { botao ->
        botao.isEnabled = false
        scope.launch {
            try {
                val finalEditado = withContext(Dispatchers.Default) {
                    FiltroImagemHelper.fundirCamadasParaSalvar(
                        fotoBaseSC, fotoBaseSC,
                        reguaSC.modoAtual, valorModoAtivoSC(),
                        reguaSC.valorVetorizacao
                    )
                }
                val uri = prepararUriCompartilhamentoAsync(finalEditado)
                if (uri != null) {
                    compartilharUri(uri, "Documento digitalizado")
                    d.dismiss()
                } else {
                    Toast.makeText(this@DashboardActivity, "Não foi possível compartilhar a imagem.", Toast.LENGTH_SHORT).show()
                }
            } finally {
                botao.isEnabled = true
            }
        }
    }

    d.show()
    d.window?.apply {
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    }
}

    inner class HmAdapter(var lista: List<ItemHm>, private val onClick: (Int) -> Unit) : RecyclerView.Adapter<HmAdapter.VH>() {
        fun atualizarLista(novaLista: List<ItemHm>) {
            this.lista = novaLista; notifyDataSetChanged()
        }
        inner class VH(val view: View) : RecyclerView.ViewHolder(view)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_hidrometro, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            if (lista.isEmpty()) return
            val realIdx = if (estacaoSelecionada.nome == "DET-01" && lista.isNotEmpty()) position % lista.size else position.coerceIn(0, lista.size - 1)
            if (realIdx < 0 || realIdx >= lista.size) return
            val item = lista[realIdx]
            val card = holder.view.findViewById<CardView>(R.id.cardHm)
            holder.view.findViewById<TextView>(R.id.nomeHm).text = item.titulo
            card.setCardBackgroundColor(Color.parseColor(item.cor))
            val sel = realIdx == hmSelecionado
            card.cardElevation = if (sel) dp(5).toFloat() else dp(2).toFloat()
            if (sel) {
                val borda = SilverBorderDrawable(dp(3).toFloat(), dp(14).toFloat())
                card.foreground = borda; borda.start(); card.tag = borda
            } else {
                (card.tag as? SilverBorderDrawable)?.stop(); card.foreground = null; card.tag = null
            }
            holder.view.setOnClickListener { onClick(realIdx) }
        }
        override fun onViewRecycled(holder: VH) {
            ((holder.view.findViewById<CardView>(R.id.cardHm)).tag as? SilverBorderDrawable)?.stop()
            super.onViewRecycled(holder)
        }
        override fun getItemCount(): Int = if (estacaoSelecionada.nome == "DET-01") Int.MAX_VALUE else lista.size
    }

    inner class LagoNAAdapter(private val lista: List<LagoNA>, private val onClick: (Int) -> Unit) : RecyclerView.Adapter<LagoNAAdapter.VH>() {
        inner class VH(val view: View) : RecyclerView.ViewHolder(view)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_hidrometro, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            if (lista.isEmpty()) return
            val realIdx = position % lista.size
            val lago = lista[realIdx]
            
            val card = holder.view.findViewById<CardView>(R.id.cardHm)
            holder.view.findViewById<TextView>(R.id.nomeHm).text = lago.abreviacao
            // ═══ Cada card de N.A. tem sua própria cor; a sombra cinza padrão
            // do CardView é apenas tingida na mesma cor (leve, sem halo extra) ═══
            val corNA = Color.parseColor(coresNeonNA[realIdx % coresNeonNA.size])
            card.setCardBackgroundColor(corNA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                card.outlineAmbientShadowColor = corNA
                card.outlineSpotShadowColor = corNA
            }
            
            val sel = realIdx == lagoNASelecionado
            card.cardElevation = if (sel) dp(5).toFloat() else dp(2).toFloat()
            if (sel) {
                val borda = SilverBorderDrawable(dp(3).toFloat(), dp(14).toFloat())
                card.foreground = borda; borda.start(); card.tag = borda
            } else {
                (card.tag as? SilverBorderDrawable)?.stop(); card.foreground = null; card.tag = null
            }
            holder.view.setOnClickListener { onClick(realIdx) }
        }
        override fun onViewRecycled(holder: VH) {
            ((holder.view.findViewById<CardView>(R.id.cardHm)).tag as? SilverBorderDrawable)?.stop()
            super.onViewRecycled(holder)
        }
        override fun getItemCount(): Int = if (lista.isEmpty()) 0 else Int.MAX_VALUE
    }

    inner class EstacaoAdapter(private val lista: List<Estacao>, private val onClick: (Estacao) -> Unit) : RecyclerView.Adapter<EstacaoAdapter.VH>() {
        inner class VH(val view: View) : RecyclerView.ViewHolder(view)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_station, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val estacao = lista[position % lista.size]
            val card = holder.view.findViewById<CardView>(R.id.cardEstacaoRoot)
            card.alpha = 1f
            val corInt = try { Color.parseColor(estacao.cor) } catch (_: Exception) { Color.parseColor("#2F5BFF") }
            card.setCardBackgroundColor(corInt)
            // ═══ CORREÇÃO: o identificador interno da estação continua
            // "SC" (usado em várias comparações tipo item.tipo == "SC"
            // espalhadas pelo arquivo — mudar o valor quebraria tudo isso).
            // Só o TEXTO exibido no card agora mostra "SCAN". ═══
            holder.view.findViewById<TextView>(R.id.nome).text =
                if (estacao.nome == "SC") "SCAN" else estacao.nome

            // ═══ Sombra cinza padrão do CardView tingida na cor da própria
            // estação — leve, sem halo extra. ═══
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                card.outlineAmbientShadowColor = corInt
                card.outlineSpotShadowColor = corInt
            }

            // Mantém o clique original para acessibilidade / performClick()
            holder.view.setOnClickListener { onClick(estacao) }

            // Feedback visual de "afundar" ao tocar no card da estação
            //
            // ═══ CORREÇÃO: antes esse listener terminava com "return false",
            // achando que isso era necessário pro RecyclerView conseguir
            // rolar o carrossel. Só que retornar false faz o Android
            // TAMBÉM processar o toque pelo caminho padrão da própria View
            // (que sozinha já dispara performClick() ao soltar o dedo) —
            // ou seja, o clique manual abaixo E o automático da View
            // disparavam JUNTOS pro mesmo toque, causando os cliques
            // duplicados/triplicados relatados (com o som de clique repetindo).
            // O padrão correto — já usado em boxHidrometro/boxBomba neste
            // mesmo arquivo — é sempre retornar true, "abraçando" o gesto
            // inteiro. Isso NÃO impede o RecyclerView de interceptar o
            // arrasto pra rolar o carrossel: a interceptação de scroll do
            // RecyclerView acontece antes do evento chegar aqui, independente
            // do que este listener devolve. ═══
            holder.view.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        animarPressDown(v)
                    }
                    MotionEvent.ACTION_UP -> {
                        animarPressUp(v)
                        v.performClick()   // aciona o setOnClickListener acima
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        // Cancela (ex: usuário deslizou o dedo) — restaura sem acionar o clique
                        animarPressUp(v)
                    }
                }
                true
            }
        }
        override fun getItemCount(): Int = Int.MAX_VALUE
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun atualizarCabecalhoSaudacao() {
        val apelido = SecurePrefs.get(this, "inspetor_prefs")
            .getString("apelido", "")
            ?.trim()
            .orEmpty()
            .ifBlank { "Inspetor" }

        val tvSaudacao = findViewById<TextView>(R.id.tvSaudacao)
        val tvApelido = findViewById<TextView>(R.id.tvApelido)

        tvSaudacao?.text = saudacaoPorHorario()
        tvApelido?.text = apelido

        // ═══ FUNÇÃO PREMIUM: DOIS TOQUES PARA EDITAR O NOME ═══
        val detectorDoisToques = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                val prefs = SecurePrefs.get(this@DashboardActivity, "inspetor_prefs")
                val apelidoAtual = prefs.getString("apelido", "") ?: ""

                val editText = android.widget.EditText(this@DashboardActivity).apply {
                    setText(apelidoAtual)
                    setSelection(apelidoAtual.length)
                    hint = "Digite seu nome ou apelido"
                }

                val container = android.widget.FrameLayout(this@DashboardActivity)
                val params = android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = (24 * resources.displayMetrics.density).toInt()
                    rightMargin = (24 * resources.displayMetrics.density).toInt()
                    topMargin = (12 * resources.displayMetrics.density).toInt()
                    bottomMargin = (12 * resources.displayMetrics.density).toInt()
                }
                editText.layoutParams = params
                container.addView(editText)

                androidx.appcompat.app.AlertDialog.Builder(this@DashboardActivity)
                    .setTitle("Editar Nome")
                    .setMessage("Altere o seu nome de exibição do aplicativo:")
                    .setView(container)
                    .setCancelable(false)
                    .setPositiveButton("SALVAR") { dialog, _ ->
                        val novoNome = editText.text.toString().trim()
                        if (novoNome.isNotEmpty()) {
                            prefs.edit().putString("apelido", novoNome).apply()
                            tvApelido?.text = novoNome
                            android.widget.Toast.makeText(this@DashboardActivity, "✓ Nome atualizado!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(this@DashboardActivity, "O nome não pode ficar vazio.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton("CANCELAR") { dialog, _ -> dialog.dismiss() }
                    .show()
                return true
            }
        })

        tvApelido?.setOnTouchListener { v, event ->
            detectorDoisToques.onTouchEvent(event)
            v.performClick()
            true
        }

        btnConfiguracoes.bringToFront()
    }

    private fun saudacaoPorHorario(): String {
        val hora = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hora) {
            in 5..11 -> "Bom dia,"
            in 12..17 -> "Boa tarde,"
            else -> "Boa noite,"
        }
    }
    
    // ════════════════════════════════════════════════════════════════════
    //  FEEDBACK VISUAL DE PRESSÃO — "afundar e subir"
    //  Usado em: cards do EstacaoAdapter, boxHidrometro, boxBomba.
    //  Durations curtas para não atrasar a resposta da UI.
    // ════════════════════════════════════════════════════════════════════

    /**
     * Anima a view para dar a sensação de "afundar" ao ser pressionada.
     * scaleX/Y → 0.94f  |  alpha → 0.7f  |  duração: 80ms
     */
    private fun animarPressDown(view: View) {
        view.animate()
            .scaleX(0.94f)
            .scaleY(0.94f)
            .alpha(0.7f)
            .setDuration(80L)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    /**
     * Restaura a view ao estado normal após soltar o toque.
     * scaleX/Y → 1f  |  alpha → 1f  |  duração: 100ms
     * Usa OvershootInterpolator para um "salto" leve ao voltar — premium feel.
     */
    private fun animarPressUp(view: View) {
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(100L)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
            .start()
    }

    

}
