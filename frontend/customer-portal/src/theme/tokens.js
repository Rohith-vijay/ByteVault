// ByteVault Media Design Tokens - Enterprise Developer Marketplace Single Source of Truth

export const tokens = {
  color: {
    // Surface & Backgrounds
    background: {
      default: "#F8FAFC", // Clean light slate for general discovery pages
      surface: "#FFFFFF", // Pure white for cards & interactive modules
      elevated: "#F1F5F9", // Slate-100 for sub-surfaces & badges
      washed: "#F5F3FF", // Soft purple tint
      darkNavy: "#070B16", // Primary dark canvas for Hero, Auth, Vault & Footer
      darkNavyAlt: "#0B1020", // Secondary dark canvas
      darkNavySurface: "#0F172A", // Dark card surface
      darkNavyBorder: "rgba(255, 255, 255, 0.08)",
      darkNavyBorderHover: "rgba(255, 255, 255, 0.18)",
    },
    // Text Levels
    text: {
      primary: "#0F172A", // Deep Oxford Slate for crisp readability
      secondary: "#334155", // Slate-700 for subtitles & descriptions
      muted: "#64748B", // Slate-500 for captions & metadata
      disabled: "#94A3B8",
      inverse: "#FFFFFF",
      inverseMuted: "#94A3B8",
      code: "#C7D2FE",
    },
    // Borders
    border: {
      default: "#E2E8F0", // Slate-200
      strong: "#CBD5E1", // Slate-300
      purple: "#E9D5FF",
      blue: "#BFDBFE",
      dark: "rgba(255, 255, 255, 0.1)",
      darkStrong: "rgba(255, 255, 255, 0.2)",
    },
    // Primary Brand (Royal Purple)
    primary: {
      main: "#7C3AED",
      hover: "#6D28D9",
      active: "#5B21B6",
      light: "#8B5CF6",
      soft: "#F5F3FF",
      border: "#DDD6FE",
      glow: "rgba(124, 58, 237, 0.18)",
    },
    // Accent (Electric & Royal Blue)
    accent: {
      main: "#2563EB",
      hover: "#1D4ED8",
      active: "#1E40AF",
      light: "#3B82F6",
      soft: "#EFF6FF",
      border: "#BFDBFE",
      glow: "rgba(37, 99, 235, 0.18)",
    },
    // Status Colors
    status: {
      success: "#10B981",
      successSoft: "#ECFDF5",
      warning: "#F59E0B",
      warningSoft: "#FFFBEB",
      error: "#EF4444",
      errorSoft: "#FEF2F2",
      info: "#0284C7",
      infoSoft: "#F0F9FF",
    },
  },
  typography: {
    fontFamily: {
      primary: '"Plus Jakarta Sans", "Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      mono: '"JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
    },
    weight: {
      light: 300,
      regular: 400,
      medium: 500,
      semibold: 600,
      bold: 700,
      extrabold: 800,
    },
    sizes: {
      display: {
        desktop: "52px",
        tablet: "42px",
        mobile: "32px",
        lineHeight: 1.1,
        letterSpacing: "-0.025em",
      },
      h1: {
        desktop: "38px",
        tablet: "32px",
        mobile: "28px",
        lineHeight: 1.18,
        letterSpacing: "-0.02em",
      },
      h2: {
        desktop: "28px",
        tablet: "24px",
        mobile: "22px",
        lineHeight: 1.25,
        letterSpacing: "-0.015em",
      },
      h3: {
        desktop: "22px",
        tablet: "20px",
        mobile: "18px",
        lineHeight: 1.3,
        letterSpacing: "-0.01em",
      },
      h4: {
        desktop: "18px",
        tablet: "17px",
        mobile: "16px",
        lineHeight: 1.35,
        letterSpacing: "-0.005em",
      },
      bodyLarge: {
        desktop: "17px",
        tablet: "16px",
        mobile: "15px",
        lineHeight: 1.6,
        letterSpacing: "-0.005em",
      },
      body: {
        desktop: "15px",
        tablet: "15px",
        mobile: "14px",
        lineHeight: 1.55,
        letterSpacing: "0",
      },
      bodySmall: {
        desktop: "13px",
        tablet: "13px",
        mobile: "12px",
        lineHeight: 1.5,
        letterSpacing: "0",
      },
      caption: {
        desktop: "12px",
        tablet: "12px",
        mobile: "11px",
        lineHeight: 1.4,
        letterSpacing: "0.01em",
      },
      label: {
        desktop: "11px",
        tablet: "11px",
        mobile: "10px",
        lineHeight: 1.4,
        letterSpacing: "0.06em",
      },
      price: {
        desktop: "22px",
        tablet: "20px",
        mobile: "18px",
        lineHeight: 1.2,
        letterSpacing: "-0.015em",
      },
      productTitle: {
        desktop: "16px",
        tablet: "15px",
        mobile: "15px",
        lineHeight: 1.4,
        letterSpacing: "-0.005em",
      },
    },
  },
  spacing: {
    xxs: "4px",
    xs: "8px",
    sm: "12px",
    md: "16px",
    lg: "24px",
    xl: "32px",
    xxl: "48px",
    "3xl": "64px",
    "4xl": "96px",
    "5xl": "128px",
  },
  radius: {
    xs: "4px",
    sm: "8px",
    md: "12px",
    lg: "16px",
    xl: "20px",
    xxl: "24px",
    full: "999px",
  },
  elevation: {
    none: "none",
    subtle: "0 1px 3px rgba(15, 23, 42, 0.04), 0 1px 2px rgba(15, 23, 42, 0.02)",
    card: "0 4px 6px -1px rgba(15, 23, 42, 0.05), 0 2px 4px -2px rgba(15, 23, 42, 0.03)",
    hover: "0 14px 28px -4px rgba(15, 23, 42, 0.09), 0 6px 12px -2px rgba(15, 23, 42, 0.04)",
    popover: "0 20px 40px -8px rgba(15, 23, 42, 0.12), 0 8px 16px -4px rgba(15, 23, 42, 0.06)",
    modal: "0 25px 50px -12px rgba(15, 23, 42, 0.25)",
    darkCard: "0 4px 20px rgba(0, 0, 0, 0.4)",
    darkHover: "0 12px 30px rgba(0, 0, 0, 0.6), 0 0 20px rgba(124, 58, 237, 0.15)",
  },
  zIndex: {
    base: 0,
    sticky: 100,
    dropdown: 200,
    header: 1000,
    drawer: 1100,
    modal: 1200,
    toast: 1300,
    tooltip: 1400,
  },
  motion: {
    duration: {
      micro: 120,
      short: 180,
      component: 240,
      page: 320,
      deliberate: 450,
    },
    easing: {
      standard: "cubic-bezier(0.16, 1, 0.3, 1)",
      smooth: "cubic-bezier(0.4, 0, 0.2, 1)",
      spring: {
        type: "spring",
        stiffness: 380,
        damping: 28,
      },
    },
  },
  iconSize: {
    xs: 16,
    sm: 20,
    md: 24,
    lg: 32,
    xl: 48,
  },
  aspectRatio: {
    product: "4/3",
    cardImage: "16/10",
    thumbnail: "1/1",
    hero: "16/9",
    digitalPreview: "16/10",
  },
  breakpoints: {
    xs: 0,
    sm: 640,
    md: 768,
    lg: 1024,
    xl: 1280,
    xxl: 1440,
  },
  containerWidth: {
    sm: "640px",
    md: "768px",
    lg: "1024px",
    xl: "1280px",
    xxl: "1400px",
  },
};

export default tokens;
