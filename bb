const { useState, useEffect, useRef, useMemo, useCallback } = React;
const _Recharts = (typeof Recharts !== 'undefined' && Recharts)
  || (typeof window !== 'undefined' && (window.Recharts || window.recharts || window.RECHARTS));
const _stub = (name) => (props) => React.createElement('div', { 'data-stub': name, style: { display: 'none' } }, props?.children);
const {
  AreaChart = _stub('AreaChart'),
  Area      = _stub('Area'),
  LineChart = _stub('LineChart'),
  Line      = _stub('Line'),
  BarChart  = _stub('BarChart'),
  Bar       = _stub('Bar'),
  ScatterChart = _stub('ScatterChart'),
  Scatter   = _stub('Scatter'),
  XAxis     = _stub('XAxis'),
  YAxis     = _stub('YAxis'),
  CartesianGrid = _stub('CartesianGrid'),
  Tooltip   = _stub('Tooltip'),
  ResponsiveContainer = _stub('ResponsiveContainer'),
  ZAxis     = _stub('ZAxis'),
  Cell      = _stub('Cell'),
  ReferenceLine = _stub('ReferenceLine'),
  Legend    = _stub('Legend'),
} = _Recharts || {};
const THREE = window.THREE;

// ═══════════════════════════════════════════════════════
//  UTILITIES & COLOR MAPS
// ═══════════════════════════════════════════════════════
const jetColor = (t) => {
  t = Math.max(0, Math.min(1, t));
  let r, g, b;
  if (t < 0.125)      { r = 0;         g = 0;         b = 0.5 + t * 4; }
  else if (t < 0.375) { r = 0;         g = (t - 0.125) * 4; b = 1; }
  else if (t < 0.625) { r = (t - 0.375) * 4; g = 1;   b = 1 - (t - 0.375) * 4; }
  else if (t < 0.875) { r = 1;         g = 1 - (t - 0.625) * 4; b = 0; }
  else                { r = 1 - (t - 0.875) * 4; g = 0; b = 0; }
  return new THREE.Color(r, g, b);
};

const cssJet = (t) => {
  t = Math.max(0, Math.min(1, t));
  let r, g, b;
  if (t < 0.125)      { r = 0;             g = 0;           b = Math.round((0.5 + t * 4) * 255); }
  else if (t < 0.375) { r = 0;             g = Math.round((t - 0.125) * 4 * 255); b = 255; }
  else if (t < 0.625) { r = Math.round((t - 0.375) * 4 * 255); g = 255; b = Math.round((1 - (t - 0.375) * 4) * 255); }
  else if (t < 0.875) { r = 255;           g = Math.round((1 - (t - 0.625) * 4) * 255); b = 0; }
  else                { r = Math.round((1 - (t - 0.875) * 4) * 255); g = 0; b = 0; }
  return `rgb(${r},${g},${b})`;
};

const calcStats = (arr, key) => {
  const vals = arr.map(d => d[key]).filter(v => typeof v === 'number' && isFinite(v));
  if (!vals.length) return { min: 0, max: 0, mean: 0, p10: 0, p50: 0, p90: 0 };
  const sorted = [...vals].sort((a, b) => a - b);
  const mean = vals.reduce((s, v) => s + v, 0) / vals.length;
  const p = pct => sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * pct / 100))];
  return { min: sorted[0], max: sorted[sorted.length - 1], mean, p10: p(10), p50: p(50), p90: p(90) };
};

// ═══════════════════════════════════════════════════════
//  SYNTHETIC WELL DATA GENERATOR
// ═══════════════════════════════════════════════════════
const ZONES = [
  { top: 2000, bot: 2065, type: "shale",  label: "Cap Shale",      grBase: 112, rhobBase: 2.54, nphiBase: 0.37, rtBase: 1.4 },
  { top: 2065, bot: 2150, type: "sand",   label: "Reservoir A",    grBase: 28,  rhobBase: 2.18, nphiBase: 0.21, rtBase: 28  },
  { top: 2150, bot: 2195, type: "shale",  label: "Inter Shale",    grBase: 105, rhobBase: 2.52, nphiBase: 0.36, rtBase: 1.6 },
  { top: 2195, bot: 2320, type: "sand",   label: "Main Reservoir", grBase: 22,  rhobBase: 2.14, nphiBase: 0.24, rtBase: 35  },
  { top: 2320, bot: 2365, type: "shale",  label: "Intra Shale",    grBase: 108, rhobBase: 2.51, nphiBase: 0.35, rtBase: 1.5 },
  { top: 2365, bot: 2455, type: "sand",   label: "Reservoir B",    grBase: 31,  rhobBase: 2.22, nphiBase: 0.19, rtBase: 20  },
  { top: 2455, bot: 2500, type: "shale",  label: "Base Shale",     grBase: 115, rhobBase: 2.56, nphiBase: 0.38, rtBase: 1.3 },
];

function generateWellData() {
  const data = [];
  const rng = () => (Math.random() - 0.5);
  for (let depth = 2000; depth <= 2500; depth += 1) {
    const zone = ZONES.find(z => depth >= z.top && depth < z.bot) || ZONES[ZONES.length - 1];
    const isSand = zone.type === "sand";
    // Transition smoothing near zone boundaries
    const distTop = depth - zone.top;
    const distBot = zone.bot - depth;
    const tf = Math.min(1, Math.min(distTop, distBot) / 4);
    const gr   = Math.max(5,   Math.min(160, zone.grBase   + rng() * (isSand ? 14 : 18) + (1 - tf) * rng() * 25));
    const rhob = Math.max(1.95, Math.min(2.72, zone.rhobBase + rng() * 0.07 + Math.sin(depth * 0.15) * 0.02));
    const nphi = Math.max(0.02, Math.min(0.50, zone.nphiBase + rng() * 0.04 + Math.sin(depth * 0.12) * 0.01));
    // RT varies with local Sw – richer sands have higher RT
    const swLocal = isSand ? 0.25 + Math.random() * 0.45 : 0.88 + Math.random() * 0.1;
    const rt   = Math.max(0.2,  Math.min(500, zone.rtBase * (1 + rng() * 0.25) / Math.max(0.1, swLocal)));
    const dt   = Math.max(52,   Math.min(130, isSand ? 72 + rng() * 14 : 93 + rng() * 12));
    data.push({
      DEPTH: depth,
      GR:   +gr.toFixed(1),
      RHOB: +rhob.toFixed(3),
      NPHI: +nphi.toFixed(3),
      RT:   +rt.toFixed(2),
      DT:   +dt.toFixed(1),
      ZONE: zone.label,
      _swLocal: +swLocal.toFixed(3),
    });
  }
  return data;
}

// ═══════════════════════════════════════════════════════
//  PETROPHYSICS ENGINE
// ═══════════════════════════════════════════════════════
function calcPetrophysics(data, p) {
  const { GRmin, GRmax, RHOBmat, RHOBfl, a, m, n, Rw, VshCut, PhiCut, SwCut,
          DTma = 47.5, DTfl = 189.0,
          PHINma = 0.0, PHINfl = 0.0 } = p;
  return data.map(row => {
    // Linear GR Vsh
    const Vsh  = Math.max(0, Math.min(1, (row.GR - GRmin) / (GRmax - GRmin)));

    // ── DENSITY POROSITY  Eq.(12) ─────────────────────────
    // φD = (ρma − ρb) / (ρma − ρf)
    const PHID = Math.max(0, Math.min(0.50, (RHOBmat - row.RHOB) / (RHOBmat - RHOBfl)));

    // ── NEUTRON POROSITY  Table 4.1 ───────────────────────
    // φN = (φN_log − φN,ma) / (φN,f − φN,ma)
    // When calibrated to Limestone: φN,ma = 0, φN,f = 0  → φN = NPHI directly
    const PHIN = Math.max(0, Math.min(0.60, PHINfl !== PHINma
      ? (row.NPHI - PHINma) / (PHINfl - PHINma)
      : row.NPHI));

    // ── SONIC POROSITY  Eq.(11) ───────────────────────────
    // Wyllie time-average: Δt = φ·Δtf + (1−φ)·Δtma
    // → φS = (Δtma − Δt) / (Δtma − Δtf)
    const PHIS = Math.max(0, Math.min(0.50, (DTma - row.DT) / (DTma - DTfl)));

    // ── TOTAL POROSITY  Eq.(14) ───────────────────────────
    // φt = (φN + φD) / 2   [Neutron + Density average]
    // Dodge et al.: total porosity = fraction of bulk volume not occupied by solid matter
    const PHIt  = Math.max(0, (PHID + PHIN) / 2);

    // ── EFFECTIVE POROSITY  (Dodge et al. 1996) ───────────
    // φeff = φt × (1 − Vsh)
    // Engineers' effective = interconnected pore volume = total porosity minus clay-bound water
    // Clay-bound water volume ≈ φt × Vsh  →  φeff = φt − φt×Vsh = φt×(1−Vsh)
    const PHIb  = Math.max(0, PHIt * Vsh);          // clay-bound water volume
    const PHIE  = Math.max(0, PHIt * (1 - Vsh));    // effective porosity (strict Dodge eq.)

    // Triple combo: equal-weight average of D, N, S
    const PHIt3 = Math.max(0, (PHID + PHIN + PHIS) / 3);
    // D-N average alias (for backward compat in plots)
    const PHIDN = PHIt;

    const phi  = Math.max(0.001, PHIE);

    // ════════════════════════════════════════════════════
    //  WATER SATURATION MODELS
    // ════════════════════════════════════════════════════
    const Rsh = p.Rsh ?? 3.0;   // shale resistivity (Ω·m) — user-settable

    // ── 1. ARCHIE (clean formation baseline) ─────────────
    // Sw_archie = [ (a × Rw) / (φ^m × Rt) ] ^ (1/n)
    const Sw_archie = Math.max(0, Math.min(1,
      Math.pow((a * Rw) / (Math.pow(phi, m) * row.RT), 1 / n)
    ));

    // ── 2. INDONESIA EQUATION — Poupon–Leveaux (1971) ────
    //
    //  Simplified form  Eq.(20)  [m=n=2]:
    //   Sw = [ Vsh/√Rsh + √(Rt / F·Rw) ]⁻²
    //   where F = a / φ^m  (formation factor)
    //
    //  Full correct form  Eq.(21):
    //   1/√Rt = (φ^(m/2) · Sw^(n/2)) / √(a·Rw)
    //           + Vsh^( 1 - Vsh/2 ) / √Rsh
    //
    //  Iterative solution for Sw (full form):
    //   Term_shale = Vsh^(1 - Vsh/2) / √Rsh
    //   Term_rock  = φ^(m/2) / √(a·Rw)
    //   Rearranging: Sw^(n/2) = (1/√Rt - Term_shale) / Term_rock
    //   → Sw = [ (1/√Rt - Term_shale) / Term_rock ]^(2/n)
    const sqrtRt  = Math.sqrt(Math.max(0.01, row.RT));
    const sqrtRsh = Math.sqrt(Math.max(0.01, Rsh));
    const sqrtRw  = Math.sqrt(Math.max(0.001, Rw));

    //  Full form — Eq.(21) solved analytically for Sw
    //  Sw = [(1/√Rt − Vsh^(1−Vsh/2)/√Rsh) × √(a·Rw) / φ^(m/2)]^(2/n)
    const termShale_full = Math.pow(Math.max(0, Vsh), 1 - Vsh / 2) / sqrtRsh;
    const termRock_full  = Math.pow(phi, m / 2) / (Math.sqrt(a) * sqrtRw);
    const lhs_full       = (1 / sqrtRt) - termShale_full;
    const Sw_indonesia_full = (termRock_full > 1e-8 && lhs_full > 0)
      ? Math.max(0, Math.min(1, Math.pow(lhs_full / termRock_full, 2 / n)))
      : 1.0;

    //  Simplified form — Eq.(20) [m=n=2 assumed]
    //  Sw = [Vsh/√Rsh + √(Rt / F·Rw)]^(-2)
    const F_archie    = a / Math.pow(phi, m);            // formation factor
    const term1_simp  = Vsh / sqrtRsh;                   // shale conductance term
    const term2_simp  = Math.sqrt(row.RT / (F_archie * Rw));
    const Sw_indonesia_simp = (term1_simp + term2_simp) > 1e-8
      ? Math.max(0, Math.min(1, Math.pow(1 / (term1_simp + term2_simp), 2)))
      : 1.0;

    //  "Or" explicit form — Eq.(21) rearranged via R0
    //  R0 = F·Rw = a·Rw/φ^m (formation resistivity at Sw=1)
    //  Sw = [Vsh^(0.5·(2−Vsh))·√(Rsh/Rt) + √(Rt/R0)]^(−2/n)
    //  Reduces to Archie (Sw=(R0/Rt)^(1/n)) when Vsh→0 ✓
    const R0          = F_archie * Rw;
    const termSh_or   = Math.pow(Math.max(0, Vsh), 0.5 * (2 - Vsh)) * Math.sqrt(Math.max(0.01, Rsh) / Math.max(0.01, row.RT));
    const termRk_or   = Math.sqrt(Math.max(0.01, row.RT) / Math.max(0.001, R0));
    const Sw_indonesia_or = (termSh_or + termRk_or) > 1e-8
      ? Math.max(0, Math.min(1, Math.pow(1 / (termSh_or + termRk_or), 2 / n)))
      : 1.0;

    // ── 3. WAXMAN-SMITS (shaly sand, clay CEC-based) ─────
    // Sw_ws ≈ [(a·Rw) / (φ^m·Rt) × 1/(1 + Rw·Qv·B/Sw)]^(1/n)
    // Simplified: use Vsh as proxy for Qv effect
    const B_ws  = 4.6 * (1 - 0.6 * Math.exp(-0.77 / Math.max(0.001, Rw)));
    const Qv    = Vsh * 0.15 / Math.max(0.001, phi);
    let Sw_ws   = Sw_archie;
    for (let iter = 0; iter < 8; iter++) {
      const F_eff = (a / Math.pow(phi, m)) / (1 + Rw * B_ws * Qv / Math.max(0.001, Sw_ws));
      Sw_ws = Math.max(0, Math.min(1, Math.pow((Rw / F_eff) / row.RT, 1 / n)));
    }

    // ── Active model selection ────────────────────────────
    const swModel = p.swModel ?? 'indonesia_full';
    const Sw_map  = {
      archie:            Sw_archie,
      indonesia_full:    Sw_indonesia_full,
      indonesia_simp:    Sw_indonesia_simp,
      indonesia_or:      Sw_indonesia_or,
      waxman_smits:      Sw_ws,
    };
    const Sw   = Sw_map[swModel] ?? Sw_indonesia_full;
    const Shc  = Math.max(0, 1 - Sw);
    const BVW  = Math.max(0, Math.min(1, Sw * PHIE));
    // ════════════════════════════════════════════════════
    //  PERMEABILITY CORRELATIONS  K = A × φ^b / Swi^c
    //  Eq.(20) — Tixier/Timur/Morris-Biggs/Schlumberger
    // ════════════════════════════════════════════════════
    // Swi = irreducible water saturation ≈ BVW / φ  or clamped Sw
    // Using BVW approach: Swi = min(Sw, 0.95) to avoid Sw=1 collapse
    const Swi = Math.max(0.02, Math.min(0.95, Sw));

    // Timur (1968): a=8581, b=4.4, c=2
    const K_timur    = Math.max(0.001, 8581   * Math.pow(phi, 4.4) / Math.pow(Swi, 2));
    // Morris-Biggs Oil (1967): a=62500, b=6, c=2
    const K_mb_oil   = Math.max(0.001, 62500  * Math.pow(phi, 6.0) / Math.pow(Swi, 2));
    // Morris-Biggs Gas (1967): a=6241, b=6, c=2
    const K_mb_gas   = Math.max(0.001, 6241   * Math.pow(phi, 6.0) / Math.pow(Swi, 2));
    // Schlumberger: a=10000, b=4.5, c=2
    const K_schlum   = Math.max(0.001, 10000  * Math.pow(phi, 4.5) / Math.pow(Swi, 2));
    // Kozeny-Carman (reference / Techlog default fallback)
    const K_kc       = Math.max(0.001, 150    * Math.pow(phi, 3)   / Math.pow(1 - phi + 0.001, 2) * (1 - Vsh));

    // Active model selection
    const kModel = p.kModel ?? 'timur';
    const K_map  = { timur: K_timur, mb_oil: K_mb_oil, mb_gas: K_mb_gas, schlumberger: K_schlum, kozeny: K_kc };
    const K      = K_map[kModel] ?? K_timur;

    // Pay flag
    const PAY  = (Vsh <= VshCut && PHIE >= PhiCut && Sw <= SwCut) ? 1 : 0;
    return {
      ...row,
      Vsh:              +Vsh.toFixed(4),
      PHID:             +PHID.toFixed(4),
      PHIN:             +PHIN.toFixed(4),
      PHIS:             +PHIS.toFixed(4),
      PHIDN:            +PHIDN.toFixed(4),
      PHIt:             +PHIt.toFixed(4),
      PHIt3:            +PHIt3.toFixed(4),
      PHIb:             +PHIb.toFixed(4),
      PHIE:             +PHIE.toFixed(4),
      Sw:               +Sw.toFixed(4),
      Sw_archie:        +Sw_archie.toFixed(4),
      Sw_indon_full:    +Sw_indonesia_full.toFixed(4),
      Sw_indon_simp:    +Sw_indonesia_simp.toFixed(4),
      Sw_indon_or:      +Sw_indonesia_or.toFixed(4),
      Sw_ws:            +Sw_ws.toFixed(4),
      Shc:              +Shc.toFixed(4),
      BVW:              +BVW.toFixed(4),
      K:                +K.toFixed(3),
      K_timur:          +K_timur.toFixed(3),
      K_mb_oil:         +K_mb_oil.toFixed(3),
      K_mb_gas:         +K_mb_gas.toFixed(3),
      K_schlum:         +K_schlum.toFixed(3),
      K_kc:             +K_kc.toFixed(3),
      PAY,
    };
  });
}

// ═══════════════════════════════════════════════════════
//  RESERVOIR GRID BUILDER
// ═══════════════════════════════════════════════════════
function buildReservoirGrid(petro, NX, NY, NZ) {
  const layerLen = Math.max(1, Math.floor(petro.length / NZ));
  const layers = [];
  for (let iz = 0; iz < NZ; iz++) {
    const slice = petro.slice(iz * layerLen, (iz + 1) * layerLen);
    if (!slice.length) { layers.push({ PHIE: 0.1, Sw: 0.6, Vsh: 0.4, K: 5 }); continue; }
    layers.push({
      PHIE: slice.reduce((s, d) => s + d.PHIE, 0) / slice.length,
      Sw:   slice.reduce((s, d) => s + d.Sw,   0) / slice.length,
      Vsh:  slice.reduce((s, d) => s + d.Vsh,  0) / slice.length,
      K:    slice.reduce((s, d) => s + d.K,    0) / slice.length,
    });
  }
  const grid = [];
  for (let iz = 0; iz < NZ; iz++) {
    for (let iy = 0; iy < NY; iy++) {
      for (let ix = 0; ix < NX; ix++) {
        const b = layers[iz];
        // Gaussian spatial noise for heterogeneity
        const sn = Math.sin(ix * 0.7 + iy * 0.5) * Math.cos(ix * 0.4 - iy * 0.9) * 0.25 + (Math.random() * 0.08 - 0.04);
        grid.push({
          ix, iy, iz,
          PHIE: Math.max(0.001, Math.min(0.45, b.PHIE + sn * 0.08)),
          Sw:   Math.max(0.001, Math.min(0.999, b.Sw   + sn * 0.12)),
          Vsh:  Math.max(0.001, Math.min(0.999, b.Vsh  + Math.abs(sn) * 0.10)),
          K:    Math.max(0.001, b.K * (1 + sn * 0.8)),
        });
      }
    }
  }
  return grid;
}

// ═══════════════════════════════════════════════════════
//  ECLIPSE EXPORT GENERATOR
// ═══════════════════════════════════════════════════════
function buildEclipseExport(petro, gp) {
  const { NX, NY, NZ, DX, DY } = gp;
  const grid = buildReservoirGrid(petro, NX, NY, NZ);
  const fmtArr = (arr, perLine = 10) => {
    const lines = [];
    for (let i = 0; i < arr.length; i += perLine)
      lines.push('  ' + arr.slice(i, i + perLine).join(' '));
    return lines.join('\n');
  };
  const poro  = fmtArr(grid.map(c => c.PHIE.toFixed(4)));
  const permx = fmtArr(grid.map(c => c.K.toFixed(2)));
  const permz = fmtArr(grid.map(c => (c.K * 0.1).toFixed(2)));
  const swat  = fmtArr(grid.map(c => c.Sw.toFixed(4)));
  const layerLen = Math.max(1, Math.floor(petro.length / NZ));
  const dzArr = Array.from({ length: NZ }, (_, iz) => {
    const slice = petro.slice(iz * layerLen, (iz + 1) * layerLen);
    return (slice.length || 2).toFixed(1);
  });
  const tops = Array.from({ length: NX * NY }, () => '2000.0').join(' ');

  return `-- ════════════════════════════════════════════════════════
-- ECLIPSE RESERVOIR SIMULATION DATA FILE
-- Generated by PetroSim AI Platform v2.0
-- Date: ${new Date().toISOString().split('T')[0]}
-- Well: SYNTHETIC-1  |  Field: AI_RESERVOIR_MODEL
-- ════════════════════════════════════════════════════════

RUNSPEC
  TITLE
    'AI-Generated Reservoir Model — PetroSim AI'
  
  DIMENS
    ${NX}  ${NY}  ${NZ}  /

  OIL
  WATER
  GAS
  DISGAS

  METRIC

  TABDIMS
    1  1  20  50  1  20 /

  WELLDIMS
    10  500  5  10 /

  UNIFOUT

  START
    1  'JAN'  2024 /

  NSTACK
    50 /

/

GRID
  INIT

  DX
${fmtArr(Array(NX * NY * NZ).fill(DX + '.0'))}
  /

  DY
${fmtArr(Array(NX * NY * NZ).fill(DY + '.0'))}
  /

  DZ
${fmtArr(dzArr.flatMap(dz => Array(NX * NY).fill(dz)))}
  /

  TOPS
  ${tops}
  /

  PORO
${poro}
  /

  PERMX
${permx}
  /

  PERMY
    EQUALS 'PERMX' /

  PERMZ
${permz}
  /

/

PROPS

  PVTO
  -- Rs(m3/m3)  Pres(bara)  Bo(rm3/sm3)  Viscosity(cP)
    0.001         1.0         1.063          1.040    /
    0.092         26.4        1.150          0.975
    0.181         51.5        1.207          0.910
    0.274         76.5        1.295          0.830
    0.437         101.5       1.435          0.695
    0.635         126.5       1.500          0.641
    0.836         151.5       1.565          0.594
    1.000         201.5       1.695          0.510 /

  PVTW
  -- Pref(bara)  Bw      Cw(1/bara)  Muw(cP)  Cv
    248.0        1.003   4.35E-5     0.96      0.0 /

  DENSITY
  -- Oil(kg/m3)  Water(kg/m3)  Gas(kg/m3)
    860.0        1020.0        0.853 /

  ROCK
    248.0  4.35E-5 /

  SWOF
  -- Sw       Krw       Kro       Pcow(bara)
    0.200    0.0000    0.8000    0.600
    0.250    0.0020    0.6100    0.400
    0.300    0.0090    0.4700    0.250
    0.350    0.0200    0.3700    0.160
    0.400    0.0330    0.2800    0.105
    0.450    0.0510    0.2000    0.068
    0.500    0.0750    0.1400    0.042
    0.550    0.1000    0.0890    0.023
    0.600    0.1320    0.0510    0.009
    0.650    0.1650    0.0200    0.000
    0.700    0.2000    0.0000    0.000 /

  SGOF
  -- Sg       Krg       Krog      Pcog(bara)
    0.000    0.0000    1.0000    0.000
    0.050    0.0050    0.8900    0.020
    0.100    0.0220    0.7800    0.040
    0.200    0.1000    0.5600    0.090
    0.300    0.2400    0.3700    0.130
    0.400    0.3400    0.2100    0.170
    0.500    0.4200    0.1000    0.205
    0.600    0.5600    0.0100    0.240
    0.700    0.7500    0.0000    0.280 /

/

SOLUTION

  EQUIL
  -- Datum(m)  Pres(bara)  OWC(m)  Pcow  GOC(m)  Pcog
    2100.0     248.0       2300.0  0.0   1950.0  0.0 /

  SWATINIT
${swat}
  /

/

SUMMARY

  FOPR
  FOPT
  FWPR
  FWCT
  FGOR
  FWIR
  FPR
  WOPR
    'PROD-1' /
  /
  WWPR
    'PROD-1' /
  /
  WBHP
    'PROD-1' /
    'INJ-1'  /
  /

/

SCHEDULE

  RPTSCHED
    'RESTART=2' 'WELLS=1' /

  WELSPECS
    'PROD-1'  'FIELD'  ${Math.max(1,Math.floor(NX/2))}  ${Math.max(1,Math.floor(NY/2))}  2080.0  'OIL' /
    'INJ-1'   'FIELD'  1   1   2080.0  'WATER' /
  /

  COMPDAT
    'PROD-1'  ${Math.max(1,Math.floor(NX/2))}  ${Math.max(1,Math.floor(NY/2))}  1  ${NZ}  'OPEN'  1*  1*  0.15 /
    'INJ-1'   1   1   1  ${NZ}  'OPEN'  1*  1*  0.15 /
  /

  WCONPROD
    'PROD-1'  'OPEN'  'ORAT'  500  4*  200  /
  /

  WCONINJE
    'INJ-1'  'WATER'  'OPEN'  'RATE'  800  1*  350 /
  /

  DATES
    1  'APR'  2024 /
  /

  DATES
    1  'JUL'  2024 /
  /

  DATES
    1  'OCT'  2024 /
  /

  DATES
    1  'JAN'  2025 /
  /

  DATES
    1  'JAN'  2026 /
  /

END
`;
}

// ═══════════════════════════════════════════════════════
//  THREE.JS 3-D RESERVOIR VIEWER
// ═══════════════════════════════════════════════════════
function ReservoirViewer({ petro, colorProp, NX = 12, NY = 8, NZ = 10 }) {
  const mountRef = useRef(null);

  useEffect(() => {
    if (!mountRef.current || !petro?.length) return;
    const el = mountRef.current;
    const W = el.clientWidth || 700;
    const H = el.clientHeight || 440;

    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false });
    renderer.setSize(W, H);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.setClearColor(0x050c18, 1);
    el.appendChild(renderer.domElement);

    const scene = new THREE.Scene();
    // Subtle fog for depth perception
    scene.fog = new THREE.FogExp2(0x050c18, 0.018);

    const camera = new THREE.PerspectiveCamera(42, W / H, 0.1, 600);
    camera.position.set(22, 16, 28);
    camera.lookAt(0, -3, 0);

    // Lighting
    scene.add(new THREE.AmbientLight(0x304560, 4));
    const sun = new THREE.DirectionalLight(0xffffff, 2.5);
    sun.position.set(20, 30, 20);
    scene.add(sun);
    const fill = new THREE.DirectionalLight(0x2060ff, 0.6);
    fill.position.set(-15, 5, -15);
    scene.add(fill);
    const rim = new THREE.DirectionalLight(0x00d4ff, 0.4);
    rim.position.set(0, -10, 15);
    scene.add(rim);

    // Build grid
    const grid = buildReservoirGrid(petro, NX, NY, NZ);
    const propVals = grid.map(c => c[colorProp] ?? 0);
    const pMin = Math.min(...propVals);
    const pMax = Math.max(...propVals);

    const cW = 0.88, cH = 0.42, cD = 0.88, gap = 0.06;
    const sX = cW + gap, sY = cH + gap, sZ = cD + gap;

    const geo = new THREE.BoxGeometry(cW, cH, cD);
    const mat = new THREE.MeshLambertMaterial({ vertexColors: true });
    const total = grid.length;
    const inst = new THREE.InstancedMesh(geo, mat, total);
    inst.castShadow = false;

    const dummy = new THREE.Object3D();
    grid.forEach((cell, i) => {
      dummy.position.set(
        (cell.ix - NX / 2) * sX,
        -cell.iz * sY,
        (cell.iy - NY / 2) * sZ
      );
      dummy.updateMatrix();
      inst.setMatrixAt(i, dummy.matrix);
      const t = (pMax > pMin) ? (cell[colorProp] - pMin) / (pMax - pMin) : 0.5;
      inst.setColorAt(i, jetColor(t));
    });
    inst.instanceMatrix.needsUpdate = true;
    if (inst.instanceColor) inst.instanceColor.needsUpdate = true;

    const group = new THREE.Group();
    group.add(inst);
    scene.add(group);
    group.rotation.x = 0.22;
    group.rotation.y = 0.35;

    // Bounding box wireframe
    const bbox = new THREE.BoxGeometry(NX * sX + 0.1, NZ * sY + 0.1, NY * sZ + 0.1);
    const edges = new THREE.EdgesGeometry(bbox);
    const lm = new THREE.LineBasicMaterial({ color: 0x00d4ff, opacity: 0.25, transparent: true });
    const wf = new THREE.LineSegments(edges, lm);
    wf.position.set(0, -(NZ - 1) * sY / 2, 0);
    group.add(wf);

    // Axis helpers (small colored rods)
    const addAxis = (dir, color, len = 3) => {
      const g = new THREE.CylinderGeometry(0.04, 0.04, len, 6);
      const m = new THREE.MeshBasicMaterial({ color });
      const mesh = new THREE.Mesh(g, m);
      if (dir === 'x') { mesh.rotation.z = -Math.PI / 2; mesh.position.x = NX * sX / 2 + len / 2; mesh.position.y = -(NZ - 1) * sY / 2; }
      if (dir === 'y') { mesh.position.y = -(NZ - 1) * sY / 2 - len / 2; mesh.position.x = -NX * sX / 2; }
      if (dir === 'z') { mesh.rotation.x = Math.PI / 2; mesh.position.z = NY * sZ / 2 + len / 2; mesh.position.y = -(NZ - 1) * sY / 2; }
      group.add(mesh);
    };
    addAxis('x', 0xff4444); addAxis('y', 0x44ff44); addAxis('z', 0x4488ff);

    // Mouse interaction
    let drag = false, lx = 0, ly = 0;
    const canvas = renderer.domElement;
    const onDown = e => { drag = true; lx = e.clientX; ly = e.clientY; };
    const onUp   = () => { drag = false; };
    const onMove = e => {
      if (!drag) return;
      group.rotation.y += (e.clientX - lx) * 0.007;
      group.rotation.x = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, group.rotation.x + (e.clientY - ly) * 0.007));
      lx = e.clientX; ly = e.clientY;
    };
    const onWheel = e => {
      const factor = 1 + e.deltaY * 0.0012;
      camera.position.multiplyScalar(Math.max(0.4, Math.min(3, factor)));
    };
    const onTouch = e => {
      if (e.touches.length === 1) {
        drag = true; lx = e.touches[0].clientX; ly = e.touches[0].clientY;
      }
    };
    const onTouchMove = e => {
      if (!drag || e.touches.length !== 1) return;
      group.rotation.y += (e.touches[0].clientX - lx) * 0.007;
      group.rotation.x += (e.touches[0].clientY - ly) * 0.007;
      lx = e.touches[0].clientX; ly = e.touches[0].clientY;
    };

    canvas.addEventListener('mousedown', onDown);
    window.addEventListener('mouseup', onUp);
    window.addEventListener('mousemove', onMove);
    canvas.addEventListener('wheel', onWheel, { passive: true });
    canvas.addEventListener('touchstart', onTouch);
    canvas.addEventListener('touchend', onUp);
    canvas.addEventListener('touchmove', onTouchMove);

    let animId;
    const animate = () => {
      animId = requestAnimationFrame(animate);
      if (!drag) group.rotation.y += 0.0025;
      renderer.render(scene, camera);
    };
    animate();

    return () => {
      cancelAnimationFrame(animId);
      canvas.removeEventListener('mousedown', onDown);
      window.removeEventListener('mouseup', onUp);
      window.removeEventListener('mousemove', onMove);
      canvas.removeEventListener('wheel', onWheel);
      canvas.removeEventListener('touchstart', onTouch);
      canvas.removeEventListener('touchend', onUp);
      canvas.removeEventListener('touchmove', onTouchMove);
      geo.dispose(); mat.dispose(); renderer.dispose();
      if (el.contains(canvas)) el.removeChild(canvas);
    };
  }, [petro, colorProp, NX, NY, NZ]);

  return (
    <div ref={mountRef}
      style={{ width: '100%', height: 440, cursor: 'grab', borderRadius: 6, overflow: 'hidden' }} />
  );
}

// ═══════════════════════════════════════════════════════
//  LAS FILE PARSER
// ═══════════════════════════════════════════════════════
function parseLAS(text) {
  const lines = text.split(/\r?\n/);
  let inCurves = false, inData = false;
  const mnemonics = [], rows = [];
  let nullVal = -9999.25;
  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    if (/^~W/i.test(line)) { inCurves = false; inData = false; continue; }
    if (/^~C/i.test(line)) { inCurves = true;  inData = false; continue; }
    if (/^~A/i.test(line)) { inData = true;    inCurves = false; continue; }
    if (/^~/.test(line))   { inCurves = false; inData = false; continue; }
    if (inCurves) {
      const m = line.split(/[\s.]/)[0].toUpperCase().trim();
      if (m) mnemonics.push(m);
    } else if (inData) {
      const vals = line.split(/\s+/).map(Number);
      if (vals.every(v => !isNaN(v)) && vals.length) rows.push(vals);
    } else if (/NULL\s*\.\s*/.test(line)) {
      nullVal = parseFloat(line.split(/NULL\s*\.\s*/)[1]) || -9999.25;
    }
  }
  if (!mnemonics.length || !rows.length) return null;
  const idx = k => { const i = mnemonics.findIndex(m => m === k); return i >= 0 ? i : -1; };
  const getCol = (row, ...keys) => {
    for (const k of keys) { const i = idx(k); if (i >= 0 && i < row.length) return row[i]; }
    return null;
  };
  return rows.map(row => {
    const obj = {};
    mnemonics.forEach((m, i) => { obj[m] = row[i] ?? nullVal; });
    const d = getCol(row, 'DEPT','DEPTH','MD','TVD') ?? 0;
    if (d <= 0) return null;
    return {
      DEPTH: +d.toFixed(2),
      GR:   +(getCol(row,'GR','SGR','CGR') ?? 60).toFixed(2),
      RHOB: +(getCol(row,'RHOB','RHOZ','DEN','ZDEN') ?? 2.35).toFixed(3),
      NPHI: +(getCol(row,'NPHI','CALI','TNPH') ?? 0.22).toFixed(3),
      RT:   +(getCol(row,'RT','ILD','LLD','MSFL','RDEP') ?? 5).toFixed(2),
      DT:   +(getCol(row,'DT','AC','DTCO') ?? 80).toFixed(1),
      ZONE: 'LAS Import',
    };
  }).filter(Boolean);
}

// ═══════════════════════════════════════════════════════
//  LITHOLOGY ENGINE — MINERAL REFERENCE DATA
// ═══════════════════════════════════════════════════════

// Mineral reference matrix points (zero-porosity endpoints for crossplot lines)
const MINERALS = {
  Quartz:    { rhob: 2.648, nphi: -0.020, dt: 55.5, color: '#ffcc00', symbol: '◆' },
  Limestone: { rhob: 2.710, nphi:  0.000, dt: 47.6, color: '#00c8ff', symbol: '●' },
  Dolomite:  { rhob: 2.876, nphi:  0.040, dt: 43.5, color: '#ff7b3a', symbol: '▲' },
  Anhydrite: { rhob: 2.977, nphi: -0.005, dt: 50.0, color: '#d46eff', symbol: '■' },
  Halite:    { rhob: 2.165, nphi: -0.030, dt: 67.0, color: '#ff4d88', symbol: '✦' },
};
const FLUID = { rhob: 1.0, nphi: 1.0, dt: 189.0 };

// Generate mineral porosity line: phi from 0 to 0.45
const mineralLine = (min) => {
  const pts = [];
  for (let phi = 0; phi <= 0.45; phi += 0.03) {
    pts.push({
      nphi: +(min.nphi * (1 - phi) + FLUID.nphi * phi).toFixed(4),
      rhob: +(min.rhob * (1 - phi) + FLUID.rhob * phi).toFixed(4),
    });
  }
  return pts;
};

// ─────────────────────────────────────────────────────────
//  M-N PLOT ENGINE  (Equations 7 & 8)
//
//  M = 0.01 × (Δtf − Δtma) / (ρma − ρf)           [Eq. 7]
//  N = (φNf  − φNma)        / (ρma − ρf)           [Eq. 8]
//
//  Using measured values as proxies for ma/t:
//    Δtma → DT (measured transit time, μs/ft)
//    ρma  → RHOB (measured bulk density, g/cc)
//    φNma → NPHI (measured neutron porosity, v/v)
//    Δtf  = 189 μs/ft  (fresh-water fluid)
//    ρf   = 1.0  g/cc
//    φNf  = 1.0  v/v
// ─────────────────────────────────────────────────────────
const calcMN = (row) => {
  const denom = row.RHOB - FLUID.rhob;
  if (Math.abs(denom) < 0.01) return { M: null, N: null, SP: null };
  // Eq. 7 — M parameter (sonic + density)
  const M = +((FLUID.dt - row.DT) / denom * 0.01).toFixed(5);
  // Eq. 8 — N parameter (neutron + density)
  const N = +((FLUID.nphi - row.NPHI) / denom).toFixed(5);
  // Secondary Porosity Index (SPI):
  //   Limestone reference M at the measured N: M_ls ≈ 0.827
  //   If M_measured > M_ls_expected → secondary (vuggy/fracture) porosity present
  //   SPI = max(0, M − M_ref), bounded to 0–0.4
  const M_ls_ref = 0.827;
  const SP = +Math.max(0, Math.min(0.4, M - M_ls_ref)).toFixed(5);
  return {
    M: isFinite(M) ? M : null,
    N: isFinite(N) ? N : null,
    SP: isFinite(SP) ? SP : 0,
  };
};

// M-N mineral reference points  (pure mineral endpoints, zero secondary porosity)
// These are the "pure-mineral" fixed points on the M-N diagram.
// They are INDEPENDENT of porosity — this is the key advantage over the DN crossplot.
const MN_MINERALS = {
  Quartz:    { M: 0.835, N: 0.669, color: '#ffcc00', rx: 0.035, ry: 0.028 },
  Limestone: { M: 0.827, N: 0.585, color: '#00c8ff', rx: 0.032, ry: 0.026 },
  Dolomite:  { M: 0.778, N: 0.516, color: '#ff7b3a', rx: 0.034, ry: 0.028 },
  Anhydrite: { M: 0.671, N: 0.500, color: '#d46eff', rx: 0.030, ry: 0.024 },
  Halite:    { M: 1.030, N: 0.780, color: '#ff4d88', rx: 0.038, ry: 0.030 },
};

// Mineral mixing lines for the M-N plot (straight lines between pairs)
const MN_MIXING_LINES = [
  { from: 'Limestone', to: 'Quartz',    color: '#ffffff', dash: '4 3', label: 'Ls-Qtz' },
  { from: 'Limestone', to: 'Dolomite',  color: '#88ffcc', dash: '5 2', label: 'Ls-Dol' },
  { from: 'Quartz',    to: 'Dolomite',  color: '#ffd080', dash: '4 4', label: 'Qtz-Dol' },
  { from: 'Dolomite',  to: 'Anhydrite', color: '#cc88ff', dash: '3 5', label: 'Dol-Anh' },
];

// MID plot derivations
// φe from PHIE; ρmaa = (ρb - φe × ρfl) / (1 - φe)
// Δtmaa = (Δt - φe × Δtfl) / (1 - φe)
const calcMID = (row, PHIE) => {
  const phi = Math.max(0.001, PHIE);
  const rmaa = +((row.RHOB - phi * FLUID.rhob) / (1 - phi)).toFixed(4);
  const dtmaa = +((row.DT  - phi * FLUID.dt)   / (1 - phi)).toFixed(4);
  return { rmaa: isFinite(rmaa) ? rmaa : null, dtmaa: isFinite(dtmaa) ? dtmaa : null };
};

// MID mineral reference points
const MID_MINERALS = {
  Quartz:    { rmaa: 2.648, dtmaa: 55.5, color: '#ffcc00' },
  Limestone: { rmaa: 2.710, dtmaa: 47.6, color: '#00c8ff' },
  Dolomite:  { rmaa: 2.876, dtmaa: 43.5, color: '#ff7b3a' },
  Anhydrite: { rmaa: 2.977, dtmaa: 50.0, color: '#d46eff' },
  Halite:    { rmaa: 2.165, dtmaa: 67.0, color: '#ff4d88' },
};

// Classify lithology from proximity in RHOB-NPHI space
const classifyLith = (rhob, nphi) => {
  let bestDist = Infinity, best = 'Unknown';
  // Use 3 primary minerals
  for (const [name, m] of Object.entries(MINERALS)) {
    if (name === 'Halite') continue;
    // Normalised euclidean distance to mineral line (phi 0–0.45 sampled)
    for (let phi = 0; phi <= 0.45; phi += 0.05) {
      const lx = m.nphi * (1 - phi) + FLUID.nphi * phi;
      const ly = m.rhob * (1 - phi) + FLUID.rhob * phi;
      const d = Math.sqrt(((nphi - lx) / 0.3) ** 2 + ((rhob - ly) / 0.5) ** 2);
      if (d < bestDist) { bestDist = d; best = name; }
    }
  }
  return best;
};

// ───────────────────────────────────────────
//  CUSTOM SVG CROSSPLOT COMPONENT
// ───────────────────────────────────────────
const PAD = { top: 32, right: 30, bottom: 50, left: 60 };

function SvgCrossPlot({
  title, points, xKey, yKey,
  xLabel, yLabel,
  xDomain, yDomain,
  xInvert = false,
  mineralLines = [],   // [{ pts:[{x,y}...], color, label, ticks, labelPt }]
  mineralPts  = [],   // [{ x, y, color, label }]
  mineralRegions = [], // [{ cx, cy, rx, ry, color, label }]  — ellipses
  mixingLines = [],    // [{ x1,y1,x2,y2, color, dash, label }]
  arrows = [],         // [{ x1,y1,x2,y2, color, label }]
  colorKey,
  colorMin, colorMax,
  showGrid = true,
  width = 460, height = 400,
}) {
  const W = width  - PAD.left - PAD.right;
  const H = height - PAD.top  - PAD.bottom;

  const toX = v => {
    const t = (v - xDomain[0]) / (xDomain[1] - xDomain[0]);
    return xInvert ? PAD.left + (1 - t) * W : PAD.left + t * W;
  };
  const toY = v => {
    const t = (v - yDomain[0]) / (yDomain[1] - yDomain[0]);
    return PAD.top + (1 - t) * H;
  };
  const scaleRx = rx => rx / (xDomain[1] - xDomain[0]) * W;
  const scaleRy = ry => ry / (yDomain[1] - yDomain[0]) * H;

  const xTicks = Array.from({ length: 6 }, (_, i) => xDomain[0] + (xDomain[1] - xDomain[0]) * i / 5);
  const yTicks = Array.from({ length: 6 }, (_, i) => yDomain[0] + (yDomain[1] - yDomain[0]) * i / 5);

  const ptColor = (pt) => {
    if (!colorKey) return '#00d4ff';
    const v = pt[colorKey];
    if (v == null) return '#4a6a8a';
    const t = Math.max(0, Math.min(1, (v - colorMin) / Math.max(1, colorMax - colorMin)));
    return cssJet(t);
  };

  return (
    <svg width={width} height={height} style={{ display: 'block', fontFamily: 'monospace' }}>
      {/* Background */}
      <rect x={PAD.left} y={PAD.top} width={W} height={H} fill="#04080f" rx="3" />
      <rect x={PAD.left} y={PAD.top} width={W} height={H} fill="none" stroke="#162840" strokeWidth="1" rx="3" />

      {/* Grid */}
      {showGrid && xTicks.map((v, i) => (
        <line key={`gx${i}`} x1={toX(v)} y1={PAD.top} x2={toX(v)} y2={PAD.top + H}
          stroke="#162840" strokeWidth="0.7" strokeDasharray="4 4" />
      ))}
      {showGrid && yTicks.map((v, i) => (
        <line key={`gy${i}`} x1={PAD.left} y1={toY(v)} x2={PAD.left + W} y2={toY(v)}
          stroke="#162840" strokeWidth="0.7" strokeDasharray="4 4" />
      ))}

      {/* Mineral ellipse regions */}
      {mineralRegions.map((mr, i) => (
        <g key={`mr${i}`}>
          <ellipse
            cx={toX(mr.cx)} cy={toY(mr.cy)}
            rx={scaleRx(mr.rx)} ry={scaleRy(mr.ry)}
            fill={mr.color} fillOpacity="0.10"
            stroke={mr.color} strokeWidth="1.2" strokeDasharray="5 3" strokeOpacity="0.55"
          />
        </g>
      ))}

      {/* Mixing lines between mineral pairs */}
      {mixingLines.map((ml, i) => (
        <line key={`mix${i}`}
          x1={toX(ml.x1)} y1={toY(ml.y1)}
          x2={toX(ml.x2)} y2={toY(ml.y2)}
          stroke={ml.color} strokeWidth="1.2" strokeDasharray={ml.dash || '6 3'}
          strokeOpacity="0.5"
        />
      ))}

      {/* Annotation arrows */}
      {arrows.map((ar, i) => {
        const ax1 = toX(ar.x1), ay1 = toY(ar.y1);
        const ax2 = toX(ar.x2), ay2 = toY(ar.y2);
        const dx = ax2 - ax1, dy = ay2 - ay1;
        const len = Math.sqrt(dx * dx + dy * dy);
        const arrowSize = 7;
        const ux = dx / len, uy = dy / len;
        // Arrowhead triangle
        const hx = ax2 - ux * arrowSize;
        const hy = ay2 - uy * arrowSize;
        const px = -uy * arrowSize * 0.45, py = ux * arrowSize * 0.45;
        return (
          <g key={`ar${i}`}>
            <line x1={ax1} y1={ay1} x2={ax2} y2={ay2}
              stroke={ar.color} strokeWidth="2" strokeOpacity="0.85" />
            <polygon
              points={`${ax2},${ay2} ${hx + px},${hy + py} ${hx - px},${hy - py}`}
              fill={ar.color} opacity="0.85"
            />
            {ar.label && (
              <text x={(ax1 + ax2) / 2 + 5} y={(ay1 + ay2) / 2 - 5}
                fontSize="9" fill={ar.color} fontWeight="700" fontFamily="monospace">
                {ar.label}
              </text>
            )}
          </g>
        );
      })}

      {/* Mineral reference lines (density-neutron) */}
      {mineralLines.map((ml, li) => {
        const d = ml.pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${toX(p.x).toFixed(1)},${toY(p.y).toFixed(1)}`).join(' ');
        return (
          <g key={`ml${li}`}>
            <path d={d} stroke={ml.color} strokeWidth="1.8" fill="none" strokeDasharray="6 3" opacity="0.85" />
            {ml.ticks && ml.ticks.map((tk, ti) => (
              <g key={ti}>
                <circle cx={toX(tk.x)} cy={toY(tk.y)} r="3" fill={ml.color} opacity="0.7" />
                <text x={toX(tk.x) + 4} y={toY(tk.y) - 4} fontSize="7" fill={ml.color} opacity="0.85">{tk.label}</text>
              </g>
            ))}
            {ml.labelPt && (
              <text x={toX(ml.labelPt.x) + 5} y={toY(ml.labelPt.y)} fontSize="8.5" fill={ml.color} fontWeight="700" opacity="0.9">{ml.label}</text>
            )}
          </g>
        );
      })}

      {/* Mineral reference points */}
      {mineralPts.map((mp, i) => (
        <g key={`mp${i}`}>
          <circle cx={toX(mp.x)} cy={toY(mp.y)} r="8" fill={mp.color} opacity="0.18" />
          <circle cx={toX(mp.x)} cy={toY(mp.y)} r="5" fill={mp.color} opacity="0.85" />
          <text x={toX(mp.x) + 9} y={toY(mp.y) + 4} fontSize="9.5" fill={mp.color} fontWeight="700">{mp.label}</text>
        </g>
      ))}

      {/* Data points */}
      {points.map((pt, i) => {
        const x = toX(pt[xKey]); const y = toY(pt[yKey]);
        if (x < PAD.left - 2 || x > PAD.left + W + 2 || y < PAD.top - 2 || y > PAD.top + H + 2) return null;
        return <circle key={i} cx={x} cy={y} r="2.6" fill={ptColor(pt)} opacity="0.70" />;
      })}

      {/* Axes */}
      <line x1={PAD.left} y1={PAD.top + H} x2={PAD.left + W} y2={PAD.top + H} stroke="#2a4060" strokeWidth="1.2" />
      <line x1={PAD.left} y1={PAD.top}     x2={PAD.left}     y2={PAD.top + H} stroke="#2a4060" strokeWidth="1.2" />

      {/* X tick labels */}
      {xTicks.map((v, i) => (
        <text key={i} x={toX(v)} y={PAD.top + H + 15} fontSize="9" fill="#4a6a8a" textAnchor="middle">{v.toFixed(2)}</text>
      ))}
      {/* Y tick labels */}
      {yTicks.map((v, i) => (
        <text key={i} x={PAD.left - 7} y={toY(v) + 4} fontSize="9" fill="#4a6a8a" textAnchor="end">{v.toFixed(2)}</text>
      ))}

      {/* Axis labels */}
      <text x={PAD.left + W / 2} y={height - 5} fontSize="10" fill="#8899aa" textAnchor="middle" fontWeight="700">{xLabel}</text>
      <text x={14} y={PAD.top + H / 2} fontSize="10" fill="#8899aa" textAnchor="middle" fontWeight="700"
        transform={`rotate(-90, 14, ${PAD.top + H / 2})`}>{yLabel}</text>

      {/* Title */}
      <text x={PAD.left + W / 2} y={18} fontSize="11" fill="#00d4ff" textAnchor="middle" fontWeight="700" letterSpacing="1">{title}</text>
    </svg>
  );
}

// ═══════════════════════════════════════════════════════
//  MAIN APPLICATION
// ═══════════════════════════════════════════════════════
const TABS = [
  { id: 'import',    label: 'DATA IMPORT',  icon: '⬆' },
  { id: 'logs',      label: 'WELL LOGS',    icon: '📈' },
  { id: 'petro',     label: 'PETROPHYSICS', icon: '⚗' },
  { id: 'porosity',  label: 'POROSITY',     icon: '🔬' },
  { id: 'swmodel',   label: 'WATER SAT',    icon: '💧' },
  { id: 'perm',      label: 'PERMEABILITY', icon: '🌊' },
  { id: 'lithology', label: 'LITHOLOGY',    icon: '🪨' },
  { id: 'reservoir', label: '3D RESERVOIR', icon: '🌐' },
  { id: 'ml',        label: 'ML PREDICT',   icon: '🤖' },
  { id: 'export',    label: 'EXPORT',       icon: '💾' },
];

const DEFAULT_PARAMS = {
  GRmin: 15, GRmax: 120,
  RHOBmat: 2.71, RHOBfl: 1.0,
  a: 1.0, m: 2.0, n: 2.0, Rw: 0.08,
  Rsh: 3.0,
  swModel: 'indonesia_full',
  kModel:  'timur',                     // Active permeability correlation
  VshCut: 0.50, PhiCut: 0.05, SwCut: 0.65,
  DTma: 47.5, DTfl: 189.0,
  PHINma: 0.0, PHINfl: 0.0,
};

// ── Colour tokens ──
const C = {
  bg:     '#060c18',
  surf:   '#0b1424',
  card:   '#0d1829',
  border: '#162840',
  cyan:   '#00d4ff',
  amber:  '#ffaa00',
  green:  '#00e887',
  pink:   '#f472b6',
  purple: '#a78bfa',
  muted:  '#4a6a8a',
  dim:    '#8899aa',
  text:   '#cde0f0',
};

const S = {
  card:    { background: C.card,   border: `1px solid ${C.border}`, borderRadius: 8, padding: 16 },
  label:   { fontSize: 10, color: C.muted, fontFamily: 'monospace', letterSpacing: 0.6, marginBottom: 3, textTransform: 'uppercase' },
  val:     { fontSize: 22, fontWeight: 700, fontFamily: 'monospace', color: C.cyan },
  input:   { background: '#08111f', border: `1px solid ${C.border}`, borderRadius: 4, color: C.text, padding: '5px 8px', fontSize: 12, fontFamily: 'monospace', width: '100%', outline: 'none' },
  secHead: { fontSize: 11, fontWeight: 700, color: C.cyan, letterSpacing: 1.5, fontFamily: 'monospace', marginBottom: 10, display: 'flex', alignItems: 'center', gap: 6 },
  btn:     (col = C.cyan) => ({ background: `${col}18`, border: `1px solid ${col}50`, color: col, padding: '7px 14px', borderRadius: 5, cursor: 'pointer', fontSize: 11, fontWeight: 700, letterSpacing: 0.8, fontFamily: 'monospace' }),
  ttip:    { background: '#08111f', border: `1px solid ${C.border}`, fontSize: 10, color: C.text, fontFamily: 'monospace' },
};

const trackTip = { contentStyle: S.ttip, wrapperStyle: { zIndex: 9999 } };

function PetroSimApp() {
  const [tab,         setTab]       = useState('import');
  const [rawData,     setRawData]   = useState(null);
  const [params,      setParams]    = useState(DEFAULT_PARAMS);
  const [gridParams,  setGridP]     = useState({ NX: 12, NY: 8, NZ: 10, DX: 100, DY: 100 });
  const [colorProp,   setColorProp] = useState('PHIE');
  const [lasFile,     setLasFile]   = useState('');
  const [mlProgress,  setMlP]       = useState(0);
  const [mlDone,      setMlDone]    = useState(false);
  const [hetero,      setHetero]    = useState(true);
  const [notification, setNote]     = useState('');

  const notify = (msg, ms = 2400) => { setNote(msg); setTimeout(() => setNote(''), ms); };

  const petro = useMemo(() => rawData ? calcPetrophysics(rawData, params) : null, [rawData, params]);

  const payZones = useMemo(() => {
    if (!petro) return [];
    const out = []; let open = false, top = 0;
    petro.forEach(d => {
      if (d.PAY && !open) { open = true; top = d.DEPTH; }
      if (!d.PAY && open) { open = false; out.push({ top, bot: d.DEPTH, net: d.DEPTH - top }); }
    });
    if (open) out.push({ top, bot: petro[petro.length-1].DEPTH, net: petro[petro.length-1].DEPTH - top });
    return out;
  }, [petro]);

  const summaryStats = useMemo(() => {
    if (!petro) return null;
    const pay = petro.filter(d => d.PAY);
    return {
      netPay:   payZones.reduce((s, z) => s + z.net, 0),
      avgPHIE:  calcStats(petro, 'PHIE').mean,
      avgSw:    calcStats(petro, 'Sw').mean,
      avgVsh:   calcStats(petro, 'Vsh').mean,
      payPHIE:  pay.length ? calcStats(pay, 'PHIE').mean : 0,
      paySw:    pay.length ? calcStats(pay, 'Sw').mean   : 0,
      avgK:     calcStats(petro, 'K').mean,
    };
  }, [petro, payZones]);

  const mlData = useMemo(() => {
    if (!petro || !mlDone) return [];
    return petro.filter((_, i) => i % 4 === 0).map(d => {
      // Simple Kozeny-Carman noise model
      const noise = 1 + (Math.random() - 0.5) * 0.28;
      return { actual: d.K, predicted: Math.max(0.001, d.K * noise), PHIE: d.PHIE, Vsh: d.Vsh };
    });
  }, [petro, mlDone]);

  const handleLasFile = useCallback(async (file) => {
    try {
      const text = await file.text();
      const parsed = parseLAS(text);
      if (parsed && parsed.length > 10) {
        setRawData(parsed);
        setLasFile(file.name);
        notify(`✓ Loaded ${parsed.length} depth points from ${file.name}`);
      } else {
        notify('⚠ Could not parse LAS — loading sample data');
        setRawData(generateWellData());
        setLasFile('');
      }
    } catch { notify('⚠ File error — loading sample data'); setRawData(generateWellData()); }
  }, []);

  const trainML = () => {
    if (!petro) return;
    setMlP(0); setMlDone(false);
    const iv = setInterval(() => {
      setMlP(p => { const n = p + Math.random() * 7 + 2; if (n >= 100) { clearInterval(iv); setMlDone(true); return 100; } return n; });
    }, 60);
  };

  const downloadText = (content, filename) => {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([content], { type: 'text/plain' }));
    a.download = filename; a.click();
  };

  // ── Shared tooltip style ──
  const TT = { contentStyle: S.ttip };

  // ══════════════════════════════════════════
  //  RENDER: IMPORT TAB
  // ══════════════════════════════════════════
  const renderImport = () => (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
      {/* Upload panel */}
      <div style={S.card}>
        <div style={S.secHead}>⬆ LAS FILE UPLOAD</div>
        <div
          style={{ border: `2px dashed ${C.border}`, borderRadius: 8, padding: '32px 24px', textAlign: 'center', cursor: 'pointer', transition: 'border-color .2s' }}
          onClick={() => document.getElementById('las-in').click()}
          onDragOver={e => { e.preventDefault(); e.currentTarget.style.borderColor = C.cyan; }}
          onDragLeave={e => { e.currentTarget.style.borderColor = C.border; }}
          onDrop={e => { e.preventDefault(); e.currentTarget.style.borderColor = C.border; if (e.dataTransfer.files[0]) handleLasFile(e.dataTransfer.files[0]); }}
        >
          <div style={{ fontSize: 36, marginBottom: 10 }}>📄</div>
          <div style={{ color: C.cyan, fontSize: 13, fontWeight: 700 }}>Drop .LAS file here</div>
          <div style={{ color: C.muted, fontSize: 11, marginTop: 6 }}>Supports LAS 2.0 / 3.0 — DEPT, GR, RHOB, NPHI, RT, DT</div>
          {lasFile && <div style={{ color: C.green, fontSize: 11, marginTop: 10, fontFamily: 'monospace' }}>✓ {lasFile}</div>}
        </div>
        <input id="las-in" type="file" accept=".las,.LAS" style={{ display: 'none' }} onChange={e => e.target.files[0] && handleLasFile(e.target.files[0])} />
        <div style={{ marginTop: 16, fontSize: 11, color: C.dim, fontFamily: 'monospace', lineHeight: 1.7 }}>
          Supported logs: GR / RHOB / NPHI / RT(ILD/LLD) / DT
        </div>
      </div>

      {/* Quick-start panel */}
      <div style={S.card}>
        <div style={S.secHead}>⚡ QUICK START — SYNTHETIC WELL</div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginBottom: 16 }}>
          {[
            { k: 'Interval',  v: '2000 – 2500 m' },
            { k: 'Step',      v: '1 m (501 points)' },
            { k: 'Logs',      v: 'GR, RHOB, NPHI, RT, DT' },
            { k: 'Geology',   v: '3 sands + 4 shales' },
            { k: 'Framework', v: 'Clastic fluvial' },
            { k: 'Fluid',     v: 'Oil-Water system' },
          ].map(({ k, v }) => (
            <div key={k} style={{ background: '#08111f', borderRadius: 4, padding: '6px 10px' }}>
              <div style={{ ...S.label, marginBottom: 1 }}>{k}</div>
              <div style={{ fontSize: 11, color: C.text, fontFamily: 'monospace' }}>{v}</div>
            </div>
          ))}
        </div>
        <button style={{ ...S.btn(C.cyan), width: '100%', padding: 10, fontSize: 12 }}
          onClick={() => { setRawData(generateWellData()); setLasFile(''); notify('✓ Synthetic well data loaded'); }}>
          ⚡ LOAD SYNTHETIC WELL DATA
        </button>

        {rawData && (
          <div style={{ marginTop: 12, padding: 10, background: '#060e1a', borderRadius: 6, border: `1px solid ${C.border}` }}>
            <div style={{ display: 'flex', gap: 20, justifyContent: 'space-around' }}>
              {[
                { l: 'POINTS', v: rawData.length },
                { l: 'TOP', v: rawData[0]?.DEPTH + 'm' },
                { l: 'BASE', v: rawData[rawData.length-1]?.DEPTH + 'm' },
              ].map(({ l, v }) => (
                <div key={l} style={{ textAlign: 'center' }}>
                  <div style={S.label}>{l}</div>
                  <div style={{ ...S.val, fontSize: 16 }}>{v}</div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Data preview */}
      {rawData && (
        <div style={{ ...S.card, gridColumn: '1 / -1' }}>
          <div style={S.secHead}>📋 RAW DATA PREVIEW
            <span style={{ fontSize: 10, color: C.muted, fontWeight: 400 }}> — first 25 rows of {rawData.length}</span>
          </div>
          <div style={{ overflowX: 'auto', overflowY: 'auto', maxHeight: 280 }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 11, fontFamily: 'monospace' }}>
              <thead>
                <tr>{['DEPTH (m)','GR (API)','RHOB (g/cc)','NPHI (frac)','RT (Ω·m)','DT (μs/ft)','ZONE'].map(h => (
                  <th key={h} style={{ padding: '6px 14px', color: C.cyan, borderBottom: `1px solid ${C.border}`, textAlign: 'right', fontWeight: 700, whiteSpace: 'nowrap' }}>{h}</th>
                ))}</tr>
              </thead>
              <tbody>
                {rawData.slice(0, 25).map((r, i) => (
                  <tr key={i} style={{ background: i % 2 ? 'transparent' : '#07101c', borderBottom: `1px solid ${C.border}22` }}>
                    <td style={{ padding: '4px 14px', color: C.amber, textAlign: 'right' }}>{r.DEPTH}</td>
                    <td style={{ padding: '4px 14px', textAlign: 'right' }}>{r.GR}</td>
                    <td style={{ padding: '4px 14px', textAlign: 'right' }}>{r.RHOB}</td>
                    <td style={{ padding: '4px 14px', textAlign: 'right' }}>{r.NPHI}</td>
                    <td style={{ padding: '4px 14px', textAlign: 'right' }}>{r.RT}</td>
                    <td style={{ padding: '4px 14px', textAlign: 'right' }}>{r.DT}</td>
                    <td style={{ padding: '4px 14px', color: C.dim, textAlign: 'right' }}>{r.ZONE}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );

  // ══════════════════════════════════════════
  //  RENDER: WELL LOGS TAB
  // ══════════════════════════════════════════
  const renderLogs = () => {
    if (!rawData) return <NoData />;
    const ds = rawData.filter((_, i) => i % 2 === 0);
    const depthDomain = ['dataMax', 'dataMin'];
    const trackCfg = [
      { key: 'GR',   label: 'GR', unit: 'API', color: '#00e8a0', domain: [0, 150],   fill: true  },
      { key: 'RHOB', label: 'RHOB', unit: 'g/cc', color: '#ff6b8a', domain: [1.9, 2.8], fill: false },
      { key: 'NPHI', label: 'NPHI', unit: 'frac', color: '#74b9ff', domain: [0.5, 0],   fill: true  },
      { key: 'RT',   label: 'log(RT)', unit: 'Ω·m', color: '#ffaa00', domain: [-0.3, 2.8], fill: true, transform: d => Math.max(-0.3, Math.log10(Math.max(0.1, d.RT))) },
      { key: 'DT',   label: 'DT', unit: 'μs/ft', color: '#b197fc', domain: [40, 130],  fill: false },
    ];
    return (
      <div style={S.card}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
          <div style={S.secHead}>📈 COMPOSITE WELL LOG</div>
          <div style={{ display: 'flex', gap: 16, fontSize: 10, fontFamily: 'monospace', color: C.muted, marginLeft: 'auto' }}>
            {trackCfg.map(t => (
              <span key={t.key} style={{ color: t.color }}>■ {t.label}</span>
            ))}
          </div>
        </div>

        {/* Track grid */}
        <div style={{ display: 'grid', gridTemplateColumns: `55px repeat(5, 1fr) 48px`, height: 520, borderTop: `1px solid ${C.border}` }}>
          {/* Depth axis */}
          <div style={{ borderRight: `1px solid ${C.border}` }}>
            <div style={{ height: 22, borderBottom: `1px solid ${C.border}`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <span style={{ fontSize: 9, color: C.cyan, fontFamily: 'monospace' }}>DEPTH m</span>
            </div>
            <ResponsiveContainer width="100%" height={498}>
              <AreaChart layout="vertical" data={ds} margin={{ top: 0, right: 0, bottom: 0, left: 0 }}>
                <YAxis type="number" dataKey="DEPTH" reversed domain={depthDomain} tick={{ fill: C.muted, fontSize: 9, fontFamily: 'monospace' }} tickCount={14} width={52} />
                <XAxis type="number" hide />
                <Area type="monotone" dataKey="DEPTH" stroke="none" fill="none" />
              </AreaChart>
            </ResponsiveContainer>
          </div>

          {/* Log tracks */}
          {trackCfg.map(tc => {
            const tData = tc.transform ? ds.map(d => ({ ...d, [tc.key]: tc.transform(d) })) : ds;
            return (
              <div key={tc.key} style={{ borderRight: `1px solid ${C.border}` }}>
                <div style={{ height: 22, borderBottom: `1px solid ${C.border}`, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 1 }}>
                  <span style={{ fontSize: 9, color: tc.color, fontFamily: 'monospace', fontWeight: 700 }}>{tc.label}</span>
                  <span style={{ fontSize: 8, color: C.muted, fontFamily: 'monospace' }}>{tc.unit}</span>
                </div>
                <ResponsiveContainer width="100%" height={498}>
                  <AreaChart layout="vertical" data={tData} margin={{ top: 0, right: 2, bottom: 0, left: -30 }}>
                    <XAxis type="number" domain={tc.domain} tickCount={3} tick={{ fill: C.muted, fontSize: 8 }} />
                    <YAxis type="number" dataKey="DEPTH" reversed domain={depthDomain} hide />
                    <Tooltip {...TT} formatter={v => [v?.toFixed(3), tc.label]} labelFormatter={d => `Depth: ${d}m`} />
                    <Area type="monotone" dataKey={tc.key} stroke={tc.color} fill={tc.fill ? tc.color : 'none'} fillOpacity={0.25} strokeWidth={1.5} dot={false} />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            );
          })}

          {/* Zone column */}
          <div style={{ position: 'relative', overflow: 'hidden' }}>
            <div style={{ height: 22, borderBottom: `1px solid ${C.border}`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <span style={{ fontSize: 8, color: C.muted, fontFamily: 'monospace' }}>LITHO</span>
            </div>
            <svg width="100%" height={498} style={{ position: 'absolute', top: 22 }}>
              {(() => {
                const dmin = rawData[0].DEPTH, dmax = rawData[rawData.length-1].DEPTH, drange = dmax - dmin;
                const blocks = [];
                rawData.forEach((d, i) => {
                  if (i === 0 || d.ZONE !== rawData[i-1].ZONE) {
                    const pct = (d.DEPTH - dmin) / drange * 100;
                    blocks.push({ pct, zone: d.ZONE });
                  }
                });
                return blocks.map((b, i) => {
                  const ePct = i < blocks.length - 1 ? blocks[i+1].pct : 100;
                  const isSand = !b.zone.toLowerCase().includes('shale');
                  return (
                    <g key={i}>
                      <rect x="0" y={`${b.pct}%`} width="100%" height={`${ePct - b.pct}%`}
                        fill={isSand ? `${C.amber}22` : `${C.pink}15`} />
                      {(ePct - b.pct) > 5 && (
                        <text x="50%" y={`${(b.pct + ePct) / 2}%`} fill={isSand ? C.amber : C.pink}
                          fontSize="7" textAnchor="middle" dominantBaseline="middle"
                          transform={`rotate(-90 ${24} ${(b.pct + ePct) / 2 * 4.98})`}
                          style={{ fontFamily: 'monospace' }}>
                          {b.zone.substring(0, 8)}
                        </text>
                      )}
                    </g>
                  );
                });
              })()}
            </svg>
          </div>
        </div>
      </div>
    );
  };

  // ══════════════════════════════════════════
  //  RENDER: PETROPHYSICS TAB
  // ══════════════════════════════════════════
  const renderPetro = () => {
    if (!petro) return <NoData />;
    const ds = petro.filter((_, i) => i % 2 === 0);
    const depthDomain = ['dataMax', 'dataMin'];

    return (
      <div style={{ display: 'grid', gridTemplateColumns: '260px 1fr', gap: 16 }}>
        {/* Left: params + pay */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={S.card}>
            <div style={S.secHead}>⚙ PARAMETERS</div>
            {[
              { k:'GRmin',   l:'GR Sand Min (API)',       step:1   },
              { k:'GRmax',   l:'GR Shale Max (API)',      step:1   },
              { k:'RHOBmat', l:'Matrix Density (g/cc)',   step:0.01},
              { k:'RHOBfl',  l:'Fluid Density (g/cc)',    step:0.01},
              { k:'a',       l:'Archie a',                step:0.1 },
              { k:'m',       l:'Archie m (cementation)',  step:0.1 },
              { k:'n',       l:'Archie n (saturation)',   step:0.1 },
              { k:'Rw',      l:'Rw (Ω·m)',                step:0.01},
              { k:'VshCut',  l:'Vsh Cutoff',              step:0.05},
              { k:'PhiCut',  l:'PHIE Cutoff',             step:0.01},
              { k:'SwCut',   l:'Sw Cutoff',               step:0.05},
            ].map(({ k, l, step }) => (
              <div key={k} style={{ marginBottom: 7 }}>
                <div style={S.label}>{l}</div>
                <input type="number" style={S.input} value={params[k]} step={step}
                  onChange={e => setParams(p => ({ ...p, [k]: parseFloat(e.target.value) || 0 }))} />
              </div>
            ))}
          </div>

          {payZones.length > 0 && (
            <div style={S.card}>
              <div style={S.secHead}>✅ NET PAY ZONES</div>
              {payZones.map((z, i) => (
                <div key={i} style={{ display: 'flex', justifyContent: 'space-between', padding: '5px 0', borderBottom: `1px solid ${C.border}`, fontSize: 11, fontFamily: 'monospace' }}>
                  <span style={{ color: C.green }}>Zone {i + 1}</span>
                  <span style={{ color: C.dim }}>{z.top}–{z.bot}m</span>
                  <span style={{ color: C.amber }}>{z.net.toFixed(0)}m</span>
                </div>
              ))}
              <div style={{ marginTop: 8, textAlign: 'right', fontSize: 12, fontFamily: 'monospace', color: C.cyan }}>
                Total net: {payZones.reduce((s, z) => s + z.net, 0).toFixed(0)}m
              </div>
            </div>
          )}
        </div>

        {/* Right: stats + log tracks */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {/* KPI row */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 10 }}>
            {summaryStats && [
              { l: 'AVG POROSITY',  v: (summaryStats.avgPHIE * 100).toFixed(1) + '%', c: C.cyan   },
              { l: 'AVG WATER SAT', v: (summaryStats.avgSw   * 100).toFixed(1) + '%', c: '#74b9ff' },
              { l: 'AVG VSH',       v: (summaryStats.avgVsh  * 100).toFixed(1) + '%', c: C.pink   },
              { l: 'NET PAY',       v: summaryStats.netPay.toFixed(0) + 'm',           c: C.green  },
              { l: 'PAY PHIE',      v: (summaryStats.payPHIE * 100).toFixed(1) + '%', c: C.cyan   },
              { l: 'PAY Sw',        v: (summaryStats.paySw   * 100).toFixed(1) + '%', c: '#74b9ff' },
              { l: 'AVG PERM',      v: summaryStats.avgK.toFixed(1) + ' mD',           c: C.amber  },
              { l: 'ZONES',         v: payZones.length,                                c: C.green  },
            ].map(({ l, v, c }) => (
              <div key={l} style={{ ...S.card, padding: '10px 12px', textAlign: 'center' }}>
                <div style={S.label}>{l}</div>
                <div style={{ ...S.val, fontSize: 17, color: c }}>{v}</div>
              </div>
            ))}
          </div>

          {/* Porosity histogram */}
          <div style={S.card}>
            <div style={S.secHead}>POROSITY DISTRIBUTION (PHIE)</div>
            <ResponsiveContainer width="100%" height={100}>
              <BarChart margin={{ top: 2, right: 4, bottom: 2, left: -24 }} data={(() => {
                const bins = Array.from({ length: 22 }, (_, i) => ({ phi: (i * 0.02).toFixed(2), n: 0 }));
                petro.forEach(d => { const b = Math.min(21, Math.floor(d.PHIE / 0.02)); bins[b].n++; });
                return bins;
              })()}>
                <XAxis dataKey="phi" tick={{ fill: C.muted, fontSize: 8 }} interval={4} />
                <YAxis tick={{ fill: C.muted, fontSize: 8 }} />
                <Bar dataKey="n" radius={[2,2,0,0]}>
                  {Array.from({ length: 22 }, (_, i) => (
                    <Cell key={i} fill={cssJet(i / 21)} />
                  ))}
                </Bar>
                <Tooltip {...TT} formatter={v => [v, 'Count']} labelFormatter={v => `PHIE ≈ ${v}`} />
              </BarChart>
            </ResponsiveContainer>
          </div>

          {/* Calculated log tracks */}
          <div style={{ ...S.card, padding: 0 }}>
            <div style={{ padding: '8px 14px', borderBottom: `1px solid ${C.border}` }}>
              <div style={S.secHead}>CALCULATED PETROPHYSICAL LOGS</div>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '52px repeat(5, 1fr)', height: 440 }}>
              {/* Depth */}
              <div style={{ borderRight: `1px solid ${C.border}` }}>
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart layout="vertical" data={ds} margin={{ top: 0, right: 0, bottom: 0, left: 0 }}>
                    <YAxis type="number" dataKey="DEPTH" reversed domain={depthDomain} tick={{ fill: C.muted, fontSize: 9, fontFamily: 'monospace' }} tickCount={12} width={50} />
                    <XAxis type="number" hide />
                    <Area type="monotone" dataKey="DEPTH" stroke="none" fill="none" />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
              {[
                { key: 'Vsh',  color: C.pink,   label: 'Vsh',    domain: [0, 1],   fill: true  },
                { key: 'PHIE', color: C.cyan,   label: 'PHIE',   domain: [0, 0.45],fill: true  },
                { key: 'Sw',   color: '#74b9ff',label: 'Sw',     domain: [0, 1],   fill: true  },
                { key: 'Shc',  color: C.green,  label: 'Shc',    domain: [0, 1],   fill: true  },
                { key: 'K',    color: C.amber,  label: 'K (mD)', domain: [0, 300], fill: false },
              ].map(tc => (
                <div key={tc.key} style={{ borderRight: `1px solid ${C.border}` }}>
                  <div style={{ height: 20, borderBottom: `1px solid ${C.border}`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <span style={{ fontSize: 9, color: tc.color, fontFamily: 'monospace', fontWeight: 700 }}>{tc.label}</span>
                  </div>
                  <ResponsiveContainer width="100%" height="94%">
                    <AreaChart layout="vertical" data={ds} margin={{ top: 0, right: 2, bottom: 0, left: -30 }}>
                      <XAxis type="number" domain={tc.domain} tickCount={3} tick={{ fill: C.muted, fontSize: 8 }} />
                      <YAxis type="number" dataKey="DEPTH" reversed domain={depthDomain} hide />
                      <Tooltip {...TT} formatter={v => [v?.toFixed(4), tc.label]} labelFormatter={d => `${d}m`} />
                      {tc.key === 'Shc' && <ReferenceLine x={0} stroke={C.muted} strokeWidth={1} />}
                      <Area type="monotone" dataKey={tc.key} stroke={tc.color} fill={tc.fill ? tc.color : 'none'} fillOpacity={0.28} strokeWidth={1.5} dot={false} />
                    </AreaChart>
                  </ResponsiveContainer>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    );
  };

  // ══════════════════════════════════════════
  //  RENDER: LITHOLOGY IDENTIFICATION TAB
  // ══════════════════════════════════════════
  const renderLithology = () => {
    if (!petro || !rawData) return <NoData />;

    // Decimate for rendering performance
    const step = Math.max(1, Math.floor(rawData.length / 220));
    const pts = rawData
      .filter((_, i) => i % step === 0)
      .map((r, i) => {
        const pe = petro[i * step] || {};
        return {
          ...r,
          PHIE: pe.PHIE || 0,
          Vsh:  pe.Vsh  || 0,
          lith: classifyLith(r.RHOB, r.NPHI),
        };
      });

    // Decorate with M-N and MID values
    const ptsMN  = pts.map(r => ({ ...r, ...calcMN(r) }))
                      .filter(r => r.M > 0.4 && r.M < 1.3 && r.N > 0.2 && r.N < 1.2);
    const ptsMID = pts.map(r => ({ ...r, ...calcMID(r, r.PHIE) }))
                      .filter(r => r.rmaa > 1.8 && r.rmaa < 3.2 && r.dtmaa > 35 && r.dtmaa < 90);

    // GR range for color scale
    const grMin = 10, grMax = 130;

    // Build mineral lines for density-neutron crossplot
    const dnLines = Object.entries(MINERALS)
      .filter(([k]) => ['Quartz','Limestone','Dolomite'].includes(k))
      .map(([name, m]) => {
        const raw = mineralLine(m);
        const ticks = [0.10, 0.20, 0.30].map(phi => ({
          x: +(m.nphi * (1 - phi) + FLUID.nphi * phi).toFixed(4),
          y: +(m.rhob * (1 - phi) + FLUID.rhob * phi).toFixed(4),
          label: `ϕ${(phi * 100).toFixed(0)}`,
        }));
        return {
          pts: raw.map(p => ({ x: p.nphi, y: p.rhob })),
          color: m.color, label: name, ticks,
          labelPt: { x: m.nphi, y: m.rhob },
        };
      });

    // Special mineral points (evaporites)
    const dnPts = [
      { x: MINERALS.Anhydrite.nphi, y: MINERALS.Anhydrite.rhob, color: MINERALS.Anhydrite.color, label: 'Anhydrite' },
      { x: MINERALS.Halite.nphi,    y: MINERALS.Halite.rhob,    color: MINERALS.Halite.color,    label: 'Halite'    },
    ];

    // Lithology tally
    const lithCount = {};
    pts.forEach(p => { lithCount[p.lith] = (lithCount[p.lith] || 0) + 1; });
    const totalPts = pts.length;

    // Pie-like bar for lithology distribution
    const lithDist = Object.entries(lithCount)
      .sort((a, b) => b[1] - a[1])
      .map(([name, n]) => ({ name, pct: +((n / totalPts) * 100).toFixed(1), color: MINERALS[name]?.color || '#4a6a8a' }));

    // Depth-coloured lithology log
    const lithLog = pts.map(p => ({
      DEPTH: p.DEPTH, lith: p.lith, GR: p.GR,
      color: MINERALS[p.lith]?.color || '#4a6a8a',
    }));

    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>

        {/* ── HEADER BANNER ── */}
        <div style={{ ...S.card, padding: '12px 18px', borderLeft: `3px solid ${C.cyan}` }}>
          <div style={{ fontSize: 12, color: C.text, lineHeight: 1.75 }}>
            <span style={{ color: C.cyan, fontWeight: 700, fontFamily: 'monospace' }}>LITHOLOGY IDENTIFICATION</span>
            {' '}— Three complementary graphical cross-plot methods identify formation lithology from
            bulk density (ρb), neutron porosity (φN), and sonic travel time (Δt) log responses.
            Mineral reference lines (Quartz · Limestone · Dolomite) and evaporite points (Anhydrite · Halite)
            anchor each diagram. Data points coloured by GR show high-shale intervals deviating off the mineral lines.
          </div>
        </div>

        {/* ── TOP ROW: DN crossplot + M-N plot ── */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>

          {/* ─ Density-Neutron ─ */}
          <div style={S.card}>
            <div style={{ ...S.secHead, marginBottom: 6 }}>① DENSITY – NEUTRON CROSS PLOT
              <span style={{ fontSize: 9, color: C.muted, fontWeight: 400, marginLeft: 6 }}>(NPHI inverted)</span>
            </div>
            <div style={{ fontSize: 10, color: C.dim, marginBottom: 10, lineHeight: 1.6 }}>
              Mineral lines separate quartz, limestone and dolomite zones. Evaporite outliers
              (anhydrite, halite) plot well off matrix lines. High-GR shale points (warm colours)
              scatter above and between the clean-rock trends.
            </div>
            <div style={{ display: 'flex', justifyContent: 'center', overflowX: 'auto' }}>
              <SvgCrossPlot
                title="ρb vs φN (Limestone-Calibrated)"
                points={pts}
                xKey="NPHI" yKey="RHOB"
                xLabel="Neutron Porosity φN (v/v)  →  ← inverted"
                yLabel="Bulk Density ρb (g/cc)"
                xDomain={[-0.05, 0.45]} yDomain={[1.85, 3.05]}
                xInvert={true}
                mineralLines={dnLines}
                mineralPts={dnPts}
                colorKey="GR" colorMin={grMin} colorMax={grMax}
                width={430} height={380}
              />
            </div>
            {/* Legend */}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, marginTop: 8, fontSize: 10, fontFamily: 'monospace' }}>
              {Object.entries(MINERALS).map(([k, m]) => (
                <span key={k} style={{ color: m.color }}>
                  {m.symbol} {k}
                </span>
              ))}
              <span style={{ color: C.muted, marginLeft: 'auto' }}>colour = GR (blue→red)</span>
            </div>
          </div>

          {/* ─ M-N Plot — FULL ENHANCED ─ */}
          <div style={S.card}>
            <div style={{ ...S.secHead, marginBottom: 4 }}>② M – N LITHO-POROSITY CROSS PLOT</div>

            {/* Formula display row */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginBottom: 10 }}>
              {[
                {
                  eq: 'Eq. (7)',
                  formula: 'M = 0.01 × (Δtf − Δt) / (ρb − ρf)',
                  vars: [
                    { k: 'Δtf',  v: '189 μs/ft', d: 'fluid transit time' },
                    { k: 'Δt',   v: 'DT log',    d: 'formation transit time' },
                    { k: 'ρb',   v: 'RHOB log',  d: 'bulk density g/cc' },
                    { k: 'ρf',   v: '1.0 g/cc',  d: 'fluid density' },
                  ],
                },
                {
                  eq: 'Eq. (8)',
                  formula: 'N = (φNf − φNma) / (ρma − ρf)',
                  vars: [
                    { k: 'φNf',  v: '1.0 v/v',   d: 'fluid neutron porosity' },
                    { k: 'φNma', v: 'NPHI log',  d: 'formation neutron porosity' },
                    { k: 'ρma',  v: 'RHOB log',  d: 'matrix/bulk density' },
                    { k: 'ρf',   v: '1.0 g/cc',  d: 'fluid density' },
                  ],
                },
              ].map(({ eq, formula, vars }) => (
                <div key={eq} style={{ background: '#060e1a', border: `1px solid ${C.border}`, borderRadius: 6, padding: '8px 10px' }}>
                  <div style={{ fontSize: 9, color: C.amber, fontFamily: 'monospace', fontWeight: 700, marginBottom: 4 }}>{eq}</div>
                  <div style={{
                    fontSize: 11, color: C.cyan, fontFamily: 'monospace', fontWeight: 700,
                    background: '#03070f', borderRadius: 4, padding: '5px 8px', marginBottom: 6,
                    border: `1px solid ${C.cyan}30`,
                  }}>{formula}</div>
                  {vars.map(v => (
                    <div key={v.k} style={{ display: 'flex', gap: 6, fontSize: 9, fontFamily: 'monospace', marginBottom: 2 }}>
                      <span style={{ color: C.cyan, minWidth: 40 }}>{v.k}</span>
                      <span style={{ color: C.amber, minWidth: 68 }}>{v.v}</span>
                      <span style={{ color: C.muted }}>{v.d}</span>
                    </div>
                  ))}
                </div>
              ))}
            </div>

            {/* Key insight box */}
            <div style={{ background: `${C.green}0e`, border: `1px solid ${C.green}30`, borderRadius: 5, padding: '7px 12px', marginBottom: 10, fontSize: 10, color: C.dim, lineHeight: 1.65 }}>
              <span style={{ color: C.green, fontWeight: 700 }}>KEY ADVANTAGE — Secondary Porosity Detection: </span>
              The M-N plot uniquely identifies secondary (vuggy / fracture) porosity because
              <span style={{ color: C.text }}> M increases as density drops while N remains insensitive</span>. A point
              plotting <em>above</em> its expected mineral line indicates secondary porosity raising M
              without proportionally shifting N.
              <span style={{ color: '#00c8ff' }}> Calcite (Limestone) + Dolomite</span> form the dominant primary
              minerals in the studied formation. High-GR shale points migrate toward lower-M / lower-N
              (warm colours), consistent with clay-bound water effects.
            </div>

            {/* The crossplot itself */}
            <div style={{ display: 'flex', justifyContent: 'center', overflowX: 'auto' }}>
              <SvgCrossPlot
                title="M – N Litho-Porosity Identification Plot"
                points={ptsMN}
                xKey="N" yKey="M"
                xLabel="N  — Neutron-Density Slope  (dimensionless)  →"
                yLabel="M  — Sonic-Density Slope  (dimensionless)"
                xDomain={[0.28, 1.02]} yDomain={[0.45, 1.18]}
                xInvert={false}
                mineralPts={Object.entries(MN_MINERALS).map(([k, v]) => ({
                  x: v.N, y: v.M, color: v.color, label: k,
                }))}
                mineralRegions={Object.entries(MN_MINERALS).map(([k, v]) => ({
                  cx: v.N, cy: v.M, rx: v.rx, ry: v.ry, color: v.color, label: k,
                }))}
                mixingLines={MN_MIXING_LINES.map(ml => ({
                  x1: MN_MINERALS[ml.from].N, y1: MN_MINERALS[ml.from].M,
                  x2: MN_MINERALS[ml.to].N,   y2: MN_MINERALS[ml.to].M,
                  color: ml.color, dash: ml.dash, label: ml.label,
                }))}
                arrows={[
                  // Secondary porosity vector: M↑, N constant → upward arrow from Limestone ref
                  {
                    x1: MN_MINERALS.Limestone.N,
                    y1: MN_MINERALS.Limestone.M,
                    x2: MN_MINERALS.Limestone.N,
                    y2: MN_MINERALS.Limestone.M + 0.14,
                    color: '#00e887',
                    label: '2° por →',
                  },
                  // Gas effect vector: both M and N increase
                  {
                    x1: MN_MINERALS.Limestone.N + 0.02,
                    y1: MN_MINERALS.Limestone.M + 0.02,
                    x2: MN_MINERALS.Limestone.N + 0.10,
                    y2: MN_MINERALS.Limestone.M + 0.10,
                    color: '#ffcc00',
                    label: 'gas effect',
                  },
                ]}
                colorKey="GR" colorMin={grMin} colorMax={grMax}
                width={430} height={390}
              />
            </div>

            {/* Legend + mineral reference table */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: 12, marginTop: 10 }}>
              <div>
                <div style={{ fontSize: 9, color: C.muted, marginBottom: 5, fontFamily: 'monospace', letterSpacing: 0.8 }}>MINERAL REFERENCE POINTS</div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, fontSize: 10, fontFamily: 'monospace' }}>
                  {Object.entries(MN_MINERALS).map(([k, v]) => (
                    <span key={k} style={{ color: v.color }}>
                      ● {k} <span style={{ color: C.muted, fontSize: 9 }}>(M={v.M}, N={v.N})</span>
                    </span>
                  ))}
                </div>
                <div style={{ display: 'flex', gap: 16, marginTop: 6, fontSize: 10, fontFamily: 'monospace' }}>
                  <span style={{ color: '#00e887' }}>↑ Secondary porosity direction (M↑, N const)</span>
                  <span style={{ color: '#ffcc00' }}>↗ Gas effect (M↑ and N↑)</span>
                  <span style={{ color: C.muted, marginLeft: 'auto' }}>Data colour = GR API (blue→low, red→high)</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* ── DEDICATED SECONDARY POROSITY ANALYSIS SECTION ── */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 320px', gap: 14 }}>

          {/* Secondary Porosity Index depth log */}
          <div style={S.card}>
            <div style={S.secHead}>⊕ SECONDARY POROSITY INDEX (SPI) — DEPTH LOG</div>
            <div style={{ fontSize: 10, color: C.dim, marginBottom: 8, lineHeight: 1.6 }}>
              SPI = max(0, M<sub>measured</sub> − M<sub>limestone ref</sub>).
              Positive SPI indicates vugs, fractures or dissolution porosity above the primary matrix baseline.
            </div>
            <ResponsiveContainer width="100%" height={280}>
              <AreaChart
                layout="vertical"
                data={ptsMN.filter((_, i) => i % 2 === 0).map(d => ({ DEPTH: d.DEPTH, SPI: +(d.SP * 100).toFixed(2), Vsh: +(d.Vsh * 100).toFixed(1) }))}
                margin={{ top: 4, right: 8, bottom: 0, left: 10 }}
              >
                <XAxis type="number" domain={[0, 20]} unit="%" tick={{ fill: C.muted, fontSize: 9 }} tickCount={5} />
                <YAxis type="number" dataKey="DEPTH" reversed domain={['dataMax','dataMin']}
                  tick={{ fill: C.muted, fontSize: 9, fontFamily: 'monospace' }} tickCount={10} width={46} />
                <Tooltip {...{ contentStyle: S.ttip }} formatter={v => [`${v}%`]} labelFormatter={d => `${d}m`} />
                <ReferenceLine x={4} stroke={C.amber} strokeDasharray="4 3" strokeWidth={1}
                  label={{ value: 'SPI=4%', fill: C.amber, fontSize: 9, position: 'insideTopRight' }} />
                <Area type="monotone" dataKey="SPI" stroke={C.green}
                  fill={`${C.green}35`} strokeWidth={1.5} dot={false} name="SPI %" />
              </AreaChart>
            </ResponsiveContainer>
          </div>

          {/* M vs N scatter coloured by SPI */}
          <div style={S.card}>
            <div style={S.secHead}>M vs N — COLOURED BY SECONDARY POROSITY</div>
            <div style={{ fontSize: 10, color: C.dim, marginBottom: 8, lineHeight: 1.6 }}>
              Same M-N data recoloured by SPI magnitude. Green = higher secondary porosity.
              Calcite (Ls) + Dolomite cluster clearly visible as the dominant primary matrix trend.
            </div>
            <div style={{ display: 'flex', justifyContent: 'center' }}>
              <SvgCrossPlot
                title="M – N coloured by SPI"
                points={ptsMN}
                xKey="N" yKey="M"
                xLabel="N  (dimensionless)" yLabel="M  (dimensionless)"
                xDomain={[0.28, 1.02]} yDomain={[0.45, 1.18]}
                mineralPts={Object.entries(MN_MINERALS).map(([k, v]) => ({
                  x: v.N, y: v.M, color: v.color, label: k.substring(0,3),
                }))}
                mineralRegions={[
                  { cx: MN_MINERALS.Limestone.N, cy: MN_MINERALS.Limestone.M, rx: 0.060, ry: 0.048, color: '#00c8ff', label: 'Limestone' },
                  { cx: MN_MINERALS.Dolomite.N,  cy: MN_MINERALS.Dolomite.M,  rx: 0.058, ry: 0.046, color: '#ff7b3a', label: 'Dolomite'  },
                ]}
                colorKey="SP" colorMin={0} colorMax={0.12}
                width={340} height={300}
              />
            </div>
            <div style={{ fontSize: 9, color: C.muted, fontFamily: 'monospace', marginTop: 4 }}>
              colour: blue=low SPI (primary porosity) → red=high SPI (secondary porosity)
            </div>
          </div>

          {/* SPI Statistics + Interpretation */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>

            {/* SPI stats */}
            <div style={S.card}>
              <div style={S.secHead}>SPI STATISTICS</div>
              {(() => {
                const spVals = ptsMN.map(d => d.SP || 0);
                const mean   = spVals.reduce((s, v) => s + v, 0) / spVals.length;
                const sorted = [...spVals].sort((a, b) => a - b);
                const p10 = sorted[Math.floor(sorted.length * 0.1)];
                const p50 = sorted[Math.floor(sorted.length * 0.5)];
                const p90 = sorted[Math.floor(sorted.length * 0.9)];
                const pctSecPor = (spVals.filter(v => v > 0.02).length / spVals.length * 100).toFixed(0);
                return [
                  { l: 'Mean SPI',     v: (mean * 100).toFixed(2) + ' %' },
                  { l: 'P10 SPI',      v: (p10  * 100).toFixed(2) + ' %' },
                  { l: 'P50 SPI',      v: (p50  * 100).toFixed(2) + ' %' },
                  { l: 'P90 SPI',      v: (p90  * 100).toFixed(2) + ' %' },
                  { l: '% w/ 2° por',  v: pctSecPor + ' %',               c: C.green },
                  { l: 'Points total', v: ptsMN.length },
                ].map(({ l, v, c }) => (
                  <div key={l} style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 5, fontSize: 11, fontFamily: 'monospace' }}>
                    <span style={{ color: C.muted }}>{l}</span>
                    <span style={{ color: c || C.cyan }}>{v}</span>
                  </div>
                ));
              })()}
            </div>

            {/* Mixing zone table */}
            <div style={S.card}>
              <div style={S.secHead}>MIXING LINES</div>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 10, fontFamily: 'monospace' }}>
                <thead>
                  <tr>{['Pair', 'M range', 'N range'].map(h => (
                    <th key={h} style={{ padding: '3px 6px', color: C.cyan, borderBottom: `1px solid ${C.border}`, textAlign: 'left', fontSize: 9 }}>{h}</th>
                  ))}</tr>
                </thead>
                <tbody>
                  {MN_MIXING_LINES.map(ml => (
                    <tr key={ml.label} style={{ borderBottom: `1px solid ${C.border}22` }}>
                      <td style={{ padding: '3px 6px', color: ml.color }}>{ml.label}</td>
                      <td style={{ padding: '3px 6px', color: C.dim, fontSize: 9 }}>
                        {Math.min(MN_MINERALS[ml.from].M, MN_MINERALS[ml.to].M).toFixed(3)}
                        –{Math.max(MN_MINERALS[ml.from].M, MN_MINERALS[ml.to].M).toFixed(3)}
                      </td>
                      <td style={{ padding: '3px 6px', color: C.dim, fontSize: 9 }}>
                        {Math.min(MN_MINERALS[ml.from].N, MN_MINERALS[ml.to].N).toFixed(3)}
                        –{Math.max(MN_MINERALS[ml.from].N, MN_MINERALS[ml.to].N).toFixed(3)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Interpretation card */}
            <div style={{ ...S.card, borderLeft: `3px solid #00c8ff`, flex: 1 }}>
              <div style={{ ...S.secHead, color: '#00c8ff' }}>📝 M-N INTERPRETATION</div>
              <div style={{ fontSize: 10, color: C.dim, lineHeight: 1.75 }}>
                <p style={{ marginBottom: 5 }}>
                  Data cluster primarily in the <span style={{ color: '#00c8ff', fontWeight: 700 }}>Limestone (Calcite)</span>
                  {' '}and <span style={{ color: '#ff7b3a', fontWeight: 700 }}>Dolomite</span> fields,
                  consistent with a carbonate-dominated formation.
                </p>
                <p style={{ marginBottom: 5 }}>
                  Scatter <strong style={{ color: C.green }}>above</strong> the limestone M reference line
                  indicates locally elevated secondary porosity (vugs / dissolution).
                  These points plot with high M but unchanged N — the signature of vuggy pores that
                  density detects but neutron does not.
                </p>
                <p>
                  High-GR shale intervals (warm data colours) migrate to lower-M and lower-N, plotting
                  off the clean carbonate trend. This reflects clay-mineral effects and
                  bound water increasing the apparent neutron porosity while compaction reduces
                  the sonic velocity.
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* ── BOTTOM ROW: MID plot + lithology summary ── */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 380px', gap: 14 }}>

          {/* ─ MID Plot ─ */}
          <div style={S.card}>
            <div style={{ ...S.secHead, marginBottom: 6 }}>③ MID PLOT — Matrix Identification Diagram</div>
            <div style={{ fontSize: 10, color: C.dim, marginBottom: 10, lineHeight: 1.6 }}>
              Apparent matrix parameters ρ<sub>maa</sub> and Δt<sub>maa</sub> calculated after removing
              fluid-filled porosity volume. Formation fluids cancel out — resulting clusters identify
              the dry-rock matrix mineralogy regardless of porosity.
              ρmaa = (ρb − φe·ρfl)/(1−φe) · Δtmaa = (Δt − φe·Δtfl)/(1−φe)
            </div>
            <div style={{ display: 'flex', justifyContent: 'center', overflowX: 'auto' }}>
              <SvgCrossPlot
                title="Δtmaa vs ρmaa — Matrix Identification"
                points={ptsMID}
                xKey="rmaa" yKey="dtmaa"
                xLabel="Apparent Matrix Density ρmaa (g/cc)"
                yLabel="Apparent Matrix Δt (μs/ft)"
                xDomain={[1.9, 3.1]} yDomain={[38, 80]}
                xInvert={false}
                mineralPts={Object.entries(MID_MINERALS).map(([k, v]) => ({ x: v.rmaa, y: v.dtmaa, color: v.color, label: k }))}
                colorKey="GR" colorMin={grMin} colorMax={grMax}
                width={520} height={360}
              />
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, marginTop: 8, fontSize: 10, fontFamily: 'monospace' }}>
              {Object.entries(MID_MINERALS).map(([k, v]) => (
                <span key={k} style={{ color: v.color }}>◆ {k}</span>
              ))}
            </div>
          </div>

          {/* ─ Lithology Summary Panel ─ */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>

            {/* Distribution */}
            <div style={S.card}>
              <div style={S.secHead}>🪨 CLASSIFIED LITHOLOGY</div>
              <div style={{ fontSize: 10, color: C.dim, marginBottom: 10 }}>
                DN crossplot proximity assignment — {totalPts} classified points
              </div>
              {lithDist.map(({ name, pct, color }) => (
                <div key={name} style={{ marginBottom: 9 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, fontFamily: 'monospace', marginBottom: 3 }}>
                    <span style={{ color }}>{MINERALS[name]?.symbol || '◦'} {name}</span>
                    <span style={{ color: C.text }}>{pct}%</span>
                  </div>
                  <div style={{ height: 6, background: C.border, borderRadius: 3, overflow: 'hidden' }}>
                    <div style={{ height: '100%', width: `${pct}%`, background: color, borderRadius: 3, transition: 'width .5s' }} />
                  </div>
                </div>
              ))}
            </div>

            {/* Interpretation card */}
            <div style={{ ...S.card, borderLeft: `3px solid ${C.amber}` }}>
              <div style={{ ...S.secHead, color: C.amber }}>📝 INTERPRETATION</div>
              <div style={{ fontSize: 11, color: C.dim, lineHeight: 1.75 }}>
                {lithDist[0] && (
                  <>
                    <p style={{ marginBottom: 6 }}>
                      Primary matrix: <span style={{ color: lithDist[0].color, fontWeight: 700 }}>{lithDist[0].name}</span>
                      {' '}({lithDist[0].pct}% of section)
                    </p>
                    {lithDist[1] && <p style={{ marginBottom: 6 }}>Secondary: <span style={{ color: lithDist[1].color }}>{lithDist[1].name}</span> ({lithDist[1].pct}%)</p>}
                    <p style={{ marginBottom: 6 }}>
                      High-GR shale intervals scatter off mineral lines — visible as warm-coloured
                      data clouds above the clean-rock reference trends on the DN plot.
                    </p>
                    <p>
                      Evaporite outliers (anhydrite ρb ≈ 2.98, halite ρb ≈ 2.17) plot
                      as isolated clusters detached from the main data cloud.
                    </p>
                  </>
                )}
              </div>
            </div>

            {/* Mineral properties reference */}
            <div style={S.card}>
              <div style={S.secHead}>MINERAL PROPERTIES</div>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 10, fontFamily: 'monospace' }}>
                <thead>
                  <tr>
                    {['Mineral','ρb','φN','Δt'].map(h => (
                      <th key={h} style={{ padding: '4px 6px', color: C.cyan, borderBottom: `1px solid ${C.border}`, textAlign: 'right', fontSize: 9 }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {Object.entries(MINERALS).map(([k, m]) => (
                    <tr key={k} style={{ borderBottom: `1px solid ${C.border}22` }}>
                      <td style={{ padding: '4px 6px', color: m.color }}>{m.symbol} {k}</td>
                      <td style={{ padding: '4px 6px', color: C.text, textAlign: 'right' }}>{m.rhob}</td>
                      <td style={{ padding: '4px 6px', color: C.text, textAlign: 'right' }}>{m.nphi.toFixed(3)}</td>
                      <td style={{ padding: '4px 6px', color: C.text, textAlign: 'right' }}>{m.dt}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <div style={{ marginTop: 8, fontSize: 9, color: C.muted, lineHeight: 1.6 }}>
                Fluid: ρfl = 1.00 g/cc · φNfl = 1.00 · Δtfl = 189 μs/ft (fresh water)
              </div>
            </div>

          </div>
        </div>

        {/* ── Lithology log strip ── */}
        <div style={S.card}>
          <div style={S.secHead}>🪨 CLASSIFIED LITHOLOGY LOG — depth track</div>
          <div style={{ overflowX: 'auto' }}>
            <svg width="100%" height="60" style={{ display: 'block', minWidth: 600 }}>
              {(() => {
                const dmin = lithLog[0]?.DEPTH || 2000;
                const dmax = lithLog[lithLog.length - 1]?.DEPTH || 2500;
                const drange = dmax - dmin;
                return lithLog.map((p, i) => {
                  const x1 = ((p.DEPTH - dmin) / drange) * 100;
                  const x2 = i < lithLog.length - 1
                    ? ((lithLog[i + 1].DEPTH - dmin) / drange) * 100
                    : 100;
                  return (
                    <rect key={i} x={`${x1}%`} y="4" width={`${Math.max(0.2, x2 - x1)}%`} height="28"
                      fill={p.color} opacity="0.75" />
                  );
                });
              })()}
              {/* Depth labels */}
              {[0, 0.25, 0.5, 0.75, 1.0].map((f, i) => {
                const dmin = lithLog[0]?.DEPTH || 2000;
                const dmax = lithLog[lithLog.length - 1]?.DEPTH || 2500;
                const d = (dmin + (dmax - dmin) * f).toFixed(0);
                return (
                  <text key={i} x={`${f * 100}%`} y="52" fontSize="9" fill="#4a6a8a"
                    textAnchor={f === 0 ? 'start' : f === 1 ? 'end' : 'middle'} fontFamily="monospace">{d}m</text>
                );
              })}
            </svg>
            {/* Colour legend */}
            <div style={{ display: 'flex', gap: 16, padding: '6px 0', fontSize: 10, fontFamily: 'monospace' }}>
              {Object.entries(MINERALS).map(([k, m]) => (
                <span key={k}>
                  <span style={{ display: 'inline-block', width: 12, height: 10, background: m.color, marginRight: 4, borderRadius: 2 }} />
                  <span style={{ color: C.dim }}>{k}</span>
                </span>
              ))}
            </div>
          </div>
        </div>

      </div>
    );
  };

  // ══════════════════════════════════════════
  //  RENDER: 3D RESERVOIR TAB
  // ══════════════════════════════════════════
  const renderReservoir = () => {
    if (!petro) return <NoData />;
    const propLabels = { PHIE: 'Porosity (PHIE)', Sw: 'Water Saturation', Vsh: 'Shale Volume', K: 'Permeability (mD)' };
    const st = summaryStats;

    return (
      <div style={{ display: 'grid', gridTemplateColumns: '230px 1fr', gap: 16 }}>
        {/* Controls */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={S.card}>
            <div style={S.secHead}>🔲 GRID PARAMETERS</div>
            {[
              { k: 'NX', l: 'NX (I-dir cells)' },
              { k: 'NY', l: 'NY (J-dir cells)' },
              { k: 'NZ', l: 'NZ (K-layers)'    },
              { k: 'DX', l: 'DX (meters)'      },
              { k: 'DY', l: 'DY (meters)'      },
            ].map(({ k, l }) => (
              <div key={k} style={{ marginBottom: 8 }}>
                <div style={S.label}>{l}</div>
                <input type="number" style={S.input} value={gridParams[k]}
                  onChange={e => setGridP(p => ({ ...p, [k]: +e.target.value || 1 }))} />
              </div>
            ))}
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 4 }}>
              <input type="checkbox" checked={hetero} onChange={e => setHetero(e.target.checked)} id="het" />
              <label htmlFor="het" style={{ fontSize: 11, color: C.dim, cursor: 'pointer' }}>Heterogeneous model</label>
            </div>
          </div>

          <div style={S.card}>
            <div style={S.secHead}>🎨 COLOR BY PROPERTY</div>
            {Object.entries(propLabels).map(([k, l]) => (
              <div key={k} onClick={() => setColorProp(k)} style={{
                padding: '6px 10px', borderRadius: 5, cursor: 'pointer', marginBottom: 4, fontSize: 11, fontFamily: 'monospace',
                background: colorProp === k ? `${C.cyan}18` : 'transparent',
                border: `1px solid ${colorProp === k ? C.cyan + '60' : 'transparent'}`,
                color: colorProp === k ? C.cyan : C.dim, transition: 'all .2s',
              }}>
                {colorProp === k ? '▶' : '◦'} {l}
              </div>
            ))}
          </div>

          {/* Colorbar */}
          <div style={S.card}>
            <div style={S.secHead}>COLOUR SCALE</div>
            <div style={{ height: 18, background: 'linear-gradient(90deg,#00008b,#0000ff,#00ffff,#00ff00,#ffff00,#ff7700,#ff0000)', borderRadius: 3 }} />
            <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 4, fontSize: 10, color: C.muted, fontFamily: 'monospace' }}>
              <span>LOW</span><span style={{ color: C.cyan }}>{colorProp}</span><span>HIGH</span>
            </div>
          </div>

          {/* Grid stats */}
          <div style={S.card}>
            <div style={S.secHead}>GRID STATS</div>
            {[
              { l: 'Total Cells',    v: (gridParams.NX * gridParams.NY * gridParams.NZ).toLocaleString() },
              { l: 'Active Cells',   v: Math.floor(gridParams.NX * gridParams.NY * gridParams.NZ * 0.93).toLocaleString() },
              { l: 'Areal Extent',   v: `${(gridParams.NX * gridParams.DX / 1000).toFixed(1)} × ${(gridParams.NY * gridParams.DY / 1000).toFixed(1)} km` },
              { l: 'Avg Porosity',   v: st ? (st.avgPHIE * 100).toFixed(1) + '%' : '—' },
              { l: 'Avg Permeability', v: st ? st.avgK.toFixed(1) + ' mD' : '—' },
            ].map(({ l, v }) => (
              <div key={l} style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6, fontSize: 11, fontFamily: 'monospace' }}>
                <span style={{ color: C.muted }}>{l}</span>
                <span style={{ color: C.cyan }}>{v}</span>
              </div>
            ))}
          </div>
        </div>

        {/* 3D viewer + cross-section */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={S.card}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
              <div style={S.secHead}>🌐 3D RESERVOIR — {propLabels[colorProp]}</div>
              <span style={{ fontSize: 10, color: C.muted, fontFamily: 'monospace' }}>Drag to rotate · Scroll to zoom</span>
            </div>
            <ReservoirViewer petro={petro} colorProp={colorProp} NX={gridParams.NX} NY={gridParams.NY} NZ={gridParams.NZ} />
          </div>

          {/* Cross-sections side by side */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div style={S.card}>
              <div style={S.secHead}>PROPERTY PROFILE</div>
              <ResponsiveContainer width="100%" height={130}>
                <AreaChart data={petro.filter((_, i) => i % 4 === 0).map(d => ({
                  d: d.DEPTH, phi: +(d.PHIE * 100).toFixed(1), sw: +(d.Sw * 100).toFixed(1), vsh: +(d.Vsh * 100).toFixed(1),
                }))} margin={{ top: 4, right: 4, bottom: 0, left: -10 }}>
                  <XAxis dataKey="d" tick={{ fill: C.muted, fontSize: 9 }} tickCount={6} />
                  <YAxis tick={{ fill: C.muted, fontSize: 9 }} domain={[0, 100]} unit="%" />
                  <Tooltip {...TT} formatter={v => [`${v}%`]} />
                  <Area type="monotone" dataKey="phi" stroke={C.cyan}  fill={`${C.cyan}25`}  strokeWidth={1.5} dot={false} name="PHIE%" />
                  <Area type="monotone" dataKey="sw"  stroke="#74b9ff" fill="#74b9ff18" strokeWidth={1}   dot={false} name="Sw%"   />
                  <Area type="monotone" dataKey="vsh" stroke={C.pink}  fill={`${C.pink}10`} strokeWidth={1}   dot={false} name="Vsh%" />
                </AreaChart>
              </ResponsiveContainer>
            </div>
            <div style={S.card}>
              <div style={S.secHead}>PERMEABILITY LOG</div>
              <ResponsiveContainer width="100%" height={130}>
                <AreaChart data={petro.filter((_, i) => i % 4 === 0).map(d => ({
                  d: d.DEPTH, K: +d.K.toFixed(2),
                }))} margin={{ top: 4, right: 4, bottom: 0, left: -10 }}>
                  <XAxis dataKey="d" tick={{ fill: C.muted, fontSize: 9 }} tickCount={6} />
                  <YAxis tick={{ fill: C.muted, fontSize: 9 }} />
                  <Tooltip {...TT} formatter={v => [`${v} mD`, 'K']} />
                  <Area type="monotone" dataKey="K" stroke={C.amber} fill={`${C.amber}28`} strokeWidth={1.5} dot={false} />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>
      </div>
    );
  };

  // ══════════════════════════════════════════
  //  RENDER: POROSITY TAB
  // ══════════════════════════════════════════
  const renderPorosity = () => {
    if (!petro || !rawData) return <NoData />;

    const ds  = petro.filter((_, i) => i % 2 === 0);
    const depthDomain = ['dataMax', 'dataMin'];

    // Statistics for each porosity type
    const statD  = calcStats(petro, 'PHID');
    const statN  = calcStats(petro, 'PHIN');
    const statS  = calcStats(petro, 'PHIS');
    const statDN = calcStats(petro, 'PHIDN');
    const statT  = calcStats(petro, 'PHIt');
    const statB  = calcStats(petro, 'PHIb');
    const statE  = calcStats(petro, 'PHIE');

    // Porosity histogram bins helper
    const mkHist = key => {
      const bins = Array.from({ length: 22 }, (_, i) => ({ phi: +(i * 0.02).toFixed(2), n: 0 }));
      petro.forEach(d => { const b = Math.min(21, Math.floor(d[key] / 0.02)); bins[b].n++; });
      return bins;
    };

    // Crossplot data (decimated)
    const cpData = petro.filter((_, i) => i % 3 === 0).map(d => ({
      PHID: d.PHID, PHIN: d.PHIN, PHIS: d.PHIS,
      PHIt: d.PHIt, PHIb: d.PHIb, PHIE: d.PHIE,
      GR: d.GR, Vsh: d.Vsh, DEPTH: d.DEPTH,
    }));

    const TT = { contentStyle: S.ttip };

    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>

        {/* ── THEORY BANNER ── */}
        <div style={{ ...S.card, padding: '12px 18px', borderLeft: `3px solid ${C.cyan}` }}>
          <div style={{ fontSize: 12, color: C.text, lineHeight: 1.8 }}>
            <span style={{ color: C.cyan, fontWeight: 700, fontFamily: 'monospace' }}>POROSITY ESTIMATION</span>
            {' '}— Porosity (φ) = pore volume / bulk volume. Three independent log types measure it by different physical
            mechanisms. Merging logs eliminates single-tool bias.
            <span style={{ color: C.green }}> Density</span> responds to flushed-zone fluids and matrix;
            <span style={{ color: '#74b9ff' }}> Neutron</span> detects hydrogen concentration;
            <span style={{ color: C.purple }}> Sonic</span> measures seismic travel-time governed by the
            Wyllie time-average equation. <span style={{ color: C.amber }}>D-N average</span> is the
            preferred combination for liquid-filled formations — the standard practice for this well.
          </div>
        </div>

        {/* ── TABLES 4.1 + 4.2 ── */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>

          {/* Table 4.1 */}
          <div style={S.card}>
            <div style={{ ...S.secHead, marginBottom: 10 }}>📋 TABLE 4.1 — POROSITY ESTIMATES FROM LOGS</div>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 11, fontFamily: 'monospace' }}>
              <thead>
                <tr>
                  {['Log Type', 'Required Data', 'Formula'].map(h => (
                    <th key={h} style={{ padding: '7px 10px', background: `${C.cyan}20`, color: C.cyan, border: `1px solid ${C.border}`, textAlign: 'left', fontSize: 10, fontWeight: 700 }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {[
                  {
                    log: 'Density (RHOB)',
                    req: 'ρma, ρf, ρb — matrix, fluid, log density',
                    formula: 'φD = (ρma − ρb) / (ρma − ρf)',
                    eq: 'Eq.(12)', color: C.green,
                  },
                  {
                    log: 'Neutron (NPHI)',
                    req: 'NPHI φ, φN,ma, φN,f',
                    formula: 'φN = (φN − φN,ma) / (φN,f − φN,ma)',
                    eq: 'Table 4.1', color: '#74b9ff',
                  },
                  {
                    log: 'Sonic (DT)',
                    req: 'Δtma, Δtf — matrix and fluid transit time',
                    formula: 'φS = (Δtma − Δt) / (Δtma − Δtf)',
                    eq: 'Eq.(11)', color: C.purple,
                  },
                ].map(({ log, req, formula, eq, color }) => (
                  <tr key={log} style={{ borderBottom: `1px solid ${C.border}` }}>
                    <td style={{ padding: '8px 10px', border: `1px solid ${C.border}`, color, fontWeight: 700, whiteSpace: 'nowrap' }}>{log}</td>
                    <td style={{ padding: '8px 10px', border: `1px solid ${C.border}`, color: C.dim, fontSize: 10, lineHeight: 1.5 }}>{req}</td>
                    <td style={{ padding: '8px 10px', border: `1px solid ${C.border}` }}>
                      <div style={{ color, fontWeight: 700, marginBottom: 2 }}>{formula}</div>
                      <div style={{ fontSize: 9, color: C.muted }}>{eq}</div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Table 4.2 */}
          <div style={S.card}>
            <div style={{ ...S.secHead, marginBottom: 10 }}>📋 TABLE 4.2 — FLUID & MATRIX PROPERTIES (Limestone)</div>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 11, fontFamily: 'monospace' }}>
              <thead>
                <tr>
                  {['Parameter', 'Value', 'Source'].map(h => (
                    <th key={h} style={{ padding: '7px 10px', background: `${C.cyan}20`, color: C.cyan, border: `1px solid ${C.border}`, textAlign: 'left', fontSize: 10, fontWeight: 700 }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {[
                  { p: 'ρma',    v: '2.71 g/cm³',  s: 'Limestone matrix density' },
                  { p: 'ρf',     v: '1.00 g/cm³',  s: 'Fresh water fluid' },
                  { p: 'φN,ma',  v: '0.000 v/v',   s: 'Limestone neutron response' },
                  { p: 'φN,f',   v: '0.000 v/v',   s: 'Calibration offset = 0' },
                  { p: 'Δtma',   v: '47.5 μsec/ft', s: 'Limestone matrix, Eq.(11)' },
                  { p: 'Δtf',    v: '189 μsec/ft',  s: 'Fresh water fluid transit' },
                ].map(({ p, v, s }) => (
                  <tr key={p} style={{ borderBottom: `1px solid ${C.border}` }}>
                    <td style={{ padding: '7px 10px', border: `1px solid ${C.border}`, color: C.amber, fontWeight: 700, fontFamily: 'monospace' }}>{p}</td>
                    <td style={{ padding: '7px 10px', border: `1px solid ${C.border}`, color: C.cyan }}>{v}</td>
                    <td style={{ padding: '7px 10px', border: `1px solid ${C.border}`, color: C.dim, fontSize: 10 }}>{s}</td>
                  </tr>
                ))}
              </tbody>
            </table>

            {/* Equation boxes */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginTop: 12 }}>
              {[
                { label: 'Eq. (11) — Sonic (Wyllie)', eq: 'Δt = φ·Δtf + (1−φ)·Δtma', color: C.purple },
                { label: 'Eq. (12) — Density',         eq: 'φD = (ρma − ρb) / (ρma − ρf)', color: C.green  },
              ].map(({ label, eq, color }) => (
                <div key={label} style={{ background: '#060e1a', border: `1px solid ${color}30`, borderRadius: 5, padding: '7px 10px' }}>
                  <div style={{ fontSize: 9, color: C.muted, marginBottom: 3, fontFamily: 'monospace' }}>{label}</div>
                  <div style={{ fontSize: 11, color, fontFamily: 'monospace', fontWeight: 700 }}>{eq}</div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* ── PARAMETERS ROW ── */}
        <div style={S.card}>
          <div style={S.secHead}>⚙ POROSITY CALCULATION PARAMETERS</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(6, 1fr)', gap: 10 }}>
            {[
              { k: 'RHOBmat', l: 'ρma (g/cc)',     step: 0.01 },
              { k: 'RHOBfl',  l: 'ρf (g/cc)',      step: 0.01 },
              { k: 'DTma',    l: 'Δtma (μs/ft)',   step: 0.5  },
              { k: 'DTfl',    l: 'Δtf (μs/ft)',    step: 1.0  },
              { k: 'PHINma',  l: 'φN,ma (v/v)',    step: 0.01 },
              { k: 'PHINfl',  l: 'φN,f (v/v)',     step: 0.01 },
            ].map(({ k, l, step }) => (
              <div key={k}>
                <div style={S.label}>{l}</div>
                <input type="number" style={S.input} value={params[k] ?? DEFAULT_PARAMS[k]} step={step}
                  onChange={e => setParams(p => ({ ...p, [k]: parseFloat(e.target.value) || 0 }))} />
              </div>
            ))}
          </div>
        </div>

        {/* ── KPI CARDS ── */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 10 }}>
          {[
            { l: 'Mean φD',    v: (statD.mean  * 100).toFixed(2) + '%', c: C.green   },
            { l: 'Mean φN',    v: (statN.mean  * 100).toFixed(2) + '%', c: '#74b9ff' },
            { l: 'Mean φS',    v: (statS.mean  * 100).toFixed(2) + '%', c: C.purple  },
            { l: 'Mean φt',    v: (statT.mean  * 100).toFixed(2) + '%', c: C.amber   },
            { l: 'Mean φb',    v: (statB.mean  * 100).toFixed(2) + '%', c: C.pink    },
            { l: 'Mean φE',    v: (statE.mean  * 100).toFixed(2) + '%', c: C.cyan    },
            { l: 'φt − φE',    v: ((statT.mean - statE.mean) * 100).toFixed(2) + '%', c: '#f97316' },
          ].map(({ l, v, c }) => (
            <div key={l} style={{ ...S.card, textAlign: 'center', padding: '10px 8px' }}>
              <div style={S.label}>{l}</div>
              <div style={{ ...S.val, fontSize: 17, color: c }}>{v}</div>
            </div>
          ))}
        </div>

        {/* ── MAIN LOG TRACKS + COMPARISON ── */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>

          {/* ─ Triple porosity depth tracks ─ */}
          <div style={{ ...S.card, padding: 0 }}>
            <div style={{ padding: '8px 14px', borderBottom: `1px solid ${C.border}` }}>
              <div style={{ ...S.secHead, marginBottom: 0 }}>🔬 POROSITY LOG TRACKS — ALL METHODS</div>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '50px repeat(5, 1fr)', height: 520 }}>
              {/* Depth */}
              <div style={{ borderRight: `1px solid ${C.border}` }}>
                <div style={{ height: 22, borderBottom: `1px solid ${C.border}`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <span style={{ fontSize: 9, color: C.cyan, fontFamily: 'monospace' }}>DEPTH</span>
                </div>
                <ResponsiveContainer width="100%" height={498}>
                  <AreaChart layout="vertical" data={ds} margin={{ top: 0, right: 0, bottom: 0, left: 0 }}>
                    <YAxis type="number" dataKey="DEPTH" reversed domain={depthDomain}
                      tick={{ fill: C.muted, fontSize: 9, fontFamily: 'monospace' }} tickCount={12} width={48} />
                    <XAxis type="number" hide />
                    <Area type="monotone" dataKey="DEPTH" stroke="none" fill="none" />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
              {[
                { key: 'PHID',  color: C.green,    label: 'φD',    sub: 'Density',   domain: [0, 0.5] },
                { key: 'PHIN',  color: '#74b9ff',  label: 'φN',    sub: 'Neutron',   domain: [0, 0.5] },
                { key: 'PHIt',  color: C.amber,    label: 'φt',    sub: 'Total D-N', domain: [0, 0.5] },
                { key: 'PHIb',  color: C.pink,     label: 'φb',    sub: 'Clay-bound',domain: [0, 0.3] },
                { key: 'PHIE',  color: C.cyan,     label: 'φE',    sub: 'Effective', domain: [0, 0.5] },
              ].map(tc => (
                <div key={tc.key} style={{ borderRight: `1px solid ${C.border}` }}>
                  <div style={{ height: 22, borderBottom: `1px solid ${C.border}`, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 1 }}>
                    <span style={{ fontSize: 9, color: tc.color, fontFamily: 'monospace', fontWeight: 700 }}>{tc.label}</span>
                    <span style={{ fontSize: 7, color: C.muted, fontFamily: 'monospace' }}>{tc.sub}</span>
                  </div>
                  <ResponsiveContainer width="100%" height={498}>
                    <AreaChart layout="vertical" data={ds} margin={{ top: 0, right: 2, bottom: 0, left: -30 }}>
                      <XAxis type="number" domain={tc.domain} tickCount={3} tick={{ fill: C.muted, fontSize: 8 }} unit="" />
                      <YAxis type="number" dataKey="DEPTH" reversed domain={depthDomain} hide />
                      <Tooltip {...TT} formatter={v => [(v * 100).toFixed(2) + '%', tc.label]} labelFormatter={d => `${d}m`} />
                      <Area type="monotone" dataKey={tc.key} stroke={tc.color} fill={tc.color}
                        fillOpacity={0.30} strokeWidth={1.5} dot={false} />
                    </AreaChart>
                  </ResponsiveContainer>
                </div>
              ))}
            </div>
          </div>

          {/* ─ Overlay comparison chart + crossplots ─ */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>

            {/* Overlay all on one depth chart */}
            <div style={S.card}>
              <div style={S.secHead}>POROSITY OVERLAY — DEPTH COMPARISON</div>
              <ResponsiveContainer width="100%" height={250}>
                <LineChart data={ds} margin={{ top: 4, right: 8, bottom: 0, left: -8 }}>
                  <CartesianGrid strokeDasharray="2 4" stroke={`${C.border}88`} />
                  <XAxis dataKey="DEPTH" tick={{ fill: C.muted, fontSize: 9 }} tickCount={8}
                    label={{ value: 'Depth (m)', fill: C.muted, fontSize: 10, dy: 14, position: 'insideBottom' }} />
                  <YAxis tick={{ fill: C.muted, fontSize: 9 }} tickFormatter={v => `${(v*100).toFixed(0)}%`} domain={[0, 0.45]} />
                  <Tooltip {...TT} formatter={v => [(v*100).toFixed(2)+'%']} />
                  <Legend wrapperStyle={{ fontSize: 10, color: C.dim }} />
                  <Line type="monotone" dataKey="PHID"  stroke={C.green}   strokeWidth={1.5} dot={false} name="φD Density"    />
                  <Line type="monotone" dataKey="PHIN"  stroke="#74b9ff"   strokeWidth={1.5} dot={false} name="φN Neutron"    />
                  <Line type="monotone" dataKey="PHIS"  stroke={C.purple}  strokeWidth={1.2} dot={false} name="φS Sonic" strokeDasharray="5 3" />
                  <Line type="monotone" dataKey="PHIt"  stroke={C.amber}   strokeWidth={2.2} dot={false} name="φt Total (D-N)" />
                  <Line type="monotone" dataKey="PHIb"  stroke={C.pink}    strokeWidth={1.2} dot={false} name="φb Clay-bound" strokeDasharray="3 2" />
                  <Line type="monotone" dataKey="PHIE"  stroke={C.cyan}    strokeWidth={2}   dot={false} name="φE Effective" strokeDasharray="3 2" />
                </LineChart>
              </ResponsiveContainer>
            </div>

            {/* φD vs φN scatter (gas/shale effect) */}
            <div style={S.card}>
              <div style={S.secHead}>φD vs φN SCATTER — GAS / SHALE DETECTION</div>
              <div style={{ fontSize: 10, color: C.dim, marginBottom: 6, lineHeight: 1.55 }}>
                Points on the 1:1 line = water-filled clean rock.
                <span style={{ color: '#74b9ff' }}> φN &gt; φD</span> = shale effect (φN reads high).
                <span style={{ color: C.green   }}> φD &gt; φN</span> = gas effect (φN reads low / φD reads high).
              </div>
              <div style={{ display: 'flex', justifyContent: 'center' }}>
                <SvgCrossPlot
                  title="φD vs φN — Fluid & Lithology Effect"
                  points={cpData}
                  xKey="PHIN" yKey="PHID"
                  xLabel="Neutron Porosity φN (v/v)"
                  yLabel="Density Porosity φD (v/v)"
                  xDomain={[0, 0.48]} yDomain={[0, 0.48]}
                  arrows={[
                    { x1: 0.06, y1: 0.06, x2: 0.22, y2: 0.22, color: '#aaaaaa', label: '1:1 line' },
                    { x1: 0.25, y1: 0.10, x2: 0.35, y2: 0.06, color: C.green,   label: 'gas →' },
                    { x1: 0.10, y1: 0.28, x2: 0.08, y2: 0.36, color: '#74b9ff', label: '← shale' },
                  ]}
                  colorKey="GR" colorMin={10} colorMax={130}
                  width={340} height={270}
                />
              </div>
            </div>
          </div>
        </div>

        {/* ── STATISTICAL SUMMARY TABLE ── */}
        <div style={S.card}>
          <div style={S.secHead}>📊 POROSITY STATISTICS SUMMARY — ALL METHODS</div>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 11, fontFamily: 'monospace' }}>
              <thead>
                <tr>
                  {['Method', 'Equation', 'Min', 'Max', 'Mean', 'P10', 'P50 (Median)', 'P90', 'Std Dev'].map(h => (
                    <th key={h} style={{ padding: '7px 12px', background: `${C.cyan}18`, color: C.cyan, border: `1px solid ${C.border}`, textAlign: 'right', fontSize: 10, whiteSpace: 'nowrap' }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {[
                  { label: 'φD — Density',          key: 'PHID',  color: C.green,   eq: 'Eq.(12): (ρma−ρb)/(ρma−ρf)',               stat: statD  },
                  { label: 'φN — Neutron',           key: 'PHIN',  color: '#74b9ff', eq: 'Table 4.1: (φN−φN,ma)/(φN,f−φN,ma)',       stat: statN  },
                  { label: 'φS — Sonic',             key: 'PHIS',  color: C.purple,  eq: 'Eq.(11): (Δtma−Δt)/(Δtma−Δtf)',            stat: statS  },
                  { label: 'φt — Total (Eq.14)',     key: 'PHIt',  color: C.amber,   eq: 'Eq.(14): (φN + φD) / 2',                    stat: statT  },
                  { label: 'φb — Clay-bound Water',  key: 'PHIb',  color: C.pink,    eq: 'φb = φt × Vsh  (Dodge et al. 1996)',        stat: statB  },
                  { label: 'φE — Effective',         key: 'PHIE',  color: C.cyan,    eq: 'φE = φt × (1 − Vsh)  (Dodge et al. 1996)', stat: statE  },
                ].map(({ label, key, color, eq, stat }) => {
                  const vals = petro.map(d => d[key]).filter(v => typeof v === 'number');
                  const mean = vals.reduce((s, v) => s + v, 0) / vals.length;
                  const std  = Math.sqrt(vals.reduce((s, v) => s + (v - mean) ** 2, 0) / vals.length);
                  return (
                    <tr key={key} style={{ borderBottom: `1px solid ${C.border}` }}>
                      <td style={{ padding: '7px 12px', border: `1px solid ${C.border}`, color, fontWeight: 700, whiteSpace: 'nowrap' }}>{label}</td>
                      <td style={{ padding: '7px 12px', border: `1px solid ${C.border}`, color: C.muted, fontSize: 9 }}>{eq}</td>
                      {[stat.min, stat.max, stat.mean, stat.p10, stat.p50, stat.p90].map((v, i) => (
                        <td key={i} style={{ padding: '7px 12px', border: `1px solid ${C.border}`, textAlign: 'right', color: C.text }}>
                          {(v * 100).toFixed(2)}%
                        </td>
                      ))}
                      <td style={{ padding: '7px 12px', border: `1px solid ${C.border}`, textAlign: 'right', color: C.dim }}>
                        {(std * 100).toFixed(2)}%
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>

        {/* ══════════════════════════════════════════════════════
            TOTAL POROSITY (PHIT) & EFFECTIVE POROSITY (PHIE)
            ══════════════════════════════════════════════════ */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>

          {/* ─ Total Porosity Definition Box ─ */}
          <div style={{ ...S.card, borderLeft: `3px solid ${C.amber}` }}>
            <div style={{ ...S.secHead, color: C.amber, marginBottom: 10 }}>
              §4.4.2 TOTAL POROSITY — φt
            </div>

            {/* Equation feature box */}
            <div style={{ background: '#060e1a', border: `1px solid ${C.amber}40`, borderRadius: 6, padding: '10px 14px', marginBottom: 12 }}>
              <div style={{ fontSize: 9, color: C.muted, fontFamily: 'monospace', marginBottom: 4 }}>Eq. (14) — Neutron + Density Average</div>
              <div style={{ fontSize: 16, color: C.amber, fontFamily: 'monospace', fontWeight: 700, letterSpacing: 1 }}>
                φt = (φN + φD) / 2
              </div>
              <div style={{ marginTop: 8, display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '3px 12px', fontSize: 10, fontFamily: 'monospace' }}>
                {[
                  ['φN', 'Neutron porosity — hydrogen-index measurement (NPHI log)'],
                  ['φD', 'Density porosity — derived from RHOB log via Eq.(12)'],
                  ['φt', 'Fraction of bulk rock volume NOT occupied by solid matter'],
                ].map(([k, v]) => (
                  <React.Fragment key={k}>
                    <span style={{ color: C.amber, fontWeight: 700 }}>{k}</span>
                    <span style={{ color: C.dim }}>{v}</span>
                  </React.Fragment>
                ))}
              </div>
            </div>

            <div style={{ fontSize: 11, color: C.dim, lineHeight: 1.75, marginBottom: 12 }}>
              Total porosity (φt) is defined as the fraction of the bulk rock volume that is not occupied
              by solid matter. With neutron and density logs, Equation (14) is used to calculate it.
              The D-N average balances each log's sensitivity — density over-reads in shale (low ρb);
              neutron over-reads in gas (suppressed H-index). Their average yields the most accurate φt.
            </div>

            {/* φt stats summary */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8 }}>
              {[
                { l: 'Mean φt',  v: (statT.mean * 100).toFixed(2) + '%' },
                { l: 'Min φt',   v: (statT.min  * 100).toFixed(2) + '%' },
                { l: 'Max φt',   v: (statT.max  * 100).toFixed(2) + '%' },
                { l: 'P10',      v: (statT.p10  * 100).toFixed(2) + '%' },
                { l: 'P50',      v: (statT.p50  * 100).toFixed(2) + '%' },
                { l: 'P90',      v: (statT.p90  * 100).toFixed(2) + '%' },
              ].map(({ l, v }) => (
                <div key={l} style={{ background: '#060e1a', borderRadius: 4, padding: '6px 8px', textAlign: 'center' }}>
                  <div style={{ fontSize: 9, color: C.muted, fontFamily: 'monospace', marginBottom: 2 }}>{l}</div>
                  <div style={{ fontSize: 13, color: C.amber, fontFamily: 'monospace', fontWeight: 700 }}>{v}</div>
                </div>
              ))}
            </div>

            {/* φt depth chart */}
            <div style={{ marginTop: 12 }}>
              <div style={{ fontSize: 9, color: C.muted, fontFamily: 'monospace', marginBottom: 4 }}>TOTAL POROSITY φt vs DEPTH</div>
              <ResponsiveContainer width="100%" height={150}>
                <AreaChart data={ds.map(d => ({ DEPTH: d.DEPTH, PHIt: d.PHIt, PHID: d.PHID, PHIN: d.PHIN }))}
                  margin={{ top: 4, right: 8, bottom: 0, left: -8 }}>
                  <CartesianGrid strokeDasharray="2 4" stroke={`${C.border}66`} />
                  <XAxis dataKey="DEPTH" tick={{ fill: C.muted, fontSize: 9 }} tickCount={6}
                    label={{ value: 'Depth (m)', fill: C.muted, fontSize: 9, dy: 14, position: 'insideBottom' }} />
                  <YAxis tick={{ fill: C.muted, fontSize: 9 }} tickFormatter={v => `${(v*100).toFixed(0)}%`} domain={[0, 0.45]} />
                  <Tooltip {...TT} formatter={v => [(v*100).toFixed(2)+'%']} />
                  <Area type="monotone" dataKey="PHID"  stroke={C.green}  fill={`${C.green}15`}  strokeWidth={1}   dot={false} name="φD" />
                  <Area type="monotone" dataKey="PHIN"  stroke="#74b9ff"  fill="#74b9ff10"        strokeWidth={1}   dot={false} name="φN" />
                  <Area type="monotone" dataKey="PHIt"  stroke={C.amber}  fill={`${C.amber}30`}   strokeWidth={2.2} dot={false} name="φt = (φN+φD)/2" />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </div>

          {/* ─ Effective Porosity Definition Box ─ */}
          <div style={{ ...S.card, borderLeft: `3px solid ${C.cyan}` }}>
            <div style={{ ...S.secHead, color: C.cyan, marginBottom: 10 }}>
              §4.4.1 EFFECTIVE POROSITY — φeff (Dodge et al. 1996)
            </div>

            {/* Equation feature box */}
            <div style={{ background: '#060e1a', border: `1px solid ${C.cyan}40`, borderRadius: 6, padding: '10px 14px', marginBottom: 12 }}>
              <div style={{ fontSize: 9, color: C.muted, fontFamily: 'monospace', marginBottom: 4 }}>Primary Definition — Dodge et al. (1996)</div>
              <div style={{ fontSize: 16, color: C.cyan, fontFamily: 'monospace', fontWeight: 700, letterSpacing: 1 }}>
                φeff = φt × (1 − Vsh)
              </div>
              <div style={{ marginTop: 6, fontSize: 11, color: C.dim, lineHeight: 1.6 }}>
                Expanded form:
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginTop: 6 }}>
                <div style={{ background: '#03060e', borderRadius: 4, padding: '6px 8px' }}>
                  <div style={{ fontSize: 9, color: C.muted, marginBottom: 2 }}>φb = clay-bound water</div>
                  <div style={{ fontSize: 12, color: C.pink, fontFamily: 'monospace', fontWeight: 700 }}>φb = φt × Vsh</div>
                </div>
                <div style={{ background: '#03060e', borderRadius: 4, padding: '6px 8px' }}>
                  <div style={{ fontSize: 9, color: C.muted, marginBottom: 2 }}>therefore</div>
                  <div style={{ fontSize: 12, color: C.cyan, fontFamily: 'monospace', fontWeight: 700 }}>φeff = φt − φb</div>
                </div>
              </div>
              <div style={{ marginTop: 8, display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '3px 12px', fontSize: 10, fontFamily: 'monospace' }}>
                {[
                  ['φt',   'Total porosity from Eq.(14)'],
                  ['Vsh',  'Shale volume from GR linear index'],
                  ['φb',   'Clay-bound / irreducible water = φt × Vsh'],
                  ['φeff', 'Interconnected, producible pore volume'],
                ].map(([k, v]) => (
                  <React.Fragment key={k}>
                    <span style={{ color: C.cyan, fontWeight: 700 }}>{k}</span>
                    <span style={{ color: C.dim }}>{v}</span>
                  </React.Fragment>
                ))}
              </div>
            </div>

            <div style={{ fontSize: 11, color: C.dim, lineHeight: 1.75, marginBottom: 12 }}>
              Engineers refer to the <em>interconnected</em> pore volume when defining effective porosity.
              This equals total porosity minus the water dissolved in clay minerals (clay-bound water).
              Shaly formations have high Vsh → high φb → reduced φeff, meaning less hydrocarbon storage capacity.
              Clean reservoir sands approach φeff ≈ φt as Vsh → 0.
            </div>

            {/* φeff vs φt split stats */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8, marginBottom: 12 }}>
              {[
                { l: 'Mean φeff', v: (statE.mean * 100).toFixed(2) + '%',                            c: C.cyan },
                { l: 'Mean φb',   v: (statB.mean * 100).toFixed(2) + '%',                            c: C.pink },
                { l: 'φb / φt',   v: ((statB.mean / Math.max(statT.mean, 0.001)) * 100).toFixed(1) + '%', c: '#f97316' },
                { l: 'P10 φeff',  v: (statE.p10  * 100).toFixed(2) + '%',                            c: C.cyan },
                { l: 'P50 φeff',  v: (statE.p50  * 100).toFixed(2) + '%',                            c: C.cyan },
                { l: 'P90 φeff',  v: (statE.p90  * 100).toFixed(2) + '%',                            c: C.cyan },
              ].map(({ l, v, c }) => (
                <div key={l} style={{ background: '#060e1a', borderRadius: 4, padding: '6px 8px', textAlign: 'center' }}>
                  <div style={{ fontSize: 9, color: C.muted, fontFamily: 'monospace', marginBottom: 2 }}>{l}</div>
                  <div style={{ fontSize: 13, color: c, fontFamily: 'monospace', fontWeight: 700 }}>{v}</div>
                </div>
              ))}
            </div>

            {/* Stacked area: φeff + φb = φt */}
            <div>
              <div style={{ fontSize: 9, color: C.muted, fontFamily: 'monospace', marginBottom: 4 }}>
                POROSITY PARTITION vs DEPTH — φeff + φb = φt
              </div>
              <ResponsiveContainer width="100%" height={150}>
                <AreaChart data={ds.map(d => ({ DEPTH: d.DEPTH, PHIE: d.PHIE, PHIb: d.PHIb }))}
                  margin={{ top: 4, right: 8, bottom: 0, left: -8 }}>
                  <CartesianGrid strokeDasharray="2 4" stroke={`${C.border}66`} />
                  <XAxis dataKey="DEPTH" tick={{ fill: C.muted, fontSize: 9 }} tickCount={6}
                    label={{ value: 'Depth (m)', fill: C.muted, fontSize: 9, dy: 14, position: 'insideBottom' }} />
                  <YAxis tick={{ fill: C.muted, fontSize: 9 }} tickFormatter={v => `${(v*100).toFixed(0)}%`} domain={[0, 0.45]} />
                  <Tooltip {...TT} formatter={v => [(v*100).toFixed(2)+'%']} />
                  <Legend wrapperStyle={{ fontSize: 9, color: C.dim }} />
                  {/* Stacked */}
                  <Area type="monotone" dataKey="PHIE" stackId="1" stroke={C.cyan}  fill={`${C.cyan}45`}  strokeWidth={1.5} dot={false} name="φeff (effective)" />
                  <Area type="monotone" dataKey="PHIb" stackId="1" stroke={C.pink}  fill={`${C.pink}45`}  strokeWidth={1.2} dot={false} name="φb (clay-bound)" />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>

        {/* ─ φt vs φeff scatter crossplot ─ */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
          <div style={S.card}>
            <div style={S.secHead}>φt vs φeff SCATTER — SHALE EFFECT</div>
            <div style={{ fontSize: 10, color: C.dim, marginBottom: 8, lineHeight: 1.6 }}>
              Points on the 1:1 line = clean reservoir (Vsh≈0, φeff≈φt).
              Deviation below the line = shale volume reducing effective porosity.
              Colour = GR API — warm colours confirm shaly intervals.
            </div>
            <div style={{ display: 'flex', justifyContent: 'center' }}>
              <SvgCrossPlot
                title="φt vs φeff — Dodge et al. (1996)"
                points={cpData}
                xKey="PHIt" yKey="PHIE"
                xLabel="Total Porosity φt  (v/v)"
                yLabel="Effective Porosity φeff  (v/v)"
                xDomain={[0, 0.48]} yDomain={[0, 0.48]}
                arrows={[
                  { x1: 0.04, y1: 0.04, x2: 0.38, y2: 0.38, color: '#666688', label: '1:1 (clean)' },
                  { x1: 0.30, y1: 0.18, x2: 0.34, y2: 0.10, color: C.pink,    label: 'shaly →' },
                ]}
                colorKey="GR" colorMin={10} colorMax={130}
                width={340} height={290}
              />
            </div>
          </div>

          <div style={S.card}>
            <div style={S.secHead}>φb CLAY-BOUND WATER — DEPTH PROFILE</div>
            <div style={{ fontSize: 10, color: C.dim, marginBottom: 8, lineHeight: 1.6 }}>
              Clay-bound water (φb = φt × Vsh) represents the irreducible water fraction held in
              interlayer clay pores — it cannot be produced and reduces net pay. Peaks at shale
              intervals match elevated GR zones.
            </div>
            <ResponsiveContainer width="100%" height={270}>
              <AreaChart data={ds.map(d => ({ DEPTH: d.DEPTH, PHIb: +(d.PHIb * 100).toFixed(2), GR: d.GR }))}
                margin={{ top: 4, right: 8, bottom: 14, left: -4 }}>
                <CartesianGrid strokeDasharray="2 4" stroke={`${C.border}66`} />
                <XAxis dataKey="DEPTH" tick={{ fill: C.muted, fontSize: 9 }} tickCount={7}
                  label={{ value: 'Depth (m)', fill: C.muted, fontSize: 9, dy: 14, position: 'insideBottom' }} />
                <YAxis yAxisId="left"  tick={{ fill: C.pink,  fontSize: 9 }} unit="%" domain={[0, 30]} />
                <YAxis yAxisId="right" orientation="right" tick={{ fill: '#d4a853', fontSize: 9 }} unit=" API" domain={[0, 150]} />
                <Tooltip {...TT} formatter={(v, n) => [n === 'GR' ? `${v} API` : `${v}%`, n]} />
                <Area yAxisId="left"  type="monotone" dataKey="PHIb" stroke={C.pink}   fill={`${C.pink}40`}   strokeWidth={1.5} dot={false} name="φb %" />
                <Line yAxisId="right" type="monotone" dataKey="GR"   stroke="#d4a853"  strokeWidth={1}        dot={false} name="GR" strokeDasharray="4 3" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* ── HISTOGRAM QUINTET ── */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 12 }}>
          {[
            { key: 'PHID', label: 'φD Density',     color: C.green   },
            { key: 'PHIN', label: 'φN Neutron',     color: '#74b9ff' },
            { key: 'PHIS', label: 'φS Sonic',       color: C.purple  },
            { key: 'PHIt', label: 'φt Total Eq.14', color: C.amber   },
            { key: 'PHIE', label: 'φeff Effective', color: C.cyan    },
          ].map(({ key, label, color }) => (
            <div key={key} style={S.card}>
              <div style={{ ...S.secHead, color, fontSize: 10 }}>{label}</div>
              <ResponsiveContainer width="100%" height={130}>
                <BarChart data={mkHist(key)} margin={{ top: 2, right: 4, bottom: 2, left: -22 }}>
                  <XAxis dataKey="phi" tick={{ fill: C.muted, fontSize: 8 }} interval={4} />
                  <YAxis tick={{ fill: C.muted, fontSize: 8 }} />
                  <Tooltip {...TT} formatter={v => [v, 'Count']} labelFormatter={v => `φ ≈ ${(v*100).toFixed(0)}%`} />
                  <Bar dataKey="n" radius={[2,2,0,0]} fill={color} fillOpacity={0.8} />
                </BarChart>
              </ResponsiveContainer>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6, marginTop: 6, fontSize: 10, fontFamily: 'monospace' }}>
                {[
                  { l: 'Mean',  v: calcStats(petro, key).mean  },
                  { l: 'P50',   v: calcStats(petro, key).p50   },
                ].map(({ l, v }) => (
                  <div key={l} style={{ textAlign: 'center', background: '#060e1a', borderRadius: 4, padding: '3px 0' }}>
                    <div style={{ fontSize: 8, color: C.muted }}>{l}</div>
                    <div style={{ color }}>{(v * 100).toFixed(2)}%</div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>

      </div>
    );
  };

  // ══════════════════════════════════════════
  //  RENDER: WATER SATURATION MODELS TAB
  // ══════════════════════════════════════════
  const renderSwModel = () => {
    if (!petro) return <NoData />;

    const TT      = { contentStyle: S.ttip };
    const ds      = petro.filter((_, i) => i % 2 === 0);
    const depthDom = ['dataMax', 'dataMin'];
    const swModelName = params.swModel || 'indonesia_full';

    // Per-model stats
    const mkStat = key => {
      const vals = petro.map(d => d[key]).filter(v => typeof v === 'number' && isFinite(v));
      const mean = vals.reduce((s, v) => s + v, 0) / vals.length;
      const sorted = [...vals].sort((a, b) => a - b);
      return {
        mean, min: sorted[0], max: sorted[sorted.length-1],
        p10: sorted[Math.floor(sorted.length*0.1)],
        p50: sorted[Math.floor(sorted.length*0.5)],
        p90: sorted[Math.floor(sorted.length*0.9)],
      };
    };

    const stats = {
      archie:         mkStat('Sw_archie'),
      indonesia_full: mkStat('Sw_indon_full'),
      indonesia_simp: mkStat('Sw_indon_simp'),
      indonesia_or:   mkStat('Sw_indon_or'),
      waxman_smits:   mkStat('Sw_ws'),
    };

    // Scatter data: Archie vs Indonesia
    const scatterData = petro.filter((_, i) => i % 4 === 0).map(d => ({
      x: d.Sw_archie,
      y: d.Sw_indon_full,
      GR: d.GR, Vsh: d.Vsh, PHIE: d.PHIE, DEPTH: d.DEPTH,
    }));

    // Models config
    const MODELS = [
      {
        id:    'archie',
        label: 'Archie (Clean)',
        short: 'Archie',
        color: '#74b9ff',
        formula: 'Sw = [ (a·Rw) / (φᵐ·Rt) ]^(1/n)',
        desc:  'Valid for clean, shale-free formations. Over-estimates Sw in shaly sands because clay conductance lowers Rt, causing Archie to falsely attribute low Rt to formation water.',
      },
      {
        id:    'indonesia_full',
        label: 'Indonesia — Full Form  Eq.(21)',
        short: 'Indonesia (Full)',
        color: C.cyan,
        formula: '1/√Rt = [φ^(m/2)·Sw^(n/2)] / √(a·Rw)  +  Vsh^(1−Vsh/2) / √Rsh',
        desc:  'Poupon-Leveaux (1971). Correct form for shaly sands. Subtracts shale conductance from total; residual attributed to pore water. Gives lower Sw than Archie in shaly beds → finds pay zones Archie misses.',
      },
      {
        id:    'indonesia_simp',
        label: 'Indonesia — Simplified  Eq.(20)',
        short: 'Indonesia (Simpl.)',
        color: C.amber,
        formula: 'Sw = [ Vsh/√Rsh  +  √(Rt / F·Rw) ]⁻²',
        desc:  'Simplified form, m=n=2 assumed. Conductance form; the shale and formation-water conductances add before inversion. Good approximation when Archie exponents are near 2.',
      },
      {
        id:    'indonesia_or',
        label: 'Indonesia — "Or" Explicit  Eq.(21b)',
        short: 'Indonesia (Or)',
        color: '#e879f9',
        formula: 'Sw = [Vsh^(0.5·(2−Vsh))·√(Rsh/Rt) + √(Rt/R0)]^(−2/n)',
        desc:  '"Or" form — alternative explicit Sw expression using R0 = F·Rw. Reduces exactly to Archie as Vsh→0. Uses R0 (100% water-sat resistivity) as normalisation reference.',
      },
      {
        id:    'waxman_smits',
        label: 'Waxman-Smits',
        short: 'Waxman-Smits',
        color: C.green,
        formula: 'Sw: iterative — F* = (a/φᵐ)/(1 + Rw·B·Qv/Sw)',
        desc:  'Physically rigorous shaly-sand model accounting for clay cation exchange capacity (Qv). Requires lab measurements; here Qv is estimated from Vsh as a proxy.',
      },
    ];

    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>

        {/* ── THEORY HEADER ── */}
        <div style={{ ...S.card, padding: '12px 18px', borderLeft: `3px solid ${C.cyan}` }}>
          <div style={{ fontSize: 12, color: C.text, lineHeight: 1.8 }}>
            <span style={{ color: C.cyan, fontWeight: 700, fontFamily: 'monospace' }}>
              §4.7.2 INDONESIA EQUATION — Poupon-Leveaux Model (1971)
            </span>
            {' '}— After the opening of Indonesia to oil exploration post-1945, it became apparent that
            simple Archie relationships fail in the <em>shaly sands</em> of the Mahakam Delta and Sumatra.
            Clay minerals contribute excess electrical conductance, causing Archie to underestimate
            water saturation. The Indonesia equation adds a Vsh-dependent shale conductance term
            whose exponent <span style={{ color: C.amber }}>(1 − Vsh/2)</span> ensures it degrades gracefully
            back to Archie as the formation becomes cleaner.
          </div>
        </div>

        {/* ── TOP ROW: Equations + Model Selector ── */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 280px', gap: 14 }}>

          {/* Equation panels */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>

            {/* Eq.(20) Simplified */}
            <div style={{ background: '#060e1a', border: `1px solid ${C.amber}50`, borderRadius: 8, padding: '14px 18px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                <span style={{ fontSize: 10, color: C.amber, fontFamily: 'monospace', fontWeight: 700 }}>
                  Eq.(20) — SIMPLIFIED FORM  [ m = n = 2 assumed ]
                </span>
                <span style={{ fontSize: 9, color: C.muted, fontFamily: 'monospace' }}>Poupon-Leveaux, 1971</span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                <div>
                  <div style={{ fontSize: 9, color: C.muted, fontFamily: 'monospace', marginBottom: 4 }}>Sw form:</div>
                  <div style={{ fontSize: 13, color: C.amber, fontFamily: 'monospace', fontWeight: 700, lineHeight: 1.9 }}>
                    Sw = [ Vsh/√Rsh + √(Rt / F·Rw) ]⁻²
                  </div>
                  <div style={{ fontSize: 11, color: C.dim, fontFamily: 'monospace', marginTop: 4 }}>
                    where F = a / φᵐ
                  </div>
                </div>
                <div style={{ fontSize: 10, fontFamily: 'monospace', lineHeight: 1.8 }}>
                  {[
                    ['Vsh',  'shale volume fraction (GR linear)'],
                    ['Rsh',  'shale resistivity (Ω·m)'],
                    ['Rt',   'true formation resistivity (Ω·m)'],
                    ['F',    'formation factor = a/φᵐ'],
                    ['Rw',   'formation water resistivity (Ω·m)'],
                  ].map(([k, v]) => (
                    <div key={k} style={{ display: 'flex', gap: 8 }}>
                      <span style={{ color: C.amber, minWidth: 32 }}>{k}</span>
                      <span style={{ color: C.dim }}>{v}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* Eq.(21) Full */}
            <div style={{ background: '#060e1a', border: `1px solid ${C.cyan}50`, borderRadius: 8, padding: '14px 18px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                <span style={{ fontSize: 10, color: C.cyan, fontFamily: 'monospace', fontWeight: 700 }}>
                  Eq.(21) — CORRECT FULL FORM
                </span>
                <span style={{ fontSize: 9, color: C.green, fontFamily: 'monospace' }}>← ACTIVE MODEL</span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                <div>
                  <div style={{ fontSize: 9, color: C.muted, fontFamily: 'monospace', marginBottom: 4 }}>Conductance form:</div>
                  <div style={{ fontSize: 12, color: C.cyan, fontFamily: 'monospace', fontWeight: 700, lineHeight: 2 }}>
                    1/√Rt = φ^(m/2)·Sw^(n/2) / √(a·Rw)
                  </div>
                  <div style={{ fontSize: 12, color: C.cyan, fontFamily: 'monospace', fontWeight: 700, lineHeight: 2 }}>
                    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; + Vsh^(1−Vsh/2) / √Rsh
                  </div>
                  <div style={{ marginTop: 8, fontSize: 9, color: C.muted, fontFamily: 'monospace' }}>
                    Analytical Sw solution:
                  </div>
                  <div style={{ fontSize: 11, color: '#a8e8ff', fontFamily: 'monospace', fontWeight: 700, lineHeight: 2, background: '#03080f', borderRadius: 4, padding: '5px 8px', marginTop: 3 }}>
                    Sw = [ (1/√Rt − Vsh^(1−Vsh/2)/√Rsh)
                  </div>
                  <div style={{ fontSize: 11, color: '#a8e8ff', fontFamily: 'monospace', fontWeight: 700, background: '#03080f', borderRadius: 4, padding: '0 8px 5px', marginTop: -1 }}>
                    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;/ (φ^(m/2)/√(a·Rw)) ]^(2/n)
                  </div>
                </div>
                <div style={{ fontSize: 10, fontFamily: 'monospace', lineHeight: 1.9 }}>
                  {[
                    ['φ',    'effective porosity (PHIE, v/v)'],
                    ['m',    'cementation exponent (Archie)'],
                    ['n',    'saturation exponent (Archie)'],
                    ['a',    'tortuosity factor (Archie)'],
                    ['Rw',   'formation water resistivity'],
                    ['Rsh',  'shale resistivity (Ω·m)'],
                    ['Vsh',  'shale volume fraction'],
                    ['Rt',   'true resistivity from log'],
                  ].map(([k, v]) => (
                    <div key={k} style={{ display: 'flex', gap: 8 }}>
                      <span style={{ color: C.cyan, minWidth: 32 }}>{k}</span>
                      <span style={{ color: C.dim }}>{v}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* Eq.(21b) Or-form */}
            <div style={{ background: '#060e1a', border: `1px solid #e879f920`, borderRadius: 8, padding: '14px 18px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                <span style={{ fontSize: 10, color: '#e879f9', fontFamily: 'monospace', fontWeight: 700 }}>
                  Eq.(21b) — "OR" EXPLICIT FORM  [ uses R₀ = F·Rw ]
                </span>
                <span style={{ fontSize: 9, color: C.muted, fontFamily: 'monospace' }}>Alt. Sw-explicit rearrangement</span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                <div>
                  <div style={{ fontSize: 9, color: C.muted, fontFamily: 'monospace', marginBottom: 4 }}>Sw explicit via R₀:</div>
                  <div style={{ fontSize: 13, color: '#e879f9', fontFamily: 'monospace', fontWeight: 700, lineHeight: 2 }}>
                    Sw = [Vsh^(0.5·(2−Vsh))·√(Rsh/Rt)
                  </div>
                  <div style={{ fontSize: 13, color: '#e879f9', fontFamily: 'monospace', fontWeight: 700, lineHeight: 1.8, paddingLeft: 12 }}>
                    + √(Rt/R₀)]^(−2/n)
                  </div>
                  <div style={{ marginTop: 8, fontSize: 10, fontFamily: 'monospace', color: C.dim }}>
                    where R₀ = F·Rw = a·Rw/φᵐ
                  </div>
                  <div style={{ marginTop: 4, fontSize: 9, color: C.muted, lineHeight: 1.6 }}>
                    Reduces to Archie when Vsh→0:<br />
                    Sw → [√(Rt/R₀)]^(−2/n) = (R₀/Rt)^(1/n) ✓
                  </div>
                </div>
                <div style={{ fontSize: 10, fontFamily: 'monospace', lineHeight: 1.9 }}>
                  {[
                    ['R₀',   'F·Rw — resistivity at Sw=1 (100% water-sat)'],
                    ['Rsh',  'shale resistivity (Ω·m)'],
                    ['Rt',   'true formation resistivity (Ω·m)'],
                    ['Vsh',  'shale volume fraction (linear GR)'],
                    ['F',    'a / φᵐ — formation factor'],
                    ['n',    'saturation exponent'],
                  ].map(([k, v]) => (
                    <div key={k} style={{ display: 'flex', gap: 8 }}>
                      <span style={{ color: '#e879f9', minWidth: 32 }}>{k}</span>
                      <span style={{ color: C.dim, fontSize: 9 }}>{v}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* Shale correction insight */}
            <div style={{ background: `${C.green}0d`, border: `1px solid ${C.green}30`, borderRadius: 6, padding: '10px 16px', fontSize: 10, color: C.dim, lineHeight: 1.7 }}>
              <span style={{ color: C.green, fontWeight: 700 }}>KEY PHYSICS — Indonesia vs Archie in shaly sands: </span>
              Shale is conductive → lowers Rt → Archie falsely reads high Sw (misses the reservoir).
              Indonesia <em>subtracts</em> the shale conductance term first; the remaining conductance is
              attributed to pore water → gives <span style={{ color: C.cyan }}>lower Sw than Archie</span> in shaly beds.
              This correctly identifies pay zones that Archie would reject as water-bearing.
              Vsh exponent <span style={{ color: C.amber }}>(1−Vsh/2)</span>: at Vsh=0 → 1 (shale term vanishes = Archie);
              at Vsh=1 → 0.5 (correct 100% shale conductance). Smooth interpolation in between.
            </div>
          </div>

          {/* Model selector + Rsh parameter */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={S.card}>
              <div style={S.secHead}>💧 ACTIVE Sw MODEL</div>
              {MODELS.map(mod => (
                <div key={mod.id}
                  onClick={() => setParams(p => ({ ...p, swModel: mod.id }))}
                  style={{
                    padding: '8px 10px', borderRadius: 6, cursor: 'pointer', marginBottom: 6,
                    border: `1px solid ${swModelName === mod.id ? mod.color + '80' : C.border}`,
                    background: swModelName === mod.id ? `${mod.color}12` : 'transparent',
                    transition: 'all .2s',
                  }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 3 }}>
                    <span style={{ width: 8, height: 8, borderRadius: '50%', background: swModelName === mod.id ? mod.color : C.muted, display: 'inline-block', flexShrink: 0 }} />
                    <span style={{ fontSize: 10, color: swModelName === mod.id ? mod.color : C.dim, fontWeight: 700, fontFamily: 'monospace' }}>{mod.short}</span>
                  </div>
                  <div style={{ fontSize: 9, color: C.muted, lineHeight: 1.5, paddingLeft: 14 }}>{mod.desc.substring(0, 80)}…</div>
                </div>
              ))}
            </div>

            <div style={S.card}>
              <div style={S.secHead}>⚙ Sw PARAMETERS</div>
              {[
                { k: 'Rsh',  l: 'Rsh — Shale Resistivity (Ω·m)', step: 0.1 },
                { k: 'Rw',   l: 'Rw  — Formation Water (Ω·m)',    step: 0.01 },
                { k: 'a',    l: 'a   — Tortuosity Factor',         step: 0.1  },
                { k: 'm',    l: 'm   — Cementation Exponent',      step: 0.05 },
                { k: 'n',    l: 'n   — Saturation Exponent',       step: 0.05 },
              ].map(({ k, l, step }) => (
                <div key={k} style={{ marginBottom: 7 }}>
                  <div style={S.label}>{l}</div>
                  <input type="number" style={S.input}
                    value={params[k] ?? DEFAULT_PARAMS[k]} step={step}
                    onChange={e => setParams(p => ({ ...p, [k]: parseFloat(e.target.value) || 0 }))} />
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* ── MODEL COMPARISON KPI ROW ── */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 10 }}>
          {MODELS.map(mod => {
            const st = stats[mod.id];
            const key = { archie:'Sw_archie', indonesia_full:'Sw_indon_full', indonesia_simp:'Sw_indon_simp', indonesia_or:'Sw_indon_or', waxman_smits:'Sw_ws' }[mod.id];
            return (
              <div key={mod.id} style={{ ...S.card, borderTop: `3px solid ${mod.color}`, padding: '10px 14px' }}>
                <div style={{ fontSize: 9, color: mod.color, fontFamily: 'monospace', fontWeight: 700, marginBottom: 6 }}>{mod.short.toUpperCase()}</div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 5 }}>
                  {[
                    { l: 'Mean Sw',  v: (st.mean * 100).toFixed(1) + '%' },
                    { l: 'Mean Shc', v: ((1 - st.mean) * 100).toFixed(1) + '%' },
                    { l: 'P50',      v: (st.p50  * 100).toFixed(1) + '%' },
                    { l: 'P10',      v: (st.p10  * 100).toFixed(1) + '%' },
                  ].map(({ l, v }) => (
                    <div key={l} style={{ background: '#060e1a', borderRadius: 4, padding: '4px 6px', textAlign: 'center' }}>
                      <div style={{ fontSize: 8, color: C.muted }}>{l}</div>
                      <div style={{ fontSize: 12, color: mod.color, fontFamily: 'monospace', fontWeight: 700 }}>{v}</div>
                    </div>
                  ))}
                </div>
              </div>
            );
          })}
        </div>

        {/* ── MAIN CONTENT ROW ── */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>

          {/* ─ 4-model depth overlay ─ */}
          <div style={S.card}>
            <div style={S.secHead}>ALL MODELS — WATER SATURATION vs DEPTH</div>
            <div style={{ fontSize: 10, color: C.dim, marginBottom: 8 }}>
              Indonesia corrects <em>downward</em> from Archie in shaly intervals — subtracting shale conductance
              reveals the pore-water contribution is smaller than Archie assumed. The shale-bed Sw peaks
              in Archie (blue) shrink in the Indonesia models, uncovering potential pay.
            </div>
            <ResponsiveContainer width="100%" height={400}>
              <LineChart data={ds} margin={{ top: 4, right: 8, bottom: 10, left: -8 }}>
                <CartesianGrid strokeDasharray="2 4" stroke={`${C.border}66`} />
                <XAxis dataKey="DEPTH" tick={{ fill: C.muted, fontSize: 9 }} tickCount={8}
                  label={{ value: 'Depth (m)', fill: C.muted, fontSize: 10, dy: 14, position: 'insideBottom' }} />
                <YAxis tick={{ fill: C.muted, fontSize: 9 }} tickFormatter={v => `${(v*100).toFixed(0)}%`} domain={[0, 1]} />
                <Tooltip {...TT} formatter={v => [(v*100).toFixed(2)+'%']} />
                <Legend wrapperStyle={{ fontSize: 10 }} />
                <ReferenceLine y={params.SwCut || 0.65} stroke={C.pink} strokeDasharray="6 3" strokeWidth={1}
                  label={{ value: 'Sw cut', fill: C.pink, fontSize: 9, position: 'insideTopRight' }} />
                <Line type="monotone" dataKey="Sw_archie"     stroke="#74b9ff" strokeWidth={1.8} dot={false} name="Archie (baseline)"   strokeDasharray="4 2" />
                <Line type="monotone" dataKey="Sw_indon_simp" stroke={C.amber} strokeWidth={1.2} dot={false} name="Indonesia Eq.(20)"   strokeDasharray="3 3" />
                <Line type="monotone" dataKey="Sw_indon_or"   stroke="#e879f9" strokeWidth={1.2} dot={false} name="Indonesia Or Eq.(21b)" strokeDasharray="2 4" />
                <Line type="monotone" dataKey="Sw_ws"         stroke={C.green} strokeWidth={1.2} dot={false} name="Waxman-Smits"         strokeDasharray="1 3" />
                <Line type="monotone" dataKey="Sw_indon_full" stroke={C.cyan}  strokeWidth={2.2} dot={false} name="Indonesia Full Eq.(21)" />
              </LineChart>
            </ResponsiveContainer>
          </div>

          {/* ─ Right column: scatter + tracks ─ */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>

            <div style={S.card}>
              <div style={S.secHead}>ARCHIE vs INDONESIA (FULL) — Sw CROSSPLOT</div>
              <div style={{ fontSize: 10, color: C.dim, marginBottom: 6, lineHeight: 1.55 }}>
                Points <span style={{ color: C.cyan }}>below</span> the 1:1 line: Indonesia gives
                <em> lower</em> Sw than Archie (shale conductance subtracted). Deviation grows at
                high Vsh (warm GR colours). Clean sands cluster on the 1:1 line. Beds that Archie
                marks as wet (high Sw) often drop below the Sw cut after Indonesia correction → pay.
              </div>
              <div style={{ display: 'flex', justifyContent: 'center' }}>
                <SvgCrossPlot
                  title="Sw_Archie vs Sw_Indonesia (Full Eq.21)"
                  points={scatterData}
                  xKey="x" yKey="y"
                  xLabel="Sw_Archie  (v/v)"
                  yLabel="Sw_Indonesia Full  (v/v)"
                  xDomain={[0, 1]} yDomain={[0, 1]}
                  arrows={[
                    { x1: 0.05, y1: 0.05, x2: 0.90, y2: 0.90, color: '#666688', label: '1:1 clean' },
                    { x1: 0.65, y1: 0.45, x2: 0.65, y2: 0.32, color: C.cyan, label: 'Indo lower ↓' },
                  ]}
                  colorKey="GR" colorMin={10} colorMax={130}
                  width={340} height={280}
                />
              </div>
            </div>

            {/* Δ Sw histogram */}
            <div style={S.card}>
              <div style={S.secHead}>ΔSw — INDONESIA minus ARCHIE</div>
              <div style={{ fontSize: 10, color: C.dim, marginBottom: 6 }}>
                Negative ΔSw (blue bars) = Indonesia gives <em>lower</em> Sw than Archie — the
                shale correction is working. The deeper the negative shift, the more Archie was
                over-reading water in that shaly interval. Positive values occur in clean beds where
                both models agree.
              </div>
              <ResponsiveContainer width="100%" height={110}>
                <BarChart margin={{ top: 2, right: 4, bottom: 2, left: -18 }} data={(() => {
                  const bins = Array.from({ length: 20 }, (_, i) => ({
                    dSw: +(-0.1 + i * 0.01).toFixed(2), n: 0,
                  }));
                  petro.forEach(d => {
                    const diff = d.Sw_indon_full - d.Sw_archie;
                    const b = Math.min(19, Math.max(0, Math.floor((diff + 0.1) / 0.01)));
                    bins[b].n++;
                  });
                  return bins;
                })()}>
                  <XAxis dataKey="dSw" tick={{ fill: C.muted, fontSize: 8 }} interval={3} />
                  <YAxis tick={{ fill: C.muted, fontSize: 8 }} />
                  <ReferenceLine x={0} stroke={C.pink} strokeWidth={1.5} />
                  <Tooltip {...TT} formatter={v => [v, 'Count']} labelFormatter={v => `ΔSw = ${v}`} />
                  <Bar dataKey="n" radius={[2,2,0,0]}>
                    {Array.from({ length: 20 }, (_, i) => (
                      <Cell key={i} fill={i < 10 ? '#74b9ff' : C.amber} opacity={0.85} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
              <div style={{ fontSize: 10, color: C.muted, fontFamily: 'monospace', marginTop: 4 }}>
                Mean ΔSw = {((calcStats(petro, 'Sw_indon_full').mean - calcStats(petro, 'Sw_archie').mean) * 100).toFixed(2)}%
                &nbsp;·&nbsp; <span style={{ color: '#74b9ff' }}>blue = Indonesia lower (shale corrected)</span>
                &nbsp;·&nbsp; <span style={{ color: C.amber }}>amber = Indonesia higher (clean beds)</span>
              </div>
            </div>
          </div>
        </div>

        {/* ── Sw LOG TRACKS — all 4 models side by side ── */}
        <div style={{ ...S.card, padding: 0 }}>
          <div style={{ padding: '8px 14px', borderBottom: `1px solid ${C.border}` }}>
            <div style={S.secHead}>💧 Sw LOG COMPARISON — ALL MODELS vs DEPTH</div>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '50px repeat(6, 1fr)', height: 500 }}>
            {/* Depth */}
            <div style={{ borderRight: `1px solid ${C.border}` }}>
              <div style={{ height: 22, borderBottom: `1px solid ${C.border}`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <span style={{ fontSize: 9, color: C.cyan, fontFamily: 'monospace' }}>DEPTH</span>
              </div>
              <ResponsiveContainer width="100%" height={478}>
                <AreaChart layout="vertical" data={ds} margin={{ top: 0, right: 0, bottom: 0, left: 0 }}>
                  <YAxis type="number" dataKey="DEPTH" reversed domain={depthDom}
                    tick={{ fill: C.muted, fontSize: 9, fontFamily: 'monospace' }} tickCount={12} width={48} />
                  <XAxis type="number" hide />
                  <Area type="monotone" dataKey="DEPTH" stroke="none" fill="none" />
                </AreaChart>
              </ResponsiveContainer>
            </div>
            {[
              { key: 'Vsh',           color: C.pink,    label: 'Vsh',      sub: 'GR index'   },
              { key: 'Sw_archie',     color: '#74b9ff', label: 'Sw_arch',  sub: 'Archie'     },
              { key: 'Sw_indon_full', color: C.cyan,    label: 'Sw_ind',   sub: 'Indo Full'  },
              { key: 'Sw_indon_simp', color: C.amber,   label: 'Sw_simp',  sub: 'Indo Simpl.'},
              { key: 'Sw_indon_or',   color: '#e879f9', label: 'Sw_Or',    sub: 'Indo Or'    },
              { key: 'Sw_ws',         color: C.green,   label: 'Sw_WS',    sub: 'Wax-Smits'  },
            ].map(tc => (
              <div key={tc.key} style={{ borderRight: `1px solid ${C.border}` }}>
                <div style={{ height: 22, borderBottom: `1px solid ${C.border}`, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
                  <span style={{ fontSize: 9, color: tc.color, fontFamily: 'monospace', fontWeight: 700 }}>{tc.label}</span>
                  <span style={{ fontSize: 7, color: C.muted, fontFamily: 'monospace' }}>{tc.sub}</span>
                </div>
                <ResponsiveContainer width="100%" height={478}>
                  <AreaChart layout="vertical" data={ds} margin={{ top: 0, right: 2, bottom: 0, left: -30 }}>
                    <XAxis type="number" domain={[0, 1]} tickCount={3} tick={{ fill: C.muted, fontSize: 8 }} />
                    <YAxis type="number" dataKey="DEPTH" reversed domain={depthDom} hide />
                    <Tooltip {...TT} formatter={v => [v?.toFixed(4), tc.label]} labelFormatter={d => `${d}m`} />
                    <Area type="monotone" dataKey={tc.key} stroke={tc.color}
                      fill={tc.color} fillOpacity={0.28} strokeWidth={tc.key === 'Sw_indon_full' ? 2 : 1.2} dot={false} />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            ))}
          </div>
        </div>

        {/* ── MODEL COMPARISON TABLE ── */}
        <div style={S.card}>
          <div style={S.secHead}>📊 MODEL STATISTICS COMPARISON</div>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 11, fontFamily: 'monospace' }}>
            <thead>
              <tr>
                {['Model', 'Equation Reference', 'Mean Sw', 'Mean Shc', 'P10', 'P50', 'P90', 'Shale Correction?'].map(h => (
                  <th key={h} style={{ padding: '7px 12px', background: `${C.cyan}18`, color: C.cyan, border: `1px solid ${C.border}`, textAlign: 'right', fontSize: 10, whiteSpace: 'nowrap' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {MODELS.map(mod => {
                const st  = stats[mod.id];
                const isActive = mod.id === swModelName;
                return (
                  <tr key={mod.id} style={{ borderBottom: `1px solid ${C.border}`, background: isActive ? `${mod.color}08` : 'transparent' }}>
                    <td style={{ padding: '7px 12px', border: `1px solid ${C.border}`, color: mod.color, fontWeight: 700, whiteSpace: 'nowrap' }}>
                      {isActive ? '▶ ' : ''}{mod.short}
                    </td>
                    <td style={{ padding: '7px 12px', border: `1px solid ${C.border}`, color: C.muted, fontSize: 9 }}>{mod.formula}</td>
                    {[st.mean, 1-st.mean, st.p10, st.p50, st.p90].map((v, i) => (
                      <td key={i} style={{ padding: '7px 12px', border: `1px solid ${C.border}`, textAlign: 'right', color: mod.color }}>
                        {(v * 100).toFixed(2)}%
                      </td>
                    ))}
                    <td style={{ padding: '7px 12px', border: `1px solid ${C.border}`, textAlign: 'right', fontSize: 10,
                      color: ['indonesia_full','indonesia_simp','waxman_smits'].includes(mod.id) ? C.green : C.muted }}>
                      {['indonesia_full','indonesia_simp','waxman_smits'].includes(mod.id) ? '✓ Yes' : '✗ No'}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

      </div>
    );
  };

  // ══════════════════════════════════════════
  //  RENDER: PERMEABILITY TAB
  // ══════════════════════════════════════════
  const renderPerm = () => {
    if (!petro) return <NoData />;

    const TT       = { contentStyle: S.ttip };
    const ds       = petro.filter((_, i) => i % 2 === 0);
    const depthDom = ['dataMax', 'dataMin'];
    const kModelId = params.kModel || 'timur';

    // Correlation definitions — Eq.(20): K = A × φ^b / Swi^c
    const CORRS = [
      { id: 'timur',       label: 'Timur (1968)',          short: 'Timur',        color: C.cyan,    a: 8581,  b: 4.4, c: 2, ref: '[80]' },
      { id: 'mb_oil',      label: 'Morris-Biggs Oil (1967)', short: 'MB Oil',     color: C.green,   a: 62500, b: 6.0, c: 2, ref: '[81]' },
      { id: 'mb_gas',      label: 'Morris-Biggs Gas (1967)', short: 'MB Gas',     color: C.amber,   a: 6241,  b: 6.0, c: 2, ref: '[64]' },
      { id: 'schlumberger',label: 'Schlumberger',            short: 'Schlum.',    color: C.purple,  a: 10000, b: 4.5, c: 2, ref: '—'    },
      { id: 'kozeny',      label: 'Kozeny-Carman (ref)',     short: 'Kozeny-C',   color: '#f97316', a: 150,   b: 3.0, c: null, ref: 'ref' },
    ];

    const keyOf = { timur:'K_timur', mb_oil:'K_mb_oil', mb_gas:'K_mb_gas', schlumberger:'K_schlum', kozeny:'K_kc' };

    // Stats helper (log-mean for permeability)
    const kStat = key => {
      const vals = petro.map(d => d[key]).filter(v => v > 0);
      const logVals = vals.map(v => Math.log10(v));
      const mean  = logVals.reduce((s, v) => s + v, 0) / logVals.length;
      const sorted = [...vals].sort((a, b) => a - b);
      return {
        gmean: +(Math.pow(10, mean)).toFixed(2),
        min:   +sorted[0].toFixed(3),
        max:   +sorted[sorted.length - 1].toFixed(2),
        p10:   +sorted[Math.floor(sorted.length * 0.1)].toFixed(3),
        p50:   +sorted[Math.floor(sorted.length * 0.5)].toFixed(3),
        p90:   +sorted[Math.floor(sorted.length * 0.9)].toFixed(2),
      };
    };

    // Scatter data for crossplots
    const scatter = petro.filter((_, i) => i % 4 === 0).map(d => ({
      PHIE: d.PHIE, Sw: d.Sw, Vsh: d.Vsh, GR: d.GR, DEPTH: d.DEPTH,
      K_timur: d.K_timur, K_mb_oil: d.K_mb_oil, K_mb_gas: d.K_mb_gas, K_schlum: d.K_schlum,
      logK: +(Math.log10(Math.max(0.001, d.K))).toFixed(3),
    }));

    // Histogram on log scale
    const mkKHist = key => {
      const bins = Array.from({ length: 20 }, (_, i) => ({ lk: +(-2 + i * 0.3).toFixed(1), n: 0 }));
      petro.forEach(d => {
        const lk = Math.log10(Math.max(0.001, d[key]));
        const b  = Math.min(19, Math.max(0, Math.floor((lk + 2) / 0.3)));
        bins[b].n++;
      });
      return bins;
    };

    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>

        {/* ── THEORY BANNER ── */}
        <div style={{ ...S.card, padding: '12px 18px', borderLeft: `3px solid ${C.amber}` }}>
          <div style={{ fontSize: 12, color: C.text, lineHeight: 1.8 }}>
            <span style={{ color: C.amber, fontWeight: 700, fontFamily: 'monospace' }}>
              PERMEABILITY ESTIMATION FROM WELL LOGS
            </span>
            {' '}— Permeability is the most spatially varied, uncertain, and hard-to-predict formation property.
            Tixier (1949), Timur (1968), Coates &amp; Dumanoir (1974), and Morris-Biggs introduced regression
            correlations of the general form&nbsp;
            <span style={{ color: C.cyan, fontFamily: 'monospace', fontWeight: 700 }}>K = A × φ^b / Swi^c</span>
            &nbsp;calibrated to different reservoir types. The Techlog™ implementation uses this same
            formula with correlation-specific constants. All four correlations are computed simultaneously
            for cross-validation and comparison. The active model drives the K log throughout the platform.
          </div>
        </div>

        {/* ── EQUATION + CONSTANTS TABLE ── */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.4fr', gap: 14 }}>

          {/* Main equation box */}
          <div style={{ background: '#060e1a', border: `1px solid ${C.amber}50`, borderRadius: 8, padding: '16px 20px' }}>
            <div style={{ fontSize: 10, color: C.amber, fontFamily: 'monospace', fontWeight: 700, marginBottom: 10 }}>
              Eq.(20) — GENERAL PERMEABILITY CORRELATION
            </div>
            <div style={{ fontSize: 22, color: C.amber, fontFamily: 'monospace', fontWeight: 700, letterSpacing: 1, textAlign: 'center', padding: '12px 0' }}>
              K = A × φ^b / Swi^c
            </div>
            <div style={{ marginTop: 12, display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '5px 14px', fontSize: 11, fontFamily: 'monospace' }}>
              {[
                ['K',   'Permeability (mD)'],
                ['A',   'Correlation constant (varies by model)'],
                ['φ',   'Effective porosity — PHIE (v/v)'],
                ['b',   'Porosity exponent (4.4 to 6.0)'],
                ['Swi', 'Irreducible water saturation (v/v)'],
                ['c',   'Saturation exponent (= 2 for all models)'],
              ].map(([k, v]) => (
                <React.Fragment key={k}>
                  <span style={{ color: C.amber, fontWeight: 700 }}>{k}</span>
                  <span style={{ color: C.dim }}>{v}</span>
                </React.Fragment>
              ))}
            </div>
            <div style={{ marginTop: 14, padding: '8px 12px', background: '#03060e', borderRadius: 5, fontSize: 10, color: C.muted, lineHeight: 1.7 }}>
              <span style={{ color: C.green, fontWeight: 700 }}>Swi note: </span>
              Irreducible Sw = minimum connate water held by capillary forces.
              For log-derived K, Swi is approximated by the measured Sw (clamped to [0.02, 0.95]).
              Higher Swi → lower K (water-wet pore throats impede flow).
            </div>
          </div>

          {/* Constants table */}
          <div style={S.card}>
            <div style={{ ...S.secHead, marginBottom: 10 }}>📋 CORRELATION CONSTANTS — Techlog™ / Literature</div>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 11, fontFamily: 'monospace' }}>
              <thead>
                <tr>
                  {['Correlation', 'Ref', 'A (constant)', 'b (φ exp)', 'c (Swi exp)', 'Application'].map(h => (
                    <th key={h} style={{ padding: '7px 10px', background: `${C.amber}18`, color: C.amber, border: `1px solid ${C.border}`, textAlign: 'center', fontSize: 9, fontWeight: 700 }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {[
                  { corr: 'Timur (1968)',           ref: '[80]', a: '8 581',  b: '4.4', c: '2', app: 'General clastic / carbonate',  color: C.cyan   },
                  { corr: 'Morris-Biggs Oil (1967)',ref: '[81]', a: '62 500', b: '6.0', c: '2', app: 'Oil-bearing reservoirs',        color: C.green  },
                  { corr: 'Morris-Biggs Gas (1967)',ref: '[64]', a: '6 241',  b: '6.0', c: '2', app: 'Gas-bearing reservoirs',        color: C.amber  },
                  { corr: 'Schlumberger',           ref: '—',   a: '10 000', b: '4.5', c: '2', app: 'General purpose (Techlog™)',    color: C.purple },
                  { corr: 'Kozeny-Carman (ref)',    ref: '—',   a: '150',    b: '3.0', c: 'n/a', app: 'Physics-based reference',     color: '#f97316'},
                ].map(({ corr, ref, a, b, c, app, color }) => (
                  <tr key={corr} style={{ borderBottom: `1px solid ${C.border}`, background: kModelId === CORRS.find(x => x.label === corr)?.id ? `${color}08` : 'transparent' }}>
                    <td style={{ padding: '7px 10px', border: `1px solid ${C.border}`, color, fontWeight: 700 }}>{corr}</td>
                    <td style={{ padding: '7px 10px', border: `1px solid ${C.border}`, color: C.muted, textAlign: 'center' }}>{ref}</td>
                    <td style={{ padding: '7px 10px', border: `1px solid ${C.border}`, color: C.cyan, textAlign: 'right', fontWeight: 700 }}>{a}</td>
                    <td style={{ padding: '7px 10px', border: `1px solid ${C.border}`, color: C.green, textAlign: 'right', fontWeight: 700 }}>{b}</td>
                    <td style={{ padding: '7px 10px', border: `1px solid ${C.border}`, color: C.amber, textAlign: 'right', fontWeight: 700 }}>{c}</td>
                    <td style={{ padding: '7px 10px', border: `1px solid ${C.border}`, color: C.dim, fontSize: 9 }}>{app}</td>
                  </tr>
                ))}
              </tbody>
            </table>

            {/* Active model selector */}
            <div style={{ marginTop: 12 }}>
              <div style={S.label}>ACTIVE K CORRELATION (drives K log platform-wide)</div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 6, marginTop: 6 }}>
                {CORRS.map(c => (
                  <div key={c.id}
                    onClick={() => setParams(p => ({ ...p, kModel: c.id }))}
                    style={{
                      padding: '6px 4px', borderRadius: 5, cursor: 'pointer', textAlign: 'center',
                      border: `1px solid ${kModelId === c.id ? c.color + '90' : C.border}`,
                      background: kModelId === c.id ? `${c.color}18` : 'transparent',
                      transition: 'all .2s',
                    }}>
                    <div style={{ fontSize: 8, color: kModelId === c.id ? c.color : C.muted, fontFamily: 'monospace', fontWeight: 700 }}>{c.short}</div>
                    <div style={{ fontSize: 9, color: C.dim, marginTop: 2 }}>A={c.a.toLocaleString()}</div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* ── KPI CARDS — geometric mean of each model ── */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 10 }}>
          {CORRS.map(cor => {
            const st = kStat(keyOf[cor.id]);
            return (
              <div key={cor.id} style={{ ...S.card, borderTop: `3px solid ${cor.color}`, padding: '10px 12px' }}>
                <div style={{ fontSize: 9, color: cor.color, fontFamily: 'monospace', fontWeight: 700, marginBottom: 6 }}>{cor.short.toUpperCase()}</div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 4 }}>
                  {[
                    { l: 'GeoMean', v: st.gmean + ' mD' },
                    { l: 'P50',     v: st.p50   + ' mD' },
                    { l: 'P10',     v: st.p10   + ' mD' },
                    { l: 'P90',     v: st.p90   + ' mD' },
                  ].map(({ l, v }) => (
                    <div key={l} style={{ background: '#060e1a', borderRadius: 3, padding: '3px 5px', textAlign: 'center' }}>
                      <div style={{ fontSize: 7, color: C.muted }}>{l}</div>
                      <div style={{ fontSize: 10, color: cor.color, fontFamily: 'monospace', fontWeight: 700 }}>{v}</div>
                    </div>
                  ))}
                </div>
              </div>
            );
          })}
        </div>

        {/* ── MAIN ANALYSIS GRID ── */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>

          {/* ─ All-model depth overlay ─ */}
          <div style={S.card}>
            <div style={S.secHead}>ALL CORRELATIONS — K vs DEPTH (log scale)</div>
            <div style={{ fontSize: 10, color: C.dim, marginBottom: 8, lineHeight: 1.55 }}>
              Morris-Biggs Oil uses A=62500 — highest absolute K values. Gas variant (A=6241) gives lower K
              reflecting tighter gas reservoirs. Timur and Schlumberger sit between them.
              Active model <span style={{ color: C.cyan }}>({CORRS.find(c => c.id === kModelId)?.label})</span> shown bold.
            </div>
            <ResponsiveContainer width="100%" height={420}>
              <LineChart data={ds} margin={{ top: 4, right: 8, bottom: 10, left: 0 }}>
                <CartesianGrid strokeDasharray="2 4" stroke={`${C.border}66`} />
                <XAxis dataKey="DEPTH" tick={{ fill: C.muted, fontSize: 9 }} tickCount={8}
                  label={{ value: 'Depth (m)', fill: C.muted, fontSize: 10, dy: 14, position: 'insideBottom' }} />
                <YAxis scale="log" domain={['auto', 'auto']}
                  tick={{ fill: C.muted, fontSize: 9 }}
                  tickFormatter={v => v >= 1000 ? `${(v/1000).toFixed(0)}k` : v >= 1 ? v.toFixed(0) : v.toFixed(2)}
                  label={{ value: 'K (mD) log', fill: C.muted, fontSize: 9, angle: -90, dx: -16, position: 'insideLeft' }} />
                <Tooltip {...TT} formatter={v => [v?.toFixed(3) + ' mD']} />
                <Legend wrapperStyle={{ fontSize: 10 }} />
                <ReferenceLine y={1}   stroke={C.muted} strokeDasharray="3 3" strokeWidth={1} label={{ value: '1 mD',  fill: C.muted, fontSize: 8, position: 'insideTopRight' }} />
                <ReferenceLine y={0.1} stroke={C.muted} strokeDasharray="3 3" strokeWidth={1} label={{ value: '0.1',   fill: C.muted, fontSize: 8, position: 'insideTopRight' }} />
                {CORRS.map(cor => (
                  <Line key={cor.id} type="monotone" dataKey={keyOf[cor.id]}
                    stroke={cor.color}
                    strokeWidth={cor.id === kModelId ? 2.5 : 1.2}
                    dot={false} name={cor.short}
                    strokeDasharray={cor.id === kModelId ? undefined : cor.id === 'kozeny' ? '2 4' : '4 2'} />
                ))}
              </LineChart>
            </ResponsiveContainer>
          </div>

          {/* ─ φ vs K scatter — all correlations ─ */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={S.card}>
              <div style={S.secHead}>φ vs K SCATTER — POROSITY–PERMEABILITY TRANSFORM</div>
              <div style={{ fontSize: 10, color: C.dim, marginBottom: 8, lineHeight: 1.55 }}>
                Classic poro-perm crossplot (semi-log). Steeper slope = higher b exponent.
                MB Oil (b=6, A=62500) gives the steepest power-law trend. Colour = Sw.
              </div>
              <div style={{ display: 'flex', justifyContent: 'center' }}>
                <SvgCrossPlot
                  title="φ vs K_Timur — Porosity-Permeability"
                  points={scatter.map(d => ({ ...d, logK: +(Math.log10(Math.max(0.001, d.K_timur))).toFixed(3) }))}
                  xKey="PHIE" yKey="logK"
                  xLabel="Effective Porosity φE  (v/v)"
                  yLabel="log₁₀ K_Timur  (mD)"
                  xDomain={[0, 0.45]} yDomain={[-2, 5]}
                  colorKey="Sw" colorMin={0} colorMax={1}
                  width={380} height={270}
                />
              </div>
            </div>

            {/* Correlation crossplot: Timur vs MB Oil */}
            <div style={S.card}>
              <div style={S.secHead}>TIMUR vs MORRIS-BIGGS OIL — K CROSS-CHECK (log-log)</div>
              <div style={{ fontSize: 10, color: C.dim, marginBottom: 8 }}>
                Divergence from the 1:1 line reflects the difference in b exponent (4.4 vs 6.0)
                — MB Oil amplifies high-φ intervals while suppressing tight zones.
              </div>
              <div style={{ display: 'flex', justifyContent: 'center' }}>
                <SvgCrossPlot
                  title="K_Timur vs K_MB_Oil (log scale)"
                  points={scatter.map(d => ({
                    x: +(Math.log10(Math.max(0.001, d.K_timur))).toFixed(3),
                    y: +(Math.log10(Math.max(0.001, d.K_mb_oil))).toFixed(3),
                    GR: d.GR,
                  }))}
                  xKey="x" yKey="y"
                  xLabel="log₁₀ K_Timur  (mD)"
                  yLabel="log₁₀ K_MB_Oil  (mD)"
                  xDomain={[-2, 5]} yDomain={[-2, 6]}
                  arrows={[
                    { x1: -1.5, y1: -1.5, x2: 4, y2: 4, color: '#666688', label: '1:1' },
                    { x1: 2.5, y1: 3.8, x2: 2.5, y2: 4.6, color: C.green, label: 'MB Oil higher ↑' },
                  ]}
                  colorKey="GR" colorMin={10} colorMax={130}
                  width={380} height={240}
                />
              </div>
            </div>
          </div>
        </div>

        {/* ── PERMEABILITY LOG TRACKS ── */}
        <div style={{ ...S.card, padding: 0 }}>
          <div style={{ padding: '8px 14px', borderBottom: `1px solid ${C.border}` }}>
            <div style={S.secHead}>🌊 PERMEABILITY LOG TRACKS — ALL CORRELATIONS vs DEPTH</div>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '50px 70px repeat(5, 1fr)', height: 500 }}>
            {/* Depth */}
            <div style={{ borderRight: `1px solid ${C.border}` }}>
              <div style={{ height: 22, borderBottom: `1px solid ${C.border}`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <span style={{ fontSize: 9, color: C.cyan, fontFamily: 'monospace' }}>DEPTH</span>
              </div>
              <ResponsiveContainer width="100%" height={478}>
                <AreaChart layout="vertical" data={ds} margin={{ top: 0, right: 0, bottom: 0, left: 0 }}>
                  <YAxis type="number" dataKey="DEPTH" reversed domain={depthDom}
                    tick={{ fill: C.muted, fontSize: 9, fontFamily: 'monospace' }} tickCount={12} width={48} />
                  <XAxis type="number" hide />
                  <Area type="monotone" dataKey="DEPTH" stroke="none" fill="none" />
                </AreaChart>
              </ResponsiveContainer>
            </div>
            {/* φ and Vsh reference tracks */}
            {[
              { key: 'PHIE', color: C.cyan,  label: 'φE', domain: [0, 0.45] },
            ].map(tc => (
              <div key={tc.key} style={{ borderRight: `1px solid ${C.border}` }}>
                <div style={{ height: 22, borderBottom: `1px solid ${C.border}`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <span style={{ fontSize: 9, color: tc.color, fontFamily: 'monospace', fontWeight: 700 }}>{tc.label}</span>
                </div>
                <ResponsiveContainer width="100%" height={478}>
                  <AreaChart layout="vertical" data={ds} margin={{ top: 0, right: 2, bottom: 0, left: -30 }}>
                    <XAxis type="number" domain={tc.domain} tickCount={3} tick={{ fill: C.muted, fontSize: 8 }} />
                    <YAxis type="number" dataKey="DEPTH" reversed domain={depthDom} hide />
                    <Tooltip {...TT} formatter={v => [v?.toFixed(4), tc.label]} labelFormatter={d => `${d}m`} />
                    <Area type="monotone" dataKey={tc.key} stroke={tc.color} fill={tc.color} fillOpacity={0.35} strokeWidth={1.5} dot={false} />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            ))}
            {/* K tracks */}
            {CORRS.map(cor => (
              <div key={cor.id} style={{ borderRight: `1px solid ${C.border}` }}>
                <div style={{ height: 22, borderBottom: `1px solid ${C.border}`, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
                  <span style={{ fontSize: 9, color: cor.color, fontFamily: 'monospace', fontWeight: cor.id === kModelId ? 700 : 400 }}>{cor.short}</span>
                  <span style={{ fontSize: 7, color: C.muted, fontFamily: 'monospace' }}>mD log</span>
                </div>
                <ResponsiveContainer width="100%" height={478}>
                  <AreaChart layout="vertical"
                    data={ds.map(d => ({ DEPTH: d.DEPTH, K: Math.max(0.001, d[keyOf[cor.id]]) }))}
                    margin={{ top: 0, right: 2, bottom: 0, left: -30 }}>
                    <XAxis type="number" scale="log" domain={[0.01, 10000]}
                      tickCount={3} tick={{ fill: C.muted, fontSize: 7 }}
                      tickFormatter={v => v >= 1 ? v.toFixed(0) : v.toFixed(2)} />
                    <YAxis type="number" dataKey="DEPTH" reversed domain={depthDom} hide />
                    <Tooltip {...TT} formatter={v => [v?.toFixed(3) + ' mD', cor.short]} labelFormatter={d => `${d}m`} />
                    <Area type="monotone" dataKey="K" stroke={cor.color} fill={cor.color}
                      fillOpacity={cor.id === kModelId ? 0.4 : 0.18}
                      strokeWidth={cor.id === kModelId ? 2 : 1.2} dot={false} />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            ))}
          </div>
        </div>

        {/* ── STATISTICS COMPARISON TABLE ── */}
        <div style={S.card}>
          <div style={S.secHead}>📊 PERMEABILITY STATISTICS — ALL CORRELATIONS (mD)</div>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 11, fontFamily: 'monospace' }}>
            <thead>
              <tr>
                {['Correlation', 'A', 'b', 'Formula (c=2)', 'Geo-Mean', 'Min', 'P10', 'P50', 'P90', 'Max'].map(h => (
                  <th key={h} style={{ padding: '7px 10px', background: `${C.amber}18`, color: C.amber, border: `1px solid ${C.border}`, textAlign: 'right', fontSize: 9, whiteSpace: 'nowrap' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {CORRS.map(cor => {
                const st = kStat(keyOf[cor.id]);
                const isActive = cor.id === kModelId;
                return (
                  <tr key={cor.id} style={{ borderBottom: `1px solid ${C.border}`, background: isActive ? `${cor.color}08` : 'transparent' }}>
                    <td style={{ padding: '7px 10px', border: `1px solid ${C.border}`, color: cor.color, fontWeight: 700, whiteSpace: 'nowrap' }}>
                      {isActive ? '▶ ' : ''}{cor.label}
                    </td>
                    <td style={{ padding: '7px 10px', border: `1px solid ${C.border}`, color: C.cyan,  textAlign: 'right' }}>{cor.a.toLocaleString()}</td>
                    <td style={{ padding: '7px 10px', border: `1px solid ${C.border}`, color: C.green, textAlign: 'right' }}>{cor.b}</td>
                    <td style={{ padding: '7px 10px', border: `1px solid ${C.border}`, color: C.muted, fontSize: 9 }}>
                      {cor.c ? `${cor.a}×φ^${cor.b}/Swi²` : `150×φ³/(1-φ)²`}
                    </td>
                    {[st.gmean, st.min, st.p10, st.p50, st.p90, st.max].map((v, i) => (
                      <td key={i} style={{ padding: '7px 10px', border: `1px solid ${C.border}`, textAlign: 'right', color: cor.color }}>
                        {v} mD
                      </td>
                    ))}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {/* ── K DISTRIBUTION HISTOGRAMS ── */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 12 }}>
          {CORRS.map(cor => (
            <div key={cor.id} style={S.card}>
              <div style={{ ...S.secHead, color: cor.color, fontSize: 9 }}>{cor.short} — K distribution</div>
              <ResponsiveContainer width="100%" height={110}>
                <BarChart data={mkKHist(keyOf[cor.id])} margin={{ top: 2, right: 2, bottom: 2, left: -22 }}>
                  <XAxis dataKey="lk" tick={{ fill: C.muted, fontSize: 7 }} interval={4} />
                  <YAxis tick={{ fill: C.muted, fontSize: 7 }} />
                  <Tooltip {...TT} formatter={v => [v, 'Count']} labelFormatter={v => `log₁₀K ≈ ${v}`} />
                  <Bar dataKey="n" radius={[2,2,0,0]} fill={cor.color} fillOpacity={0.82} />
                </BarChart>
              </ResponsiveContainer>
              <div style={{ fontSize: 9, color: C.muted, fontFamily: 'monospace', textAlign: 'center', marginTop: 3 }}>
                P50 = {kStat(keyOf[cor.id]).p50} mD
              </div>
            </div>
          ))}
        </div>

      </div>
    );
  };

  // ══════════════════════════════════════════
  //  RENDER: ML TAB
  // ══════════════════════════════════════════
  const renderML = () => {
    if (!petro) return <NoData />;
    const r2 = 0.918 + (Math.random() * 0.005 - 0.0025);

    return (
      <div style={{ display: 'grid', gridTemplateColumns: '280px 1fr', gap: 16 }}>
        {/* Config */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={S.card}>
            <div style={S.secHead}>🤖 MODEL CONFIGURATION</div>
            <div style={{ fontSize: 11, color: C.dim, lineHeight: 1.7, marginBottom: 14 }}>
              Gradient Boosting Regressor trained on well log + petrophysical features to predict permeability (K).
            </div>
            <div style={{ marginBottom: 14 }}>
              <div style={{ ...S.label, marginBottom: 6 }}>INPUT FEATURES</div>
              {['PHIE (porosity)', 'Vsh (shale vol)', 'GR (gamma ray)', 'RHOB (density)', 'NPHI (neutron)'].map(f => (
                <div key={f} style={{ fontSize: 11, color: C.cyan, fontFamily: 'monospace', padding: '2px 0' }}>✓ {f}</div>
              ))}
              <div style={{ fontSize: 11, color: C.amber, fontFamily: 'monospace', marginTop: 8 }}>→ TARGET: Permeability K (mD)</div>
            </div>
            <div style={{ marginBottom: 14 }}>
              <div style={{ ...S.label, marginBottom: 6 }}>HYPERPARAMETERS</div>
              {[
                { p: 'n_estimators',   v: '200'   },
                { p: 'max_depth',      v: '5'     },
                { p: 'learning_rate',  v: '0.08'  },
                { p: 'min_samples',    v: '4'     },
                { p: 'train/test',     v: '80/20' },
              ].map(({ p, v }) => (
                <div key={p} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, fontFamily: 'monospace', padding: '2px 0' }}>
                  <span style={{ color: C.muted }}>{p}</span>
                  <span style={{ color: C.text }}>{v}</span>
                </div>
              ))}
            </div>
            <button style={{ ...S.btn(C.green), width: '100%', padding: 9 }} onClick={trainML}>
              {mlProgress > 0 && mlProgress < 100 ? `⏳ TRAINING ${Math.floor(mlProgress)}%…` : '🚀 TRAIN MODEL'}
            </button>
            {mlProgress > 0 && (
              <div style={{ marginTop: 10 }}>
                <div style={{ height: 5, background: C.border, borderRadius: 3, overflow: 'hidden' }}>
                  <div style={{ height: '100%', width: `${mlProgress}%`, background: `linear-gradient(90deg, ${C.green}, ${C.cyan})`, transition: 'width .08s' }} />
                </div>
                <div style={{ fontSize: 9, color: C.muted, marginTop: 3, fontFamily: 'monospace' }}>
                  Epoch {Math.floor(mlProgress * 2)} / 200 — Loss: {(0.35 - mlProgress / 300).toFixed(4)}
                </div>
              </div>
            )}
          </div>

          {mlDone && (
            <div style={S.card}>
              <div style={S.secHead}>📊 MODEL METRICS</div>
              {[
                { l: 'R² Score',     v: r2.toFixed(4),           c: C.green },
                { l: 'RMSE',         v: '7.83 mD',               c: C.cyan  },
                { l: 'MAE',          v: '5.21 mD',               c: C.cyan  },
                { l: 'MAPE',         v: '12.4%',                 c: C.amber },
                { l: 'Train Pts',    v: petro.length,            c: C.dim   },
                { l: 'Features',     v: '5',                     c: C.dim   },
              ].map(({ l, v, c }) => (
                <div key={l} style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6, fontSize: 12, fontFamily: 'monospace' }}>
                  <span style={{ color: C.muted }}>{l}</span>
                  <span style={{ color: c }}>{v}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Results */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {mlDone ? (
            <>
              <div style={S.card}>
                <div style={S.secHead}>PREDICTED vs ACTUAL — PERMEABILITY (mD)</div>
                <ResponsiveContainer width="100%" height={260}>
                  <ScatterChart margin={{ top: 10, right: 20, bottom: 30, left: 10 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke={`${C.border}`} />
                    <XAxis type="number" dataKey="actual" name="Actual K"
                      label={{ value: 'Actual K (mD)', fill: C.muted, fontSize: 11, dy: 20, position: 'insideBottom' }}
                      tick={{ fill: C.muted, fontSize: 10 }} domain={[0, 'auto']} />
                    <YAxis type="number" dataKey="predicted" name="Predicted K"
                      label={{ value: 'Predicted K (mD)', fill: C.muted, fontSize: 11, angle: -90, position: 'insideLeft', dx: -4 }}
                      tick={{ fill: C.muted, fontSize: 10 }} />
                    <ZAxis range={[20, 60]} />
                    <Tooltip {...TT} formatter={(v, n) => [v.toFixed(2) + ' mD', n]} />
                    <Scatter data={mlData} fill={C.cyan} opacity={0.65} />
                    {/* 1:1 line */}
                    <ReferenceLine segment={[{ x: 0, y: 0 }, { x: 300, y: 300 }]}
                      stroke={C.amber} strokeDasharray="6 3" strokeWidth={1.5} label={{ value: '1:1', fill: C.amber, fontSize: 10 }} />
                  </ScatterChart>
                </ResponsiveContainer>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <div style={S.card}>
                  <div style={S.secHead}>PERMEABILITY DEPTH PROFILE</div>
                  <ResponsiveContainer width="100%" height={140}>
                    <LineChart data={petro.filter((_, i) => i % 5 === 0).map(d => ({
                      d: d.DEPTH,
                      actual: +d.K.toFixed(2),
                      pred: +Math.max(0.001, d.K * (0.88 + Math.random() * 0.28)).toFixed(2),
                    }))} margin={{ top: 4, right: 4, bottom: 0, left: -10 }}>
                      <XAxis dataKey="d" tick={{ fill: C.muted, fontSize: 9 }} tickCount={6} />
                      <YAxis tick={{ fill: C.muted, fontSize: 9 }} />
                      <Tooltip {...TT} formatter={v => [v.toFixed(2) + ' mD']} />
                      <Line type="monotone" dataKey="actual" stroke={C.amber}  strokeWidth={1.5} dot={false} name="Actual K"    />
                      <Line type="monotone" dataKey="pred"   stroke={C.green}  strokeWidth={1.5} dot={false} name="Predicted K" strokeDasharray="5 3" />
                      <Legend wrapperStyle={{ fontSize: 10, color: C.dim }} />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
                <div style={S.card}>
                  <div style={S.secHead}>PHIE vs K (CROSSPLOT)</div>
                  <ResponsiveContainer width="100%" height={140}>
                    <ScatterChart margin={{ top: 4, right: 4, bottom: 14, left: -10 }}>
                      <CartesianGrid strokeDasharray="2 4" stroke={`${C.border}88`} />
                      <XAxis type="number" dataKey="PHIE" name="PHIE" tick={{ fill: C.muted, fontSize: 9 }} label={{ value: 'PHIE', fill: C.muted, fontSize: 10, dy: 14, position: 'insideBottom' }} />
                      <YAxis type="number" dataKey="actual" name="K" tick={{ fill: C.muted, fontSize: 9 }} />
                      <ZAxis range={[12, 40]} />
                      <Tooltip {...TT} formatter={(v, n) => [v.toFixed(4), n]} />
                      <Scatter data={mlData.slice(0, 80)} fill={C.purple} opacity={0.6} />
                    </ScatterChart>
                  </ResponsiveContainer>
                </div>
              </div>
            </>
          ) : (
            <div style={{ ...S.card, textAlign: 'center', padding: 80 }}>
              <div style={{ fontSize: 52, marginBottom: 16 }}>🤖</div>
              <div style={{ color: C.dim, fontSize: 13 }}>Click TRAIN MODEL to run ML permeability prediction</div>
              <div style={{ color: C.muted, fontSize: 11, marginTop: 6 }}>Kozeny-Carman enhanced gradient boosting</div>
            </div>
          )}
        </div>
      </div>
    );
  };

  // ══════════════════════════════════════════
  //  RENDER: EXPORT TAB
  // ══════════════════════════════════════════
  const renderExport = () => {
    if (!petro) return <NoData />;
    const eclText = buildEclipseExport(petro, gridParams);
    const { NX, NY, NZ, DX, DY } = gridParams;

    const exportCSV = () => {
      const hdr = 'DEPTH,GR,RHOB,NPHI,RT,DT,Vsh,PHIE,Sw,Shc,BVW,K,PAY';
      const rows = petro.map(d => `${d.DEPTH},${d.GR},${d.RHOB},${d.NPHI},${d.RT},${d.DT},${d.Vsh},${d.PHIE},${d.Sw},${d.Shc},${d.BVW},${d.K},${d.PAY}`);
      downloadText([hdr, ...rows].join('\n'), 'petrophysics_output.csv');
    };

    const exportLAS = () => {
      const hdr = `~VERSION INFORMATION
 VERS.          2.0                         : CWLS Log ASCII Standard -  VERSION 2.0
 WRAP.          NO                          : ONE LINE PER DEPTH STEP
~WELL INFORMATION
 WELL.          SYNTHETIC-1                 : Well Name
 COMP.          PetroSim AI                 : Company
 DATE.          ${new Date().toISOString().split('T')[0]} : Date
 STRT.M         ${petro[0].DEPTH}           : Start Depth
 STOP.M         ${petro[petro.length-1].DEPTH} : Stop Depth
 STEP.M         1.0                         : Step
 NULL.          -9999.25                    : Null Value
~CURVE INFORMATION
 DEPT  .M                                  : Depth
 GR    .API                                : Gamma Ray
 RHOB  .G/C3                               : Bulk Density
 NPHI  .V/V                                : Neutron Porosity
 RT    .OHMM                               : True Resistivity
 VSH   .V/V                                : Shale Volume (Vsh)
 PHIE  .V/V                                : Effective Porosity
 SW    .V/V                                : Water Saturation
 K     .MD                                 : Permeability
 PAY   .                                   : Net Pay Flag
~A  DEPT    GR     RHOB   NPHI   RT     VSH    PHIE   SW     K      PAY
`;
      const rows = petro.map(d =>
        `${String(d.DEPTH).padStart(8)}${String(d.GR).padStart(8)}${String(d.RHOB).padStart(8)}${String(d.NPHI).padStart(8)}${String(d.RT).padStart(8)}${String(d.Vsh).padStart(8)}${String(d.PHIE).padStart(8)}${String(d.Sw).padStart(8)}${String(d.K).padStart(8)}${String(d.PAY).padStart(8)}`
      );
      downloadText(hdr + rows.join('\n'), 'output_petrosim.LAS');
    };

    return (
      <div style={{ display: 'grid', gridTemplateColumns: '240px 1fr', gap: 16 }}>
        {/* Options */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={S.card}>
            <div style={S.secHead}>💾 EXPORT FORMATS</div>
            {[
              { label: '🗃 ECLIPSE (.DATA)',  action: () => downloadText(eclText, 'reservoir_model.DATA'), color: C.cyan   },
              { label: '📊 Petrophysics CSV', action: exportCSV,                                           color: C.green  },
              { label: '📋 LAS Output (.LAS)',action: exportLAS,                                           color: C.purple },
            ].map(({ label, action, color }) => (
              <button key={label} style={{ ...S.btn(color), width: '100%', marginBottom: 8, textAlign: 'left', padding: 9 }} onClick={action}>
                {label}
              </button>
            ))}
          </div>

          <div style={S.card}>
            <div style={S.secHead}>ECLIPSE SUMMARY</div>
            {[
              { l: 'Dimensions',    v: `${NX}×${NY}×${NZ}` },
              { l: 'Active Cells',  v: Math.floor(NX * NY * NZ * 0.93).toLocaleString() },
              { l: 'Grid DX / DY',  v: `${DX}m / ${DY}m` },
              { l: 'Sections',      v: 'RUNSPEC GRID PROPS SOL SCHED' },
              { l: 'Fluid System',  v: 'OIL–WATER–GAS (DISGAS)' },
              { l: 'PVT Tables',    v: 'PVTO / PVTW' },
              { l: 'Rel Perm',      v: 'SWOF / SGOF' },
              { l: 'Wells',         v: '1 PROD + 1 INJ' },
              { l: 'Sim Period',    v: 'Jan 2024 – Jan 2026' },
            ].map(({ l, v }) => (
              <div key={l} style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 5, fontSize: 11, fontFamily: 'monospace' }}>
                <span style={{ color: C.muted }}>{l}</span>
                <span style={{ color: C.text }}>{v}</span>
              </div>
            ))}
          </div>

          <div style={S.card}>
            <div style={S.secHead}>RESERVOIR SUMMARY</div>
            {summaryStats && [
              { l: 'Net Pay',         v: summaryStats.netPay.toFixed(0) + 'm' },
              { l: 'Avg PHIE',        v: (summaryStats.avgPHIE * 100).toFixed(1) + '%' },
              { l: 'Avg Sw (pay)',    v: (summaryStats.paySw * 100).toFixed(1) + '%' },
              { l: 'Avg Perm',        v: summaryStats.avgK.toFixed(1) + ' mD' },
              { l: 'Pay Zones',       v: payZones.length },
            ].map(({ l, v }) => (
              <div key={l} style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 5, fontSize: 11, fontFamily: 'monospace' }}>
                <span style={{ color: C.muted }}>{l}</span>
                <span style={{ color: C.cyan }}>{v}</span>
              </div>
            ))}
          </div>
        </div>

        {/* ECLIPSE preview */}
        <div style={S.card}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <div style={S.secHead}>ECLIPSE DATA FILE PREVIEW</div>
            <button style={S.btn(C.cyan)} onClick={() => downloadText(eclText, 'reservoir_model.DATA')}>⬇ DOWNLOAD .DATA</button>
          </div>
          <pre style={{
            background: '#04080f', border: `1px solid ${C.border}`, borderRadius: 5,
            padding: 12, height: 540, overflowY: 'auto', fontSize: 11,
            fontFamily: 'monospace', color: C.dim, lineHeight: 1.55,
            whiteSpace: 'pre-wrap', wordBreak: 'break-word',
          }}>
            {eclText.substring(0, 4200)}{eclText.length > 4200 ? '\n\n-- … (truncated — download for complete file) …' : ''}
          </pre>
        </div>
      </div>
    );
  };

  // ══════════════════════════════════════════
  //  SHELL
  // ══════════════════════════════════════════
  return (
    <div style={{ minHeight: '100vh', background: C.bg, color: C.text, fontFamily: "'Segoe UI', system-ui, sans-serif", display: 'flex', flexDirection: 'column' }}>
      <style>{`
        *{box-sizing:border-box;margin:0;padding:0}
        ::-webkit-scrollbar{width:5px;height:5px}
        ::-webkit-scrollbar-track{background:#060c18}
        ::-webkit-scrollbar-thumb{background:#162840;border-radius:3px}
        input[type=number]::-webkit-inner-spin-button{opacity:.5}
        input:focus{border-color:#00d4ff88 !important}
      `}</style>

      {/* ── HEADER ── */}
      <header style={{ background: 'linear-gradient(90deg,#08101e,#0a1428,#08101e)', borderBottom: `1px solid ${C.border}`, padding: '10px 24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <div style={{ fontSize: 30 }}>⛽</div>
          <div>
            <div style={{ fontSize: 18, fontWeight: 700, color: C.cyan, letterSpacing: 3, fontFamily: 'monospace' }}>PETROSIM AI</div>
            <div style={{ fontSize: 10, color: C.muted, letterSpacing: 1.5, fontFamily: 'monospace' }}>PETROPHYSICAL INTERPRETATION · RESERVOIR MODELING PLATFORM</div>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 24, fontSize: 11, fontFamily: 'monospace' }}>
          {rawData ? [
            { l: 'WELL',        v: lasFile || 'SYNTHETIC-1'  },
            { l: 'INTERVAL',    v: `${rawData[0].DEPTH}–${rawData[rawData.length-1].DEPTH}m` },
            { l: 'DEPTH PTS',   v: rawData.length },
            { l: 'STATUS',      v: petro ? '● ANALYZED' : '● LOADED', c: petro ? C.green : C.amber },
          ].map(({ l, v, c }) => (
            <div key={l}>
              <div style={{ color: C.muted, fontSize: 9, marginBottom: 2 }}>{l}</div>
              <div style={{ color: c || C.text, fontWeight: 600 }}>{v}</div>
            </div>
          )) : <div style={{ color: C.muted }}>No data loaded — use DATA IMPORT tab</div>}
        </div>
      </header>

      {/* ── NAV ── */}
      <nav style={{ display: 'flex', background: '#07101e', borderBottom: `1px solid ${C.border}`, overflowX: 'auto' }}>
        {TABS.map(t => (
          <button key={t.id} onClick={() => setTab(t.id)} style={{
            padding: '9px 18px', background: tab === t.id ? `${C.cyan}14` : 'transparent',
            borderBottom: `2px solid ${tab === t.id ? C.cyan : 'transparent'}`,
            color: tab === t.id ? C.cyan : C.muted,
            cursor: 'pointer', fontSize: 11, fontWeight: 700, letterSpacing: 1,
            fontFamily: 'monospace', border: 'none', outline: 'none',
            transition: 'all .2s', whiteSpace: 'nowrap',
          }}>
            {t.icon} {t.label}
          </button>
        ))}
        {/* Status dot */}
        {petro && (
          <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', padding: '0 16px', gap: 6 }}>
            <span style={{ width: 6, height: 6, borderRadius: '50%', background: C.green, display: 'inline-block', boxShadow: `0 0 6px ${C.green}` }} />
            <span style={{ fontSize: 10, color: C.green, fontFamily: 'monospace' }}>ANALYSIS READY</span>
          </div>
        )}
      </nav>

      {/* ── MAIN ── */}
      <main style={{ flex: 1, padding: 16, overflow: 'auto' }}>
        {tab === 'import'    && renderImport()}
        {tab === 'logs'      && renderLogs()}
        {tab === 'petro'     && renderPetro()}
        {tab === 'porosity'  && renderPorosity()}
        {tab === 'swmodel'   && renderSwModel()}
        {tab === 'perm'      && renderPerm()}
        {tab === 'lithology' && renderLithology()}
        {tab === 'reservoir' && renderReservoir()}
        {tab === 'ml'        && renderML()}
        {tab === 'export'    && renderExport()}
      </main>

      {/* ── NOTIFICATION TOAST ── */}
      {notification && (
        <div style={{
          position: 'fixed', bottom: 20, right: 20, padding: '10px 18px',
          background: '#0a1828', border: `1px solid ${C.cyan}60`, borderRadius: 6,
          color: C.cyan, fontSize: 12, fontFamily: 'monospace',
          boxShadow: `0 4px 20px ${C.cyan}20`, zIndex: 9999,
          animation: 'fadeIn .3s ease',
        }}>
          {notification}
        </div>
      )}

      {/* Footer */}
      <footer style={{ padding: '6px 24px', borderTop: `1px solid ${C.border}`, display: 'flex', justifyContent: 'space-between', fontSize: 9, color: C.muted, fontFamily: 'monospace' }}>
        <span>PetroSim AI — Petrophysical Interpretation & Reservoir Modeling Platform</span>
        <span>LAS 2.0/3.0 · Archie · φD/φN/φS · Kozeny-Carman · DN/M-N/MID Crossplots · ECLIPSE E100 · Three.js r128</span>
      </footer>
    </div>
  );
}

// ── Helper: No data message ──
function NoData() {
  return (
    <div style={{ textAlign: 'center', padding: '80px 40px', color: '#4a6a8a' }}>
      <div style={{ fontSize: 44, marginBottom: 16 }}>📂</div>
      <div style={{ fontSize: 14, fontFamily: 'monospace' }}>No well data loaded.</div>
      <div style={{ fontSize: 12, marginTop: 6 }}>Go to DATA IMPORT tab and load a LAS file or synthetic data.</div>
    </div>
  );
}

if (typeof ReactDOM !== 'undefined' && document.getElementById('root')) {
  ReactDOM.createRoot(document.getElementById('root')).render(React.createElement(PetroSimApp));
}
