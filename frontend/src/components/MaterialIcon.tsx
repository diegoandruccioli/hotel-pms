interface MaterialIconProps {
  /** Material Symbols icon name, e.g. "dashboard", "hotel", "group" */
  name: string;
  /** Additional CSS classes */
  className?: string;
  /** Render with filled variant */
  filled?: boolean;
  /** Pixel size (applied as font-size and width/height) */
  size?: number;
  /** Accessible label — omit for decorative icons (aria-hidden will be set) */
  label?: string;
}

export const MaterialIcon = ({
  name,
  className = "",
  filled = false,
  size,
  label,
}: MaterialIconProps) => {
  const sizeStyle = size
    ? { fontSize: `${size}px`, width: `${size}px`, height: `${size}px` }
    : undefined;

  return (
    <span
      className={`material-symbols-outlined${filled ? " filled" : ""} ${className}`.trim()}
      style={sizeStyle}
      aria-hidden={!label}
      aria-label={label}
      role={label ? "img" : undefined}
    >
      {name}
    </span>
  );
};
