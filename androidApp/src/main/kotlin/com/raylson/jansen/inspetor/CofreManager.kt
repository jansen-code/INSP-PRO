package com.raylson.jansen.inspetor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CofreManager — gerenciador do formato proprietário .INS com suporte a Subpastas
 */
object CofreManager {

    private const val PASTA = "cofre"
    private const val EXTENSAO = ".ins"
    private const val MAGIC = "INSPV1"
    // ═══ HD: qualidade de gravação aumentada (era 88). Isso vale só para
    // fotos NOVAS a partir de agora — as antigas continuam sendo lidas
    // normalmente, só foram salvas num tamanho de arquivo menor. ═══
    private const val QUALIDADE = 97
    private const val PREFS_META = "cofre_meta"

    private val CHAVE = byteArrayOf(
        0x49, 0x4E, 0x53, 0x50, 0x45, 0x54, 0x4F, 0x52.toByte(),
        0x43, 0x4F, 0x46, 0x52, 0x45, 0x32, 0x30, 0x32.toByte()
    )

    data class SaveResult(val file: File?, val skippedDuplicate: Boolean)

    // ═══ PERFORMANCE: removido o "% CHAVE.size" de dentro do loop — em
    // arquivos grandes (fotos HD), calcular o resto da divisão a cada
    // byte é um custo que se soma rápido. Em vez disso, andamos um índice
    // `k` que só "reseta" quando chega no fim da chave, sem nunca dividir. ═══
    private fun ofuscar(dados: ByteArray): ByteArray {
        val saida = ByteArray(dados.size)
        val chaveLen = CHAVE.size
        var k = 0
        for (i in dados.indices) {
            saida[i] = (dados[i].toInt() xor CHAVE[k].toInt()).toByte()
            k++
            if (k == chaveLen) k = 0
        }
        return saida
    }

    enum class Grupo(val slug: String, val rotulo: String) {
        DET01("DET01", "DET-01"),
        NA("NA", "N.A."),
        ARBS("ARBS", "ARB'S")
    }

    data class ItemCofre(
        val arquivo: File,
        val grupo: Grupo,
        val subpasta: String,
        val timestampMillis: Long,
        val numero: Int
    ) {
        val dataHoraFormatada: String
            get() = SimpleDateFormat("dd.MM.yyyy HH:mm'h'", Locale.getDefault()).format(Date(timestampMillis))

        val rotuloExibicao: String
            get() = "$numero. $subpasta - $dataHoraFormatada"
    }

    fun classificarGrupo(nomeGrupoOuEstacao: String): Grupo {
        val n = nomeGrupoOuEstacao.trim()
        return when {
            n.equals("N.A.", ignoreCase = true) || n.equals("NA", ignoreCase = true) -> Grupo.NA
            n.startsWith("ARB", ignoreCase = true) -> Grupo.ARBS
            else -> Grupo.DET01
        }
    }

    private fun pastaCofre(context: Context): File {
        val dir = File(context.filesDir, PASTA)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun encodeSafe(texto: String): String = URLEncoder.encode(texto.trim(), "UTF-8").replace("+", "%20")
    private fun decodeSafe(texto: String): String = try { URLDecoder.decode(texto, "UTF-8") } catch (e: Exception) { texto }

    private fun comprimirBitmap(bitmap: Bitmap): ByteArray {
        val baos = ByteArrayOutputStream()
        val formato = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        bitmap.compress(formato, QUALIDADE, baos)
        return baos.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return buildString(digest.size * 2) { digest.forEach { append("%02x".format(it)) } }
    }

    private fun chaveHash(grupo: Grupo, subpasta: String): String = "ultimo_hash_${grupo.slug}_${encodeSafe(subpasta)}"

    private fun salvarBytes(context: Context, grupo: Grupo, subpasta: String, bytesComprimidos: ByteArray): File {
        val dir = pastaCofre(context)
        val timestamp = System.currentTimeMillis()
        val proximoNumero = contarPorGrupo(context, grupo) + 1
        val nomeArquivo = "${grupo.slug}__${encodeSafe(subpasta)}__${timestamp}__${proximoNumero}$EXTENSAO"
        val arquivo = File(dir, nomeArquivo)

        arquivo.outputStream().use { out ->
            out.write(MAGIC.toByteArray(Charsets.US_ASCII))
            out.write(ofuscar(bytesComprimidos))
        }
        return arquivo
    }

    fun salvarSeNovo(context: Context, bitmapLimpo: Bitmap, grupoOuEstacao: String, nomeSubpasta: String): SaveResult {
        return try {
            val grupo = classificarGrupo(grupoOuEstacao)
            val bytes = comprimirBitmap(bitmapLimpo)
            val hashAtual = sha256(bytes)
            val prefs = context.getSharedPreferences(PREFS_META, Context.MODE_PRIVATE)
            val chave = chaveHash(grupo, nomeSubpasta)
            val hashAnterior = prefs.getString(chave, null)

            if (hashAnterior == hashAtual) {
                SaveResult(file = null, skippedDuplicate = true)
            } else {
                val arquivo = salvarBytes(context, grupo, nomeSubpasta, bytes)
                prefs.edit().putString(chave, hashAtual).apply()
                SaveResult(file = arquivo, skippedDuplicate = false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            SaveResult(file = null, skippedDuplicate = false)
        }
    }

    private fun lerBytesDecodificados(arquivo: File): ByteArray? {
        return try {
            val todosBytes = arquivo.readBytes()
            val magicLen = MAGIC.length
            if (todosBytes.size <= magicLen) return null
            val assinatura = String(todosBytes, 0, magicLen, Charsets.US_ASCII)
            if (assinatura != MAGIC) return null
            val corpo = todosBytes.copyOfRange(magicLen, todosBytes.size)
            ofuscar(corpo)
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    fun carregar(arquivo: File): Bitmap? {
        val bytes = lerBytesDecodificados(arquivo) ?: return null
        return try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (e: Exception) { null }
    }

    private const val PASTA_THUMBS = "cofre_thumbs"

    private fun pastaThumbs(context: Context): File {
        val dir = File(context.filesDir, PASTA_THUMBS)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // ═══ PERFORMANCE: antes, toda vez que a Galeria 2 abria, cada
    // miniatura era gerada do zero — lendo o arquivo .ins inteiro,
    // desofuscando (XOR) o corpo completo e só depois decodificando/
    // reduzindo o bitmap. Isso é caro e se repetia sempre, mesmo pra
    // fotos já vistas antes. Agora existe um cache de miniaturas EM DISCO
    // (JPEG pequeno, qualidade 80): na primeira vez ainda precisa gerar,
    // mas nas próximas aberturas da Galeria 2 a miniatura é lida direto
    // desse arquivo pequeno — muito mais rápido. O cache é invalidado
    // automaticamente se o arquivo original mudar (comparação de
    // lastModified). ═══
    // ═══ PERFORMANCE: pré-aquecimento em lote. Chamado uma vez quando a
    // tela da Galeria 2 abre (ou troca de grupo), gera a miniatura de
    // TODAS as fotos daquele grupo em segundo plano — mesmo as que ainda
    // não apareceram na tela. Assim, quando o usuário rola o scroll pra
    // baixo, a miniatura já está pronta no cache em disco (ou já foi
    // gerada) em vez de precisar decodificar bem na hora, o que é o que
    // causa os engasgos durante o scroll. Idempotente: se a miniatura já
    // está em cache em disco, praticamente não custa nada re-checar. ═══
    fun pregerarMiniaturas(context: Context, itens: List<ItemCofre>, tamanhoMaxPx: Int = 320) {
        for (item in itens) {
            carregarMiniatura(context, item.arquivo, tamanhoMaxPx)
        }
    }

    fun carregarMiniatura(context: Context, arquivo: File, tamanhoMaxPx: Int = 320): Bitmap? {
        val arquivoCache = File(pastaThumbs(context), "${arquivo.name}.thumb.jpg")
        if (arquivoCache.exists() && arquivoCache.lastModified() >= arquivo.lastModified()) {
            val doCache = try { BitmapFactory.decodeFile(arquivoCache.absolutePath) } catch (_: Exception) { null }
            if (doCache != null) return doCache
        }

        val bytes = lerBytesDecodificados(arquivo) ?: return null
        val bmp = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var amostra = 1
            while ((bounds.outWidth / (amostra * 2)) >= tamanhoMaxPx && (bounds.outHeight / (amostra * 2)) >= tamanhoMaxPx) amostra *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = amostra }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (e: Exception) { null }

        if (bmp != null) {
            try {
                arquivoCache.outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 80, out) }
            } catch (_: Exception) { /* cache é só otimização, se falhar não tem problema */ }
        }
        return bmp
    }

    fun exportarComoWebp(arquivo: File, destino: File): File? {
        return try {
            val bytes = lerBytesDecodificados(arquivo) ?: return null
            destino.parentFile?.mkdirs()
            destino.outputStream().use { out -> out.write(bytes) }
            destino
        } catch (e: Exception) { null }
    }

    /**
     * Exporta o conteúdo do arquivo .ins decodificado como JPEG em alta
     * qualidade (100) para uso em compartilhamento externo.
     */
    fun exportarComoJpegHighQuality(arquivo: File, destino: File): File? {
        return try {
            val bytes = lerBytesDecodificados(arquivo) ?: return null
            val bmp = try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (_: Exception) { null }
            if (bmp == null) return null
            destino.parentFile?.mkdirs()
            destino.outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 100, out) }
            destino
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    // ═══ CORREÇÃO: a numeração agora é exclusiva POR ESTRUTURA (subpasta),
    // não mais pelo grupo inteiro. Antes, todas as fotos de um grupo (ex:
    // ARB'S) eram numeradas juntas em sequência — então se ARB-01 e ARB-02
    // tivessem fotos intercaladas, a numeração pulava entre estruturas
    // diferentes. Agora cada estrutura (ex: "ARB-01", "LAGOA BRUTA") tem
    // sua própria contagem: 1, 2, 3... começando do zero para cada uma. ═══
    fun listarPorGrupo(context: Context, grupo: Grupo): List<ItemCofre> {
        val dir = pastaCofre(context)
        val arquivos = dir.listFiles { f -> f.isFile && f.name.startsWith("${grupo.slug}__") && f.name.endsWith(EXTENSAO) } ?: return emptyList()

        val itens = arquivos.mapNotNull { f -> parseNomeArquivo(f, grupo) }
        val numerados = itens.groupBy { it.subpasta }
            .flatMap { (_, itensDaEstrutura) ->
                itensDaEstrutura.sortedBy { it.timestampMillis }
                    .mapIndexed { index, item -> item.copy(numero = index + 1) }
            }
        return numerados.sortedByDescending { it.timestampMillis }
    }

    fun contarPorGrupo(context: Context, grupo: Grupo): Int {
        val dir = pastaCofre(context)
        return dir.listFiles { f -> f.isFile && f.name.startsWith("${grupo.slug}__") && f.name.endsWith(EXTENSAO) }?.size ?: 0
    }

    private fun parseNomeArquivo(f: File, grupo: Grupo): ItemCofre? {
        return try {
            val semExtensao = f.name.removeSuffix(EXTENSAO)
            val partes = semExtensao.split("__")
            if (partes.size < 3) return null
            val rawSubpasta = decodeSafe(partes[1])
            
            // Regra para identificar o que é arquivo antigo
            val isNovoFormato = rawSubpasta.startsWith("HIDR") || rawSubpasta.startsWith("BOMB") || 
                                rawSubpasta.startsWith("FLOW") || rawSubpasta.startsWith("VAZ") || 
                                rawSubpasta.startsWith("ESTR") || rawSubpasta.startsWith("ARB") || 
                                rawSubpasta.startsWith("LAG") || rawSubpasta.startsWith("COOL") || 
                                rawSubpasta.startsWith("DET")
                                
            val subpastaFinal = if (isNovoFormato) rawSubpasta else "ARQUIVO ANTIGO"
            val timestamp = partes[2].toLongOrNull() ?: return null

            ItemCofre(arquivo = f, grupo = grupo, subpasta = subpastaFinal, timestampMillis = timestamp, numero = 0)
        } catch (_: Exception) { null }
    }

    fun excluir(item: ItemCofre): Boolean {
        try { File(item.arquivo.parentFile?.parentFile, "$PASTA_THUMBS/${item.arquivo.name}.thumb.jpg").delete() } catch (_: Exception) {}
        return try { item.arquivo.delete() } catch (e: Exception) { false }
    }
}
