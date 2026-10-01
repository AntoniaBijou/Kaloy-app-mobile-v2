package com.kaloy.app.core.util

import java.time.LocalDate

/** Android : java.time suffit, LocalDate.now() utilise deja le fuseau du telephone. */
actual fun aujourdHuiLocal(): String = LocalDate.now().toString()
