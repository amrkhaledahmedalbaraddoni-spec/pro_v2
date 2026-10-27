package com.red.sovereign.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.red.sovereign.auth.AuthState
import com.red.sovereign.auth.AuthViewModel
import com.red.sovereign.calls.*
import com.red.sovereign.contacts.DirectoryViewModel
import com.red.sovereign.core.UnifiedNetworkManager
import com.red.sovereign.core.YounesId
import com.red.sovereign.features.chat.*
import com.red.sovereign.features.contacts.ContactsScreen
import com.red.sovereign.groups.GroupViewModel
import com.red.sovereign.media.AttachmentViewModel
import com.red.sovereign.media.VoiceMessageViewModel
import com.red.sovereign.settings.SettingsPage
import com.red.sovereign.settings.SettingsViewModel
import com.red.sovereign.settings.YounesSettingsSheet
import com.red.sovereign.ui.components.*
import com.red.sovereign.ui.screens.*
import com.red.sovereign.ui.theme.*
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

/**
 * لوحة تحكم يونس الحديثة - أفضل من واتساب وتيليجرام
 * 
 * المميزات:
 * - تصميم Liquid Glass 2026 مع زجاج ضبابي حقيقي
 * - 5 تبويبات رئيسية: الدردشات، المجموعات، المكالمات، الاستكشاف، المزيد
 * - كل تبويب له واجهة منفصلة حسب عمله الأساسي
 * - مكالمات تعمل وترن وتتعرف وتعمل على الشبكة المحلية وكل الشبكات
 * - أحدث التقنيات: Compose + Material3 + Haze + Coil3 + Lottie
 * - أداء فائق مع LazyColumn و paging
 * - دعم كامل للـ P2P المحلي
 */

enum class ModernSection(val icon: ImageVector, val label: String, val description: String) {
    CHATS(Icons.Default.Chat, "الدردشات", "محادثات فردية مشفرة E2EE"),
    GROUPS(Icons.Default.Groups, "المجموعات", "مجموعات مشفرة بـ Sender Keys"),
    CALLS(Icons.Default.Call, "المكالمات", "مركز المكالمات السيادي"),
    EXPLORE(Icons.Default.Explore, "استكشاف", "قنوات ومجتمعات وبثوث"),
    MORE(Icons.Default.MoreHoriz, "المزيد", "الإعدادات والخدمات")
}

// توحيد onChat (مع RedDashboard.resolveChatTarget): تطبيع + تحقق — يمنع فتح محادثة بمعرف فاسد.
private fun resolveModernChatTarget(raw: String?): String? {
    val clean = raw?.let { YounesId.normalizeInput(it) }?.trim().orEmpty()
    return if (clean.isNotBlank() && YounesId.isValid(clean)) clean else null
}

@Composable
inline fun <reified VM : androidx.lifecycle.ViewModel> appViewModel(): VM {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as android.app.Application
    return androidx.lifecycle.viewmodel.compose.viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernRedDashboard(
    account: AuthState.Authenticated,
    authViewModel: AuthViewModel,
    deepLinkSender: String? = null,
    deepLinkConversation: String? = null
) {
    val context = LocalContext.current
    val hazeState = rememberSovereignHaze()
    
    // تكيّف العرض 2026: WindowLayout الموحّد للمشروع (هاتف/قابل للطي/لوحي/سطح مكتب).
    // بديل calculateWindowSizeClass: لا تبعية إضافية، ونفس تصنيفات Material 3:
    // Compact = هاتف (<600dp) ، Medium = قابل للطي (600..839) ، Expanded = لوحي (≥840).
    val windowLayout = WindowLayout.current()
    val isCompact = windowLayout.isPhone
    val isMedium = windowLayout.isFoldable
    val isExpanded = windowLayout.isTablet
    
    var currentSection by remember { mutableStateOf(ModernSection.CHATS) }
    var showCallDialer by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    // ربط حقيقي (من RedDashboard): بحث شامل + جهات اتصال + هدف دردشة معلّق + صفحة إعدادات مستهدفة.
    var showSearch by remember { mutableStateOf(false) }
    var showContacts by remember { mutableStateOf(false) }
    var pendingChatTarget by remember { mutableStateOf<String?>(null) }
    var settingsInitialPage by remember { mutableStateOf(SettingsPage.ROOT) }
    val openSettingsAt: (SettingsPage) -> Unit = { page ->
        settingsInitialPage = page
        showSettings = true
    }
    // حوار البث المباشر (من RedDashboard: عنوان + خاص/عام + كلمة سر).
    var showLiveCreate by remember { mutableStateOf(false) }
    var liveTitle by remember { mutableStateOf("") }
    var liveIsPrivate by remember { mutableStateOf(false) }
    var livePassword by remember { mutableStateOf("") }
    // هدف الاتصال المعلق + بوابة الأذونات (من RedDashboard 364-373): لا اتصال بلا RECORD_AUDIO/CAMERA.
    var pendingDialerTarget by remember { mutableStateOf<String?>(null) }
    var pendingDialerVideo by remember { mutableStateOf(false) }
    val settings: SettingsViewModel = appViewModel()
    // مسح الهدف بعد فتح المحادثة حتى لا يُعاد فتحها عند تبديل التبويبات (RedDashboard 333-338).
    LaunchedEffect(pendingChatTarget, currentSection) {
        if (pendingChatTarget != null && currentSection == ModernSection.CHATS) {
            kotlinx.coroutines.delay(600)
            pendingChatTarget = null
        }
    }
    val dialerCallPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val audioGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = !pendingDialerVideo ||
            grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val redId = pendingDialerTarget
        if (audioGranted && cameraGranted && redId != null && YounesId.isValid(redId)) {
            // P0 2026-09-27: بلا آلة حالة ثانية — YounesCallService هو المالك الوحيد لـ CallRuntime،
            // وهو الذي يفرض حراسه بنفسه (مشغول / نفسك / أوفلاين / إذن الميكروفون).
            YounesCallService.start(context, redId, pendingDialerVideo)
            currentSection = ModernSection.CALLS
        } else if (redId != null) {
            android.widget.Toast.makeText(context, "تعذر الاتصال — تحقق من الأذونات والمعرف", android.widget.Toast.LENGTH_SHORT).show()
        }
        pendingDialerTarget = null
    }
    // بوابة أذونات المؤتمر (صوت+كاميرا) قبل ConferenceService.join — بلا صمت.
    val conferencePermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val audioOk = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camOk = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!audioOk) {
            android.widget.Toast.makeText(context, "امنح إذن الميكروفون لإنشاء المؤتمر", android.widget.Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        ConferenceService.join(
            context,
            "conf-${account.redId}-${System.currentTimeMillis()}",
            account.redId,
            camOk,
            asHost = true,
            title = "مؤتمر ${account.username}"
        )
        currentSection = ModernSection.CALLS
    }
    // بوابة أذونات البث (كاميرا+ميك) قبل LiveStreamService.start (RedDashboard 710-724).
    val livePermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val camOk = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val micOk = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (camOk && micOk) {
            val titleFinal = liveTitle.trim().ifBlank { "بث مباشر ${account.username}" }
            val pw = if (liveIsPrivate) livePassword.trim().takeIf { it.isNotBlank() } else null
            if (liveIsPrivate && pw.isNullOrBlank()) {
                android.widget.Toast.makeText(context, "أدخل كلمة سر للبث الخاص", android.widget.Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            showLiveCreate = false
            // توحيد المسار الحي (مع RedDashboard): تطبيع المعرف عبر RoomSeparationPolicy — لا خام.
            val liveRawId = "stream-${account.redId}-${System.currentTimeMillis()}"
            LiveStreamService.start(
                context,
                RoomSeparationPolicy.normalizeStreamId(liveRawId),
                account.redId,
                true,
                titleFinal,
                liveIsPrivate,
                pw
            )
            livePassword = ""
        } else {
            android.widget.Toast.makeText(context, "البث يحتاج الكاميرا والميكروفون", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    // ViewModels - الأصلية التي تعمل 100% بدون تكرار - أحدث وأفضل
    val groups: GroupViewModel = appViewModel()
    val directory: DirectoryViewModel = appViewModel()
    val callHistory: CallHistoryViewModel = appViewModel()
    val safety: com.red.sovereign.crypto.SafetyViewModel = appViewModel()
    val attachments: AttachmentViewModel = appViewModel()
    val voiceMessages: VoiceMessageViewModel = appViewModel()
    
    // شبكة موحدة - كل الشبكات: WiFi/Ethernet/USB/VPN/Hotspot/BT/Mobile + P2P
    val networkInfo by UnifiedNetworkManager.currentNetwork.collectAsState()
    val isOnline by UnifiedNetworkManager.isOnline.collectAsState()
    
    // مكالمات موحدة - 9 أنواع + 6 مسارات رنين مضمونة
    // P0 2026-09-27: المصدر الوحيد لحالة المكالمة هو CallRuntime (يكتبه YounesCallService وحده).
    val callState = CallRuntime.state
    
    // تتبع فتح محادثة من الفردي أو المجموعات لإخفاء الشريط السفلي والـ FAB والشريط العلوي ومنع التداخل
    var isChatOpen by remember { mutableStateOf(false) }
    var closeSignal by remember { mutableIntStateOf(0) }

    // P0 2026-09-27: موزع هرمي ذكي موحد لزر الرجوع (نمط واتساب وتيليجرام العالمي)
    // المستوى 1: إغلاق النوافذ والشيتات العائمة
    // المستوى 2: إغلاق المحادثة النشطة والعودة للقائمة
    // المستوى 3: الانتقال من الأقسام الفرعية إلى تبويب الدردشات الرئيسي
    // المستوى 4: في جذر الدردشات، ضغطتان للخروج أو النزول الآمن للخلفية (moveTaskToBack)
    var lastBackPressMs by remember { mutableLongStateOf(0L) }
    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            showSettings -> showSettings = false
            showSearch -> showSearch = false
            showContacts -> showContacts = false
            showCreateGroup -> showCreateGroup = false
            showCallDialer -> showCallDialer = false
            showLiveCreate -> showLiveCreate = false
            isChatOpen -> closeSignal++
            currentSection != ModernSection.CHATS -> currentSection = ModernSection.CHATS
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressMs < 2000L) {
                    val activity = context as? Activity
                    if (activity != null) {
                        val sentToBack = activity.moveTaskToBack(true)
                        if (!sentToBack) activity.finish()
                    }
                } else {
                    lastBackPressMs = now
                    Toast.makeText(context, "اضغط رجوع مجدداً للخروج", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // تبديل تلقائي لتبويب المكالمات عند وجود مكالمة - UX أسطوري
    // (صار يعمل للوارد أيضاً: كان مربوطاً بحالة لا يكتبها إلا المنسق الوهمي فلا يُطلَق أبداً)
    LaunchedEffect(callState) {
        if (callState !is CallUiState.Idle && callState !is CallUiState.CallEnded) {
            currentSection = ModernSection.CALLS
        }
    }

    LaunchedEffect(currentSection) {
        if (currentSection != ModernSection.CHATS && currentSection != ModernSection.GROUPS) {
            isChatOpen = false
        }
    }
    
    Scaffold(
        containerColor = YounesMidnight,
        bottomBar = {
            if (!isChatOpen) {
                ModernBottomBar(
                    currentSection = currentSection,
                    onSectionSelected = { section ->
                        currentSection = section
                        if (section == ModernSection.CALLS) {
                            callHistory.load()
                            directory.refreshPresence()
                        }
                    },
                    unreadChats = 0, // سيتم حسابه من المحادثات
                    missedCalls = callHistory.calls.count { it.status == "MISSED" },
                    hazeState = hazeState
                )
            }
        },
        floatingActionButton = {
            if (!isChatOpen) {
                ModernFabForSection(
                    section = currentSection,
                    // RedDashboard: زر الدردشة يفتح جهات الاتصال الحقيقية (CONTACTS).
                    onChatClick = { showContacts = true },
                    onGroupClick = { showCreateGroup = true },
                    onCallClick = { showCallDialer = true },
                    // إنشاء محتوى حقيقي: حوار البث المباشر (بدل no-op).
                    onExploreClick = {
                        liveTitle = ""
                        liveIsPrivate = false
                        livePassword = ""
                        showLiveCreate = true
                    }
                )
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .sovereignHazeSource(hazeState)
                .background(YounesMidnight)
                .padding(if (isChatOpen) PaddingValues(0.dp) else padding)
        ) {
            Column(Modifier.fillMaxSize()) {
                if (!isChatOpen) {
                    // شريط علوي حديث مع حالة الشبكة (يختفي عند فتح محادثة لمنع التداخل)
                    ModernTopBar(
                        redId = account.redId,
                        username = account.username,
                        networkQuality = networkInfo?.quality?.name ?: "UNKNOWN",
                        isLan = networkInfo?.type?.name?.contains("WIFI") == true,
                        isOnline = isOnline,
                        onSettings = { settingsInitialPage = SettingsPage.ROOT; showSettings = true },
                        // RedDashboard: البحث يفتح RedGlobalSearch الحقيقية (SEARCH).
                        onSearch = { showSearch = true }
                    )
                }
                
                // P0 2026-09-27: تنبيه إعفاء البطارية — النظام يقتل التطبيق في
                // الخلفية فينقطع السوكت/ntfy ويبدو "السيرفر لا يصل". يظهر مرة واحدة.
                BatteryExemptionBanner()

                // محتوى التبويب الحالي — انتقال سلس حديث بين التبويبات
                AnimatedContent(
                    targetState = currentSection,
                    transitionSpec = {
                        if (AppThemeState.reduceMotion) {
                            fadeIn(animationSpec = tween(1)).togetherWith(fadeOut(animationSpec = tween(1)))
                        } else {
                            (fadeIn(animationSpec = tween(300)) +
                                slideInHorizontally(animationSpec = SpringSnappyIntOffset) { fullWidth -> if (targetState.ordinal > initialState.ordinal) fullWidth / 2 else -fullWidth / 2 }
                            ).togetherWith(
                                fadeOut(animationSpec = tween(200)) +
                                slideOutHorizontally(animationSpec = SpringSnappyIntOffset) { fullWidth -> if (targetState.ordinal > initialState.ordinal) -fullWidth / 2 else fullWidth / 2 }
                            )
                        }
                    },
                    label = "ModernSectionTransition"
                ) { section ->
                    when (section) {
                        ModernSection.CHATS -> ModernChatsScreen(
                            account = account,
                            groups = groups,
                            directory = directory,
                            attachments = attachments,
                            voiceMessages = voiceMessages,
                            // RedDashboard 610: الهدف المعلق أولاً ثم deep link الإشعار.
                            deepLinkSender = pendingChatTarget ?: deepLinkSender,
                            deepLinkConversation = deepLinkConversation,
                            onConversationOpen = { isChatOpen = it },
                            closeSignal = closeSignal
                        )
                        ModernSection.GROUPS -> ModernGroupsScreen(
                            account = account,
                            groups = groups,
                            directory = directory,
                            onCreateGroup = { showCreateGroup = true },
                            onConversationOpen = { isChatOpen = it },
                            closeSignal = closeSignal
                        )
                        ModernSection.CALLS -> ModernCallsScreen(
                            ownUserId = account.redId,
                            history = callHistory,
                            contacts = directory.contacts,
                            onlineIds = directory.onlineIds.toSet(),
                            myDisplayName = account.username,
                            onExplore = { currentSection = ModernSection.EXPLORE }
                        )
                        ModernSection.EXPLORE -> ModernExploreScreen(
                            account = account,
                            onBack = { currentSection = ModernSection.CHATS },
                            onStartLive = {
                                liveTitle = ""
                                liveIsPrivate = false
                                livePassword = ""
                                showLiveCreate = true
                            },
                            onCreateConference = {
                                conferencePermissions.launch(
                                    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                                )
                            }
                        )
                        ModernSection.MORE -> ModernMoreScreen(
                            account = account,
                            onSettings = { settingsInitialPage = SettingsPage.ROOT; showSettings = true },
                            onOpenSettingsPage = openSettingsAt
                        )
                    }
                }
            }
        }
    }
    
    // Overlays للمكالمات — الموحدة الحقيقية (1:1 + زووم + جماعية + مؤتمر + بث) المدارة عبر CallRuntime.
    // P0 2026-09-27: حُذف النداء المكرر `UnifiedCallOverlaysModern()` (نسخة ui المحلية) — كان
    // يُرسم فوق الشاشة الحيّة فيحجبها ويُظهر لوحة "جاري الاتصال…" الأبدية (الحالة Active لا تُبلَّغ قط)
    // ويُنشئ مسار إنهاء ثانياً = انهيار عند إغلاق المكالمة. المصدر الوحيد الآن: طبقة calls/
    // (`UnifiedCallOverlays` + `YounesCallOverlay` + `ActiveCallScreen`).
    com.red.sovereign.calls.UnifiedCallOverlays()

    // بحث شامل حقيقي (RedGlobalSearch) — كان no-op.
    if (showSearch) {
        RedGlobalSearch(
            onBack = { showSearch = false },
            onOpenConversation = { redId ->
                resolveModernChatTarget(redId)?.let {
                    pendingChatTarget = it
                    showSearch = false
                    currentSection = ModernSection.CHATS
                }
            }
        )
    }

    // جهات اتصال حقيقية (ContactsScreen): دردشة تفتح المحادثة، اتصال يبدأ المكالمة (RedDashboard 545).
    if (showContacts) {
        ContactsScreen(
            directory = directory,
            onBack = { showContacts = false },
            onChat = { person ->
                resolveModernChatTarget(person.redId)?.let {
                    pendingChatTarget = it
                    showContacts = false
                    currentSection = ModernSection.CHATS
                }
            },
            onCall = { person, video ->
                showContacts = false
                currentSection = ModernSection.CALLS
                YounesCallService.start(context, person.redId, video)
            },
            onCreateGroup = { showContacts = false; showCreateGroup = true }
        )
    }
    
    // حوارات
    if (showCallDialer) {
        ModernCallDialerDialog(
            onDismiss = { showCallDialer = false },
            // بوابة RedDashboard: تطبيع + تحقق + أذونات قبل بدء المكالمة.
            onCall = { redIdRaw, isVideo ->
                val redId = YounesId.normalizeInput(redIdRaw)
                if (!YounesId.isValid(redId)) {
                    android.widget.Toast.makeText(context, YounesId.ERROR_MESSAGE, android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    showCallDialer = false
                    pendingDialerTarget = redId
                    pendingDialerVideo = isVideo
                    dialerCallPermissions.launch(
                        buildList {
                            add(Manifest.permission.RECORD_AUDIO)
                            if (isVideo) add(Manifest.permission.CAMERA)
                        }.toTypedArray()
                    )
                }
            }
        )
    }
    
    if (showCreateGroup) {
        ModernCreateGroupDialog(
            onDismiss = { showCreateGroup = false },
            onCreate = { name, description, memberIds ->
                // حارس العضو الواحد (موحد مع RedDashboard): لا مجموعة بلا أعضاء آخرين.
                if (memberIds.isEmpty()) {
                    android.widget.Toast.makeText(context, "اختر عضوًا واحدًا على الأقل لإنشاء المجموعة", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    showCreateGroup = false
                    groups.create(name, description, memberRedIds = memberIds) {
                        currentSection = ModernSection.GROUPS
                    }
                }
            },
            contacts = directory.contacts
        )
    }
    
    // إعدادات حقيقية (YounesSettingsSheet) — كانت طبقة معتمة بلا محتوى (RedDashboard 657).
    if (showSettings) {
        YounesSettingsSheet(
            account,
            settings,
            authViewModel,
            authViewModel::logout,
            { showSettings = false; settingsInitialPage = SettingsPage.ROOT },
            initialPage = settingsInitialPage
        )
    }

    // حوار إنشاء البث المباشر — خاص بكلمة سر أو عام (RedDashboard 709-763).
    if (showLiveCreate) {
        AlertDialog(
            onDismissRequest = { showLiveCreate = false; livePassword = "" },
            title = { Text("بدء بث مباشر 🔴", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = liveTitle,
                        onValueChange = { liveTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("عنوان البث (اختياري)") },
                        singleLine = true
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            if (liveIsPrivate) Icons.Default.Lock else Icons.Default.Public,
                            null,
                            tint = if (liveIsPrivate) Color(0xFFE53935) else YounesPrimary
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (liveIsPrivate) "بث خاص بكلمة سر" else "بث عام (بدون كلمة سر)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                if (liveIsPrivate) "المشاهدون يحتاجون كلمة السر" else "يمكن للجميع المشاهدة",
                                fontSize = 11.sp,
                                color = YounesMuted
                            )
                        }
                        Switch(checked = liveIsPrivate, onCheckedChange = { liveIsPrivate = it })
                    }
                    if (liveIsPrivate) {
                        OutlinedTextField(
                            value = livePassword,
                            onValueChange = { livePassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("كلمة السر") },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { livePermissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }) {
                    Text("بدء البث")
                }
            },
            dismissButton = { TextButton({ showLiveCreate = false; livePassword = "" }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun ModernTopBar(
    redId: String,
    username: String,
    networkQuality: String,
    isLan: Boolean,
    isOnline: Boolean,
    onSettings: () -> Unit,
    onSearch: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(YounesSurface1.copy(alpha = 0.95f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // أفاتار وشعار
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(YounesPrimary, YounesCobalt))),
            contentAlignment = Alignment.Center
        ) {
            Text("ي", color = YounesOnPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
        
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "يونس • @$username",
                    fontSize = 14.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                NetworkStatusBadge(
                    quality = networkQuality,
                    isLan = isLan
                )
            }
            // مؤشر حالة السيرفر الحقيقية (حالة المقبس لا الشبكة): أخضر=متصل، ذهبي=جارٍ الاتصال، أحمر=طافي مع العد التنازلي.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val serverStatus by com.red.sovereign.core.ConnectionStatusRepository.status.collectAsState()
                val (serverDot, serverLabel, serverColor) = when (serverStatus.state) {
                    com.red.sovereign.core.ConnectionStatusRepository.ServerUiState.ONLINE ->
                        Triple(Color(0xFF00C98C), redId, YounesCobalt)
                    com.red.sovereign.core.ConnectionStatusRepository.ServerUiState.CONNECTING ->
                        Triple(YounesAccent, "جارٍ الاتصال بالسيرفر…", YounesAccent)
                    com.red.sovereign.core.ConnectionStatusRepository.ServerUiState.OFFLINE ->
                        Triple(
                            Color(0xFFF25C5C),
                            if (serverStatus.retryInSec > 0) "السيرفر طافي — إعادة خلال ${serverStatus.retryInSec}ث" else "السيرفر طافي",
                            Color(0xFFF25C5C)
                        )
                }
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(serverDot)
                )
                Text(
                    serverLabel,
                    color = serverColor,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
        
        IconButton(onClick = onSearch) {
            Icon(Icons.Default.Search, "بحث", tint = Color.White)
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, "إعدادات", tint = Color.White)
        }
    }
}

@Composable
fun ModernBottomBar(
    currentSection: ModernSection,
    onSectionSelected: (ModernSection) -> Unit,
    unreadChats: Int,
    missedCalls: Int,
    hazeState: dev.chrisbanes.haze.HazeState? = null
) {
    // سيتم تنفيذ شريط سفلي حديث مع Haze
    NavigationBar(
        modifier = Modifier.sovereignHazeEffect(hazeState, SovereignGlassTier.NavBar),
        containerColor = if (AppThemeState.liquidGlassEnabled && hazeState != null) Color.Transparent else YounesSurface1.copy(alpha = 0.95f),
        contentColor = Color.White
    ) {
        ModernSection.values().forEach { section ->
            val isSelected = currentSection == section
            val badgeCount = when (section) {
                ModernSection.CHATS -> unreadChats
                ModernSection.CALLS -> missedCalls
                else -> 0
            }
            
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSectionSelected(section) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (badgeCount > 0) {
                                Badge { Text("$badgeCount") }
                            }
                        }
                    ) {
                        Icon(
                            section.icon,
                            section.label,
                            tint = if (isSelected) YounesPrimary else YounesMuted
                        )
                    }
                },
                label = {
                    Text(
                        section.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) YounesPrimary else YounesMuted
                    )
                }
            )
        }
    }
}

@Composable
fun ModernFabForSection(
    section: ModernSection,
    onChatClick: () -> Unit,
    onGroupClick: () -> Unit,
    onCallClick: () -> Unit,
    onExploreClick: () -> Unit
) {
    when (section) {
        ModernSection.CHATS -> ModernFab(
            icon = Icons.Default.Chat,
            text = "دردشة",
            onClick = onChatClick
        )
        ModernSection.GROUPS -> ModernFab(
            icon = Icons.Default.GroupAdd,
            text = "مجموعة",
            onClick = onGroupClick
        )
        ModernSection.CALLS -> ModernFab(
            icon = Icons.Default.Dialpad,
            text = "اتصال",
            onClick = onCallClick
        )
        ModernSection.EXPLORE -> ModernFab(
            icon = Icons.Default.Add,
            text = "إنشاء",
            onClick = onExploreClick
        )
        else -> {}
    }
}

// شاشات فرعية حديثة أسطورية - تصلح كل المشاكل بدون تكرارات

/**
 * P0 2026-09-27: يفعّل BatteryOptimizationHelper الميت — بدونه يخنق النظام
 * التطبيق خلفياً فيتوقف الرنين والمزامنة ويظن المستخدم أن السيرفر ساقط.
 */
@Composable
private fun BatteryExemptionBanner() {
    val context = LocalContext.current
    var dismissed by remember { mutableStateOf(false) }
    var exempt by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        exempt = com.red.sovereign.util.BatteryOptimizationHelper.isBatteryOptimizationIgnored(context)
    }
    if (dismissed || exempt) return
    Surface(
        color = Color(0xFF3D2E00),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "للبقاء متصلاً واستقبال المكالمات: أعِف التطبيق من تحسين البطارية",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFF0D48C),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                com.red.sovereign.util.BatteryOptimizationHelper.requestIgnoreBatteryOptimization(context)
                dismissed = true
            }) { Text("استثناء", color = Color(0xFFF0D48C), fontWeight = FontWeight.Bold) }
            TextButton(onClick = {
                com.red.sovereign.util.BatteryOptimizationHelper.openAutostartSettings(context)
                dismissed = true
            }) { Text("الشركة", color = Color(0xFFF0D48C)) }
        }
    }
}

@Composable
fun ModernChatsScreen(
    account: AuthState.Authenticated,
    groups: GroupViewModel,
    directory: DirectoryViewModel,
    attachments: AttachmentViewModel,
    voiceMessages: VoiceMessageViewModel,
    deepLinkSender: String?,
    deepLinkConversation: String?,
    onConversationOpen: (Boolean) -> Unit = {},
    closeSignal: Int = 0
) {
    // استخدام الشاشة الأصلية المحسنة التي تعمل 100% - لا تكرار
    // ChatHubScreen موجودة وممتازة مع E2EE + P2P + كل المميزات
    // تحتاج SafetyViewModel
    val safety: com.red.sovereign.crypto.SafetyViewModel = appViewModel()
    com.red.sovereign.ui.screens.ChatHubScreen(
        account = account,
        groups = groups,
        directory = directory,
        safety = safety,
        attachments = attachments,
        voiceMessages = voiceMessages,
        showGroups = false,
        deepLinkSender = deepLinkSender,
        deepLinkConversation = deepLinkConversation,
        onConversationOpen = onConversationOpen,
        closeSignal = closeSignal
    )
}

@Composable
fun ModernGroupsScreen(
    account: AuthState.Authenticated,
    groups: GroupViewModel,
    directory: DirectoryViewModel,
    onCreateGroup: () -> Unit,
    onConversationOpen: (Boolean) -> Unit = {},
    closeSignal: Int = 0
) {
    // إصلاح أسطوري: المجموعات تنشأ وتظهر - كل شيء موجود
    // استخدام ChatHubScreen مع showGroups=true - هي الأصل وتعمل 100%
    // GroupViewModel موجود وممتاز مع optimistic UI + bulk add + كل المميزات
    val safety: com.red.sovereign.crypto.SafetyViewModel = appViewModel()
    val attachments: AttachmentViewModel = appViewModel()
    val voiceMessages: VoiceMessageViewModel = appViewModel()
    com.red.sovereign.ui.screens.ChatHubScreen(
        account = account,
        groups = groups,
        directory = directory,
        safety = safety,
        attachments = attachments,
        voiceMessages = voiceMessages,
        showGroups = true,
        onCreateGroup = onCreateGroup,
        onConversationOpen = onConversationOpen,
        closeSignal = closeSignal
    )
}

@Composable
fun ModernCallsScreen(
    ownUserId: String,
    history: CallHistoryViewModel,
    contacts: List<com.red.sovereign.contacts.PublicRedProfile>,
    onlineIds: Set<String>,
    myDisplayName: String,
    onExplore: () -> Unit
) {
    // مركز المكالمات الحي — منقول سلوكه من RedDashboard.UnifiedCallsScreen (قراءة فقط):
    // بوابات إذن قبل كل إطلاق + GroupCallPicker + ConferenceHub + LiveStreamHub + مساحة + سجل بحث/فلترة + إعادة اتصال.
    // (لا يستدعي UnifiedCallsScreen الميتة ذات الـ4 معاملات — كانت تكسر البناء بمعاملات contacts/onlineIds الزائدة).
    val context = LocalContext.current
    var showNewCallDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showLiveDialog by remember { mutableStateOf(false) }
    var showSpaceDialog by remember { mutableStateOf(false) }
    var showGroupCallPicker by remember { mutableStateOf(false) }
    // إزالة الميت: شاشة المجدولة الحقيقية بدل toast التحويل (موحدة مع RedDashboard).
    var showScheduledCalls by remember { mutableStateOf(false) }
    var newCallTargetInput by remember { mutableStateOf("") }
    var roomInput by remember { mutableStateOf("") }
    var isSpaceHost by remember { mutableStateOf(false) }

    if (showScheduledCalls) {
        com.red.sovereign.features.calls.ScheduledCallsScreen(onBack = { showScheduledCalls = false })
        return
    }

    val privateCallLauncher = rememberCallPermissionLauncher(
        needCamera = true,
        onGranted = { showNewCallDialog = true },
        onDenied = { android.widget.Toast.makeText(context, "مطلوب إذن الميكروفون والكاميرا لإجراء المكالمة", android.widget.Toast.LENGTH_SHORT).show() }
    )
    val groupCallLauncher = rememberCallPermissionLauncher(
        needCamera = true,
        onGranted = { showGroupCallPicker = true },
        onDenied = { android.widget.Toast.makeText(context, "مطلوب إذن الميكروفون والكاميرا للمكالمة الجماعية", android.widget.Toast.LENGTH_SHORT).show() }
    )
    val conferenceLauncher = rememberCallPermissionLauncher(
        needCamera = true,
        onGranted = { showJoinDialog = true },
        onDenied = { android.widget.Toast.makeText(context, "مطلوب إذن الميكروفون والكاميرا للمؤتمر", android.widget.Toast.LENGTH_SHORT).show() }
    )
    val liveLauncher = rememberCallPermissionLauncher(
        needCamera = true,
        onGranted = { showLiveDialog = true },
        onDenied = { android.widget.Toast.makeText(context, "مطلوب إذن الميكروفون والكاميرا للبث", android.widget.Toast.LENGTH_SHORT).show() }
    )
    val spaceLauncher = rememberCallPermissionLauncher(
        needCamera = false,
        onGranted = { showSpaceDialog = true },
        onDenied = { android.widget.Toast.makeText(context, "مطلوب إذن الميكروفون لدخول المساحة الصوتية", android.widget.Toast.LENGTH_SHORT).show() }
    )
    var showZoomDialog by remember { mutableStateOf(false) }
    val zoomLauncher = rememberCallPermissionLauncher(
        needCamera = true,
        onGranted = { showZoomDialog = true },
        onDenied = { android.widget.Toast.makeText(context, "مطلوب إذن الميكروفون والكاميرا لاجتماعات Zoom و IMO", android.widget.Toast.LENGTH_SHORT).show() }
    )

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Text("مركز المكالمات السيادي", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("المكالمات الفردية، المؤتمرات، والبث المباشر", color = YounesMuted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        CallsHubLaunchers(
            onNewCall = { privateCallLauncher() },
            onGroupCallPicker = { groupCallLauncher() },
            onConference = { conferenceLauncher() },
            onSpace = { spaceLauncher() },
            onLive = { liveLauncher() },
            onExplore = onExplore,
            onScheduledCalls = { showScheduledCalls = true },
            onZoomMeeting = { zoomLauncher() }
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = history.searchQuery,
            onValueChange = { history.searchQuery = it },
            placeholder = { Text("بحث في سجل المكالمات (اسم أو معرف)...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(CallFilterType.values()) { fType ->
                FilterChip(
                    selected = history.selectedFilter == fType,
                    onClick = { history.selectedFilter = fType },
                    label = { Text(fType.label, fontSize = 11.sp) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val visible = history.filteredCalls
        when {
            history.loading -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            history.error != null -> Text(history.error.orEmpty(), color = YounesMuted, fontSize = 12.sp)
            visible.isEmpty() -> Text("لا توجد مكالمات تطابق البحث — ستظهر هنا المكالمات المفلترة.", color = YounesMuted, fontSize = 12.sp)
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.id }) { call ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = YounesSurface1)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(call.peerLabel.ifBlank { call.peerId }, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                                Text("${call.type} · ${call.status}", color = YounesMuted, fontSize = 11.sp)
                            }
                            IconButton(onClick = {
                                // توحيد onChat: إعادة الاتصال تتطلب معرفًا صالحًا فقط.
                                if (YounesId.isValid(call.peerId)) YounesCallService.start(context, call.peerId, video = false)
                                else android.widget.Toast.makeText(context, YounesId.ERROR_MESSAGE, android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.Call, "إعادة صوتية", tint = YounesPrimary)
                            }
                            IconButton(onClick = {
                                if (YounesId.isValid(call.peerId)) YounesCallService.start(context, call.peerId, video = true)
                                else android.widget.Toast.makeText(context, YounesId.ERROR_MESSAGE, android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.Videocam, "إعادة فيديو", tint = YounesPrimary)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGroupCallPicker) {
        GroupCallPickerDialog(
            contacts = contacts,
            onlineIds = onlineIds,
            onDismiss = { showGroupCallPicker = false },
            onStartCall = { selectedIds, isVideo ->
                showGroupCallPicker = false
                // حارس العضو الواحد: مكالمة جماعية بلا مدعوين = مع النفس — امنع بتفسير.
                if (selectedIds.isEmpty()) {
                    android.widget.Toast.makeText(context, "اختر عضوًا واحدًا على الأقل للمكالمة الجماعية", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    val selectedNames = selectedIds.map { id -> contacts.find { it.redId == id }?.displayName ?: id }
                    GroupCallService.startGroupCall(
                        context = context,
                        myUserId = ownUserId,
                        inviteeIds = selectedIds,
                        inviteeNames = selectedNames,
                        isVideo = isVideo,
                        hostName = myDisplayName
                    )
                }
            }
        )
    }
    if (showJoinDialog) {
        ConferenceHubDialog(
            onDismiss = { showJoinDialog = false },
            onCreateNew = {
                showJoinDialog = false
                ConferenceService.join(context, "conf-${ownUserId}-${System.currentTimeMillis()}", ownUserId, true, asHost = true, title = "مؤتمر $myDisplayName")
            },
            onJoinExisting = { roomId, password ->
                showJoinDialog = false
                ConferenceService.join(context, roomId, ownUserId, true, asHost = false, joinPassword = password)
            }
        )
    }
    if (showLiveDialog) {
        LiveStreamHubDialog(
            onDismiss = { showLiveDialog = false },
            onStartBroadcasting = { title, audience, pass, friendIds, category ->
                val hasCam = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasCam || !hasMic) {
                    android.widget.Toast.makeText(context, "امنح الكاميرا والميكروفون أولاً", android.widget.Toast.LENGTH_LONG).show()
                    return@LiveStreamHubDialog
                }
                val isPriv = audience != "PUBLIC"
                val password = if (isPriv) pass.trim().takeIf { it.isNotBlank() } else null
                showLiveDialog = false
                // توحيد المسار الحي (مع RedDashboard): نفس الصيغة stream-<المالك>-<زمن> ثم normalize — لا خام قصير.
                val streamId = RoomSeparationPolicy.normalizeStreamId("stream-${ownUserId}-${System.currentTimeMillis()}")
                LiveStreamService.start(
                    context = context,
                    streamId = streamId,
                    userId = ownUserId,
                    isBroadcaster = true,
                    title = title.ifBlank { "بث $myDisplayName" },
                    isPrivate = isPriv,
                    password = password,
                    category = category
                )
            },
            onWatchStream = { streamId, password ->
                showLiveDialog = false
                LiveStreamService.watch(context, streamId, ownUserId, password)
            },
            friends = contacts
        )
    }
    if (showSpaceDialog) {
        AlertDialog(
            onDismissRequest = { showSpaceDialog = false; roomInput = ""; isSpaceHost = false },
            title = { Text("مساحة صوتية يونس") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("مساحة صوتية مشفرة عبر SFU — صوت فقط بلا كاميرا.", fontSize = 14.sp)
                    OutlinedTextField(
                        value = roomInput,
                        onValueChange = { roomInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("معرف المساحة (اختياري)") },
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(checked = isSpaceHost, onCheckedChange = { isSpaceHost = it })
                        Text("الانضمام كمضيف", fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showSpaceDialog = false
                    val spaceId = roomInput.trim().ifBlank { "space-${ownUserId.lowercase()}-${System.currentTimeMillis() % 100000}" }
                    ConferenceService.join(context, spaceId, ownUserId, false, asHost = isSpaceHost || roomInput.isBlank())
                    roomInput = ""
                    isSpaceHost = false
                }) { Text(if (roomInput.isBlank()) "إنشاء مساحة جديدة" else "دخول المساحة") }
            },
            dismissButton = {
                TextButton(onClick = { showSpaceDialog = false; roomInput = ""; isSpaceHost = false }) { Text("إلغاء") }
            }
        )
    }
    if (showZoomDialog) {
        ZoomMeetingHubDialog(
            onDismiss = { showZoomDialog = false },
            onStartInstant = { title, isVideo ->
                showZoomDialog = false
                val meetingId = com.red.sovereign.calls.ZoomGroupCallService.generateMeetingId()
                com.red.sovereign.calls.ZoomGroupCallService.startZoom(
                    context = context,
                    myUserId = ownUserId,
                    inviteeIds = emptyList(),
                    inviteeNames = emptyList(),
                    isVideo = isVideo,
                    title = title.ifBlank { "اجتماع $myDisplayName" },
                    meetingId = meetingId,
                    hostName = myDisplayName
                )
            },
            onJoinByMeetingId = { meetingId, isVideo ->
                showZoomDialog = false
                com.red.sovereign.calls.ZoomGroupCallService.accept(
                    context = context,
                    meetingId = meetingId,
                    myUserId = ownUserId,
                    isVideo = isVideo,
                    hostId = ""
                )
            }
        )
    }
    if (showNewCallDialog) {
        AlertDialog(
            onDismissRequest = { showNewCallDialog = false; newCallTargetInput = "" },
            title = { Text("مكالمة جديدة مشفرة E2EE") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newCallTargetInput,
                        onValueChange = { newCallTargetInput = YounesId.normalizeInput(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("معرف يونس (مثال: 10001)") },
                        singleLine = true
                    )
                    // توحيد onChat: تلميح فوري عند معرف غير صالح (تطبيع عند الإدخال أعلاه).
                    if (newCallTargetInput.trim().isNotBlank() && !YounesId.isValid(newCallTargetInput)) {
                        Text(YounesId.ERROR_MESSAGE, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }
                    if (contacts.isNotEmpty()) {
                        Text("جهات الاتصال السريعة:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        LazyColumn(modifier = Modifier.fillMaxWidth().height(160.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val filtered = contacts.filter {
                                newCallTargetInput.isBlank() || it.displayName.contains(newCallTargetInput, true) || it.redId.contains(newCallTargetInput)
                            }
                            items(filtered, key = { it.redId }) { contact ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(contact.displayName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(contact.redId, color = YounesMuted, fontSize = 11.sp)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(onClick = { showNewCallDialog = false; YounesCallService.start(context, contact.redId, video = false) }) {
                                            Icon(Icons.Default.Call, "صوت")
                                        }
                                        IconButton(onClick = { showNewCallDialog = false; YounesCallService.start(context, contact.redId, video = true) }) {
                                            Icon(Icons.Default.Videocam, "فيديو")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                // توحيد onChat: تطبيع + تحقق YounesId قبل بدء المكالمة — يمنع الاتصال بمعرف فاسد.
                val cleanModernTarget = YounesId.normalizeInput(newCallTargetInput)
                val canDialModern = YounesId.isValid(cleanModernTarget)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (!canDialModern) {
                                android.widget.Toast.makeText(context, YounesId.ERROR_MESSAGE, android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                showNewCallDialog = false
                                YounesCallService.start(context, cleanModernTarget, video = false)
                                newCallTargetInput = ""
                            }
                        },
                        enabled = newCallTargetInput.trim().isNotBlank()
                    ) { Text("صوتية") }
                    Button(
                        onClick = {
                            if (!canDialModern) {
                                android.widget.Toast.makeText(context, YounesId.ERROR_MESSAGE, android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                showNewCallDialog = false
                                YounesCallService.start(context, cleanModernTarget, video = true)
                                newCallTargetInput = ""
                            }
                        },
                        enabled = newCallTargetInput.trim().isNotBlank()
                    ) { Text("فيديو") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewCallDialog = false; newCallTargetInput = "" }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
fun ModernExploreScreen(
    account: AuthState.Authenticated,
    onBack: () -> Unit,
    onStartLive: () -> Unit,
    onCreateConference: () -> Unit
) {
    var exploreTab by remember { mutableIntStateOf(0) }
    val feedVm: com.red.sovereign.social.FeedViewModel = appViewModel()
    val storiesVm: com.red.sovereign.stories.StoryViewModel = appViewModel()
    val directoryVm: DirectoryViewModel = appViewModel()

    Column(
        Modifier
            .fillMaxSize()
            .background(YounesMidnight)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "رجوع", tint = Color.White)
            }
            Text("الاستكشاف والمجتمع", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        TabRow(
            selectedTabIndex = exploreTab,
            containerColor = YounesSurface1,
            contentColor = Color.White
        ) {
            Tab(
                selected = exploreTab == 0,
                onClick = { exploreTab = 0 },
                text = { Text("المنشورات والحالات", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Public, null) }
            )
            Tab(
                selected = exploreTab == 1,
                onClick = { exploreTab = 1 },
                text = { Text("البثوث والمؤتمرات", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.LiveTv, null) }
            )
        }

        if (exploreTab == 0) {
            FeedScreen(
                account = account,
                feed = feedVm,
                stories = storiesVm,
                directory = directoryVm,
                onCreate = { /* FeedScreen يدير حوارات إنشاء المنشور والحالة داخلياً */ }
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // بث مباشر - بدون شاشة سوداء
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = YounesSurface1)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .background(Color(0xFFEF4444), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("مباشر", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("البث المباشر", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Text("بث مباشر مع جمهور غير محدود - بدون شاشة سوداء، EGL مضمون", fontSize = 12.sp, color = YounesMuted)
                        Button(
                            onClick = onStartLive,
                            colors = ButtonDefaults.buttonColors(containerColor = YounesRose),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.LiveTv, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("بدء بث مباشر")
                        }
                    }
                }
                
                // مؤتمرات - أفضل من تويتر
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = YounesSurface1)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .background(Color(0xFF7C3AED), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("أفضل من تويتر", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("المؤتمرات السيادية", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Text("100 مشارك فيديو vs تويتر 13 صوت فقط + غرف فرعية + تسجيل + مشاركة شاشة - شغالة 100%", fontSize = 12.sp, color = YounesMuted)
                        Button(
                            onClick = onCreateConference,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.VideoCameraFront, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("إنشاء مؤتمر")
                        }
                    }
                }
                
                // قنوات ومجتمعات — كل بطاقة تفتح وجهتها بدل البطاقة الميتة.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        Modifier.weight(1f).clickable { onStartLive() },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = YounesSurface1)
                    ) {
                        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Campaign, null, tint = YounesPrimary, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("القنوات", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            Text("بثوث عامة — اضغط للاستكشاف", fontSize = 11.sp, color = YounesMuted)
                        }
                    }
                    Card(
                        Modifier.weight(1f).clickable { onCreateConference() },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = YounesSurface1)
                    ) {
                        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Diversity3, null, tint = YounesCobalt, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("المجتمعات", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            Text("غرف جماعية — اضغط للإنشاء", fontSize = 11.sp, color = YounesMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernMoreScreen(
    account: AuthState.Authenticated,
    onSettings: () -> Unit,
    onOpenSettingsPage: (SettingsPage) -> Unit
) {
    // المزيد: كل الخدمات السيادية - ألوان مقروءة AAA + دعم كل الهواتف
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(YounesMidnight),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("المزيد - الخدمات السيادية", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        item {
            // بطاقة الملف الشخصي وتغيير الصورة والاسم
            Card(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpenSettingsPage(SettingsPage.ACCOUNT) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = YounesSurface1)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val context = LocalContext.current
                    val avatarFile = remember { java.io.File(context.filesDir, "avatar_me.jpg") }
                    val hasAvatar = avatarFile.exists() && avatarFile.length() > 0
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(YounesEmerald, AqyalCyanGlow, AqyalGold))),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasAvatar) {
                            AsyncImage(
                                model = avatarFile,
                                contentDescription = "الصورة الشخصية",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Text(
                                text = account.username.take(1).uppercase(),
                                color = Color(0xFF03120E),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Box(
                                Modifier
                                    .padding(2.dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(YounesPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = "تغيير الصورة",
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = account.username,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        Text(
                            text = "تعديل الصورة الشخصية والمعرف السيادي",
                            fontSize = 12.sp,
                            color = YounesEmerald
                        )
                        Text(
                            text = account.redId,
                            fontSize = 10.sp,
                            color = YounesMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = YounesEmerald)
                }
            }
        }
        
        item {
            // بطاقات الخدمات - كل بند يفتح وجهته الحية (RedDashboard/MoreScreen + DeviceSettingsScreen):
            // الأجهزة←صفحة DEVICES (الجلسات الحية)، التخزين←DATA، المساعدة←ABOUT — بلا شارات ميتة.
            val services = buildList {
                add(Triple("الإعدادات", "الخصوصية والأمان والمظهر", Icons.Default.Settings) to onSettings)
                add(Triple("الأجهزة المرتبطة", "إدارة الجلسات النشطة", Icons.Default.Devices) to { onOpenSettingsPage(SettingsPage.DEVICES) })
                add(Triple("تشخيصات الشبكة والخوادم", "فحص الجودة واكتشاف خوادم RED", Icons.Default.Wifi) to { onOpenSettingsPage(SettingsPage.NETWORK_DIAG) })
                if (account.isAdmin) {
                    add(Triple("لوحة الإدارة والتحكم", "موافقة المستخدمين والإحصائيات الحية", Icons.Default.Security) to { onOpenSettingsPage(SettingsPage.ADMIN_DASHBOARD) })
                }
                add(Triple("التخزين", "الكاش والبيانات والتنزيلات", Icons.Default.Folder) to { onOpenSettingsPage(SettingsPage.DATA) })
                add(Triple("المساعدة", "الدعم وحول التطبيق", Icons.Default.Help) to { onOpenSettingsPage(SettingsPage.ABOUT) })
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                services.forEach { (info, onClick) ->
                    val (title, desc, icon) = info
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onClick as () -> Unit),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = YounesSurface1)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .background(YounesPrimary.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, null, tint = YounesPrimary, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White) // AAA مقروء
                                Text(desc, fontSize = 12.sp, color = YounesMuted) // ثانوي مقروء 6.19:1
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = YounesMuted)
                        }
                    }
                }
            }
        }
        
        item {
            // معلومات النظام - أحدث التقنيات + مزامنة سريعة
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = YounesPrimary.copy(alpha = 0.1f))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = YounesAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("نظام موحد أسطوري", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    }
                    Text("✓ مكالمات ترن وتتصل 9 أنواع 6 مسارات - لا تعارضات", fontSize = 11.sp, color = YounesMuted)
                    Text("✓ مجموعات تنشأ وتظهر كل المميزات", fontSize = 11.sp, color = YounesMuted)
                    Text("✓ بث لا شاشة سوداء + مؤتمرات أفضل من تويتر 100%", fontSize = 11.sp, color = YounesMuted)
                    Text("✓ كل قواعد البيانات مطورة + مزامنة سريعة <2s", fontSize = 11.sp, color = YounesMuted)
                    Text("✓ واجهات أحدث + AAA مقروءة + كل الهواتف + أحدث تقنيات", fontSize = 11.sp, color = YounesMuted)
                }
            }
        }
    }
}

@Composable
fun ModernCallDialerDialog(
    onDismiss: () -> Unit,
    onCall: (String, Boolean) -> Unit
) {
    var redId by remember { mutableStateOf("") }
    var isVideo by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مكالمة جديدة مشفرة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = redId,
                    onValueChange = { redId = YounesId.normalizeInput(it) },
                    label = { Text("معرف يونس RED ID") },
                    placeholder = { Text(YounesId.PLACEHOLDER) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (redId.isNotBlank() && !YounesId.isValid(redId)) {
                    Text(YounesId.ERROR_MESSAGE, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isVideo, onCheckedChange = { isVideo = it })
                    Text("مكالمة فيديو")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCall(redId, isVideo) },
                enabled = YounesId.isValid(redId)
            ) {
                Text(if (isVideo) "فيديو" else "صوتي")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun ModernCreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String?, List<String>) -> Unit,
    contacts: List<com.red.sovereign.contacts.PublicRedProfile>
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء مجموعة مشفرة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المجموعة") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("الوصف (اختياري)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("الأعضاء: ${selected.size}", fontSize = 12.sp, color = YounesMuted)
                // حارس العضو الواحد: قائمة اختيار حقيقية — كانت تعرض العدد فقط (دائمًا 0) بلا طريقة للاختيار.
                if (contacts.isEmpty()) {
                    Text("لا توجد جهات اتصال — أضف صديقًا أولًا", fontSize = 11.sp, color = YounesMuted)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(contacts, key = { it.redId }) { contact ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (selected.contains(contact.redId)) selected.remove(contact.redId)
                                    else selected.add(contact.redId)
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selected.contains(contact.redId),
                                    onCheckedChange = { checked ->
                                        if (checked) selected.add(contact.redId) else selected.remove(contact.redId)
                                    }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(contact.displayName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(contact.redId, fontSize = 11.sp, color = YounesMuted)
                                }
                            }
                        }
                    }
                }
                if (selected.isEmpty()) {
                    Text("اختر عضوًا واحدًا على الأقل", fontSize = 11.sp, color = YounesMuted)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, description.ifBlank { null }, selected.toList()) },
                // حارس العضو الواحد: اسم غير فارغ + عضو واحد على الأقل (كان الاسم وحده).
                enabled = name.isNotBlank() && selected.isNotEmpty()
            ) {
                Text("إنشاء")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
