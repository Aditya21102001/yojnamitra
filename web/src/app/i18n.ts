import { Injectable, signal } from '@angular/core';

export type Lang = 'en' | 'hi';
const KEY = 'ym_lang';

/** UI string translations. The AI-generated text (reasons, chat) is translated
 *  server-side by passing `lang` to the API. */
const DICT: Record<string, { en: string; hi: string }> = {
  'nav.find': { en: 'Find schemes', hi: 'योजनाएँ खोजें' },
  'nav.dashboard': { en: 'Dashboard', hi: 'डैशबोर्ड' },
  'nav.login': { en: 'Log in', hi: 'लॉग इन' },
  'nav.signup': { en: 'Sign up', hi: 'साइन अप' },
  'nav.logout': { en: 'Log out', hi: 'लॉग आउट' },
  'nav.hi': { en: 'Hi', hi: 'नमस्ते' },

  'hero.title': {
    en: 'Find the government schemes you actually qualify for',
    hi: 'वे सरकारी योजनाएँ खोजें जिनके लिए आप वास्तव में पात्र हैं',
  },
  'hero.sub': {
    en: 'Describe your situation. A local AI matches you to welfare schemes, tells you why you qualify, and how to apply.',
    hi: 'अपनी स्थिति बताइए। एक लोकल AI आपको कल्याणकारी योजनाओं से मिलाता है, बताता है कि आप क्यों पात्र हैं और कैसे आवेदन करें।',
  },

  'form.title': { en: 'Your details', hi: 'आपकी जानकारी' },
  'form.subtitle': { en: 'Fill in whatever you know — every field is optional.', hi: 'जो भी पता हो भरें — हर फ़ील्ड वैकल्पिक है।' },
  'form.age': { en: 'Age', hi: 'आयु' },
  'form.gender': { en: 'Gender', hi: 'लिंग' },
  'form.state': { en: 'State', hi: 'राज्य' },
  'form.category': { en: 'Social category', hi: 'सामाजिक श्रेणी' },
  'form.occupation': { en: 'Occupation', hi: 'व्यवसाय' },
  'form.income': { en: 'Annual household income (₹)', hi: 'वार्षिक पारिवारिक आय (₹)' },
  'form.about': { en: 'Anything else about your situation?', hi: 'अपनी स्थिति के बारे में और कुछ?' },
  'form.submit': { en: 'Find my schemes', hi: 'मेरी योजनाएँ खोजें' },
  'form.matching': { en: 'Matching…', hi: 'मिलान हो रहा है…' },
  'form.selectState': { en: 'Select state', hi: 'राज्य चुनें' },
  'form.selectCategory': { en: 'Select category', hi: 'श्रेणी चुनें' },
  'form.preferNot': { en: 'Prefer not to say', hi: 'नहीं बताना चाहते' },

  'status.matching': { en: 'Reasoning about your eligibility — this can take a few seconds.', hi: 'आपकी पात्रता का विश्लेषण हो रहा है — इसमें कुछ सेकंड लग सकते हैं।' },
  'status.none': { en: 'No matching schemes found. Try adding more detail above.', hi: 'कोई मेल खाती योजना नहीं मिली। ऊपर अधिक जानकारी जोड़कर देखें।' },
  'results.heading': { en: 'schemes for you', hi: 'आपके लिए योजनाएँ' },
  'results.saveHint': { en: 'Log in to save schemes to your dashboard', hi: 'योजनाएँ सहेजने के लिए लॉग इन करें' },

  'scheme.benefit': { en: 'Benefit:', hi: 'लाभ:' },
  'scheme.apply': { en: 'How to apply:', hi: 'आवेदन कैसे करें:' },
  'scheme.official': { en: 'Official site ↗', hi: 'आधिकारिक साइट ↗' },
  'scheme.ask': { en: 'Ask a question', hi: 'प्रश्न पूछें' },
  'scheme.close': { en: 'Close', hi: 'बंद करें' },
  'scheme.save': { en: '☆ Save', hi: '☆ सहेजें' },
  'scheme.saved': { en: '✓ Saved', hi: '✓ सहेजा गया' },
  'scheme.saving': { en: 'Saving…', hi: 'सहेजा जा रहा…' },
  'scheme.qplaceholder': { en: 'e.g. What documents do I need?', hi: 'जैसे— मुझे कौन-से दस्तावेज़ चाहिए?' },
  'scheme.askBtn': { en: 'Ask', hi: 'पूछें' },

  'verdict.eligible': { en: 'Likely eligible', hi: 'संभवतः पात्र' },
  'verdict.maybe': { en: 'Maybe eligible', hi: 'शायद पात्र' },
  'verdict.not_eligible': { en: 'Not eligible', hi: 'पात्र नहीं' },

  'toast.saved': { en: 'Saved ✓', hi: 'सहेजा गया ✓' },
  'toast.saveError': { en: 'Could not save — try again', hi: 'सहेजा नहीं जा सका — पुनः प्रयास करें' },

  'footer.note': {
    en: 'Informational matches based on a curated dataset — always confirm on the official portal. 100% open-source · powered by open LLMs.',
    hi: 'यह जानकारी एक क्यूरेटेड डेटासेट पर आधारित है — कृपया आधिकारिक पोर्टल पर पुष्टि करें। 100% ओपन-सोर्स · ओपन LLM द्वारा संचालित।',
  },
};

@Injectable({ providedIn: 'root' })
export class I18n {
  readonly lang = signal<Lang>((localStorage.getItem(KEY) as Lang) || 'en');

  toggle(): void {
    const next: Lang = this.lang() === 'en' ? 'hi' : 'en';
    this.lang.set(next);
    localStorage.setItem(KEY, next);
  }

  t(key: string): string {
    return DICT[key]?.[this.lang()] ?? key;
  }
}
