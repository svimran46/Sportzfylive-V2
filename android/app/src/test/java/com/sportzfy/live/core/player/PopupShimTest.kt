package com.sportzfy.live.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupShimTest {

    @Test
    fun `wraps window open and never answers null`() {
        val script = PopupShim.SCRIPT
        assertTrue(script.contains("var nativeOpen = window.open;"))
        assertTrue(script.contains("nativeOpen.apply(window, arguments)"))
        // A real window passes through; a null or a throw becomes the stand-in.
        assertTrue(script.contains("return win || standIn();"))
        assertTrue(script.contains("window.open = open;"))
    }

    @Test
    fun `stand-in navigates nowhere and can be closed`() {
        val script = PopupShim.SCRIPT
        assertTrue(script.contains("""href: "about:blank""""))
        assertTrue(script.contains("close: function () { win.closed = true; }"))
        assertFalse(script.contains("location.href ="))
        assertFalse(script.contains("eval("))
    }

    @Test
    fun `installs once per frame and keeps a native-looking toString`() {
        assertTrue(PopupShim.SCRIPT.contains("if (window.__sfyPopupShim) return;"))
        assertTrue(PopupShim.SCRIPT.contains("[native code]"))
    }

    @Test
    fun `runs in every frame`() {
        // The check runs inside embed.st's nested cross-domain player frames.
        assertEquals(setOf("*"), PopupShim.ALLOWED_ORIGINS)
    }
}
