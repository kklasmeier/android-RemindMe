package com.phoenix.remindme.alerts

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoenix.remindme.ui.theme.Accent
import com.phoenix.remindme.ui.theme.Glass
import com.phoenix.remindme.ui.theme.RemindMeTheme
import com.phoenix.remindme.ui.theme.TextDim
import com.phoenix.remindme.ui.theme.TextMuted
import com.phoenix.remindme.ui.theme.TextPrimary
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FullScreenAlertActivity : ComponentActivity() {
    private val reminderId by lazy { intent.getLongExtra(EXTRA_REMINDER_ID, -1L) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val title = intent.getStringExtra(EXTRA_REMINDER_TITLE).orEmpty().ifBlank { "Reminder" }
        setContent {
            RemindMeTheme {
                FullScreenAlert(
                    title = title,
                    onDone = { perform(ReminderActionReceiver.ACTION_DONE) },
                    onSnooze = { perform(ReminderActionReceiver.ACTION_SNOOZE, it) },
                    onDismiss = { perform(ReminderActionReceiver.ACTION_DISMISS) },
                )
            }
        }
    }

    private fun perform(action: String, snoozeMinutes: Int? = null) {
        AlarmSoundService.stop(this, reminderId)
        val actionIntent = Intent(this, ReminderActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_REMINDER_ID, reminderId)
        snoozeMinutes?.let { actionIntent.putExtra(EXTRA_SNOOZE_MINUTES, it) }
        sendBroadcast(actionIntent)
        finish()
    }
}

@Composable
private fun FullScreenAlert(
    title: String,
    onDone: () -> Unit,
    onSnooze: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val now = LocalDateTime.now()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF05070F), Color(0xFF0D0520)),
                ),
            )
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("REMINDME", color = TextDim, fontSize = 11.sp, letterSpacing = 3.sp)
        Text(
            DateTimeFormatter.ofPattern("h:mm").format(now),
            color = TextPrimary,
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            DateTimeFormatter.ofPattern("EEEE, MMMM d").format(now),
            color = TextMuted,
            fontSize = 14.sp,
        )

        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(Accent, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Alarm, null, Modifier.size(40.dp), tint = Color.White)
        }
        Spacer(Modifier.height(20.dp))
        Text("REMINDER · TIME", color = TextDim, fontSize = 11.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text("Scheduled · just now", color = Accent, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = Color.White,
            ),
        ) {
            Icon(Icons.Default.Check, null)
            Text("  Done", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(5 to "5 min", 30 to "30 min", 60 to "1 hr").forEach { (minutes, label) ->
                Button(
                    onClick = { onSnooze(minutes) },
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Glass,
                        contentColor = TextPrimary,
                    ),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Snooze", color = TextDim, fontSize = 9.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
        ) {
            Text("Dismiss")
        }
    }
}
