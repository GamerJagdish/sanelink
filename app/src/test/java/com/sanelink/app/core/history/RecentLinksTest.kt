package com.sanelink.app.core.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentLinksTest {

    private fun link(n: Int) = RecentLink(
        id = "id$n",
        original = "https://example.com/$n?utm_source=x",
        clean = "https://example.com/$n",
        source = CleanSource.AUTO,
        timestamp = n.toLong()
    )

    @Test
    fun `newest entry goes first`() {
        val list = RecentLinks.add(listOf(link(1)), link(2), limit = 20)
        assertEquals(listOf("id2", "id1"), list.map { it.id })
    }

    @Test
    fun `limit keeps only the newest entries`() {
        var list = emptyList<RecentLink>()
        for (n in 1..12) list = RecentLinks.add(list, link(n), limit = 10)
        assertEquals(10, list.size)
        assertEquals("id12", list.first().id)
        assertEquals("id3", list.last().id)
    }

    @Test
    fun `unlimited keeps everything and off keeps nothing`() {
        var list = emptyList<RecentLink>()
        for (n in 1..60) list = RecentLinks.add(list, link(n), limit = RecentLinks.UNLIMITED)
        assertEquals(60, list.size)
        assertTrue(RecentLinks.add(list, link(61), limit = RecentLinks.OFF).isEmpty())
    }

    @Test
    fun `cleaning the same link again moves it to the top without duplicating`() {
        val again = link(1).copy(id = "again", timestamp = 99)
        val list = RecentLinks.add(listOf(link(2), link(1)), again, limit = 20)
        assertEquals(listOf("again", "id2"), list.map { it.id })
    }

    @Test
    fun `lowering the limit trims existing entries`() {
        val list = (1..30).map { link(it) }
        assertEquals(10, RecentLinks.trim(list, 10).size)
        assertEquals(30, RecentLinks.trim(list, RecentLinks.UNLIMITED).size)
        assertTrue(RecentLinks.trim(list, RecentLinks.OFF).isEmpty())
    }
}
