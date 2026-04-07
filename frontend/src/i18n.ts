import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import commonEn from './locales/en/common.json';
import authEn from './locales/en/auth.json';
import errorsEn from './locales/en/errors.json';
import settingsEn from './locales/en/settings.json';
import staysEn from './locales/en/stays.json';
import guestsEn from './locales/en/guests.json';
import reservationsEn from './locales/en/reservations.json';
import roomsEn from './locales/en/rooms.json';
import calendarEn from './locales/en/calendar.json';
import billingEn from './locales/en/billing.json';
import restaurantEn from './locales/en/restaurant.json';
import housekeepingEn from './locales/en/housekeeping.json';
import dashboardEn from './locales/en/dashboard.json';

import commonIt from './locales/it/common.json';
import authIt from './locales/it/auth.json';
import errorsIt from './locales/it/errors.json';
import settingsIt from './locales/it/settings.json';
import staysIt from './locales/it/stays.json';
import guestsIt from './locales/it/guests.json';
import reservationsIt from './locales/it/reservations.json';
import roomsIt from './locales/it/rooms.json';
import calendarIt from './locales/it/calendar.json';
import billingIt from './locales/it/billing.json';
import restaurantIt from './locales/it/restaurant.json';
import housekeepingIt from './locales/it/housekeeping.json';
import dashboardIt from './locales/it/dashboard.json';

const resources = {
  en: {
    common: commonEn,
    auth: authEn,
    errors: errorsEn,
    settings: settingsEn,
    stays: staysEn,
    guests: guestsEn,
    reservations: reservationsEn,
    rooms: roomsEn,
    calendar: calendarEn,
    billing: billingEn,
    restaurant: restaurantEn,
    housekeeping: housekeepingEn,
    dashboard: dashboardEn,
  },
  it: {
    common: commonIt,
    auth: authIt,
    errors: errorsIt,
    settings: settingsIt,
    stays: staysIt,
    guests: guestsIt,
    reservations: reservationsIt,
    rooms: roomsIt,
    calendar: calendarIt,
    billing: billingIt,
    restaurant: restaurantIt,
    housekeeping: housekeepingIt,
    dashboard: dashboardIt,
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
