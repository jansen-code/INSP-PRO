package com.raylson.jansen.inspetor.domain

data class PontoGeotecnico(
    val id: String = "",
    val nomeProjeto: String = "",
    val dataHora: Long = 0L,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val precisaoMetros: Double = 0.0, // Adicionado para exibir no mapa igual ao SW Maps
    val zonaUtm: String = "UTM-23S",
    val caminhosFotos: List<String> = emptyList() // Mudado de ByteArray para String (Caminho local da foto)
)
