package com.thorctl.daemon.feature.recents

import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.hardware.DisplayController
import com.thorctl.daemon.system.AppTask
import com.thorctl.daemon.system.FakeDisplayService
import com.thorctl.daemon.system.FakeInputService
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenshotFilesTest {
    private val directory = createTempDirectory("recents").toFile()
    private val ids = PhysicalDisplayIds(ScreenshotTestDisplays.create())
    private val files = ScreenshotFiles(ids, directory)

    @Test
    fun physicalIdsComeFromTheUniqueIds() {
        assertEquals("111", ids.of(0))
        assertEquals("222", ids.of(1))
        // Anything that is not the bottom screen counts as the top one.
        assertEquals("111", ids.of(7))
    }

    @Test
    fun physicalIdsAreEmptyWhileTheScreensAreUnknown() {
        val unknown = DisplayController(FakeSys(), FakeDisplayService(), FakeInputService())
        assertEquals("", PhysicalDisplayIds(unknown).of(0))
    }

    @Test
    fun keepsOneHomePicturePerScreen() {
        assertEquals(File(directory, "home-111.jpg"), files.home(0))
        assertEquals(File(directory, "home-222.jpg"), files.home(1))
        assertEquals(File("/data/adb/thorctl/home-222.jpg"), ScreenshotFiles(ids).home(1))
    }

    @Test
    fun aPressOnTheHomeScreenRefreshesItsPicture() {
        assertEquals(files.home(1), files.forPress(1, origin = null))
        assertEquals(File(directory, "screen.jpg"), files.forPress(1, AppTask(3, "app")))
    }

    @Test
    fun aHomePictureStaysFreshForAMinute() {
        assertFalse(files.hasFreshHome(0, now = 0))
        val home = files.home(0).apply { writeBytes(ByteArray(10)) }
        home.setLastModified(1_000_000)
        assertTrue(files.hasFreshHome(0, now = 1_000_000 + ScreenshotFiles.HOME_MAX_AGE_MS - 1000))
        assertFalse(files.hasFreshHome(0, now = 1_000_000 + ScreenshotFiles.HOME_MAX_AGE_MS))
    }
}
