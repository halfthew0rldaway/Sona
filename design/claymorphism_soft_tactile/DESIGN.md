---
name: Claymorphism Soft-Tactile
colors:
  surface: '#faf9f6'
  surface-dim: '#dbdad7'
  surface-bright: '#faf9f6'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f4f3f1'
  surface-container: '#efeeeb'
  surface-container-high: '#e9e8e5'
  surface-container-highest: '#e3e2e0'
  on-surface: '#1a1c1a'
  on-surface-variant: '#4f453e'
  inverse-surface: '#2f312f'
  inverse-on-surface: '#f2f1ee'
  outline: '#81756d'
  outline-variant: '#d3c4ba'
  surface-tint: '#765842'
  primary: '#765842'
  on-primary: '#ffffff'
  primary-container: '#ffd6ba'
  on-primary-container: '#7a5b45'
  inverse-primary: '#e6bfa4'
  secondary: '#3a675a'
  on-secondary: '#ffffff'
  secondary-container: '#bceddc'
  on-secondary-container: '#406d60'
  tertiary: '#5a5c7c'
  on-tertiary: '#ffffff'
  tertiary-container: '#dbdbff'
  on-tertiary-container: '#5e5f7f'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#ffdcc4'
  primary-fixed-dim: '#e6bfa4'
  on-primary-fixed: '#2b1706'
  on-primary-fixed-variant: '#5c412c'
  secondary-fixed: '#bceddc'
  secondary-fixed-dim: '#a1d0c1'
  on-secondary-fixed: '#002019'
  on-secondary-fixed-variant: '#214e43'
  tertiary-fixed: '#e1e0ff'
  tertiary-fixed-dim: '#c3c3e9'
  on-tertiary-fixed: '#171935'
  on-tertiary-fixed-variant: '#434463'
  background: '#faf9f6'
  on-background: '#1a1c1a'
  surface-variant: '#e3e2e0'
typography:
  h1:
    fontFamily: Plus Jakarta Sans
    fontSize: 40px
    fontWeight: '700'
    lineHeight: '1.2'
    letterSpacing: -0.02em
  h2:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: '1.3'
  h3:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '600'
    lineHeight: '1.4'
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '500'
    lineHeight: '1.6'
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '500'
    lineHeight: '1.6'
  label-caps:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '700'
    lineHeight: '1.0'
    letterSpacing: 0.05em
  button:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '700'
    lineHeight: '1.0'
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 8px
  xs: 4px
  sm: 12px
  md: 24px
  lg: 48px
  xl: 80px
  gutter: 24px
  margin: 32px
---

## Brand & Style

This design system is built on the principles of **Claymorphism**, prioritizing a soft, approachable, and highly tactile user experience. The aesthetic mimics physical, matte clay surfaces that feel "squishy" yet structured. 

The personality is friendly and optimistic, targeting lifestyle, wellness, or educational platforms where a sense of calm and playfulness is desired. To achieve this, the system avoids all harshness—replacing sharp corners with generous radii and stark black strokes with soft, colored shadows. The emotional response is intended to be one of comfort and ease, utilizing a "puffy" depth model that invites interaction through perceived physical volume.

## Colors

The color strategy uses a curated selection of desaturated pastels set against a warm off-white background (`#FAF9F6`). 

- **Primary (Peach):** Used for main actions and highlights.
- **Secondary (Mint) & Tertiary (Lavender):** Used for categorical distinction and secondary accents.
- **Background:** Avoids pure white to reduce eye strain and maintain the organic clay feel.
- **Contrast:** While the palette is pastel, all text and functional iconography must use the high-contrast `text-high-contrast` shade to ensure WCAG accessibility. Colors should never be used as gradients; they are applied as solid, matte fills.

## Typography

This design system utilizes **Plus Jakarta Sans** for all levels of the hierarchy. Its slightly rounded terminals and open apertures perfectly complement the soft visual language of claymorphism.

Headlines should be set with tighter letter spacing and heavy weights to ground the playful UI. Body text maintains a medium weight (`500`) as the default to ensure legibility against pastel backgrounds, avoiding thin weights that might disappear into the soft shadows. All type is rendered in the deep neutral shade to maintain high contrast.

## Layout & Spacing

The layout follows a **fluid grid** model with generous whitespace to allow "inflated" elements room to breathe. 

A 12-column system is used for desktop, scaling down to 4 columns for mobile. Spacing rhythm is based on an 8px base unit. Because elements have large rounded corners and outer shadows, the internal padding of containers (cards, buttons) must be larger than standard flat designs to prevent content from feeling pinched at the corners. High-level sections should use the `lg` or `xl` spacing tokens to emphasize the airy, light nature of the design.

## Elevation & Depth

Depth is the defining characteristic of this design system. It is achieved through a combination of two specific shadow techniques:

1.  **Outer Shadows:** Soft, diffused shadows using a slightly darkened version of the background color or a very low-opacity neutral (e.g., `rgba(0,0,0,0.08)`). These should have a large blur radius (20px+) and a moderate offset to give the impression of the element floating off the surface.
2.  **Inner Highlights:** To create the "clay" effect, elements use a top-left inner shadow in a lighter tint (white or a lighter version of the fill color) and a bottom-right inner shadow that is slightly darker. This mimics a 3D light source hitting a matte, curved object.

No borders are used to define depth; the transition between the object and the background is handled entirely by these tonal shifts.

## Shapes

The shape language is consistently **Rounded**. Square corners are strictly forbidden.

- **Standard Containers:** Use `rounded-lg` (1rem / 16px) for cards and modals.
- **Interactive Elements:** Use `rounded-xl` (1.5rem / 24px) for buttons and input fields to emphasize the "squishy" tactile feel.
- **Small Elements:** Use `rounded` (0.5rem / 8px) for tags or tooltips.
- **Icons:** Should feature rounded caps and joins, avoiding any sharp points or 90-degree angles.

## Components

### Buttons
Buttons are rendered as thick, inflated capsules. They use the `rounded-xl` token and feature both the outer ambient shadow and the dual inner highlights. On hover, the inner highlights should intensify slightly to simulate the button being pressed.

### Cards
Cards serve as the primary content containers. They should use the off-white background or a very light pastel tint. Unlike buttons, their elevation is lower, using a more subtle outer shadow to keep the hierarchy clear.

### Input Fields
Inputs are slightly recessed or flat with a subtle inner shadow to indicate "emptiness" compared to the "fullness" of buttons. When focused, the highlight color changes to the primary peach or sky blue.

### Chips & Tags
These are small, highly rounded pill shapes. They use the tertiary colors (Lavender, Mint) to categorize information without overwhelming the primary actions.

### Checkboxes & Radios
These components are treated as "miniature clay buttons." When selected, they should appear more "inflated" than their unselected states, utilizing a vibrant pastel fill to indicate the active state.