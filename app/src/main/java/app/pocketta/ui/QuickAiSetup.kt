package app.pocketta.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.pocketta.ai.QUICK_AI_OFFLINE
import app.pocketta.ai.QuickAiHttpError
import app.pocketta.ai.QuickAiStore
import app.pocketta.ai.QuickAiTransport
import app.pocketta.core.QuickAi
import kotlinx.coroutines.launch

/**
 * "Set up AI assistant": the only setup there is. Explain, get a key, paste it, Test. The key is saved (encrypted) only after the test passes.
 * With a saved key it shows the key masked (last 4 only) with Replace and Delete.
 * While it is open the window is secure (no screenshots, blank in recent apps), because a key is typed here.
 * The typed key lives in plain `remember` state (never rememberSaveable, so it is not written to the saved instance state) and is cleared after each test.
 */
@Composable
fun QuickAiSetup(store: QuickAiStore, onKeyChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(store.masked()) }
    var replacing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val w = (ctx as? Activity)?.window
        val had = w != null && (w.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        w?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!had) w?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    fun test() {
        val key = field.trim()
        if (key.isEmpty() || busy) return
        busy = true; message = null
        scope.launch {
            try {
                when (val r = QuickAi.readTest(QuickAiTransport(key).send(QuickAi.testBody()))) {
                    is QuickAi.TestOutcome.Ok -> {
                        r.usage?.let { store.addUsage(it, true) }
                        store.setKey(key)
                        saved = store.masked(); replacing = false
                        message = "تمام، المفتاح شغّال واتحفظ على الموبايل ده بس."; failed = false
                        onKeyChanged()
                    }
                    is QuickAi.TestOutcome.WrongModel -> {
                        r.usage?.let { store.addUsage(it, false) }
                        store.addLog("Test: answer came from model '${r.got}', expected '${QuickAi.MODEL}'.")
                        message = "الرد جه من موديل غير المتوقع فاتجاهلته. المفتاح ما اتحفظش."; failed = true
                    }
                    QuickAi.TestOutcome.Unreadable -> { message = "الرد مش مفهوم. المفتاح ما اتحفظش، جرّب تاني."; failed = true }
                }
            } catch (e: QuickAiHttpError) {
                message = e.friendly(); failed = true
            } catch (e: Exception) {
                message = QUICK_AI_OFFLINE; failed = true
            } finally {
                field = ""; busy = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (saved != null && !replacing) {
            SectionCard {
                Label("المساعد شغّال")
                Gap(6)
                Text("المفتاح المحفوظ: " + saved, style = MaterialTheme.typography.titleMedium)
                Gap(4)
                Text("محفوظ مشفّر على الموبايل ده بس. مش بيتنقل مع النسخة الاحتياطية ولا المزامنة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap(10)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { replacing = true; message = null }) { Text("استبدال") }
                    OutlinedButton(onClick = { store.removeKey(); saved = null; replacing = false; message = "المفتاح اتحذف من الموبايل."; failed = false; onKeyChanged() }) { Text("حذف") }
                }
            }
        } else {
            SectionCard {
                Label("إعداد مساعد الذكاء الاصطناعي")
                Gap(6)
                Text("بتكتب جملة بالعامية زي «كشري ٦٠ وسلفت باسم ٥٠٠»، والمساعد يطلّع منها المعاملات وانت بتراجعها وتعدّلها وتأكّدها قبل ما تتسجل.")
                Gap(6)
                Text("كل واحد بيستخدم مفتاح Anthropic بتاعه هو، والتكلفة سنتات في الشهر (الموديل الصغير بس).")
                Gap(10)
                Text("خطوات:", style = MaterialTheme.typography.titleSmall)
                Text("١) دوس «هات مفتاح» واعمل مفتاح جديد مخصوص للتطبيق ده.")
                Text("٢) حدّد له حد صرف شهري صغير (مثلاً ٥ دولار) من إعدادات Anthropic، فلو المفتاح اتسرق أقصى خسارة معروفة.")
                Text("٣) انسخ المفتاح والصقه تحت، ودوس «اختبار».")
                Gap(10)
                OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.anthropic.com/"))) }) { Text("هات مفتاح") }
                Gap(10)
                OutlinedTextField(
                    value = field, onValueChange = { field = it }, label = { Text("المفتاح (sk-ant-...)") }, singleLine = true, shape = FieldShape,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Gap(8)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { test() }, enabled = field.isNotBlank() && !busy) { Text("اختبار") }
                    if (busy) CircularProgressIndicator(Modifier.padding(4.dp))
                    if (saved != null) OutlinedButton(onClick = { replacing = false; field = ""; message = null }, enabled = !busy) { Text("إلغاء") }
                }
                Gap(8)
                Text(
                    "المفتاح بيتحفظ بعد ما الاختبار ينجح، مشفّر بمفتاح في Android Keystore. الاختبار بيبعت طلب صغير جداً لـ api.anthropic.com بس. ماتبعتش المفتاح لأي حد.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        message?.let {
            SectionCard(container = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer) { Text(it) }
        }
    }
}
