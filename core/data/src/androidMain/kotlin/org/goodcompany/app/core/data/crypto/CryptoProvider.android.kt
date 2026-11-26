package org.goodcompany.app.core.data.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.jdk.JDK

/** The JDK's own crypto (Conscrypt on Android). */
internal actual fun backupCryptoProvider(): CryptographyProvider = CryptographyProvider.JDK
