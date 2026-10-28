package com.red.sovereign.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/**
 * سجل الأيقونات الدلالي — «اسم واحد = أيقونة واحدة في كل التطبيق».
 *
 * كان نفس المعنى يظهر بثلاث أيقونات مختلفة: `Icons.Filled.Wifi` في شاشة
 * الدخول، `Icons.Rounded.NetworkCheck` في الإعدادات، و`Icons.Default.SignalWifi4Bar`
 * في التشخيص. كما كان خلط بين `Icons.Filled.*` و`Icons.Rounded.*` يعطي
 * سماكات خط مختلفة داخل نفس الشريط. كل الشاشات الجديدة تستخدم هذا السجل؛
 * والشاشات القديمة تُهاجر تدريجياً.
 *
 * القاعدة: أضف any أيقونة جديدة **هنا** فقط، مع وصف محتوى عربي.
 */
object YounesIcons {
    // التنقل
    val Chats = Icons.Rounded.ChatBubble
    val Groups = Icons.Rounded.Groups
    val Calls = Icons.Filled.Call
    val Explore = Icons.Filled.Explore
    val More = Icons.Filled.MoreHoriz
    val Back = Icons.AutoMirrored.Rounded.ArrowBack

    // الحساب والأمان
    val Person = Icons.Rounded.Person
    val PersonAdd = Icons.Rounded.PersonAdd
    val Lock = Icons.Rounded.Lock
    val Shield = Icons.Rounded.Shield
    val Key = Icons.Filled.VpnKey
    val Badge = Icons.Filled.VpnKey

    // الشبكة والخادم
    val Server = Icons.Filled.Dns
    val Wifi = Icons.Filled.Wifi
    val WifiOff = Icons.Filled.WifiOff
    val NetworkCheck = Icons.Filled.NetworkCheck
    val Refresh = Icons.Rounded.Refresh
    val CloudOff = Icons.Filled.WifiOff

    // المكالمات
    val Mic = Icons.Rounded.Mic
    val MicOff = Icons.Rounded.MicOff
    val Camera = Icons.Rounded.Videocam
    val CameraOff = Icons.Rounded.VideocamOff
    val CameraSwitch = Icons.Filled.Cameraswitch
    val CallEnd = Icons.Rounded.CallEnd
    val Speaker = Icons.Filled.VolumeUp
    val Headset = Icons.Rounded.HeadsetMic

    // المراسلة
    val Send = Icons.AutoMirrored.Filled.Send
    val Attach = Icons.Filled.AttachFile
    val Emoji = Icons.Filled.EmojiEmotions
    val Sticker = Icons.Filled.Image
    val Reply = Icons.AutoMirrored.Rounded.Reply
    val Copy = Icons.Filled.ContentCopy
    val Delete = Icons.Filled.Delete
    val Edit = Icons.Filled.Edit
    val Star = Icons.Filled.Star
    val Check = Icons.Rounded.Check

    // الحالة
    val Success = Icons.Rounded.CheckCircle
    val Error = Icons.Rounded.Error
    val Warning = Icons.Filled.Warning
    val Info = Icons.Rounded.Info
    val Search = Icons.Filled.Search
    val Settings = Icons.Filled.Settings
    val Language = Icons.Filled.Language
    val Visibility = Icons.Rounded.Visibility
    val VisibilityOff = Icons.Rounded.VisibilityOff

    // المحتوى الاجتماعي
    val Story = Icons.Rounded.Star
    val Post = Icons.Filled.DynamicFeed
    val Poll = Icons.Filled.Poll
    val Live = Icons.Filled.LiveTv
    val Broadcast = Icons.Filled.Campaign
}

/**
 * أيقونة موحّدة: مقاس من [YounesIconSize]، لون من الثيم، ووصف محتوى
 * إلزامي (null فقط للأيقونات الزخرفية التي يتبعها نص).
 */
@Composable
fun YounesIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = YounesIconSize.large,
    tint: Color = LocalContentColor.current
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier.then(Modifier.size(size)),
        tint = tint
    )
}

/** لون الأيقونة الدلالي حسب الحالة — مركزي حتى لا تتعدد درجات الأحمر. */
@Composable
fun semanticIconTint(state: YounesIconState): Color = when (state) {
    YounesIconState.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    YounesIconState.ACTIVE -> MaterialTheme.colorScheme.primary
    YounesIconState.SUCCESS -> SovereignColors.Success
    YounesIconState.WARNING -> SovereignColors.Gold
    YounesIconState.DANGER -> MaterialTheme.colorScheme.error
}

enum class YounesIconState { NEUTRAL, ACTIVE, SUCCESS, WARNING, DANGER }
