package takagi.ru.monica.credentialexchange

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import androidx.core.content.FileProvider
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.providerevents.IntentHandler
import androidx.credentials.providerevents.transfer.ImportCredentialsRequest
import androidx.credentials.providerevents.transfer.ImportCredentialsResponse
import androidx.credentials.providerevents.transfer.ProviderImportCredentialsRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.MessageDigest
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.passkey.PasskeyBrowserOrigin
import takagi.ru.monica.passkey.PasskeyOriginResolver
import takagi.ru.monica.security.SecurityManager

@RunWith(AndroidJUnit4::class)
class TransferSecurityInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun exporterResolvesTheMimeTypeInferredFromTheSdkTransferUri() {
        val directory = File(context.cacheDir, "import_export_temp").apply { mkdirs() }
        val file = File(directory, "exchange-resolution-${UUID.randomUUID()}").apply { createNewFile() }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.importexport.provider", file)
            assertEquals("application/octet-stream", context.contentResolver.getType(uri))
            val request = Intent("androidx.identitycredentials.action.IMPORT_CREDENTIALS")
                .setData(uri).setPackage(context.packageName)
            val resolved = context.packageManager.resolveActivity(request, PackageManager.MATCH_DEFAULT_ONLY)
            assertEquals("The same implicit request sent by Google must resolve an exporter",
                CredentialExportActivity::class.java.name, resolved?.activityInfo?.name)
        } finally { file.delete() }
    }

    @Test fun exporterValidatesSecretCallerSignatureUriOwnershipAndRequestedTypes() {
        val security = SecurityManager(context)
        val secretKey = "credential_exchange_export_entry_v1"
        val previous = security.getProtectedString(secretKey)
        val secret = CxfCredentialCodec.base64Url(ByteArray(32) { (it + 73).toByte() })
        val receiver = Uri.parse("content://takagi.ru.monica.test.exchange-receiver")
        val token = UUID.randomUUID().toString()
        val uri = receiver.buildUpon().appendPath(token).build()
        try {
            security.putProtectedString(secretKey, secret)
            context.contentResolver.call(receiver, "create", token, null)
            val receiverPackage = InstrumentationRegistry.getInstrumentation().context.packageName
            val signing = context.packageManager.getPackageInfo(receiverPackage, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo!!
            val caller = CallingAppInfo(receiverPackage, signing)
            val types = ImportCredentialsRequest(setOf("basic-auth", "passkey"), emptySet())
            fun request(target: Uri = uri, app: CallingAppInfo = caller, id: String = secret, selection: ImportCredentialsRequest = types) =
                ProviderImportCredentialsRequest(selection, app, target, id)
            assertTrue("The registered secret must match", CredentialExchangeRegistrar.matchesSecret(context, secret))
            assertNotNull("The installed caller signature must match", TransferCallingAppVerifier.verifiedLabel(context, caller))
            val provider = context.packageManager.resolveContentProvider(uri.authority!!, 0)!!
            assertEquals(receiverPackage, provider.packageName)
            assertNotEquals("Use a real recipient in a different UID", Process.myUid(), provider.applicationInfo.uid)
            assertEquals(PackageManager.PERMISSION_GRANTED, context.checkUriPermission(uri, Process.myPid(),
                Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
            assertNotNull(TransferCallingAppVerifier.verifyExport(context, request()))
            assertNull(TransferCallingAppVerifier.verifyExport(context, request(id = "not-the-secret")))
            assertNull(TransferCallingAppVerifier.verifyExport(context, request(target = Uri.parse("file:///synthetic.json"))))
            assertNull(TransferCallingAppVerifier.verifyExport(context, request(target = Uri.parse("content://settings/system"))))
            val wrongSignature = context.packageManager.getPackageInfo("android", PackageManager.GET_SIGNING_CERTIFICATES).signingInfo!!
            assertNull(TransferCallingAppVerifier.verifyExport(context, request(app = CallingAppInfo(receiverPackage, wrongSignature))))
            assertNull(TransferCallingAppVerifier.verifyExport(context, request(selection = ImportCredentialsRequest(setOf("credit-card"), emptySet()))))
            fun received() = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            assertEquals("untouched", received())
            // Exercise the real AndroidX URI transport after an independently granted one-shot authorization.
            val auth = ExportAuthorization()
            assertFalse(auth.consume("LOCAL:0", 0))
            auth.grant("LOCAL:0", 1)
            assertTrue(auth.consume("LOCAL:0", 2))
            val payload = CxfCredentialCodec.encode(emptyList(), "Synthetic", setOf("basic-auth"))
            val result = Intent()
            IntentHandler.setImportCredentialsResponse(context, uri, result, ImportCredentialsResponse(payload))
            assertEquals(payload, received())
            assertFalse(auth.consume("LOCAL:0", 3))
            assertFalse(result.extras?.toString().orEmpty().contains(secret))
            context.contentResolver.call(receiver, "release", token, null)
            assertNull("A revoked grant must reject another export", TransferCallingAppVerifier.verifyExport(context, request()))
        } finally {
            security.putProtectedString(secretKey, previous)
            context.contentResolver.call(receiver, "release", token, null)
        }
    }

    @Test fun newAndroidxOriginApiKeepsChromeAndNativeAppPasskeysCompatible() {
        val pm = context.packageManager
        val chrome = pm.getPackageInfo("com.android.chrome", PackageManager.GET_SIGNING_CERTIFICATES)
        val browserCaller = CallingAppInfo("com.android.chrome", chrome.signingInfo!!, "https://login.example.com")
        assertEquals("https://login.example.com", PasskeyBrowserOrigin.read(context, browserCaller))
        val browserOrigin = PasskeyOriginResolver.resolveOriginWithMeta(context, "{}", browserCaller, "example.com")
        assertEquals("https://login.example.com", browserOrigin.origin)
        assertEquals(PasskeyOriginResolver.Source.CALLING_APP_ORIGIN, browserOrigin.source)

        val native = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val nativeCaller = CallingAppInfo(context.packageName, native.signingInfo!!)
        val hash = CxfCredentialCodec.base64Url(MessageDigest.getInstance("SHA-256").digest(native.signingInfo!!.apkContentsSigners[0].toByteArray()))
        assertNull(PasskeyBrowserOrigin.read(context, nativeCaller))
        assertEquals("android:apk-key-hash:$hash", PasskeyOriginResolver.resolveOrigin(context, "{}", nativeCaller, "example.com"))
        assertNull(PasskeyBrowserOrigin.read(context, CallingAppInfo(context.packageName, native.signingInfo!!, "https://example.com")))
    }

    @Test fun androidAppScopeKeepsCertificateConstraintsAcrossAllDestinations() = runBlocking {
        val fixture = TransferFixture()
        try { with(fixture) {
            val signature = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo!!.apkContentsSigners[0]
            val hash = CxfCredentialCodec.base64Url(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()))
            val apps = Json.parseToJsonElement("""[{"bundleId":"${context.packageName}","name":"Monica","certificate":{"hashAlg":"sha256","fingerprint":"$hash"}}]""").jsonArray
            val source = decoded().let { it.copy(items = it.items.map { item -> item.copy(androidApps = apps) }) }
            for (target in listOf(ImportDestination.Local, keepass(), mdbx(), bitwarden())) {
                assertEquals("${target.kind}: import", 2, importer.importExchange(source, target).imported)
                val storedPasswords = importedPasswords(target)
                assertEquals("${target.kind}: stored passwords", 1, storedPasswords.size)
                assertEquals(context.packageName, storedPasswords.single().appPackageName)
                val exported = CredentialExchangeExporter(context).prepare(target, setOf("basic-auth"))
                val exportedPasswords = CxfCredentialCodec.decode(exported.json).items.filter { it.title.startsWith(prefix) }
                assertEquals("${target.kind}: exported password items", 1, exportedPasswords.size)
                assertEquals(apps, exportedPasswords.single().androidApps)
                assertEquals(0, importer.importExchange(source, target).imported)
            }
            val badApps = Json.parseToJsonElement(apps.toString().replace(hash, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")).jsonArray
            val badSource = source.copy(items = source.items.map { it.copy(title = "$prefix-wrong-certificate", passkeys = emptyList(), androidApps = badApps) })
            assertEquals(1, importer.importExchange(badSource, ImportDestination.Local).imported)
            val stored = importedPasswords(ImportDestination.Local).single { it.title == "$prefix-wrong-certificate" }
            assertEquals("An unverified certificate must not become an unconditional app binding", "", stored.appPackageName)
            assertEquals(badApps.toString(), db.customFieldDao().getFieldsByEntryIds(listOf(stored.id))
                .single { it.title == CxfAndroidAppScope.FIELD_NAME }.value)
        } } finally { fixture.close() }
    }
}
