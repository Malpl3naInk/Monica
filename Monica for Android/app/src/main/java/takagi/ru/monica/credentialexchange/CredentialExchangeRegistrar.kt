package takagi.ru.monica.credentialexchange

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.credentials.providerevents.ProviderEventsManager
import androidx.credentials.providerevents.transfer.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.security.SecurityManager
import java.security.MessageDigest
import java.security.SecureRandom

object CredentialExchangeRegistrar {
    val supportedTypes = setOf(CredentialTypes.CREDENTIAL_TYPE_BASIC_AUTH, CredentialTypes.CREDENTIAL_TYPE_PUBLIC_KEY)
    private const val SECRET_KEY = "credential_exchange_export_entry_v1"
    private val mutex = Mutex()

    /** A single stable app entry opens Monica's source selector; it never implies an unlocked vault. */
    suspend fun register(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val security = SecurityManager(context)
                val secret = security.getProtectedString(SECRET_KEY) ?: CxfCredentialCodec.base64Url(
                    ByteArray(32).also { SecureRandom().nextBytes(it) }
                ).also { security.putProtectedString(SECRET_KEY, it) }
                val drawable = context.packageManager.getApplicationIcon(context.applicationInfo)
                val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, bitmap.width, bitmap.height)
                drawable.draw(Canvas(bitmap))
                val entry = ExportEntry(
                    id = secret, accountDisplayName = "Monica",
                    userDisplayName = context.getString(R.string.exchange_source), icon = bitmap,
                    supportedCredentialTypes = supportedTypes,
                )
                ProviderEventsManager.create(context).registerExport(RegisterExportRequest.create(context, listOf(entry)))
                CredentialExchangeErrors.record(context, CredentialExchangeErrors.Operation.REGISTER)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Keep the vault usable, but retain the failure category in exported diagnostics.
                CredentialExchangeErrors.record(context, CredentialExchangeErrors.Operation.REGISTER, error)
            }
        }
    }

    fun matchesSecret(context: Context, received: String): Boolean {
        if (received.length !in 32..128) return false
        val expected = SecurityManager(context).getProtectedString(SECRET_KEY) ?: return false
        return MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), received.toByteArray(Charsets.UTF_8))
    }
}
