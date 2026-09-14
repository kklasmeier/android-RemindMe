package com.phoenix.remindme.ai

import com.phoenix.remindme.alerts.AlertPreferences
import com.phoenix.remindme.data.BaseType
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.YearMonth
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ReminderDraft(
    val title: String,
    val baseType: BaseType,
    val triggerTime: Long?,
    val locationName: String?,
    val locationOffsetMinutes: Int,
    val isRecurring: Boolean,
    val recurrenceRule: String?,
    val alertFullScreen: Boolean,
    val alertSound: Boolean,
    val alertHeadsUp: Boolean,
    val alertLockScreen: Boolean,
)

class AiServiceException(
    message: String,
    val kind: Kind,
) : IOException(message) {
    enum class Kind { NO_KEY, UNAUTHORIZED, NETWORK, TIMEOUT, SERVER, INVALID_RESPONSE }
}

@Singleton
class AiRepository @Inject constructor(
    private val apiKeys: ApiKeyRepository,
    private val alertPreferences: AlertPreferences,
) {
    suspend fun ping(candidateKey: String): Unit = withContext(Dispatchers.IO) {
        request(
            apiKey = candidateKey,
            prompt = "Reply with OK.",
            maxTokens = 8,
        )
    }

    suspend fun parse(transcript: String): ReminderDraft = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val localSchedule = NaturalLanguageSchedule.parse(transcript, Instant.ofEpochMilli(now), zone)
        val key = apiKeys.getKey()
            ?: throw AiServiceException("Claude API key is not configured", AiServiceException.Kind.NO_KEY)
        val alerts = alertPreferences.get()
        val scheduleInstruction = localSchedule?.let {
            """
            A deterministic local parser already resolved the schedule. These values are authoritative:
            triggerTimeEpochMs=${it.triggerTime}
            isRecurring=${it.recurrenceRule != null}
            recurrenceRule=${it.recurrenceRule ?: "null"}
            Copy those exact values into the JSON. Do not reinterpret the user's date, time, or recurrence.
            """.trimIndent()
        } ?: """
            No deterministic schedule matched. Resolve triggerTimeEpochMs and recurrence from the user's
            language. Return null rather than inventing a time the user did not express.
        """.trimIndent()
        val prompt = """
            Parse this spoken reminder into JSON only. Do not add markdown.
            Current epoch milliseconds: $now
            Device time zone: ${zone.id}
            User said: ${JSONObject.quote(transcript)}
            $scheduleInstruction

            Return exactly these fields:
            {
              "title": "concise task only",
              "baseType": "TIME" or "LOCATION",
              "triggerTimeEpochMs": number or null,
              "locationName": string or null,
              "locationOffsetMinutes": integer,
              "isRecurring": boolean,
              "recurrenceRule": RRULE string without DTSTART or null,
              "alertFullScreen": boolean,
              "alertSound": boolean,
              "alertHeadsUp": boolean,
              "alertLockScreen": boolean
            }
            Resolve relative dates using the supplied current time and time zone.
            LOCATION means entering/leaving a named place; recurrence is a separate modifier.
            "Next time I am at/near/in [place]" is a one-time location reminder, not recurrence.
            Only set isRecurring=true when the user explicitly asks for repetition, such as
            every day, every week, weekdays, monthly, or each time.
            Unless the user explicitly requests a different alert behavior, use these defaults:
            alertFullScreen=${alerts.fullScreen}, alertSound=${alerts.sound},
            alertHeadsUp=${alerts.headsUp}, alertLockScreen=${alerts.lockScreen}.
        """.trimIndent()
        val aiDraft = parseDraft(
            request(key, prompt, 600),
            allowMissingTime = localSchedule != null,
        )
        val draft = mergeAuthoritativeSchedule(aiDraft, localSchedule)
        if (
            draft.baseType == BaseType.LOCATION &&
            Regex("\\bnext\\s+time\\b", RegexOption.IGNORE_CASE).containsMatchIn(transcript)
        ) {
            draft.copy(isRecurring = false, recurrenceRule = null)
        } else if (
            draft.baseType == BaseType.LOCATION &&
            Regex("\\bevery\\s+time\\b", RegexOption.IGNORE_CASE).containsMatchIn(transcript)
        ) {
            draft.copy(isRecurring = true, recurrenceRule = null)
        } else {
            draft
        }
    }

    private fun request(apiKey: String, prompt: String, maxTokens: Int): String {
        val connection = URL(API_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 35_000
            connection.doOutput = true
            connection.setRequestProperty("content-type", "application/json")
            connection.setRequestProperty("x-api-key", apiKey.trim())
            connection.setRequestProperty("anthropic-version", "2023-06-01")

            val body = JSONObject()
                .put("model", MODEL)
                .put("max_tokens", maxTokens)
                .put(
                    "messages",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", prompt),
                    ),
                )
                .toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            if (status !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val kind = when (status) {
                    401, 403 -> AiServiceException.Kind.UNAUTHORIZED
                    in 500..599 -> AiServiceException.Kind.SERVER
                    else -> AiServiceException.Kind.NETWORK
                }
                throw AiServiceException(
                    when (kind) {
                        AiServiceException.Kind.UNAUTHORIZED -> "API key was rejected"
                        AiServiceException.Kind.SERVER -> "Claude is temporarily unavailable"
                        else -> "Claude request failed ($status) ${detail.take(120)}"
                    },
                    kind,
                )
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val content = try {
                JSONObject(response).optJSONArray("content")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    .orEmpty()
            } catch (_: Exception) {
                throw AiServiceException(
                    "Claude returned an invalid response",
                    AiServiceException.Kind.INVALID_RESPONSE,
                )
            }
            if (content.isBlank()) {
                throw AiServiceException("Claude returned an empty response", AiServiceException.Kind.INVALID_RESPONSE)
            }
            return content
        } catch (exception: java.net.SocketTimeoutException) {
            throw AiServiceException("Request timed out", AiServiceException.Kind.TIMEOUT)
        } catch (exception: AiServiceException) {
            throw exception
        } catch (exception: IOException) {
            throw AiServiceException(
                exception.message ?: "Network request failed",
                AiServiceException.Kind.NETWORK,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parseDraft(text: String, allowMissingTime: Boolean = false): ReminderDraft {
        try {
            val alerts = alertPreferences.get()
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) error("JSON object missing")
            val json = JSONObject(text.substring(start, end + 1))
            val baseType = BaseType.valueOf(json.getString("baseType").uppercase())
            val triggerTime = if (json.has("triggerTimeEpochMs") && !json.isNull("triggerTimeEpochMs")) {
                json.getLong("triggerTimeEpochMs")
            } else {
                null
            }
            val title = json.getString("title").trim()
            if (title.isBlank()) error("Title is empty")
            if (baseType == BaseType.TIME && triggerTime == null && !allowMissingTime) {
                error("Time trigger is missing")
            }
            return ReminderDraft(
                title = title,
                baseType = baseType,
                triggerTime = triggerTime,
                locationName = json.optString("locationName").takeIf { it.isNotBlank() && it != "null" },
                locationOffsetMinutes = json.optInt("locationOffsetMinutes", 0).coerceAtLeast(0),
                isRecurring = json.optBoolean("isRecurring", false),
                recurrenceRule = json.optString("recurrenceRule").takeIf { it.isNotBlank() && it != "null" },
                alertFullScreen = json.optBoolean("alertFullScreen", alerts.fullScreen),
                alertSound = json.optBoolean("alertSound", alerts.sound),
                alertHeadsUp = json.optBoolean("alertHeadsUp", alerts.headsUp),
                alertLockScreen = json.optBoolean("alertLockScreen", alerts.lockScreen),
            )
        } catch (exception: Exception) {
            throw AiServiceException(
                "Claude response could not be parsed",
                AiServiceException.Kind.INVALID_RESPONSE,
            )
        }
    }

    companion object {
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val MODEL = "claude-sonnet-4-6"
    }
}

internal data class ParsedSchedule(
    val triggerTime: Long,
    val recurrenceRule: String?,
)

internal fun mergeAuthoritativeSchedule(
    aiDraft: ReminderDraft,
    localSchedule: ParsedSchedule?,
): ReminderDraft {
    if (localSchedule == null) return aiDraft
    return aiDraft.copy(
        triggerTime = localSchedule.triggerTime,
        isRecurring = localSchedule.recurrenceRule != null,
        recurrenceRule = localSchedule.recurrenceRule,
    )
}

internal object NaturalLanguageSchedule {
    private val twelveHourTime =
        Regex("""\b(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)\b""", RegexOption.IGNORE_CASE)
    private val twentyFourHourTime =
        Regex("""\bat\s+([01]?\d|2[0-3]):([0-5]\d)\b""", RegexOption.IGNORE_CASE)
    private val dayCodes = linkedMapOf(
        "monday" to "MO",
        "tuesday" to "TU",
        "wednesday" to "WE",
        "thursday" to "TH",
        "friday" to "FR",
        "saturday" to "SA",
        "sunday" to "SU",
    )

    fun parse(
        transcript: String,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ParsedSchedule? {
        val normalized = transcript.lowercase()
        val time = parseTime(normalized) ?: return null
        val recurrence = recurrenceRule(normalized)
        val current = now.atZone(zone)
        val recurrenceDays = recurrenceDays(recurrence)
        val monthDay = ruleValue(recurrence, "BYMONTHDAY")?.toIntOrNull()
        val setPosition = ruleValue(recurrence, "BYSETPOS")?.toIntOrNull()
        var date = when {
            Regex("""\btomorrow\b""").containsMatchIn(normalized) -> current.toLocalDate().plusDays(1)
            Regex("""\btoday\b""").containsMatchIn(normalized) -> current.toLocalDate()
            monthDay != null -> nextMonthlyDate(current, time, monthDay)
            setPosition != null && recurrenceDays.size == 1 ->
                nextNthWeekdayDate(current, time, recurrenceDays.single(), setPosition)
            recurrenceDays.isNotEmpty() -> nextAllowedDate(current, time, recurrenceDays)
            else -> current.toLocalDate()
        }
        var trigger = ZonedDateTime.of(date, time, zone)
        if (!trigger.toInstant().isAfter(now) && !normalized.contains("today")) {
            date = when (recurrence) {
                null -> date.plusDays(1)
                else -> when {
                    monthDay != null -> monthlyDate(date.plusMonths(1), monthDay)
                    setPosition != null && recurrenceDays.size == 1 ->
                        nthWeekday(date.plusMonths(1), recurrenceDays.single(), setPosition)
                    recurrenceDays.isNotEmpty() -> nextDate(date, recurrenceDays)
                    else -> date.plusDays(1)
                }
            }
            trigger = ZonedDateTime.of(date, time, zone)
        }
        val normalizedRecurrence = if (recurrence == "FREQ=MONTHLY") {
            "$recurrence;BYMONTHDAY=${trigger.dayOfMonth}"
        } else {
            recurrence
        }
        return ParsedSchedule(trigger.toInstant().toEpochMilli(), normalizedRecurrence)
    }

    private fun parseTime(text: String): LocalTime? {
        if (Regex("""\bnoon\b""").containsMatchIn(text)) return LocalTime.NOON
        if (Regex("""\bmidnight\b""").containsMatchIn(text)) return LocalTime.MIDNIGHT
        twelveHourTime.find(text)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: return null
            if (hour !in 1..12 || minute !in 0..59) return null
            val isPm = match.groupValues[3].lowercase().startsWith("p")
            return LocalTime.of(
                when {
                    isPm && hour != 12 -> hour + 12
                    !isPm && hour == 12 -> 0
                    else -> hour
                },
                minute,
            )
        }
        return twentyFourHourTime.find(text)?.let {
            LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
    }

    private fun recurrenceRule(text: String): String? {
        Regex("""\b(?:on\s+)?the\s+(\d{1,2})(?:st|nd|rd|th)?\s+of\s+every\s+month\b""")
            .find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..31 }?.let {
                return "FREQ=MONTHLY;BYMONTHDAY=$it"
            }
        Regex("""\bevery\s+month\s+on\s+the\s+(\d{1,2})(?:st|nd|rd|th)?\b""")
            .find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..31 }?.let {
                return "FREQ=MONTHLY;BYMONTHDAY=$it"
            }
        val ordinal = Regex("""\b(first|second|third|fourth|last)\s+(${dayCodes.keys.joinToString("|")})\s+of\s+(?:every|the)\s+month\b""")
            .find(text)
        if (ordinal != null) {
            val position = mapOf("first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "last" to -1)
                .getValue(ordinal.groupValues[1])
            return "FREQ=MONTHLY;BYDAY=${dayCodes.getValue(ordinal.groupValues[2])};BYSETPOS=$position"
        }
        if (Regex("""\bevery\s+other\s+week\b""").containsMatchIn(text)) return "FREQ=WEEKLY;INTERVAL=2"
        val mentionedDays = dayCodes.filterKeys { day ->
            Regex("""\b${day}s?\b""").containsMatchIn(text)
        }.values
        if (
            mentionedDays.isNotEmpty() &&
            (Regex("""\bevery\b""").containsMatchIn(text) ||
                Regex("""\bon\s+(?:${dayCodes.keys.joinToString("|")})s?\b""").containsMatchIn(text))
        ) {
            return "FREQ=WEEKLY;BYDAY=${mentionedDays.joinToString(",")}"
        }
        return when {
        Regex("""\b(every\s+weekday|weekdays)\b""").containsMatchIn(text) -> WEEKDAYS
        Regex("""\b(every\s+weekend|weekends)\b""").containsMatchIn(text) -> WEEKENDS
        Regex("""\b(every\s+day|daily)\b""").containsMatchIn(text) -> "FREQ=DAILY"
        Regex("""\b(every\s+week|weekly)\b""").containsMatchIn(text) -> "FREQ=WEEKLY"
        Regex("""\b(every\s+month|monthly)\b""").containsMatchIn(text) -> "FREQ=MONTHLY"
        Regex("""\b(every\s+year|yearly|annually)\b""").containsMatchIn(text) -> "FREQ=YEARLY"
        else -> null
        }
    }

    private fun recurrenceDays(rule: String?): Set<DayOfWeek> =
        ruleValue(rule, "BYDAY")
            ?.split(',')
            ?.mapNotNull { code -> dayCodes.entries.firstOrNull { it.value == code }?.key }
            ?.map { DayOfWeek.valueOf(it.uppercase()) }
            ?.toSet()
            .orEmpty()

    private fun ruleValue(rule: String?, key: String): String? =
        rule?.split(';')?.firstOrNull { it.startsWith("$key=") }?.substringAfter('=')

    private fun nextAllowedDate(
        current: ZonedDateTime,
        time: LocalTime,
        allowedDays: Set<DayOfWeek>,
    ): LocalDate {
        val today = current.toLocalDate()
        if (today.dayOfWeek in allowedDays && time.isAfter(current.toLocalTime())) return today
        return nextDate(today, allowedDays)
    }

    private fun nextDate(date: LocalDate, allowedDays: Set<DayOfWeek>): LocalDate {
        var candidate = date.plusDays(1)
        while (candidate.dayOfWeek !in allowedDays) candidate = candidate.plusDays(1)
        return candidate
    }

    private fun nextMonthlyDate(current: ZonedDateTime, time: LocalTime, day: Int): LocalDate {
        var candidate = monthlyDate(current.toLocalDate(), day)
        if (candidate.isBefore(current.toLocalDate()) ||
            (candidate == current.toLocalDate() && !time.isAfter(current.toLocalTime()))
        ) {
            candidate = monthlyDate(current.toLocalDate().plusMonths(1), day)
        }
        return candidate
    }

    private fun monthlyDate(month: LocalDate, day: Int): LocalDate {
        val yearMonth = YearMonth.from(month)
        return yearMonth.atDay(day.coerceAtMost(yearMonth.lengthOfMonth()))
    }

    private fun nextNthWeekdayDate(
        current: ZonedDateTime,
        time: LocalTime,
        day: DayOfWeek,
        position: Int,
    ): LocalDate {
        var candidate = nthWeekday(current.toLocalDate(), day, position)
        if (candidate.isBefore(current.toLocalDate()) ||
            (candidate == current.toLocalDate() && !time.isAfter(current.toLocalTime()))
        ) {
            candidate = nthWeekday(current.toLocalDate().plusMonths(1), day, position)
        }
        return candidate
    }

    private fun nthWeekday(month: LocalDate, day: DayOfWeek, position: Int): LocalDate {
        val yearMonth = YearMonth.from(month)
        if (position == -1) {
            var candidate = yearMonth.atEndOfMonth()
            while (candidate.dayOfWeek != day) candidate = candidate.minusDays(1)
            return candidate
        }
        var candidate = yearMonth.atDay(1)
        while (candidate.dayOfWeek != day) candidate = candidate.plusDays(1)
        return candidate.plusWeeks((position - 1).toLong())
    }

    private const val WEEKDAYS = "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
    private const val WEEKENDS = "FREQ=WEEKLY;BYDAY=SA,SU"
}
