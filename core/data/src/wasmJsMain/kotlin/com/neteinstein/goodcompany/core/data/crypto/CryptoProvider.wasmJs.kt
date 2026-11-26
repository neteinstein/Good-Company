package com.neteinstein.goodcompany.core.data.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.webcrypto.WebCrypto

/** The browser's WebCrypto (`crypto.subtle`). */
internal actual fun backupCryptoProvider(): CryptographyProvider = CryptographyProvider.WebCrypto
