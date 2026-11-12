package test

import com.dshatz.passphrasemeter.PassphraseTester
import com.dshatz.passphrasemeter.PasswordStrength
import com.dshatz.passphrasemeter.pin.PinTestIssue
import com.dshatz.passphrasemeter.TestDictionaries
import com.dshatz.passphrasemeter.initialize
import com.dshatz.passphrasemeter.loadTestDictionary
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Based on wultra/passphrase-meter android instrumented test.
 *
 * https://github.com/wultra/passphrase-meter/blob/develop/Source/examples/Android/PassMeterExample/app/src/androidTest/java/com/wultra/passmeterexample/PassMeterInstrumentedTest.kt
 */
class PassMeterTest {

    @BeforeTest
    fun setup() {
        PassphraseTester.initialize()
    }

    @AfterTest
    fun teardown() {
        PassphraseTester.freeDictionary()
    }

    @Test
    fun testWrongInputPin() {
        assertFails {
            PassphraseTester.testPin("asdf")
        }
    }

    @Test
    fun testPinIssues() {
        val tester = PassphraseTester
        val frequent = tester.testPin("1111")
        assertContains(frequent.issues, PinTestIssue.FREQUENTLY_USED)
        val pattern = tester.testPin("1357")
        assertContains(pattern.issues, PinTestIssue.HAS_PATTERN)
        val date = tester.testPin("1990")
        assertContains(date.issues, PinTestIssue.POSSIBLY_DATE)
        val unique = tester.testPin("1112")
        assertContains(unique.issues, PinTestIssue.NOT_UNIQUE)
        val repeating = tester.testPin("1111")
        assertContains(repeating.issues, PinTestIssue.REPEATING_CHARACTERS)
    }

    @Test
    fun testOKPin() {
        val result = PassphraseTester.testPin("9562")
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun testPinDates() {
        val dates = arrayOf("0304", "1012", "0101", "1998", "2005", "150990", "241065", "16021998", "03122001", "2901", "2802")
        val noDates = arrayOf("1313", "0028", "1287", "9752", "151590", "001297", "41121987", "3002", "3102")

        val tester = PassphraseTester
        for (date in dates) {
            assertContains(tester.testPin(date).issues, PinTestIssue.POSSIBLY_DATE, message = date)
        }

        for (nodate in noDates) {
            assertFalse(tester.testPin(nodate).issues.contains(PinTestIssue.POSSIBLY_DATE), message = nodate)
        }
    }

    @Test
    fun testEnglishDictionary() {

        val tester = PassphraseTester

        val words = arrayOf(
            "international",
            "january",
            "development",
            "different",
            "television",
            "established",
            "championship",
            "performance",
            "municipality",
            "approximately",
            "background",
            "administrative"
        )

        for (word in words) {
            val result = tester.testPassword(word)
            assertTrue(result == PasswordStrength.GOOD || result == PasswordStrength.STRONG, message = word)
        }

        assertTrue { loadTestDictionary(TestDictionaries.EN) }

        for (word in words) {
            val result = tester.testPassword(word)
            assertTrue(result == PasswordStrength.VERY_WEAK || result == PasswordStrength.WEAK, message = word)
        }

        tester.freeDictionary()
    }

    @Test
    fun testCzskDictionary() {
        val tester = PassphraseTester
        val words = arrayOf(
            "spolecnosti",
            "rozcestnik",
            "rimskokatolicka",
            "ceskoslovenske",
            "historicke",
            "ostrava",
            "bratislava",
            "organizacie",
            "juhovychodnej",
            "demokratickej",
            "vydavatelstvo",
            "svajciarsko"
        )

        for (word in words) {
            val result = tester.testPassword(word)
            assertTrue(result == PasswordStrength.GOOD || result == PasswordStrength.STRONG)
        }

        assertTrue { loadTestDictionary(TestDictionaries.CZSK) }

        for (word in words) {
            val result = tester.testPassword(word)
            assertTrue(result == PasswordStrength.VERY_WEAK || result == PasswordStrength.WEAK)
        }

        tester.freeDictionary()
    }

    @Test
    fun testPasswords()  {
        val tester = PassphraseTester
        assertEquals(PasswordStrength.WEAK, tester.testPassword("qwerty")) // keyboard pattern
        assertEquals(PasswordStrength.VERY_WEAK, tester.testPassword("12345678")) // keyboard pattern
        assertEquals(PasswordStrength.VERY_WEAK, tester.testPassword("ap")) // too short
        assertEquals(PasswordStrength.WEAK, tester.testPassword("apwu")) // short
        assertEquals(PasswordStrength.GOOD, tester.testPassword("apwunb")) // OK
        assertEquals(PasswordStrength.STRONG, tester.testPassword("ap,wu92nbSm;#/")) // Strong
    }
}