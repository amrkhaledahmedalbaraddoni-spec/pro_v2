package com.red.sovereign.groups

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * حراس بقاء قائمة المجموعات (تشخيص الاختفاء 2026-09-27):
 * رد خادم فارغ عابر + مقابر الأشباح + فلتر العرض الموحد.
 */
class GroupsVisibilityGuardTest {

    private fun group(
        id: String,
        name: String = "مجموعة $id",
        privacy: String = "PRIVATE",
        createdAt: String = "2026-09-27T00:00:00Z"
    ) = Group(id = id, name = name, ownerRedId = "10001", privacy = privacy, createdAt = createdAt)

    @Test
    fun `empty server response keeps visible groups instead of wiping`() {
        val current = listOf(group("g1"), group("g2"))

        assertNull(resolveGroupListUpdate(emptyList(), current))
    }

    @Test
    fun `empty server with empty current resolves to empty`() {
        assertEquals(emptyList<Group>(), resolveGroupListUpdate(emptyList(), emptyList()))
    }

    @Test
    fun `non-empty server replaces and sorts newest first`() {
        val old = group("g1", createdAt = "2026-01-01T00:00:00Z")
        val fresh = group("g2", createdAt = "2026-09-27T00:00:00Z")

        val resolved = resolveGroupListUpdate(listOf(old, fresh), listOf(old))

        assertEquals(listOf("g2", "g1"), resolved!!.map { it.id })
    }

    @Test
    fun `tombstoned groups are hidden from cache placeholders`() {
        val list = listOf(group("g1"), group("g2"))

        assertEquals(listOf("g2"), applyGroupTombstones(list, setOf("g1")).map { it.id })
    }

    @Test
    fun `empty tombstones change nothing`() {
        val list = listOf(group("g1"))

        assertEquals(list, applyGroupTombstones(list, emptySet()))
    }

    @Test
    fun `privacy filter and arabic search share one contract`() {
        val groups = listOf(
            group("g1", "عائلة", "PRIVATE"),
            group("g2", "قناة عامة", "PUBLIC"),
            group("g3", "سري للغاية", "SECRET")
        )

        assertEquals(listOf("g2"), filterUserGroups(groups, "عام", "").map { it.id })
        assertEquals(listOf("g1"), filterUserGroups(groups, "الكل", "عائلة").map { it.id })
        assertEquals(3, filterUserGroups(groups, "الكل", "").size)
        // فلتر مجهول = الكل (لا مسح صامت)
        assertEquals(3, filterUserGroups(groups, "؟؟؟", "").size)
    }

    @Test
    fun `filtered-empty with non-empty source is distinguishable from no-groups`() {
        val groups = listOf(group("g1", "عائلة"))

        assertTrue(groups.isNotEmpty() && filterUserGroups(groups, "عام", "").isEmpty())
    }
}
