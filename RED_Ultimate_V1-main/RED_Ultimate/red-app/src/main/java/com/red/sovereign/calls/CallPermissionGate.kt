package com.red.sovereign.calls

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.red.sovereign.core.YounesId

/**
 * بوابة أذونات المكالمات — تُستخدم في **كل** نقطة تبدأ مكالمة من الواجهة.
 *
 * ## الخلل الذي تعالجه
 * `ModernRedDashboard` كان يستدعي `YounesCallService.start(context, id, video)`
 * مباشرة من 6 مواضع (جهات الاتصال، سجل المكالمات، حوار الاتصال الجديد،
 * إعادة الاتصال…) بلا أي تحقق من `CAMERA`. إن لم يكن الإذن ممنوحاً يصل
 * `WebRtcEngine.createVideoTrack()` ويرجع `null` — فتُفتح شاشة «مكالمة فيديو»
 * بشاشة سوداء بلا رسالة. هذا هو السبب الظاهر «الكاميرا لا تعمل في كل أنواع
 * المكالمات».
 *
 * ## السلوك الجديد
 * - يتحقق من `RECORD_AUDIO` و (للفيديو فقط) `CAMERA` قبل بدء المكالمة.
 * - يطلب الأذونات الناقصة في اللحظة المناسبة (من الـ Activity، لا من الخدمة).
 * - إن رُفضت الكاميرا صراحةً تبدأ المكالمة **صوتية** مع إشعار واضح بدل الفشل
 *   الصامت — نفس سلوك واتساب/تيليجرام.
 * - يطبّع المعرّف عبر [YounesId] قبل الإرسال.
 */
@Composable
fun rememberCallStarter(): (String, Boolean) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val request = pending ?: return@rememberLauncherForActivityResult
        pending = null
        val (target, video) = request
        val audioOk = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val cameraOk = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        when {
            audioOk && (!video || cameraOk) -> YounesCallService.start(context, target, video)
            audioOk && video -> {
                // رُفضت الكاميرا — لا نترك المستخدم بلا مكالمة.
                CallRuntime.cameraNotice = true
                YounesCallService.start(context, target, false)
            }
            else -> CallRuntime.cameraNotice = video
        }
    }

    val startCall: (String, Boolean) -> Unit = remember(context, launcher) {
        { rawTarget, video ->
            val target = YounesId.normalizeInput(rawTarget).ifBlank { rawTarget.trim() }
            if (target.isBlank()) {
                // لا مكالمة بلا هدف — كان يصل معرف فارغ للخدمة أحياناً.
            } else {
                val audioOk = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                val cameraOk = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (audioOk && (!video || cameraOk)) {
                    YounesCallService.start(context, target, video)
                } else {
                    pending = target to video
                    val permissions = buildList {
                        if (!audioOk) add(Manifest.permission.RECORD_AUDIO)
                        if (video && !cameraOk) add(Manifest.permission.CAMERA)
                    }
                    launcher.launch(permissions.toTypedArray())
                }
            }
        }
    }
    return startCall
}
