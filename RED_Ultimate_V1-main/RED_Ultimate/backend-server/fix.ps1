
$file = "src/main/kotlin/com/red/server/websocket/CallWebSocketHandler.kt"
$content = Get-Content $file -Raw -Encoding UTF8

$target = @"
        val outbound = OutgoingCallSignal(callId, source, signal.targetUserId, type, signal.mode.uppercase(), signal.payload)
        val targets = liveSessions(signal.targetUserId)
        if (targets.isEmpty()) {
            enqueue(signal.targetUserId, outbound)
            if (type == "OFFER") {
                notifications.sendVoipPushNotification(signal.targetUserId, source, callId, signal.mode)
            }
            if (type in TERMINAL_TYPES) dropPending(callId)
            session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "RINGING_PUSH_SENT", "callId" to callId))))
            return
        }

        val json = objectMapper.writeValueAsString(outbound)
        targets.forEach { target -> runCatching { target.sendMessage(TextMessage(json)) } }
"@

$replacement = @"
        val outbound = OutgoingCallSignal(callId, source, signal.targetUserId, type, signal.mode.uppercase(), signal.payload)
        val json = objectMapper.writeValueAsString(outbound)
        redisTemplate.convertAndSend("red:calls:signals", json)

        enqueue(signal.targetUserId, outbound)
        if (type in TERMINAL_TYPES) dropPending(callId)
        
        val isOnlineGlobally = redisTemplate.opsForSet().isMember("red:online", signal.targetUserId) == true
        if (type == "OFFER" && !isOnlineGlobally) {
            notifications.sendVoipPushNotification(signal.targetUserId, source, callId, signal.mode)
            session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "RINGING_PUSH_SENT", "callId" to callId))))
        }
"@

$newContent = $content.Replace($target, $replacement)

$target2 = @"
    private fun enqueue(target: String, signal: OutgoingCallSignal) {
        val list = pending.computeIfAbsent(target) { CopyOnWriteArrayList() }
        list.removeIf { it.expiresAt.isBefore(Instant.now()) }
        // ??? ???? ??????: 50 ??? ?????? (???? ?????) ?? ??? ??? ??? ??????? ???? ??????.
        while (list.size >= MAX_PENDING_PER_USER) {
            list.removeAt(0)
        }
        list.add(
            PendingCallSignal(
                json = objectMapper.writeValueAsString(signal),
                expiresAt = Instant.now().plusSeconds(PENDING_TTL_SECONDS),
                callId = signal.callId,
                type = signal.type
            )
        )
    }

    private fun flushPending(redId: String, session: WebSocketSession) {
        val list = pending.remove(redId) ?: return
        val now = Instant.now()
        list.filter { it.expiresAt.isAfter(now) }.forEach { item ->
            runCatching { session.sendSafe(TextMessage(item.json)) }
        }
    }

    private fun dropPending(callId: String) {
        pending.values.forEach { list -> list.removeIf { it.callId == callId } }
        pending.entries.removeIf { it.value.isEmpty() }
    }
"@

$replacement2 = @"
    private fun enqueue(target: String, signal: OutgoingCallSignal) {
        runCatching {
            val key = "red:call:pending:`$target"
            val json = objectMapper.writeValueAsString(signal)
            redisTemplate.opsForList().rightPush(key, json)
            redisTemplate.expire(key, PENDING_TTL_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            redisTemplate.opsForList().trim(key, -MAX_PENDING_PER_USER.toLong(), -1L)
        }
    }

    private fun flushPending(redId: String, session: WebSocketSession) {
        runCatching {
            val key = "red:call:pending:`$redId"
            val list = redisTemplate.opsForList().range(key, 0, -1)
            list?.forEach { json -> session.sendSafe(TextMessage(json)) }
            
            val mailboxKey = "call:mailbox:`$redId"
            val mailboxList = redisTemplate.opsForList().range(mailboxKey, 0, -1)
            mailboxList?.forEach { msgStr ->
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
            redisTemplate.delete(mailboxKey)
            redisTemplate.delete(key)
        }
    }

    private fun dropPending(callId: String) {
    }

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
"@

$newContent = $newContent.Replace($target2, $replacement2)
Set-Content $file $newContent -Encoding UTF8
Write-Host "Done"

