type IconProps = { size?: number };

function Svg({ size = 20, children }: IconProps & { children: React.ReactNode }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

export function IdCardIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <rect x="2.75" y="5" width="18.5" height="14" rx="2.5" />
      <circle cx="8.5" cy="11" r="2.1" />
      <path d="M5.4 16c.6-1.5 1.8-2.3 3.1-2.3s2.5.8 3.1 2.3M14 10h4.5M14 13.5h3" />
    </Svg>
  );
}

export function FaceIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M4 8V6.5A2.5 2.5 0 0 1 6.5 4H8M16 4h1.5A2.5 2.5 0 0 1 20 6.5V8M20 16v1.5a2.5 2.5 0 0 1-2.5 2.5H16M8 20H6.5A2.5 2.5 0 0 1 4 17.5V16" />
      <path d="M9 10v.5M15 10v.5M12 10.5v2.5h-1M9.5 15.5c1.4 1.1 3.6 1.1 5 0" />
    </Svg>
  );
}

export function ShieldIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M12 3.25 19 6v5.5c0 4.3-2.9 7.7-7 9.25-4.1-1.55-7-4.95-7-9.25V6l7-2.75Z" />
      <path d="m9 12 2.1 2.1L15.2 10" />
    </Svg>
  );
}

export function LockIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <rect x="5" y="10.5" width="14" height="9.5" rx="2.2" />
      <path d="M8.25 10.5V8a3.75 3.75 0 0 1 7.5 0v2.5" />
    </Svg>
  );
}

export function SunIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <circle cx="12" cy="12" r="3.75" />
      <path d="M12 3v1.75M12 19.25V21M3 12h1.75M19.25 12H21M5.6 5.6l1.25 1.25M17.15 17.15l1.25 1.25M5.6 18.4l1.25-1.25M17.15 6.85l1.25-1.25" />
    </Svg>
  );
}

export function SparkOffIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M12 4.5 13.6 10.4 19.5 12l-5.9 1.6L12 19.5l-1.6-5.9L4.5 12l5.9-1.6L12 4.5Z" />
      <path d="M4 4l16 16" />
    </Svg>
  );
}

export function CornersIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M4 9V6a2 2 0 0 1 2-2h3M15 4h3a2 2 0 0 1 2 2v3M20 15v3a2 2 0 0 1-2 2h-3M9 20H6a2 2 0 0 1-2-2v-3" />
    </Svg>
  );
}

export function BarcodeIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M4 6v12M7 6v12M10.5 6v12M13 6v12M16.5 6v12M20 6v12" />
    </Svg>
  );
}

export function GlassesIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <circle cx="7" cy="13.5" r="3.25" />
      <circle cx="17" cy="13.5" r="3.25" />
      <path d="M10.25 13.25c1.1-.8 2.4-.8 3.5 0M3.75 13l1-5M20.25 13l-1-5" />
    </Svg>
  );
}

export function PhoneIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <rect x="7" y="2.75" width="10" height="18.5" rx="2.5" />
      <path d="M11 18h2" />
    </Svg>
  );
}

export function AlertIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M12 4 21 19.5H3L12 4Z" />
      <path d="M12 10v4M12 16.75v.25" />
    </Svg>
  );
}

export function InfoIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <circle cx="12" cy="12" r="8.75" />
      <path d="M12 11v5M12 8v.25" />
    </Svg>
  );
}

export function LinkOffIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M9.5 14.5 8 16a3.5 3.5 0 0 1-5-5l2.5-2.5M14.5 9.5 16 8a3.5 3.5 0 0 1 5 5l-2.5 2.5M4 4l16 16" />
    </Svg>
  );
}

export function ClockIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <circle cx="12" cy="12" r="8.75" />
      <path d="M12 7.5V12l3 2" />
    </Svg>
  );
}

export function WifiOffIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M2.5 9a14 14 0 0 1 5-2.9M21.5 9a14 14 0 0 0-8.3-3.4M5.5 12.5a9 9 0 0 1 3.4-2M18.5 12.5a9 9 0 0 0-2.1-1.4M9 16a4.5 4.5 0 0 1 6 0M12 19.5v.25M3 3l18 18" />
    </Svg>
  );
}

export function CheckIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="m5 12.5 4.5 4.5L19 7.5" />
    </Svg>
  );
}

export function CloseIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M6 6l12 12M18 6 6 18" />
    </Svg>
  );
}

export function UploadIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M12 15.5V4.5M7.5 9 12 4.5 16.5 9M4.5 15v2.5A2.5 2.5 0 0 0 7 20h10a2.5 2.5 0 0 0 2.5-2.5V15" />
    </Svg>
  );
}
