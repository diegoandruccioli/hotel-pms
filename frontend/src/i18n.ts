import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import commonEn from './locales/en/common.json';
import authEn from './locales/en/auth.json';
import errorsEn from './locales/en/errors.json';

import commonIt from './locales/it/common.json';
import authIt from './locales/it/auth.json';
import errorsIt from './locales/it/errors.json';

const resources = {
  en: {
    common: commonEn,
    auth: authEn,
    errors: errorsEn,
  },
  it: {
    common: commonIt,
    auth: authIt,
    errors: errorsIt,
  },
};

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: 'en',
    defaultNS: 'common',
    interpolation: {
      escapeValue: false, // react already safes from xss
    },
  });

export default i18n;
