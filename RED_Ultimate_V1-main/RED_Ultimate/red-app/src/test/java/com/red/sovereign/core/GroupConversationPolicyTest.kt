package com.red.sovereign.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * سياسة تمييز المحادثات الجماعية (2026-09-26):
 * lookup صريح بمعرفات معروفة + fallback الـ prefix للسياقات بلا DB.
 */
class GroupConversationPolicyTest {

    @Test fun `prefix fallback detects groups`() {
        assertTrue(RedConnectionService.isGroupConversation("grp_123"))
        assertTrue(RedConnectionService.isGroupConversation("group-abc"))
        assertTrue(RedConnectionService.isGroupConversation("group_xyz"))
        assertTrue(RedConnectionService.isGroupConversation("x:group:y"))
    }

    @Test fun `prefix fallback rejects direct chats`() {
        assertFalse(RedConnectionService.isGroupConversation("red_abc123"))
        assertFalse(RedConnectionService.isGroupConversation(""))
    }

    @Test fun `explicit lookup wins over shape`() {
        val known = setOf("red_custom123")
        assertTrue(RedConnectionService.isGroupConversation("red_custom123", known))
        assertFalse(RedConnectionService.isGroupConversation("red_other999", known))
        // prefix ما زال يعمل مع lookup فارغ/ممرر
        assertTrue(RedConnectionService.isGroupConversation("grp_1", emptySet()))
    }
}
