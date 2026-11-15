"""Generate original, quiet demo audio. Python standard library only."""
from pathlib import Path
import math
import random
import struct
import wave

out = Path(__file__).resolve().parent.parent / 'app/src/main/res/raw'
out.mkdir(parents=True, exist_ok=True)
rate, seconds = 22050, 16
rng = random.Random(817)
notes = [220.0, 261.6256, 329.6276, 293.6648, 261.6256, 196.0, 220.0, 164.8138]

def write(name, sample):
    with wave.open(str(out / name), 'wb') as audio:
        audio.setnchannels(1); audio.setsampwidth(2); audio.setframerate(rate)
        samples = bytearray()
        for i in range(rate * seconds):
            value = max(-1, min(1, sample(i / rate)))
            samples.extend(struct.pack('<h', int(value * 20000)))
        audio.writeframes(samples)

def melody(t):
    result = 0
    for index, frequency in enumerate(notes):
        age = t - index * 1.6
        if 0 <= age <= 4:
            envelope = (1 - math.exp(-age * 8)) * math.exp(-age * 1.25)
            result += envelope * (math.sin(2 * math.pi * frequency * age) + 0.15 * math.sin(4 * math.pi * frequency * age)) * 0.30
    return result * min(1, (seconds - t) / 1.5)

filtered = 0.0
def rain(t):
    global filtered
    filtered = 0.86 * filtered + 0.14 * rng.uniform(-1, 1)
    envelope = min(1, t / 1.5, (seconds - t) / 1.5)
    return filtered * envelope * (0.55 + 0.08 * math.sin(2 * math.pi * 0.25 * t))

write('melody.wav', melody)
write('rain.wav', rain)
print('Generated original melody.wav and rain.wav')
