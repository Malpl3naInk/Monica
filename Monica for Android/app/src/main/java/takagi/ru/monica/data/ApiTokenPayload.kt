package takagi.ru.monica.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI

/** Native CLI payload. Keep unknown extensions when editing a supported schema. */
object ApiTokenPayload {
    const val NATIVE_TYPE = "api-token"
    const val SCHEMA = "monica.gateway.credential.v1"
    const val MAX_BYTES = 16 * 1024

    fun decode(value: String): JsonObject? = runCatching {
        if (value.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return null
        val fields = Json.parseToJsonElement(value) as? JsonObject ?: return null
        if (text(fields, "schema") != SCHEMA) return null
        if (listOf("provider", "api_base", "token").any {
                (fields[it] as? JsonPrimitive)?.isString != true
            }) return null
        if (fields.containsKey("note") && (fields["note"] as? JsonPrimitive)?.isString != true) return null
        fields
    }.getOrNull()

    fun text(fields: JsonObject?, key: String): String =
        (fields?.get(key) as? JsonPrimitive)?.content.orEmpty()

    fun update(value: String, key: String, text: String): String {
        // An unfinished edit may exceed validation limits. Keep its other fields
        // so correcting that input never silently discards extensions or secrets.
        val current = runCatching { Json.parseToJsonElement(value) as? JsonObject }.getOrNull()
            ?.takeIf { ApiTokenPayload.text(it, "schema") == SCHEMA } ?: return value
        val fields = current.toMutableMap()
        fields[key] = JsonPrimitive(text)
        return JsonObject(fields).toString()
    }

    fun empty(): JsonObject = JsonObject(mapOf(
        "schema" to JsonPrimitive(SCHEMA),
        "provider" to JsonPrimitive("gitlab"),
        "api_base" to JsonPrimitive("https://gitlab.com/api/v4/"),
        "note" to JsonPrimitive(""),
        "token" to JsonPrimitive("")
    ))

    fun isValid(value: String): Boolean {
        val fields = decode(value) ?: return false
        val token = text(fields, "token")
        val uri = runCatching { URI(text(fields, "api_base")) }.getOrNull() ?: return false
        val provider = text(fields, "provider")
        val note = text(fields, "note")
        val validPath = when (provider) {
            "gitlab" -> uri.rawPath == "/api/v4/"
            "github" -> uri.rawPath in setOf("", "/", "/api/v3/")
            else -> false
        }
        return validPath && text(fields, "api_base").toByteArray(Charsets.UTF_8).size <= 2048 &&
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null &&
            token.length in 16..4096 && token.all { it.code in 33..126 } &&
            note.toByteArray(Charsets.UTF_8).size <= 1024 &&
            note.none { it.isISOControl() || it in '\u202a'..'\u202e' || it in '\u2066'..'\u2069' } &&
            !note.contains(token) && !text(fields, "api_base").contains(token)
    }

    fun isValidName(value: String): Boolean = value.length in 1..64 &&
        value.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }
}

data class NativeApiTokenSummary(
    val databaseId: Long,
    val entryId: String,
    val collectionId: String,
    val collectionTitle: String,
    val title: String,
    val ancestorCollectionIds: List<String> = emptyList(),
    val isFavorite: Boolean = false
)

// Deliberately no generated toString(): payloads must never appear in diagnostic output.
class NativeApiToken(val summary: NativeApiTokenSummary, val payload: String)
