package com.raylson.jansen.inspetor.domain

data class PontoGeotecnico(
    val id: String = "",
    val nomeProjeto: String = "",
    val dataHora: Long = 0L,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val zonaUtm: String = "UTM-23S",
    val fotos: List<ByteArray> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PontoGeotecnico) return false
        return id == other.id &&
            nomeProjeto == other.nomeProjeto &&
            dataHora == other.dataHora &&
            latitude == other.latitude &&
            longitude == other.longitude &&
            zonaUtm == other.zonaUtm &&
            fotos.size == other.fotos.size &&
            fotos.zip(other.fotos).all { (a, b) -> a.contentEquals(b) }
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + nomeProjeto.hashCode()
        result = 31 * result + dataHora.hashCode()
        result = 31 * result + latitude.hashCode()
        result = 31 * result + longitude.hashCode()
        result = 31 * result + zonaUtm.hashCode()
        return result
    }
}
