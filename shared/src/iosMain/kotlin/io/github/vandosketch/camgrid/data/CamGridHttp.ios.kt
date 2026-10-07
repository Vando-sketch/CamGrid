package io.github.vandosketch.camgrid.data

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

internal actual fun platformHttpEngine(): HttpClientEngine = Darwin.create()
