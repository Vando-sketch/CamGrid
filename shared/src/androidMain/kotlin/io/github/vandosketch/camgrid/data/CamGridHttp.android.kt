package io.github.vandosketch.camgrid.data

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android

internal actual fun platformHttpEngine(): HttpClientEngine = Android.create()
