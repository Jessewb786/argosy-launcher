package com.nendo.argosy.ui.screens.common

import android.content.Context
import android.content.Intent
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GameStartRouter"

/**
 * Process-lifetime entry point that resolves a game's placement and starts it. A start that
 * fails cancels the play session its launch opened.
 */
@Singleton
class GameStartRouter internal constructor(
    context: Context,
    private val launchTargetResolver: EmulatorLaunchTargetResolver,
    private val playSessionTracker: PlaySessionTracker,
    private val scope: CoroutineScope
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        launchTargetResolver: EmulatorLaunchTargetResolver,
        playSessionTracker: PlaySessionTracker
    ) : this(
        context,
        launchTargetResolver,
        playSessionTracker,
        SafeCoroutineScope(Dispatchers.Main.immediate, "GameStart")
    )

    private val fallbackStarter = GameActivityStarter(context) { null }

    fun start(
        gameId: Long,
        intent: Intent,
        overrideDisplayId: Int? = null,
        onFailure: ((Throwable) -> Unit)? = null
    ) {
        scope.launch {
            val result = try {
                val options = launchTargetResolver.launchOptionsFor(
                    gameId,
                    overrideDisplayId,
                    intent.component?.packageName ?: intent.`package`
                )
                startResolved(GameLaunchRequest(intent, options))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.error(TAG, "Placement for gameId=$gameId could not be resolved", e)
                Result.failure(e)
            }
            result.exceptionOrNull()?.let { error ->
                cancelSessionOf(gameId)
                onFailure?.invoke(error)
            }
        }
    }

    private fun cancelSessionOf(gameId: Long) {
        if (playSessionTracker.activeSession.value?.gameId != gameId) return
        Logger.info(TAG, "Start failed for gameId=$gameId, cancelling its session")
        playSessionTracker.cancelSession()
    }

    private fun startResolved(request: GameLaunchRequest): Result<Unit> {
        val manager = DualScreenManagerHolder.instance ?: return fallbackStarter.start(request)
        return manager.startGame(request)
    }
}
