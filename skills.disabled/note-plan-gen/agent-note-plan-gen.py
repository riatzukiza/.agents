#!/usr/bin/env python3
"""note-plan-gen: convert syllable/stress/beat lyrics into a rubberband-compatible notes.txt"""
import argparse, csv, math, sys
from pathlib import Path

SCALES = {
    'major':      [0, 2, 4, 5, 7, 9, 11],
    'minor':      [0, 2, 3, 5, 7, 8, 10],
    'dorian':     [0, 2, 3, 5, 7, 9, 10],
    'mixolydian': [0, 2, 4, 5, 7, 9, 10],
    'pentatonic': [0, 2, 4, 7, 9],
    'blues':      [0, 3, 5, 6, 7, 10],
}

NOTE_NAMES = {'C':0,'D':2,'E':4,'F':5,'G':7,'A':9,'B':11}

def parse_key(k):
    k = k.strip()
    root = NOTE_NAMES.get(k[0].upper())
    if root is None: sys.exit(f'Unknown key root: {k[0]}')
    if len(k) > 1:
        if k[1] == '#': root += 1
        elif k[1] in ('b', chr(9837)): root -= 1
    return root % 12

def scale_midi(root, intervals, octave, degree):
    n = len(intervals)
    oct_shift, deg = divmod(degree, n)
    return 12 * (octave + 1) + root + intervals[deg] + 12 * oct_shift

CONTOURS = {
    'flat':    lambda i, n, hi: hi,
    'arch':    lambda i, n, hi: hi + round((min(i, n-1-i) / max(n//2,1)) * 2) if n > 1 else hi,
    'descend': lambda i, n, hi: hi - round((i / max(n-1,1)) * 2),
    'ascend':  lambda i, n, hi: hi + round((i / max(n-1,1)) * 2),
    'wave':    lambda i, n, hi: hi + round(math.sin(2*math.pi*i/max(n,1)) * 1.5),
}

STRESS_OFFSETS = {1: 2, 2: 1, 0: 0}

def main():
    p = argparse.ArgumentParser()
    p.add_argument('--lyrics',  required=True)
    p.add_argument('--key',     default='C')
    p.add_argument('--scale',   default='major', choices=list(SCALES))
    p.add_argument('--bpm',     type=float, default=90)
    p.add_argument('--octave',  type=int, default=4)
    p.add_argument('--output',  default='notes.txt')
    p.add_argument('--swing',   type=float, default=0.5)
    p.add_argument('--contour', default='arch', choices=list(CONTOURS))
    args = p.parse_args()

    root = parse_key(args.key)
    intervals = SCALES[args.scale]
    beat_sec = 60.0 / args.bpm
    contour_fn = CONTOURS[args.contour]
    swing = max(0.5, min(0.75, args.swing))

    syllables = []
    with open(args.lyrics) as f:
        for row in csv.reader(f, delimiter='\t'):
            if not row or row[0].strip().startswith('#'): continue
            if len(row) < 3: continue
            syllables.append((row[0].strip(), int(row[1].strip()), float(row[2].strip())))

    if not syllables: sys.exit('No syllable rows found')

    n = len(syllables)
    base_degree = 2
    rows = []
    t = 0.0
    for i, (syl, stress, beats) in enumerate(syllables):
        swing_factor = swing if i % 2 == 0 else (1 - swing)
        dur = beat_sec * beats * swing_factor * 2
        degree = max(0, contour_fn(i, n, base_degree) + STRESS_OFFSETS.get(stress, 0))
        midi = scale_midi(root, intervals, args.octave, degree)
        rows.append((t, t + dur, midi, syl))
        t += dur

    out = Path(args.output)
    with out.open('w') as f:
        f.write('# start_sec\tend_sec\tmidi_note\t# syllable\n')
        for start, end, midi, syl in rows:
            f.write(f'{start:.3f}\t{end:.3f}\t{midi}\t# {syl}\n')

    print(f'Wrote {len(rows)} notes -> {out}')
    for s, e, m, sy in rows:
        print(f'  {s:.3f}-{e:.3f}  MIDI {m:3d}  {sy}')

if __name__ == '__main__': main()
