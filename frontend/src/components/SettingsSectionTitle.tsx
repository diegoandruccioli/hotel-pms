interface SettingsSectionTitleProps {
  title: string;
  subtitle?: string;
}

/** Title of one settings section, under the settings layout's own page title. */
export const SettingsSectionTitle = ({ title, subtitle }: SettingsSectionTitleProps) => (
  <div>
    <h2 className="text-xl font-display font-semibold text-on-surface">{title}</h2>
    {subtitle && <p className="mt-1 text-sm font-body text-on-surface-variant">{subtitle}</p>}
  </div>
);
