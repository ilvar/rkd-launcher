package pw.rkd.launcher

/** Whether a HOME intent arrived while this launcher was already on screen. */
internal class HomePressState {
    private var leftLauncher = true

    fun onResume() {
        leftLauncher = false
    }

    fun onStop() {
        leftLauncher = true
    }

    fun onHomeIntent(): Boolean {
        val alreadyOnHome = !leftLauncher
        leftLauncher = false
        return alreadyOnHome
    }
}
