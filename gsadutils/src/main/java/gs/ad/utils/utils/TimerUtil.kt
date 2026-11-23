package gs.ad.utils.utils

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TimerUtil(
    private val delayMillis: Long = 0,
    private val repeatMillis: Long = 0,
    private val doSomethingBackground : (() -> Unit),
    private val doSomethingMainThread : (() -> Unit)
) {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    private fun startCoroutineTimer(delayMillis: Long = 0, repeatMillis: Long = 0, action: () -> Unit) = scope.launch(Dispatchers.IO) {
        delay(delayMillis)
        if (repeatMillis > 0) {
            while (true) {
                action()
                delay(repeatMillis)
            }
        } else {
            action()
        }
    }

    private val timer: Job = startCoroutineTimer(delayMillis = delayMillis, repeatMillis = repeatMillis) {
        Log.d(TAG, "Background - tick")
        doSomethingBackground()
        scope.launch(Dispatchers.Main) {
            Log.d(TAG, "Main thread - tick")
            doSomethingMainThread()
        }
    }

    fun startTimer() {
        timer.start()
    }

    fun cancelTimer() {
        timer.cancel()
    }

    companion object{
        const val TAG = "Timer"
    }
}