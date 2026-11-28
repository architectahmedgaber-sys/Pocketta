package app.pocketta.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddSheetTest {
    private fun i(s: String) = QuickIntentParser.parse(s)

    @Test fun plainExpense() {
        val a = i("كشري ٦٠")
        assertEquals(QuickKind.EXPENSE, a.kind); assertEquals(60.0, a.amount!!, 0.0); assertEquals("كشري", a.note); assertTrue(a.confident)
        val b = i("1.5k rent")
        assertEquals(1500.0, b.amount!!, 0.0); assertEquals(QuickKind.EXPENSE, b.kind)
        assertEquals(1, i("امبارح تاكسي ٨٥").daysAgo)
    }

    @Test fun incomeWords() {
        assertEquals(QuickKind.INCOME, i("مرتب 12000").kind)
        assertEquals(12000.0, i("مرتب ١٢٠٠٠").amount!!, 0.0)
        assertEquals(QuickKind.INCOME, i("قبضت 500 من محمد").kind)
        assertEquals(QuickKind.INCOME, i("salary 9k").kind)
        assertEquals(9000.0, i("salary 9k").amount!!, 0.0)
    }

    @Test fun borrowedSentences() {
        val a = i("استلفت ٣٠٠٠ من محمد")
        assertEquals(QuickKind.BORROWED, a.kind); assertEquals(3000.0, a.amount!!, 0.0); assertEquals("محمد", a.person)
        val b = i("borrowed 3000 from Sam")
        assertEquals(QuickKind.BORROWED, b.kind); assertEquals("Sam", b.person); assertEquals(3000.0, b.amount!!, 0.0)
    }

    @Test fun lentSentences() {
        val a = i("سلفت محمد ٥٠٠")
        assertEquals(QuickKind.LENT, a.kind); assertEquals("محمد", a.person); assertEquals(500.0, a.amount!!, 0.0)
        val b = i("سلّفت محمد 500")
        assertEquals(QuickKind.LENT, b.kind); assertEquals("محمد", b.person)
        val c = i("lent 500 to Sam")
        assertEquals(QuickKind.LENT, c.kind); assertEquals("Sam", c.person)
        val d = i("lent Sam 500")
        assertEquals("Sam", d.person)
    }

    @Test fun ambiguousFallsBackToChips() {
        assertFalse(i("coffee").confident)            // no amount
        assertFalse(i("استلفت مرتب 500").confident)   // loan and income words together
        assertNull(i("coffee").amount)
        assertFalse(i("60").confident)                // no words to file it under
    }

    @Test fun textKeyIgnoresSpellingVariants() {
        assertEquals(TextKey.normalize("قَهْوة"), TextKey.normalize("قهوه"))
        assertEquals(TextKey.normalize("أكل"), TextKey.normalize("اكل"))
        assertEquals("coffee", TextKey.normalize("  Coffee "))
    }

    @Test fun favouritesAreFrequentRecentCombinations() {
        val today = 20000L
        val e = listOf(
            FavEntry("قهوة", 1, 30.0, today - 1), FavEntry("قهوة", 1, 30.0, today - 3), FavEntry("قهوة", 1, 30.0, today - 9),
            FavEntry("مواصلات", 2, 55.0, today - 2), FavEntry("مواصلات", 2, 55.0, today - 4),
            FavEntry("كشري", 3, 60.0, today - 5),                       // only once: not a favourite
            FavEntry("قهوة", 1, 25.0, today - 70), FavEntry("قهوة", 1, 25.0, today - 80), // too old
        )
        val f = Favourites.top(e, today)
        assertEquals(listOf("قهوة", "مواصلات"), f.map { it.note })
        assertEquals(3, f[0].count)
        // A removed chip stays removed.
        assertEquals(listOf("مواصلات"), Favourites.top(e, today, hidden = setOf(f[0].key)).map { it.note })
        assertTrue(Favourites.top(e, today, limit = 1).size == 1)
    }

    @Test fun smartDefaultPrefersMostFrequentThenRecent() {
        // After two "كشري" entries filed under Food (id 1), the third needs no category tap.
        assertEquals(1L, SmartDefaults.mostFrequent(listOf(1L to 10L, 1L to 11L, 2L to 12L)))
        // One correction does not beat a habit; a tie goes to the latest choice.
        assertEquals(2L, SmartDefaults.mostFrequent(listOf(1L to 10L, 2L to 12L)))
        assertNull(SmartDefaults.mostFrequent(emptyList<Pair<Long, Long>>()))
    }

    @Test fun smsCardsRoundTripAndNeverDuplicate() {
        val c = SmsCard(7, 125.5, "EGP", "Carrefour\tMaadi", 20000)
        val s = SmsCards.encode(listOf(c))
        val back = SmsCards.decode(s)
        assertEquals(1, back.size); assertEquals(125.5, back[0].amount, 0.0); assertEquals("Carrefour Maadi", back[0].merchant)
        assertEquals(1, SmsCards.add(back, c).size)
        assertEquals(0, SmsCards.remove(back, 7).size)
        assertEquals(SmsCards.MAX, (1..30).fold(emptyList<SmsCard>()) { l, n -> SmsCards.add(l, SmsCard(n, 1.0, "EGP", "", 1)) }.size)
        assertEquals(emptyList<SmsCard>(), SmsCards.decode(""))
    }

    @Test fun smsParserSampleFormats() {
        // Made-up samples in the style of bank, wallet and card messages (no real numbers).
        val cib = SmsParser.parse("CIB: Purchase of EGP 245.50 at CARREFOUR MAADI on card ending 1234. Avail bal EGP 5,000.00")
        assertEquals(245.5, cib!!.amount, 0.001); assertEquals("EGP", cib.currency); assertTrue(cib.merchant.contains("CARREFOUR", true))
        val nbe = SmsParser.parse("NBE: تم خصم 120.00 جنيه عملية شراء لدى TALABAT من بطاقتك")
        assertEquals(120.0, nbe!!.amount, 0.001)
        val wallet = SmsParser.parse("You have paid EGP 75 to VODAFONE SHOP using Vodafone Cash. Payment successful")
        assertEquals(75.0, wallet!!.amount, 0.001)
        val arabicDigits = SmsParser.parse("تم سحب ٣٥٠ جنيه من ماكينة ATM")
        assertEquals(350.0, arabicDigits!!.amount, 0.001)
        assertNull(SmsParser.parse("Your OTP is 123456. Do not share it. Payment of EGP 100"))
        assertNull(SmsParser.parse("Salary credited EGP 9,000 to your account"))
    }
}
