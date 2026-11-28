package app.pocketta.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class QuickAiTest {
    private val today = LocalDate.of(2026, 11, 28)

    /** Runs a suspend block that never really suspends (the fakes below answer at once). */
    private fun <T> run(block: suspend () -> T): T {
        var out: kotlin.Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { out = it })
        return out!!.getOrThrow()
    }

    private fun esc(s: String) = Json.quote(s)

    /** A Messages API answer with [text] as the model's reply. */
    private fun api(text: String, model: String = QuickAi.MODEL, input: Int = 500, output: Int = 80) =
        """{"id":"msg_1","type":"message","role":"assistant","model":${esc(model)},"content":[{"type":"text","text":${esc(text)}}],"stop_reason":"end_turn","usage":{"input_tokens":$input,"output_tokens":$output}}"""

    private val coffee = """{"transactions":[{"type":"expense","amount_egp":60,"category":"أكل","person":null,"due_date":null,"date":"2026-11-28","note":"كشري"}]}"""

    private class FakeSink : QuickAi.Sink {
        val saved = ArrayList<QuickAi.Proposal>()
        override suspend fun save(p: QuickAi.Proposal) { saved += p }
    }

    private class Recorder {
        val usage = ArrayList<Pair<QuickAi.Usage, Boolean>>()
        val logs = ArrayList<String>()
    }

    private fun session(answer: String, sink: FakeSink, rec: Recorder = Recorder(), sent: MutableList<String> = ArrayList()) =
        QuickAi.Session({ body -> sent += body; answer }, sink, { u, ok -> rec.usage += u to ok }, { rec.logs += it })

    // ---------- the pinned model ----------

    @Test fun modelIsPinnedToHaiku() {
        assertEquals("claude-haiku-5-5", QuickAi.MODEL)
        val body = QuickAi.requestBody(today, "كشري ٦٠")
        assertTrue(body.startsWith("{\"model\":\"claude-haiku-5-5\""))
        assertFalse(body.contains("sonnet", true)); assertFalse(body.contains("opus", true)); assertFalse(body.contains("latest", true))
    }

    @Test fun requestStartsWithTodayAndUsesThePrompt() {
        assertEquals("today=2026-11-28\nكشري ٦٠", QuickAi.userMessage(today, "  كشري ٦٠ "))
        val sent = ArrayList<String>()
        run { session(api(coffee), FakeSink(), sent = sent).propose("كشري ٦٠", today) }
        val root = Json.parse(sent.single()) as Map<*, *>
        assertEquals(QuickAi.SYSTEM_PROMPT, root["system"])
        val msg = ((root["messages"] as List<*>).single() as Map<*, *>)["content"]
        assertEquals("today=2026-11-28\nكشري ٦٠", msg)
        assertTrue(QuickAi.SYSTEM_PROMPT.contains("loan_repaid_received") && QuickAi.SYSTEM_PROMPT.contains("needs_review"))
    }

    // ---------- a wrong model is rejected ----------

    @Test fun wrongModelIsDiscardedLoggedAndCounted() {
        for (other in listOf("claude-sonnet-5-5", "claude-opus-5-5", "claude-haiku-5-5-latest", "Claude-Haiku-5-5", "claude-haiku-5-5 ", "claude-haiku-5-4")) {
            val sink = FakeSink(); val rec = Recorder()
            val r = run { session(api(coffee, model = other), sink, rec).propose("كشري ٦٠", today) }
            assertTrue("model '$other' must be rejected", r is QuickAi.Proposed.WrongModel)
            assertEquals(other, (r as QuickAi.Proposed.WrongModel).got)
            assertTrue(sink.saved.isEmpty())
            assertEquals(1, rec.logs.size)                       // logged
            assertEquals(listOf(QuickAi.Usage(500, 80) to false), rec.usage) // tokens were billed, so they are counted (flagged not ok)
        }
    }

    @Test fun missingModelFieldIsRejected() {
        val body = """{"content":[{"type":"text","text":${esc(coffee)}}],"usage":{"input_tokens":1,"output_tokens":1}}"""
        assertTrue(QuickAi.readReply(body).result is QuickAi.Result.WrongModel)
    }

    // ---------- malformed JSON is rejected ----------

    @Test fun malformedJsonBecomesNeedsReview() {
        val bad = listOf(
            "```json\n$coffee\n```",                      // markdown fence
            "تمام: $coffee",                               // text before
            "$coffee\nشكرا",                               // text after
            """{"transactions":[{"type":"expense","amount_egp":60""",  // cut off
            "not json at all", "", "[]", "null",
            """{"transactions":"x"}""",
            """{"transactions":[]}""",                      // empty without needs_review
            """{"transactions":[],"needs_review":true}""",   // no reason
            """{"transactions":[{"type":"gift","amount_egp":5,"category":"أخرى","person":null,"due_date":null,"date":"2026-11-28","note":"x"}]}""",
            """{"transactions":[{"type":"expense","amount_egp":"60","category":"أكل","person":null,"due_date":null,"date":"2026-11-28","note":"x"}]}""",
            """{"transactions":[{"type":"expense","amount_egp":0,"category":"أكل","person":null,"due_date":null,"date":"2026-11-28","note":"x"}]}""",
            """{"transactions":[{"type":"expense","amount_egp":-5,"category":"أكل","person":null,"due_date":null,"date":"2026-11-28","note":"x"}]}""",
            """{"transactions":[{"type":"expense","amount_egp":5,"category":"أكل","person":null,"due_date":null,"date":"28/11/2026","note":"x"}]}""",
            """{"transactions":[{"type":"expense","amount_egp":5,"category":"أكل","person":null,"due_date":null,"date":"2026-02-30","note":"x"}]}""",
            """{"transactions":[{"type":"loan_given","amount_egp":5,"category":"أخرى","person":"باسم","due_date":"بكرة","date":"2026-11-28","note":"x"}]}""",
            """{"transactions":[{"type":"expense","amount_egp":5,"category":"أكل","person":null,"due_date":null,"note":"x"}]}""",   // no date
            """{"transactions":[{"type":"expense","amount_egp":5,"category":"أكل","person":null,"due_date":null,"date":"2026-11-28","note":"x"}],"needs_review":true}""",
        )
        for (b in bad) {
            val r = QuickAi.readReply(api(b)).result
            assertTrue("must not be accepted: $b", r is QuickAi.Result.NeedsReview)
            assertTrue("a broken answer is marked malformed: $b", (r as QuickAi.Result.NeedsReview).malformed)
        }
        // Not even an envelope.
        assertTrue(QuickAi.readReply("<html>502</html>").result is QuickAi.Result.NeedsReview)
    }

    @Test fun malformedAnswerSavesNothingAndShowsNeedsReview() {
        val sink = FakeSink(); val rec = Recorder()
        val r = run { session(api("```json\n$coffee\n```"), sink, rec).propose("كشري ٦٠", today) }
        assertTrue(r is QuickAi.Proposed.NeedsReview)
        assertTrue((r as QuickAi.Proposed.NeedsReview).malformed)
        assertTrue(sink.saved.isEmpty())
        assertEquals(1, rec.logs.size)
        assertEquals(listOf(QuickAi.Usage(500, 80) to true), rec.usage)
    }

    @Test fun modelSaidNeedsReview() {
        val sink = FakeSink()
        val r = run { session(api("""{"transactions": [], "needs_review": true, "reason": "المبلغ ناقص"}"""), sink).propose("اشتريت حاجة", today) }
        assertEquals(QuickAi.Proposed.NeedsReview("المبلغ ناقص", false), r)
        assertTrue(sink.saved.isEmpty())
    }

    @Test fun blankMessageDoesNotCallTheApi() {
        var calls = 0
        val s = QuickAi.Session({ calls++; api(coffee) }, FakeSink())
        assertTrue(run { s.propose("   ", today) } is QuickAi.Proposed.NeedsReview)
        assertEquals(0, calls)
    }

    // ---------- a good answer ----------

    @Test fun goodAnswerGivesProposalsInOrder() {
        val text = """{"transactions":[
          {"type":"loan_received","amount_egp":1000,"category":"أخرى","person":"باسم","due_date":"2026-12-01","date":"2026-11-28","note":"سلفة"},
          {"type":"expense","amount_egp":1.5,"category":"مواصلات","person":null,"due_date":"2026-12-01","date":"2026-11-27","note":"ميكروباص"},
          {"type":"loan_repaid_received","amount_egp":200,"category":"أخرى","person":"سامي","due_date":null,"date":"2026-11-28","note":"رجع"}]}"""
        val r = QuickAi.readReply(api(text)).result as QuickAi.Result.Proposals
        assertEquals(listOf("loan_received", "expense", "loan_repaid_received"), r.items.map { it.type })
        assertEquals(LocalDate.of(2026, 12, 1), r.items[0].dueDate)
        assertNull("due_date is kept only for loans", r.items[1].dueDate)
        assertEquals(1.5, r.items[1].amountEgp, 0.0)
        assertEquals("سامي", r.items[2].person)
    }

    @Test fun jsonReaderHandlesEscapesAndRejectsJunk() {
        assertEquals("a\"b\\c\né", (Json.parse(""""a\"b\\c\né"""") as String))
        assertEquals(mapOf("a" to listOf(1.0, true, null)), Json.parse("""{"a":[1,true,null]}"""))
        for (b in listOf("{", "[1,]", "{\"a\":1,}", "01", "'a'", "{\"a\" 1}", "[1] x", "NaN", "\"a\nb\"")) {
            try { Json.parse(b); throw AssertionError("accepted: $b") } catch (e: Json.JsonError) { /* expected */ }
        }
    }

    // ---------- usage and cost ----------

    @Test fun costUsesTheTwoPrices() {
        assertEquals(0.10, QuickAi.INPUT_USD_PER_MILLION, 0.0)
        assertEquals(0.50, QuickAi.OUTPUT_USD_PER_MILLION, 0.0)
        assertEquals(0.10, QuickAi.costUsd(1_000_000, 0), 1e-12)
        assertEquals(0.50, QuickAi.costUsd(0, 1_000_000), 1e-12)
        assertEquals(0.60, QuickAi.costUsd(1_000_000, 1_000_000), 1e-12)
        // one typical request: 500 in, 80 out = 0.00005 + 0.00004
        assertEquals(0.00009, QuickAi.costUsd(500, 80), 1e-12)
        assertEquals(0.0, QuickAi.costUsd(0, 0), 0.0)
        // editable: other prices give other costs
        assertEquals(3.0 + 15.0, QuickAi.costUsd(1_000_000, 1_000_000, 3.0, 15.0), 1e-9)
    }

    @Test fun monthCostAddsOnlyThatMonth() {
        val log = listOf(
            QuickAi.UsageEntry("2026-11", 1_000_000, 0, true),
            QuickAi.UsageEntry("2026-11", 0, 1_000_000, false),   // a discarded answer still costs money
            QuickAi.UsageEntry("2026-10", 5_000_000, 5_000_000, true),
        )
        assertEquals(0.60, QuickAi.monthCostUsd(log, "2026-11"), 1e-9)
        assertEquals(3.0, QuickAi.monthCostUsd(log, "2026-10"), 1e-9)
        assertEquals(0.0, QuickAi.monthCostUsd(log, "2026-09"), 0.0)
    }

    @Test fun usageIsReadFromTheAnswer() {
        assertEquals(QuickAi.Usage(1234, 56), QuickAi.readReply(api(coffee, input = 1234, output = 56)).usage)
        val noUsage = """{"model":"claude-haiku-5-5","content":[{"type":"text","text":${esc(coffee)}}]}"""
        assertNull(QuickAi.readReply(noUsage).usage)
        val rec = Recorder()
        run { session(api(coffee, input = 700, output = 90), FakeSink(), rec).propose("كشري ٦٠", today) }
        assertEquals(listOf(QuickAi.Usage(700, 90) to true), rec.usage)
    }

    // ---------- nothing is saved without confirmation ----------

    @Test fun nothingIsSavedUntilConfirm() {
        val sink = FakeSink()
        val s = session(api(coffee), sink)
        val r = run { s.propose("كشري ٦٠", today) } as QuickAi.Proposed.Ready
        assertEquals(1, r.pending.proposals.size)
        assertTrue("proposing must not save", sink.saved.isEmpty())
        assertFalse(r.pending.confirmed)
        // Walking away (never calling confirm) leaves the database untouched.
        assertTrue(sink.saved.isEmpty())
        assertEquals(1, run { r.pending.confirm() })
        assertEquals(60.0, sink.saved.single().amountEgp, 0.0)
        assertEquals("expense", sink.saved.single().type)
    }

    @Test fun confirmSavesOnlyTheChosenOnesAndOnlyOnce() {
        val text = """{"transactions":[
          {"type":"expense","amount_egp":10,"category":"أكل","person":null,"due_date":null,"date":"2026-11-28","note":"a"},
          {"type":"expense","amount_egp":20,"category":"أكل","person":null,"due_date":null,"date":"2026-11-28","note":"b"}]}"""
        val sink = FakeSink()
        val p = (run { session(api(text), sink).propose("x", today) } as QuickAi.Proposed.Ready).pending
        assertEquals(1, run { p.confirm(setOf(1)) })
        assertEquals(listOf(20.0), sink.saved.map { it.amountEgp })
        try { run { p.confirm() }; throw AssertionError("second confirm must fail") } catch (e: IllegalStateException) { /* expected */ }
        assertEquals(1, sink.saved.size)
    }

    @Test fun rejectedAnswersNeverReachTheSink() {
        val sink = FakeSink()
        run { session(api(coffee, model = "claude-sonnet-5-5"), sink).propose("كشري ٦٠", today) }
        run { session(api("oops"), sink).propose("كشري ٦٠", today) }
        run { session(api("""{"transactions":[],"needs_review":true,"reason":"x"}"""), sink).propose("كشري ٦٠", today) }
        assertTrue(sink.saved.isEmpty())
    }
}
