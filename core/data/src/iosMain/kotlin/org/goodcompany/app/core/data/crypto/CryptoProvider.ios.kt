package org.goodcompany.app.core.data.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.apple.Apple

/** Apple's CommonCrypto (PBKDF2, AES-CBC and HMAC; no Swift needed). */
internal actual fun backupCryptoProvider(): CryptographyProvider = CryptographyProvider.Apple
