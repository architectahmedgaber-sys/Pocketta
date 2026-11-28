package app.pocketta.i18n

/**
 * English for the Arabic-written texts of Miqrish's quick entry (the screen, the key setup, the error messages).
 * Used only when the app language is English, so the page follows the language the user picked. "#" stands for a number.
 */
object EnWords {
    val dict: Map<String, String> = mapOf(
        "إدخال بالذكاء الاصطناعي" to "AI quick entry",
        "المفتاح" to "Key", "رجوع" to "Back", "اكتب اللي حصل" to "Write what happened",
        "كشري ٦٠ وبنزين ٢٠٠ النهاردة" to "Koshary 60 and fuel 200 today",
        "افهم" to "Understand",
        "مفيش حاجة بتتسجل غير لما تدوس «تأكيد الكل»." to "Nothing is saved until you tap “Confirm all”.",
        "محتاج مراجعة" to "Needs a review", "اكتب تاني" to "Write again", "إدخال يدوي" to "Add manually",
        "مفيش حاجة اتسجلت." to "Nothing was saved.", "راجع وعدّل" to "Review and edit",
        "＋ ضيف معاملة" to "＋ Add a transaction", "ماقدرتش أسجل. راجع الكروت وجرّب تاني." to "Could not save. Check the cards and try again.",
        "اتلغى. مفيش حاجة اتسجلت." to "Cancelled. Nothing was saved.", "إلغاء" to "Cancel", "التكلفة" to "Cost",
        "الرد جه من موديل غير المتوقع فاتجاهلته. مفيش حاجة اتسجلت." to "The answer came from an unexpected model, so I ignored it. Nothing was saved.",
        "حذف" to "Delete", "المبلغ" to "Amount", "اختار تصنيف" to "Pick a category", "الشخص" to "Person", "ملاحظة" to "Note",
        "تاريخ السداد: —" to "Due date: —", "مسح" to "Clear",
        "مصروف" to "Expense", "دخل" to "Income", "استلفت" to "I borrowed", "سلّفت" to "I lent", "سدّدت لـ" to "I paid back", "سدّد لي" to "Paid me back",
        "تمام، المفتاح شغّال واتحفظ على الموبايل ده بس." to "Done, the key works and is saved on this phone only.",
        "الرد جه من موديل غير المتوقع فاتجاهلته. المفتاح ما اتحفظش." to "The answer came from an unexpected model, so I ignored it. The key was not saved.",
        "الرد مش مفهوم. المفتاح ما اتحفظش، جرّب تاني." to "The answer was not understood. The key was not saved, try again.",
        "المساعد شغّال" to "The assistant is on",
        "محفوظ مشفّر على الموبايل ده بس. مش بيتنقل مع النسخة الاحتياطية ولا المزامنة." to "Saved encrypted on this phone only. It does not travel with backups or sync.",
        "استبدال" to "Replace", "المفتاح اتحذف من الموبايل." to "The key was removed from this phone.",
        "إعداد مساعد الذكاء الاصطناعي" to "Set up the AI assistant",
        "بتكتب جملة بالعامية زي «كشري ٦٠ وسلفت باسم ٥٠٠»، والمساعد يطلّع منها المعاملات وانت بتراجعها وتعدّلها وتأكّدها قبل ما تتسجل." to
            "You write a sentence like “koshary 60 and I lent Basem 500”, and the assistant pulls out the transactions. You review, edit and confirm them before anything is saved.",
        "كل واحد بيستخدم مفتاح Anthropic بتاعه هو، والتكلفة سنتات في الشهر (الموديل الصغير بس)." to "Everyone uses their own Anthropic key. The cost is cents a month (small model only).",
        "خطوات:" to "Steps:", "١) دوس «هات مفتاح» واعمل مفتاح جديد مخصوص للتطبيق ده." to "1) Tap “Get a key” and make a new key just for this app.",
        "٢) حدّد له حد صرف شهري صغير (مثلاً ٥ دولار) من إعدادات Anthropic، فلو المفتاح اتسرق أقصى خسارة معروفة." to "2) Set a small monthly spending limit (for example $5) in Anthropic settings, so if the key is stolen the most you can lose is known.",
        "٣) انسخ المفتاح والصقه تحت، ودوس «اختبار»." to "3) Copy the key, paste it below and tap “Test”.",
        "هات مفتاح" to "Get a key", "المفتاح (sk-ant-...)" to "Key (sk-ant-...)", "اختبار" to "Test",
        "المفتاح بيتحفظ بعد ما الاختبار ينجح، مشفّر بمفتاح في Android Keystore. الاختبار بيبعت طلب صغير جداً لـ api.anthropic.com بس. ماتبعتش المفتاح لأي حد." to
            "The key is saved after the test passes, encrypted with a key in the Android Keystore. The test sends one tiny request to api.anthropic.com only. Never send the key to anyone.",
        "افتح الإدخال بالذكاء الاصطناعي" to "Open AI quick entry",
        "المفتاح مش مقبول. اتأكد إنك نسخته كله وإنه لسه شغّال." to "The key was not accepted. Make sure you copied all of it and that it still works.",
        "الطلب اتّرفض. غالباً الرصيد في حسابك على Anthropic خلص أو المفتاح لسه مش مفعّل." to "The request was refused. Your Anthropic balance may be used up, or the key is not active yet.",
        "طلبات كتير أو وصلت للحد الشهري اللي حطّيته. جرّب بعد شوية." to "Too many requests, or you reached the monthly limit you set. Try again in a little while.",
        "خدمة Anthropic مشغولة دلوقتي. جرّب تاني بعد شوية." to "The Anthropic service is busy right now. Try again in a little while.",
        "مفيش إنترنت أو الاتصال ضعيف. جرّب تاني." to "No internet, or the connection is weak. Try again.",
        "الرد مش مفهوم" to "The answer was not understood", "الرسالة فاضية" to "The message is empty",
        "نوع المعاملة مش مظبوط" to "The transaction type is not right", "اكتب مبلغ أكبر من صفر" to "Enter an amount above zero",
        "اكتب اسم الشخص" to "Enter the person's name", "تاريخ السداد للسلف بس" to "A due date is for loans only",
        "تاريخ السداد قبل تاريخ المعاملة" to "The due date is before the transaction date",
        "اتسجل #" to "Saved #",
        "هذا الشهر: حوالي $# (# طلب). تقدير على سعر $# لكل مليون توكن دخل و $# لكل مليون توكن خرج." to
            "This month: about $# (# requests). Estimated at $# per million input tokens and $# per million output tokens.",
    )

    /** Texts with a changing part. "@1" is the first captured part (translated on its own). */
    val rules: List<Pair<Regex, String>> = listOf(
        Regex("""تأكيد الكل \((.+)\)""") to "Confirm all (@1)",
        Regex("""التاريخ: (.+)""") to "Date: @1",
        Regex("""السداد: (.+)""") to "Due: @1",
        Regex("""المفتاح المحفوظ: (.+)""") to "Saved key: @1",
        Regex("""الموديل: (.+) \(ثابت\)\.""") to "Model: @1 (fixed).",
        Regex("""الموديل: (.+) \(ثابت\)\. مصدر الأسعار: (.+)""") to "Model: @1 (fixed). Price source: @2",
        Regex("""مفيش سلفة مفتوحة مع (.+) علشان تتسدّد\. سجّلها الأول أو غيّر النوع\.""") to "There is no open loan with @1 to pay back. Record it first or change the type.",
        Regex("""مقدرتش أوصل للمساعد \(كود (.+)\)\.""") to "Could not reach the assistant (code @1).",
        Regex("""مقدرتش أفهم الرد \((.+)\)، راجع الرسالة""") to "Could not understand the answer (@1). Check the message.",
        Regex("""(.+) ▾""") to "@1 ▾",
    )
}
