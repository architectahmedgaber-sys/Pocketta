// Countries, their money, and how many decimals that money uses. One list for the first-run picker and Settings.
// [code, Arabic name, English name, currency code, decimals]
export const COUNTRIES = [
  ['EG', 'مصر', 'Egypt', 'EGP', 2],
  ['JO', 'الأردن', 'Jordan', 'JOD', 3],
  ['PS', 'فلسطين', 'Palestine', 'ILS', 2],
  ['LY', 'ليبيا', 'Libya', 'LYD', 3],
  ['TN', 'تونس', 'Tunisia', 'TND', 3],
  ['DZ', 'الجزائر', 'Algeria', 'DZD', 2],
  ['MA', 'المغرب', 'Morocco', 'MAD', 2],
  ['MR', 'موريتانيا', 'Mauritania', 'MRU', 2],
  ['SD', 'السودان', 'Sudan', 'SDG', 2],
  ['SA', 'السعودية', 'Saudi Arabia', 'SAR', 2],
  ['AE', 'الإمارات', 'United Arab Emirates', 'AED', 2],
  ['KW', 'الكويت', 'Kuwait', 'KWD', 3],
  ['QA', 'قطر', 'Qatar', 'QAR', 2],
  ['BH', 'البحرين', 'Bahrain', 'BHD', 3],
  ['OM', 'عُمان', 'Oman', 'OMR', 3],
  ['IQ', 'العراق', 'Iraq', 'IQD', 0],
  ['LB', 'لبنان', 'Lebanon', 'LBP', 0],
  ['SY', 'سوريا', 'Syria', 'SYP', 0],
  ['YE', 'اليمن', 'Yemen', 'YER', 0],
];
// Other choices that are not a country: the dollar and the euro.
export const OTHER_CURRENCIES = [['USD', 'دولار أمريكي', 'US dollar', 2], ['EUR', 'يورو', 'Euro', 2]];
export const FLAGS = { EG: '🇪🇬', JO: '🇯🇴', PS: '🇵🇸', LY: '🇱🇾', TN: '🇹🇳', DZ: '🇩🇿', MA: '🇲🇦', MR: '🇲🇷', SD: '🇸🇩', SA: '🇸🇦', AE: '🇦🇪', KW: '🇰🇼', QA: '🇶🇦', BH: '🇧🇭', OM: '🇴🇲', IQ: '🇮🇶', LB: '🇱🇧', SY: '🇸🇾', YE: '🇾🇪', XX: '🌍' };
// Currency names, for the list and the Settings card.
export const CURRENCY_AR = { EGP: 'جنيه مصري', JOD: 'دينار أردني', ILS: 'شيكل', LYD: 'دينار ليبي', TND: 'دينار تونسي', DZD: 'دينار جزائري', MAD: 'درهم مغربي', MRU: 'أوقية', SDG: 'جنيه سوداني', SAR: 'ريال سعودي', AED: 'درهم إماراتي', KWD: 'دينار كويتي', QAR: 'ريال قطري', BHD: 'دينار بحريني', OMR: 'ريال عُماني', IQD: 'دينار عراقي', LBP: 'ليرة لبنانية', SYP: 'ليرة سورية', YER: 'ريال يمني', USD: 'دولار أمريكي', EUR: 'يورو' };
const DEC = {};
for (const [, , , cur, d] of COUNTRIES) DEC[cur] = d;
for (const [cur, , , d] of OTHER_CURRENCIES) DEC[cur] = d;

/** Decimals a currency uses (2 for any code we do not know). */
export const decimalsOf = (cur) => (cur in DEC ? DEC[cur] : 2);
/** The country a saved setting points to. Old data has none: Egypt with EGP, as before. */
export function countryFor(code, cur) {
  return COUNTRIES.find((c) => c[0] === code) || COUNTRIES.find((c) => c[3] === cur) || null;
}
