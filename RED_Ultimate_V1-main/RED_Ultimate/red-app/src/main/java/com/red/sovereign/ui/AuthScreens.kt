package com.red.sovereign.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.R
import com.red.sovereign.auth.AuthState
import com.red.sovereign.auth.AuthViewModel
import com.red.sovereign.auth.ServerState
import com.red.sovereign.ui.theme.AppThemeState
import com.red.sovereign.ui.theme.PlexArabicFamily
import com.red.sovereign.ui.theme.YounesIcons
import com.red.sovereign.ui.theme.YounesMotion
import com.red.sovereign.ui.theme.YounesOnBrand
import com.red.sovereign.ui.theme.YounesPrimary
import com.red.sovereign.ui.theme.YounesAccent
import com.red.sovereign.ui.theme.YounesRadius
import com.red.sovereign.ui.theme.YounesSpace
import com.red.sovereign.ui.theme.YounesSuccess

/**
 * ═══════════════════════════════════════════════════════════════════════
 * شاشات الدخول والتسجيل — Younes Sovereign 2026
 * ═══════════════════════════════════════════════════════════════════════
 *
 * ## ما الذي تغير (P0 2026-10-28)
 * 1. **إزالة ألوان المنافسين الصلبة**: كانت الشاشة تعرّف `TelegramDarkBg`،
 *    `CleanAccentGreen = #00A884` (واتساب) و`CleanAccentBlue = #2AABEE`
 *    (تيليجرام) محلياً — أي أن أول شاشة في التطبيق كانت تبدو نسخة من
 *    تيليجرام بينما بقيّة التطبيق يستخدم «ذهب الليل» السيادي. الآن كل لون
 *    من `MaterialTheme.colorScheme` / لوحة Younes.
 * 2. **الخلفية**: شفافة فوق `SovereignBackground` (الشفق السيادي المتحرك)
 *    بدل طبقة `#0E1621` مسطّحة تغطي كل شيء — تحترم الوضع الفاتح/الداكن
 *    والثيمات المخصصة تلقائياً.
 * 3. **الاتصال بالخادم**: يقبل `192.168.1.50` و`host:port` والروابط الكاملة،
 *    ويعرض زر «تشخيص الاتصال» بتقرير طبقي قابل للتنفيذ بدل رسالة غامضة.
 * 4. **الأيقونات/المسافات/الزوايا** من السجل الموحّد (YounesIcons/Space/Radius).
 * 5. **الحركة** هادئة وتحترم reduceMotion؛ نبضة واحدة فقط للعلامة.
 * 6. **إمكانية الوصول**: أهداف لمس ≥48dp، وصف محتوى، تباين AA على الأقل.
 */

// ─── الألوان الدلالية المشتقة من الثيم (لا قيم محلية صلبة) ───────────────
@Composable
private fun authSurface(): Color =
    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)

@Composable
private fun authField(): Color =
    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f)

@Composable
private fun authAccent(): Color = MaterialTheme.colorScheme.primary

@Composable
fun AuthFlow(viewModel: AuthViewModel) {
    when (val state = viewModel.state) {
        AuthState.Loading, AuthState.Submitting -> LoadingScreen()
        AuthState.Welcome -> WelcomeScreen(
            server = viewModel.serverState,
            discover = viewModel::discoverServer,
            register = viewModel::showRegister,
            login = viewModel::showLogin,
            setServerUrl = viewModel::setServerUrl,
            report = viewModel.connectionReport,
            diagnosticsRunning = viewModel.diagnosticsRunning,
            runDiagnostics = viewModel::runDiagnostics
        )
        AuthState.Register -> RegisterTabScreen(viewModel::register, viewModel::showLogin, viewModel::showWelcome)
        AuthState.Login -> LoginTabScreen(viewModel::login, viewModel::showRegister, viewModel::showRecovery, viewModel::showWelcome)
        AuthState.Recovery -> RecoveryScreen(viewModel::recover, viewModel::showLogin)
        AuthState.RecoveryComplete -> StatusScreen(
            "تم تغيير كلمة المرور 🔐",
            "أُلغيت كل الجلسات القديمة بنجاح. يمكنك الآن تسجيل الدخول بكلمة المرور الجديدة.",
            viewModel::showLogin
        )
        is AuthState.Pending -> PendingScreen(state, viewModel::checkApproval, viewModel::showLogin)
        is AuthState.Rejected -> StatusScreen("تم رفض طلب الحساب ❌", state.reason ?: "يرجى مراجعة مسؤول منظومة يونس المحلية.", viewModel::showLogin)
        AuthState.Suspended -> StatusScreen("الحساب موقوف مؤقتاً ⚠️", "تواصل مع المسؤول المحلي لتفعيل الجلسة.", viewModel::showLogin)
        AuthState.Banned -> StatusScreen("الحساب محظور 🚫", "تم إلغاء صلاحية هذا الحساب والأجهزة المرتبطة به.", viewModel::showLogin)
        is AuthState.Error -> ErrorStatusScreen("تعذر إكمال العملية", state.message, viewModel::showWelcome)
        is AuthState.Authenticated -> Unit
    }
}

@Composable
private fun LoadingScreen() = Centered {
    CircularProgressIndicator(color = authAccent(), modifier = Modifier.size(42.dp))
    Spacer(Modifier.height(YounesSpace.m))
    Text(
        "جارٍ الاتصال بالخادم السيادي…",
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = PlexArabicFamily
    )
    Spacer(Modifier.height(YounesSpace.xxs))
    Text(
        "ثوانٍ معدودة — لن يبقى التطبيق معلّقاً إذا كان الخادم بعيدًا",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        fontFamily = PlexArabicFamily
    )
}

@Composable
private fun FeatureChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YounesSpace.xxs),
        modifier = Modifier
            .clip(RoundedCornerShape(YounesRadius.pill))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(YounesRadius.pill))
            .padding(horizontal = YounesSpace.s, vertical = YounesSpace.xxs)
    ) {
        Icon(icon, null, tint = authAccent(), modifier = Modifier.size(16.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontFamily = PlexArabicFamily)
    }
}

@Composable
private fun WelcomeScreen(
    server: ServerState,
    discover: () -> Unit,
    register: () -> Unit,
    login: () -> Unit,
    setServerUrl: (String) -> String?,
    report: com.red.sovereign.core.ConnectionReport?,
    diagnosticsRunning: Boolean,
    runDiagnostics: (String) -> Unit
) = Centered(scrollable = true) {
    var showManualInput by remember { mutableStateOf(false) }
    var serverInput by remember { mutableStateOf("") }
    var serverInputError by remember { mutableStateOf<String?>(null) }
    val accent = authAccent()

    BrandMarkAnimated(112)
    Spacer(Modifier.height(YounesSpace.m))
    Text(
        "يونس",
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold,
        fontFamily = PlexArabicFamily
    )
    Text(
        "منظومة اتصالات سيادية مشفّرة • بلا رقم هاتف",
        color = accent,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        fontFamily = PlexArabicFamily
    )

    Spacer(Modifier.height(YounesSpace.m))
    Row(horizontalArrangement = Arrangement.spacedBy(YounesSpace.xs)) {
        FeatureChip("تشفير طرف لطرف", YounesIcons.Shield)
        FeatureChip("خادمك على شبكتك", YounesIcons.Server)
    }

    Spacer(Modifier.height(YounesSpace.l))

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = authSurface()),
        shape = RoundedCornerShape(YounesRadius.card)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(YounesSpace.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(YounesSpace.xs)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(YounesSpace.xs)) {
                Icon(YounesIcons.Server, null, tint = accent, modifier = Modifier.size(YounesIconSizeMedium))
                Text("حالة الخادم", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontFamily = PlexArabicFamily)
            }
            when (server) {
                ServerState.Discovering -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(YounesSpace.xs)) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = accent, strokeWidth = 2.dp)
                        Text("جارٍ التحقق الذكي من شبكة يونس…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = PlexArabicFamily)
                    }
                }
                is ServerState.Ready -> StatusPill(server.url, positive = true)
                is ServerState.Error -> {
                    StatusPill(server.message, positive = false)
                    Text("العنوان الحالي: ${server.fallbackUrl}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontFamily = PlexArabicFamily)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(YounesSpace.xs)) {
                TextButton(onClick = discover, enabled = server !is ServerState.Discovering) {
                    Icon(YounesIcons.Wifi, null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(YounesSpace.xxs))
                    Text("إعادة الاكتشاف", color = accent, fontSize = 12.sp, fontFamily = PlexArabicFamily)
                }
                TextButton(onClick = { runDiagnostics(serverInput.ifBlank { currentServerUrl(server) }) }, enabled = !diagnosticsRunning) {
                    Icon(YounesIcons.NetworkCheck, null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(YounesSpace.xxs))
                    Text(if (diagnosticsRunning) "جارٍ التشخيص…" else "تشخيص الاتصال", color = accent, fontSize = 12.sp, fontFamily = PlexArabicFamily)
                }
            }

            // تقرير التشخيص الطبقي — يحل محل رسالة «تعذر الاتصال» الغامضة.
            report?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = authField()),
                    shape = RoundedCornerShape(YounesRadius.field)
                ) {
                    Column(Modifier.padding(YounesSpace.s), verticalArrangement = Arrangement.spacedBy(YounesSpace.xxs)) {
                        result.steps.forEach { step ->
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(YounesSpace.xs)) {
                                Icon(
                                    if (step.ok) YounesIcons.Success else YounesIcons.Error,
                                    null,
                                    tint = if (step.ok) YounesSuccess else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(YounesIconSizeSmall)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(step.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = PlexArabicFamily)
                                    Text(step.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontFamily = PlexArabicFamily)
                                    if (step.hint.isNotBlank()) {
                                        Text(step.hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontFamily = PlexArabicFamily)
                                    }
                                }
                            }
                        }
                        Text(result.summary, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlexArabicFamily)
                    }
                }
            }

            if (!showManualInput) {
                TextButton(onClick = { showManualInput = true; serverInputError = null }) {
                    Text("تحديد عنوان الخادم يدويًا", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = PlexArabicFamily)
                }
            } else {
                OutlinedTextField(
                    value = serverInput,
                    onValueChange = { serverInput = it; serverInputError = null },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("192.168.1.50:8088 أو http://red.local:8088", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) },
                    singleLine = true,
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = PlexArabicFamily),
                    colors = fieldColors()
                )
                serverInputError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, textAlign = TextAlign.Center, fontFamily = PlexArabicFamily) }
                Row(horizontalArrangement = Arrangement.spacedBy(YounesSpace.xs)) {
                    Button(
                        onClick = {
                            val error = setServerUrl(serverInput)
                            serverInputError = error
                            if (error == null) {
                                showManualInput = false
                                runDiagnostics(serverInput)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = YounesOnBrand)
                    ) { Text("حفظ", fontSize = 12.sp, fontFamily = PlexArabicFamily) }
                    OutlinedButton(onClick = { showManualInput = false; serverInputError = null }) {
                        Text("إلغاء", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = PlexArabicFamily)
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(YounesSpace.l))
    Button(
        onClick = register,
        modifier = Modifier.fillMaxWidth().height(YounesSpace.minTouch),
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = YounesOnBrand),
        shape = RoundedCornerShape(YounesRadius.pill)
    ) { Text("إنشاء حساب سيادي", fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = PlexArabicFamily) }
    Spacer(Modifier.height(YounesSpace.xs))
    OutlinedButton(
        onClick = login,
        modifier = Modifier.fillMaxWidth().height(YounesSpace.minTouch),
        shape = RoundedCornerShape(YounesRadius.pill)
    ) { Text("لدي حساب بالفعل", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontFamily = PlexArabicFamily) }
    Spacer(Modifier.height(YounesSpace.s))
    Text(
        "مفاتيحك تُولَّد داخل الجهاز ولا تغادره أبداً",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        fontFamily = PlexArabicFamily
    )
}

private val YounesIconSizeSmall = 16.dp
private val YounesIconSizeMedium = 20.dp

private fun currentServerUrl(server: ServerState): String = when (server) {
    is ServerState.Ready -> server.url
    is ServerState.Error -> server.fallbackUrl
    ServerState.Discovering -> ""
}

@Composable
private fun StatusPill(text: String, positive: Boolean) {
    val color = if (positive) YounesSuccess else MaterialTheme.colorScheme.error
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YounesSpace.xxs),
        modifier = Modifier
            .clip(RoundedCornerShape(YounesRadius.pill))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(YounesRadius.pill))
            .padding(horizontal = YounesSpace.s, vertical = YounesSpace.xxs)
    ) {
        Icon(if (positive) YounesIcons.Success else YounesIcons.Warning, null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontFamily = PlexArabicFamily)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = authField(),
    unfocusedContainerColor = authField(),
    focusedBorderColor = authAccent(),
    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = authAccent()
)

@Composable
private fun RegisterTabScreen(
    submit: (String, String, String) -> Unit,
    goLogin: () -> Unit,
    goWelcome: () -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    FormColumn(title = "إنشاء حساب") {
        Field(value = displayName, change = { displayName = it; error = null }, label = "الاسم الظاهر", leading = YounesIcons.Person, keyboard = KeyboardOptions(imeAction = ImeAction.Next))
        Field(value = username, change = { username = it; error = null }, label = "اسم المستخدم (إنكليزي)", leading = YounesIcons.PersonAdd, keyboard = KeyboardOptions(imeAction = ImeAction.Next))
        PasswordField(value = password, change = { password = it; error = null }, label = "كلمة المرور (12 حرفاً على الأقل)")
        PasswordStrengthBar(password, username)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, textAlign = TextAlign.Center, fontFamily = PlexArabicFamily) }
        PrimaryButton("إنشاء الحساب المشفّر") {
            error = when {
                displayName.isBlank() -> "أدخل الاسم الظاهر"
                username.length < 3 -> "اسم المستخدم 3 أحرف على الأقل"
                password.length < 12 -> "كلمة المرور 12 حرفاً على الأقل"
                else -> null
            }
            if (error == null) submit(displayName, username, password)
        }
        TextButtonLink("لديك حساب؟ سجّل الدخول", goLogin)
        TextButtonLink("العودة", goWelcome)
    }
}

@Composable
private fun LoginTabScreen(
    submit: (String, String) -> Unit,
    goRegister: () -> Unit,
    goRecovery: () -> Unit,
    goWelcome: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    FormColumn(title = "تسجيل الدخول") {
        Field(value = username, change = { username = it; error = null }, label = "اسم المستخدم أو معرّف يونس", leading = YounesIcons.Person, keyboard = KeyboardOptions(imeAction = ImeAction.Next))
        PasswordField(value = password, change = { password = it; error = null }, label = "كلمة المرور")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, textAlign = TextAlign.Center, fontFamily = PlexArabicFamily) }
        PrimaryButton("دخول آمن") {
            error = if (username.isBlank() || password.isBlank()) "أدخل اسم المستخدم وكلمة المرور" else null
            if (error == null) submit(username, password)
        }
        TextButtonLink("نسيت كلمة المرور؟ استعادة", goRecovery)
        TextButtonLink("إنشاء حساب جديد", goRegister)
        TextButtonLink("العودة", goWelcome)
    }
}

@Composable
private fun RecoveryScreen(submit: (String, String, String) -> Unit, back: () -> Unit) {
    var redId by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    FormColumn(title = "استعادة الحساب") {
        Field(redId, { redId = it }, "معرّف يونس", leading = YounesIcons.Badge)
        Field(code, { code = it }, "رمز الاستعادة", leading = YounesIcons.Key)
        PasswordField(newPassword, { newPassword = it }, "كلمة المرور الجديدة")
        PrimaryButton("تغيير كلمة المرور") { submit(redId, code, newPassword) }
        TextButtonLink("العودة لتسجيل الدخول", back)
    }
}

@Composable
private fun PendingScreen(state: AuthState.Pending, check: () -> Unit, login: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Centered {
        Icon(YounesIcons.Shield, null, tint = authAccent(), modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(YounesSpace.s))
        Text("طلب الحساب بانتظار الاعتماد ⏳", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, fontFamily = PlexArabicFamily)
        Text(
            "تم إنشاء مفاتيح هويتك المشفرة داخل هاتفك ولن تغادر جهازك.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
            fontFamily = PlexArabicFamily
        )
        Spacer(Modifier.height(YounesSpace.m))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = authSurface()), shape = RoundedCornerShape(YounesRadius.card)) {
            Column(Modifier.padding(YounesSpace.m), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("معرّف يونس الخاص بك:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontFamily = PlexArabicFamily)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.redId, color = authAccent(), fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = PlexArabicFamily)
                    IconButton(onClick = { clipboard.setText(AnnotatedString(state.redId)) }) {
                        Icon(YounesIcons.Copy, "نسخ المعرّف", tint = authAccent(), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        if (state.recoveryCodes.isNotEmpty()) {
            Spacer(Modifier.height(YounesSpace.s))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)), shape = RoundedCornerShape(YounesRadius.card)) {
                Column(Modifier.padding(YounesSpace.m), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(YounesSpace.xs)) {
                    Text("⚠️ رموز الاستعادة — احفظها خارج الهاتف", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = PlexArabicFamily)
                    Text(state.recoveryCodes.joinToString("  •  "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, fontFamily = PlexArabicFamily)
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(state.recoveryCodes.joinToString("\n"))) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(YounesIcons.Copy, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(YounesSpace.xxs))
                        Text("نسخ كل الرموز", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontFamily = PlexArabicFamily)
                    }
                }
            }
        }
        Spacer(Modifier.height(YounesSpace.m))
        PrimaryButton("التحقق من اعتماد الحساب", check)
        Spacer(Modifier.height(YounesSpace.xs))
        TextButtonLink("تسجيل الدخول لاحقاً", login)
    }
}

@Composable
private fun StatusScreen(title: String, description: String, action: () -> Unit) = Centered {
    Icon(YounesIcons.Lock, null, tint = authAccent(), modifier = Modifier.size(64.dp))
    Spacer(Modifier.height(YounesSpace.s))
    Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, fontFamily = PlexArabicFamily)
    Spacer(Modifier.height(YounesSpace.xs))
    Text(description, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontFamily = PlexArabicFamily)
    Spacer(Modifier.height(YounesSpace.l))
    PrimaryButton("متابعة", action)
}

@Composable
private fun ErrorStatusScreen(title: String, description: String, action: () -> Unit) = Centered {
    Icon(YounesIcons.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
    Spacer(Modifier.height(YounesSpace.s))
    Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontFamily = PlexArabicFamily)
    Spacer(Modifier.height(YounesSpace.xs))
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))) {
        Text(description, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, modifier = Modifier.padding(YounesSpace.m), fontFamily = PlexArabicFamily)
    }
    Spacer(Modifier.height(YounesSpace.l))
    PrimaryButton("محاولة أخرى", action)
}

/** العلامة: نبضة واحدة بطيئة تحترم reduceMotion، بصورة متجهة لا PNG ضخمة. */
@Composable
private fun BrandMarkAnimated(size: Int) {
    val still = AppThemeState.reduceMotion || AppThemeState.highContrast
    val scale = if (still) 1f else {
        val transition = rememberInfiniteTransition(label = "brand")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.035f,
            animationSpec = infiniteRepeatable(tween(YounesMotion.SLOW_MS * 4, easing = LinearEasing), RepeatMode.Reverse),
            label = "brand_scale"
        ).value
    }
    Box(
        Modifier
            .size(size.dp)
            .scale(scale)
            .background(Brush.radialGradient(listOf(YounesPrimary.copy(alpha = 0.28f), Color.Transparent)), CircleShape)
            .border(1.5.dp, YounesPrimary.copy(alpha = 0.65f), CircleShape)
            .padding(YounesSpace.s),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.younes_icon_master_vector),
            contentDescription = "شعار يونس",
            modifier = Modifier.fillMaxSize().clip(CircleShape),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun Centered(scrollable: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val scrollModifier = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Column(
        Modifier
            .fillMaxSize()
            .then(scrollModifier)
            .padding(horizontal = YounesSpace.screen, vertical = YounesSpace.m)
            .widthIn(max = 520.dp)
            .animateContentSize(),
        Arrangement.Center,
        Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
private fun FormColumn(title: String, content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = YounesSpace.m, vertical = YounesSpace.m)
        .widthIn(max = 520.dp)
        .animateContentSize(),
    Arrangement.spacedBy(YounesSpace.xs, Alignment.CenterVertically),
    Alignment.CenterHorizontally
) {
    BrandMarkAnimated(72)
    Spacer(Modifier.height(YounesSpace.xs))
    Text("منظومة يونس السيادية 🛡️", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontFamily = PlexArabicFamily)
    Text(title, color = authAccent(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlexArabicFamily)
    Spacer(Modifier.height(YounesSpace.s))
    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = authSurface()),
        shape = RoundedCornerShape(YounesRadius.card)
    ) {
        Column(Modifier.fillMaxWidth().padding(YounesSpace.l), Arrangement.spacedBy(YounesSpace.s), content = content)
    }
}

@Composable
private fun Field(
    value: String,
    change: (String) -> Unit,
    label: String,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    leading: androidx.compose.ui.graphics.vector.ImageVector? = null
) = OutlinedTextField(
    value = value,
    onValueChange = change,
    modifier = Modifier.fillMaxWidth(),
    label = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontFamily = PlexArabicFamily) },
    leadingIcon = leading?.let { { Icon(it, null, tint = authAccent(), modifier = Modifier.size(YounesIconSizeMedium)) } },
    singleLine = true,
    keyboardOptions = keyboard,
    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = PlexArabicFamily),
    colors = fieldColors(),
    shape = RoundedCornerShape(YounesRadius.field)
)

@Composable
private fun PasswordField(value: String, change: (String) -> Unit, label: String) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = change,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontFamily = PlexArabicFamily) },
        leadingIcon = { Icon(YounesIcons.Lock, null, tint = authAccent(), modifier = Modifier.size(YounesIconSizeMedium)) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) YounesIcons.VisibilityOff else YounesIcons.Visibility,
                    if (visible) "إخفاء كلمة المرور" else "إظهار كلمة المرور",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = PlexArabicFamily),
        colors = fieldColors(),
        shape = RoundedCornerShape(YounesRadius.field)
    )
}

@Composable
private fun PasswordStrengthBar(password: String, username: String) {
    var score = 0
    if (password.length >= 12) score++
    if (password.length >= 16) score++
    if (password.any(Char::isUpperCase) && password.any(Char::isLowerCase)) score++
    if (password.any(Char::isDigit) || password.any { !it.isLetterOrDigit() }) score++
    if (username.isNotBlank() && password.contains(username, ignoreCase = true)) score = 0

    val label = when (score) { 0, 1 -> "ضعيفة ⚠️"; 2 -> "مقبولة 🟡"; 3 -> "قوية 🟢"; else -> "قوية جدًا 🛡️" }
    val color = when (score) {
        0, 1 -> MaterialTheme.colorScheme.error
        2 -> YounesAccent
        else -> YounesSuccess
    }
    Column(Modifier.fillMaxWidth().padding(vertical = YounesSpace.xxxs)) {
        LinearProgressIndicator(
            progress = { score / 4f },
            modifier = Modifier.fillMaxWidth().height(YounesSpace.xxs).clip(RoundedCornerShape(YounesSpace.xxxs)),
            color = color
        )
        Text("قوة كلمة المرور: $label", color = color, fontSize = 11.sp, fontFamily = PlexArabicFamily)
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) = Button(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().height(YounesSpace.minTouch),
    colors = ButtonDefaults.buttonColors(containerColor = authAccent(), contentColor = YounesOnBrand),
    shape = RoundedCornerShape(YounesRadius.pill)
) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = PlexArabicFamily) }

@Composable
private fun TextButtonLink(text: String, onClick: () -> Unit) = TextButton(onClick = onClick) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = PlexArabicFamily)
}
