package com.dshatz.passphrasemeter.pin

import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/**
 * Compares [PinTester] with the results of Wultra's C implementation for every 4-7 digit PIN
 * (wultra/passphrase-meter Source/tests/testfile.txt, downloaded by scripts/build-dictionaries.sh).
 *
 * JVM only: PinTester is plain common Kotlin, so one platform is enough.
 */
class PinReferenceTest {

    /** Year of CONSISTENCY_TEST_TIME (1551049200), when Wultra generated the file. */
    private val referenceYear = 2019

    @Test
    fun matchesWultraReference() {
        val stream = javaClass.getResourceAsStream("/wultra-pins.txt")
            ?: error("wultra-pins.txt is not on the test classpath - run scripts/build-dictionaries.sh first")

        val checked = measureTimedValue {
            var checked = 0
            val mismatches = mutableListOf<String>()
            stream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.isBlank()) continue
                    val (pin, expected) = line.split('-')
                    val actual = PinTester.testPin(pin, currentYear = referenceYear).toWultraMask()
                    if (actual != expected.toInt()) mismatches += "$pin: expected $expected, got $actual"
                    checked++
                }
            }
            assertTrue(checked > 1_000_000, "only $checked PINs in the reference file")
            assertEquals(emptyList(), mismatches.take(20), "${mismatches.size} of $checked PINs differ")
            checked
        }
        println("Checked ${checked.value} pins in ${checked.duration}: ${checked.duration / checked.value} per pin")

    }

    /** WPM_PasscodeResult bits from wultra/passphrase-meter wpm_types.h. */
    private fun PinTestResult.toWultraMask(): Int {
        var mask = 0
        for (issue in issues) {
            mask = mask or when (issue) {
                PinTestIssue.NOT_UNIQUE -> 1 shl 1
                PinTestIssue.REPEATING_CHARACTERS -> 1 shl 2
                PinTestIssue.HAS_PATTERN -> 1 shl 3
                PinTestIssue.POSSIBLY_DATE -> 1 shl 4
                PinTestIssue.FREQUENTLY_USED -> 1 shl 5
                PinTestIssue.TOO_SHORT, PinTestIssue.TOO_LONG -> 1 shl 6 // WrongInput
            }
        }
        return if (mask == 0) 1 shl 0 else mask // Ok
    }
}
