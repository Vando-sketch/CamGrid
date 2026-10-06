package io.github.vandosketch.camgrid.data

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.java.Java

internal actual fun platformHttpEngine(): HttpClientEngine = Java.create()
