package com.red.sovereign.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.VideoCapturer

/**
 * مصنع الكاميرا الموحّد — نقطة الدخول الوحيدة لفتح الكاميرا في **كل** أنواع
 * المكالمات (1:1 عبر WebRtcEngine، الشبكية عبر MeshRtcSession، مؤتمرات SFU،
 * البث المباشر، ومكالمات LAN P2P).
 *
 * ## لماذا هذا الملف؟
 * كانت كل وحدة تنسخ نفس كود `Camera2Enumerator(context)` بثلاثة اختلافات
 * قاتلة:
 *  1. `LanRtcSession` لم يتحقق من إذن `CAMERA` إطلاقاً — أول نداء لـ
 *     `getCameraIdList()` يرمي `SecurityException` فتتحول المكالمة لصوتية
 *     بلا أي رسالة للمستخدم.
 *  2. لا يوجد fallback إلى `Camera1Enumerator` عند رفض بعض الأجهزة
 *     (LEVEL_LEGACY / أجهزة MediaTek القديمة) فتفشل الكاميرا على تلك الأجهزة
 *     رغم existence الكاميرا.
 *  3. لا يوجد `CameraEventsHandler`: أخطاء like "Camera is in use by
 *     another app" أو `onCameraDisconnected` كانت تُبتلع صمتاً وتبقى الشاشة
 *     سوداء بلا سبب ظاهر.
 *
 * ## القاعدة الجديدة
 * - تحقق صريح من الإذن قبل أي استدعاء لـ CameraManager.
 * - Camera2 أولاً (أداء/تحكم أفضل)، ثم Camera1 كطوق نجاة حقيقي.
 * - اختيار الكاميرا الأمامية إن وُجدت، وإلا أي كاميرا متاحة.
 * - تمرير `CameraEventsHandler` دائماً لتسجيل أول إطار/انقطاع/تجميد.
 * - إرجاع [Result] بسبب واضح بدل `null` صامت — تستخدمه الواجهات لعرض
 *   «إعادة محاولة الكاميرا» بدل شاشة سوداء غامضة.
 */
object CameraCaptureFactory {
    private const val TAG = "YounesCamera"

    /** نتيجة محاولة فتح الكاميرا. [reason] فارغ عند النجاح، وإلا سبب مقروء. */
    data class Result(
        val capturer: VideoCapturer?,
        val reason: String = "",
        val usedCamera1Fallback: Boolean = false
    ) {
        val isSuccess: Boolean get() = capturer != null
    }

    fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /**
     * ينشئ capturer جديداً. **يجب** استدعاؤه بعد منح الإذن (ويفضّل من foreground).
     * لا يبدأ الالتقاط هنا — المتصل هو من يملك `SurfaceTextureHelper` و
     * `VideoSource` ويستدعي `initialize` + `startCapture`.
     */
    fun create(
        context: Context,
        preferFront: Boolean = true,
        tag: String = "call",
        listener: Listener? = null
    ): Result {
        if (!hasCameraPermission(context)) {
            val reason = "CAMERA_PERMISSION_DENIED"
            Log.w(TAG, "[$tag] $reason")
            listener?.onFailure(reason)
            return Result(null, reason)
        }
        if (!anyCameraAvailable(context)) {
            val reason = "NO_CAMERA_HARDWARE"
            Log.w(TAG, "[$tag] $reason")
            listener?.onFailure(reason)
            return Result(null, reason)
        }

        val handler = eventsHandler(tag, listener)
        // لا نعتمد على Camera2Enumerator.isSupported (يختلف توفره بين إصدارات
        // libwebrtc): نحاول الإنشاء مباشرة ونلتقط أي فشل للانتقال إلى Camera1.
        val camera2 = runCatching {
            chooseCapturer(Camera2Enumerator(context), preferFront, handler)
        }.getOrElse { e ->
            Log.w(TAG, "[$tag] Camera2 enumeration failed: ${e.message}")
            null
        }
        if (camera2 != null) {
            Log.i(TAG, "[$tag] Camera2 capturer ready (front=$preferFront)")
            return Result(camera2)
        }

        // طوق النجاة الحقيقي — كان غائباً في WebRtcEngine/MeshRtcSession/LanRtcSession.
        val camera1 = runCatching {
            chooseCapturer(Camera1Enumerator(true /* captureToTexture */), preferFront, handler)
        }.getOrElse { e ->
            Log.w(TAG, "[$tag] Camera1 enumeration failed: ${e.message}")
            null
        }
        if (camera1 != null) {
            Log.i(TAG, "[$tag] Camera1 fallback capturer ready (Camera2 unavailable)")
            return Result(camera1, usedCamera1Fallback = true)
        }

        val reason = "CAMERA_OPEN_FAILED"
        Log.e(TAG, "[$tag] $reason — no capturer could be created")
        listener?.onFailure(reason)
        return Result(null, reason)
    }

    private fun anyCameraAvailable(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
        manager?.cameraIdList?.isNotEmpty() == true
    }.getOrDefault(true) // true = let the enumerators decide (older APIs)

    private fun chooseCapturer(
        enumerator: CameraEnumerator,
        preferFront: Boolean,
        handler: CameraVideoCapturer.CameraEventsHandler
    ): VideoCapturer? {
        val names = runCatching { enumerator.deviceNames }.getOrElse {
            Log.w(TAG, "deviceNames failed: ${it.message}")
            return null
        }
        if (names.isEmpty()) return null

        val ordered = if (preferFront) {
            val fronts = names.filter { runCatching { enumerator.isFrontFacing(it) }.getOrDefault(false) }
            val backs = names.filterNot { it in fronts }
            fronts + backs
        } else names.toList()

        for (name in ordered) {
            val c = runCatching {
                // Camera2Enumerator.createCapturer يحتاج CameraEventsHandler غير null
                // لتسجيل أخطاء الفتح/الانقطاع؛ Camera1Enumerator يتجاهله لكنه آمن.
                enumerator.createCapturer(name, handler)
            }.getOrElse { e ->
                Log.w(TAG, "createCapturer($name) failed: ${e.message}")
                null
            }
            if (c != null) return c
        }
        return null
    }

    /** تبديل الكاميرا (أمامية ↔ خلفية) مع نتيجة بدل الاستدعاء الصامت. */
    fun switchCamera(capturer: VideoCapturer?, tag: String = "call", listener: Listener? = null) {
        val camera = capturer as? CameraVideoCapturer
        if (camera == null) {
            Log.w(TAG, "[$tag] switchCamera: capturer is not a CameraVideoCapturer")
            listener?.onFailure("CAMERA_SWITCH_UNSUPPORTED")
            return
        }
        runCatching {
            camera.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                    Log.i(TAG, "[$tag] camera switched, front=$isFrontCamera")
                    listener?.onSwitched(isFrontCamera)
                }
                override fun onCameraSwitchError(errorDescription: String?) {
                    Log.w(TAG, "[$tag] camera switch failed: $errorDescription")
                    listener?.onFailure(errorDescription ?: "CAMERA_SWITCH_FAILED")
                }
            })
        }.onFailure {
            Log.w(TAG, "[$tag] switchCamera threw: ${it.message}")
            listener?.onFailure("CAMERA_SWITCH_FAILED")
        }
    }

    /** مستمع خفيف — لا جديد في الواجهات، مجرد callbacks اختيارية للتشخيص/الـ UI. */
    interface Listener {
        fun onFirstFrame() {}
        fun onFailure(reason: String) {}
        fun onSwitched(isFrontCamera: Boolean) {}
        fun onDisconnected() {}
    }

    fun eventsHandler(tag: String = "call", listener: Listener? = null): CameraVideoCapturer.CameraEventsHandler =
        object : CameraVideoCapturer.CameraEventsHandler {
            override fun onCameraError(errorDescription: String?) {
                Log.e(TAG, "[$tag] onCameraError: $errorDescription")
                listener?.onFailure(errorDescription ?: "CAMERA_ERROR")
            }
            override fun onCameraDisconnected() {
                Log.w(TAG, "[$tag] onCameraDisconnected")
                listener?.onDisconnected()
                listener?.onFailure("CAMERA_DISCONNECTED")
            }
            override fun onCameraFreezed(errorDescription: String?) {
                Log.w(TAG, "[$tag] onCameraFreezed: $errorDescription")
            }
            override fun onCameraOpening(cameraName: String?) {
                Log.i(TAG, "[$tag] onCameraOpening: $cameraName")
            }
            override fun onFirstFrameAvailable() {
                Log.i(TAG, "[$tag] first camera frame available ✅")
                listener?.onFirstFrame()
            }
            override fun onCameraClosed() {
                Log.i(TAG, "[$tag] onCameraClosed")
            }
        }
}
