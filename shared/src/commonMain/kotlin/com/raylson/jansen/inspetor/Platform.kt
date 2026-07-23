package com.raylson.jansen.inspetor

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform