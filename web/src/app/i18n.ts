import { Injectable, effect, signal } from '@angular/core';

export type Lang = 'en' | 'hi';
const KEY = 'ym_lang';

/** UI string translations. The AI-generated text (reasons, chat) is translated
 *  server-side by passing `lang` to the API.
 *
 *  Decorative glyphs (☆, ✓, ↗, emoji) deliberately live in the templates, not
 *  here, so they can be marked aria-hidden instead of being read aloud. */
const DICT: Record<string, { en: string; hi: string }> = {
  'nav.find': { en: 'Find schemes', hi: 'योजनाएँ खोजें' },
  'nav.dashboard': { en: 'Dashboard', hi: 'डैशबोर्ड' },
  'nav.login': { en: 'Log in', hi: 'लॉग इन' },
  'nav.signup': { en: 'Sign up', hi: 'साइन अप' },
  'nav.logout': { en: 'Log out', hi: 'लॉग आउट' },
  'nav.hi': { en: 'Hi', hi: 'नमस्ते' },
  'nav.primary': { en: 'Primary', hi: 'मुख्य' },

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
  'form.agePlaceholder': { en: 'e.g. 26', hi: 'जैसे— 26' },
  'form.occupationPlaceholder': { en: 'e.g. small farmer, student', hi: 'जैसे— छोटा किसान, विद्यार्थी' },
  'form.incomePlaceholder': { en: 'e.g. 90000', hi: 'जैसे— 90000' },
  'form.aboutPlaceholder': {
    en: 'e.g. I want to start a small tailoring business and need a collateral-free loan',
    hi: 'जैसे— मैं एक छोटा सिलाई व्यवसाय शुरू करना चाहता/चाहती हूँ और बिना गारंटी का ऋण चाहिए',
  },

  // ---- Select options ----
  // Labels only. The <option value> stays English because it is sent to the
  // API as the profile and matched against the English scheme dataset.
  'gender.male': { en: 'Male', hi: 'पुरुष' },
  'gender.female': { en: 'Female', hi: 'महिला' },
  'gender.other': { en: 'Other', hi: 'अन्य' },

  // The English acronym is kept in the Hindi label — official forms use it.
  'category.General': { en: 'General', hi: 'सामान्य' },
  'category.SC': { en: 'SC', hi: 'अनुसूचित जाति (SC)' },
  'category.ST': { en: 'ST', hi: 'अनुसूचित जनजाति (ST)' },
  'category.OBC': { en: 'OBC', hi: 'अन्य पिछड़ा वर्ग (OBC)' },
  'category.Minority': { en: 'Minority', hi: 'अल्पसंख्यक' },

  'state.Andhra Pradesh': { en: 'Andhra Pradesh', hi: 'आंध्र प्रदेश' },
  'state.Assam': { en: 'Assam', hi: 'असम' },
  'state.Bihar': { en: 'Bihar', hi: 'बिहार' },
  'state.Chhattisgarh': { en: 'Chhattisgarh', hi: 'छत्तीसगढ़' },
  'state.Delhi': { en: 'Delhi', hi: 'दिल्ली' },
  'state.Gujarat': { en: 'Gujarat', hi: 'गुजरात' },
  'state.Haryana': { en: 'Haryana', hi: 'हरियाणा' },
  'state.Himachal Pradesh': { en: 'Himachal Pradesh', hi: 'हिमाचल प्रदेश' },
  'state.Jharkhand': { en: 'Jharkhand', hi: 'झारखंड' },
  'state.Karnataka': { en: 'Karnataka', hi: 'कर्नाटक' },
  'state.Kerala': { en: 'Kerala', hi: 'केरल' },
  'state.Madhya Pradesh': { en: 'Madhya Pradesh', hi: 'मध्य प्रदेश' },
  'state.Maharashtra': { en: 'Maharashtra', hi: 'महाराष्ट्र' },
  'state.Odisha': { en: 'Odisha', hi: 'ओडिशा' },
  'state.Punjab': { en: 'Punjab', hi: 'पंजाब' },
  'state.Rajasthan': { en: 'Rajasthan', hi: 'राजस्थान' },
  'state.Tamil Nadu': { en: 'Tamil Nadu', hi: 'तमिलनाडु' },
  'state.Telangana': { en: 'Telangana', hi: 'तेलंगाना' },
  'state.Uttar Pradesh': { en: 'Uttar Pradesh', hi: 'उत्तर प्रदेश' },
  'state.Uttarakhand': { en: 'Uttarakhand', hi: 'उत्तराखंड' },
  'state.West Bengal': { en: 'West Bengal', hi: 'पश्चिम बंगाल' },

  'status.matching': { en: 'Reasoning about your eligibility — this can take a few seconds.', hi: 'आपकी पात्रता का विश्लेषण हो रहा है — इसमें कुछ सेकंड लग सकते हैं।' },
  'status.none': { en: 'No matching schemes found. Try adding more detail above.', hi: 'कोई मेल खाती योजना नहीं मिली। ऊपर अधिक जानकारी जोड़कर देखें।' },
  'results.heading': { en: 'schemes for you', hi: 'आपके लिए योजनाएँ' },
  'results.saveHint': { en: 'Log in to save schemes to your dashboard', hi: 'योजनाएँ सहेजने के लिए लॉग इन करें' },

  'scheme.benefit': { en: 'Benefit:', hi: 'लाभ:' },
  'scheme.apply': { en: 'How to apply:', hi: 'आवेदन कैसे करें:' },
  'scheme.official': { en: 'Official site', hi: 'आधिकारिक साइट' },
  'scheme.ask': { en: 'Ask a question', hi: 'प्रश्न पूछें' },
  'scheme.close': { en: 'Close', hi: 'बंद करें' },
  'scheme.save': { en: 'Save', hi: 'सहेजें' },
  'scheme.saved': { en: 'Saved', hi: 'सहेजा गया' },
  'scheme.saving': { en: 'Saving…', hi: 'सहेजा जा रहा…' },
  'scheme.qlabel': { en: 'Your question about this scheme', hi: 'इस योजना के बारे में आपका प्रश्न' },
  'scheme.qplaceholder': { en: 'e.g. What documents do I need?', hi: 'जैसे— मुझे कौन-से दस्तावेज़ चाहिए?' },
  'scheme.askBtn': { en: 'Ask', hi: 'पूछें' },
  'scheme.matchScore': { en: 'match', hi: 'मिलान' },

  'verdict.eligible': { en: 'Likely eligible', hi: 'संभवतः पात्र' },
  'verdict.maybe': { en: 'Maybe eligible', hi: 'शायद पात्र' },
  'verdict.not_eligible': { en: 'Not eligible', hi: 'पात्र नहीं' },

  'toast.saved': { en: 'Saved', hi: 'सहेजा गया' },
  'toast.saveError': { en: 'Could not save — try again', hi: 'सहेजा नहीं जा सका — पुनः प्रयास करें' },
  'toast.removed': { en: 'Removed', hi: 'हटा दिया गया' },

  // ---- Dashboard ----
  'dash.title': { en: 'Your dashboard', hi: 'आपका डैशबोर्ड' },
  'dash.subtitle': { en: 'Saved schemes and recent searches for', hi: 'सहेजी गई योजनाएँ और हाल की खोजें —' },
  'dash.savedHeading': { en: 'Saved schemes', hi: 'सहेजी गई योजनाएँ' },
  'dash.emptySaved': { en: 'No saved schemes yet — run a match and tap Save on any result.', hi: 'अभी कोई योजना सहेजी नहीं गई — एक मिलान चलाएँ और किसी भी परिणाम पर सहेजें दबाएँ।' },
  'dash.historyHeading': { en: 'Recent searches', hi: 'हाल की खोजें' },
  'dash.emptyHistory': { en: 'No searches yet.', hi: 'अभी कोई खोज नहीं।' },
  'dash.noDetails': { en: '(no details given)', hi: '(कोई जानकारी नहीं दी गई)' },
  'dash.remove': { en: 'Remove', hi: 'हटाएँ' },
  'dash.removeLabel': { en: 'Remove saved scheme', hi: 'सहेजी गई योजना हटाएँ' },
  'dash.matchCount': { en: 'matches', hi: 'मिलान' },
  'dash.matchCountOne': { en: 'match', hi: 'मिलान' },

  // ---- Auth ----
  'auth.loginTitle': { en: 'Log in', hi: 'लॉग इन' },
  'auth.loginSubtitle': { en: 'Welcome back — access your saved schemes.', hi: 'वापसी पर स्वागत है — अपनी सहेजी गई योजनाएँ देखें।' },
  'auth.registerTitle': { en: 'Create your account', hi: 'अपना खाता बनाएँ' },
  'auth.registerSubtitle': { en: 'Sign up to save schemes and keep a dashboard.', hi: 'योजनाएँ सहेजने और डैशबोर्ड रखने के लिए साइन अप करें।' },
  'auth.username': { en: 'Username', hi: 'उपयोगकर्ता नाम' },
  'auth.password': { en: 'Password', hi: 'पासवर्ड' },
  'auth.usernameRequired': { en: 'Username is required.', hi: 'उपयोगकर्ता नाम आवश्यक है।' },
  'auth.passwordRequired': { en: 'Password is required.', hi: 'पासवर्ड आवश्यक है।' },
  'auth.usernameMin': { en: 'Username must be at least 3 characters.', hi: 'उपयोगकर्ता नाम कम से कम 3 अक्षरों का होना चाहिए।' },
  'auth.passwordMin': { en: 'Password must be at least 6 characters.', hi: 'पासवर्ड कम से कम 6 अक्षरों का होना चाहिए।' },
  'auth.usernamePlaceholder': { en: 'at least 3 characters', hi: 'कम से कम 3 अक्षर' },
  'auth.passwordPlaceholder': { en: 'at least 6 characters', hi: 'कम से कम 6 अक्षर' },
  'auth.loggingIn': { en: 'Logging in…', hi: 'लॉग इन हो रहा है…' },
  'auth.creating': { en: 'Creating…', hi: 'बनाया जा रहा है…' },
  'auth.newHere': { en: 'New here?', hi: 'यहाँ नए हैं?' },
  'auth.createAccount': { en: 'Create an account', hi: 'खाता बनाएँ' },
  'auth.haveAccount': { en: 'Already have an account?', hi: 'पहले से खाता है?' },
  'auth.invalidCreds': { en: 'Invalid username or password', hi: 'उपयोगकर्ता नाम या पासवर्ड ग़लत है' },
  'auth.loginFailed': { en: 'Login failed. Is the API running?', hi: 'लॉग इन विफल। क्या API चल रही है?' },
  'auth.taken': { en: 'That username is already taken', hi: 'यह उपयोगकर्ता नाम पहले से लिया जा चुका है' },
  'auth.registerFailed': { en: 'Registration failed. Is the API running?', hi: 'पंजीकरण विफल। क्या API चल रही है?' },
  'auth.lengthError': { en: 'Username must be 3+ chars and password 6+ chars.', hi: 'उपयोगकर्ता नाम 3+ अक्षर और पासवर्ड 6+ अक्षर का होना चाहिए।' },

  // ---- Two-factor authentication ----
  'mfa.heading': { en: 'Two-factor authentication', hi: 'दो-चरणीय सत्यापन' },
  'mfa.explain': {
    en: 'Add a second step to your login using a free authenticator app such as Google Authenticator or Authy. No SMS, no phone number needed.',
    hi: 'Google Authenticator या Authy जैसे मुफ़्त ऐप से अपने लॉग इन में एक दूसरा चरण जोड़ें। न SMS, न फ़ोन नंबर चाहिए।',
  },
  'mfa.enableBtn': { en: 'Enable two-factor', hi: 'दो-चरणीय सत्यापन चालू करें' },
  // Names the apps on the QR screen itself. The pre-enrolment blurb that used to
  // be the only place they appeared is replaced by this text the moment the user
  // clicks Enable — i.e. exactly when they need to know what to scan it with.
  'mfa.scanExplain': {
    en: 'Scan this QR code with a free authenticator app, then enter the 6-digit code it shows.',
    hi: 'इस QR कोड को किसी मुफ़्त प्रमाणक ऐप से स्कैन करें, फिर उसमें दिख रहा 6-अंकों का कोड दर्ज करें।',
  },
  'mfa.appSuggestions': {
    en: "Don't have one? Google Authenticator, Microsoft Authenticator, and Authy are all free. Any app that supports TOTP will work.",
    hi: 'कोई ऐप नहीं है? Google Authenticator, Microsoft Authenticator और Authy — सभी मुफ़्त हैं। TOTP समर्थित कोई भी ऐप काम करेगा।',
  },
  'mfa.qrAlt': {
    en: 'QR code for enrolling this account in your authenticator app',
    hi: 'इस खाते को आपके प्रमाणक ऐप में जोड़ने के लिए QR कोड',
  },
  'mfa.manualEntry': { en: "Can't scan? Enter this key manually:", hi: 'स्कैन नहीं कर सकते? यह कुंजी हाथ से दर्ज करें:' },
  'mfa.confirmLabel': { en: 'Code from your app', hi: 'आपके ऐप का कोड' },
  'mfa.enableConfirm': { en: 'Verify and enable', hi: 'सत्यापित करें और चालू करें' },
  'mfa.stateOn': { en: 'Two-factor authentication is on.', hi: 'दो-चरणीय सत्यापन चालू है।' },
  'mfa.codesRemaining': { en: 'recovery codes remaining', hi: 'रिकवरी कोड शेष' },
  'mfa.disableBtn': { en: 'Turn off two-factor', hi: 'दो-चरणीय सत्यापन बंद करें' },
  'mfa.disableExplain': {
    en: 'Confirm with your password and a current code. This removes your second factor and all recovery codes.',
    hi: 'अपना पासवर्ड और एक मौजूदा कोड दर्ज करें। इससे आपका दूसरा चरण और सभी रिकवरी कोड हट जाएँगे।',
  },
  'mfa.disableConfirm': { en: 'Turn off', hi: 'बंद करें' },
  'mfa.cancel': { en: 'Cancel', hi: 'रद्द करें' },
  'mfa.working': { en: 'Working…', hi: 'हो रहा है…' },
  'mfa.enabled': { en: 'Two-factor authentication enabled', hi: 'दो-चरणीय सत्यापन चालू हो गया' },
  'mfa.disabled': { en: 'Two-factor authentication turned off', hi: 'दो-चरणीय सत्यापन बंद कर दिया गया' },

  'mfa.recoveryTitle': { en: 'Save your recovery codes now', hi: 'अपने रिकवरी कोड अभी सहेजें' },
  'mfa.recoveryExplain': {
    en: 'Each code works once, and they will never be shown again. Keep them somewhere safe — they are the only way back in if you lose your phone.',
    hi: 'हर कोड केवल एक बार चलेगा और ये दोबारा नहीं दिखाए जाएँगे। इन्हें सुरक्षित रखें — फ़ोन खोने पर यही एकमात्र रास्ता है।',
  },
  'mfa.copy': { en: 'Copy codes', hi: 'कोड कॉपी करें' },
  'mfa.copied': { en: 'Recovery codes copied', hi: 'रिकवरी कोड कॉपी हो गए' },
  'mfa.copyFailed': { en: 'Could not copy — select and copy manually', hi: 'कॉपी नहीं हुआ — चुनकर हाथ से कॉपी करें' },
  'mfa.savedThem': { en: "I've saved them", hi: 'मैंने सहेज लिए' },

  'mfa.verifyTitle': { en: 'Two-factor verification', hi: 'दो-चरणीय सत्यापन' },
  'mfa.verifySubtitle': {
    en: 'Enter the 6-digit code from your authenticator app.',
    hi: 'अपने प्रमाणक ऐप का 6-अंकों का कोड दर्ज करें।',
  },
  'mfa.codeLabel': { en: 'Verification code', hi: 'सत्यापन कोड' },
  'mfa.codePlaceholder': { en: '123456', hi: '123456' },
  'mfa.verifyBtn': { en: 'Verify', hi: 'सत्यापित करें' },
  'mfa.verifying': { en: 'Verifying…', hi: 'सत्यापित हो रहा है…' },
  'mfa.invalidCode': { en: 'That code is not valid. Try the next one your app shows.', hi: 'यह कोड मान्य नहीं है। ऐप में दिख रहा अगला कोड आज़माएँ।' },
  'mfa.tooManyAttempts': { en: 'Too many incorrect codes. Try again in a few minutes.', hi: 'बहुत बार ग़लत कोड। कुछ मिनट बाद पुनः प्रयास करें।' },
  'mfa.recoveryHint': { en: 'Lost your phone? Use a recovery code instead, or', hi: 'फ़ोन खो गया? इसके बजाय रिकवरी कोड डालें, या' },
  'mfa.startOver': { en: 'start over', hi: 'फिर से शुरू करें' },

  // ---- Errors & screen-reader-only text ----
  'error.generic': {
    en: 'Something went wrong. Are the API and GenAI services running?',
    hi: 'कुछ गड़बड़ हुई। क्या API और GenAI सेवाएँ चल रही हैं?',
  },
  'a11y.skip': { en: 'Skip to main content', hi: 'मुख्य सामग्री पर जाएँ' },
  'a11y.newTab': { en: '(opens in a new tab)', hi: '(नए टैब में खुलता है)' },
  'a11y.loading': { en: 'Loading…', hi: 'लोड हो रहा है…' },
  'a11y.langToEn': { en: 'View in English', hi: 'View in English' },
  'a11y.langToHi': { en: 'हिंदी में देखें', hi: 'हिंदी में देखें' },

  'footer.note': {
    en: 'Informational matches based on a curated dataset — always confirm on the official portal. 100% open-source · powered by open LLMs.',
    hi: 'यह जानकारी एक क्यूरेटेड डेटासेट पर आधारित है — कृपया आधिकारिक पोर्टल पर पुष्टि करें। 100% ओपन-सोर्स · ओपन LLM द्वारा संचालित।',
  },
};

@Injectable({ providedIn: 'root' })
export class I18n {
  readonly lang = signal<Lang>((localStorage.getItem(KEY) as Lang) || 'en');

  constructor() {
    // Screen readers pick pronunciation from <html lang>. Leaving it at "en"
    // while rendering Devanagari makes the page unintelligible aloud.
    effect(() => (document.documentElement.lang = this.lang()));
  }

  toggle(): void {
    const next: Lang = this.lang() === 'en' ? 'hi' : 'en';
    this.lang.set(next);
    localStorage.setItem(KEY, next);
  }

  /** `fallback` is for keys built from data (option values): an untranslated
   *  option should read as its English name, not as a raw dictionary key. */
  t(key: string, fallback?: string): string {
    return DICT[key]?.[this.lang()] ?? fallback ?? key;
  }
}
