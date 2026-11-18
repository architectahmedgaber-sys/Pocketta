// Arabic for the web app, same idea as the Android app: the UI is written in English and, when the language is Arabic,
// every text on the page is looked up in a dictionary right after it is drawn. Texts it doesn't know stay in English.
import { DICT, MONTHS, DAYS, RULES } from './ar-dict.js';

const EXTRA = {
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
  'Currency': 'العملة', 'Or pick a day': 'أو اختار يوم', 'English': 'English',
};
const hasLetters = /[A-Za-z]/;
const number = /\d[\d,]*(?:\.\d+)?[kKM]?/g;
const currencyTail = /^(.*\S) ([A-Z]{3})$/;
const monthsRe = new RegExp('\\b(' + Object.keys(MONTHS).join('|') + ')\\b', 'g');
const monthDay = /\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) (\d{1,2})\b/g;
const daysRe = /\b(Mon|Tue|Wed|Thu|Fri|Sat|Sun|AM|PM)\b/g;
const EXTRA_RULES = [
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
