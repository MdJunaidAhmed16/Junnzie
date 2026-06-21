package com.junnz.phone.matching

import com.junnz.phone.embedding.LocalEmbeddingService
import com.junnz.shared.domain.ContextEvent
import com.junnz.shared.domain.Reminder
import com.junnz.shared.domain.Trigger
import com.junnz.shared.matching.MatchingEngine
import com.junnz.shared.matching.ReminderQuery
import com.junnz.shared.parsing.ReminderParser
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SemanticMatchingTest {

    private val embeddings = LocalEmbeddingService()
    private val at = Instant.parse("2025-06-11T10:00:00Z")

    private fun reminderWithAnchor(anchor: String): Reminder = runBlocking {
        Reminder(
            id = UUID.randomUUID().toString(),
            text = anchor,
            rawTranscript = anchor,
            createdAt = at,
            triggers = listOf(
                Trigger.SemanticTrigger(
                    anchorText = anchor,
                    embeddingVector = embeddings.embed(anchor).toList(),
                    threshold = ReminderParser.SEMANTIC_THRESHOLD,
                ),
            ),
        )
    }

    private fun engineFor(reminder: Reminder): MatchingEngine {
        val query = object : ReminderQuery {
            override suspend fun getActiveReminders() = listOf(reminder)
        }
        return MatchingEngine(query, embeddings, InMemoryCooldownStore())
    }

    @Test
    fun `fires on semantically related notification`() = runBlocking {
        val reminder = reminderWithAnchor("buy milk and bread")
        val decisions = engineFor(reminder).evaluate(
            ContextEvent.AppNotification(
                packageName = "com.example.store",
                contextPhrase = "Reminder: milk and bread are on your shopping list",
                occurredAt = at,
            ),
        )
        assertTrue(
            "expected a semantic match, got $decisions",
            decisions.any { it.reminder.id == reminder.id && it.triggerType == "SEMANTIC" },
        )
    }

    @Test
    fun `ignores unrelated notification`() = runBlocking {
        val reminder = reminderWithAnchor("buy milk and bread")
        val decisions = engineFor(reminder).evaluate(
            ContextEvent.AppNotification(
                packageName = "com.example.cab",
                contextPhrase = "Your cab is arriving in 5 minutes",
                occurredAt = at,
            ),
        )
        assertFalse(
            "did not expect a match, got $decisions",
            decisions.any { it.reminder.id == reminder.id },
        )
    }

    @Test
    fun `local embedding cosine separates related from unrelated`() = runBlocking {
        val anchor = embeddings.embed("buy milk and bread")
        val related = embeddings.embed("Reminder: milk and bread are on your shopping list")
        val unrelated = embeddings.embed("Your cab is arriving in 5 minutes")
        val relatedScore = cosine(anchor, related)
        assertTrue("related=$relatedScore unrelated=${cosine(anchor, unrelated)}",
            relatedScore > cosine(anchor, unrelated))
        assertTrue("related=$relatedScore below threshold",
            relatedScore > ReminderParser.SEMANTIC_THRESHOLD)
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot // both are L2-normalized by LocalEmbeddingService
    }
}
