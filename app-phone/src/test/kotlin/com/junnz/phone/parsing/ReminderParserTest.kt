package com.junnz.phone.parsing

import com.junnz.shared.domain.Trigger
import com.junnz.shared.parsing.ReminderParser
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderParserTest {

    // Fixed reference: 2025-06-11T10:00:00Z (a Wednesday), evaluated in UTC.
    private val refNow = Instant.parse("2025-06-11T10:00:00Z")
    private val zone = TimeZone.UTC
    private val parser = ReminderParser(clock = object : Clock { override fun now() = refNow }, zone = zone)

    private fun fireAt(t: Trigger) = (t as Trigger.TimeTrigger).fireAt.toLocalDateTime(zone)

    @Test
    fun `app trigger with two known apps`() {
        val r = parser.parse("remind me to buy cucumber when I open Blinkit or Zepto")
        assertEquals("Buy cucumber", r.text)
        val t = r.triggers.single() as Trigger.AppContextTrigger
        assertTrue(t.packageNames.contains("com.grofers.customerapp"))
        assertTrue(t.packageNames.contains("com.zeptoconsumerapp"))
    }

    @Test
    fun `location trigger from reach the X`() {
        val r = parser.parse("remind me to buy groceries when I reach the supermarket")
        assertEquals("Buy groceries", r.text)
        val t = r.triggers.single() as Trigger.GeofenceTrigger
        assertEquals("Supermarket", t.label)
    }

    @Test
    fun `weekday with explicit clock time`() {
        val r = parser.parse("remind me to apply hair serum this Saturday at 9:30 AM")
        assertEquals("Apply hair serum", r.text)
        val ldt = fireAt(r.triggers.single())
        assertEquals(DayOfWeek.SATURDAY, ldt.dayOfWeek)
        assertEquals(9, ldt.hour)
        assertEquals(30, ldt.minute)
        assertTrue(ldt.toInstant(zone) > refNow)
    }

    @Test
    fun `tomorrow evening resolves to next day at 6pm`() {
        val r = parser.parse("call mom tomorrow evening")
        assertEquals("Call mom", r.text)
        val ldt = fireAt(r.triggers.single())
        assertEquals(refNow.toLocalDateTime(zone).date.plus(1, DateTimeUnit.DAY), ldt.date)
        assertEquals(18, ldt.hour)
    }

    @Test
    fun `relative in two hours`() {
        val r = parser.parse("remind me to take medicine in 2 hours")
        assertEquals("Take medicine", r.text)
        val instant = (r.triggers.single() as Trigger.TimeTrigger).fireAt
        assertEquals(refNow.plus(2, DateTimeUnit.HOUR, zone), instant)
    }

    @Test
    fun `pm time in the past pushes to tomorrow`() {
        // It's 10:00 UTC; "at 8 pm" today is still ahead → today 20:00.
        val r = parser.parse("remind me to water the plants at 8 pm")
        assertEquals("Water the plants", r.text)
        val ldt = fireAt(r.triggers.single())
        assertEquals(20, ldt.hour)
        assertEquals(refNow.toLocalDateTime(zone).date, ldt.date)
    }

    @Test
    fun `am time already past pushes to tomorrow`() {
        // It's 10:00 UTC; "at 8 am" already passed → tomorrow 08:00.
        val r = parser.parse("remind me to stretch at 8 am")
        val ldt = fireAt(r.triggers.single())
        assertEquals(8, ldt.hour)
        assertEquals(refNow.toLocalDateTime(zone).date.plus(1, DateTimeUnit.DAY), ldt.date)
    }

    @Test
    fun `plain task has no trigger`() {
        val r = parser.parse("remember to water the plants")
        assertEquals("Water the plants", r.text)
        assertTrue(r.triggers.isEmpty())
    }

    @Test
    fun `weekday without time defaults to 9am`() {
        val r = parser.parse("remind me to pay rent on Friday")
        assertEquals("Pay rent", r.text)
        val ldt = fireAt(r.triggers.single())
        assertEquals(DayOfWeek.FRIDAY, ldt.dayOfWeek)
        assertEquals(9, ldt.hour)
    }

    @Test
    fun `known app mentioned without open verb`() {
        val r = parser.parse("remind me to reply to messages when I'm on WhatsApp")
        val t = r.triggers.single() as Trigger.AppContextTrigger
        assertTrue(t.packageNames.contains("com.whatsapp"))
    }
}
