package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [GpuInfo] without a GPU.
 *
 * Everything here goes through [GpuInfo.enumerateWith], which takes the one call that needs a real
 * `user32` as a parameter — so the walk, the NUL-trimming and the active-flag test are exercised
 * for real and only the `EnumDisplayDevicesW` call itself stays uncovered.
 */
class GpuInfoTest {

    private fun device(
        display: String = "\\\\.\\DISPLAY1",
        adapter: String = "NVIDIA GeForce GTX 1660 Ti",
        id: String = "PCI\\VEN_10DE&DEV_2182&SUBSYS_375E1462&REV_A1",
        active: Boolean = true,
    ) = GpuInfo.DisplayDevice().apply {
        fun fill(buf: CharArray, s: String) {
            s.forEachIndexed { i, c -> buf[i] = c }
        }
        fill(DeviceName, display)
        fill(DeviceString, adapter)
        fill(DeviceID, id)
        StateFlags = if (active) 1 else 0
    }

    /** Hands [GpuInfo.enumerateWith] a fixed list, the way the real call hands it the OS's. */
    private fun enumerate(devices: List<GpuInfo.DisplayDevice>): List<DisplayAdapter> {
        var next = 0
        return GpuInfo.enumerateWith(newDevice = { devices.getOrElse(next) { GpuInfo.DisplayDevice() } }) { i, _ ->
            next = i + 1
            i < devices.size
        }
    }

    // ── vendorOf ──────────────────────────────────────────────────────────────

    @Test
    fun `reads the vendor out of a PCI device id`() {
        assertEquals("NVIDIA", GpuInfo.vendorOf("PCI\\VEN_10DE&DEV_2182&SUBSYS_375E1462&REV_A1"))
        assertEquals("AMD", GpuInfo.vendorOf("PCI\\VEN_1002&DEV_743F"))
        assertEquals("Intel", GpuInfo.vendorOf("PCI\\VEN_8086&DEV_3E9B"))
    }

    @Test
    fun `names virtual adapters, because no real GPU is itself the diagnosis`() {
        assertEquals("Microsoft", GpuInfo.vendorOf("PCI\\VEN_1414&DEV_008C"))
        assertEquals("VMware", GpuInfo.vendorOf("PCI\\VEN_15AD&DEV_0405"))
    }

    @Test
    fun `keeps an unrecognised vendor id rather than discarding it`() {
        assertEquals("0xbeef", GpuInfo.vendorOf("PCI\\VEN_BEEF&DEV_0001"))
    }

    @Test
    fun `is unknown when the id carries no vendor at all`() {
        assertEquals("unknown", GpuInfo.vendorOf(""))
        assertEquals("unknown", GpuInfo.vendorOf("ROOT\\BasicDisplay"))
    }

    @Test
    fun `matches the vendor id case-insensitively`() {
        assertEquals("NVIDIA", GpuInfo.vendorOf("PCI\\VEN_10de&DEV_2182"))
    }

    // ── enumerateWith ─────────────────────────────────────────────────────────

    @Test
    fun `stops at the first device the OS declines to fill`() {
        val found = enumerate(listOf(device(), device(display = "\\\\.\\DISPLAY2")))
        assertEquals(2, found.size)
        assertEquals("NVIDIA GeForce GTX 1660 Ti", found[0].adapterName)
    }

    @Test
    fun `trims each fixed-width buffer at its NUL`() {
        val found = enumerate(listOf(device(adapter = "Intel UHD Graphics 630")))
        assertEquals("Intel UHD Graphics 630", found.single().adapterName)
        // The buffer is 128 chars wide; untrimmed the name would carry 106 NULs after it.
        assertEquals(22, found.single().adapterName.length)
    }

    @Test
    fun `reads the active flag`() {
        val found = enumerate(listOf(device(active = true), device(active = false)))
        assertTrue(found[0].active)
        assertFalse(found[1].active)
    }

    @Test
    fun `returns nothing when the OS reports no devices`() {
        assertEquals(emptyList(), enumerate(emptyList()))
    }

    // ── distinctActive / summarise ────────────────────────────────────────────

    @Test
    fun `collapses four displays on one card into one adapter`() {
        val found = enumerate(
            listOf(
                device(display = "\\\\.\\DISPLAY1"),
                device(display = "\\\\.\\DISPLAY2"),
                device(display = "\\\\.\\DISPLAY3", active = false),
            )
        )
        assertEquals("NVIDIA GeForce GTX 1660 Ti (NVIDIA)", GpuInfo.summarise(found))
    }

    @Test
    fun `names both adapters on a hybrid machine`() {
        val found = enumerate(
            listOf(
                device(adapter = "Intel UHD Graphics 630", id = "PCI\\VEN_8086&DEV_3E9B"),
                device(display = "\\\\.\\DISPLAY2"),
            )
        )
        assertEquals(
            "Intel UHD Graphics 630 (Intel) + NVIDIA GeForce GTX 1660 Ti (NVIDIA)",
            GpuInfo.summarise(found),
        )
    }

    @Test
    fun `an inactive adapter is not reported as driving anything`() {
        val found = enumerate(listOf(device(active = false)))
        assertEquals("unknown", GpuInfo.summarise(found))
    }

    @Test
    fun `summarises to unknown rather than throwing when nothing was read`() {
        assertEquals("unknown", GpuInfo.summarise(emptyList()))
    }

    // ── tags ──────────────────────────────────────────────────────────────────

    @Test
    fun `sends no tag at all when nothing could be read`() {
        assertEquals(emptyMap(), GpuInfo.tags(emptyList()))
    }

    @Test
    fun `tags carry the vendor, the adapter and the count`() {
        val tags = GpuInfo.tags(enumerate(listOf(device())))
        assertEquals("NVIDIA", tags["gpu.vendor"])
        assertEquals("NVIDIA GeForce GTX 1660 Ti", tags["gpu.adapter"])
        assertEquals("1", tags["gpu.count"])
    }

    @Test
    fun `a hybrid machine tags both vendors, which is the axis a GPU fault is grouped by`() {
        val tags = GpuInfo.tags(
            enumerate(
                listOf(
                    device(adapter = "Intel UHD Graphics 630", id = "PCI\\VEN_8086&DEV_3E9B"),
                    device(display = "\\\\.\\DISPLAY2"),
                )
            )
        )
        assertEquals("Intel+NVIDIA", tags["gpu.vendor"])
        assertEquals("2", tags["gpu.count"])
    }

    // ── isSupported ───────────────────────────────────────────────────────────

    @Test
    fun `only Windows has EnumDisplayDevices`() {
        assertTrue(GpuInfo.isSupported("Windows 11"))
        assertTrue(GpuInfo.isSupported("Windows Server 2022"))
        assertFalse(GpuInfo.isSupported("Mac OS X"))
        assertFalse(GpuInfo.isSupported("Linux"))
        assertFalse(GpuInfo.isSupported(""))
    }
}
