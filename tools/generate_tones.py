"""Generate Cue's short, distinct notification tones as compact Ogg assets."""
from array import array
from math import exp, pi, sin
from pathlib import Path
import subprocess

NOTES = [
    (72, 76, 79), (74, 79, 83), (76, 79, 84), (67, 72, 76),
    (79, 76, 72), (72, 79, 84), (69, 74, 77), (76, 74, 69),
    (81, 84, 88), (72, 75, 79), (74, 78, 81), (77, 81, 84),
    (69, 72, 76), (67, 74, 79), (76, 81, 88), (72, 77, 81),
    (74, 72, 67), (79, 83, 86), (72, 79, 76, 84), (67, 72, 79, 84),
]
RATE = 22050
OUT = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"

for index, melody in enumerate(NOTES, 1):
    length = int(RATE * (0.31 * len(melody) + 0.40))
    pcm = array("h")
    for n in range(length):
        t = n / RATE
        value = 0.0
        for step, midi in enumerate(melody):
            local = t - step * 0.28
            if local < 0:
                continue
            frequency = 440 * 2 ** ((midi - 69) / 12)
            attack = min(1.0, local / 0.012)
            decay = exp(-local * (4.3 + index % 4 * 0.35))
            fundamental = sin(2 * pi * frequency * local)
            harmonic = sin(2 * pi * frequency * (2 if index % 3 else 3) * local)
            value += attack * decay * (fundamental + harmonic * (0.14 + index % 4 * 0.05))
        pcm.append(int(max(-1, min(1, value * 0.16)) * 32767))
    target = OUT / f"cue_tone_{index:02}.ogg"
    subprocess.run([
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "s16le",
        "-ar", str(RATE), "-ac", "1", "-i", "pipe:0", "-c:a", "libvorbis",
        "-q:a", "3", str(target),
    ], input=pcm.tobytes(), check=True)
