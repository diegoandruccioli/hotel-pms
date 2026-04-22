import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import commonEn from './locales/en/common.json';
import authEn from './locales/en/auth.json';
import errorsEn from './locales/en/errors.json';
import settingsEn from './locales/en/settings.json';
import guestsEn from './locales/en/guests.json';
import staysEn from './locales/en/stays.json';
import roomsEn from './locales/en/rooms.json';
import reservationsEn from './locales/en/reservations.json';

import commonIt from './locales/it/common.json';
import authIt from './locales/it/auth.json';
import errorsIt from './locales/it/errors.json';
import settingsIt from './locales/it/settings.json';
import guestsIt from './locales/it/guests.json';
import staysIt from './locales/it/stays.json';
import roomsIt from './locales/it/rooms.json';
import reservationsIt from './locales/it/reservations.json';

const resources = {
  en: {
    common: commonEn,
    auth: authEn,
    errors: errorsEn,
    settings: settingsEn,
    guests: guestsEn,
    stays: staysEn,
    rooms: roomsEn,
    reservations: reservationsEn,
  },
  it: {
    common: commonIt,
    auth: authIt,
    errors: errorsIt,
    settings: settingsIt,
    guests: guestsIt,
    stays: staysIt,
    rooms: roomsIt,
    reservations: reservationsIt,
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
