import { tokens } from "../theme/tokens";

/**
 * Detects if the user has requested reduced motion at the OS or browser level.
 */
export const checkReducedMotion = () => {
  if (typeof window === "undefined" || !window.matchMedia) return false;
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
};

/**
 * Returns a standard Framer Motion Transition configuration.
 * Automatically switches to a simple linear fade if reduced motion is enabled.
 */
export const getTransition = (type, overrideDuration) => {
  const isReduced = checkReducedMotion();

  if (isReduced) {
    return {
      type: "tween",
      duration: 0.15,
      ease: "linear",
    };
  }

  if (type === "spring") {
    return tokens.motion.easing.spring;
  }

  const durationMs = tokens.motion.duration[type];
  const durationSec = (overrideDuration ?? durationMs) / 1000;

  let ease = tokens.motion.easing.standard;
  if (type === "component" || type === "micro") {
    ease = tokens.motion.easing.decelerate;
  }

  return {
    type: "tween",
    duration: durationSec,
    ease: ease,
  };
};

/**
 * Tactile scale response variants for clickable/hoverable items.
 */
export const hoverScale = () => {
  if (checkReducedMotion()) {
    return { opacity: 0.95 };
  }
  return {
    scale: 1.02,
    transition: { type: "spring", stiffness: 400, damping: 15 },
  };
};

export const activePress = () => {
  if (checkReducedMotion()) {
    return { opacity: 0.85 };
  }
  return {
    scale: 0.98,
    transition: { type: "spring", stiffness: 500, damping: 15 },
  };
};

/**
 * Standardized Framer Motion Variants for components
 */

// Simple opacity fade
export const fadeInVariants = {
  initial: { opacity: 0 },
  animate: {
    opacity: 1,
    transition: { duration: tokens.motion.duration.micro / 1000, ease: "linear" },
  },
  exit: {
    opacity: 0,
    transition: { duration: tokens.motion.duration.micro / 1000, ease: "linear" },
  },
};

// Slide-up with fade
export const slideUpVariants = {
  initial: {
    opacity: 0,
    y: checkReducedMotion() ? 0 : 16,
  },
  animate: {
    opacity: 1,
    y: 0,
    transition: getTransition("component"),
  },
  exit: {
    opacity: 0,
    y: checkReducedMotion() ? 0 : 12,
    transition: getTransition("micro"),
  },
};

// Slide-in from right with fade (useful for drawers / panels)
export const slideInRightVariants = {
  initial: {
    opacity: 0,
    x: checkReducedMotion() ? 0 : 32,
  },
  animate: {
    opacity: 1,
    x: 0,
    transition: getTransition("component"),
  },
  exit: {
    opacity: 0,
    x: checkReducedMotion() ? 0 : 32,
    transition: getTransition("micro"),
  },
};

// Scale-in with fade (useful for modals / popovers)
export const scaleInVariants = {
  initial: {
    opacity: 0,
    scale: checkReducedMotion() ? 1 : 0.95,
  },
  animate: {
    opacity: 1,
    scale: 1,
    transition: getTransition("spring"),
  },
  exit: {
    opacity: 0,
    scale: checkReducedMotion() ? 1 : 0.95,
    transition: { duration: tokens.motion.duration.micro / 1000 },
  },
};

// Stagger child elements progressively
export const staggerContainerVariants = {
  initial: {},
  animate: {
    transition: {
      staggerChildren: checkReducedMotion() ? 0 : 0.05,
    },
  },
};

export const staggerChildVariants = {
  initial: { opacity: 0, y: checkReducedMotion() ? 0 : 10 },
  animate: {
    opacity: 1,
    y: 0,
    transition: { duration: tokens.motion.duration.component / 1000, ease: "easeOut" },
  },
};
