package takagi.ru.monica.credentialexchange

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.credentials.providerevents.ProviderEventsManager
import androidx.credentials.providerevents.transfer.ExportEntry
import androidx.credentials.providerevents.transfer.ImportCredentialsRequest
import androidx.credentials.providerevents.transfer.RegisterExportRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.security.SecurityManager

/** Opt-in live-service probe. It never starts the picker or reads a credential vault. */
@RunWith(AndroidJUnit4::class)
class CredentialExchangePlatformProbeTest {
    @Test fun registrationAndImportPreflight() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("probeCredentialExchange") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report = StringBuilder()
        val security = SecurityManager(context)
        val secretKey = "credential_exchange_export_entry_v1"
        val secret = security.getProtectedString(secretKey) ?: CxfCredentialCodec.base64Url(
            ByteArray(32).also { SecureRandom().nextBytes(it) }
        ).also { security.putProtectedString(secretKey, it) }
        val request = RegisterExportRequest.create(context, listOf(ExportEntry(
            id = secret,
            accountDisplayName = "Monica",
            userDisplayName = context.getString(takagi.ru.monica.R.string.exchange_source),
            icon = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888),
            supportedCredentialTypes = CredentialExchangeRegistrar.supportedTypes,
        )))
        val pm = context.packageManager
        report.appendLine("sdk=${android.os.Build.VERSION.SDK_INT}")
        report.appendLine("gms=${pm.getPackageInfo("com.google.android.gms", 0).versionName}")
        report.appendLine("matcherBytes=${request.exportMatcher.size}")
        val transferDir = File(context.cacheDir, "import_export_temp").apply { mkdirs() }
        val transferFile = File.createTempFile("exchange-probe-", ".json", transferDir)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.importexport.provider", transferFile)
        try {
            try {
                withTimeout(20_000) { ProviderEventsManager.create(context).registerExport(request) }
                report.appendLine("androidx.register=success")
            } catch (error: Exception) {
                report.appendLine("androidx.register=${error.javaClass.simpleName}; ${safeReason(error)}")
            }

            // AndroidX drops the underlying ApiException on import. Inspect the same runtime
            // client only in this probe, without adding another production API/dependency.
            val manager = Class.forName("com.google.android.gms.identitycredentials.IdentityCredentialManager")
            val client = manager.getMethod("getClient", Context::class.java).invoke(null, context)
            val clientType = Class.forName("com.google.android.gms.identitycredentials.IdentityCredentialClient")
            val registerType = Class.forName("com.google.android.gms.identitycredentials.RegisterExportRequest")
            val rawRegister = registerType.getConstructor(ByteArray::class.java, ByteArray::class.java, String::class.java)
                .newInstance(request.exportMatcher, request.credentialBytes, "credential_transfer")
            val registerTask = clientType.getMethod("registerExport", registerType).invoke(client, rawRegister)
            val registered = awaitTask(registerTask, "gms.register", report)

            val importType = Class.forName("com.google.android.gms.identitycredentials.ImportCredentialsRequest")
            val importRequest = ImportCredentialsRequest(CredentialExchangeRegistrar.supportedTypes, emptySet())
            val rawImport = importType.getConstructor(String::class.java, Uri::class.java)
                .newInstance(importRequest.requestJson, uri)
            val importTask = clientType.getMethod("importCredentials", importType).invoke(client, rawImport)
            val imported = awaitTask(importTask, "gms.import", report)
            assertTrue("Live registration failed; see credential-exchange-platform-probe.txt", registered)
            assertTrue("Live import preflight failed; see credential-exchange-platform-probe.txt", imported)
        } finally {
            transferFile.delete()
            File(context.getExternalFilesDir(null), "credential-exchange-platform-probe.txt").writeText(report.toString())
            // Restore the application's actual icon and entry metadata after the probe.
            withTimeout(20_000) { CredentialExchangeRegistrar.register(context) }
        }
    }

    private fun awaitTask(task: Any, operation: String, report: StringBuilder): Boolean {
        val taskType = Class.forName("com.google.android.gms.tasks.Task")
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (taskType.getMethod("isComplete").invoke(task) != true && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50)
        }
        if (taskType.getMethod("isComplete").invoke(task) != true) {
            report.appendLine("$operation=timeout")
            return false
        }
        val error = taskType.getMethod("getException").invoke(task) as? Exception
        if (error == null) {
            report.appendLine("$operation=success")
            return true
        }
        val status = runCatching { error.javaClass.getMethod("getStatusCode").invoke(error) }.getOrNull()
        report.appendLine("$operation=${error.javaClass.simpleName}; status=$status; ${safeReason(error)}")
        return false
    }

    /** Only fixed diagnostic labels are written; messages can contain request secrets. */
    private fun safeReason(error: Exception): String {
        val message = error.message.orEmpty().lowercase()
        return listOf("allowlist", "whitelist", "not allowed", "permission", "developer_error", "api_disabled",
            "not available", "unsupported", "unknown", "invalid", "network", "caller", "feature")
            .filter { it in message }.joinToString(",").ifEmpty { "unclassified" }
    }
}
