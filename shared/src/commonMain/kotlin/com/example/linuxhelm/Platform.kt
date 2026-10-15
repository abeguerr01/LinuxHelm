package com.example.linuxhelm

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform