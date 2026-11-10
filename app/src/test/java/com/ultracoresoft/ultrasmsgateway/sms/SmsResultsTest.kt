package com.ultracoresoft.ultrasmsgateway.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsResultsTest {

    @Test
    fun resultOkIsSuccess() {
        assertTrue(SmsResults.isSuccess(-1))
        assertFalse(SmsResults.isSuccess(1))
    }

    @Test
    fun knownCodesKeepTheirAndroidName() {
        assertEquals("RESULT_ERROR_GENERIC_FAILURE", SmsResults.reason(1))
        assertEquals("RESULT_ERROR_NO_SERVICE", SmsResults.reason(4))
    }

    @Test
    fun unknownCodesKeepTheirNumber() {
        assertEquals("RESULT_ERROR_999", SmsResults.reason(999))
    }

    @Test
    fun deliveryStatusFollowsTpStatus() {
        assertTrue(SmsResults.isDelivered(0x00))
        assertFalse(SmsResults.isDelivered(0x20))
        assertFalse(SmsResults.isDelivered(0x40))
    }
}
