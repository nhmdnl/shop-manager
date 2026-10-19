package com.archsolutions.shopmanager.core.designsystem.components

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class AmountTransformationTest {

    private fun transform(digits: String) = AmountTransformation.filter(AnnotatedString(digits))

    @Test
    fun `shows separators but keeps the caret after the last digit typed`() {
        val shown = transform("12345")
        assertEquals("12,345", shown.text.text)
        // Typing the 5th digit used to land before the 4th ("12,354").
        assertEquals(6, shown.offsetMapping.originalToTransformed(5))
        assertEquals(5, shown.offsetMapping.originalToTransformed(4))
        assertEquals(2, shown.offsetMapping.originalToTransformed(2))
        assertEquals(0, shown.offsetMapping.originalToTransformed(0))
    }

    @Test
    fun `maps a caret on either side of a separator back to the same digit`() {
        val mapping = transform("1234567").offsetMapping // 1,234,567
        assertEquals(1, mapping.transformedToOriginal(1))
        assertEquals(1, mapping.transformedToOriginal(2))
        assertEquals(4, mapping.transformedToOriginal(6))
        assertEquals(7, mapping.transformedToOriginal(9))
    }

    @Test
    fun `leaves an empty box alone`() {
        val shown = transform("")
        assertEquals("", shown.text.text)
        assertEquals(0, shown.offsetMapping.originalToTransformed(0))
    }
}
