package takagi.ru.monica.credentialexchange

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.providerevents.transfer.ProviderImportCredentialsRequest
import java.security.MessageDigest

object TransferCallingAppVerifier {
    /** Compare the SDK assertion to the installed package, including multi-signer packages. */
    @Suppress("DEPRECATION")
    fun verifiedLabel(context: Context, caller: CallingAppInfo): String? = runCatching {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = context.packageManager.getPackageInfo(caller.packageName, flags)
        val actual = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners?.toList().orEmpty()
            else installed.signatures?.toList().orEmpty()
        val reported = if (Build.VERSION.SDK_INT >= 28) caller.signingInfoCompat.apkContentsSigners
            else caller.signingInfoCompat.signingCertificateHistory
        fun fingerprints(signatures: List<android.content.pm.Signature>) = signatures.map {
            CxfCredentialCodec.base64Url(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()))
        }.toSet()
        if (actual.isEmpty() || fingerprints(actual) != fingerprints(reported)) return null
        val info = installed.applicationInfo ?: return null
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrNull()

    fun verifyExport(context: Context, request: ProviderImportCredentialsRequest): String? = runCatching {
        if (!CredentialExchangeRegistrar.matchesSecret(context, request.credId)) return null
        if (request.uri.scheme != "content" || request.uri.authority.isNullOrBlank()) return null
        val label = verifiedLabel(context, request.callingAppInfo) ?: return null
        val provider = context.packageManager.resolveContentProvider(requireNotNull(request.uri.authority), 0) ?: return null
        if (provider.packageName != request.callingAppInfo.packageName) return null
        if (context.checkUriPermission(request.uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            != PackageManager.PERMISSION_GRANTED) return null
        if (request.request.credentialTypes.none { it in CredentialExchangeRegistrar.supportedTypes }) return null
        label
    }.getOrNull()
}
