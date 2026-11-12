package com.dshatz.passphrasemeter.pin

import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

internal object PinTester {

    /** Size of the "tested groups" buffer in the original C implementation (MAX_PIN_LENGTH / 2). */
    private const val MAX_TESTED_GROUPS = 50

    /**
     * @param currentYear year used by the date check (years within the last 90 years are treated
     * as possible birth years). Defaults to the current year.
     * @throws IllegalArgumentException if the PIN contains anything other than digits 0-9.
     */
    fun testPin(
        pin: String,
        minLength: Int = 4,
        maxLength: Int = 100,
        currentYear: Int = currentYear(),
    ): PinTestResult {
        val ln = pin.length
        if (ln < minLength) {
            return PinTestResult(ln, setOf(PinTestIssue.TOO_SHORT))
        }
        if (ln > maxLength) {
            return PinTestResult(ln, setOf(PinTestIssue.TOO_LONG))
        }
        require(pin.all { it in '0'..'9' }) { "PIN must contain only digits 0-9" }

        val issues = mutableSetOf<PinTestIssue>()
        if (pin in Pins.mostUsed) {
            issues += PinTestIssue.FREQUENTLY_USED
        }

        val chars = pin.toCharArray()
        try {
            if (!isUniqueOk(chars)) {
                issues += PinTestIssue.NOT_UNIQUE
            }

            if (!isPatternOk(chars)) {
                issues += PinTestIssue.HAS_PATTERN
            }

            if (!isRepeatingOk(chars)) {
                issues += PinTestIssue.REPEATING_CHARACTERS
            }
        } finally {
            chars.fill('\u0000') // don't leave a copy of the PIN in memory
        }

        if (!isDateOk(pin, currentYear)) {
            issues += PinTestIssue.POSSIBLY_DATE
        }

        return PinTestResult(
            pin.length,
            issues
        )
    }

    /**
     * Test if given pin has enough unique digits inside.
     *  For example for pin 1122 returns false, it only contains 2 unique digits.
     *
     *  @param digits Array with pin
     *  @return Returns if the are enough unique digits
     */
    private fun isUniqueOk(digits: CharArray): Boolean {
        val min = if (digits.size <= 7) 3 else 4
        return uniqueDigitsCount(digits) >= min
    }

    /**
     * Calculates how many different digits are in the array.
     * For example in array {1,5,6,5} there are 3 unique integer 1,5,6.
     *
     * @param digits Array with digits
     * @return Returns count of unique digits
     */
    private fun uniqueDigitsCount(digits: CharArray): Int {
        // The first assumption is that all digits are unique.
        var uniqueDigits = digits.size

        // Verify every digit with the following digits.
        for (i in digits.indices) {
            for (u in i + 1 until digits.size) {
                // If the same digits are found, mark this digit as not unique
                if (digits[i] == digits[u]) {
                    uniqueDigits--
                    break
                }
            }
        }
        return uniqueDigits
    }

    /**
     * Searches for patterns in the pin.
     * Patterns can be 1234, 3579, 5331, 2580, ...
     *
     * @param digits Array with the PIN
     * @return Returns if no patterns were found
     */
    private fun isPatternOk(digits: CharArray): Boolean {
        val pinLength = digits.size
        var totalSequence = 0

        // start searching for a pattern for where each digit can be the start of the pattern
        // search to pinLength-2, because the pattern needs to be at least 3 digits long
        var index = 0
        while (index < pinLength - 2) {

            // next number in the pattern. Adding 2 here to skip the first following.
            // 2 numbers cannot be a pattern, they just set how big the 'gap' in the pattern is
            var following = index + 2
            var sequence = 0 // how many numbers are involved in the pattern
            var repeating = 0 // when the number in the pattern is repeated (like 4556)

            val shift = digits[index + 1] - digits[index] // the gap between two digits of the pattern (it will be 4 for 1,5,9)

            while (following < pinLength) {
                if (digits[following] - digits[following - 1] == shift) {
                    // if the third, fourth, ... number has the same gap, it's a pattern
                    // if we found the first number in the pattern, add 3 as the minimum length for the pattern
                    sequence += if (sequence == 0) 3 else 1
                } else if (digits[following] == digits[following - 1]) {
                    // if the number is the same as previous, add it to pattern too
                    repeating++
                } else {
                    break // stop if the pattern is broken
                }
                following++
            }

            if (sequence != 0) {
                sequence += repeating
            }

            // if the sequence was found (minimum sequence length is 3)
            if (sequence >= 3) {
                index = following + 1 // move past the sequence
                totalSequence += sequence // add to total sequence count
            } else {
                index++
            }
        }
        return when {
            pinLength <= 5 -> totalSequence <= 2
            pinLength <= 6 -> totalSequence <= 3
            pinLength <= 8 -> totalSequence <= 4
            else -> totalSequence <= pinLength / 2
        }
    }

    /**
     * Searches for repeating digits in the pin.
     * For example, 692692 is repeating.
     *
     * @param digits Array with the PIN
     * @return Returns if there are no repeating digits
     */
    private fun isRepeatingOk(digits: CharArray): Boolean {
        val pinLength = digits.size
        var digitsRepeating = 0 // how many digits were found that are in patterns
        // groups that were tested: (start, length)
        val groupsTested = ArrayList<Pair<Int, Int>>()

        // this is the biggest length of a possible repeating pattern
        val maxLength = pinLength / 2

        // start searching for patterns with various lengths
        for (length in 2..maxLength) {

            // start searching for parts of the pin that could be repeated
            // following comments will be explaining searching in pin "12312" for length 2
            var start = 0
            while (start + length <= pinLength - length) {

                // check if this group was already tested; if so, skip it
                val groupWasTested = groupsTested.any { (groupStart, groupLength) ->
                    groupLength == length && arrayEquals(digits, start, digits, groupStart, length)
                }
                if (groupWasTested) {
                    break
                }

                // remember the group as tested
                if (groupsTested.size >= MAX_TESTED_GROUPS) {
                    return false
                }
                groupsTested += start to length

                var lastRepeatingFound = false

                // now start moving after the first group to search for repeating patterns
                var searchStart = start + length
                while (searchStart + length <= pinLength) {

                    // now check if the two groups are equals
                    // for the first loop {3,1}, they won't. for second {1,2}, they will. Also, we're checking for
                    // inverted order of the second group to potentially match {1,2,3,2,1}
                    if (arrayEquals(digits, start, digits, searchStart, length) ||
                        arrayEqualsInv(digits, start, digits, searchStart, length)
                    ) {
                        // we found a repeating pattern! save how many digits are part of this pattern
                        digitsRepeating += length
                        // move searchStart behind this pattern we just found
                        searchStart += length
                        // mark as found
                        lastRepeatingFound = true
                    } else {
                        searchStart++ // continue searching
                    }
                }
                if (lastRepeatingFound) {
                    digitsRepeating += length
                    start += length
                } else {
                    start++
                }
            }
        }

        return digitsRepeating <= uniqueDigitsCount(digits)
    }

    /**
     * Compares two array slices of the same [length].
     */
    private fun arrayEquals(ar1: CharArray, ar1start: Int, ar2: CharArray, ar2start: Int, length: Int): Boolean {
        for (i in 0 until length) {
            if (ar1[ar1start + i] != ar2[ar2start + i]) {
                return false
            }
        }
        return true
    }

    /**
     * Compares two array slices of the same [length]; the 2nd slice is processed in reverse order.
     */
    private fun arrayEqualsInv(ar1: CharArray, ar1start: Int, ar2: CharArray, ar2start: Int, length: Int): Boolean {
        for (i in 0 until length) {
            if (ar1[ar1start + i] != ar2[ar2start + length - i - 1]) {
                return false
            }
        }
        return true
    }

    /**
     * Checks, if the pin could be a date in various formats.
     * This check should be presented to the user only as a mild warning,
     * for example: "This pin looks like a date, is it your birthday?"
     *
     * It checks following formats: mmdd, ddmm, mmddyy, ddmmyy, mmddyyyy, ddmmyyyy, yyyy.
     * For years, it only checks years between currentYear - 90 and currentYear.
     *
     * @param pin String with the PIN
     * @return Returns if the PIN is not a date
     */
    private fun isDateOk(pin: String, currentYear: Int): Boolean {
        fun parse(format: String) = parseDate(pin, format, currentYear)
        // only check for pins with even length
        return when (pin.length) {
            // If the PIN could be date like 0304 (3rd of April or 4th of March)
            // If the PIN could be valid year (like 1982), that could be year of birth
            4 -> !(parse("dm") || parse("md") || parse("Y"))
            // If the PIN could be a date with a year (like 121091 that could be 12th of October or 10th of December 1991)
            6 -> !(parse("dmy") || parse("mdy"))
            // If the PIN could be a date with a year (like 12101991 that could be 12th of October or 10th of December 1991)
            8 -> !(parse("dmY") || parse("mdY"))
            else -> true
        }
    }

    /**
     * Parses the given date in the chosen format (Y = 4-digit year, y = 2-digit year, m = month, d = day).
     *
     * @param date Date string
     * @param format Format of the date
     * @return If parsing was successful
     */
    private fun parseDate(date: String, format: String, currentYear: Int): Boolean {
        var year = -1
        var month = -1
        var day = -1
        var pos = 0
        var f = 0
        while (f < format.length && pos < date.length) {
            when (format[f++]) {
                'Y' -> {
                    year = parseYear(date, pos, currentYear)
                    if (year < 0) return false
                    pos += 4
                }
                'y' -> {
                    year = parseShortYear(date, pos, currentYear)
                    if (year < 0) return false
                    pos += 2
                }
                'm' -> {
                    month = parseMonth(date, pos)
                    if (month < 0) return false
                    pos += 2
                }
                'd' -> {
                    day = parseDay(date, pos)
                    if (day < 0) return false
                    pos += 2
                }
                else -> return false
            }
        }
        if (day > 0 && month > 0) {
            return isDayInMonthOk(day, month, year)
        }
        return true
    }

    /**
     * Parses [size] digits starting at [start].
     * @return the parsed number, or -1 if there are fewer than [size] digits.
     */
    private fun parseInt(date: String, start: Int, size: Int): Int {
        if (start + size > date.length) return -1
        var value = 0
        for (i in start until start + size) {
            val c = date[i]
            if (c !in '0'..'9') return -1
            value = value * 10 + (c - '0')
        }
        return value
    }

    /** @return the 4-digit year at [start], or -1 if it is out of the expected range. */
    private fun parseYear(date: String, start: Int, currentYear: Int): Int {
        val year = parseInt(date, start, 4)
        if (year < 0) return -1
        return if (isYearOk(year, isShort = false, currentYear)) year else -1
    }

    /** @return the 2-digit year at [start] as a full year, or -1 if it is out of the expected range. */
    private fun parseShortYear(date: String, start: Int, currentYear: Int): Int {
        val shortYear = parseInt(date, start, 2)
        if (shortYear < 0) return -1
        // The "Year 2000: The Millennium Rollover" paper suggests that
        // values in the range 69-99 refer to the twentieth century.
        // Ref: https://sources.debian.org/src/glibc/2.35-2/time/strptime_l.c/#L755
        val year = if (shortYear >= 69) 1900 + shortYear else 2000 + shortYear
        return if (isYearOk(year, isShort = true, currentYear)) year else -1
    }

    /** @return the month at [start], or -1 if it is not a month. */
    private fun parseMonth(date: String, start: Int): Int {
        val m = parseInt(date, start, 2)
        return if (m < 1 || m > 12) -1 else m
    }

    /** @return the day at [start], or -1 if it is not a day. */
    private fun parseDay(date: String, start: Int): Int {
        val d = parseInt(date, start, 2)
        return if (d < 1 || d > 31) -1 else d
    }

    /**
     * Checks if the year in the date should be considered as a pattern.
     */
    private fun isYearOk(year: Int, isShort: Boolean, currentYear: Int): Boolean {
        var y = year
        // We don't expect future dates, so if year is greater than current, then
        // just assume it's in past.
        if (isShort && y > currentYear) {
            y -= 100
        }
        // Check if the year is more than the current year or less than current minus 90.
        return y >= currentYear - 90 && y <= currentYear
    }

    private fun isLeapYear(year: Int): Boolean = when {
        year % 4 != 0 -> false // Not divisible by 4, so it's not leap year.
        year % 100 != 0 -> true // divisible by 4, but not divisible by 100
        else -> year % 400 == 0 // If is also divisible by also 400, then it's leap year.
    }

    /**
     * Determines whether day in month is OK.
     * @param year Year, if -1, then year is not known.
     */
    private fun isDayInMonthOk(day: Int, month: Int, year: Int): Boolean {
        if (day < 1) return false
        return when (month) {
            4, 6, 9, 11 -> day <= 30 // april, june, september, november
            // feb: if year is not provided, then both variants are valid
            2 -> day <= if (year < 0 || isLeapYear(year)) 29 else 28
            else -> day <= 31
        }
    }

    /** Current year in the system time zone (like localtime() in the original C implementation). */
    private fun currentYear(): Int = Clock.System.todayIn(TimeZone.currentSystemDefault()).year
}


private object Pins {
    val mostUsed = setOf(
        "1234","1111","0000","1212","7777","1004","2000","4444","2222","6969","9999","3333","5555","6666","1122","1313","8888",
        "2001","4321","1010","0909","2580","0007","1818","1230","1984","1986","0070","1985","0987","1000","1231","1987","1999",
        "2468","2002","2323","0123","1123","1233","1357","1221","1324","1988","2112","2121","5150","1024","1112","1224","1969",
        "1225","1235","1982","1983","1001","1978","1979","7410","1020","1223","1974","1975","1977","1980","1981","1029","1121",
        "1213","1973","1976","2020","2345","2424","2525","1515","1970","1972","1989","0001","1023","1414","9876","0101","0907",
        "1245","1966","1967","1971","8520","1964","1968","4545","1318","5678","1011","1124","1211","1963","4200",

        "12345","123456","1234567","12345678","123456789","1234567890","11111","123123","7777777","11111111","987654321",
        "0123456789","55555","111111","1111111","88888888","123123123","0987654321","00000","121212","8675309","87654321",
        "789456123","1111111111","54321","123321","1234321","00000000","999999999","1029384756","13579","666666","0000000",
        "12341234","147258369","9876543210","77777","000000","4830033","69696969","741852963","0000000000","22222","654321",
        "7654321","12121212","111111111","1357924680","12321","696969","5201314","11223344","123454321","1122334455","99999",
        "112233","0123456","12344321","123654789","1234512345","33333","159753","2848048","77777777","147852369","1234554321",
        "00700","292513","7005425","99999999","111222333","5555555555","90210","131313","1080413","22222222","963852741",
        "1212121212","88888","123654","7895123","55555555","321654987","9999999999","38317","222222","1869510","33333333",
        "420420420","2222222222","09876","789456","3223326","44444444","007007007","7777777777","44444","999999","1212123",
        "66666666","135792468","3141592654","98765","101010","1478963","11112222","397029049","3333333333","01234","777777",
        "2222222","13131313","012345678","7894561230","42069","007007","5555555","10041004","123698745","1234567891"
    )
}