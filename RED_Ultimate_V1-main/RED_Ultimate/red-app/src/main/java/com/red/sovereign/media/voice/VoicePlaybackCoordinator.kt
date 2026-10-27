package com.red.sovereign.media.voice

/**
 * P1 2026-09-27: تشغيل حصري للصوت (نمط واتساب) — كان تشغيل رسالة لا يوقف
 * الأخرى فتتداخل الأصوات ويبدو "التشغيل غير صحيح". أي مشغل يسجّل نفسه
 * عند الضغط فيُوقَف السابق فوراً. إيقاف المستخدم idempotent فلا ضرر.
 */
object VoicePlaybackCoordinator {
    private var currentId: Any? = null
    private var currentStop: (() -> Unit)? = null

    @Synchronized
    fun register(id: Any, stop: () -> Unit) {
        if (currentId != id) runCatching { currentStop?.invoke() }
        currentId = id
        currentStop = stop
    }

    @Synchronized
    fun unregister(id: Any) {
        if (currentId == id) {
            currentId = null
            currentStop = null
        }
    }
}
