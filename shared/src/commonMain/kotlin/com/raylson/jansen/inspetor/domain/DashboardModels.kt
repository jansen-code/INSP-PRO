package com.raylson.jansen.inspetor.domain

/**
 * ═══════════════════════════════════════════════════════════════════
 * ARQUIVO: DashboardModels.kt
 * Extraído de DashboardActivity.kt (classes internas Estacao, ItemHm,
 * LagoNA) para um arquivo de domínio puro Kotlin, sem nenhuma
 * dependência de Android — roda em commonMain (Android + iOS).
 *
 * ATENÇÃO KMP: `Bitmap` (android.graphics.Bitmap) não existe fora do
 * Android. Trocamos por `ByteArray` (bytes JPEG/PNG crus). Isso é
 * proposital: ByteArray é trivialmente serializável (persistência,
 * cache em disco, envio pro Cofre) e cada plataforma decodifica pra
 * ImageBitmap (Compose Multiplatform) só na hora de desenhar na tela,
 * via `PlatformImageDecoder` (ver PlatformMappers.kt).
 * ═══════════════════════════════════════════════════════════════════
 */

data class Estacao(
    val nome: String,
    val cor: String
)

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

    // ═══ Antes: Bitmap?. Agora: bytes crus da foto (JPEG), decodificados
    // sob demanda pra ImageBitmap na UI. ═══
    var fotoSup: ByteArray? = null,
    var fotoInf: ByteArray? = null,
    var dataHoraSup: String = "",
    var dataHoraInf: String = "",

    // ═══ Leitura manual (duplo clique no boxHidrometro). Guarda só o
    // valor cru digitado (ex: "55.7"). O prefixo "+ " (HM) ou sufixo
    // " m³/hr" (HM_VAZAO) é aplicado só na hora de exibir/desenhar. ═══
    var leituraManual: String? = null,
    var incluirLeituraNaFoto: Boolean = false,

    // ═══ Persistência do diálogo LIVRE — precisa sobreviver a
    // fechar/reabrir e "gerar novamente" na mesma sessão. ═══
    var textoLivre: String = "",
    var incluirDataHoraLivre: Boolean = true,

    // ═══ Marca se fotoSup/fotoInf vieram do Cofre (Galeria 2) em vez de
    // foto nova da câmera/galeria. Evita duplicar a mesma foto no Cofre
    // toda vez que o usuário gera de novo. ═══
    var fotoSupVeioDoCofre: Boolean = false,
    var fotoInfVeioDoCofre: Boolean = false
) {
    // data class com ByteArray precisa de equals/hashCode manuais pra não
    // comparar por referência de array (Regra de Ouro: preserva o
    // comportamento esperado de igualdade estrutural do data class original)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ItemHm) return false
        return id == other.id && titulo == other.titulo && cor == other.cor &&
            cardAzulLabel == other.cardAzulLabel && cardAzulSub == other.cardAzulSub &&
            tipo == other.tipo && statusDisponiveis == other.statusDisponiveis &&
            statusPadrao == other.statusPadrao && statusVazao == other.statusVazao &&
            fotoSup.contentEquals(other.fotoSup) && fotoInf.contentEquals(other.fotoInf) &&
            dataHoraSup == other.dataHoraSup && dataHoraInf == other.dataHoraInf &&
            leituraManual == other.leituraManual && incluirLeituraNaFoto == other.incluirLeituraNaFoto &&
            textoLivre == other.textoLivre && incluirDataHoraLivre == other.incluirDataHoraLivre &&
            fotoSupVeioDoCofre == other.fotoSupVeioDoCofre && fotoInfVeioDoCofre == other.fotoInfVeioDoCofre
    }

    override fun hashCode(): Int = id.hashCode()
}

data class LagoNA(
    val abreviacao: String,
    val nomeCard: String,
    var fotoRegua: ByteArray? = null,
    var dataHora: String = "",
    var valor: String? = null,
    var foraRegua: Boolean = false,
    var fotoVeioDoCofre: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LagoNA) return false
        return abreviacao == other.abreviacao && nomeCard == other.nomeCard &&
            fotoRegua.contentEquals(other.fotoRegua) && dataHora == other.dataHora &&
            valor == other.valor && foraRegua == other.foraRegua &&
            fotoVeioDoCofre == other.fotoVeioDoCofre
    }

    override fun hashCode(): Int = abreviacao.hashCode()
}

/**
 * Dados estáticos do app — 100% portados sem alteração de valores da
 * DashboardActivity original (Regra de Ouro: nenhum dado foi mudado).
 */
object DashboardData {

    val estacoes = listOf(
        Estacao("DET-01", "#2F5BFF"),
        Estacao("ARB-05", "#EAB308"),
        Estacao("ARB-06", "#22C55E"),
        Estacao("ARB-07", "#F59E0B"),
        Estacao("ARB-08", "#EF4444"),
        Estacao("ARB-09", "#06B6D4"),
        Estacao("LIVRE", "#8B5CF6"),
        Estacao("SC", "#8B5C29"),
        Estacao("N.A.", "#64748B")
    )

    val itensPorEstacao: Map<String, List<ItemHm>> = mapOf(
        "DET-01" to listOf(
            ItemHm("01", "HM-01", "#2F5BFF", "BOMBA-01", "BOMBA CORRESPONDENTE", "HM", listOf("LIGADA", "DESLIGADA"), "DESLIGADA"),
            ItemHm("02", "HM-02", "#2F5BFF", "BOMBA-02", "BOMBA CORRESPONDENTE", "HM", listOf("LIGADA", "DESLIGADA"), "DESLIGADA"),
            ItemHm("03", "HM-03", "#2F5BFF", "BOMBA-03", "BOMBA CORRESPONDENTE", "HM", listOf("LIGADA", "DESLIGADA"), "DESLIGADA"),
            ItemHm("04", "HM-04", "#2F5BFF", "BOMBA-04", "BOMBA CORRESPONDENTE", "HM", listOf("LIGADA", "DESLIGADA"), "DESLIGADA"),
            ItemHm("GAL", "GALERIA", "#EF4444", "GALERIA", "ESTRUTURA", "SIMPLES", emptyList(), ""),
            ItemHm("SIF-SUP", "SIFÃO SUP.", "#06B6D4", "SIFÃO SUPERIOR", "FLUXOS", "SIFAO", listOf("COM VAZÃO", "SEM VAZÃO"), "SEM VAZÃO"),
            ItemHm("SIF-INF", "SIFÃO INF.", "#0EA5E9", "SIFÃO INFERIOR", "FLUXOS", "SIFAO", listOf("COM VAZÃO", "SEM VAZÃO"), "SEM VAZÃO"),
            ItemHm("CALHA", "CALHA", "#F97316", "CALHA PARSHALL", "ESTRUTURA", "SIMPLES", emptyList(), "")
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
        "LIVRE" to listOf(
            ItemHm("LIVRE-01", "GERAL", "#8B5CF6", "GERAL", "ARB'S e LAGOs", "LIVRE", listOf("LIGADO", "DESLIGADO", "ZERADO", "NENHUM"), "NENHUM")
        ),
        "SC" to listOf(
            ItemHm("SC-01", "DOC", "#8B5C29", "DOCUMENTO", "SCANNER", "SC", emptyList(), "")
        )
    )

    val lagosNA = listOf(
        LagoNA("ARB-01", "ARB-01"),
        LagoNA("ARB-08", "ARB-08"),
        LagoNA("ARB-09", "ARB-09"),
        LagoNA("ARB-10", "ARB-10"),
        LagoNA("DET-01", "LAGOA DE DETENÇÃO 01"),
        LagoNA("DET-02", "LAGOA DE DETENÇÃO 02"),
        LagoNA("DT2-ex", "DET-02 EXTRAVASOR"),
        LagoNA("DET-03", "LAGOA DE DETENÇÃO 03"),
        LagoNA("L.BRUTA", "LAGOA BRUTA"),
        LagoNA("CP", "COOLING POND"),
        LagoNA("CP-ex", "COOLING POND EXTRAVASOR")
    )

    // Uma cor fixa por lago de N.A. (mesma ordem de `lagosNA`)
    val coresNeonNA = listOf(
        "#FF0000", "#FF7A00", "#8D6E63", "#00C853", "#FFB300",
        "#2F5BFF", "#212121", "#F4511E", "#EAB308", "#9E9E9E", "#7B1FF2"
    )
}
