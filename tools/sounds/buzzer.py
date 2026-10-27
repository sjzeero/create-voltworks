#!/usr/bin/env python3
"""Synthesizes assets/voltworks/sounds/buzzer.ogg: a DC piezo/electromagnetic buzzer tone.

A real DC buzzer is driven by a self-oscillating circuit, so its diaphragm is pushed
with a roughly rectangular waveform at ~2.7 kHz. The result is a harsh tone with
strong odd harmonics, plus a coarse "buzz" from the interrupter / diaphragm rattle.

Model used here (all frequencies complete an integer number of periods in the
chunk, so the chunk is seamlessly loopable):
  * carrier 2700 Hz, asymmetric pulse (duty ~40 %) built additively from its
    Fourier series up to 15 kHz (band limited, no aliasing),
  * amplitude modulation by a rough 300 Hz rattle (~25 % depth, itself with harmonics),
  * a little diaphragm resonance (5400 Hz partial),
  * soft clipping (tanh) for the harsh, overdriven edge,
  * very light filtered noise, 4 ms fades at both ends (no clicks when the chunk
    is re-triggered by the game).

Requires numpy and ffmpeg (libvorbis). Run from the repo root:
    python3 tools/sounds/buzzer.py
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
DURATION = 0.5          # 22050 samples; 2700*0.5 and 300*0.5 are integers -> loopable
F0 = 2700.0
RATTLE = 300.0
DUTY = 0.4
OUT = os.path.join("src", "main", "resources", "assets", "voltworks", "sounds", "buzzer.ogg")


def main():
    n = int(SR * DURATION)
    t = np.arange(n) / SR
    rng = np.random.default_rng(1591)

    # Band-limited rectangular pulse with duty cycle DUTY.
    tone = np.zeros(n)
    k = 1
    while F0 * k < 15000:
        amp = np.sin(np.pi * k * DUTY) / k
        tone += amp * np.cos(2 * np.pi * F0 * k * t)
        k += 1
    tone /= np.max(np.abs(tone))

    # Diaphragm resonance partial.
    tone += 0.18 * np.sin(2 * np.pi * 2 * F0 * t + 0.7)

    # Rough interrupter rattle: 300 Hz with a few harmonics.
    rattle = (np.sin(2 * np.pi * RATTLE * t)
              + 0.5 * np.sin(2 * np.pi * 2 * RATTLE * t + 1.1)
              + 0.3 * np.sin(2 * np.pi * 3 * RATTLE * t + 2.3))
    rattle /= np.max(np.abs(rattle))
    sig = tone * (1.0 + 0.25 * rattle)

    # Light high-passed noise (hiss of the vibrating plate).
    noise = rng.standard_normal(n)
    noise = noise - np.convolve(noise, np.ones(8) / 8, mode="same")
    sig += 0.03 * noise

    # Harsh soft clip.
    sig = np.tanh(1.8 * sig)
    sig -= sig.mean()  # remove DC from the asymmetric pulse
    sig /= np.max(np.abs(sig))
    sig *= 0.85

    # Short fades so re-triggered chunks do not click.
    fade = int(0.004 * SR)
    ramp = np.linspace(0.0, 1.0, fade)
    sig[:fade] *= ramp
    sig[-fade:] *= ramp[::-1]

    pcm = (sig * 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "buzzer.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        os.makedirs(os.path.dirname(OUT), exist_ok=True)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-ac", "1", "-ar", str(SR),
                        "-c:a", "libvorbis", "-q:a", "6", OUT], check=True)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
