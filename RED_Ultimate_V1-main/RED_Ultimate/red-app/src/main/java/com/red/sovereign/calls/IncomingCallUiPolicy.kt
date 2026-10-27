package com.red.sovereign.calls

/**
 * يختار مصدر واجهة الرنين الوحيد:
 * واجهة RED الموحدة عند ظهور التطبيق، أو Activity مخصصة فقط عند الخلفية/القفل.
 */
object IncomingCallUiPolicy {
    fun shouldLaunchIncomingActivity(hasResumedRedActivity: Boolean): Boolean = !hasResumedRedActivity

    /**
     * هل يجب إغلاق شاشة الوارد الكاملة الآن؟
     *
     * السبب: كانت الشاشة تعتمد على مؤقتها الخاص (٤٥ ثانية) فقط. فإذا ألغى المتصل،
     * أو انتهت المكالمة من أي مسار آخر، بقيت الشاشة ترنّ لمكالمة ميتة، والضغط على
     * «قبول» لا يفعل شيئاً لأن المالك (CallRuntime) لم يعد في حالة Incoming —
     * وهذا ما يبدو للمستخدم «التطبيق لا يستقبل المكالمة».
     *
     * القاعدة محافظة عمداً: لا تُغلق على [CallUiState.Idle] لأن مسار إيقاظ الدفع
     * يعرض الشاشة قبل وصول العرض، ولا على [CallUiState.Connecting] لأن مسار الصادر
     * الاحتياطي يُعرض بها. تُغلق فقط عندما يصبح حال المالك نهائياً أو مملوكاً لمكالمة أخرى.
     */
    fun shouldDismissRingScreen(state: CallUiState, openedForCallId: String): Boolean = when (state) {
        is CallUiState.Active,
        is CallUiState.ActiveWithIncoming,
        is CallUiState.CallEnded,
        is CallUiState.Error,
        is CallUiState.Busy,
        is CallUiState.Declined,
        is CallUiState.NoAnswer -> true
        is CallUiState.Incoming -> openedForCallId.isNotBlank() && state.callId != openedForCallId
        is CallUiState.Idle,
        is CallUiState.Connecting,
        is CallUiState.Reconnecting -> false
    }
}
