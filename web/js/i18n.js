// Arabic for the web app, same idea as the Android app: the UI is written in English and, when the language is Arabic,
// every text on the page is looked up in a dictionary right after it is drawn. Texts it doesn't know stay in English.
import { DICT, MONTHS, DAYS, RULES } from './ar-dict.js';

const EXTRA = {
  "Mobile wallet": "محفظة موبايل",
  "Main currency": "العملة الأساسية",
  "Your country": "بلدك",
  "Pick your country": "اختار بلدك",
  "Spent that day": "اللي اتصرف في اليوم ده",
  "Previous month": "الشهر اللي فات",
  "Left for flexible spending": "متبقي للمرن",
  "Over the flexible amount": "عديت المبلغ المرن",
  "of # left after commitments": "من # بعد الالتزامات",
  "of # left after commitments and household shopping": "من # بعد الالتزامات ومشتريات البيت",
  "Month passed": "اللي عدّى من الشهر",
  "Flexible money spent": "اللي اتصرف من المرن",
  "🔥 # days in a row": "🔥 # يوم ورا بعض",
  "🔥 # day in a row": "🔥 # يوم ورا بعض",
  "Saving challenge": "تحدي الادخار",
  "best #": "الأفضل #",
  "Every day the last # days stayed under": "كل يوم، آخر # أيام فضلوا تحت",
  "Keep the last # days under": "خلّي آخر # أيام تحت",
  "to start a streak.": "عشان تبدأ سلسلة.",
  "left for this week": "فاضل الأسبوع ده",
  "over this week": "زيادة الأسبوع ده",
  "Your week · from Miqrish": "أسبوعك · من مِقرِش",
  "Miqrish is writing your summary...": "مِقرِش بيكتب ملخصك...",
  "Add your own key in Miqrish to get this written for you every Friday.": "ضيف مفتاحك في مِقرِش وهيكتبلك الملخص ده كل جمعة.",
  "Tags": "الوسوم",
  "Tags (optional)": "وسوم (اختياري)",
  "Weddings, trips, projects: see the total of each across categories": "أفراح ورحلات ومشاريع: شوف إجمالي كل واحد عبر كل التصنيفات",
  "Add a tag to any spending or income (like a wedding or a trip) and see its total here, across all categories.": "حط وسم على أي مصروف أو دخل (زي فرح أو رحلة) وشوف إجماليه هنا عبر كل التصنيفات.",
  "No tags yet. When you add an expense, write a tag in the Tags box.": "لسه مفيش وسوم. وانت بتضيف مصروف اكتب وسم في خانة الوسوم.",
  "Spent on this tag": "اتصرف على الوسم ده",
  "Received #": "اتقبض #",
  "# records": "# عمليات",
  "# record": "عملية",
  "Records": "العمليات",
  "Nothing with this tag.": "مفيش حاجة بالوسم ده.",
  "Rename tag": "غيّر اسم الوسم",
  "Rename": "غيّر الاسم",
  "Remove tag from everything": "شيل الوسم من كل حاجة",
  "Type a name": "اكتب اسم",
  "Mark a whole group as paid": "علّم مجموعة كاملة إنها اتدفعت",
  "Next month": "الشهر الجاي",
  "# items": "# بنود",
  "# item": "بند",
  "Nothing to pay in this month.": "مفيش حاجة تتدفع في الشهر ده.",
  "These payments will be recorded as paid now:": "الدفعات دي هتتسجل إنها اتدفعت دلوقتي:",
  "You can undo it right after.": "تقدر تتراجع بعدها على طول.",
  "Mark all as paid": "علّم الكل مدفوع",
  "Undone": "اتراجع",
  "Last 6 months": "آخر 6 شهور",
  "▲ #% vs last month": "▲ #% عن الشهر اللي فات",
  "▼ #% vs last month": "▼ #% عن الشهر اللي فات",
  "＝ same as last month": "＝ زي الشهر اللي فات",
  "Household shopping each month (optional)": "مشتريات البيت كل شهر (اختياري)",
  "Kept apart from your flexible money. Groceries you add use this amount first.": "بتتفصل عن فلوسك المرنة. البقالة اللي بتضيفها بتخصم منه الأول.",
  "Money I have": "الفلوس اللي معايا",
  "Add it to my balance too": "ضيفها على رصيدي كمان",
  "Transfer": "تحويل",
  "Your money and gold in one number, in dollars and gold, and how it changed": "فلوسك والدهب في رقم واحد، بالدولار والدهب، وإزاي اتغيّر",
  "Nothing to pay in this month": "مفيش حاجة تتدفع في الشهر ده",
  "Month plan": "خطة الشهور",
  "Every month like your spreadsheet: income, installments, living, what is left": "كل شهر زي الشيت: الدخل والأقساط والمعيشة واللي بيفضل",
  "Miqrish": "مِقرِش",
  "More": "المزيد",
  "Your AI money advisor. Chat to add, change and calculate, with your own key": "مستشارك المالي بالذكاء الاصطناعي. كلّمه يضيف ويعدّل ويحسب، بمفتاحك أنت",
  "Living cost in a normal month": "المعيشة في شهر عادي",
  "Food, transport, everyday things. Used for every coming month.": "أكل ومواصلات وحاجات يومية. بتتحسب في كل الشهور الجاية.",
  "Living cost": "المعيشة",
  "Salary raise": "زيادة مرتب",
  "Edit": "تعديل",
  "Actual": "فعلي",
  "Expected": "متوقع",
  "Salary / income": "المرتب / الدخل",
  "Loans coming back": "سلف راجعة ليك",
  "Bills and subscriptions": "فواتير واشتراكات",
  "Credit cards": "كروت ائتمان",
  "Gam'eya shares": "أقساط الجمعية",
  "Loans to pay back": "سلف هترجّعها",
  "Living": "معيشة",
  "Change living cost": "غيّر المعيشة",
  "Salary from here": "المرتب من هنا",
  "Only for this month. Other months keep the normal living cost.": "للشهر ده بس. باقي الشهور بتفضل على المعيشة العادية.",
  "Salary from a month": "مرتب من شهر معيّن",
  "From this month on, the salary is this amount, until the next raise.": "من الشهر ده وبعده المرتب بالمبلغ ده، لحد الزيادة الجاية.",
  "Month": "الشهر",
  "Salary": "المرتب",
  "Chat": "الشات",
  "Write to Miqrish": "اكتب لمِقرِش",
  "Send": "ابعت",
  "Save this?": "أحفظ ده؟",
  "Yes, save": "أيوه احفظ",
  "No": "لأ",
  "Hi, I'm Miqrish, your money advisor.": "أهلاً، أنا مِقرِش، مستشارك المالي.",
  "Tell me things like: koshary 60 · I borrowed 1000 from Basem · I paid the installment · how much did I spend on food this month? I always ask before I save anything.": "قول لي زي: كشري ٦٠ · استلفت ١٠٠٠ من باسم · دفعت القسط · صرفت كام على الأكل الشهر ده؟ وأنا بسألك دايماً قبل ما أحفظ أي حاجة.",
  "Choose the company": "اختار الشركة",
  "Your API key": "مفتاحك (API key)",
  "Model": "الموديل",
  "Save and chat": "احفظ وكلّمه",
  "Remove key": "امسح المفتاح",
  "What Miqrish learned about you": "اللي مِقرِش عرفه عنك",
  "Nothing yet. When you correct it or tell it a habit, it remembers here.": "لسه مفيش. لما تصحّح له حاجة أو تقول له عادة، بيفتكرها هنا.",
  "Forget everything": "انسى كل حاجة",
  "The key stays on this phone only. It is not in your backups and it is sent only to the company you picked. Your chat is sent to that company, and any cost is on your own account there.": "المفتاح بيفضل على الموبايل ده بس، مش في النسخ الاحتياطي، وبيتبعت بس للشركة اللي اخترتها. كلامك بيروح لنفس الشركة، وأي تكلفة على حسابك عندهم.",
  "The company did not accept the request. Check the model name in the assistant settings.": "الشركة مقبلتش الطلب. راجع اسم الموديل في إعدادات المساعد.",
  "The key was refused. Check that you pasted it fully and that it is still active.": "المفتاح اترفض. اتأكد إنك لزقته كامل وإنه لسه شغال.",
  "That model name was not found. Check it in the assistant settings.": "اسم الموديل مش موجود. راجعه في إعدادات المساعد.",
  "Too many requests or the free limit is used up. Try again in a minute.": "طلبات كتير أو الباقة المجانية خلصت. جرّب بعد دقيقة.",
  "The company's service is busy. Try again in a moment.": "خدمة الشركة مشغولة. جرّب بعد شوية.",
  "Could not reach the assistant. Check your internet and try again.": "مقدرتش أوصل للمساعد. اتأكد من النت وجرّب تاني.",
  "Actual · In # · Out #": "فعلي · دخل # · خرج #",
  "This month · In # · Out #": "الشهر ده · دخل # · خرج #",
  "Expected · In # · Out #": "متوقع · دخل # · خرج #",
  "Total #": "الإجمالي #",
  "I borrowed money (I owe)": "أنا استلفت فلوس (عليّا أرجّعها)",
  "I lent money (they owe me)": "أنا سلّفت حد فلوس (هو اللي هيرجّعها)",
  "Pick one first": "اختار الأول",
  "Pick: I borrowed or I lent": "اختار: استلفت ولا سلّفت",
  'My gold': 'دهبي',
  "Write down gold you bought to see your profit or loss against today's price.": 'سجّل الدهب اللي اشتريته وشوف ربحك أو خسارتك مقارنة بسعر النهارده.',
  "Today's gold price isn't downloaded yet.": 'سعر الدهب النهارده لسه ما اتحمّلش.',
  'Profit #': 'ربح #', 'Loss #': 'خسارة #', '# g': '# جرام', 'Paid #': 'دفعت #', 'Worth #': 'تساوي #',
  'Bought at # per gram': 'اشتريته بـ # للجرام',
  'Grams': 'جرامات', 'Price per gram': 'سعر الجرام', 'Add gold': 'ضيف دهب', "Today's price per gram:": 'سعر الجرام النهارده:',
  "At month end, offer to move what's left to my savings goal": 'في آخر الشهر، اقترح أحط الباقي في هدف الادخار',
  'Move to goal': 'حطها في الهدف', 'Not now': 'مش دلوقتي',
  'Language': 'اللغة',
  'The month ends today. Left: #': 'الشهر خلصان النهارده. فاضلك: #', 'The month ends tomorrow. Left: #': 'الشهر بيخلص بكرة. فاضلك: #',
  'Move it to your savings goal?': 'تحب تحطها في هدف الادخار؟',
  'Gold 21k': 'ذهب 21', 'Result': 'النتيجة', 'Swap': 'بدّل',
  'Month start, look, savings goal, PIN lock, backup': 'بداية الشهر، الشكل، هدف الادخار، قفل PIN، النسخة الاحتياطية',
  'Add wallet': 'ضيف محفظة', '+ Sub': '+ فرعي', 'Add category': 'ضيف تصنيف',
  "Today's rates and gold": 'أسعار النهارده والدهب', 'Update rates from the internet': 'حدّث الأسعار من الإنترنت', 'Currencies to show:': 'العملات اللي تظهر:',
  'Set a goal (Umra, car, wedding...) and watch the ring fill up on Today.': 'حدد هدف (عمرة، عربية، فرح...) وشوف الدايرة بتتملي في النهارده.',
  'Alerts': 'التنبيهات',
  "Bills, installments and gam'eya shares that are due soon (by the days you choose on each one) show on Home and as a number on the app icon.": 'الفواتير والأقساط وأقساط الجمعية القريبة (حسب الأيام اللي تختارها) بتظهر في الرئيسية وكرقم على أيقونة التطبيق.',
  'Lock the app with a PIN': 'اقفل التطبيق برقم PIN', 'Set PIN': 'حدد PIN',
  'Asked when you open the app or come back after a minute.': 'بيتطلب لما تفتح التطبيق أو ترجعله بعد دقيقة.',
  "Everything is on this phone only, inside the browser. Save a backup file now and then (to Files, Drive, or WhatsApp to yourself). If you clear Safari's website data, the app's data goes too.": 'كل حاجة على التليفون ده بس، جوه المتصفح. احفظ نسخة احتياطية من وقت للتاني (في الملفات أو درايف أو واتساب لنفسك). لو مسحت بيانات المواقع من سفاري، بيانات التطبيق بتتمسح كمان.',
  'Save backup': 'احفظ نسخة', 'About': 'عن التطبيق', 'Updates come by themselves the next time you open it with internet.': 'التحديثات بتيجي لوحدها أول ما تفتحه وفيه إنترنت.',
  'Delete all data': 'امسح كل البيانات',
  'Amount, or quick: koshary 60': 'المبلغ، أو بسرعة: كشري 60', 'koshary 60  or  yesterday taxi 85': 'كشري 60  أو  امبارح تاكسي 85',
  // Plan ahead
  "Plan ahead": "خطّط قدّام",
  "Can I afford this?": "أقدر أشتريها؟",
  "What if I save?": "لو وفّرت؟",
  "Emergency mode": "وضع الطوارئ",
  "Can I afford this? What if I save?": "أقدر أشتريها؟ لو وفّرت؟",
  "Can I afford this? What if I save? When money runs short": "أقدر أشتريها؟ لو وفّرت؟ ولو الفلوس قلّت",
  "Price": "السعر",
  "Yes, you can afford it": "أيوه، تقدر تشتريها",
  "You can, but it will be tight": "تقدر، بس هتبقى ضيقة",
  "Not this month": "مش الشهر ده",
  "Daily number now": "رقم اليوم دلوقتي",
  "Daily number after": "رقم اليوم بعدها",
  "Left this month now": "الباقي الشهر ده دلوقتي",
  "Left this month after": "الباقي الشهر ده بعدها",
  "It would have to come out of your savings: #": "هتتاخد من مدخراتك: #",
  "Goal date": "ميعاد الهدف",
  "Your goal moves back # month": "هدفك هيتأخر # شهر",
  "Your goal moves back # months": "هدفك هيتأخر # شهور",
  "Type a price to see what it does to this month and your goal": "اكتب سعر وشوف تأثيره على الشهر ده وعلى هدفك",
  "Add some spending first, then try cutting a category": "سجّل شوية مصاريف الأول، وبعدين جرّب تقلّل نوع",
  "You spend per month": "بتصرف في الشهر",
  "You would save per month": "هتوفّر في الشهر",
  "You would save per year": "هتوفّر في السنة",
  "Set a savings goal in Settings to see the date": "حدّد هدف ادخار من الإعدادات عشان تشوف الميعاد",
  "Goal reached!": "وصلت للهدف!",
  "Goal date now": "ميعاد الهدف دلوقتي",
  "Goal date with this cut": "ميعاد الهدف بالتقليل ده",
  "Sooner by # month": "أبكر بـ # شهر",
  "Sooner by # months": "أبكر بـ # شهور",
  "Without a monthly saving the goal has no date. This cut gives it one.": "من غير ادخار شهري الهدف ملوش ميعاد. التقليل ده بيديله ميعاد.",
  "Your money should last until the end of the month": "فلوسك المفروض تكفي لآخر الشهر",
  "Your money may run out": "فلوسك ممكن تخلص",
  "Money runs out around": "الفلوس بتخلص تقريباً",
  "Days before the month ends": "أيام قبل آخر الشهر",
  "Missing by month end": "الناقص لآخر الشهر",
  "Tap to see what to postpone or cut": "دوس عشان تشوف إيه تأجّله أو تقلّله",
  "Postpone if you can": "أجّل لو تقدر",
  "Spend less on": "قلّل المصروف على",
  "Do both and you reach the end of the month.": "اعمل الاتنين وهتوصل لآخر الشهر.",
  "Still missing after this": "لسه ناقص بعد كده",
  "Think about extra income or moving money from savings": "فكّر في دخل إضافي أو انقل من المدخرات",
  "now": "دلوقتي",
  "# month": "# شهر",
  "# months": "# شهور",
  // Gam'eya card
  "Gam'eya": "جمعية", "Your turn": "دورك", "Days to your turn": "أيام لحد دورك", "You collect": "هتقبض",
  "You collected your turn": "قبضت دورك", "It's your turn! Collect #": "جه دورك! اقبض #",
  "Installments left": "الأقساط الباقية", "Next installment": "القسط الجاي", "Got my payout": "استلمت المبلغ", "Paid installment": "دفعت القسط",
  // Wealth, price rises, weekly summary
  "Everything I own": "كل اللي أملكه",
  "Total": "الإجمالي",
  "In wallets": "في المحافظ",
  "Gold I bought, at today's price": "الدهب اللي اشتريته بسعر النهارده",
  "In dollars": "بالدولار",
  "In gold (21k)": "بالدهب (عيار 21)",
  "Turn on today's rates in Currencies & gold to see dollars and gold": "شغّل أسعار النهارده من العملات والدهب عشان تشوف بالدولار والدهب",
  "Over time": "على مدار الوقت",
  "Last 7 days": "آخر 7 أيام",
  "Last 30 days": "آخر 30 يوم",
  "Last 90 days": "آخر 90 يوم",
  "Come back in a few days. Pocketta saves your total once a day and draws the line here.": "ارجع بعد كام يوم. Pocketta بيحفظ إجماليك مرة في اليوم وبيرسم الخط هنا.",
  "Wallets and gold in one number, in dollars and gold, and how it changed": "المحافظ والدهب في رقم واحد، بالدولار والدهب، وإزاي اتغيّر",
  "Prices are rising": "الأسعار بتزيد",
  "At this pace, compared with your average of the last 3 months": "بالمعدل ده، مقارنة بمتوسط آخر 3 شهور",
  "Keep going, next week is a fresh start.": "كمّل، والأسبوع الجاي بداية جديدة.",
  // Piggy bank and goals
  "Piggy bank": "الحصالة",
  "Everything you saved, month by month": "كل اللي وفرته، شهر بشهر",
  "Goals": "الأهداف",
  "Umra, a car, a laptop, each with its own date": "عمرة، عربية، لابتوب، وكل واحد له ميعاده",
  "In your piggy bank": "في الحصالة",
  "In your piggy bank: #": "في الحصالة: #",
  "Put in": "حط فيها",
  "Take out": "اسحب",
  "Saved each month": "اللي وفرته كل شهر",
  "Month by month": "شهر بشهر",
  "so far": "لحد دلوقتي",
  "Saved over # months, about # a month": "وفرت على # شهور، حوالي # في الشهر",
  "Nothing in your piggy bank yet. Tap Put in when you set money aside.": "الحصالة فاضية لسه. دوس \"حط فيها\" لما تحوّش فلوس.",
  "# of #": "# من #",
  "# a month": "# في الشهر",
  "# goal. You put # a month towards it.": "# هدف. بتحط # في الشهر عليه.",
  "# goals. You put # a month towards them.": "# أهداف. بتحط # في الشهر عليهم.",
  "Finishes first": "هيخلص الأول",
  "No date yet. Put an amount towards it each month.": "مفيش ميعاد لسه. حط مبلغ ليه كل شهر.",
  "Add # a month and it moves up # months": "زوّد # في الشهر وهيتقدم # شهور",
  "No goals yet. Add one (Umra, a car, a laptop) and see when you will reach it.": "مفيش أهداف لسه. ضيف واحد (عمرة، عربية، لابتوب) وشوف هتوصله إمتى.",
  "Add a goal": "ضيف هدف",
  "New goal": "هدف جديد",
  "Edit goal": "تعديل الهدف",
  "Put towards it each month": "اللي بتحطه عليه كل شهر",
  "Delete this goal": "امسح الهدف ده",
  "Also goes into your piggy bank": "وبيتحط كمان في الحصالة",
  "Over the limit": "عديت الحد",
  "# of # EGP": "# من # جنيه",
  "Put in piggy bank": "حطها في الحصالة", "Put it in your piggy bank?": "تحب تحطها في الحصالة؟", "Put in your piggy bank": "اتحطت في الحصالة",
  "Tell me when a category goes over its monthly limit": "قولي لما بند يعدّي حده الشهري",
  "At month end, offer to put what's left in my piggy bank": "في آخر الشهر، اقترح أحط الباقي في الحصالة",
  "How long it lasts": "الحصالة تكفي قد إيه",
  "Your piggy bank covers # months of your spending": "الحصالة بتغطي # شهور من مصروفك",
  "You spend about # a month": "بتصرف حوالي # في الشهر",
  "Monthly target": "الهدف الشهري",
  "Pick an amount to put in every month and see how many months in a row you hit it.": "اختار مبلغ تحطه كل شهر وشوف كام شهر ورا بعض حققته.",
  "Set a monthly target": "حدد هدف شهري",
  "Change target": "غيّر الهدف",
  "Amount each month (0 to remove)": "المبلغ كل شهر (0 للإلغاء)",
  "This month: # of #": "الشهر ده: # من #",
  "You hit this month's target": "حققت هدف الشهر ده",
  "Still # to go this month": "فاضل # الشهر ده",
  "No streak yet. Hit the target this month to start one.": "لسه مفيش سلسلة. حقق هدف الشهر ده عشان تبدأ.",
  "1 month in a row": "شهر واحد ورا بعض",
  "# months in a row": "# شهور ورا بعض",
  "Scan receipt": "صوّر الفاتورة",
  "From gallery": "من المعرض",
  "Reading the receipt...": "بقرا الفاتورة...",
  "Found #. Check it is right.": "لقيت #. اتأكد إنه صح.",
  "Could not find an amount. Type it in.": "ملقيتش مبلغ. اكتبه بنفسك.",
  "Category over its limit": "بند عدّى حده",
  "Once a month per category, only after you pass the limit you set": "مرة في الشهر لكل بند، بعد ما تعدّي الحد اللي حطيته بس",
  "Offers to put what's left in your piggy bank": "بيقترح تحط اللي فاضل في الحصالة",
  "Bank messages": "رسايل البنك",
  "Suggest expenses from bank messages": "اقترح مصروفات من رسايل البنك",
  "When a bank text looks like spending, a notification offers to add it. Read on this phone only, never sent anywhere. You confirm every one.": "لما رسالة بنك تبان إنها مصروف، بيجيلك إشعار يقترح تسجله. بتتقرا على التليفون بس ومبتتبعتش لأي حتة. وانت اللي بتأكد كل واحدة.",
  'Currency': 'العملة', 'Or pick a day': 'أو اختار يوم', 'English': 'English',
};
const hasLetters = /[A-Za-z]/;
const number = /\d[\d,]*(?:\.\d+)?[kKM]?/g;
const currencyTail = /^(.*\S) ([A-Z]{3})$/;
const monthsRe = new RegExp('\\b(' + Object.keys(MONTHS).join('|') + ')\\b', 'g');
const monthDay = /\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) (\d{1,2})\b/g;
const daysRe = /\b(Mon|Tue|Wed|Thu|Fri|Sat|Sun|AM|PM)\b/g;
const EXTRA_RULES = [
  ["Add expense on (.+)", "سجّل مصروف يوم @1"],
  ["You're (.+) over the flexible amount for this month\\.", "عديت المبلغ المرن بـ @1 الشهر ده."],
  ["(.+): (\\d+) items? paid", "@1: @2 اتدفعوا"],
  ["(.+): mark all as paid", "@1: علّم الكل مدفوع"],
  ["Spending is up on last week\\. (.+) is the place to look first\\.", "الصرف زاد عن الأسبوع اللي فات. بص على @1 الأول."],
  ["(.+) took the biggest share\\. A small limit on it would keep the rest of the month calm\\.", "@1 أخد أكبر حصة. حد صغير عليه هيريّح باقي الشهر."],
  ["Good week: you spent less than the week before\\. Keep the same pace\\.", "أسبوع كويس: صرفت أقل من اللي قبله. كمّل على نفس الإيقاع."],
  ["You are over the flexible amount for this month: skip anything you can wait on until the next salary\\.", "عديت المبلغ المرن للشهر ده: أجّل أي حاجة تستحمل لحد المرتب الجاي."],
  ["A steady week\\. Keep logging expenses and the month stays under control\\.", "أسبوع هادي. كمّل سجّل مصاريفك والشهر هيفضل تحت السيطرة."],
  ["No spending recorded this week\\. Add what you spend so the summary has something to say\\.", "مفيش صرف متسجل الأسبوع ده. سجّل مصاريفك عشان الملخص يبقى فيه كلام."],
  ["Done by (.+)", "هيخلص في @1"],
  ["You spent (\\S+) (\\S+) this week\\.", "صرفت @1 @2 الأسبوع ده."],
  ["You spent (\\S+) (\\S+) this week, (\\d+)% more than last week\\.", "صرفت @1 @2 الأسبوع ده، أكتر بـ @3% من الأسبوع اللي فات."],
  ["You spent (\\S+) (\\S+) this week, (\\d+)% less than last week\\.", "صرفت @1 @2 الأسبوع ده، أقل بـ @3% من الأسبوع اللي فات."],
  ["You spent (\\S+) (\\S+) this week, the same as last week\\.", "صرفت @1 @2 الأسبوع ده، زي الأسبوع اللي فات بالظبط."],
  ["Most of it went to (.+), and you stayed under your daily budget on (\\d+) days\\.", "أغلبه راح على @1، وفضلت تحت ميزانيتك اليومية في @2 أيام."],
  ["Most of it went to (.+), and (\\d+) days were under your daily budget\\.", "أغلبه راح على @1، و@2 أيام كانوا تحت ميزانيتك اليومية."],
  ["Most of it went to (.+)\\.", "أغلبه راح على @1."],
  ["▲ (\\d+)% vs your usual", "▲ @1% عن المعتاد"],
];
const rules = [...RULES, ...EXTRA_RULES].map(([re, to]) => [new RegExp('^' + re + '$'), to]);
const cache = new Map();
let on = false;

export function tr(s) {
  if (!on || !s || !hasLetters.test(s)) return s;
  const lead = s.match(/^\s*/)[0], tail = s.match(/\s*$/)[0], core = s.trim();
  if (!core) return s;
  let r = cache.get(core);
  if (r === undefined) { r = translate(core); if (cache.size > 4000) cache.clear(); cache.set(core, r); }
  return lead + r + tail;
}

function lookup(s) { return EXTRA[s] ?? DICT[s]; }

function translate(s) {
  const d = lookup(s);
  if (d !== undefined) return d;
  const nums = s.match(number);
  if (nums) {
    const ar = lookup(s.replace(number, '#'));
    if (ar !== undefined) { let i = 0; return ar.replace(/#/g, () => nums[i++] ?? '#'); }
  }
  for (const [re, to] of rules) {
    const m = re.exec(s);
    if (!m) continue;
    let out = to;
    for (let i = 1; i < m.length; i++) out = out.replace('@' + i, translate(m[i]));
    return out;
  }
  for (const sep of [' · ', ' → ']) if (s.includes(sep)) return s.split(sep).map(translate).join(sep);
  const ct = currencyTail.exec(s);
  if (ct) { const ar = translate(ct[1]); if (ar !== ct[1]) return ar + ' ' + ct[2]; }
  return s.replace(monthDay, (_, mo, d2) => d2 + ' ' + (MONTHS[mo] || mo)).replace(monthsRe, (m) => MONTHS[m] || m).replace(daysRe, (m) => DAYS[m] || m);
}

const ATTRS = ['placeholder', 'aria-label', 'title'];
function walk(node) {
  if (node.nodeType === 3) {
    const t = tr(node.nodeValue);
    if (t !== node.nodeValue) node.nodeValue = t;
    return;
  }
  if (node.nodeType !== 1 || /^(SCRIPT|STYLE|INPUT|TEXTAREA)$/.test(node.tagName) && !node.placeholder) return;
  for (const a of ATTRS) {
    const v = node.getAttribute && node.getAttribute(a);
    if (v) { const t = tr(v); if (t !== v) node.setAttribute(a, t); }
  }
  for (let c = node.firstChild; c; c = c.nextSibling) walk(c);
}

export const isArabic = () => on;

/** Turns Arabic on: right-to-left page and every text translated as it appears. */
export function startArabic() {
  if (on) return;
  on = true;
  document.documentElement.lang = 'ar';
  document.documentElement.dir = 'rtl';
  walk(document.body);
  new MutationObserver((muts) => {
    for (const m of muts) {
      if (m.type === 'childList') m.addedNodes.forEach(walk);
      else if (m.type === 'characterData') walk(m.target);
      else walk(m.target);
    }
  }).observe(document.body, { childList: true, subtree: true, characterData: true, attributes: true, attributeFilter: ATTRS });
}
