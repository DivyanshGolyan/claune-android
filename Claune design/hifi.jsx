// Hi-fi Bottom Capsule — 3 directions × 7 hero screens
// Task: order margherita pizza from Di Fara, deliver 7pm
// Directions: A Soft Kraft · B Mono Signal · C Editorial

const PHONE_W = 320;
const PHONE_H = 680;

// ─────────────────────────────────────────────────────────────
// THEMES
// ─────────────────────────────────────────────────────────────
const THEMES = {
  kraft: {
    name: 'Soft Kraft',
    tag: 'warm · organic · premium',
    bg: '#f3ede1',
    bgDeep: '#e8dfce',
    ink: '#1d2a22',
    inkSoft: '#4a574d',
    inkFaint: '#8a8777',
    rule: '#d4c9b0',
    accent: '#2d5a3f', // forest green
    accentSoft: '#c7d8ca',
    accentDeep: '#1a3d28',
    display: `"Fraunces", "Cormorant Garamond", Georgia, serif`,
    displayWeight: 500,
    displayFeat: `"opsz" 144, "SOFT" 50, "WONK" 1`,
    body: `"Inter", system-ui, sans-serif`,
    mono: `"JetBrains Mono", ui-monospace, monospace`,
    radiusLg: 28,
    radiusMd: 18,
    radiusSm: 10,
  },
  mono: {
    name: 'Mono Signal',
    tag: 'brutalist · precise · warm',
    bg: '#f5f2ec',
    bgDeep: '#ebe6dc',
    ink: '#0f0e0c',
    inkSoft: '#3a3832',
    inkFaint: '#7a7770',
    rule: '#c8c2b4',
    accent: '#e24a2e', // coral
    accentSoft: '#f8d6cb',
    accentDeep: '#b23a23',
    display: `"JetBrains Mono", ui-monospace, monospace`,
    displayWeight: 500,
    body: `"JetBrains Mono", ui-monospace, monospace`,
    mono: `"JetBrains Mono", ui-monospace, monospace`,
    radiusLg: 4,
    radiusMd: 2,
    radiusSm: 0,
  },
  editorial: {
    name: 'Editorial',
    tag: 'magazine · asymmetric · ink',
    bg: '#f7f4ee',
    bgDeep: '#ece6d6',
    ink: '#14182b',
    inkSoft: '#3f4358',
    inkFaint: '#8a8d9f',
    rule: '#c5c0b1',
    accent: '#2a3fbe', // ink blue
    accentSoft: '#c9d0ec',
    accentDeep: '#1c2d8a',
    display: `"Instrument Serif", "Playfair Display", Georgia, serif`,
    displayWeight: 400,
    body: `"Inter", system-ui, sans-serif`,
    mono: `"JetBrains Mono", ui-monospace, monospace`,
    radiusLg: 20,
    radiusMd: 12,
    radiusSm: 6,
  },
};

// ─────────────────────────────────────────────────────────────
// PRIMITIVES (theme-aware)
// ─────────────────────────────────────────────────────────────
const useT = () => React.useContext(ThemeCtx);
const ThemeCtx = React.createContext(THEMES.kraft);

const Display = ({ children, size = 32, c, style, italic }) => {
  const T = useT();
  return <div style={{
    fontFamily: T.display,
    fontWeight: T.displayWeight,
    fontSize: size,
    fontStyle: italic ? 'italic' : 'normal',
    fontVariationSettings: T.displayFeat,
    color: c || T.ink,
    lineHeight: 1.05,
    letterSpacing: T === THEMES.editorial ? -0.5 : -0.2,
    ...style,
  }}>{children}</div>;
};

const Body = ({ children, size = 15, c, style, weight = 400 }) => {
  const T = useT();
  return <span style={{
    fontFamily: T.body,
    fontSize: size,
    fontWeight: weight,
    color: c || T.ink,
    lineHeight: 1.4,
    ...style,
  }}>{children}</span>;
};

const Label = ({ children, size = 10, c, style, weight = 500 }) => {
  const T = useT();
  return <span style={{
    fontFamily: T.mono,
    fontSize: size,
    fontWeight: weight,
    color: c || T.inkSoft,
    letterSpacing: T === THEMES.mono ? 0.1 : 1.5,
    textTransform: 'uppercase',
    ...style,
  }}>{children}</span>;
};

// ─────────────────────────────────────────────────────────────
// ICONS
// ─────────────────────────────────────────────────────────────
const Mic = ({ s = 22, c = 'currentColor' }) => (
  <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <rect x="9" y="3" width="6" height="12" rx="3"/>
    <path d="M5 11a7 7 0 0014 0M12 18v3"/>
  </svg>
);
const Kbd = ({ s = 20, c = 'currentColor' }) => (
  <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
    <rect x="2" y="6" width="20" height="12" rx="2"/>
    <path d="M6 10h0M10 10h0M14 10h0M18 10h0M6 14h12"/>
  </svg>
);
const Check = ({ s = 16, c = 'currentColor' }) => (
  <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
    <path d="M4 12l5 5L20 6"/>
  </svg>
);
const Chev = ({ s = 14, c = 'currentColor', dir = 'up' }) => (
  <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
    style={{ transform: dir === 'up' ? 'none' : dir === 'down' ? 'rotate(180deg)' : dir === 'right' ? 'rotate(90deg)' : 'rotate(-90deg)' }}>
    <path d="M6 15l6-6 6 6"/>
  </svg>
);
const Arr = ({ s = 14, c = 'currentColor' }) => (
  <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="M5 12h14M13 6l6 6-6 6"/>
  </svg>
);

// ─────────────────────────────────────────────────────────────
// PHONE SHELL
// ─────────────────────────────────────────────────────────────
const Phone = ({ children, label }) => {
  const T = useT();
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 14 }}>
      <div style={{
        width: PHONE_W, height: PHONE_H, borderRadius: 44,
        border: `1px solid ${T.rule}`,
        background: T.bg, position: 'relative', overflow: 'hidden',
        boxShadow: '0 30px 60px -20px rgba(20,16,12,0.25), 0 8px 20px -8px rgba(20,16,12,0.12)',
      }}>
        {/* status bar */}
        <div style={{
          height: 34, display: 'flex', justifyContent: 'space-between',
          alignItems: 'center', padding: '0 22px',
          fontFamily: T.mono, fontSize: 11, color: T.ink,
          position: 'relative', zIndex: 5,
        }}>
          <span style={{ fontWeight: 500 }}>9:30</span>
          <div style={{ width: 6, height: 6, borderRadius: 6, background: T.ink, opacity: 0.5 }}/>
          <span style={{ display: 'flex', gap: 4, opacity: 0.7 }}>
            <span style={{ letterSpacing: -1 }}>◢◣</span>
            <span>▫</span>
          </span>
        </div>
        <div style={{ position: 'absolute', inset: '34px 0 20px 0', overflow: 'hidden' }}>
          {children}
        </div>
        <div style={{
          position: 'absolute', bottom: 8, left: '50%', transform: 'translateX(-50%)',
          width: 100, height: 4, borderRadius: 2,
          background: T.ink, opacity: 0.25,
        }}/>
      </div>
      <div style={{ textAlign: 'center' }}>
        <Label size={10} c={T.inkFaint}>{label}</Label>
      </div>
    </div>
  );
};

// ─────────────────────────────────────────────────────────────
// LIVE APP — realistic Uber Eats-like food ordering mock
// ─────────────────────────────────────────────────────────────
const LiveFoodApp = ({ state = 'browsing' }) => {
  // state: browsing, item-selected, checkout
  return (
    <div style={{ height: '100%', background: '#ffffff', position: 'relative', overflow: 'hidden', fontFamily: 'Inter, system-ui, sans-serif' }}>
      {/* app nav */}
      <div style={{
        height: 48, background: '#fff', borderBottom: '1px solid #eee',
        display: 'flex', alignItems: 'center', padding: '0 16px', gap: 12,
        position: 'relative', zIndex: 1,
      }}>
        <div style={{ width: 18, height: 14, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div style={{ height: 2, background: '#111', borderRadius: 1 }}/>
          <div style={{ height: 2, background: '#111', borderRadius: 1, width: '70%' }}/>
          <div style={{ height: 2, background: '#111', borderRadius: 1 }}/>
        </div>
        <div style={{ fontSize: 13, fontWeight: 600, color: '#111' }}>Home · 7pm</div>
        <div style={{ flex: 1 }}/>
        <div style={{ width: 26, height: 26, borderRadius: 13, background: '#f0ece4', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, fontWeight: 600, color: '#333' }}>A</div>
      </div>

      {/* restaurant hero */}
      <div style={{ position: 'relative', height: 130, background: 'linear-gradient(135deg, #8b4513 0%, #c2691c 40%, #e89a3c 100%)' }}>
        {/* stylized pizza illustration */}
        <svg width="100%" height="130" style={{ position: 'absolute', inset: 0 }}>
          <circle cx="80%" cy="45%" r="65" fill="#f4c478"/>
          <circle cx="80%" cy="45%" r="55" fill="#d97148"/>
          <circle cx="80%" cy="45%" r="48" fill="#f0d5a3"/>
          {[[72,38],[85,35],[78,50],[88,52],[70,48],[82,45]].map(([cx,cy],i)=>(
            <circle key={i} cx={`${cx}%`} cy={`${cy}%`} r="4" fill="#b92c28" opacity="0.9"/>
          ))}
          {[[74,42],[86,48],[80,40]].map(([cx,cy],i)=>(
            <ellipse key={i} cx={`${cx}%`} cy={`${cy}%`} rx="3" ry="2" fill="#2d5a3f"/>
          ))}
        </svg>
        <div style={{
          position: 'absolute', bottom: 10, left: 16, color: '#fff',
          textShadow: '0 1px 4px rgba(0,0,0,0.4)',
        }}>
          <div style={{ fontSize: 20, fontWeight: 700, letterSpacing: -0.3 }}>Di Fara Pizza</div>
          <div style={{ fontSize: 11, opacity: 0.95, marginTop: 2 }}>★ 4.8 · 25–35 min · $$</div>
        </div>
      </div>

      {/* category tabs */}
      <div style={{ display: 'flex', gap: 16, padding: '10px 16px', borderBottom: '1px solid #f0f0f0', background: '#fff', overflowX: 'hidden' }}>
        {['Popular', 'Classic', 'Specialty', 'Drinks'].map((t, i) => (
          <div key={t} style={{
            fontSize: 13, fontWeight: i === 1 ? 600 : 400,
            color: i === 1 ? '#111' : '#888',
            borderBottom: i === 1 ? '2px solid #111' : 'none',
            paddingBottom: 4, whiteSpace: 'nowrap',
          }}>{t}</div>
        ))}
      </div>

      {/* menu items */}
      <div style={{ padding: '0 16px' }}>
        {[
          ['Margherita', 'Tomato, mozzarella, basil, olive oil', '$18', true],
          ['Marinara', 'Tomato, garlic, oregano, anchovy', '$15', false],
          ['Quattro Formaggi', 'Four-cheese blend, honey', '$22', false],
          ['Diavola', 'Spicy salami, mozzarella, chili', '$20', false],
        ].map(([name, desc, price, sel], i) => (
          <div key={i} style={{
            padding: '12px 0',
            borderBottom: i < 3 ? '1px solid #f0f0f0' : 'none',
            display: 'flex', gap: 12, alignItems: 'center',
            background: state === 'item-selected' && sel ? '#fff8f0' : 'transparent',
            margin: state === 'item-selected' && sel ? '0 -16px' : 0,
            padding: state === 'item-selected' && sel ? '12px 16px' : '12px 0',
            borderLeft: state === 'item-selected' && sel ? '3px solid #e24a2e' : 'none',
          }}>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 14, fontWeight: 600, color: '#111' }}>{name}</div>
              <div style={{ fontSize: 11, color: '#888', marginTop: 2, lineHeight: 1.3 }}>{desc}</div>
              <div style={{ fontSize: 13, fontWeight: 600, color: '#111', marginTop: 6 }}>{price}</div>
            </div>
            <div style={{
              width: 60, height: 60, borderRadius: 8,
              background: `hsl(${25 + i * 12}, 55%, ${70 - i * 5}%)`,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              position: 'relative',
            }}>
              <div style={{
                position: 'absolute', inset: 10, borderRadius: '50%',
                background: `hsl(${20 + i * 10}, 50%, ${55 - i * 4}%)`,
              }}/>
            </div>
          </div>
        ))}
      </div>

      {/* bottom checkout bar if in checkout state */}
      {state === 'checkout' && (
        <div style={{
          position: 'absolute', bottom: 80, left: 16, right: 16,
          background: '#111', color: '#fff', borderRadius: 12,
          padding: '14px 16px', display: 'flex', alignItems: 'center', gap: 10,
        }}>
          <div style={{
            width: 22, height: 22, borderRadius: 11, background: '#e24a2e',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 11, fontWeight: 700,
          }}>1</div>
          <div style={{ fontSize: 13, fontWeight: 500 }}>View cart · 1 item</div>
          <div style={{ flex: 1 }}/>
          <div style={{ fontSize: 13, fontWeight: 600 }}>$18.00</div>
        </div>
      )}
    </div>
  );
};

// ─────────────────────────────────────────────────────────────
// BOTTOM CAPSULE — idle PTT bar (theme-aware)
// ─────────────────────────────────────────────────────────────
const Capsule = ({ label = 'hold to talk', state = 'idle', onLiveApp = false }) => {
  const T = useT();
  const active = state === 'listening';
  const working = state === 'working';

  // when floating over live app, use dark bg for contrast
  const barBg = working ? T.ink : active ? T.accent : (onLiveApp ? T.ink : T.bg);
  const fg = working || active || onLiveApp ? T.bg : T.ink;
  const borderC = working || active || onLiveApp ? 'transparent' : T.ink;

  return (
    <div style={{
      position: 'absolute', left: 14, right: 14, bottom: 16, zIndex: 10,
      display: 'flex', gap: 8, alignItems: 'center',
    }}>
      <div style={{
        flex: 1, height: 56, borderRadius: T.radiusLg,
        border: `1.5px solid ${borderC}`,
        background: barBg, color: fg,
        display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10,
        boxShadow: onLiveApp ? '0 8px 24px rgba(0,0,0,0.25)' : 'none',
        transition: 'all 0.2s',
      }}>
        {working && <div style={{ width: 7, height: 7, borderRadius: 7, background: T.accent }}/>}
        <Mic s={20} c={fg}/>
        <Body size={15} c={fg} weight={500}>{label}</Body>
      </div>
      <div style={{
        width: 56, height: 56, borderRadius: T.radiusLg,
        border: `1.5px solid ${onLiveApp ? 'transparent' : T.ink}`,
        background: onLiveApp ? T.ink : T.bg,
        color: onLiveApp ? T.bg : T.ink,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        boxShadow: onLiveApp ? '0 8px 24px rgba(0,0,0,0.25)' : 'none',
      }}>
        <Kbd s={20}/>
      </div>
    </div>
  );
};

// ─────────────────────────────────────────────────────────────
// SCREEN: IDLE
// ─────────────────────────────────────────────────────────────
const Idle = () => {
  const T = useT();
  return (
    <div style={{ height: '100%', padding: '24px 22px 96px', display: 'flex', flexDirection: 'column' }}>
      <Label size={10} c={T.inkFaint}>FRI APR 18 · 9:30</Label>
      <div style={{ marginTop: 8 }}>
        {T === THEMES.editorial ? (
          <>
            <Display size={40} italic>what do you</Display>
            <Display size={40}>need done?</Display>
          </>
        ) : T === THEMES.mono ? (
          <Display size={26}>
            what do you<br/>
            need done<span style={{ color: T.accent }}>?</span>
          </Display>
        ) : (
          <>
            <Display size={36}>what do you</Display>
            <Display size={36} italic>need done?</Display>
          </>
        )}
      </div>

      <div style={{ marginTop: 28 }}>
        <Label size={10}>PICK UP WHERE YOU LEFT OFF</Label>
        <div style={{
          marginTop: 10, padding: 14,
          borderRadius: T.radiusMd,
          border: `1px solid ${T.rule}`,
          background: T.bgDeep,
        }}>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
            <div style={{ width: 6, height: 6, borderRadius: 3, background: T.accent }}/>
            <Body size={14} weight={500}>Dinner for tonight</Body>
          </div>
          <Body size={13} c={T.inkSoft} style={{ display: 'block', marginTop: 4 }}>
            You asked about pizza yesterday. Continue?
          </Body>
        </div>
      </div>

      <div style={{ marginTop: 22 }}>
        <Label size={10}>RECENT</Label>
        <div style={{ marginTop: 8 }}>
          {['ordered sushi from Nori', 'booked haircut thursday', 'paid phone bill'].map((t, i) => (
            <div key={i} style={{
              padding: '10px 0',
              borderBottom: `1px solid ${T.rule}`,
              display: 'flex', alignItems: 'center',
            }}>
              <Body size={14}>{t}</Body>
              <div style={{ flex: 1 }}/>
              <Arr s={14} c={T.inkFaint}/>
            </div>
          ))}
        </div>
      </div>

      <div style={{ flex: 1 }}/>
      <Capsule label="hold to talk"/>
    </div>
  );
};

// ─────────────────────────────────────────────────────────────
// SCREEN: LISTENING
// ─────────────────────────────────────────────────────────────
const Listening = () => {
  const T = useT();
  return (
    <div style={{ height: '100%', padding: '24px 22px 96px', display: 'flex', flexDirection: 'column', background: T.bgDeep }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <div style={{
          width: 8, height: 8, borderRadius: 8, background: T.accent,
          boxShadow: `0 0 0 4px ${T.accentSoft}`,
        }}/>
        <Label size={10} c={T.accent}>LISTENING · 00:04</Label>
      </div>

      {/* big waveform */}
      <div style={{ marginTop: 36, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 4, height: 100 }}>
        {[14,32,52,22,68,84,46,96,62,40,72,30,54,22,14].map((h,i) => (
          <div key={i} style={{
            width: 5, height: h, borderRadius: 3,
            background: T.accent,
            opacity: 0.3 + (h/96)*0.7,
          }}/>
        ))}
      </div>

      <div style={{ marginTop: 36 }}>
        <Label size={10} c={T.inkFaint}>TRANSCRIPT</Label>
        <div style={{ marginTop: 8 }}>
          {T === THEMES.editorial ? (
            <Display size={26} italic>"order a margherita</Display>
          ) : (
            <Display size={22}>"order a margherita</Display>
          )}
          <Display size={T === THEMES.editorial ? 26 : 22} italic={T === THEMES.editorial}>
            from di fara —
          </Display>
          <Display size={T === THEMES.editorial ? 26 : 22} italic={T === THEMES.editorial}>
            deliver at seven"
          </Display>
          <span style={{
            display: 'inline-block', marginLeft: 3,
            width: 2, height: 24, background: T.accent,
            verticalAlign: 'middle', animation: 'blink 1s steps(2) infinite',
          }}/>
        </div>
      </div>

      <div style={{ flex: 1 }}/>
      <Capsule label="release to send" state="listening"/>
    </div>
  );
};

// ─────────────────────────────────────────────────────────────
// SCREEN: AMBIENT (agent working over live app)
// ─────────────────────────────────────────────────────────────
const Ambient = () => {
  const T = useT();
  return (
    <div style={{ height: '100%', position: 'relative' }}>
      <LiveFoodApp state="item-selected"/>
      {/* top thin status bar */}
      <div style={{
        position: 'absolute', top: 0, left: 0, right: 0,
        height: 3, background: T.accent, zIndex: 4,
      }}/>
      <div style={{
        position: 'absolute', top: 12, left: '50%', transform: 'translateX(-50%)',
        display: 'flex', alignItems: 'center', gap: 8,
        padding: '6px 12px',
        background: T.ink, color: T.bg,
        borderRadius: T.radiusLg,
        boxShadow: '0 4px 12px rgba(0,0,0,0.2)', zIndex: 4,
      }}>
        <div style={{
          width: 6, height: 6, borderRadius: 6, background: T.accent,
        }}/>
        <Label size={9} c={T.bg}>WORKING · 0:47 · STEP 3/5</Label>
      </div>

      {/* bottom capsule as minimized dark status */}
      <Capsule label="picking margherita — hold to steer" state="working" onLiveApp/>
    </div>
  );
};

// ─────────────────────────────────────────────────────────────
// SCREEN: ASSIST
// ─────────────────────────────────────────────────────────────
const Assist = () => {
  const T = useT();
  return (
    <div style={{ height: '100%', position: 'relative' }}>
      <LiveFoodApp state="item-selected"/>
      <div style={{ position: 'absolute', inset: 0, background: 'rgba(20,16,12,0.35)' }}/>

      <div style={{
        position: 'absolute', left: 14, right: 14, bottom: 16,
        padding: 18, borderRadius: T.radiusLg,
        background: T.ink, color: T.bg,
        boxShadow: '0 20px 40px rgba(0,0,0,0.35)',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{
            width: 8, height: 8, borderRadius: 8, background: T.accent,
          }}/>
          <Label size={9} c={T.accent}>ASSIST · 0:47</Label>
          <div style={{ flex: 1 }}/>
          <Label size={9} c={T.bg} style={{ opacity: 0.6 }}>1/1</Label>
        </div>

        <div style={{ marginTop: 10 }}>
          {T === THEMES.editorial ? (
            <Display size={22} c={T.bg} italic>Di Fara's closed —</Display>
          ) : (
            <Display size={18} c={T.bg}>Di Fara's closed tonight.</Display>
          )}
          <Body size={14} c={T.bg} style={{ display: 'block', marginTop: 6, opacity: 0.75 }}>
            Try L&B Spumoni ($19, 30min) or Roberta's ($22, 40min)?
          </Body>
        </div>

        <div style={{ display: 'flex', gap: 8, marginTop: 14, flexWrap: 'wrap' }}>
          {[
            ['L&B', false],
            ['Roberta\'s', false],
            ['cancel', false],
          ].map(([t, filled], i) => (
            <div key={i} style={{
              flex: 1, textAlign: 'center',
              padding: '10px 8px',
              borderRadius: T.radiusMd,
              border: `1px solid ${T.bg}`,
              background: filled ? T.bg : 'transparent',
              color: filled ? T.ink : T.bg,
            }}>
              <Body size={13} weight={500} c={filled ? T.ink : T.bg}>{t}</Body>
            </div>
          ))}
        </div>

        <div style={{
          marginTop: 14, paddingTop: 12,
          borderTop: `1px solid rgba(255,255,255,0.15)`,
          display: 'flex', alignItems: 'center', gap: 8,
        }}>
          <Mic s={14} c={T.accent}/>
          <Body size={12} c={T.bg} style={{ opacity: 0.75 }}>or just say which one</Body>
        </div>
      </div>
    </div>
  );
};

// ─────────────────────────────────────────────────────────────
// SCREEN: DONE
// ─────────────────────────────────────────────────────────────
const Done = () => {
  const T = useT();
  return (
    <div style={{ height: '100%', padding: '24px 22px 96px', display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <div style={{
          width: 22, height: 22, borderRadius: 11,
          background: T.accent, color: T.bg,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <Check s={13} c={T.bg}/>
        </div>
        <Label size={10} c={T.accent}>DONE · 2M 14S</Label>
      </div>

      <div style={{ marginTop: 18 }}>
        {T === THEMES.editorial ? (
          <>
            <Display size={40} italic>pizza's</Display>
            <Display size={40}>on its way.</Display>
          </>
        ) : T === THEMES.mono ? (
          <Display size={26}>
            pizza's on<br/>
            its way<span style={{ color: T.accent }}>.</span>
          </Display>
        ) : (
          <>
            <Display size={32}>Pizza's on</Display>
            <Display size={32} italic>the way.</Display>
          </>
        )}
      </div>

      {/* receipt card */}
      <div style={{
        marginTop: 24, padding: 18,
        borderRadius: T.radiusMd,
        background: T.bgDeep,
        border: `1px solid ${T.rule}`,
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
          <div>
            <Label size={9} c={T.inkFaint}>FROM</Label>
            <Body size={15} weight={600} style={{ display: 'block', marginTop: 2 }}>L&B Spumoni Gardens</Body>
          </div>
          <Body size={18} weight={600} c={T.accent}>$19.00</Body>
        </div>

        <div style={{ height: 1, background: T.rule, margin: '14px 0' }}/>

        <div style={{ display: 'flex', gap: 12 }}>
          <div style={{
            width: 56, height: 56, borderRadius: T.radiusSm,
            background: 'linear-gradient(135deg, #c2691c, #e89a3c)',
            position: 'relative', overflow: 'hidden',
          }}>
            <div style={{ position: 'absolute', inset: 6, borderRadius: '50%', background: '#f0d5a3' }}/>
            <div style={{ position: 'absolute', inset: 12, borderRadius: '50%', background: '#d97148' }}/>
            {[[28,22],[36,28],[22,32],[32,36]].map(([cx,cy],i)=>(
              <div key={i} style={{ position: 'absolute', left: cx, top: cy, width: 4, height: 4, borderRadius: 2, background: '#b92c28' }}/>
            ))}
          </div>
          <div style={{ flex: 1 }}>
            <Body size={14} weight={500}>Margherita (large)</Body>
            <Body size={12} c={T.inkSoft} style={{ display: 'block', marginTop: 2 }}>
              Deliver 7:00 PM · 30 min
            </Body>
          </div>
        </div>

        <div style={{ marginTop: 14, display: 'flex', gap: 8 }}>
          {['Track', 'Add to cal', 'Undo'].map((t, i) => (
            <div key={t} style={{
              flex: 1, textAlign: 'center', padding: '8px 4px',
              borderRadius: T.radiusSm,
              border: `1px solid ${T.ink}`,
              background: i === 0 ? T.ink : 'transparent',
              color: i === 0 ? T.bg : T.ink,
            }}>
              <Body size={12} weight={500} c={i === 0 ? T.bg : T.ink}>{t}</Body>
            </div>
          ))}
        </div>
      </div>

      <div style={{ flex: 1 }}/>
      <Capsule label="what's next?"/>
    </div>
  );
};

// ─────────────────────────────────────────────────────────────
// SCREEN: TIMELINE
// ─────────────────────────────────────────────────────────────
const Timeline = () => {
  const T = useT();
  return (
    <div style={{ height: '100%', padding: '24px 22px 96px', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      {T === THEMES.editorial ? (
        <Display size={44} italic>timeline</Display>
      ) : T === THEMES.mono ? (
        <Display size={26}>timeline<span style={{ color: T.accent }}>_</span></Display>
      ) : (
        <Display size={32}>Timeline</Display>
      )}

      <div style={{ display: 'flex', gap: 6, marginTop: 14 }}>
        {['Today', 'Week', 'Month'].map((t, i) => (
          <div key={t} style={{
            padding: '5px 12px', borderRadius: T.radiusLg,
            border: `1px solid ${i === 0 ? T.ink : T.rule}`,
            background: i === 0 ? T.ink : 'transparent',
            color: i === 0 ? T.bg : T.ink,
          }}>
            <Body size={12} weight={500} c={i === 0 ? T.bg : T.ink}>{t}</Body>
          </div>
        ))}
      </div>

      <div style={{ marginTop: 20, overflow: 'auto' }}>
        <Label size={10} c={T.inkFaint}>TODAY</Label>

        {/* expanded featured item */}
        <div style={{
          marginTop: 10,
          padding: 14,
          borderRadius: T.radiusMd,
          background: T.bgDeep,
          border: `1px solid ${T.rule}`,
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
            <Label size={9} c={T.inkFaint}>6:32 PM</Label>
            <Chev dir="up" s={12} c={T.inkSoft}/>
          </div>
          <Body size={15} weight={500} style={{ display: 'block', marginTop: 2 }}>
            Ordered margherita pizza
          </Body>
          <Body size={12} c={T.accent} style={{ display: 'block' }}>
            L&B Spumoni · $19 · 30 min · 5 steps
          </Body>

          <div style={{ marginTop: 10, paddingTop: 10, borderTop: `1px solid ${T.rule}` }}>
            <Label size={9} c={T.inkFaint}>STEPS</Label>
            <div style={{ marginTop: 6 }}>
              {[
                '✓ Opened Uber Eats',
                '✓ Searched Di Fara (closed)',
                '✓ Suggested alternates',
                '✓ Picked L&B Margherita',
                '✓ Paid, delivery 7:00pm',
              ].map((s, i) => (
                <Body key={i} size={12} c={T.inkSoft} style={{ display: 'block', padding: '3px 0' }}>
                  {s}
                </Body>
              ))}
            </div>
          </div>
        </div>

        {[
          ['2:14 PM', 'Rescheduled meeting', 'Mon 3→4pm'],
          ['11:08 AM', 'Texted mom', 'Sent'],
          ['8:41 AM', 'Summarized inbox', '12 mails'],
        ].map(([t, title, sub], i) => (
          <div key={i} style={{
            padding: '14px 2px',
            borderBottom: `1px solid ${T.rule}`,
            display: 'flex', alignItems: 'center',
          }}>
            <div style={{ width: 70 }}>
              <Label size={9} c={T.inkFaint}>{t}</Label>
            </div>
            <div style={{ flex: 1 }}>
              <Body size={14}>{title}</Body>
              <Body size={12} c={T.inkSoft} style={{ display: 'block' }}>{sub}</Body>
            </div>
            <Chev dir="down" s={12} c={T.inkFaint}/>
          </div>
        ))}

        <Label size={10} c={T.inkFaint} style={{ marginTop: 18, display: 'block' }}>YESTERDAY ›</Label>
      </div>

      <Capsule/>
    </div>
  );
};

// ─────────────────────────────────────────────────────────────
// SCREEN: SETTINGS
// ─────────────────────────────────────────────────────────────
const Settings = () => {
  const T = useT();
  return (
    <div style={{ height: '100%', padding: '24px 22px 96px', overflow: 'auto' }}>
      {T === THEMES.editorial ? (
        <Display size={44} italic>settings</Display>
      ) : T === THEMES.mono ? (
        <Display size={26}>settings</Display>
      ) : (
        <Display size={32}>Settings</Display>
      )}

      {[
        ['VOICE', [
          ['Input', 'Hold to talk', true],
          ['Wake word', 'Off', false],
          ['Assistant voice', 'Warm · Fiona', true],
          ['Response speed', '1.0×', true],
        ]],
        ['BEHAVIOR', [
          ['Trust mode', 'Full · no confirms', true],
          ['Assist style', 'Overlay + voice', true],
          ['Show step log', 'After task ends', true],
          ['Budget alerts', '$50+', true],
        ]],
        ['APPEARANCE', [
          ['Theme', 'Light', true],
          ['Accent', '■ ' + T.name, true],
          ['Haptics', 'On', true],
        ]],
      ].map(([h, rows], i) => (
        <div key={h} style={{ marginTop: i === 0 ? 24 : 22 }}>
          <Label size={10} c={T.inkFaint}>{h}</Label>
          <div style={{
            marginTop: 10,
            borderTop: `1px solid ${T.rule}`,
          }}>
            {rows.map(([k, v, on]) => (
              <div key={k} style={{
                padding: '12px 0',
                borderBottom: `1px solid ${T.rule}`,
                display: 'flex', alignItems: 'center',
              }}>
                <Body size={14}>{k}</Body>
                <div style={{ flex: 1 }}/>
                <Body size={13} c={on ? T.ink : T.inkFaint}>{v}</Body>
                <div style={{ width: 12 }}/>
                <Chev dir="right" s={12} c={T.inkFaint}/>
              </div>
            ))}
          </div>
        </div>
      ))}

      <div style={{ height: 40 }}/>
    </div>
  );
};

// ─────────────────────────────────────────────────────────────
// LAYOUT
// ─────────────────────────────────────────────────────────────
const SCREENS = [
  { key: 'idle', label: 'Idle', C: Idle },
  { key: 'listen', label: 'Listening', C: Listening },
  { key: 'ambient', label: 'Ambient · agent working', C: Ambient },
  { key: 'assist', label: 'Assist · agent asks', C: Assist },
  { key: 'done', label: 'Done', C: Done },
  { key: 'timeline', label: 'Timeline', C: Timeline },
  { key: 'settings', label: 'Settings', C: Settings },
];

function App() {
  const [dir, setDir] = React.useState(() => localStorage.getItem('hifi_dir') || 'kraft');
  const [showTweaks, setShowTweaks] = React.useState(false);

  React.useEffect(() => {
    localStorage.setItem('hifi_dir', dir);
  }, [dir]);

  React.useEffect(() => {
    const handler = (e) => {
      if (e.data?.type === '__activate_edit_mode') setShowTweaks(true);
      if (e.data?.type === '__deactivate_edit_mode') setShowTweaks(false);
    };
    window.addEventListener('message', handler);
    window.parent.postMessage({ type: '__edit_mode_available' }, '*');
    return () => window.removeEventListener('message', handler);
  }, []);

  const T = THEMES[dir];

  return (
    <ThemeCtx.Provider value={T}>
      <style>{`
        @keyframes blink { 0%,50% { opacity: 1; } 51%,100% { opacity: 0; } }
        html, body { margin: 0; background: ${T.bg}; }
      `}</style>
      <div style={{
        minHeight: '100vh',
        background: T.bg,
        fontFamily: T.body,
        color: T.ink,
        padding: '56px 40px 80px',
        transition: 'background 0.3s',
      }}>
        {/* header */}
        <div style={{ maxWidth: 1800, margin: '0 auto' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', flexWrap: 'wrap', gap: 24 }}>
            <div>
              <Label size={10} c={T.inkFaint}>HI-FI · BOTTOM CAPSULE · V1</Label>
              <div style={{ marginTop: 8 }}>
                {T === THEMES.editorial ? (
                  <>
                    <Display size={72} italic>A phone that</Display>
                    <Display size={72}>listens, acts, reports back.</Display>
                  </>
                ) : T === THEMES.mono ? (
                  <Display size={44}>
                    a phone that listens,<br/>
                    acts, reports back<span style={{ color: T.accent }}>.</span>
                  </Display>
                ) : (
                  <>
                    <Display size={56}>A phone that listens,</Display>
                    <Display size={56} italic>acts, reports back.</Display>
                  </>
                )}
              </div>
              <Body size={16} c={T.inkSoft} style={{ display: 'block', marginTop: 14, maxWidth: 620 }}>
                Hold the bottom capsule to speak. The agent drives your phone directly — you see the real app while it works.
                Our UI only surfaces in four moments: listening, ambient status, an assist request, and the result.
              </Body>
            </div>
            <div style={{
              padding: 16, border: `1px solid ${T.rule}`,
              borderRadius: T.radiusMd, maxWidth: 300,
            }}>
              <Label size={9} c={T.inkFaint}>DIRECTION</Label>
              <Body size={15} weight={600} style={{ display: 'block', marginTop: 4 }}>{T.name}</Body>
              <Body size={13} c={T.inkSoft} style={{ display: 'block', marginTop: 2 }}>{T.tag}</Body>
              <div style={{ marginTop: 10, paddingTop: 10, borderTop: `1px solid ${T.rule}`, display: 'flex', gap: 8 }}>
                <Body size={11} c={T.inkSoft}>Accent</Body>
                <div style={{ width: 16, height: 16, borderRadius: 3, background: T.accent }}/>
              </div>
            </div>
          </div>

          {/* direction switcher */}
          <div style={{
            marginTop: 40, display: 'flex', gap: 0,
            borderBottom: `1px solid ${T.rule}`,
          }}>
            {Object.entries(THEMES).map(([key, theme]) => (
              <div key={key} onClick={() => setDir(key)} style={{
                padding: '14px 24px', cursor: 'pointer',
                borderBottom: `2px solid ${dir === key ? T.accent : 'transparent'}`,
                marginBottom: -1,
                display: 'flex', flexDirection: 'column', gap: 2,
              }}>
                <Body size={14} weight={600} c={dir === key ? T.ink : T.inkSoft}>
                  {theme.name}
                </Body>
                <Body size={11} c={T.inkFaint}>{theme.tag}</Body>
              </div>
            ))}
            <div style={{ flex: 1 }}/>
            <div style={{ alignSelf: 'center' }}>
              <Label size={10} c={T.inkFaint}>TASK · ORDER MARGHERITA FROM DI FARA, DELIVER 7PM</Label>
            </div>
          </div>

          {/* phone grid */}
          <div style={{
            marginTop: 48,
            display: 'flex', flexWrap: 'wrap', gap: 36,
            justifyContent: 'center',
          }}>
            {SCREENS.map(({ key, label, C }) => (
              <Phone key={key} label={label}>
                <C/>
              </Phone>
            ))}
          </div>

          {/* system / notes */}
          <div style={{ marginTop: 80, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 28, maxWidth: 1400, margin: '80px auto 0' }}>
            <div>
              <Label size={10} c={T.accent}>THE CAPSULE</Label>
              <Display size={22} style={{ marginTop: 8 }} italic={T === THEMES.editorial}>
                One bar, four modes.
              </Display>
              <Body size={14} c={T.inkSoft} style={{ display: 'block', marginTop: 8 }}>
                Idle invites. Listening glows. Working shrinks + darkens. Assist inflates into a full dialog.
                Shape is always the same — the <i>state</i> changes.
              </Body>
            </div>
            <div>
              <Label size={10} c={T.accent}>THE OVERLAY</Label>
              <Display size={22} style={{ marginTop: 8 }} italic={T === THEMES.editorial}>
                We only appear when needed.
              </Display>
              <Body size={14} c={T.inkSoft} style={{ display: 'block', marginTop: 8 }}>
                The agent drives real apps through accessibility APIs. 95% of a task runs with our UI out of the way —
                just a top strip + a dark capsule to confirm it's working.
              </Body>
            </div>
            <div>
              <Label size={10} c={T.accent}>THE RECORD</Label>
              <Display size={22} style={{ marginTop: 8 }} italic={T === THEMES.editorial}>
                Steps live in the timeline.
              </Display>
              <Body size={14} c={T.inkSoft} style={{ display: 'block', marginTop: 8 }}>
                Step-by-step logs, receipts, and undo all collapse into the timeline after a task ends.
                The moment itself stays minimal.
              </Body>
            </div>
          </div>
        </div>

        {/* Tweaks panel */}
        {showTweaks && (
          <div style={{
            position: 'fixed', bottom: 20, right: 20,
            width: 280, padding: 18,
            background: T.bg, color: T.ink,
            border: `1px solid ${T.ink}`,
            borderRadius: T.radiusMd,
            boxShadow: '0 20px 40px rgba(0,0,0,0.2)',
            zIndex: 100,
          }}>
            <Label size={10} c={T.accent}>TWEAKS</Label>
            <Display size={22} style={{ marginTop: 4 }}>Adjust</Display>
            <div style={{ marginTop: 14 }}>
              <Label size={9} c={T.inkFaint}>DIRECTION</Label>
              <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 6 }}>
                {Object.entries(THEMES).map(([key, th]) => (
                  <div key={key} onClick={() => setDir(key)} style={{
                    padding: '8px 10px',
                    borderRadius: T.radiusSm,
                    border: `1px solid ${dir === key ? T.ink : T.rule}`,
                    background: dir === key ? T.bgDeep : 'transparent',
                    display: 'flex', alignItems: 'center', gap: 8,
                    cursor: 'pointer',
                  }}>
                    <div style={{ width: 12, height: 12, borderRadius: 2, background: th.accent }}/>
                    <Body size={13} weight={500}>{th.name}</Body>
                    <div style={{ flex: 1 }}/>
                    {dir === key && <Check s={12} c={T.ink}/>}
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>
    </ThemeCtx.Provider>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
