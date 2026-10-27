package com.red.server.websocket

import tools.jackson.databind.ObjectMapper
import com.red.server.calls.CallHistoryService
import com.red.server.calls.CallRoute
import com.red.server.calls.CallStatus
import com.red.server.calls.ActiveCallRegistry
import com.red.server.calls.CallType
import com.red.server.calls.RoomAliasService
import com.red.server.calls.RoomSeparationPolicy
import com.red.server.services.NotificationService
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Authenticated WebRTC signaling router with multi-device ringing and offline offer mailbox. */
@Component
class CallWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val history: CallHistoryService,
    private val notifications: NotificationService,
    private val redisTemplate: org.springframework.data.redis.core.StringRedisTemplate,
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private val roomAliases: RoomAliasService? = null,
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private val activeCalls: ActiveCallRegistry? = null
) : TextWebSocketHandler() {
    private val sessions = ConcurrentHashMap<String, CopyOnWriteArrayList<WebSocketSession>>()
    /**
     * صندوق الإشارات المعلّقة **في ذاكرة العملية** — كان معرّفاً ولا يُكتب فيه أبداً
     * بينما كل التخزين يمرّ بـRedis، فإذا غاب Redis أو تعطّل صار كل رنين وإشارة
     * تفاوض تُسقَط بصمت (runCatching يبتلع الفشل) ⇒ مكالمة لا تُرنّ بلا أي خطأ ظاهر.
     * الآن هو شبكة الأمان: يُملأ حين يفشل/يغيب Redis، ويُفرَّغ عند اتصال المستلم.
     */
    private val pending = ConcurrentHashMap<String, CopyOnWriteArrayList<PendingCallSignal>>()
    private val groupRooms = ConcurrentHashMap<String, GroupCallRoom>()

    // FIX: Split-Brain Architecture Fix. Fetches room state from Redis if not found in local memory.
    private fun getRoomSafe(roomId: String): GroupCallRoom? {
        val local = groupRooms[roomId]
        if (local != null) return local
        try {
            val json = redisTemplate.opsForValue().get("call_room:$roomId")
            if (json != null) {
                val room = objectMapper.readValue(json, GroupCallRoom::class.java)
                groupRooms[roomId] = room // Cache locally
                return room
            }
        } catch (e: Exception) {
            // Fallback
        }
        return null
    }

    private fun saveRoomSafe(roomId: String, room: GroupCallRoom) {
        groupRooms[roomId] = room
        try {
            redisTemplate.opsForValue().set("call_room:$roomId", objectMapper.writeValueAsString(room), java.time.Duration.ofHours(24))
        } catch (e: Exception) {}
    }

    private fun removeRoomSafe(roomId: String): GroupCallRoom? {
        val room = groupRooms.remove(roomId)
        try {
            redisTemplate.delete("call_room:$roomId")
        } catch (e: Exception) {}
        return room
    }
    /** المعدل: حارس نافذة ثابتة مشترك بدل ArrayDeque يدوية بلا تنظيف (كانت تتسرب بلا حد). */
    private val rateLimiter = WebSocketRateLimiter(maxMessages = 120, windowMillis = 60_000)
    /** عروض v1 المخزنة للسحب عند اتصال المستلم — نفس صندوق pending بغلاف SDP. */
    private val callOffers = ConcurrentHashMap<String, StoredCallOffer>()

    public override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val source = session.attributes["userId"] as? String
        if (source.isNullOrBlank()) {
            sendError(session, "", "UNAUTHENTICATED")
            return
        }
        if (!checkRateLimit(session.id)) {
            sendError(session, "", "RATE_LIMITED")
            return
        }
        val signal: IncomingCallSignal = try {
            objectMapper.readValue(message.payload, IncomingCallSignal::class.java)
        } catch (_: Exception) {
            sendError(session, "", "INVALID_PAYLOAD")
            return
        }
        try {
            handleSignal(session, source, signal)
        } catch (e: Exception) {
            // إطار ERROR فقط — الجلسة تبقى مفتوحة أبداً.
            sendError(session, signal.callId?.trim().orEmpty(), e.message?.takeIf { it.isNotBlank() } ?: "INTERNAL_ERROR")
        }
    }

    private fun handleSignal(session: WebSocketSession, source: String, signal: IncomingCallSignal) {
        val type = signal.type.uppercase()
        when (type) {
            // دعوة مكالمة جماعية: targetUserId فارغ والقائمة في inviteeIds — يُرن لكل مدعو
            "GROUP_CALL_INVITE" -> {
                val rawId = signal.callId?.trim().orEmpty()
                if (rawId.isBlank()) { sendError(session, "", "callId is required"); return }
                val groupCallId = resolveRoom(rawId)
                if (!ROOM_ID_REGEX.matches(groupCallId)) { sendError(session, rawId, "INVALID_ROOM_ID"); return }
                // حد واتساب: 32 مشاركاً كحد أقصى — كان الخادم يقبل عدداً غير محدود.
                val invitees = signal.inviteeIds.filter { it.isNotBlank() && it != source }.take(MAX_GROUP_CALL_MEMBERS)
                if (invitees.isEmpty()) { sendError(session, groupCallId, "inviteeIds is required"); return }
                saveRoomSafe(groupCallId, GroupCallRoom(host = source, members = invitees.toMutableList()))
                val payload = signal.payload + ("hostName" to (signal.payload["hostName"] ?: ""))
                invitees.forEach { invitee ->
                    val outbound = OutgoingCallSignal(groupCallId, source, invitee, type, signal.mode.uppercase(), payload)
                    val json = objectMapper.writeValueAsString(outbound)
                    val targets = liveSessions(invitee)
                    // الرنين/التخزين فقط إن لم يكن المدعو متصلاً بأي مكان — وإلا فالبث يوصله.
                    if (targets.isEmpty() && !isOnlineOnAnotherInstance(invitee)) {
                        enqueue(invitee, outbound)
                        notifications.sendVoipPushNotification(invitee, source, groupCallId, signal.mode)
                    } else {
                        targets.forEach { target -> target.sendSafe(TextMessage(json)) }
                    }
                    publishToPeerInstances(json)
                }
                session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to groupCallId))))
                return
            }

            // ردود الأعضاء إلى المضيف: ACCEPT/DECLINE جوابٌ على الدعوة
            // فوجهته المضيف طبعًا.
            "GROUP_CALL_ACCEPT", "GROUP_CALL_DECLINE" -> {
                val groupCallId = validGroupRoomOrError(session, signal.callId) ?: return
                val validId = groupCallId.takeIf { it.isNotBlank() } ?: signal.callId?.trim().orEmpty()
                val room = getRoomSafe(validId)
                // Kicked members rejoining (stale invite / message tap): bounce them out cleanly.
                if (type == "GROUP_CALL_ACCEPT" && room != null && room.kicked.any { it.equals(source, ignoreCase = true) }) {
                    val bounce = OutgoingCallSignal(groupCallId, room.host, source, "GROUP_CALL_END", signal.mode.uppercase(), mapOf("reason" to "kicked"))
                    session.sendSafe(TextMessage(objectMapper.writeValueAsString(bounce)))
                    return
                }
                // وجهة صريحة إن أرسلها العميل، وإلا المضيف، وإلا المصدر نفسه.
                val hostId = signal.targetUserId.takeIf { it.isNotBlank() } ?: room?.host ?: source
                val outbound = OutgoingCallSignal(groupCallId, source, hostId, type, signal.mode.uppercase(), signal.payload + ("memberStatus" to signal.memberStatus.orEmpty()))
                val json = objectMapper.writeValueAsString(outbound)
                val targets = liveSessions(hostId)
                if (targets.isEmpty() && !isOnlineOnAnotherInstance(hostId)) {
                    enqueue(hostId, outbound)
                } else {
                    targets.forEach { target -> target.sendSafe(TextMessage(json)) }
                }
                publishToPeerInstances(json)
                session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to groupCallId))))
                return
            }

            // ═══ GROUP_CALL_STATUS — تحديث سجل الأعضاء، لا رسالة للمضيف وحده ═══
            //
            // ⚠️ كان هذا مدموجًا مع ACCEPT/DECLINE فيُوجَّه دائمًا إلى
            // `room.host`. وذلك يكسر أهم مُنتِج للإشارة: **المضيف نفسه**.
            // GroupCallService.kt:224-232 broadcasts memberStatus=no_answer (ring timeout)
            // لكل عضو لم يُجب عند انتهاء مهلة الرنين (45s)، وبما أن المرسل
            // هو المضيف فإن `hostId == source` ⇒ ترتد الإشارة إلى المضيف
            // نفسه ولا يعلم أي عضو أن الرنين انتهى، فتبقى بطاقات الأعضاء
            // على RINGING أبدًا في واجهات البقية.
            //
            // ودلالة الإشارة أصلًا «حالة عضو تغيّرت» — معلومة تخصّ كل من في
            // الغرفة لا المضيف فقط، خصوصًا في المكالمة الشبكية (mesh) حيث
            // يبني كل عضو اتصالًا مباشرًا بالبقية ويحتاج سجلًا متطابقًا.
            //
            // الترتيب: وجهة صريحة إن وُجدت، ثم كل مشاركي الغرفة عدا المصدر،
            // ثم المضيف كسقوط أخير حين لا تكون الغرفة معروفة للخادم (مثل
            // إعادة تشغيله وسط مكالمة).
            "GROUP_CALL_STATUS" -> {
                val groupCallId = validGroupRoomOrError(session, signal.callId) ?: return
                val validId = groupCallId.takeIf { it.isNotBlank() } ?: signal.callId?.trim().orEmpty()
                val room = getRoomSafe(validId)
                val recipients: List<String> = when {
                    signal.targetUserId.isNotBlank() -> listOf(signal.targetUserId)
                    room != null -> (room.members + room.host)
                        .filter { it.isNotBlank() && it != source }
                        .distinct()
                    else -> emptyList()
                }.ifEmpty { listOf(source) }

                val enriched = signal.payload + ("memberStatus" to signal.memberStatus.orEmpty())
                recipients.forEach { recipient ->
                    val outbound = OutgoingCallSignal(groupCallId, source, recipient, type, signal.mode.uppercase(), enriched)
                    val json = objectMapper.writeValueAsString(outbound)
                    val targets = liveSessions(recipient)
                    if (targets.isEmpty() && !isOnlineOnAnotherInstance(recipient)) {
                        enqueue(recipient, outbound)
                    } else {
                        targets.forEach { target -> target.sendSafe(TextMessage(json)) }
                    }
                    publishToPeerInstances(json)
                }
                session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to groupCallId))))
                return
            }
            "GROUP_CALL_END" -> {
                val groupCallId = validGroupRoomOrError(session, signal.callId) ?: return
                val validId = groupCallId.takeIf { it.isNotBlank() } ?: signal.callId?.trim().orEmpty()
                val room = removeRoomSafe(validId)
                val targets = (room?.members ?: emptyList()) + room?.host
                dropPending(groupCallId)
                targets.filterNotNull().filter { it.isNotBlank() && it != source }.forEach { memberId ->
                    val outbound = OutgoingCallSignal(groupCallId, source, memberId, type, signal.mode.uppercase(), signal.payload)
                    val memberTargets = liveSessions(memberId)
                    val json = objectMapper.writeValueAsString(outbound)
                    if (memberTargets.isEmpty() && !isOnlineOnAnotherInstance(memberId)) enqueue(memberId, outbound)
                    else memberTargets.forEach { t -> t.sendSafe(TextMessage(json)) }
                    publishToPeerInstances(json)
                }
                session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to groupCallId))))
                return
            }
            // كتم الكل — المضيف فقط، يُبث لكل الأعضاء (كان يُسقط: لا targetUserId فيُرفض).
            "GROUP_CALL_MUTE_ALL" -> {
                val groupCallId = resolveRoom(requireCallId(signal))
                val validId = groupCallId.takeIf { it.isNotBlank() } ?: signal.callId?.trim().orEmpty()
                val room = getRoomSafe(validId)
                if (room == null || !room.host.equals(source, ignoreCase = true)) {
                    session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to groupCallId))))
                    return
                }
                (room.members + room.host)
                    .filter { it.isNotBlank() && !it.equals(source, ignoreCase = true) }
                    .distinct()
                    .forEach { memberId ->
                        val outbound = OutgoingCallSignal(groupCallId, source, memberId, type, signal.mode.uppercase(), signal.payload)
                        val memberTargets = liveSessions(memberId)
                    val json = objectMapper.writeValueAsString(outbound)
                    if (memberTargets.isEmpty() && !isOnlineOnAnotherInstance(memberId)) enqueue(memberId, outbound)
                    else memberTargets.forEach { t -> t.sendSafe(TextMessage(json)) }
                    publishToPeerInstances(json)
                    }
                session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to groupCallId))))
                return
            }
            // طرد عضو — المضيف فقط: يُحذف من الغرفة + يُمنع من العودة + يُبث للجميع.
            "GROUP_CALL_KICK" -> {
                val groupCallId = resolveRoom(requireCallId(signal))
                val room = groupRooms[groupCallId] ?: groupRooms[signal.callId?.trim().orEmpty()]
                val victim = (signal.payload["memberId"] as? String).orEmpty()
                if (room == null || !room.host.equals(source, ignoreCase = true) || victim.isBlank()
                    || victim.equals(source, ignoreCase = true)
                ) {
                    session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to groupCallId))))
                    return
                }
                room.members.removeIf { it.equals(victim, ignoreCase = true) }
                room.kicked.add(victim)
                ((room.members + room.host + victim).filter { it.isNotBlank() }.distinct()).forEach { memberId ->
                    val outbound = OutgoingCallSignal(groupCallId, source, memberId, type, signal.mode.uppercase(), signal.payload)
                    val memberTargets = liveSessions(memberId)
                    val json = objectMapper.writeValueAsString(outbound)
                    if (memberTargets.isEmpty() && !isOnlineOnAnotherInstance(memberId)) enqueue(memberId, outbound)
                    else memberTargets.forEach { t -> t.sendSafe(TextMessage(json)) }
                    publishToPeerInstances(json)
                }
                session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to groupCallId))))
                return
            }
            // كتم عضو واحد — المضيف فقط: يُوجَّه للعضو نفسه (+ المضيف يعرف ضمنياً).
            "GROUP_CALL_MUTE_MEMBER" -> {
                val groupCallId = resolveRoom(requireCallId(signal))
                val room = groupRooms[groupCallId] ?: groupRooms[signal.callId?.trim().orEmpty()]
                val victim = (signal.payload["memberId"] as? String).orEmpty()
                if (room == null || !room.host.equals(source, ignoreCase = true) || victim.isBlank()
                    || victim.equals(source, ignoreCase = true)
                ) {
                    session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to groupCallId))))
                    return
                }
                val outbound = OutgoingCallSignal(groupCallId, source, victim, type, signal.mode.uppercase(), signal.payload)
                val memberTargets = liveSessions(victim)
                val json = objectMapper.writeValueAsString(outbound)
                if (memberTargets.isEmpty() && !isOnlineOnAnotherInstance(victim)) enqueue(victim, outbound)
                else memberTargets.forEach { t -> t.sendSafe(TextMessage(json)) }
                publishToPeerInstances(json)
                session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to groupCallId))))
                return
            }
        }

        // Group-call media/signaling (mesh) reuses the 1:1 envelope but carries the group call id
        // as `callId`. Relay those to the room members instead of creating a bogus 1:1 call record
        // (a shared callId could otherwise collide and abort the frame).
        val rawCallId = signal.callId?.trim().orEmpty()
        val groupRoom = if (rawCallId.isNotBlank()) {
            groupRooms[resolveRoom(rawCallId)] ?: groupRooms[rawCallId]
        } else null
        if (groupRoom != null) {
            val recipients = signal.targetUserId.takeIf { it.isNotBlank() && it != source }
                ?.let { listOf(it) }
                ?: (groupRoom.members + groupRoom.host)
                    .filter { it.isNotBlank() && !it.equals(source, ignoreCase = true) }
                    .distinct()
            recipients.forEach { recipient ->
                val outbound = OutgoingCallSignal(rawCallId, source, recipient, type, signal.mode.uppercase(), signal.payload)
                val targets = liveSessions(recipient)
                if (targets.isNotEmpty()) {
                    val json = objectMapper.writeValueAsString(outbound)
                    targets.forEach { target -> runCatching { target.sendMessage(TextMessage(json)) } }
                }
            }
            session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to rawCallId))))
            return
        }
        if (signal.targetUserId.isBlank()) {
            session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf(
                "type" to "ERROR",
                "callId" to rawCallId,
                "payload" to mapOf("error" to "targetUserId is required")
            ))))
            return
        }
        if (signal.targetUserId.equals(source, ignoreCase = true)) {
            session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf(
                "type" to "ERROR",
                "callId" to rawCallId,
                "payload" to mapOf("error" to "Cannot call the same RED ID")
            ))))
            return
        }
        val callId = when (type) {
            "OFFER" -> {
                // BUSY: if the callee is already in an established call, answer BUSY instead of
                // ringing. The registry is only populated once a call is answered.
                val busy = activeCalls?.let {
                    it.isInCall(signal.targetUserId) && !it.isActiveCall(rawCallId)
                } == true
                if (busy) {
                    val busySignal = OutgoingCallSignal(rawCallId, signal.targetUserId, source, "BUSY", signal.mode.uppercase(), emptyMap())
                    session.sendSafe(TextMessage(objectMapper.writeValueAsString(busySignal)))
                    session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to rawCallId))))
                    return
                }
                history.start(source, signal.targetUserId, signal.targetUserId,
                    callTypeForMode(signal.mode), CallRoute.RED, signal.callId).id
            }
            "ANSWER" -> requireCallId(signal).also {
                history.answer(it, source)
                activeCalls?.register(it, listOf(source, signal.targetUserId))
            }
            "END" -> requireCallId(signal).also {
                history.end(it, source)
                activeCalls?.unregister(it)
            }
            "ICE", "HOLD", "RESUME", "RENEGOTIATE", "CALL_REACTION", "CALL_RAISE_HAND" -> requireCallId(signal)
            "REJECT" -> requireCallId(signal).also { id ->
                val doc = runCatching { history.findById(id) }.getOrNull()
                if (doc != null && doc.status == CallStatus.RINGING && doc.targetId == source) {
                    history.rejected(id, source)
                } else {
                    history.end(id, source)
                }
                activeCalls?.unregister(id)
            }
            "CONFERENCE_INVITE", "LIVE_INVITE" -> requireCallId(signal)
            else -> throw IllegalArgumentException("Unsupported call signal type")
        }

        val outbound = OutgoingCallSignal(callId, source, signal.targetUserId, type, signal.mode.uppercase(), signal.payload)
        val json = objectMapper.writeValueAsString(outbound)

        // 1) التسليم المحلي أولاً: جلسة حيّة على هذه النسخة لا تحتاج Redis إطلاقاً.
        //    كان التسليم كله مؤجَّلاً إلى قناة Redis، فإن غاب Redis أو تعطّل توقف
        //    الرنين والتفاوض (RENEGOTIATE/ICE/إجابة) معاً ولا يصل إطار واحد.
        val liveTargets = liveSessions(signal.targetUserId)
        liveTargets.forEach { target -> target.sendSafe(TextMessage(json)) }

        // 2) بقية النسخ (اختياري): فشل النشر لا يمسّ ما سُلّم محلياً.
        publishToPeerInstances(json)

        // 3) الإسقاط أولاً ثم التصفير: إشارة نهائية (ANSWER/REJECT/END) لنظير
        //    غائب يجب أن تبقى في صندوقه — العكس كان يُصفِّرها ثم يحذفها فوراً.
        if (type in TERMINAL_TYPES) dropPending(callId)
        if (liveTargets.isEmpty()) enqueue(signal.targetUserId, outbound)

        // 4) Push للرنين فقط حين لا جلسة محلية ولا جلسة على نسخة أخرى.
        if (type == "OFFER" && liveTargets.isEmpty() && !isOnlineOnAnotherInstance(signal.targetUserId)) {
            notifications.sendVoipPushNotification(signal.targetUserId, source, callId, signal.mode)
            session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "RINGING_PUSH_SENT", "callId" to callId))))
        }
        session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to callId))))

        // Once one device answers/rejects/ends, stop the ringing state on the user's other devices.
        // (بلا dropPending هنا: الإسقاط تم في خطوة 3 قبل التصفير، وتكراره هنا
        // كان يحذف الإشارة النهائية المُصفَّرة للنظير الغائب فوراً.)
        if (type in TERMINAL_TYPES) {
            val cancelType = if (type == "ANSWER") "CANCELLED" else type
            val cancel = objectMapper.writeValueAsString(mapOf("type" to cancelType, "callId" to callId, "sourceUserId" to source))
            // الإلغاء يذهب إلى **جلسات المُرسل الأخرى** (كان `targets` غير معرّف هنا ⇒ 5 أخطاء ترجمة).
            liveSessions(source).filter { it.id != session.id }.forEach { target -> runCatching { target.sendMessage(TextMessage(cancel)) } }
        }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val redId = session.attributes["userId"] as? String ?: return
        val list = sessions.computeIfAbsent(redId) { CopyOnWriteArrayList() }
        list.removeIf { !it.isOpen }
        list.add(session)
        // حضور: مستخدم مكالمات WS متصل فعلاً — يُسجَّل في نفس بنية الحضور
        // الحية حتى يراه كشف online (isOnlineOnAnotherInstance) والمتصلون بصدق.
        // (كان غائباً: المتصل عبر /ws/calls فقط يُحسب offline دائماً عبر النسخ.)
        runCatching {
            val now = System.currentTimeMillis().toDouble()
            redisTemplate.opsForZSet().add("red:presence:index", redId, now)
            redisTemplate.opsForSet().add(ONLINE_SET_KEY, redId)
        }
        flushPending(redId, session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        // إغلاق: تنظيف كل حالة الجلسة بلا رمي — الجلسة الميتة لا تُبقي رنيناً ولا معدلاً.
        runCatching { rateLimiter.remove(session.id) }
        val redId = session.attributes["userId"] as? String
        var lastSessionClosed = false
        if (redId == null) {
            sessions.values.forEach { it.removeIf { candidate -> candidate.id == session.id } }
        } else {
            sessions.computeIfPresent(redId) { _, list ->
                list.removeIf { it.id == session.id }
                list.takeIf { it.isNotEmpty() }
            }
            lastSessionClosed = sessions[redId].isNullOrEmpty()
        }
        // متمم الحضور أعلاه: آخر جلسة تُغلق تُخرجه من المتصلين.
        if (redId != null && lastSessionClosed) {
            runCatching { redisTemplate.opsForZSet().remove("red:presence:index", redId) }
            runCatching { redisTemplate.opsForSet().remove(ONLINE_SET_KEY, redId) }
        }
        // P0: تنظيف العضوية الجماعية عند انقطاع الاتصال
        if (redId != null) handleGroupCallDisconnect(redId)
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        // النقل المكسور يُعامل كإغلاق: تنظيف فقط، بلا بثّ وبلا رمي.
        runCatching { afterConnectionClosed(session, CloseStatus.SERVER_ERROR) }
    }

    /** المعدل: 120 إطار/دقيقة لكل جلسة (مفتاح session.id ليتطابق مع remove عند الإغلاق — كان source فيتسرب). */
    private fun checkRateLimit(sessionId: String): Boolean = rateLimiter.tryAcquire(sessionId)

    /** خطأ موحد بلا إغلاق: الجلسة تبقى مفتوحة دائماً لأخطاء الأعمال. */
    private fun sendError(session: WebSocketSession, callId: String, code: String) {
        val json = runCatching {
            objectMapper.writeValueAsString(mapOf("type" to "ERROR", "callId" to callId, "payload" to mapOf("error" to code)))
        }.getOrNull() ?: return
        session.sendSafe(TextMessage(json))
    }

    /**
     * تنظيف العضوية الجماعية عند انقطاع الاتصال.
     *
     * الغرفة مخزنة بقيم غير قابلة للتغيير (GroupCallRoom مع List)، فالتحديث
     * يكون بنسخة جديدة عبر copy — لا تعديل مباشر. وroomId هو مفتاح الغرفة
     * (groupCallId) نفسه لا اشتقاق هش من الحقول.
     */
    private fun handleGroupCallDisconnect(redId: String) {
        groupRooms.forEach { (streamId, room) ->
            if (redId != room.host && redId !in room.members) return@forEach
            val remaining = room.members.filter { it != redId }.toMutableList()
            if (room.host == redId) {
                // المضيف انقطع: إنهاء للبقية وإسقاط الغرفة كاملة.
                groupRooms.remove(streamId)
                val endJson = objectMapper.writeValueAsString(
                    mapOf("type" to "GROUP_CALL_END", "roomId" to streamId, "payload" to mapOf("userId" to redId))
                )
                remaining.filter { it.isNotBlank() }.forEach { memberId ->
                    liveSessions(memberId).forEach { session -> session.sendSafe(TextMessage(endJson)) }
                }
            } else {
                // عضو عادي: تحديث الغرفة وإشعار البقية بالمغادرة.
                groupRooms[streamId] = room.copy(members = remaining)
                val leftJson = objectMapper.writeValueAsString(
                    mapOf("type" to "PARTICIPANT_LEFT", "roomId" to streamId, "userId" to redId)
                )
                (remaining + room.host).filter { it.isNotBlank() }.distinct().forEach { memberId ->
                    liveSessions(memberId).forEach { session -> session.sendSafe(TextMessage(leftJson)) }
                }
            }
        }
    }

    private fun liveSessions(redId: String) = sessions[redId]?.filter(WebSocketSession::isOpen).orEmpty()

    /**
     * تخزين إشارة لمستلم غير متصل محلياً.
     *
     * الترتيب: Redis أولاً (ليصل الصندوق من أي نسخة خلف موازن الحِمل)، فإن تعذّر
     * وصوله أو رجع `null` سقطنا إلى صندوق ذاكرة العملية — لا تُفقد الإشارة أبداً،
     * ولا تُخزَّن مرتين (مسار واحد فقط يكتب).
     */
    private fun enqueue(target: String, signal: OutgoingCallSignal) {
        if (target.isBlank()) return
        val json = runCatching { objectMapper.writeValueAsString(signal) }.getOrNull() ?: return
        val storedInRedis = runCatching {
            val key = pendingKey(target)
            val list = redisTemplate.opsForList() ?: return@runCatching false
            list.rightPush(key, json)
            redisTemplate.expire(key, PENDING_TTL_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            list.trim(key, -MAX_PENDING_PER_USER.toLong(), -1L)
            true
        }.getOrDefault(false)
        if (!storedInRedis) stashInMemory(target, PendingCallSignal(json, Instant.now().plusSeconds(PENDING_TTL_SECONDS), signal.callId, signal.type))
    }

    /** صندوق ذاكرة العملية: TTL + سقف لكل مستخدم — لا ينمو بلا حد إن غاب Redis طويلاً. */
    private fun stashInMemory(target: String, item: PendingCallSignal) {
        val now = Instant.now()
        val list = pending.computeIfAbsent(target) { CopyOnWriteArrayList() }
        list.removeIf { it.expiresAt.isBefore(now) }
        list.add(item)
        while (list.size > MAX_PENDING_PER_USER) list.removeAt(0)
    }

    /**
     * تفريغ صناديق المستلم عند اتصال جلسة جديدة: Redis ثم ذاكرة العملية ثم بريد
     * `call:mailbox` — كل مصدر في `runCatching` مستقل حتى لا يُسقط فشل مصدر
     * واحد بقية الصناديق (كان كل شيء في كتلة واحدة: أول استثناء يُهمل الباقي).
     */
    private fun flushPending(redId: String, session: WebSocketSession) {
        runCatching {
            val key = pendingKey(redId)
            val list = redisTemplate.opsForList() ?: return@runCatching
            list.range(key, 0, -1)?.forEach { json -> session.sendSafe(TextMessage(json)) }
            redisTemplate.delete(key)
        }
        drainMemoryPending(redId, session)
        runCatching {
            val mailboxKey = "call:mailbox:$redId"
            val list = redisTemplate.opsForList() ?: return@runCatching
            list.range(mailboxKey, 0, -1)?.forEach { msgStr -> forwardMailboxEntry(redId, session, msgStr) }
            redisTemplate.delete(mailboxKey)
        }
    }

    /** إشارة واحدة من صندوق ذاكرة العملية — ثم يُسقط الصندوق إن صار فارغاً. */
    private fun drainMemoryPending(redId: String, session: WebSocketSession) {
        val mailbox = pending.remove(redId) ?: return
        val now = Instant.now()
        mailbox.filter { it.expiresAt.isAfter(now) }.forEach { item -> session.sendSafe(TextMessage(item.json)) }
    }

    /** سطر بريد واحد: تلف سطر لا يُسقط بقية الصندوق. */
    private fun forwardMailboxEntry(redId: String, session: WebSocketSession, msgStr: String) {
        runCatching {
            val map = objectMapper.readValue(msgStr, Map::class.java)
            val outbound = OutgoingCallSignal(
                callId = map["callId"] as String,
                sourceUserId = map["sourceId"] as String,
                targetUserId = redId,
                type = map["type"] as String,
                mode = map["mode"] as String,
                payload = (map["payload"] as? Map<String, Any?>) ?: emptyMap()
            )
            session.sendSafe(TextMessage(objectMapper.writeValueAsString(outbound)))
        }
    }

    private fun pendingKey(redId: String) = "red:call:pending:$redId"

    /** قناة توزيع الإشارات بين نسخ الخادم. */
    private fun publishToPeerInstances(json: String) {
        runCatching { redisTemplate.convertAndSend(CALL_SIGNAL_CHANNEL, json) }
    }

    /** هل المستلم متصل على نسخة أخرى؟ غياب Redis ⇒ «لا أعرف» = غير متصل (فلا يُسقط الرنين). */
    private fun isOnlineOnAnotherInstance(redId: String): Boolean =
        redId.isNotBlank() && runCatching {
            redisTemplate.opsForSet()?.isMember(ONLINE_SET_KEY, redId) == true
        }.getOrDefault(false)

    /**
     * إسقاط الإشارات المعلّقة لمكالمة انتهت — لا تُسلَّم عند إعادة اتصال المستلم.
     *
     * إنقاذ من تلف بنيوي (2026-09-26): كان جسم الدالة قد أُفرغ ونُقل سطره الأخير
     * خارجها، مع دالة `handleRedisSignal` مكرّرة مرتين وقوسين زائدين `} }` أغلقا
     * الصفّ مبكراً ⇒ 118 خطأ ترجمة (كل ما بعد السطر صار خارج نطاق الصف).
     * الاسترجاع يحفظ السلوك المقصود: إزالة إشارات هذا `callId` ثم تنظيف الصناديق الفارغة.
     */
    private fun dropPending(callId: String) {
        pending.entries.removeIf { (_, queued) ->
            queued.removeIf { it.callId == callId }
            queued.isEmpty()
        }
    }

    /** بثّ إشارة قادمة من قناة Redis إلى كل جلسات المستلم الحيّة (+ إلغاء عند إشارة نهائية). */
    fun handleRedisSignal(json: String) {
        val signal = runCatching { objectMapper.readValue(json, OutgoingCallSignal::class.java) }.getOrNull() ?: return
        val targets = liveSessions(signal.targetUserId)
        targets.forEach { target -> runCatching { target.sendMessage(TextMessage(json)) } }
        if (signal.type in TERMINAL_TYPES) {
            val cancelType = if (signal.type == "ANSWER") "CANCELLED" else signal.type
            val cancel = objectMapper.writeValueAsString(mapOf("type" to cancelType, "callId" to signal.callId, "sourceUserId" to signal.sourceUserId))
            targets.forEach { target -> runCatching { target.sendMessage(TextMessage(cancel)) } }
        }
    }

    /** Deliver a conference/live invite to a RED ID: live socket if present, else 60s mailbox. */
    fun deliverInvite(targetRedId: String, type: String, roomId: String, sourceRedId: String, mode: String, payload: Map<String, Any?> = emptyMap()) {
        val outbound = OutgoingCallSignal(roomId, sourceRedId, targetRedId, type.uppercase(), mode.uppercase(), payload)
        val targets = liveSessions(targetRedId)
        if (targets.isEmpty()) {
            enqueue(targetRedId, outbound)
            notifications.sendVoipPushNotification(targetRedId, sourceRedId, roomId, mode)
            return
        }
        val json = objectMapper.writeValueAsString(outbound)
        targets.forEach { target -> runCatching { target.sendMessage(TextMessage(json)) } }
    }

    /**
     * نسخة REST من تدفق GROUP_CALL_INVITE أعلاه (handleTextMessage س 34-53):
     * تسجّل الغرفة ثم ترنّ لكل مدعو عبر deliverInvite (سوكت حي أو صندوق 60s + push).
     */
    fun deliverGroupCallInvite(
        groupCallId: String,
        hostRedId: String,
        inviteeIds: List<String>,
        mode: String,
        payload: Map<String, Any?> = emptyMap()
    ) {
        val added = addGroupCallMembers(groupCallId, hostRedId, inviteeIds, mode, payload)
        require(added.isNotEmpty()) { "inviteeIds is required" }
    }

    /**
     * دمج أعضاء جدد في غرفة قائمة بدل استبدالها — كان كل استدعاء لـ
     * deliverGroupCallInvite يستبدل groupRooms كاملة فيسقط المضيف والأعضاء
     * الأصليين (دعوة invite-extra كانت تطرد الجميع ما عدا الأخير).
     * يرجع قائمة من أُضيف فعلاً (بعد إسقاط المكرر والحد 32).
     */
    fun addGroupCallMembers(
        groupCallId: String,
        hostRedId: String,
        extraIds: List<String>,
        mode: String,
        payload: Map<String, Any?> = emptyMap()
    ): List<String> {
        val effectiveId = resolveRoom(groupCallId)
        val existing = groupRooms[effectiveId] ?: groupRooms[groupCallId.trim()]
        val current = (existing?.members.orEmpty() + (existing?.host?.let { listOf(it) } ?: emptyList()))
            .filter { it.isNotBlank() }.distinct()
        val fresh = extraIds.filter { it.isNotBlank() && it != hostRedId && it !in current }
            .take((MAX_GROUP_CALL_MEMBERS - current.size).coerceAtLeast(0))
        if (existing == null) {
            require(fresh.isNotEmpty()) { "inviteeIds is required" }
            groupRooms[effectiveId] = GroupCallRoom(host = hostRedId, members = fresh.toMutableList())
        } else if (fresh.isNotEmpty()) {
            groupRooms[effectiveId] = existing.copy(members = (existing.members + fresh).distinct().toMutableList())
        }
        val enriched = payload + ("hostName" to (payload["hostName"] ?: ""))
        fresh.forEach { invitee ->
            deliverInvite(invitee, "GROUP_CALL_INVITE", effectiveId, hostRedId, mode, enriched)
        }
        return fresh
    }

    /** مضيف الغرفة الجماعية — للتحقق من صلاحية الدعوات الإضافية عبر REST. */
    fun groupCallHost(groupCallId: String): String? {
        val effective = resolveRoom(groupCallId)
        return groupRooms[effective]?.host ?: groupRooms[groupCallId.trim()]?.host
    }

    /** عدد مشاركي الغرفة الجماعية (مضيف + أعضاء) — لفرض الحد. */
    fun groupCallSize(groupCallId: String): Int {
        val effective = resolveRoom(groupCallId)
        val room = groupRooms[effective] ?: groupRooms[groupCallId.trim()] ?: return 0
        return ((room.members + room.host).filter { it.isNotBlank() }.distinct()).size
    }

    /** إشارة عامة (KICKED وغيرها): نفس بدائية deliverInvite بتوقيع مسمّى مريح. */
    fun deliverSignal(
        targetRedId: String,
        type: String,
        roomId: String,
        sourceRedId: String = "",
        mode: String = "SIGNAL",
        payload: Map<String, Any?> = emptyMap()
    ) = deliverInvite(targetRedId, type, roomId, sourceRedId, mode, payload)

    /**
     * تنظيف الغرف الجماعية العالقة: يزيل الغرف التي لا يملك مضيفها ولا أي عضو
     * جلسة حية. صندوق البريد المؤقت (pending) لا يُمس — يُسلَّم عند إعادة الاتصال،
     * ومسار ACCEPT يتحمل غياب الغرفة (hostId يسقط إلى source).
     */
    fun cleanupStaleGroups() {
        val stale = groupRooms.filter { (_, room) ->
            val participants = (room.members + room.host).filter { it.isNotBlank() }
            participants.none { liveSessions(it).isNotEmpty() }
        }.keys
        stale.forEach { groupRooms.remove(it) }
    }

    private fun requireCallId(signal: IncomingCallSignal) =
        requireNotNull(signal.callId?.takeIf(String::isNotBlank)) { "callId is required" }

    /** تحقق الغرفة الجماعية: يحل alias ثم يطابق النمط الموحد — خطأ بلا إغلاق. */
    private fun validGroupRoomOrError(session: WebSocketSession, rawCallId: String?): String? {
        val raw = rawCallId?.trim().orEmpty()
        if (raw.isBlank()) { sendError(session, "", "callId is required"); return null }
        val resolved = resolveRoom(raw)
        if (!ROOM_ID_REGEX.matches(resolved)) { sendError(session, raw, "INVALID_ROOM_ID"); return null }
        return resolved
    }

    // ── توافق v1 (CallsV1Controller): نفس الصندوق 60s/50 بغلاف SDP — لا نظام جديد ──

    /** مسار v1 لكل callId: يبقى بعد سحب العرض حتى تنتهي المكالمة ليعرف الجواب لمن يُوجَّه. */
    private val callRoutes = ConcurrentHashMap<String, V1CallRoute>()

    fun storeCallOffer(callId: String, callerRedId: String, targetRedId: String, mode: String, offerSdp: String, ttlSeconds: Int = 120) {
        val ttl = ttlSeconds.coerceIn(5, 120).toLong()
        callOffers[callId] = StoredCallOffer(callId, callerRedId, targetRedId, mode, offerSdp, Instant.now().plusSeconds(ttl))
        callRoutes[callId] = V1CallRoute(callerRedId, targetRedId, mode.uppercase())
        callOffers.entries.removeIf { it.value.expiresAt.isBefore(Instant.now()) }
        if (callOffers.size > 10_000) callOffers.entries.firstOrNull()?.let { callOffers.remove(it.key) }
        if (callRoutes.size > 10_000) callRoutes.entries.firstOrNull()?.let { callRoutes.remove(it.key) }
        enqueue(targetRedId, OutgoingCallSignal(callId, callerRedId, targetRedId, "OFFER", mode.uppercase(), mapOf("offerSdp" to offerSdp)))
    }

    fun takeCallOffer(callId: String, takerRedId: String): StoredCallOffer? {
        val offer = callOffers[callId] ?: return null
        if (offer.expiresAt.isBefore(Instant.now())) { callOffers.remove(callId); return null }
        if (!offer.targetRedId.equals(takerRedId, ignoreCase = true)) return null
        callOffers.remove(callId)
        dropPending(callId)
        return offer
    }

    /**
     * ترحيل جواب v1 إلى المتصل: كان no-op (يقرأ جلسات المُجيب ثم لا يرسل
     * شيئاً) فيضيع الـANSWER ولا تكتمل أي مكالمة عبر REST أبداً.
     * الآن يُسلَّم حيّاً أو يُصفَّر للمتصل الغائب + نشر للنسخ + سجل ANSWER.
     */
    fun sendAnswerToCaller(callId: String, answererRedId: String, answerSdp: String) {
        val route = callRoutes[callId]
        val callerRedId = route?.callerRedId
        // الإسقاط أولاً: يمسح العرض القديم، ثم يُصفَّر الجواب الجديد —
        // (العكس كان يحذف الجواب المُصفَّر للمتصل الغائب فوراً).
        dropPending(callId)
        if (!callerRedId.isNullOrBlank()) {
            val outbound = OutgoingCallSignal(
                callId, answererRedId, callerRedId, "ANSWER", route.mode,
                mapOf("answerSdp" to answerSdp)
            )
            deliverToPeerOrQueue(callerRedId, outbound)
        }
        runCatching { history.answer(callId, answererRedId) }
        if (route != null) activeCalls?.register(callId, listOf(route.callerRedId, answererRedId))
    }

    /** ترحيل رفض v1 إلى المتصل — كان يُسقط الإشارة بصمت فيبقى المتصل على رنين. */
    fun sendRejectToCaller(callId: String, rejecterRedId: String) {
        val route = callRoutes.remove(callId)
        val callerRedId = route?.callerRedId
        dropPending(callId)
        if (!callerRedId.isNullOrBlank() && !callerRedId.equals(rejecterRedId, ignoreCase = true)) {
            deliverToPeerOrQueue(
                callerRedId,
                OutgoingCallSignal(callId, rejecterRedId, callerRedId, "REJECT", route.mode, emptyMap())
            )
        }
        runCatching { history.rejected(callId, rejecterRedId) }
        activeCalls?.unregister(callId)
    }

    fun endCall(callId: String, actorRedId: String) {
        val route = callRoutes.remove(callId)
        val peer = when {
            route == null -> null
            route.callerRedId.equals(actorRedId, ignoreCase = true) -> route.targetRedId
            else -> route.callerRedId
        }
        dropPending(callId)
        if (!peer.isNullOrBlank() && !peer.equals(actorRedId, ignoreCase = true)) {
            deliverToPeerOrQueue(
                peer,
                OutgoingCallSignal(callId, actorRedId, peer, "END", route?.mode ?: "VOICE", emptyMap())
            )
        }
        activeCalls?.unregister(callId)
        runCatching { history.end(callId, actorRedId) }
    }

    /**
     * ترحيل مرشح ICE إلى النظير الحي (بلا صندوق: حساس زمنياً ويتقادم
     * فوراً) + نشر للنسخ. كان no-op فيبقى التفاوض معلقاً عبر REST.
     */
    fun relayIceCandidate(callId: String, senderRedId: String, candidate: String, sdpMLineIndex: Int, sdpMid: String?) {
        val route = callRoutes[callId] ?: return
        val peer = if (route.callerRedId.equals(senderRedId, ignoreCase = true)) route.targetRedId else route.callerRedId
        if (peer.isBlank() || peer.equals(senderRedId, ignoreCase = true)) return
        val outbound = OutgoingCallSignal(
            callId, senderRedId, peer, "ICE", route.mode,
            mapOf("candidate" to candidate, "sdpMLineIndex" to sdpMLineIndex, "sdpMid" to sdpMid)
        )
        val json = runCatching { objectMapper.writeValueAsString(outbound) }.getOrNull() ?: return
        liveSessions(peer).forEach { target -> target.sendSafe(TextMessage(json)) }
        publishToPeerInstances(json)
    }

    /** تسليم لنظير: جلسات حية أولاً، وإلا صفّ في صندوقه + نشر للنسخ الأخرى. */
    private fun deliverToPeerOrQueue(targetRedId: String, signal: OutgoingCallSignal) {
        val json = runCatching { objectMapper.writeValueAsString(signal) }.getOrNull() ?: return
        val targets = liveSessions(targetRedId)
        if (targets.isEmpty() && !isOnlineOnAnotherInstance(targetRedId)) {
            enqueue(targetRedId, signal)
        } else {
            targets.forEach { target -> target.sendSafe(TextMessage(json)) }
        }
        publishToPeerInstances(json)
    }

    /** إسقاط معلّق الذاكرة لمكالمة منتهية — واجهة عمومية لمهمة انتهاء الرنين. */
    fun cancelPendingCall(callId: String) {
        if (callId.isNotBlank()) dropPending(callId)
    }

    /**
     * تطهير طوابير Redis (pending + mailbox) من بقايا callId حتى لا يرنّ
     * عرض منتهٍ (MISSED) عند إعادة اتصال المستلم لاحقاً.
     */
    fun purgeQueuedCall(callId: String, targetRedId: String) {
        if (callId.isBlank() || targetRedId.isBlank()) return
        purgeQueueKey(pendingKey(targetRedId)) { raw ->
            runCatching { objectMapper.readValue(raw, OutgoingCallSignal::class.java) }
                .getOrNull()?.callId == callId
        }
        purgeQueueKey("call:mailbox:$targetRedId") { raw ->
            (runCatching { objectMapper.readValue(raw, Map::class.java) }
                .getOrNull()?.get("callId") as? String) == callId
        }
    }

    private fun purgeQueueKey(key: String, matches: (String) -> Boolean) {
        runCatching {
            val ops = redisTemplate.opsForList() ?: return@runCatching
            val all = ops.range(key, 0, -1) ?: return@runCatching
            if (all.none(matches)) return@runCatching
            redisTemplate.delete(key)
            all.filterNot(matches).forEach { ops.rightPush(key, it) }
            // سقف محافظ للبقايا حتى لا تخلد إن كان المفتاح بلا TTL.
            redisTemplate.expire(key, 60, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    fun toggleScreenShare(callId: String, userRedId: String, enabled: Boolean) {
        // حالة عرض فقط — تُبثّ عبر إشارات WS الحية لا REST.
    }

    fun startRecording(callId: String, userRedId: String, mode: String): String? = null

    fun getGroupCallMembers(groupId: String): List<String> {
        val effective = resolveRoom(groupId)
        val room = groupRooms[effective] ?: groupRooms[groupId.trim()] ?: return emptyList()
        return (room.members + room.host).filter { it.isNotBlank() }.distinct()
    }

    /** G13: حل alias الغرفة عبر RoomAliasService (Redis+ذاكرة) مع سقوط للخام. */
    private fun resolveRoom(raw: String?): String {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return v
        return runCatching { roomAliases?.resolve(v) ?: RoomSeparationPolicy.resolve(v) }.getOrNull()?.takeIf { it.isNotBlank() } ?: v
    }

    /** تحويل أنماط التطبيق (VOICE/VIDEO/…) إلى أنواع سجل المكالمات عند بدء OFFER. */
    private fun callTypeForMode(mode: String): CallType = when (mode.uppercase()) {
        "VIDEO" -> CallType.VIDEO_1V1
        "VOICE" -> CallType.AUDIO_1V1
        "GROUP" -> CallType.GROUP_AUDIO
        "GROUP_VIDEO" -> CallType.GROUP_VIDEO
        "LIVE" -> CallType.LIVE_STREAM
        "SPACE" -> CallType.SPACE
        else -> CallType.AUDIO_1V1
    }

    private fun WebSocketSession.sendSafe(message: TextMessage) {
        if (isOpen) {
            synchronized(this) {
                runCatching { sendMessage(message) }
            }
        }
    }

    companion object {
        private const val PENDING_TTL_SECONDS = 60L
        private const val MAX_PENDING_PER_USER = 50
        /** قناة Redis لتوزيع إشارات المكالمات بين نسخ الخادم (مطابقة لـRedisPubSubConfig). */
        private const val CALL_SIGNAL_CHANNEL = "red:calls:signals"
        /** مجموعة المتصلين عبر كل النسخ. */
        private const val ONLINE_SET_KEY = "red:online"
        private val TERMINAL_TYPES = setOf("ANSWER", "REJECT", "END")
        /** النمط الموحد الوحيد — إزالة التكرار: المرجع RoomSeparationPolicy.ROOM_ID. */
        private val ROOM_ID_REGEX = com.red.server.calls.RoomSeparationPolicy.ROOM_ID
        /** حد واتساب للمكالمات الجماعية — يُفرض في WS وREST معاً. */
        const val MAX_GROUP_CALL_MEMBERS = 32
    }
}

data class StoredCallOffer(
    val callId: String,
    val callerId: String,
    val targetRedId: String,
    val mode: String,
    val offerSdp: String,
    val expiresAt: Instant
)

/** مسار v1: طرفا المكالمة ووضعها — يبقى بعد سحب العرض حتى END/REJECT. */
private data class V1CallRoute(
    val callerRedId: String,
    val targetRedId: String,
    val mode: String
)

private data class PendingCallSignal(
    val json: String,
    val expiresAt: Instant,
    val callId: String,
    val type: String
)

/** غرفة مكالمة جماعية — يوجّه السيرفر بها ردود الأعضاء إلى المضيف والإنهاء للجميع. */
private data class GroupCallRoom(
    val host: String,
    val members: MutableList<String> = mutableListOf(),
    val kicked: MutableSet<String> = mutableSetOf()
)

/**
 * تجاهل الحقول الزائدة من التطبيق (نفس علة IncomingConferenceSignal:
 * UnrecognizedPropertyException كانت تقتل جلسة /ws/calls).
 */
// تحمّل الحقول الزائدة من التطبيق مفروض **عالمياً** في JacksonConfig
// (FAIL_ON_UNKNOWN_PROPERTIES معطّل) — لا تعليق لكل DTO، ولا حزمة Jackson 2.
data class IncomingCallSignal(
    val callId: String? = null,
    val targetUserId: String = "",
    val type: String = "",
    val mode: String = "VOICE",
    val inviteeIds: List<String> = emptyList(),
    val memberStatus: String? = null,
    val payload: Map<String, Any?> = emptyMap()
)

data class OutgoingCallSignal(
    val callId: String,
    val sourceUserId: String,
    val targetUserId: String,
    val type: String,
    val mode: String,
    val payload: Map<String, Any?>
)




