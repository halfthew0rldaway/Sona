# UI/UX Design Prompt: High-Fidelity USB Audio Player (Project: uapp_wannabe)

## 1. Project Overview
The "uapp_wannabe" project is a High-Fidelity (Hi-Fi) USB Audio Player for Android. Its primary purpose is to bypass the standard Android audio processing stack (which often resamples or alters audio) to deliver "bit-perfect" audio directly to an external USB DAC (Digital-to-Analog Converter).

## 2. The Objective
Design a premium, professional, and modern mobile UI/UX that caters to **audiophiles**. The interface should emphasize technical transparency (showing exactly what is happening to the audio) while maintaining a sleek, high-end "luxury audio gear" aesthetic.

## 3. Key Features & Functionality
- **Bit-Perfect Playback**: Custom USB driver integration to talk directly to hardware.
- **Audio Routing Control**: Intelligent switching between USB DAC and internal phone speakers.
- **Format Support**: Focused on high-quality PCM (WAV) playback in this stage.
- **Real-time Technical Stats**: Displaying sample rate, bit depth, and active backend (USB vs Android).
- **USB Device Management**: Automatic discovery and permission handling for external DACs.

## 4. User Flow
1. **Initial Launch**: User enters the app. If no file is selected, show a "Select Audio" state.
2. **Device Connection**: User plugs in a USB DAC. The app should provide immediate visual confirmation that a high-end device is detected.
3. **File Selection**: User chooses a WAV file from storage.
4. **Playback**: User hits Play. The UI shows the audio format (e.g., 96kHz / 24-bit) and confirms it is being sent to the DAC without modification.
5. **Route Management**: User can manually override the audio path (e.g., force Android system output even if a DAC is plugged in).

## 5. Screen-by-Screen Requirements

### A. The "Now Playing" Experience (Main Screen)
- **Visual Priority**: The technical status (Sample Rate, Bit Depth) is as important as the Album Art/Filename.
- **Playback Controls**: Play/Pause, Stop, and a progress bar (even if currently streaming).
- **"Audio Path" Indicator**: A prominent visual element showing the signal chain: 
  *   `Source File` -> `Bit-Perfect Backend` -> `[DAC Name]`
  *   Or `Source File` -> `System Native` -> `Phone Speakers`
- **Metadata**: Display the filename and technical properties of the WAV file clearly.

### B. File Browser / Library
- Minimalist list view focused on finding high-quality files.
- Show file properties (Sample Rate/Bit Depth) in the list if possible.

### C. Audio Configuration / Settings
- **Route Selection**: A clear choice between:
    - **Auto**: Best available route (USB preferred).
    - **USB Only**: Error if no DAC is found (Strict bit-perfect mode).
    - **System Only**: Force use of Android standard output.
- **USB Device Info**: Technical details of the connected DAC (Vendor, Product ID, Supported Formats).

## 6. Design Aesthetics (Style Guide)
- **Theme**: Dark Mode by default. Inspired by premium hi-fi brands (Chord, Astell&Kern, McIntosh).
- **Colors**: Deep charcoal/black backgrounds with vibrant accent colors for "Active" states (e.g., Electric Blue or Gold for the "Bit-Perfect" status).
- **Typography**: Clean, monospace or high-readability sans-serif (e.g., Inter, JetBrains Mono for stats) to give a "precision instrument" feel.
- **Elements**: Subtle gradients, "glassmorphism" for overlays, and buttery-smooth micro-animations for play/pause transitions and device connection.

## 7. Crucial "Audiophile" Requirements
- **No Hidden Processing**: The UI must never lie about the sample rate. If the DAC is playing 44.1kHz but the file is 48kHz, the UI should flag this mismatched/resampled state (though the engine aims for exact match).
- **Visual Confirmation**: Use a "Verified Bit-Perfect" badge or light when the system is successfully bypassing the Android mixer.
