import { useTranslation, type UseTranslationOptions } from 'react-i18next';
import i18n from '../i18n';

/**
 * Registers local component translations on the fly and returns the `t` function.
 * @param namespace Unique namespace for this component (e.g. 'UserMenu')
 * @param itTranslations Italian translation dictionary
 * @param enTranslations English translation dictionary
 */
export function useComponentTranslation(
  namespace: string,
  itTranslations: Record<string, string>,
  enTranslations: Record<string, string>,
  options?: UseTranslationOptions<string>
) {
  // Inject translations synchronously if they haven't been added yet.
  if (!i18n.hasResourceBundle('it', namespace)) {
    i18n.addResourceBundle('it', namespace, itTranslations, true, true);
  }
  if (!i18n.hasResourceBundle('en', namespace)) {
    i18n.addResourceBundle('en', namespace, enTranslations, true, true);
  }

  return useTranslation(namespace, options);
}
