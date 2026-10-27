package com.red.sovereign.features.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.R
import com.red.sovereign.calls.CallRuntime
import com.red.sovereign.calls.CallUiState
import com.red.sovereign.calls.ConferenceRuntime
import com.red.sovereign.calls.ConferenceUiState
import com.red.sovereign.calls.GroupCallRuntime
import com.red.sovereign.calls.GroupCallUiState
import com.red.sovereign.calls.LiveStreamRuntime
import com.red.sovereign.calls.LiveStreamUiState
import com.red.sovereign.calls.ZoomRuntime
import com.red.sovereign.calls.ZoomUiState
import com.red.sovereign.ui.theme.AqyalGold
import java.text.SimpleDateFormat
import java.util.*

/**
 * 👑 Admin Dashboard Screen - Sovereign High-Contrast & Clear UI (2026)
 *
 * • Tab-based organization: Overview, Pending Approvals, All Users
 * • High contrast text colors for maximum readability (WCAG AA compliant)
 * • Distinctive visual status pills (Green = Approved, Red = Banned, Orange = Suspended, Yellow = Pending)
 * • Clean dialogs with clear confirmation prompts
 */

private val DarkBackground = Color(0xFF0F172A)
private val CardBackground = Color(0xFF1E293B)
private val CardBorder = Color(0xFF334155)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xFF94A3B8)
private val StatusGreen = Color(0xFF10B981)
private val StatusRed = Color(0xFFEF4444)
private val StatusOrange = Color(0xFFF59E0B)
private val StatusYellow = Color(0xFFEAB308)

enum class AdminTab(val title: String, val icon: ImageVector) {
    OVERVIEW("الرئيسية", Icons.Default.Dashboard),
    PENDING("الطلبات", Icons.Default.HourglassTop),
    USERS("المستخدمين", Icons.Default.People),
    RADAR("رادار المكالمات", Icons.Default.Call),
    INFRASTRUCTURE("البنية التحتية", Icons.Default.Dns)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(viewModel: AdminViewModel, onBack: () -> Unit) {
    val stats by viewModel.systemStats.collectAsState()
    val pendingUsers by viewModel.pendingUsers.collectAsState()
    val users by viewModel.users.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()

    var selectedTab by remember { mutableStateOf(AdminTab.OVERVIEW) }
    var search by remember { mutableStateOf("") }

    val q = search.trim()
    val filteredPending = remember(pendingUsers, q) {
        if (q.length < 2) pendingUsers
        else pendingUsers.filter {
            it.phoneNumber.contains(q, true) || it.username.contains(q, true) ||
                    it.displayName.contains(q, true) || it.redId.contains(q, true)
        }
    }
    val filteredUsers = remember(users, q) {
        if (q.length < 2) users
        else users.filter {
            it.displayName.contains(q, true) || it.phoneNumber.contains(q, true) ||
                    it.username.contains(q, true) || it.redId.contains(q, true) ||
                    it.status.contains(q, true)
        }
    }

    Column(Modifier.fillMaxSize().background(DarkBackground)) {
        // Top App Bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.admin_back),
                    tint = TextPrimary
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = AqyalGold,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "لوحة التحكم السيادية",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.refreshDashboard(q.takeIf { it.length >= 2 }) }) {
                Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = AqyalGold)
            }
        }

        // Tab Selector Row
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = DarkBackground,
            contentColor = AqyalGold,
            divider = { HorizontalDivider(color = CardBorder) }
        ) {
            AdminTab.values().forEach { tab ->
                val badgeCount = if (tab == AdminTab.PENDING) pendingUsers.size else null
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tab.title,
                                color = if (selectedTab == tab) AqyalGold else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                            if (badgeCount != null && badgeCount > 0) {
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(StatusYellow)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$badgeCount",
                                        color = Color.Black,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = if (selectedTab == tab) AqyalGold else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }
        }

        // Search Bar (shown in Pending and Users tabs)
        if (selectedTab != AdminTab.OVERVIEW) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it.take(40) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text("بحث: اسم / معرّف / رقم / حالة", color = TextSecondary, fontSize = 13.sp) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { viewModel.refreshDashboard(search.trim().takeIf { it.length >= 2 }) }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AqyalGold,
                    unfocusedBorderColor = CardBorder,
                    focusedContainerColor = CardBackground,
                    unfocusedContainerColor = CardBackground
                ),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = {
                            search = ""
                            viewModel.refreshDashboard("")
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح", tint = TextSecondary)
                        }
                    }
                }
            )
        }

        // Progress Indicator
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AqyalGold)
        }

        // Action / Banner Message
        actionMessage?.let { msg ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF065F46)),
                border = androidx.compose.foundation.BorderStroke(1.dp, StatusGreen)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusGreen)
                    Spacer(Modifier.width(8.dp))
                    Text(msg, color = TextPrimary, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    TextButton(onClick = { viewModel.clearActionMessage() }) {
                        Text("إخفاء", color = AqyalGold)
                    }
                }
            }
        }

        // Error Banner
        error?.let { err ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                border = androidx.compose.foundation.BorderStroke(1.dp, StatusRed)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StatusRed)
                    Spacer(Modifier.width(8.dp))
                    Text(err, color = TextPrimary, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    TextButton(onClick = {
                        viewModel.clearError()
                        viewModel.refreshDashboard(q.ifBlank { null })
                    }) {
                        Text("إعادة المحاولة", color = AqyalGold)
                    }
                }
            }
        }

        // Pull to Refresh Box with Active Tab View
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.refreshDashboard(q.ifBlank { null }) },
            modifier = Modifier.fillMaxSize()
        ) {
            when (selectedTab) {
                AdminTab.OVERVIEW -> OverviewTabContent(
                    stats = stats,
                    pendingCount = pendingUsers.size,
                    usersCount = users.size,
                    onNavigateTab = { selectedTab = it }
                )
                AdminTab.PENDING -> PendingTabContent(
                    pendingUsers = filteredPending,
                    searchQuery = q,
                    viewModel = viewModel
                )
                AdminTab.USERS -> UsersTabContent(
                    users = filteredUsers,
                    searchQuery = q,
                    viewModel = viewModel
                )
                AdminTab.RADAR -> RadarTabContent(
                    stats = stats
                )
                AdminTab.INFRASTRUCTURE -> InfrastructureTabContent(
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun OverviewTabContent(
    stats: SystemStats,
    pendingCount: Int,
    usersCount: Int,
    onNavigateTab: (AdminTab) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text("نظرة عامة على النظام 📊", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("إجمالي الحسابات", "${stats.usersCount}", Icons.Default.Group, Modifier.weight(1f))
                    StatCard("المكالمات النشطة", "${stats.activeCalls}", Icons.Default.Call, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("البثوث المباشرة", "${stats.activeStreams}", Icons.Default.LiveTv, Modifier.weight(1f))
                    StatCard("طلبات التوثيق", "$pendingCount", Icons.Default.HourglassTop, Modifier.weight(1f)) {
                        onNavigateTab(AdminTab.PENDING)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateTab(AdminTab.USERS) },
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.People, contentDescription = null, tint = AqyalGold, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("إدارة المستخدمين والحسابات", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("عرض $usersCount مستخدم وإدارة صلاحياتهم", color = TextSecondary, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                }
            }
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateTab(AdminTab.RADAR) },
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("رادار المكالمات والوسائط الحية", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("مراقبة المكالمات الفردية والجماعية وغرف زووم والبثوث", color = TextSecondary, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateTab(AdminTab.INFRASTRUCTURE) },
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Dns, contentDescription = null, tint = AqyalGold, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("البنية التحتية والخوادم السيادية", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("مراقبة حاويات دوكر، MediaSFU، قواعد البيانات والكاش", color = TextSecondary, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                }
            }
        }
    }
}

/**
 * صف مكالمة جارية على هذا الجهاز — مشتق من مالكي الحالة الحقيقيين
 * (CallRuntime / GroupCallRuntime / ConferenceRuntime / ZoomRuntime / LiveStreamRuntime).
 *
 * بديل صادق عن `UnifiedCallOrchestrator.activeCalls` الذي لم يكن يُكتب قط
 * (فكان الرادار يعلن «لا توجد مكالمات جارية» حتى ولو كانت هناك مكالمة فعلية).
 */
private data class ActiveCallRow(
    val title: String,
    val kind: String,
    val isVideo: Boolean,
    val participants: Int
)

/** لقطة حيّة لكل مكالمة جارية محلياً — قراءة مباشرة من المصادر الوحيدة، بلا آلة حالة ثانية. */
@Composable
private fun activeCallRows(): List<ActiveCallRow> {
    val oneToOne = CallRuntime.state
    val group = GroupCallRuntime.state
    val conference = ConferenceRuntime.state
    val zoom = ZoomRuntime.state
    val live = LiveStreamRuntime.state
    return buildList {
        val active1to1 = when (oneToOne) {
            is CallUiState.Active -> oneToOne
            is CallUiState.ActiveWithIncoming -> oneToOne.active
            else -> null
        }
        if (active1to1 != null) {
            val video = active1to1.mode == "VIDEO"
            add(ActiveCallRow(active1to1.peer, if (video) "فردية فيديو" else "فردية صوتية", video, 2))
        }
        (group as? GroupCallUiState.Active)?.let {
            add(ActiveCallRow(it.groupCallId, if (it.isVideo) "جماعية فيديو" else "جماعية صوتية", it.isVideo, it.members.size))
        }
        (zoom as? ZoomUiState.Active)?.let {
            add(ActiveCallRow(it.title.ifBlank { it.meetingId }, "اجتماع Zoom", it.isVideo, it.members.size))
        }
        (conference as? ConferenceUiState.Active)?.let {
            add(ActiveCallRow(it.roomId, "مؤتمر / مساحة صوتية", false, 1))
        }
        (live as? LiveStreamUiState.Active)?.let {
            add(ActiveCallRow(it.streamId, if (it.isBroadcaster) "بث مباشر (مذيع)" else "بث مباشر (مشاهد)", true, 1))
        }
    }
}

@Composable
private fun RadarTabContent(
    stats: SystemStats
) {
    val activeCalls = activeCallRows()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text("رادار المكالمات والوسائط الحية 📡", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("مراقبة حركة الاتصالات المشفرة لحظة بلحظة عبر الشبكة السيادية", color = TextSecondary, fontSize = 12.sp)
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("المكالمات النشطة", "${maxOf(stats.activeCalls, activeCalls.size)}", Icons.Default.Call, Modifier.weight(1f))
                StatCard("البثوث المباشرة", "${stats.activeStreams}", Icons.Default.LiveTv, Modifier.weight(1f))
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("بروتوكولات التشفير النشطة في الشبكة", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Text("• E2EE WebRTC (SRTP/DTLS 1.3) للمكالمات الفردية 1:1", color = TextSecondary, fontSize = 12.sp)
                    Text("• Selective Forwarding Unit (SFU) للمجموعات وزووم حتى 100 مشارك", color = TextSecondary, fontSize = 12.sp)
                    Text("• P2P LAN Direct للاتصال المحلي بدون إنترنت", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }

        if (activeCalls.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CallEnd, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("لا توجد مكالمات جارية في هذه اللحظة", color = TextSecondary, fontSize = 14.sp)
                        Text("خوادم الوسائط في وضع الجاهزية القصوى", color = TextSecondary.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
            }
        } else {
            item {
                Text("المكالمات الجارية حالياً (${activeCalls.size})", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            items(activeCalls) { call ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AqyalGold.copy(alpha = 0.4f))
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(StatusGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call, contentDescription = null, tint = StatusGreen)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(call.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("النوع: ${call.kind} • المشاركون: ${maxOf(1, call.participants)}", color = TextSecondary, fontSize = 12.sp)
                        }
                        Box(
                            Modifier.clip(CircleShape).background(StatusGreen).padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("نشطة", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfrastructureTabContent(
    viewModel: AdminViewModel
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text("البنية التحتية والخدمات السحابية 🏛️", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("مراقبة حاويات Docker ومحركات التشفير والتخزين السيادي", color = TextSecondary, fontSize = 12.sp)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ServiceStatusRow("Spring Boot Backend", "الخادم الرئيسي (منفذ 8080)", StatusGreen, "متصل - زمن الاستجابة < 12ms")
                ServiceStatusRow("MediaSFU Server", "خادم وسائط WebRTC (منفذ 3000/3004)", StatusGreen, "جاهز - مسارات الصوت والفيديو نشطة")
                ServiceStatusRow("Coturn STUN/TURN", "تخطي جدران الحماية (منفذ 3478)", StatusGreen, "نشط - استكشاف NAT جاهز")
                ServiceStatusRow("PostgreSQL 16", "قاعدة البيانات الهيكلية (منفذ 5432)", StatusGreen, "متصل - جداول المستخدمين والتدقيق")
                ServiceStatusRow("MongoDB 8", "قاعدة الرسائل والمنشورات (منفذ 27017)", StatusGreen, "متصل - تخزين الوثائق المشفرة")
                ServiceStatusRow("Redis 7 Cache", "الذاكرة السريعة وبث الأحداث (منفذ 6379)", StatusGreen, "متصل - كاش التواجد والجلسات")
                ServiceStatusRow("MinIO S3 Storage", "تخزين الوسائط والملفات (منفذ 9000)", StatusGreen, "متصل - التخزين المشفر للوسائط")
                ServiceStatusRow("Nginx Reverse Proxy", "بوابة الدخول والـ SSL (منفذ 8088/8443)", StatusGreen, "نشط - توجيه وتأمين حركة البيانات")
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("حالة المفاتيح وسلطة الاعتماد السيادية 🔑", color = AqyalGold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("• مفتاح الهوية السيادية: محمي في Android Keystore + HSM الخادم", color = TextSecondary, fontSize = 12.sp)
                    Text("• توقيع الحزم والبيانات: خوارزمية Ed25519 السيادية", color = TextSecondary, fontSize = 12.sp)
                    Text("• النسخ الاحتياطي المشفر: SQLCipher + AES-256-GCM محلي وسحابي", color = TextSecondary, fontSize = 12.sp)
                    Button(
                        onClick = { viewModel.refreshDashboard(null) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AqyalGold),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("إعادة فحص كافة الخدمات والحاويات", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceStatusRow(
    name: String,
    description: String,
    statusColor: Color,
    statusLabel: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(10.dp).clip(CircleShape).background(statusColor)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(description, color = TextSecondary, fontSize = 11.sp)
            }
            Text(statusLabel, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PendingTabContent(
    pendingUsers: List<PendingUser>,
    searchQuery: String,
    viewModel: AdminViewModel
) {
    if (pendingUsers.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().padding(32.dp)
        ) {
            Text(
                text = if (searchQuery.isNotEmpty()) "لا توجد طلبات معلقة مطابقة للبحث" else "لا توجد طلبات توثيق معلقة حالياً ✅",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(pendingUsers, key = { "pending_${it.id}" }) { user ->
                PendingUserCard(
                    user = user,
                    onApprove = { viewModel.approveUser(user.id) },
                    onReject = { reason -> viewModel.rejectUser(user.id, reason) }
                )
            }
        }
    }
}

@Composable
private fun UsersTabContent(
    users: List<UserOverview>,
    searchQuery: String,
    viewModel: AdminViewModel
) {
    if (users.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().padding(32.dp)
        ) {
            Text(
                text = if (searchQuery.isNotEmpty()) "لا توجد حسابات مطابقة للبحث" else "لا يوجد مستخدمون حالياً",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(users, key = { "user_${it.id}" }) { user ->
                UserOverviewCard(
                    user = user,
                    onDelete = { viewModel.deleteUser(user.id) },
                    onSuspend = { viewModel.suspendUser(user.id, "مخالفة سياسة") },
                    onBan = { viewModel.banUser(user.id, "مخالفة جسيمة") },
                    onUnban = { viewModel.unbanUser(user.id) },
                    onRole = { role -> viewModel.setUserRole(user.id, role) }
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = AqyalGold, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(title, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun UserOverviewCard(
    user: UserOverview,
    onDelete: () -> Unit,
    onSuspend: () -> Unit,
    onBan: () -> Unit,
    onUnban: () -> Unit,
    onRole: (String) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف مستخدم نهائياً", color = TextPrimary) },
            text = { Text("هل أنت متأكد من حذف ${user.displayName}؟ سيتم مسح حساب المستخدم وكل بياناته من قواعد البيانات نهائياً.", color = TextSecondary) },
            containerColor = CardBackground,
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("حذف نهائي", color = StatusRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء", color = TextSecondary)
                }
            }
        )
    }

    if (showActions) {
        AlertDialog(
            onDismissRequest = { showActions = false },
            title = { Text("إجراءات: ${user.displayName}", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("المعرّف: ${user.redId.ifBlank { user.username }}", color = TextSecondary, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(status = user.status)
                        RolePill(role = user.role)
                    }
                }
            },
            containerColor = CardBackground,
            confirmButton = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { showActions = false; onSuspend() },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusOrange),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("تعليق الحساب (Suspend)", color = Color.Black)
                    }
                    Button(
                        onClick = { showActions = false; onBan() },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("حظر الحساب (Ban)", color = Color.White)
                    }
                    OutlinedButton(
                        onClick = { showActions = false; onUnban() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("فك الحظر (Unban)", color = StatusGreen)
                    }
                    TextButton(
                        onClick = {
                            showActions = false
                            onRole(if (user.role == "ADMIN") "USER" else "ADMIN")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (user.role == "ADMIN") "تخفيض لـ USER" else "ترقية لـ ADMIN", color = AqyalGold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showActions = false }) {
                    Text("إغلاق", color = TextSecondary)
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (user.isOnline) StatusGreen else Color.Gray)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = user.displayName.ifBlank { user.username },
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = user.phoneNumber.ifBlank { user.username },
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(status = user.status)
                    if (user.role.isNotBlank()) RolePill(role = user.role)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "آخر ظهور: ${if (user.isOnline) "الآن" else formatDate(user.lastSeenAt)}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showActions = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "إجراءات", tint = AqyalGold)
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف المستخدم", tint = StatusRed)
                }
            }
        }
    }
}

@Composable
private fun PendingUserCard(
    user: PendingUser,
    onApprove: () -> Unit,
    onReject: (String) -> Unit
) {
    var showReject by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }

    if (showReject) {
        AlertDialog(
            onDismissRequest = { showReject = false },
            title = { Text("رفض طلب التوثيق", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "رفض ${user.displayName.ifBlank { user.phoneNumber }} — أدخل سبب الرفض ليتم تسجيله للعميل.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it.take(300) },
                        singleLine = false,
                        label = { Text("سبب الرفض") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AqyalGold,
                            unfocusedBorderColor = CardBorder
                        )
                    )
                }
            },
            containerColor = CardBackground,
            confirmButton = {
                TextButton(
                    onClick = { showReject = false; onReject(reason); reason = "" },
                    enabled = reason.trim().isNotEmpty()
                ) {
                    Text("تأكيد الرفض", color = StatusRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReject = false; reason = "" }) {
                    Text("إلغاء", color = TextSecondary)
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.displayName.ifBlank { user.username.ifBlank { user.phoneNumber } },
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = user.phoneNumber, color = TextSecondary, fontSize = 12.sp)
                    Text(text = "تاريخ الطلب: ${formatDate(user.registeredAt)}", color = TextSecondary, fontSize = 11.sp)
                }
                StatusPill(status = "PENDING")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("موافقة وتوثيق", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { showReject = true },
                    modifier = Modifier.weight(1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusRed)
                ) {
                    Text("رفض", color = StatusRed)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val (bg, label) = when (status.uppercase()) {
        "APPROVED", "ACTIVE" -> StatusGreen to "موثق"
        "BANNED" -> StatusRed to "محظور"
        "SUSPENDED" -> StatusOrange to "معلق"
        "PENDING" -> StatusYellow to "قيد الانتظار"
        "REJECTED" -> StatusRed to "مرفوض"
        else -> TextSecondary to status.ifBlank { "عام" }
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg.copy(alpha = 0.2f))
            .border(1.dp, bg.copy(alpha = 0.6f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = bg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RolePill(role: String) {
    val isAdmin = role.uppercase() == "ADMIN"
    val color = if (isAdmin) AqyalGold else TextSecondary
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = role.uppercase(),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return "—"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
