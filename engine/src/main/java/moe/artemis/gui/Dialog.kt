package moe.artemis.gui

import android.app.Activity
import android.util.Log
import org.tvp.kirikiri2.KrDialogStyle
import java.util.concurrent.ConcurrentHashMap

class Dialog(
    private val activity: Activity,
    title: String?,
    message: String?,
    private val cancelable: Boolean,
    private val textField: Boolean,
    private val context: Long,
) {
    private val title: String = title ?: ""
    private val message: String = message ?: ""

    init {
        activity.runOnUiThread { showInternal() }
    }

    private external fun OnClose(result: Int, text: String?, context: Long)
    companion object {
        private const val TAG = "ArtemisDialog"
        private val INSTANCES = ConcurrentHashMap<Int, Dialog>()
        private var seed = 0

        @JvmStatic
        fun Release(id: Int) {
            synchronized(INSTANCES) { INSTANCES.remove(id) }
        }

        @JvmStatic
        fun Show(activity: Activity?, title: String?, message: String?, cancelable: Boolean, textField: Boolean, context: Long): Int {
            if (activity == null) return 0
            val id: Int
            synchronized(INSTANCES) {
                id = ++seed
                INSTANCES[id] = Dialog(activity, title, message, cancelable, textField, context)
            }
            return id
        }
    }

    private fun showInternal() {
        try {
            if (activity.isFinishing) {
                close(0, "")
                return
            }
            val buttons = if (cancelable) arrayOf("OK", "Cancel") else arrayOf("OK")
            val initialText = if (textField) message else null
            val dialogMessage = if (textField) null else message
            KrDialogStyle.showInputBox(activity, title, dialogMessage, initialText, buttons, cancelable) { which, text ->
                close(if (which == 0) 1 else 0, text)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "show Artemis dialog failed", t)
            close(0, "")
        }
    }

    private fun close(result: Int, text: String?) {
        try {
            OnClose(result, text, context)
        } catch (t: Throwable) {
            Log.e(TAG, "notify Artemis dialog close failed", t)
        }
    }
}
