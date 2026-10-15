// The companions as pixel art. A sprite is composed from a few shapes (an egg, a head, ears, a belly), shaded,
// given a face and arms for the frame, outlined, and decorated (sparkles, notes, a tear). An animation is a short
// list of such frames played at a stepped pace, like a sprite sheet. Pure functions with no imports, and only syntax
// Node can strip, so the Angular component and scripts/render-mascots.mjs (e-mail GIFs) draw the very same frames.

export type MascotType = 'owl' | 'rabbit' | 'cat';
export type AnimationName = 'idle' | 'happy' | 'dance' | 'wave' | 'cheer' | 'think' | 'sad';
export type Eyes = 'open' | 'blink' | 'happy' | 'sad' | 'up';
export type Mouth = 'smile' | 'open' | 'flat' | 'frown' | 'none';
export type Arm = 'down' | 'out' | 'up';
export type ExtraKind = 'sparkle' | 'note' | 'tear' | 'dots' | 'question' | 'confetti' | 'heart';

export interface Extra {
  kind: ExtraKind;
  x: number;
  y: number;
  /** A palette character, for confetti. */
  c?: string;
}

export interface Frame {
  eyes: Eyes;
  mouth: Mouth;
  armL: Arm;
  armR: Arm;
  dx: number;
  dy: number;
  /** How long this frame is shown. */
  ms: number;
  extras: Extra[];
}

export const MASCOT_TYPES: readonly MascotType[] = ['owl', 'rabbit', 'cat'];
export const MASCOT_LABEL: Record<MascotType, string> = { owl: 'Ollie the owl', rabbit: 'Remy the rabbit', cat: 'Cleo the cat' };
export const ANIMATION_NAMES: readonly AnimationName[] = ['idle', 'happy', 'dance', 'wave', 'cheer', 'think', 'sad'];

export const GRID_W = 32;
export const GRID_H = 32;
const OX = 4;
const OY = 7;
const EMPTY = ' ';

type Grid = string[][];

/** One colour per character. Shared characters first, then each mascot's own coat. */
const SHARED: Record<string, string> = {
  o: '#161616',
  e: '#ffffff',
  k: '#161616',
  y: '#f5a623',
  g: '#ffd23f',
  q: '#6ec6ff',
  r: '#ff4d4f',
  u: '#60a5fa',
  c: '#4ade80',
  v: '#a78bfa'
};

const COAT: Record<MascotType, Record<string, string>> = {
  owl: { b: '#a0693d', d: '#7a4a28', l: '#c58a56', w: '#f3e0bf', p: '#f08aa0', n: '#f5a623', s: '#7a4a28' },
  rabbit: { b: '#f2f0ec', d: '#cfcac2', l: '#ffffff', w: '#ffffff', p: '#f4a6b8', n: '#e86c8c', s: '#cfcac2' },
  cat: { b: '#e8a05e', d: '#c2792f', l: '#f6c28b', w: '#fbe6cf', p: '#f08aa0', n: '#e5748a', s: '#b4682c' }
};

export function paletteOf(type: MascotType): Record<string, string> {
  return { ...SHARED, ...COAT[type] };
}

// ---------------------------------------------------------------------------------------------- drawing helpers

interface Pen {
  g: Grid;
  ox: number;
  oy: number;
}

function blank(): Grid {
  return Array.from({ length: GRID_H }, () => Array.from({ length: GRID_W }, () => EMPTY));
}

function put(pen: Pen, x: number, y: number, c: string): void {
  const gx = Math.round(x) + pen.ox;
  const gy = Math.round(y) + pen.oy;
  if (gx >= 0 && gx < GRID_W && gy >= 0 && gy < GRID_H) {
    pen.g[gy][gx] = c;
  }
}

function get(pen: Pen, x: number, y: number): string {
  const gx = Math.round(x) + pen.ox;
  const gy = Math.round(y) + pen.oy;
  return gx >= 0 && gx < GRID_W && gy >= 0 && gy < GRID_H ? pen.g[gy][gx] : EMPTY;
}

function ellipse(pen: Pen, cx: number, cy: number, rx: number, ry: number, c: string, only?: string): void {
  for (let y = Math.floor(cy - ry); y <= Math.ceil(cy + ry); y++) {
    for (let x = Math.floor(cx - rx); x <= Math.ceil(cx + rx); x++) {
      if (((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1 && (only === undefined || get(pen, x, y) === only)) {
        put(pen, x, y, c);
      }
    }
  }
}

function dots(pen: Pen, points: ReadonlyArray<readonly [number, number]>, c: string): void {
  for (const [x, y] of points) {
    put(pen, x, y, c);
  }
}

/** The pixels of one side, mirrored about the sprite's centre line (x = 11.5). */
function mirror(points: ReadonlyArray<readonly [number, number]>): Array<[number, number]> {
  return points.map(([x, y]) => [23 - x, y]);
}

// ---------------------------------------------------------------------------------------------- the three bodies

interface Face {
  /** Where the eyes are: top-left pixel of the left eye, in sprite space. */
  eyeY: number;
  eyeLX: number;
  disc: boolean;
  mouthY: number;
  blush: ReadonlyArray<readonly [number, number]>;
  shoulderL: readonly [number, number];
  shoulderR: readonly [number, number];
}

const FACE: Record<MascotType, Face> = {
  owl: { eyeY: 9, eyeLX: 7, disc: true, mouthY: 14, blush: [], shoulderL: [3, 14], shoulderR: [20, 14] },
  rabbit: { eyeY: 9, eyeLX: 7, disc: false, mouthY: 13, blush: [[5, 12], [6, 12], [17, 12], [18, 12]], shoulderL: [4, 17], shoulderR: [19, 17] },
  cat: { eyeY: 10, eyeLX: 7, disc: false, mouthY: 14, blush: [[4, 13], [5, 13], [18, 13], [19, 13]], shoulderL: [4, 18], shoulderR: [19, 18] }
};

function drawBody(pen: Pen, type: MascotType): void {
  if (type === 'owl') {
    ellipse(pen, 11.5, 13.5, 9, 9.5, 'b');
    const tuft: Array<[number, number]> = [[4, 2], [3, 3], [4, 3], [5, 3], [3, 4], [4, 4], [5, 4], [6, 4], [4, 5], [5, 5]];
    dots(pen, tuft, 'b');
    dots(pen, mirror(tuft), 'b');
    ellipse(pen, 11.5, 17, 5.5, 5.5, 'w');
    dots(pen, [[9, 15], [11, 15], [13, 15], [10, 18], [12, 18], [9, 21], [13, 21]], 'd');
    // eye sockets: a darker ring round a white disc
    for (const cx of [7.5, 15.5]) {
      ellipse(pen, cx, 9.5, 4.4, 4.4, 'd', 'b');
      ellipse(pen, cx, 9.5, 3.3, 3.3, 'e');
    }
    dots(pen, [[11, 12], [12, 12], [11, 13], [12, 13]], 'n');
    dots(pen, [[7, 23], [8, 23], [9, 23], [14, 23], [15, 23], [16, 23]], 'y');
  } else if (type === 'rabbit') {
    ellipse(pen, 7, 1.5, 2.4, 6, 'b');
    ellipse(pen, 16, 1.5, 2.4, 6, 'b');
    ellipse(pen, 7, 2, 1, 4, 'p');
    ellipse(pen, 16, 2, 1, 4, 'p');
    ellipse(pen, 11.5, 17, 7.5, 6.5, 'b');
    ellipse(pen, 11.5, 10.5, 7.3, 6.2, 'b');
    ellipse(pen, 11.5, 19, 4.3, 4, 'w');
    dots(pen, [[11, 11], [12, 11]], 'n');
    dots(pen, [[7, 23], [8, 23], [9, 23], [14, 23], [15, 23], [16, 23]], 'w');
  } else {
    const tail: Array<[number, number]> = [[19, 21], [20, 21], [21, 20], [21, 19], [22, 18], [22, 17], [22, 16], [23, 16]];
    dots(pen, tail, 'b');
    ellipse(pen, 11.5, 18.5, 6.5, 5, 'b');
    ellipse(pen, 11.5, 19.5, 3.4, 3.4, 'w');
    const ear: Array<[number, number]> = [[3, 2], [3, 3], [4, 3], [3, 4], [4, 4], [5, 4], [3, 5], [4, 5], [5, 5], [6, 5]];
    dots(pen, ear, 'b');
    dots(pen, mirror(ear), 'b');
    ellipse(pen, 11.5, 11, 8.6, 6.6, 'b');
    dots(pen, [[4, 4], [4, 5]], 'p');
    dots(pen, mirror([[4, 4], [4, 5]]), 'p');
    dots(pen, [[11, 5], [12, 5], [11, 6], [12, 6], [8, 5], [8, 6], [15, 5], [15, 6]], 's');
    dots(pen, [[3, 11], [4, 11], [3, 13], [19, 11], [20, 11], [20, 13]], 's');
    ellipse(pen, 11.5, 13.5, 3.2, 1.9, 'w');
    dots(pen, [[11, 12], [12, 12]], 'n');
    dots(pen, [[8, 23], [9, 23], [14, 23], [15, 23]], 'w');
  }
}

/** A light edge up and to the left, a shadow down and to the right: enough to make it read as round. */
function shade(pen: Pen): void {
  const snapshot = pen.g.map((row) => row.slice());
  for (let y = 0; y < GRID_H; y++) {
    for (let x = 0; x < GRID_W; x++) {
      if (snapshot[y][x] !== 'b') {
        continue;
      }
      const downRight = snapshot[y + 1]?.[x + 1] ?? EMPTY;
      const upLeft = snapshot[y - 1]?.[x - 1] ?? EMPTY;
      if (downRight === EMPTY) {
        pen.g[y][x] = 'd';
      } else if (upLeft === EMPTY) {
        pen.g[y][x] = 'l';
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------- the face

function drawEyes(pen: Pen, type: MascotType, eyes: Eyes): void {
  const face = FACE[type];
  const lx = face.eyeLX;
  const rx = 23 - lx - 1;
  const y = face.eyeY;
  for (const x of [lx, rx]) {
    if (face.disc) {
      // owl: a 2 by 2 pupil in a white disc
      const px = x + (eyes === 'up' ? 1 : 0);
      const py = y + (eyes === 'up' ? 0 : 1);
      if (eyes === 'blink' || eyes === 'happy') {
        ellipse(pen, x + 0.5, y + 0.5, 3.3, 3.3, 'b');
        if (eyes === 'blink') {
          dots(pen, [[x - 1, y + 1], [x, y + 1], [x + 1, y + 1], [x + 2, y + 1]], 'k');
        } else {
          dots(pen, [[x - 1, y + 2], [x, y + 1], [x + 1, y + 1], [x + 2, y + 2]], 'k');
        }
      } else {
        dots(pen, [[px, py], [px + 1, py], [px, py + 1], [px + 1, py + 1]], 'k');
        dots(pen, [[px, py]], 'e');
      }
    } else if (eyes === 'blink') {
      dots(pen, [[x, y + 1], [x + 1, y + 1]], 'k');
    } else if (eyes === 'happy') {
      dots(pen, [[x - 1, y + 2], [x, y + 1], [x + 1, y + 1], [x + 2, y + 2]], 'k');
    } else {
      const top = eyes === 'up' ? y - 1 : y;
      dots(pen, [[x, top], [x + 1, top], [x, top + 1], [x + 1, top + 1], [x, top + 2], [x + 1, top + 2]], 'k');
      dots(pen, [[x, top]], 'e');
    }
  }
  if (eyes === 'sad') {
    // brows that slope up toward the middle
    dots(pen, [[lx - 1, y - 2], [lx, y - 2], [lx + 1, y - 3], [lx + 2, y - 3]], 'k');
    dots(pen, mirror([[lx - 1, y - 2], [lx, y - 2], [lx + 1, y - 3], [lx + 2, y - 3]]), 'k');
  }
}

function drawMouth(pen: Pen, type: MascotType, mouth: Mouth): void {
  if (mouth === 'none') {
    return;
  }
  const face = FACE[type];
  const my = face.mouthY;
  if (type === 'owl') {
    // an owl's mouth is its beak: open it for a smile, droop it for a frown
    if (mouth === 'open' || mouth === 'smile') {
      dots(pen, [[11, 14], [12, 14]], 'n');
    }
    if (mouth === 'frown') {
      dots(pen, [[11, 12], [12, 12]], 'd');
    }
    return;
  }
  switch (mouth) {
    case 'smile':
      dots(pen, [[10, my], [13, my], [11, my + 1], [12, my + 1]], 'k');
      break;
    case 'open':
      dots(pen, [[10, my], [11, my], [12, my], [13, my], [11, my + 1], [12, my + 1]], 'k');
      dots(pen, [[11, my + 1], [12, my + 1]], 'r');
      break;
    case 'frown':
      dots(pen, [[10, my + 1], [13, my + 1], [11, my], [12, my]], 'k');
      break;
    default:
      dots(pen, [[11, my], [12, my]], 'k');
  }
}

function drawBlush(pen: Pen, type: MascotType, on: boolean): void {
  if (on) {
    dots(pen, FACE[type].blush, 'p');
  }
}

function drawArm(pen: Pen, type: MascotType, side: -1 | 1, arm: Arm): void {
  const [sx, sy] = side === -1 ? FACE[type].shoulderL : FACE[type].shoulderR;
  const s = side;
  const px: Array<[number, number]> = [];
  switch (arm) {
    case 'down':
      for (let k = 0; k < 4; k++) {
        px.push([sx, sy + k], [sx + s, sy + k]);
      }
      break;
    case 'out':
      for (let k = 0; k < 5; k++) {
        px.push([sx + s * k, sy - k], [sx + s * (k + 1), sy - k]);
      }
      break;
    case 'up':
      for (let k = 0; k < 6; k++) {
        px.push([sx + s, sy - k], [sx + s * 2, sy - k]);
      }
      break;
  }
  const colour = 'b';
  for (const [x, y] of px) {
    put(pen, x, y, colour);
  }
  // No hands or fingers: the arms are plain little wings, so nothing can read as a gesture.
}

function outline(pen: Pen): void {
  const snapshot = pen.g.map((row) => row.slice());
  for (let y = 0; y < GRID_H; y++) {
    for (let x = 0; x < GRID_W; x++) {
      if (snapshot[y][x] !== EMPTY) {
        continue;
      }
      if (
        (snapshot[y - 1]?.[x] ?? EMPTY) !== EMPTY ||
        (snapshot[y + 1]?.[x] ?? EMPTY) !== EMPTY ||
        (snapshot[y]?.[x - 1] ?? EMPTY) !== EMPTY ||
        (snapshot[y]?.[x + 1] ?? EMPTY) !== EMPTY
      ) {
        pen.g[y][x] = 'o';
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------- extras

const GLYPH_QUESTION = ['.###.', '#...#', '....#', '..##.', '..#..', '.....', '..#..'];

function drawExtra(g: Grid, extra: Extra): void {
  const pen: Pen = { g, ox: 0, oy: 0 };
  const { x, y } = extra;
  switch (extra.kind) {
    case 'sparkle':
      dots(pen, [[x, y], [x - 1, y], [x + 1, y], [x, y - 1], [x, y + 1]], 'g');
      break;
    case 'heart':
      dots(pen, [[x, y], [x + 1, y], [x + 3, y], [x + 4, y], [x, y + 1], [x + 1, y + 1], [x + 2, y + 1], [x + 3, y + 1], [x + 4, y + 1], [x + 1, y + 2], [x + 2, y + 2], [x + 3, y + 2], [x + 2, y + 3]], 'r');
      break;
    case 'note':
      dots(pen, [[x + 2, y], [x + 2, y + 1], [x + 2, y + 2], [x + 2, y + 3], [x + 3, y], [x + 4, y + 1], [x, y + 4], [x + 1, y + 4], [x, y + 5], [x + 1, y + 5]], extra.c ?? 'u');
      break;
    case 'tear':
      dots(pen, [[x, y], [x, y + 1]], 'q');
      break;
    case 'dots':
      dots(pen, [[x, y], [x + 3, y], [x + 6, y]], 'k');
      break;
    case 'question':
      GLYPH_QUESTION.forEach((row, ry) => {
        [...row].forEach((ch, rx) => {
          if (ch === '#') {
            put(pen, x + rx, y + ry, 'k');
          }
        });
      });
      break;
    case 'confetti':
      dots(pen, [[x, y], [x, y + 1]], extra.c ?? 'r');
      break;
  }
}

// ---------------------------------------------------------------------------------------------- frames

function frame(partial: Partial<Frame>): Frame {
  return { eyes: 'open', mouth: 'smile', armL: 'down', armR: 'down', dx: 0, dy: 0, ms: 200, extras: [], ...partial };
}

const spark = (x: number, y: number): Extra => ({ kind: 'sparkle', x, y });
const note = (x: number, y: number, c: string): Extra => ({ kind: 'note', x, y, c });

export const ANIMATIONS: Record<AnimationName, Frame[]> = {
  // Breathing: a one-pixel bob, and a blink now and then.
  idle: [
    frame({ ms: 520 }),
    frame({ dy: 1, ms: 380 }),
    frame({ ms: 520 }),
    frame({ dy: 1, ms: 380 }),
    frame({ eyes: 'blink', ms: 140 }),
    frame({ dy: 1, ms: 380 })
  ],
  // Thumbs up, a grin, and a little jump with sparkles.
  happy: [
    frame({ eyes: 'happy', mouth: 'open', armL: 'out', armR: 'out', ms: 200, extras: [spark(5, 8), spark(29, 12)] }),
    frame({ eyes: 'happy', mouth: 'open', armL: 'out', armR: 'out', dy: -2, ms: 170, extras: [spark(4, 6), spark(30, 10)] }),
    frame({ eyes: 'happy', mouth: 'open', armL: 'out', armR: 'out', dy: -3, ms: 150, extras: [spark(5, 4)] }),
    frame({ eyes: 'happy', mouth: 'open', armL: 'out', armR: 'out', dy: -1, ms: 150, extras: [spark(29, 6), spark(5, 10)] }),
    frame({ eyes: 'happy', mouth: 'open', armL: 'out', armR: 'out', dy: 1, ms: 160, extras: [spark(4, 8), spark(30, 12)] }),
    frame({ eyes: 'happy', mouth: 'smile', armL: 'out', armR: 'out', ms: 260, extras: [spark(29, 10)] })
  ],
  // Arms swing, hips sway, notes float up.
  dance: [
    frame({ eyes: 'happy', mouth: 'open', armL: 'up', armR: 'down', dx: -1, ms: 160, extras: [note(26, 10, 'u')] }),
    frame({ eyes: 'happy', mouth: 'open', armL: 'out', armR: 'out', dy: -1, ms: 160, extras: [note(27, 7, 'r')] }),
    frame({ eyes: 'happy', mouth: 'open', armL: 'down', armR: 'up', dx: 1, ms: 160, extras: [note(2, 9, 'c')] }),
    frame({ eyes: 'happy', mouth: 'open', armL: 'out', armR: 'out', dy: -1, ms: 160, extras: [note(1, 5, 'v')] }),
    frame({ eyes: 'happy', mouth: 'open', armL: 'up', armR: 'up', dy: -2, ms: 160, extras: [note(26, 4, 'u')] }),
    frame({ eyes: 'happy', mouth: 'smile', armL: 'down', armR: 'up', dx: 1, ms: 160, extras: [note(2, 6, 'r')] }),
    frame({ eyes: 'happy', mouth: 'open', armL: 'up', armR: 'down', dx: -1, dy: 1, ms: 160, extras: [note(27, 9, 'c')] }),
    frame({ eyes: 'happy', mouth: 'open', armL: 'out', armR: 'out', ms: 160, extras: [note(1, 8, 'v')] })
  ],
  wave: [
    frame({ eyes: 'open', mouth: 'smile', armR: 'out', ms: 240 }),
    frame({ eyes: 'open', mouth: 'smile', armR: 'up', ms: 240 }),
    frame({ eyes: 'happy', mouth: 'open', armR: 'out', dy: -1, ms: 240 }),
    frame({ eyes: 'open', mouth: 'smile', armR: 'up', ms: 240 })
  ],
  // Both arms up, big jumps, confetti raining.
  cheer: [
    frame({ eyes: 'happy', mouth: 'open', armL: 'up', armR: 'up', ms: 130, extras: [{ kind: 'confetti', x: 4, y: 3, c: 'r' }, { kind: 'confetti', x: 27, y: 5, c: 'u' }, { kind: 'confetti', x: 15, y: 1, c: 'g' }] }),
    frame({ eyes: 'happy', mouth: 'open', armL: 'up', armR: 'up', dy: -3, ms: 130, extras: [{ kind: 'confetti', x: 5, y: 6, c: 'c' }, { kind: 'confetti', x: 26, y: 8, c: 'v' }, { kind: 'confetti', x: 12, y: 3, c: 'r' }, spark(28, 4)] }),
    frame({ eyes: 'happy', mouth: 'open', armL: 'up', armR: 'up', dy: -4, ms: 130, extras: [{ kind: 'confetti', x: 3, y: 10, c: 'g' }, { kind: 'confetti', x: 28, y: 12, c: 'r' }, { kind: 'confetti', x: 18, y: 6, c: 'u' }, spark(4, 5)] }),
    frame({ eyes: 'happy', mouth: 'open', armL: 'up', armR: 'up', dy: -2, ms: 130, extras: [{ kind: 'confetti', x: 6, y: 14, c: 'v' }, { kind: 'confetti', x: 25, y: 16, c: 'c' }, { kind: 'confetti', x: 14, y: 10, c: 'g' }] }),
    frame({ eyes: 'happy', mouth: 'smile', armL: 'out', armR: 'out', dy: 1, ms: 150, extras: [{ kind: 'confetti', x: 4, y: 19, c: 'r' }, { kind: 'confetti', x: 27, y: 21, c: 'g' }] })
  ],
  think: [
    frame({ eyes: 'up', mouth: 'flat', ms: 420, extras: [{ kind: 'dots', x: 21, y: 6 }] }),
    frame({ eyes: 'up', mouth: 'flat', dy: 1, ms: 420, extras: [{ kind: 'question', x: 25, y: 2 }] }),
    frame({ eyes: 'up', mouth: 'flat', ms: 420, extras: [{ kind: 'question', x: 25, y: 1 }] }),
    frame({ eyes: 'blink', mouth: 'flat', dy: 1, ms: 160, extras: [{ kind: 'dots', x: 21, y: 6 }] })
  ],
  sad: [
    frame({ eyes: 'sad', mouth: 'frown', ms: 380, extras: [{ kind: 'tear', x: 8, y: 20 }] }),
    frame({ eyes: 'sad', mouth: 'frown', dy: 1, ms: 380, extras: [{ kind: 'tear', x: 8, y: 22 }] }),
    frame({ eyes: 'sad', mouth: 'frown', ms: 380, extras: [{ kind: 'tear', x: 8, y: 24 }] }),
    frame({ eyes: 'sad', mouth: 'frown', dy: 1, ms: 380 })
  ]
};

/** The finished picture of one frame: every pixel a palette character or a space. */
export function renderGrid(type: MascotType, f: Frame): Grid {
  const pen: Pen = { g: blank(), ox: OX + f.dx, oy: OY + f.dy };
  drawBody(pen, type);
  shade(pen);
  drawBlush(pen, type, f.eyes === 'happy');
  drawEyes(pen, type, f.eyes);
  drawMouth(pen, type, f.mouth);
  drawArm(pen, type, -1, f.armL);
  drawArm(pen, type, 1, f.armR);
  outline(pen);
  for (const extra of f.extras) {
    drawExtra(pen.g, extra);
  }
  return pen.g;
}

/** Pixels grouped by colour as SVG path data: one path per colour keeps a frame to a few hundred bytes. */
export function gridToSvg(type: MascotType, grid: Grid, title: string): string {
  const palette = paletteOf(type);
  const byColour = new Map<string, string>();
  for (let y = 0; y < GRID_H; y++) {
    let x = 0;
    while (x < GRID_W) {
      const c = grid[y][x];
      if (c === EMPTY) {
        x++;
        continue;
      }
      let run = 1;
      while (x + run < GRID_W && grid[y][x + run] === c) {
        run++;
      }
      byColour.set(c, `${byColour.get(c) ?? ''}M${x} ${y}h${run}v1h-${run}z`);
      x += run;
    }
  }
  const paths = [...byColour.entries()].map(([c, d]) => `<path fill="${palette[c] ?? '#000'}" d="${d}"/>`).join('');
  return (
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${GRID_W} ${GRID_H}" shape-rendering="crispEdges" role="img" aria-label="${title}">` +
    paths +
    `</svg>`
  );
}

export function frameSvg(type: MascotType, name: AnimationName, index: number): string {
  const frames = ANIMATIONS[name];
  return gridToSvg(type, renderGrid(type, frames[index % frames.length]), MASCOT_LABEL[type]);
}
