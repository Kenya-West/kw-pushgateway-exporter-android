package com.kw.pushgatewayexporter.reliability.oem

import org.junit.Assert.assertEquals
import org.junit.Test

class OemProfileResolverTest {

    @Test fun `Xiaomi device matches Xiaomi profile`() {
        val p = OemProfileResolver.resolve(manufacturer = "Xiaomi", brand = "Redmi", fingerprint = "xiaomi/redmi", hardware = "qcom")
        assertEquals("xiaomi", p.id)
    }

    @Test fun `Huawei device matches huawei profile even via honor sub-brand`() {
        val p = OemProfileResolver.resolve(manufacturer = "HONOR", brand = "honor", fingerprint = "honor/magicos", hardware = "kirin")
        assertEquals("huawei", p.id)
    }

    @Test fun `Samsung matches samsung`() {
        val p = OemProfileResolver.resolve(manufacturer = "samsung", brand = "samsung", fingerprint = "samsung/oneui", hardware = "exynos")
        assertEquals("samsung", p.id)
    }

    @Test fun `Realme falls under oppo profile`() {
        val p = OemProfileResolver.resolve(manufacturer = "realme", brand = "realme", fingerprint = "realme", hardware = "mt6")
        assertEquals("oppo", p.id)
    }

    @Test fun `Unknown manufacturer falls back to generic`() {
        val p = OemProfileResolver.resolve(manufacturer = "MadeUpBrand", brand = "xyz", fingerprint = "x/y/z", hardware = "abc")
        assertEquals("generic", p.id)
    }

    @Test fun `Null inputs fall back to generic`() {
        val p = OemProfileResolver.resolve(manufacturer = null, brand = null, fingerprint = null, hardware = null)
        assertEquals("generic", p.id)
    }

    @Test fun `Case-insensitive matching`() {
        val p = OemProfileResolver.resolve(manufacturer = "XIAOMI", brand = "POCO", fingerprint = "", hardware = "")
        assertEquals("xiaomi", p.id)
    }
}
