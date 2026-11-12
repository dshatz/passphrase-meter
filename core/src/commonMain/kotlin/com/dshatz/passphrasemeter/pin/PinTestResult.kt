package com.dshatz.passphrasemeter.pin

import com.dshatz.kni.annotations.JniSerializable

@JniSerializable
data class PinTestResult(
    val pinLength: Int,
    val issues: Set<PinTestIssue>
)