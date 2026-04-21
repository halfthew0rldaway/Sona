# Design System Specification: The Precision Sonic Gallery

## 1. Overview & Creative North Star
**Creative North Star: The Digital Audiophile’s Monolith**

This design system is not a mere interface; it is a digital instrument designed to mirror the tactile, weighted experience of high-end hifi hardware. We are moving away from the "flat web" and toward a philosophy of **Tactile Sophistication**. Inspired by the industrial design of Chord and McIntosh, the system breaks the traditional grid through intentional asymmetry, allowing content to breathe within a "void" of deep charcoal. 

The aesthetic is defined by the contrast between a silent, dark background and the high-energy "glow" of technical precision. Every element should feel machined, polished, and purposeful.

---

## 2. Colors & Surface Philosophy
The palette is rooted in the absence of light, using layers of black and charcoal to create physical depth without the use of artificial lines.

### The "No-Line" Rule
**Explicit Instruction:** Designers are prohibited from using 1px solid borders for sectioning or containment. Structural boundaries must be defined solely through:
*   **Background Shifts:** Transitioning from `surface` (#131313) to `surface_container_low` (#1C1B1B).
*   **Tonal Nesting:** A `surface_container_high` (#2A2A2A) element sitting on a `surface_container_low` base.

### Surface Hierarchy & Nesting
Treat the UI as a series of stacked, precision-cut materials. 
*   **Base Layer:** `surface_dim` (#131313) or `surface_container_lowest` (#0E0E0E) for the main canvas.
*   **The Component Layer:** Use `surface_container` (#201F1F) for cards or main content areas.
*   **The Elevated Layer:** Use `surface_container_highest` (#353534) for floating elements or active interactive zones.

### The "Glass & Gradient" Rule
To mimic the crystal-clear displays of luxury DACs (Digital-to-Analog Converters), use glassmorphism for overlays. 
*   **Glass Specs:** Apply a backdrop blur (20px–40px) to `surface_container` at 70% opacity.
*   **Signature Gradients:** Main CTAs should utilize a subtle linear gradient from `primary` (#C3F5FF) to `primary_container` (#00E5FF) at a 135-degree angle to provide a "lit-from-within" glow.

---

## 3. Typography
The typography strategy creates a tension between human-centric legibility (Inter) and machine-driven precision (JetBrains Mono/Space Grotesk).

*   **Editorial Authority:** Use `display-lg` and `display-md` (Inter) for product names and hero messaging. The tight tracking and heavy weight should feel like a physical brand mark on a metal chassis.
*   **Technical Integrity:** Use `label-md` and `label-sm` (Space Grotesk/Monospace) for bitrates, sample frequencies (e.g., "192kHz / 24-bit"), and technical specs. This font choice signals "data accuracy" to the user.
*   **Functional Clarity:** `body-md` (Inter) is reserved for descriptions and settings, ensuring high readability against the dark backgrounds using `on_surface_variant`.

---

## 4. Elevation & Depth
In this system, depth is a function of light and material, not drop shadows.

*   **The Layering Principle:** Depth is achieved by "stacking" tonal tiers. For example, a `surface_container_highest` tooltip appearing over a `surface_container` card creates a soft, natural lift.
*   **Ambient Shadows:** When a "floating" effect is mandatory (e.g., a modal), use an extra-diffused shadow: `Blur: 60px, Spread: -10px, Color: #000000 at 40%`. This simulates a heavy object resting near a surface rather than a flat paper shadow.
*   **The "Ghost Border" Fallback:** For high-density technical layouts where separation is critical, use a "Ghost Border." Apply the `outline_variant` (#3B494C) at **15% opacity**. It should be felt, not seen.
*   **Active States:** Use the `surface_tint` (#00DAF3) as a subtle outer glow (0px 0px 12px) for active knobs or buttons to signify they are "powered on."

---

## 5. Components

### Buttons (The "Pill" Aesthetic)
*   **Primary:** Full pill-shape (`rounded-full`). Background uses the `primary_container` (#00E5FF) gradient. Text: `on_primary` (#00363D), Bold.
*   **Secondary:** Pill-shape. Background: `surface_container_highest`. Border: Ghost Border (15% `outline_variant`).
*   **Precision Toggle:** For technical switches, use the `secondary` Gold (#E9C349) for the 'On' state to mimic brass hardware components.

### Cards & Lists
*   **Rule:** No dividers. Use `spacing-lg` (vertical white space) or a background shift to `surface_container_low` to separate items. 
*   **Hover State:** Cards should subtly transition from `surface_container` to `surface_container_high` on hover, accompanied by a 2px scale-up to feel "magnetized."

### The "Verified Bit-Perfect" Badge
*   **Style:** A small, pill-shaped badge using `secondary_container` (#AF8D11) with `on_secondary` (#3C2F00) text. 
*   **Detail:** Include a 1px `secondary` (#E9C349) "Ghost Border" at 30% opacity to make it feel like a gold-plated certification seal.

### Input Fields
*   **Logic:** Inputs should look like recessed slots in a metal chassis. Use `surface_container_lowest` (#0E0E0E) with a subtle inner shadow. 
*   **Focus:** On focus, the `outline` (#849396) should transition to `primary` (#C3F5FF) with a soft outer glow.

---

## 6. Do's and Don'ts

### Do:
*   **Do** embrace asymmetry. Align technical stats to the right while headlines remain left-aligned to create a "technical manual" feel.
*   **Do** use "Optical Centering." Because of the heavy weighting of the typography, visual balance is more important than mathematical centering.
*   **Do** use the `primary_fixed_dim` (#00DAF3) for interactive icons to ensure they "pop" against the charcoal.

### Don't:
*   **Don't** use pure white (#FFFFFF) for body text. Use `on_surface` (#E5E2E1) to reduce eye strain in the dark mode environment.
*   **Don't** use sharp corners. Use the `DEFAULT` (1rem) or `md` (1.5rem) roundedness to maintain the "machined" feel.
*   **Don't** use standard blue for links. Use `secondary` (Gold) or `primary` (Electric Blue) to maintain the luxury audio aesthetic.
*   **Don't** use more than one "Glass" element per view. Too much transparency breaks the illusion of solid, high-end hardware.