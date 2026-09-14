package com.phoenix.remindme.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.phoenix.remindme.ai.ReminderDraft
import com.phoenix.remindme.alerts.AlertPreferences
import com.phoenix.remindme.data.Recurrence
import com.phoenix.remindme.data.BaseType
import com.phoenix.remindme.ui.theme.Accent
import com.phoenix.remindme.ui.theme.Accent2
import com.phoenix.remindme.ui.theme.Danger
import com.phoenix.remindme.ui.theme.Glass
import com.phoenix.remindme.ui.theme.GlassBorder
import com.phoenix.remindme.ui.theme.Success
import com.phoenix.remindme.ui.theme.TextDim
import com.phoenix.remindme.ui.theme.TextMuted
import com.phoenix.remindme.ui.theme.Warning
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun ApiKeySetupScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    viewModel: ApiKeyViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    var manualEntry by remember { mutableStateOf(false) }
    var scannerError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scanner = remember {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options)
    }

    if (state.success) {
        OnboardingSuccess(onComplete)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .background(Accent.copy(alpha = .2f), RoundedCornerShape(22.dp))
                .border(1.dp, Accent.copy(alpha = .4f), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("🔔", fontSize = 34.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (manualEntry) "Enter API Key" else "Welcome to RemindMe",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Claude turns natural speech into a reminder draft. Your key is encrypted and stored only on this device.",
            color = TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        if (!manualEntry) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OnboardingOption(
                    icon = Icons.Default.QrCodeScanner,
                    title = "Scan QR Code",
                    subtitle = "Scan a raw sk-ant- key",
                    primary = true,
                    modifier = Modifier.weight(1f),
                ) {
                    scanner.startScan()
                        .addOnSuccessListener { barcode ->
                            val value = barcode.rawValue.orEmpty()
                            if (com.phoenix.remindme.ai.ApiKeyRepository.isValidFormat(value)) {
                                viewModel.setKey(value)
                                viewModel.verify()
                            } else {
                                scannerError = "That QR code does not contain a Claude API key."
                            }
                        }
                        .addOnFailureListener {
                            scannerError = "Couldn't scan the QR code. You can enter the key manually."
                        }
                }
                OnboardingOption(
                    icon = Icons.Default.Keyboard,
                    title = "Enter Manually",
                    subtitle = "Paste or type your key",
                    primary = false,
                    modifier = Modifier.weight(1f),
                ) { manualEntry = true }
            }
            scannerError?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Danger, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            if (state.verifying) {
                Spacer(Modifier.height(14.dp))
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Text("Verifying scanned key…", color = TextMuted, fontSize = 11.sp)
            }
            state.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Danger, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        } else {
            OutlinedTextField(
                value = state.key,
                onValueChange = viewModel::setKey,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Claude API Key") },
                placeholder = { Text("sk-ant-api03-…") },
                visualTransformation =
                    if (state.showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = viewModel::toggleVisibility) {
                        Icon(
                            if (state.showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle key visibility",
                        )
                    }
                },
                singleLine = true,
            )
            Spacer(Modifier.height(6.dp))
            if (state.key.isNotBlank()) {
                Text(
                    if (state.validFormat) "✓ Key format looks valid"
                    else "Claude API keys start with sk-ant-",
                    color = if (state.validFormat) Success else Warning,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Danger, fontSize = 12.sp)
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = viewModel::verify,
                enabled = state.validFormat && !state.verifying,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                if (state.verifying) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.verifying) "Verifying…" else "Verify & Connect")
            }
            OutlinedButton(onClick = { manualEntry = false }, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "Set up later · AI features will be limited",
            color = TextDim,
            fontSize = 12.sp,
            modifier = Modifier
                .clickable {
                    viewModel.skip()
                    onSkip()
                }
                .padding(12.dp),
        )
    }
}

@Composable
private fun OnboardingOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    primary: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .height(150.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (primary) Accent.copy(alpha = .1f) else Glass,
        ),
        border = BorderStroke(1.dp, if (primary) Accent.copy(alpha = .4f) else GlassBorder),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, Modifier.size(30.dp), tint = if (primary) Accent else TextMuted)
            Spacer(Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center)
            Text(subtitle, color = TextDim, fontSize = 10.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun OnboardingSuccess(onComplete: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(80.dp)
                .background(Success.copy(alpha = .15f), CircleShape)
                .border(2.dp, Success.copy(alpha = .4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Check, null, Modifier.size(42.dp), tint = Success) }
        Spacer(Modifier.height(20.dp))
        Text("You're all set!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Try “Remind me to call Mom tomorrow at 7.”",
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
            Text("Go to Dashboard")
        }
    }
}

@Composable
fun VoiceFlowScreen(
    onClose: () -> Unit,
    onEditDraft: (ReminderDraft) -> Unit,
    viewModel: VoiceFlowViewModel = hiltViewModel(),
) {
    when (val state = viewModel.state) {
        VoiceFlowState.Listening -> VoiceListening(
            onClose = onClose,
            onSubmit = viewModel::parse,
        )
        is VoiceFlowState.Parsing -> ParsingScreen(state.transcript)
        is VoiceFlowState.Confirmation -> {
            LaunchedEffect(state.draft) { onEditDraft(state.draft) }
            ParsingScreen(state.transcript)
        }
        is VoiceFlowState.Failure -> FailureScreen(
            transcript = state.transcript,
            message = state.message,
            onManual = {
                onEditDraft(
                    ReminderDraft(
                        title = state.transcript,
                        baseType = BaseType.TIME,
                        triggerTime = null,
                        locationName = null,
                        locationOffsetMinutes = 0,
                        isRecurring = false,
                        recurrenceRule = null,
                        alertFullScreen = true,
                        alertSound = true,
                        alertHeadsUp = true,
                        alertLockScreen = true,
                    ),
                )
            },
            onRetry = viewModel::retry,
            onCancel = onClose,
        )
    }
}

@Composable
private fun VoiceListening(
    onClose: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val context = LocalContext.current
    val autoSubmitSeconds = remember(context) { AlertPreferences(context).autoSubmitSeconds() }
    var transcript by remember { mutableStateOf(TextFieldValue("")) }
    var recording by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var rms by remember { mutableFloatStateOf(0f) }
    var countdown by remember { mutableIntStateOf(-1) }
    var error by remember { mutableStateOf<String?>(null) }
    var dictationBase by remember { mutableStateOf("") }
    var dictationStart by remember { mutableIntStateOf(0) }
    var dictationEnd by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val speechAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val recognizer = remember(speechAvailable) {
        if (speechAvailable) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    fun applySpoken(spoken: String) {
        val (text, cursor) = insertSpokenAtCursor(
            dictationBase,
            dictationStart,
            dictationEnd,
            spoken,
        )
        transcript = TextFieldValue(text, TextRange(cursor))
    }

    fun startListening() {
        val activeRecognizer = recognizer
        if (activeRecognizer == null) {
            error = "Speech recognition is unavailable. You can type the reminder below."
            return
        }
        error = null
        countdown = -1
        dictationBase = transcript.text
        dictationStart = transcript.selection.min.coerceIn(0, transcript.text.length)
        dictationEnd = transcript.selection.max.coerceIn(dictationStart, transcript.text.length)
        activeRecognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startListening() else error = "Microphone permission is required for voice input."
    }

    DisposableEffect(recognizer) {
        if (recognizer == null) {
            error = "Speech recognition is unavailable. You can type the reminder below."
            onDispose { }
        } else {
            recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { recording = true }
                override fun onBeginningOfSpeech() { recording = true; countdown = -1 }
                override fun onRmsChanged(rmsdB: Float) { rms = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    recording = false
                    if (transcript.text.isNotBlank() && !editing) countdown = autoSubmitSeconds
                }
                override fun onError(code: Int) {
                    recording = false
                    if (
                        code != SpeechRecognizer.ERROR_NO_MATCH &&
                        code != SpeechRecognizer.ERROR_SPEECH_TIMEOUT &&
                        code != SpeechRecognizer.ERROR_CLIENT
                    ) {
                        error = "Speech recognition stopped. Tap the microphone to try again."
                    }
                }
                override fun onResults(results: Bundle?) {
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.let(::applySpoken)
                    recording = false
                    if (transcript.text.isNotBlank() && !editing) countdown = autoSubmitSeconds
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.let(::applySpoken)
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
            )
            onDispose {
                recognizer.cancel()
                recognizer.destroy()
            }
        }
    }

    DisposableEffect(lifecycleOwner, recognizer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                recognizer?.cancel()
                recording = false
                countdown = -1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!speechAvailable) {
            error = "Speech recognition is unavailable. You can type the reminder below."
        } else if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(countdown, editing) {
        if (countdown > 0 && !editing) {
            delay(1_000)
            countdown--
            if (countdown == 0 && transcript.text.isNotBlank()) onSubmit(transcript.text)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", fontSize = 32.sp, modifier = Modifier.clickable(onClick = onClose))
            Spacer(Modifier.width(8.dp))
            Text("New Reminder", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier
                .size(80.dp)
                .background(if (editing) Accent2 else Accent, CircleShape)
                .clickable {
                    if (!recording) startListening() else recognizer?.stopListening()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (editing) Icons.Default.Edit else Icons.Default.Mic,
                null,
                Modifier.size(38.dp),
                tint = Color.White,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                editing && recording -> "Listening at cursor…"
                editing -> "Editing — tap the mic to insert at the cursor"
                recording -> "Listening…"
                transcript.text.isNotBlank() -> "Tap the mic to insert at the cursor"
                else -> "Tap the microphone to speak"
            },
            color = if (editing) Accent2 else Accent,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(18) { index ->
                val factor = .2f + ((index * 37) % 10) / 12f
                Box(
                    Modifier
                        .padding(horizontal = 2.dp)
                        .width(4.dp)
                        .height((8 + 36 * rms * factor).dp)
                        .alpha(if (editing && !recording) .3f else 1f)
                        .background(if (index % 2 == 0) Accent else Accent2, RoundedCornerShape(4.dp)),
                )
            }
        }
        OutlinedTextField(
            value = transcript,
            onValueChange = {
                transcript = it
                countdown = -1
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    editing = it.isFocused
                    if (it.isFocused) countdown = -1
                    else if (transcript.text.isNotBlank()) countdown = autoSubmitSeconds
                },
            placeholder = { Text("Your words will appear here…") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (transcript.text.isNotBlank()) onSubmit(transcript.text)
                },
            ),
            trailingIcon = {
                IconButton(
                    onClick = {
                        transcript = TextFieldValue("")
                        countdown = -1
                        recognizer?.cancel()
                        startListening()
                    },
                ) { Icon(Icons.Default.Clear, "Clear", tint = Danger) }
            },
            minLines = 3,
        )
        Text(
            "Place the cursor, then tap the mic to insert or replace there. ✕ clears everything.",
            color = TextDim,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Spacer(Modifier.height(12.dp))
        error?.let { Text(it, color = Danger, fontSize = 12.sp, textAlign = TextAlign.Center) }
        if (countdown > 0 && !editing) {
            Text("Auto-creating in $countdown… tap text to pause.", color = TextMuted, fontSize = 12.sp)
        } else {
            Spacer(Modifier.height(18.dp))
        }
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(
            onClick = { onSubmit(transcript.text) },
            enabled = transcript.text.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(if (editing) "Done Editing — Create Reminder" else "Done — Create Reminder Now")
        }
        Text(
            "Cancel",
            color = TextDim,
            modifier = Modifier
                .clickable(onClick = onClose)
                .padding(14.dp),
        )
    }
}

@Composable
private fun ParsingScreen(transcript: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(60.dp)
                .background(Accent.copy(alpha = .18f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Bolt, null, tint = Accent, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(16.dp))
        Text("Parsing your reminder…", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Claude AI is processing your request", color = TextDim, fontSize = 12.sp)
        Spacer(Modifier.height(24.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Glass)) {
            Text(
                "“$transcript”",
                Modifier.padding(16.dp),
                color = TextMuted,
                fontStyle = FontStyle.Italic,
            )
        }
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(color = Accent)
    }
}

@Composable
private fun ConfirmationScreen(
    transcript: String,
    initialDraft: ReminderDraft,
    onSave: (ReminderDraft) -> Unit,
    onEdit: (ReminderDraft) -> Unit,
    onAgain: () -> Unit,
) {
    var draft by remember(initialDraft) { mutableStateOf(initialDraft) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🤖", fontSize = 42.sp)
        Text("Does this look right?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("AI parsed your reminder — review before saving", color = TextDim, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Glass),
            border = BorderStroke(1.dp, GlassBorder),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("YOU SAID", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(transcript, color = TextMuted, fontStyle = FontStyle.Italic)
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Accent.copy(alpha = .08f)),
            border = BorderStroke(1.dp, Accent.copy(alpha = .3f)),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("⚡ AI UNDERSTOOD THIS AS", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                ParsedRow("Task", draft.title)
                ParsedRow("Trigger", draftTrigger(draft))
                ParsedRow("Offset", if (draft.locationOffsetMinutes == 0) "Immediately" else "${draft.locationOffsetMinutes} min")
                if (draft.baseType == BaseType.TIME) {
                    ParsedRow("Repeat", Recurrence.label(draft.recurrenceRule))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { onEdit(draft) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) { Text("Review & save", fontWeight = FontWeight.Bold) }
        Text(
            "🎙 Say it again",
            color = TextDim,
            modifier = Modifier
                .clickable(onClick = onAgain)
                .padding(14.dp),
        )
    }
}

@Composable
private fun ParsedRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(), color = TextDim, fontSize = 9.sp, modifier = Modifier.width(64.dp))
        Text(value, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun recurrenceLabel(isRecurring: Boolean, rule: String?): String {
    if (!isRecurring || rule.isNullOrBlank()) return "Does not repeat"
    val values = rule.split(';')
        .mapNotNull {
            val parts = it.split('=', limit = 2)
            if (parts.size == 2) parts[0].uppercase() to parts[1].uppercase() else null
        }
        .toMap()
    val interval = values["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val days = values["BYDAY"]?.split(',')?.mapNotNull {
        mapOf(
            "MO" to "Mon", "TU" to "Tue", "WE" to "Wed", "TH" to "Thu",
            "FR" to "Fri", "SA" to "Sat", "SU" to "Sun",
        )[it]
    }.orEmpty()
    if (days == listOf("Mon", "Tue", "Wed", "Thu", "Fri")) return "Every weekday"
    if (days.isNotEmpty()) return "Every ${days.joinToString(", ")}"
    return when (values["FREQ"]) {
        "DAILY" -> if (interval == 1) "Every day" else "Every $interval days"
        "WEEKLY" -> if (interval == 1) "Every week" else "Every $interval weeks"
        "MONTHLY" -> if (interval == 1) "Every month" else "Every $interval months"
        "YEARLY" -> if (interval == 1) "Every year" else "Every $interval years"
        else -> "Custom schedule"
    }
}

@Composable
private fun AlertToggle(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(
                if (enabled) Accent2.copy(alpha = .12f) else Glass,
                RoundedCornerShape(20.dp),
            )
            .border(
                1.dp,
                if (enabled) Accent2.copy(alpha = .4f) else GlassBorder,
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) Accent2 else TextDim, fontSize = 10.sp)
    }
}

private fun draftTrigger(draft: ReminderDraft): String = when (draft.baseType) {
    BaseType.LOCATION -> draft.locationName ?: "Location not found"
    BaseType.TIME -> draft.triggerTime?.let {
        DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a")
            .format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
    } ?: "Time not found"
}

@Composable
private fun FailureScreen(
    transcript: String,
    message: String,
    onManual: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("⚠️", fontSize = 44.sp)
        Text("Couldn't finish that reminder", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(message, color = TextDim, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Glass)) {
            Text("“$transcript”", Modifier.padding(16.dp), color = TextMuted, fontStyle = FontStyle.Italic)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Nothing was lost. You can fill in the rest yourself, or try again.",
            color = TextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Edit, null)
            Text("  Edit Manually")
        }
        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, null)
            Text("  Try again")
        }
        Text(
            "Cancel",
            color = TextDim,
            modifier = Modifier
                .clickable(onClick = onCancel)
                .padding(14.dp),
        )
    }
}

private fun insertSpokenAtCursor(
    base: String,
    start: Int,
    end: Int,
    spoken: String,
): Pair<String, Int> {
    val safeStart = start.coerceIn(0, base.length)
    val safeEnd = end.coerceIn(safeStart, base.length)
    val before = base.substring(0, safeStart)
    val after = base.substring(safeEnd)
    val piece = spoken.trim()
    val prefix = if (before.isNotEmpty() && !before.last().isWhitespace() && piece.isNotEmpty()) " " else ""
    val suffix = if (after.isNotEmpty() && !after.first().isWhitespace() && piece.isNotEmpty()) " " else ""
    val inserted = prefix + piece + suffix
    return (before + inserted + after) to (before + prefix + piece).length
}
