package com.vknxworks.clockctrl.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.clockctrl.R
import com.vknxworks.clockctrl.ui.components.SectionLabel
import com.vknxworks.clockctrl.ui.theme.CtrlColors
import com.vknxworks.clockctrl.ui.theme.CtrlShapes
import com.vknxworks.clockctrl.ui.theme.CtrlType

/** The visible sum, such as "7 × 14 + 38". */
@Composable
fun sumText(s: Sum): String {
    val first = "${s.a} ${opSymbol(s.op1)} ${s.b}"
    return if (s.op2 == null) first else "$first ${opSymbol(s.op2)} ${s.c}"
}

/** The sum as TalkBack reads it, such as "What is 7 times 14 plus 38?". */
@Composable
fun sumSpoken(s: Sum): String {
    val first = stringResource(opSpoken(s.op1), s.a, s.b)
    val all = if (s.op2 == null) first else stringResource(opSpoken(s.op2), first, s.c)
    return stringResource(R.string.sum_question_spoken, all)
}

@Composable
private fun opSymbol(op: Op): String = stringResource(
    when (op) {
        Op.PLUS -> R.string.op_plus
        Op.MINUS -> R.string.op_minus
        Op.TIMES -> R.string.op_times
    },
)

private fun opSpoken(op: Op): Int = when (op) {
    Op.PLUS -> R.string.sum_plus_spoken
    Op.MINUS -> R.string.sum_minus_spoken
    Op.TIMES -> R.string.sum_times_spoken
}

/**
 * "Solve a sum to dismiss": the sum, the answer typed so far and a large keypad. Three wrong answers bring a new sum,
 * so nobody gets stuck. [onSolved] runs once the answer is right.
 */
@Composable
fun ChallengePanel(level: Int, key: Any, modifier: Modifier = Modifier, onSolved: () -> Unit) {
    val session = remember(key) { ChallengeSession(level) }
    var sum by remember(key) { mutableStateOf(session.sum) }
    var typed by remember(key) { mutableStateOf("") }
    // 0: nothing yet, 1..: tries left after a wrong answer, -1: a new sum after three wrong ones.
    var feedback by remember(key) { mutableIntStateOf(0) }

    fun check() {
        when (val res = session.submit(typed.toIntOrNull())) {
            ChallengeSession.Result.Correct -> onSolved()
            is ChallengeSession.Result.Wrong -> { feedback = res.triesLeft; typed = "" }
            ChallengeSession.Result.NewSum -> { feedback = -1; typed = ""; sum = session.sum }
        }
    }

    Column(modifier.fillMaxWidth().widthIn(max = 480.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SectionLabel(stringResource(R.string.challenge_heading), Modifier.semantics { heading() })
        Spacer(Modifier.height(10.dp))
        val spoken = sumSpoken(sum)
        Text(
            "${sumText(sum)} = ?",
            style = CtrlType.Readout.copy(fontSize = 34.sp, lineHeight = 40.sp),
            color = CtrlColors.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics { contentDescription = spoken; liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(12.dp))
        val answerSpoken = if (typed.isEmpty()) stringResource(R.string.challenge_answer_empty) else stringResource(R.string.challenge_answer_spoken, typed)
        Box(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(CtrlShapes.Field).background(CtrlColors.Surface)
                .border(1.dp, CtrlColors.ControlOutline, CtrlShapes.Field)
                .clearAndSetSemantics { contentDescription = answerSpoken },
            contentAlignment = Alignment.Center,
        ) {
            Text(typed.ifEmpty { "–" }, style = CtrlType.Readout, color = if (typed.isEmpty()) CtrlColors.Inactive else CtrlColors.Text)
        }
        val message = when {
            feedback == -1 -> stringResource(R.string.challenge_new_sum)
            feedback > 0 -> androidx.compose.ui.res.pluralStringResource(R.plurals.challenge_wrong, feedback, feedback)
            else -> ""
        }
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (feedback > 0) CtrlColors.Error else CtrlColors.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).heightIn(min = 20.dp).semantics { liveRegion = LiveRegionMode.Assertive },
        )
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("<", "0", "ok"))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        val keyModifier = Modifier.weight(1f).height(64.dp).clip(CtrlShapes.Key)
                        when (k) {
                            "<" -> {
                                val label = stringResource(R.string.backspace)
                                Box(
                                    keyModifier.background(CtrlColors.Surface).border(1.dp, CtrlColors.Outline, CtrlShapes.Key)
                                        .clickable(enabled = typed.isNotEmpty(), role = Role.Button, onClickLabel = label) { typed = typed.dropLast(1) },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.AutoMirrored.Outlined.Backspace, label, Modifier.size(26.dp), tint = if (typed.isEmpty()) CtrlColors.Inactive else CtrlColors.Text) }
                            }
                            "ok" -> {
                                val label = stringResource(R.string.challenge_check)
                                val on = typed.isNotEmpty()
                                Box(
                                    keyModifier.background(if (on) CtrlColors.Accent else CtrlColors.Raised)
                                        .clickable(enabled = on, role = Role.Button, onClickLabel = label) { check() },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Outlined.Check, label, Modifier.size(28.dp), tint = if (on) CtrlColors.OnAccent else CtrlColors.Inactive) }
                            }
                            else -> Box(
                                keyModifier.background(CtrlColors.Surface).border(1.dp, CtrlColors.Outline, CtrlShapes.Key)
                                    .clickable(role = Role.Button) {
                                        if (typed.length < ChallengeSession.MAX_DIGITS) typed = (typed + k).trimStart('0').ifEmpty { "0" }
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Text(k, style = CtrlType.Key, color = CtrlColors.Text) }
                        }
                    }
                }
            }
        }
    }
}
