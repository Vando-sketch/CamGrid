package io.github.vandosketch.camgrid.desktop.video

import io.github.vandosketch.camgrid.core.FitMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FrameGeometryTest {

    @Test
    fun fitLetterboxesAWideFrameInATallBox() {
        val r = FrameGeometry.place(1920, 1080, 400f, 400f, FitMode.FIT)!!
        assertEquals(DrawRects(0, 0, 1920, 1080, 0f, 87.5f, 400f, 225f), r)
    }

    @Test
    fun fitPillarboxesATallFrameInAWideBox() {
        val r = FrameGeometry.place(1080, 1920, 1600f, 900f, FitMode.FIT)!!
        assertEquals(0, r.srcLeft)
        assertEquals(1080, r.srcWidth)
        assertEquals(900f, r.dstHeight)
        assertEquals(506.25f, r.dstWidth)
        assertEquals((1600f - 506.25f) / 2, r.dstLeft)
        assertEquals(0f, r.dstTop)
    }

    @Test
    fun fitWithSameAspectFillsTheBox() {
        val r = FrameGeometry.place(640, 360, 1280f, 720f, FitMode.FIT)!!
        assertEquals(DrawRects(0, 0, 640, 360, 0f, 0f, 1280f, 720f), r)
    }

    @Test
    fun cropCutsTheSidesOfAWideFrame() {
        val r = FrameGeometry.place(1920, 1080, 400f, 400f, FitMode.CROP)!!
        // The visible part is the centred 1080x1080 square.
        assertEquals(DrawRects(420, 0, 1080, 1080, 0f, 0f, 400f, 400f), r)
    }

    @Test
    fun cropCutsTopAndBottomOfATallFrame() {
        val r = FrameGeometry.place(1080, 1920, 1600f, 900f, FitMode.CROP)!!
        // 1080 wide at scale 1600/1080 shows 900 * 1080 / 1600 = 607.5 source rows, centred.
        assertEquals(0, r.srcLeft)
        assertEquals(1080, r.srcWidth)
        assertEquals(608, r.srcHeight)
        assertEquals(656, r.srcTop)
        assertEquals(DrawRects(0, 656, 1080, 608, 0f, 0f, 1600f, 900f), r)
    }

    @Test
    fun cropNeverReadsOutsideTheFrame() {
        for ((fw, fh) in listOf(1 to 1, 3 to 7, 1921 to 1081, 640 to 480)) {
            for ((bw, bh) in listOf(1f to 1f, 333f to 77f, 77f to 333f, 1000f to 1000f)) {
                val r = FrameGeometry.place(fw, fh, bw, bh, FitMode.CROP)!!
                assert(r.srcLeft >= 0 && r.srcTop >= 0) { "$fw x $fh in $bw x $bh: $r" }
                assert(r.srcLeft + r.srcWidth <= fw && r.srcTop + r.srcHeight <= fh) { "$fw x $fh in $bw x $bh: $r" }
                assert(r.srcWidth >= 1 && r.srcHeight >= 1) { "$fw x $fh in $bw x $bh: $r" }
            }
        }
    }

    @Test
    fun emptyFrameOrBoxDrawsNothing() {
        assertNull(FrameGeometry.place(0, 1080, 400f, 400f, FitMode.FIT))
        assertNull(FrameGeometry.place(1920, 0, 400f, 400f, FitMode.CROP))
        assertNull(FrameGeometry.place(1920, 1080, 0f, 400f, FitMode.FIT))
        assertNull(FrameGeometry.place(1920, 1080, 400f, -1f, FitMode.CROP))
    }

    @Test
    fun decodeSizeDownscalesToTheBoxForFit() {
        // 1920x1080 in a 480x480 tile is drawn 480x270, so there is no point decoding more.
        assertEquals(480 to 270, FrameGeometry.decodeSize(1920, 1080, 480, 480, FitMode.FIT))
    }

    @Test
    fun decodeSizeKeepsWhatCropShows() {
        // Crop scales by the larger factor, 480/1080: 853.3x480, rounded down to even.
        assertEquals(852 to 480, FrameGeometry.decodeSize(1920, 1080, 480, 480, FitMode.CROP))
    }

    @Test
    fun decodeSizeNeverUpscales() {
        assertEquals(640 to 360, FrameGeometry.decodeSize(640, 360, 1920, 1080, FitMode.FIT))
        assertEquals(640 to 360, FrameGeometry.decodeSize(640, 360, 1920, 1080, FitMode.CROP))
    }

    @Test
    fun decodeSizeIsEvenAndAtLeastTwo() {
        assertEquals(2 to 2, FrameGeometry.decodeSize(1920, 1080, 1, 1, FitMode.FIT))
        val (w, h) = FrameGeometry.decodeSize(1919, 1079, 999, 999, FitMode.FIT)
        assertEquals(0, w % 2)
        assertEquals(0, h % 2)
    }

    @Test
    fun decodeSizeWithUnknownBoxKeepsTheFrame() {
        assertEquals(1920 to 1080, FrameGeometry.decodeSize(1920, 1080, 0, 0, FitMode.FIT))
    }
}
