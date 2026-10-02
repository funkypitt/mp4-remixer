package com.freedomfighter.mp4remixer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlannerTest {

    @Test
    fun nothingToDo() {
        assertNull(Planner.plan(1920, 1080, square = false, limit = Limit.ORIGINAL, center = 0.5f))
        assertNull(Planner.plan(1920, 1080, square = false, limit = Limit.P1080, center = 0.5f))
        assertNull(Planner.plan(1280, 720, square = false, limit = Limit.P1080, center = 0.5f))
        assertNull(Planner.plan(1080, 1080, square = true, limit = Limit.P1080, center = 0.5f))
    }

    @Test
    fun scalesByTheShortSide() {
        val landscape = Planner.plan(3840, 2160, false, Limit.P1080, 0.5f)!!
        assertEquals(1920 to 1080, landscape.width to landscape.height)
        val portrait = Planner.plan(2160, 3840, false, Limit.P720, 0.5f)!!
        assertEquals(720 to 1280, portrait.width to portrait.height)
        val wide = Planner.plan(2560, 1080, false, Limit.P720, 0.5f)!!
        assertEquals(1706 to 720, wide.width to wide.height)
    }

    @Test
    fun squareCentred() {
        val plan = Planner.plan(1920, 1080, true, Limit.ORIGINAL, 0.5f)!!
        assertEquals(1080 to 1080, plan.width to plan.height)
        assertEquals(-0.5625f, plan.left, 1e-4f)
        assertEquals(0.5625f, plan.right, 1e-4f)
        assertEquals(-1f, plan.bottom, 0f)
        assertEquals(1f, plan.top, 0f)
    }

    @Test
    fun squareAtTheEnds() {
        val leftmost = Planner.plan(1920, 1080, true, Limit.ORIGINAL, 0f)!!
        assertEquals(-1f, leftmost.left, 1e-4f)
        assertEquals(0.125f, leftmost.right, 1e-4f)
        val rightmost = Planner.plan(1920, 1080, true, Limit.ORIGINAL, 1f)!!
        assertEquals(-0.125f, rightmost.left, 1e-4f)
        assertEquals(1f, rightmost.right, 1e-4f)
    }

    @Test
    fun squarePortraitCountsFromTheTop() {
        val topmost = Planner.plan(1080, 1920, true, Limit.ORIGINAL, 0f)!!
        assertEquals(1f, topmost.top, 1e-4f)
        assertEquals(-0.125f, topmost.bottom, 1e-4f)
        assertEquals(-1f, topmost.left, 0f)
        assertEquals(1f, topmost.right, 0f)
    }

    @Test
    fun squareAndSmaller() {
        val plan = Planner.plan(3840, 2160, true, Limit.P720, 0.5f)!!
        assertEquals(720 to 720, plan.width to plan.height)
        assertEquals("Clip-1x1-720p.mp4", Planner.outputName("Clip.mov", plan, Limit.P720))
        val small = Planner.plan(1280, 720, true, Limit.P1080, 0.5f)!!
        assertEquals(720 to 720, small.width to small.height)
        assertEquals("Clip-1x1.mp4", Planner.outputName("Clip.mp4", small, Limit.P1080))
    }
}
