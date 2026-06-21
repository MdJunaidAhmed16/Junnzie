package com.junnz.wear.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class CaptureResult(
    val sessionId: String,
    val transcript: String,
    val intent: String,
    val outcome: String,
)

@Singleton
class CaptureResultBus @Inject constructor() {
    private val _results = MutableSharedFlow<CaptureResult>(replay = 1)
    val results: SharedFlow<CaptureResult> = _results.asSharedFlow()

    suspend fun post(result: CaptureResult) = _results.emit(result)
}
