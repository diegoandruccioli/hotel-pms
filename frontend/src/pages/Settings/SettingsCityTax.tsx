import { useTranslation } from 'react-i18next';
import { SettingsSectionTitle } from '../../components/SettingsSectionTitle';
import { CityTaxApplicabilitySection } from './CityTax/CityTaxApplicabilitySection';
import { CityTaxBackfillSection } from './CityTax/CityTaxBackfillSection';
import { CityTaxRatesSection } from './CityTax/CityTaxRatesSection';
import { HotelCategorySection } from './CityTax/HotelCategorySection';

export const SettingsCityTax = () => {
  const { t } = useTranslation('settings');

  return (
    <div className="space-y-6">
      <SettingsSectionTitle title={t('settings_section_city_tax')} subtitle={t('city_tax_page_subtitle')} />
      <CityTaxApplicabilitySection />
      <HotelCategorySection />
      <CityTaxRatesSection />
      <CityTaxBackfillSection />
    </div>
  );
};
