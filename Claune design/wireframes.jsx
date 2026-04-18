// Voice Computer Use — Android wireframes
// Sketchy low-fi, warm off-white + single burnt-orange accent
// 5 variations × 7 screens each (Idle · Listening · Ambient · Assist · Done · Timeline · Settings)

const COLORS = {
  paper: '#f6f2ec',
  paperDeep: '#eee6d8',
  ink: '#1a1613',
  inkSoft: '#534a40',
  inkFaint: '#9a8f81',
  rule: '#cdbfa8',
  ruleFaint: '#e2d8c5',
  accent: '#c85a1e',
  accentSoft: '#f2c9af',
};

const FONTS = {
  hand: `"Kalam", "Caveat", cursive`,
  mono: `"JetBrains Mono", "Roboto Mono", ui-monospace, monospace`,
};

// ─── primitives ─────────────────────────────────────────────
const Line = ({ w = '100%', c = COLORS.ink, t = 1.5, style }) => (
  <div style={{ width: w, height: t, background: c, ...style }} />
);

const Box = ({ children, style, dashed, thick, radius = 6 }) => (
  <div style={{
    border: `${thick ? 2 : 1.5}px ${dashed ? 'dashed' : 'solid'} ${COLORS.ink}`,
    borderRadius: radius, padding: 10, background: 'transparent',
    ...style,
  }}>{children}</div>
);

const Hand = ({ children, size = 18, c = COLORS.ink, style, italic }) => (
  <span style={{
    fontFamily: FONTS.hand, fontSize: size, color: c,
    fontStyle: italic ? 'italic' : 'normal', lineHeight: 1.2, ...style,
  }}>{children}</span>
);

const Mono = ({ children, size = 10, c = COLORS.inkSoft, style }) => (
  <span style={{
    fontFamily: FONTS.mono, fontSize: size, color: c,
    letterSpacing: 0.3, textTransform: 'uppercase', ...style,
  }}>{children}</span>
);

const Squiggle = ({ w = 40, c = COLORS.ink }) => (
  <svg width={w} height="8" viewBox={`0 0 ${w} 8`} style={{ display: 'block' }}>
    <path d={`M0 4 Q ${w/8} 0, ${w/4} 4 T ${w/2} 4 T ${3*w/4} 4 T ${w} 4`}
      fill="none" stroke={c} strokeWidth="1.5" strokeLinecap="round" />
  </svg>
);

const Dot = ({ size = 6, c = COLORS.ink, style }) => (
  <div style={{
    width: size, height: size, borderRadius: '50%',
    background: c, flexShrink: 0, ...style,
  }} />
);

const Circle = ({ size = 40, c = COLORS.ink, thick = 1.5, fill, style, children }) => (
  <div style={{
    width: size, height: size, borderRadius: '50%',
    border: `${thick}px solid ${c}`, background: fill || 'transparent',
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    flexShrink: 0, ...style,
  }}>{children}</div>
);

const Mic = ({ size = 20, c = COLORS.ink }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <rect x="9" y="3" width="6" height="12" rx="3"/>
    <path d="M5 11a7 7 0 0014 0M12 18v3"/>
  </svg>
);

const Keyb = ({ size = 18, c = COLORS.ink }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="1.8" strokeLinecap="round">
    <rect x="2" y="6" width="20" height="12" rx="2"/>
    <path d="M6 10h0M10 10h0M14 10h0M18 10h0M6 14h12"/>
  </svg>
);

const ArrowR = ({ size = 14, c = COLORS.ink }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="M5 12h14M13 6l6 6-6 6"/>
  </svg>
);

const Check = ({ size = 14, c = COLORS.ink }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M4 12l5 5L20 6"/>
  </svg>
);

const Chevron = ({ size = 14, c = COLORS.ink, dir = 'down' }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
    style={{ transform: dir === 'up' ? 'rotate(180deg)' : dir === 'right' ? 'rotate(-90deg)' : 'none' }}>
    <path d="M6 9l6 6 6-6"/>
  </svg>
);

// ─── phone shell ────────────────────────────────────────────
const PHONE_W = 280;
const PHONE_H = 580;

const Phone = ({ children, label, dark }) => (
  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12 }}>
    <div style={{
      width: PHONE_W, height: PHONE_H, borderRadius: 36,
      border: `2px solid ${COLORS.ink}`,
      background: dark ? '#1a1613' : COLORS.paper,
      position: 'relative', overflow: 'hidden',
      boxShadow: '4px 6px 0 rgba(26,22,19,0.08)',
    }}>
      <div style={{
        height: 24, display: 'flex', justifyContent: 'space-between',
        alignItems: 'center', padding: '0 16px',
        fontFamily: FONTS.mono, fontSize: 9,
        color: dark ? '#f6f2ec' : COLORS.ink, opacity: 0.7,
        position: 'relative', zIndex: 2,
      }}>
        <span>9:30</span>
        <div style={{ width: 6, height: 6, borderRadius: 6, background: dark ? '#f6f2ec' : COLORS.ink, opacity: 0.5 }} />
        <span>••• ▫</span>
      </div>
      <div style={{ position: 'absolute', inset: '24px 0 18px 0', overflow: 'hidden' }}>{children}</div>
      <div style={{
        position: 'absolute', bottom: 6, left: '50%', transform: 'translateX(-50%)',
        width: 80, height: 3, borderRadius: 2,
        background: dark ? '#f6f2ec' : COLORS.ink, opacity: 0.35,
      }} />
    </div>
    <div style={{ textAlign: 'center' }}>
      <Mono size={11} c={COLORS.inkSoft}>{label}</Mono>
    </div>
  </div>
);

// ─── Placeholder "live app" background ─────────────────────
// Shows a stylized Kayak-ish flight-search UI with stripes,
// labeled clearly so the viewer knows: this is the real app
// the agent is operating, NOT our UI.
const LiveApp = ({ children, variant = 'kayak' }) => {
  return (
    <div style={{ height: '100%', position: 'relative', background: '#fff' }}>
      {/* the "live app" — drawn as a subtle placeholder */}
      <div style={{ height: '100%', background: '#fafafa', position: 'relative', overflow: 'hidden' }}>
        {/* app header */}
        <div style={{
          height: 52, background: '#e8eef5', borderBottom: '1px solid #d5dde6',
          display: 'flex', alignItems: 'center', padding: '0 14px', gap: 10,
        }}>
          <Chevron dir="right" size={14} c="#4a5568" />
          <div style={{ fontFamily: FONTS.mono, fontSize: 11, color: '#4a5568', letterSpacing: 0.3 }}>
            KAYAK · SFO→BER
          </div>
          <div style={{ flex: 1 }}/>
          <div style={{ width: 16, height: 16, borderRadius: 8, background: '#c5d0dc' }}/>
        </div>

        {/* filter chips */}
        <div style={{ display: 'flex', gap: 6, padding: '10px 14px', borderBottom: '1px solid #eee' }}>
          {['dates','stops','price','bags'].map(t => (
            <div key={t} style={{
              padding: '4px 10px', border: '1px solid #cbd5e0', borderRadius: 14,
              fontFamily: FONTS.mono, fontSize: 9, color: '#4a5568',
            }}>{t}</div>
          ))}
        </div>

        {/* flight cards — stripey placeholders */}
        {[
          ['UA 932', '09:40 → 10:55+1', '$842', true],
          ['LH 455', '11:20 → 08:10+1', '$698', false],
          ['AF 083', '14:05 → 12:30+1', '$912', false],
        ].map(([airline, time, price, active], i) => (
          <div key={i} style={{
            padding: '12px 14px',
            borderBottom: '1px solid #eee',
            background: active ? '#fef5e7' : '#fff',
            borderLeft: active ? `3px solid ${COLORS.accent}` : '3px solid transparent',
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
              <div style={{ fontFamily: FONTS.mono, fontSize: 11, color: '#2d3748', fontWeight: 600 }}>
                {airline}
              </div>
              <div style={{ fontFamily: FONTS.mono, fontSize: 13, color: active ? COLORS.accent : '#2d3748', fontWeight: 600 }}>
                {price}
              </div>
            </div>
            <div style={{ fontFamily: FONTS.mono, fontSize: 10, color: '#718096', marginTop: 2 }}>
              {time}
            </div>
            {/* striped bar for visual texture */}
            <div style={{
              marginTop: 8, height: 4, borderRadius: 2,
              backgroundImage: 'repeating-linear-gradient(90deg, #cbd5e0 0 8px, transparent 8px 12px)',
            }}/>
          </div>
        ))}

        {/* caption corner — labels this as the real app */}
        <div style={{
          position: 'absolute', top: 6, right: 6,
          padding: '2px 6px', background: 'rgba(26,22,19,0.08)', borderRadius: 4,
        }}>
          <Mono size={8} c="#718096">LIVE APP</Mono>
        </div>
      </div>

      {/* overlay slot */}
      {children}
    </div>
  );
};

// ═══════════════════════════════════════════════════════════════
// SHARED OVERLAY PIECES
// ═══════════════════════════════════════════════════════════════

// A corner pip / status chip that appears while the agent works
const WorkingPip = ({ style, variant = 'chip' }) => {
  if (variant === 'chip') return (
    <div style={{
      display: 'inline-flex', alignItems: 'center', gap: 6,
      padding: '4px 10px 4px 8px',
      background: COLORS.ink, color: COLORS.paper,
      borderRadius: 100, boxShadow: '0 2px 6px rgba(0,0,0,0.2)',
      ...style,
    }}>
      <Dot size={6} c={COLORS.accent} />
      <Mono size={9} c={COLORS.paper}>WORKING · 0:47</Mono>
    </div>
  );
  return null;
};

// A shared "assist" dialog look, with yes/no/talk-back
const AssistCard = ({ style, title = 'nonstops are +$180.', sub = 'keep searching, or take stops?' }) => (
  <div style={{
    background: COLORS.paper, color: COLORS.ink,
    border: `2px solid ${COLORS.ink}`, borderRadius: 16,
    padding: 14, boxShadow: '0 8px 24px rgba(0,0,0,0.18)',
    ...style,
  }}>
    <Mono size={9} c={COLORS.accent}>ASSIST</Mono>
    <Hand size={17} style={{ display: 'block', marginTop: 4 }}>{title}</Hand>
    <Hand size={14} c={COLORS.inkSoft} italic style={{ display: 'block' }}>{sub}</Hand>
    <div style={{ display: 'flex', gap: 6, marginTop: 10, flexWrap: 'wrap' }}>
      {['keep','stops ok','stop'].map(t => (
        <div key={t} style={{
          padding: '4px 10px', borderRadius: 14,
          border: `1.5px solid ${COLORS.ink}`,
        }}>
          <Hand size={13}>{t}</Hand>
        </div>
      ))}
      <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 4 }}>
        <Mic size={14} c={COLORS.accent}/>
        <Mono size={9} c={COLORS.accent}>TALK BACK</Mono>
      </div>
    </div>
  </div>
);

// ═══════════════════════════════════════════════════════════════
// VARIATION 1 — ORB
// ═══════════════════════════════════════════════════════════════

const V1_Idle = () => (
  <div style={{ padding: '20px 18px', height: '100%', display: 'flex', flexDirection: 'column' }}>
    <Hand size={14} c={COLORS.inkSoft}>good morning</Hand>
    <Hand size={22} style={{ marginTop: 2 }}>what should I do?</Hand>
    <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <Circle size={160} thick={2} c={COLORS.ink}>
        <Circle size={120} thick={1.5} c={COLORS.inkFaint}>
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
            <Mic size={28} />
            <Hand size={14} c={COLORS.inkSoft}>hold to talk</Hand>
          </div>
        </Circle>
      </Circle>
    </div>
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: 8, borderTop: `1px dashed ${COLORS.rule}` }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <Keyb size={16}/><Hand size={14} c={COLORS.inkSoft}>or type</Hand>
      </div>
      <Hand size={14} c={COLORS.inkSoft}>history →</Hand>
    </div>
  </div>
);

const V1_Listening = () => (
  <div style={{ padding: '20px 18px', height: '100%', display: 'flex', flexDirection: 'column', background: COLORS.paperDeep }}>
    <Mono size={10} c={COLORS.accent}>● LISTENING</Mono>
    <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative' }}>
      {[200,170,140].map((s,i)=>(
        <div key={i} style={{
          position: 'absolute', width: s, height: s, borderRadius: '50%',
          border: `1.5px solid ${COLORS.accent}`, opacity: 0.25 + i*0.15,
        }}/>
      ))}
      <Circle size={110} thick={2.5} c={COLORS.accent} fill={COLORS.accentSoft}>
        <Mic size={32} c={COLORS.accent}/>
      </Circle>
    </div>
    <div style={{ marginBottom: 14 }}>
      <Hand size={18}>"book me a flight to</Hand>
      <Hand size={18}> berlin next fri—"</Hand>
      <span style={{ display: 'inline-block', width: 2, height: 16, background: COLORS.accent, marginLeft: 2, verticalAlign: 'middle' }}/>
    </div>
    <Mono size={10} c={COLORS.inkSoft}>release to send · swipe up to cancel</Mono>
  </div>
);

// AMBIENT: the agent is working. We see the live app. Our UI is a small corner chip only.
const V1_Ambient = () => (
  <LiveApp>
    <div style={{ position: 'absolute', top: 10, left: 10 }}>
      <WorkingPip />
    </div>
    <div style={{
      position: 'absolute', bottom: 14, left: '50%', transform: 'translateX(-50%)',
      display: 'flex', gap: 6, padding: '6px 10px',
      background: COLORS.paper, borderRadius: 100,
      border: `1.5px solid ${COLORS.ink}`,
      boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
    }}>
      <Mono size={9} c={COLORS.inkSoft}>HOLD TO INTERRUPT · TAP TO PEEK</Mono>
    </div>
  </LiveApp>
);

// ASSIST: the agent needs input. A floating dialog overlays the live app.
const V1_Assist = () => (
  <LiveApp>
    <div style={{ position: 'absolute', top: 10, left: 10 }}>
      <WorkingPip />
    </div>
    {/* dimmer */}
    <div style={{
      position: 'absolute', inset: 0,
      background: 'rgba(26,22,19,0.25)',
    }}/>
    <AssistCard style={{
      position: 'absolute', left: 16, right: 16, bottom: 20,
    }}/>
  </LiveApp>
);

const V1_Done = () => (
  <div style={{ padding: '20px 18px', height: '100%', display: 'flex', flexDirection: 'column' }}>
    <Mono size={10} c={COLORS.ink}>✓ DONE · 2m 14s</Mono>
    <Hand size={22} style={{ marginTop: 8 }}>flight booked.</Hand>
    <Squiggle w={60} c={COLORS.accent}/>
    <Box radius={12} style={{ marginTop: 16, padding: 14, borderColor: COLORS.accent, borderWidth: 2 }}>
      <Hand size={16}>SFO → BER</Hand>
      <div style={{ fontFamily: FONTS.mono, fontSize: 11, marginTop: 6, color: COLORS.inkSoft, lineHeight: 1.6 }}>
        FRI APR 24 · 09:40<br/>UA 932 · NONSTOP<br/>$842
      </div>
      <div style={{ marginTop: 10, paddingTop: 10, borderTop: `1px dashed ${COLORS.rule}` }}>
        <Hand size={14} c={COLORS.inkSoft}>confirmation in mail</Hand>
      </div>
    </Box>
    <Hand size={14} c={COLORS.inkSoft} style={{ marginTop: 14 }}>anything else?</Hand>
    <div style={{ flex: 1 }}/>
    <Circle size={90} thick={2} c={COLORS.ink} style={{ alignSelf: 'center' }}>
      <Mic size={22}/>
    </Circle>
  </div>
);

// TIMELINE with expandable step log
const V1_Timeline = () => (
  <div style={{ padding: '18px 18px', height: '100%', display: 'flex', flexDirection: 'column', overflow: 'auto' }}>
    <Hand size={22}>timeline</Hand>
    <Mono size={10} c={COLORS.inkSoft}>TODAY · APR 18</Mono>

    {/* expanded item */}
    <Box radius={10} style={{ marginTop: 14, padding: 12, background: COLORS.paperDeep, borderColor: COLORS.ink }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
        <div>
          <Mono size={9} c={COLORS.inkSoft}>9:32</Mono>
          <Hand size={16} style={{ display: 'block' }}>flight to berlin</Hand>
        </div>
        <Chevron dir="up" size={14}/>
      </div>
      <Hand size={13} c={COLORS.accent} italic style={{ display: 'block', marginTop: 2 }}>booked · $842</Hand>
      {/* expanded step log */}
      <div style={{ marginTop: 10, paddingTop: 10, borderTop: `1px dashed ${COLORS.rule}` }}>
        <Mono size={9} c={COLORS.inkSoft}>STEPS (6)</Mono>
        <div style={{ marginTop: 6, fontFamily: FONTS.hand, fontSize: 13, lineHeight: 1.7 }}>
          <div>✓ opened Kayak · 0:08</div>
          <div>✓ searched SFO → BER · 0:14</div>
          <div>✓ filtered nonstop · 0:22</div>
          <div>✓ picked fri apr 24 · 1:05</div>
          <div>✓ seat 1A · 1:42</div>
          <div>✓ paid · 2:14</div>
        </div>
      </div>
    </Box>

    {/* collapsed items */}
    {[['9:18','reschedule haircut','moved to thu'],['8:41','text mom re: sunday','sent']].map(([t, title, sub], i) => (
      <div key={i} style={{
        padding: '12px 2px', borderBottom: `1px dashed ${COLORS.ruleFaint}`,
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      }}>
        <div>
          <Mono size={9} c={COLORS.inkSoft}>{t}</Mono>
          <Hand size={15} style={{ display: 'block' }}>{title}</Hand>
          <Hand size={12} c={COLORS.inkSoft} italic>{sub}</Hand>
        </div>
        <Chevron dir="down" size={14} c={COLORS.inkFaint}/>
      </div>
    ))}
    <Mono size={10} c={COLORS.inkFaint} style={{ marginTop: 12 }}>YESTERDAY ›</Mono>
  </div>
);

const V1_Settings = () => (
  <div style={{ padding: '20px 18px', height: '100%' }}>
    <Hand size={22}>settings</Hand>
    <Squiggle w={40} c={COLORS.accent}/>
    <div style={{ marginTop: 14 }}>
      {[
        ['voice', 'push-to-talk', true],
        ['wake word', 'off', false],
        ['trust mode', 'on — no confirms', true],
        ['assist style', 'overlay · voice', true],
        ['haptics', 'on', true],
        ['accent', '■ burnt orange', true],
        ['dark mode', 'auto', false],
      ].map(([k,v,on],i)=>(
        <div key={i} style={{
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
          padding: '12px 0', borderBottom: `1px dashed ${COLORS.ruleFaint}`,
        }}>
          <Hand size={15}>{k}</Hand>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Hand size={13} c={COLORS.inkSoft} italic>{v}</Hand>
            <div style={{
              width: 28, height: 16, borderRadius: 10,
              border: `1.5px solid ${COLORS.ink}`,
              background: on ? COLORS.accent : 'transparent', position: 'relative',
            }}>
              <div style={{
                position: 'absolute', top: 1, [on ? 'right' : 'left']: 1,
                width: 12, height: 12, borderRadius: '50%',
                background: on ? COLORS.paper : COLORS.ink,
              }}/>
            </div>
          </div>
        </div>
      ))}
    </div>
  </div>
);

// ═══════════════════════════════════════════════════════════════
// VARIATION 2 — BOTTOM CAPSULE
// ═══════════════════════════════════════════════════════════════

const V2_PTT = ({ active, label='hold to talk' }) => (
  <div style={{
    position: 'absolute', bottom: 12, left: 16, right: 16,
    display: 'flex', gap: 8, alignItems: 'center', zIndex: 5,
  }}>
    <div style={{
      flex: 1, height: 48, borderRadius: 24,
      border: `2px solid ${active ? COLORS.accent : COLORS.ink}`,
      background: active ? COLORS.accentSoft : COLORS.paper,
      display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
    }}>
      <Mic size={18} c={active ? COLORS.accent : COLORS.ink}/>
      <Hand size={15} c={active ? COLORS.accent : COLORS.ink}>{label}</Hand>
    </div>
    <Circle size={48} thick={2} c={COLORS.ink} fill={COLORS.paper}>
      <Keyb size={20}/>
    </Circle>
  </div>
);

const V2_Idle = () => (
  <div style={{ padding: '20px 18px 80px', height: '100%' }}>
    <Hand size={24}>hello,</Hand>
    <Hand size={24} c={COLORS.accent}>alex</Hand>
    <Mono size={10} c={COLORS.inkSoft} style={{ marginTop: 12, display: 'block' }}>RECENT</Mono>
    {['flights to berlin', 'text mom', 'reschedule haircut'].map((t,i)=>(
      <div key={i} style={{
        padding: '10px 12px', borderRadius: 10,
        border: `1.5px dashed ${COLORS.rule}`, marginTop: 8,
        display: 'flex', justifyContent: 'space-between',
      }}>
        <Hand size={15}>{t}</Hand>
        <ArrowR size={14} c={COLORS.inkSoft}/>
      </div>
    ))}
    <Mono size={10} c={COLORS.inkSoft} style={{ marginTop: 18, display: 'block' }}>TRY</Mono>
    <Hand size={15} c={COLORS.inkSoft} style={{ marginTop: 4 }}>"pay my phone bill"</Hand>
    <Hand size={15} c={COLORS.inkSoft}>"find sushi nearby, book 7pm"</Hand>
    <V2_PTT/>
  </div>
);

const V2_Listening = () => (
  <div style={{ padding: '20px 18px 80px', height: '100%', background: COLORS.paperDeep }}>
    <Mono size={10} c={COLORS.accent}>● LISTENING</Mono>
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 3, height: 120, marginTop: 20 }}>
      {[12,28,44,20,56,72,40,80,52,36,60,28,44,20,12].map((h,i)=>(
        <div key={i} style={{ width: 4, height: h, borderRadius: 2, background: COLORS.accent, opacity: 0.4 + (h/80)*0.6 }}/>
      ))}
    </div>
    <div style={{ marginTop: 20 }}>
      <Hand size={20}>"book me a flight to berlin</Hand>
      <Hand size={20}> next friday, window seat"</Hand>
    </div>
    <V2_PTT active label="release to send"/>
  </div>
);

// Ambient: capsule shrinks to a small corner bubble; live app is visible
const V2_Ambient = () => (
  <LiveApp>
    <div style={{
      position: 'absolute', bottom: 14, left: 16, right: 16,
      padding: '8px 12px', borderRadius: 24,
      background: COLORS.ink, color: COLORS.paper,
      display: 'flex', alignItems: 'center', gap: 10,
      boxShadow: '0 6px 18px rgba(0,0,0,0.25)',
    }}>
      <Dot size={8} c={COLORS.accent}/>
      <div style={{ flex: 1 }}>
        <Mono size={9} c={COLORS.accentSoft}>BOOKING FLIGHT · 0:47</Mono>
        <Hand size={14} c={COLORS.paper} style={{ display: 'block' }}>picking fri apr 24</Hand>
      </div>
      <Chevron dir="up" size={14} c={COLORS.paper}/>
    </div>
  </LiveApp>
);

// Assist: capsule grows into full-width card with buttons
const V2_Assist = () => (
  <LiveApp>
    <div style={{ position: 'absolute', inset: 0, background: 'rgba(26,22,19,0.2)' }}/>
    <div style={{
      position: 'absolute', bottom: 14, left: 16, right: 16,
      padding: 16, borderRadius: 20,
      background: COLORS.ink, color: COLORS.paper,
      boxShadow: '0 8px 24px rgba(0,0,0,0.25)',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <Dot size={8} c={COLORS.accent}/>
        <Mono size={9} c={COLORS.accentSoft}>ASSIST · 0:47</Mono>
      </div>
      <Hand size={17} c={COLORS.paper} style={{ display: 'block', marginTop: 6 }}>
        nonstops are +$180.
      </Hand>
      <Hand size={15} c={COLORS.paper} italic style={{ opacity: 0.7, display: 'block' }}>
        keep nonstop, or allow stops?
      </Hand>
      <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
        {['keep','stops ok','stop'].map(t=>(
          <div key={t} style={{
            flex: 1, textAlign: 'center', padding: '8px 6px',
            borderRadius: 12, border: `1.5px solid ${COLORS.paper}`,
          }}>
            <Hand size={13} c={COLORS.paper}>{t}</Hand>
          </div>
        ))}
      </div>
      <div style={{ display: 'flex', justifyContent: 'center', marginTop: 10, gap: 6, alignItems: 'center' }}>
        <Mic size={14} c={COLORS.accentSoft}/>
        <Mono size={9} c={COLORS.accentSoft}>OR JUST SPEAK</Mono>
      </div>
    </div>
  </LiveApp>
);

const V2_Done = () => (
  <div style={{ padding: '20px 18px 80px', height: '100%' }}>
    <Mono size={10} c={COLORS.ink}>✓ DONE · 2m 14s</Mono>
    <Hand size={22} style={{ marginTop: 6 }}>all set.</Hand>
    <Box radius={14} style={{ marginTop: 14, padding: 14, background: COLORS.paperDeep }}>
      <Mono size={9} c={COLORS.accent}>FLIGHT · BOOKED</Mono>
      <Hand size={18} style={{ marginTop: 4 }}>SFO → BER</Hand>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 8 }}>
        <div><Mono size={9} c={COLORS.inkSoft}>DEPART</Mono><Hand size={14} style={{ display:'block'}}>Fri Apr 24</Hand></div>
        <div><Mono size={9} c={COLORS.inkSoft}>TOTAL</Mono><Hand size={14} style={{ display:'block'}}>$842</Hand></div>
      </div>
      <Line style={{ margin: '10px 0' }} c={COLORS.rule}/>
      <Hand size={13} c={COLORS.inkSoft} italic>UA 932 · nonstop · 1A</Hand>
    </Box>
    <div style={{ display: 'flex', gap: 8, marginTop: 12, flexWrap: 'wrap' }}>
      {['share','add to cal','undo'].map(t=>(
        <div key={t} style={{ padding: '6px 12px', borderRadius: 16, border: `1.5px solid ${COLORS.ink}` }}>
          <Hand size={13}>{t}</Hand>
        </div>
      ))}
    </div>
    <V2_PTT label="what's next?"/>
  </div>
);

const V2_Timeline = () => (
  <div style={{ padding: '20px 18px 80px', height: '100%', overflow: 'auto' }}>
    <Hand size={22}>timeline</Hand>
    <div style={{ display: 'flex', gap: 6, marginTop: 8, flexWrap: 'wrap' }}>
      {['today','week','month'].map((t,i)=>(
        <div key={t} style={{
          padding: '4px 10px', borderRadius: 12,
          border: `1.5px solid ${COLORS.ink}`,
          background: i === 0 ? COLORS.ink : 'transparent',
        }}>
          <Hand size={12} c={i === 0 ? COLORS.paper : COLORS.ink}>{t}</Hand>
        </div>
      ))}
    </div>
    <div style={{ marginTop: 14 }}>
      {[
        ['9:32','flight → berlin','$842 · 2m · 6 steps',true],
        ['9:18','reschedule haircut','thu 4pm',false],
        ['8:41','text mom','sent',false],
      ].map(([t,title,sub,hero],i)=>(
        <div key={i} style={{
          padding: '10px 0', borderBottom: `1px dashed ${COLORS.ruleFaint}`,
          display: 'flex', gap: 10,
        }}>
          <Mono size={9} c={COLORS.inkSoft} style={{ width: 40, paddingTop: 4 }}>{t}</Mono>
          <div style={{ flex: 1 }}>
            <Hand size={15} c={hero ? COLORS.accent : COLORS.ink}>{title}</Hand>
            <Hand size={12} c={COLORS.inkSoft} italic style={{ display: 'block' }}>{sub}</Hand>
          </div>
          <Chevron size={12} c={COLORS.inkFaint} dir={hero ? 'up' : 'down'}/>
        </div>
      ))}
    </div>
    <V2_PTT/>
  </div>
);

const V2_Settings = () => (
  <div style={{ padding: '20px 18px 80px', height: '100%' }}>
    <Hand size={22}>settings</Hand>
    {[
      ['VOICE',[['input','push-to-talk'],['voice','warm'],['speed','1.0×']]],
      ['BEHAVIOR',[['trust mode','on'],['assist','overlay+voice'],['accent','■']]],
    ].map(([h,rows],i)=>(
      <div key={i}>
        <Mono size={10} c={COLORS.inkSoft} style={{ display: 'block', marginTop: 16 }}>{h}</Mono>
        {rows.map(([k,v])=>(
          <div key={k} style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 0', borderBottom: `1px dashed ${COLORS.ruleFaint}` }}>
            <Hand size={15}>{k}</Hand>
            <Hand size={14} c={COLORS.inkSoft} italic>{v} →</Hand>
          </div>
        ))}
      </div>
    ))}
    <V2_PTT/>
  </div>
);

// ═══════════════════════════════════════════════════════════════
// VARIATION 3 — FULL-BLEED TIMELINE + SIDE HANDLE
// ═══════════════════════════════════════════════════════════════

const V3_Handle = ({ active }) => (
  <div style={{
    position: 'absolute', right: -2, top: '50%', transform: 'translateY(-50%)',
    width: 38, height: 110, borderRadius: '18px 0 0 18px',
    border: `2px solid ${active ? COLORS.accent : COLORS.ink}`, borderRight: 'none',
    background: active ? COLORS.accentSoft : COLORS.paper,
    display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
    gap: 6, zIndex: 10,
  }}>
    <Mic size={20} c={active ? COLORS.accent : COLORS.ink}/>
    <div style={{ fontFamily: FONTS.hand, fontSize: 11, writingMode: 'vertical-rl', transform: 'rotate(180deg)', color: active ? COLORS.accent : COLORS.inkSoft }}>
      hold
    </div>
  </div>
);

const V3_Idle = () => (
  <div style={{ height: '100%', padding: '18px 44px 18px 18px', position: 'relative' }}>
    <Hand size={22}>today</Hand>
    <Mono size={9} c={COLORS.inkSoft}>APR 18 · FRI</Mono>
    <div style={{ marginTop: 14 }}>
      <div style={{ padding: '14px 0', borderTop: `1.5px solid ${COLORS.ink}`, borderBottom: `1px dashed ${COLORS.ruleFaint}` }}>
        <Hand size={16} c={COLORS.inkSoft} italic>no tasks yet today —</Hand>
        <Hand size={16} c={COLORS.inkSoft} italic>hold the side to start</Hand>
      </div>
      <Mono size={9} c={COLORS.inkFaint} style={{ display: 'block', marginTop: 18 }}>YESTERDAY</Mono>
      {[['17:02','grocery order · $64'],['14:20','paid electric · $94'],['11:55','rescheduled standup'],['09:04','inbox summary · 12 mails']].map(([t,title],i)=>(
        <div key={i} style={{ padding: '8px 0', display: 'flex', gap: 10, borderBottom: `1px dashed ${COLORS.ruleFaint}` }}>
          <Mono size={9} c={COLORS.inkSoft} style={{ width: 38, paddingTop: 3 }}>{t}</Mono>
          <Hand size={14}>{title}</Hand>
        </div>
      ))}
    </div>
    <V3_Handle/>
  </div>
);

const V3_Listening = () => (
  <div style={{ height: '100%', padding: '18px 44px 18px 18px', position: 'relative', background: COLORS.paperDeep }}>
    <Mono size={10} c={COLORS.accent}>● 00:04</Mono>
    <div style={{ marginTop: 20, borderLeft: `3px solid ${COLORS.accent}`, paddingLeft: 12 }}>
      <Hand size={20}>"book a flight</Hand>
      <Hand size={20}> to berlin next fri,</Hand>
      <Hand size={20}> window please</Hand>
      <span style={{ display:'inline-block', width:2, height:18, background: COLORS.accent, marginLeft: 2, verticalAlign:'middle' }}/>
    </div>
    <div style={{ display: 'flex', alignItems: 'center', gap: 2, marginTop: 24, height: 40 }}>
      {Array.from({length:40}).map((_,i)=>{
        const h = 8 + Math.abs(Math.sin(i*0.5))*28;
        return <div key={i} style={{ flex: 1, height: h, background: COLORS.accent, opacity: 0.4 + (h/36)*0.6, borderRadius: 1 }}/>
      })}
    </div>
    <Mono size={9} c={COLORS.inkSoft} style={{ marginTop: 14, display:'block' }}>RELEASE → SEND</Mono>
    <V3_Handle active/>
  </div>
);

// Ambient: live app, side handle shows agent is working via dot indicator
const V3_Ambient = () => (
  <LiveApp>
    {/* thin status strip at top */}
    <div style={{
      position: 'absolute', top: 0, left: 0, right: 0,
      height: 3, background: COLORS.accent,
    }}/>
    <div style={{
      position: 'absolute', top: 8, right: 60,
      padding: '2px 8px', borderRadius: 100,
      background: COLORS.paper, border: `1px solid ${COLORS.accent}`,
    }}>
      <Mono size={9} c={COLORS.accent}>● 0:47 · TAP ↗</Mono>
    </div>
    <V3_Handle/>
    {/* the handle gets a dot to show work is happening */}
    <div style={{
      position: 'absolute', right: 14, top: 'calc(50% - 70px)',
      width: 10, height: 10, borderRadius: 10, background: COLORS.accent,
      border: `2px solid ${COLORS.paper}`, zIndex: 11,
    }}/>
  </LiveApp>
);

// Assist: live app, overlay card slides from the handle side
const V3_Assist = () => (
  <LiveApp>
    <div style={{ position: 'absolute', inset: 0, background: 'rgba(26,22,19,0.2)' }}/>
    <AssistCard style={{
      position: 'absolute', left: 14, right: 44, top: 120,
    }}/>
    <V3_Handle active/>
  </LiveApp>
);

const V3_Done = () => (
  <div style={{ height: '100%', padding: '18px 44px 18px 18px', position: 'relative' }}>
    <Hand size={22}>today</Hand>
    <Mono size={9} c={COLORS.inkSoft}>APR 18 · FRI</Mono>
    <div style={{ marginTop: 14 }}>
      <div style={{
        padding: 12, borderRadius: 10,
        border: `2px solid ${COLORS.ink}`, position: 'relative',
      }}>
        <div style={{
          position: 'absolute', top: -8, right: 10, background: COLORS.paper, padding: '0 6px',
        }}>
          <Mono size={9} c={COLORS.accent}>✓ JUST NOW</Mono>
        </div>
        <Hand size={18}>flight · berlin</Hand>
        <Squiggle w={50} c={COLORS.accent}/>
        <div style={{ fontFamily: FONTS.mono, fontSize: 10, color: COLORS.inkSoft, marginTop: 8, lineHeight: 1.6 }}>
          FRI APR 24 · 09:40<br/>UA 932 · SFO → BER<br/>1A · $842
        </div>
        <div style={{ display: 'flex', gap: 6, marginTop: 10 }}>
          {['undo','share'].map(t=>(
            <div key={t} style={{ padding: '3px 10px', border: `1.5px solid ${COLORS.ink}`, borderRadius: 12 }}>
              <Hand size={12}>{t}</Hand>
            </div>
          ))}
        </div>
      </div>
    </div>
    <div style={{ padding: '10px 0', marginTop: 12, display: 'flex', gap: 10, borderBottom: `1px dashed ${COLORS.ruleFaint}` }}>
      <Mono size={9} c={COLORS.inkSoft} style={{ width: 38, paddingTop: 3 }}>9:18</Mono>
      <Hand size={14} c={COLORS.inkSoft}>reschedule haircut</Hand>
    </div>
    <V3_Handle/>
  </div>
);

const V3_Timeline = () => (
  <div style={{ height: '100%', padding: '18px 44px 18px 18px', position: 'relative', overflow: 'auto' }}>
    <Hand size={22}>all tasks</Hand>
    <Mono size={9} c={COLORS.inkSoft}>THIS WEEK · 23</Mono>
    <div style={{ display: 'flex', gap: 4, marginTop: 12, alignItems: 'flex-end', height: 50 }}>
      {[3,7,2,5,8,1,0].map((n,i)=>(
        <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
          <div style={{ height: n*5 + 4, width: '100%', background: i === 4 ? COLORS.accent : COLORS.ink, borderRadius: 2 }}/>
          <Mono size={8} c={COLORS.inkSoft}>{'mtwtfss'[i].toUpperCase()}</Mono>
        </div>
      ))}
    </div>
    <Mono size={9} c={COLORS.inkSoft} style={{ display: 'block', marginTop: 14 }}>FRI · TODAY</Mono>
    {/* expanded flight entry with steps */}
    <div style={{ padding: '10px 0', borderBottom: `1px dashed ${COLORS.ruleFaint}` }}>
      <div style={{ display: 'flex', gap: 10 }}>
        <Mono size={9} c={COLORS.inkSoft} style={{ width: 38, paddingTop: 3 }}>9:32</Mono>
        <Hand size={14} c={COLORS.accent} style={{ flex: 1 }}>flight → berlin</Hand>
        <Chevron dir="up" size={12}/>
      </div>
      <div style={{ marginLeft: 48, marginTop: 4, fontFamily: FONTS.hand, fontSize: 12, color: COLORS.inkSoft, lineHeight: 1.6 }}>
        ✓ kayak → search → filter<br/>
        ✓ fri apr 24 · 1A · $842
      </div>
    </div>
    {[['9:18','reschedule haircut'],['8:41','text mom']].map(([t,title],i)=>(
      <div key={i} style={{ padding: '8px 0', display: 'flex', gap: 10, borderBottom: `1px dashed ${COLORS.ruleFaint}` }}>
        <Mono size={9} c={COLORS.inkSoft} style={{ width: 38, paddingTop: 3 }}>{t}</Mono>
        <Hand size={14}>{title}</Hand>
      </div>
    ))}
    <V3_Handle/>
  </div>
);

const V3_Settings = () => (
  <div style={{ height: '100%', padding: '18px 44px 18px 18px', position: 'relative' }}>
    <Hand size={22}>settings</Hand>
    {['voice','behavior','assist overlay','apps','privacy','about'].map((s,i)=>(
      <div key={i} style={{
        padding: '14px 0', borderBottom: `1px dashed ${COLORS.ruleFaint}`,
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      }}>
        <Hand size={16}>{s}</Hand>
        <ArrowR size={14} c={COLORS.inkSoft}/>
      </div>
    ))}
    <Box radius={10} style={{ marginTop: 18, padding: 12, background: COLORS.paperDeep, borderStyle: 'dashed' }}>
      <Mono size={9} c={COLORS.accent}>TRUST MODE · ON</Mono>
      <Hand size={13} c={COLORS.inkSoft} style={{ display: 'block', marginTop: 4 }}>
        acts without asking. undo via timeline.
      </Hand>
    </Box>
    <V3_Handle/>
  </div>
);

// ═══════════════════════════════════════════════════════════════
// VARIATION 4 — PAPER
// ═══════════════════════════════════════════════════════════════

const V4_Bg = ({ children, ruled }) => (
  <div style={{
    height: '100%',
    background: ruled
      ? `repeating-linear-gradient(${COLORS.paper}, ${COLORS.paper} 28px, ${COLORS.ruleFaint} 28px, ${COLORS.ruleFaint} 29px)`
      : COLORS.paper,
    position: 'relative', overflow: 'hidden',
  }}>{children}</div>
);

const V4_Idle = () => (
  <V4_Bg ruled>
    <div style={{ padding: '24px 22px', height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Mono size={9} c={COLORS.inkSoft}>FRI · APR 18</Mono>
      <Hand size={28} style={{ marginTop: 2, lineHeight: 1 }}>dear assistant,</Hand>
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ textAlign: 'center' }}>
          <Circle size={96} thick={2} c={COLORS.accent}>
            <Mic size={30} c={COLORS.accent}/>
          </Circle>
          <Hand size={16} c={COLORS.inkSoft} italic style={{ display:'block', marginTop: 12 }}>hold to speak</Hand>
          <Hand size={13} c={COLORS.inkFaint} italic>or tap to write</Hand>
        </div>
      </div>
      <div style={{ textAlign: 'right' }}>
        <Hand size={15} c={COLORS.inkSoft} italic>~ yours, A.</Hand>
      </div>
    </div>
  </V4_Bg>
);

const V4_Listening = () => (
  <V4_Bg ruled>
    <div style={{ padding: '24px 22px', height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Mono size={9} c={COLORS.accent}>● LISTENING</Mono>
      <div style={{ marginTop: 12, fontFamily: FONTS.hand, fontSize: 22, lineHeight: '29px' }}>
        book me a flight to<br/>
        berlin next friday,<br/>
        window seat
        <span style={{ display:'inline-block', marginLeft:4, width:10, height:2, background: COLORS.accent }}/>
      </div>
      <div style={{ flex: 1 }}/>
      <div style={{
        display: 'flex', alignItems: 'center', gap: 10, padding: '12px 14px',
        background: COLORS.accentSoft, borderRadius: 100, alignSelf: 'center',
      }}>
        <Circle size={10} fill={COLORS.accent} thick={0}/>
        <Hand size={14} c={COLORS.accent}>release to send</Hand>
      </div>
    </div>
  </V4_Bg>
);

// Ambient: paper-style sticky note in corner over the live app
const V4_Ambient = () => (
  <LiveApp>
    <div style={{
      position: 'absolute', top: 64, right: 14,
      padding: 10, width: 120,
      background: '#fffcf0', transform: 'rotate(3deg)',
      boxShadow: '0 4px 10px rgba(0,0,0,0.15)',
      borderBottom: `2px solid ${COLORS.accent}`,
    }}>
      <Mono size={8} c={COLORS.accent}>WORKING · 0:47</Mono>
      <div style={{ fontFamily: FONTS.hand, fontSize: 13, marginTop: 2, lineHeight: 1.3 }}>
        picking fri apr 24
      </div>
      <Hand size={11} c={COLORS.inkSoft} italic style={{ display:'block', marginTop: 4 }}>
        tap to peek
      </Hand>
    </div>
  </LiveApp>
);

// Assist: handwritten margin note pinned to the side
const V4_Assist = () => (
  <LiveApp>
    <div style={{ position: 'absolute', inset: 0, background: 'rgba(26,22,19,0.15)' }}/>
    <div style={{
      position: 'absolute', left: 14, right: 14, bottom: 20,
      padding: 14,
      background: COLORS.paper,
      borderLeft: `4px solid ${COLORS.accent}`,
      boxShadow: '0 8px 20px rgba(0,0,0,0.2)',
      backgroundImage: `repeating-linear-gradient(${COLORS.paper}, ${COLORS.paper} 24px, ${COLORS.ruleFaint} 24px, ${COLORS.ruleFaint} 25px)`,
    }}>
      <Mono size={9} c={COLORS.accent}>— MARGIN NOTE —</Mono>
      <div style={{ fontFamily: FONTS.hand, fontSize: 16, lineHeight: '24px', marginTop: 2 }}>
        "nonstops are +$180.
      </div>
      <div style={{ fontFamily: FONTS.hand, fontSize: 16, lineHeight: '24px' }}>
        keep, or allow stops?"
      </div>
      <div style={{ display: 'flex', gap: 12, marginTop: 8 }}>
        <Hand size={14} c={COLORS.accent}>□ keep</Hand>
        <Hand size={14} c={COLORS.accent}>□ stops ok</Hand>
        <Hand size={14} c={COLORS.accent}>□ stop</Hand>
      </div>
    </div>
  </LiveApp>
);

const V4_Done = () => (
  <V4_Bg ruled>
    <div style={{ padding: '22px 22px', height: '100%' }}>
      <Mono size={9} c={COLORS.inkSoft}>FRI · APR 18 · 9:34</Mono>
      <div style={{
        display: 'inline-block', padding: '2px 10px', background: COLORS.accent,
        color: COLORS.paper, transform: 'rotate(-2deg)', marginTop: 6,
      }}>
        <Hand size={16} c={COLORS.paper}>DONE.</Hand>
      </div>
      <div style={{ marginTop: 18, fontFamily: FONTS.hand, fontSize: 20, lineHeight: '29px' }}>
        booked: <u>SFO → BER</u>
      </div>
      <div style={{ fontFamily: FONTS.hand, fontSize: 18, lineHeight: '29px', color: COLORS.inkSoft }}>
        fri apr 24, 9:40am
      </div>
      <div style={{ fontFamily: FONTS.hand, fontSize: 18, lineHeight: '29px', color: COLORS.inkSoft }}>
        UA 932, seat 1A
      </div>
      <div style={{ fontFamily: FONTS.hand, fontSize: 18, lineHeight: '29px' }}>
        <span style={{ color: COLORS.accent }}>$842</span> <span style={{ color: COLORS.inkFaint }}>— paid</span>
      </div>
      <div style={{ marginTop: 24 }}>
        <Hand size={14} c={COLORS.inkSoft} italic>confirmation → mail</Hand>
      </div>
      <div style={{ position: 'absolute', bottom: 20, right: 22 }}>
        <Circle size={56} thick={1.5} c={COLORS.ink}><Mic size={20}/></Circle>
      </div>
    </div>
  </V4_Bg>
);

const V4_Timeline = () => (
  <V4_Bg ruled>
    <div style={{ padding: '22px 22px', height: '100%', overflow: 'auto' }}>
      <Hand size={26}>journal</Hand>
      <Squiggle w={50} c={COLORS.accent}/>
      <Mono size={9} c={COLORS.inkSoft} style={{ display: 'block', marginTop: 14 }}>FRI · TODAY</Mono>
      <div style={{ fontFamily: FONTS.hand, fontSize: 17, lineHeight: '29px', marginTop: 4 }}>
        · 9:32 — <span style={{ color: COLORS.accent }}>booked berlin flight</span>
      </div>
      <div style={{ fontFamily: FONTS.hand, fontSize: 13, lineHeight: '22px', color: COLORS.inkSoft, marginLeft: 14 }}>
        (6 steps · kayak · nonstop · 1A · $842)
      </div>
      <div style={{ fontFamily: FONTS.hand, fontSize: 17, lineHeight: '29px' }}>
        · 9:18 — rescheduled haircut
      </div>
      <div style={{ fontFamily: FONTS.hand, fontSize: 17, lineHeight: '29px' }}>
        · 8:41 — texted mom
      </div>
      <Mono size={9} c={COLORS.inkSoft} style={{ display: 'block', marginTop: 14 }}>THU</Mono>
      <div style={{ fontFamily: FONTS.hand, fontSize: 16, lineHeight: '29px', color: COLORS.inkSoft }}>
        · 17:02 — grocery order<br/>
        · 14:20 — paid electric<br/>
        · 11:55 — rescheduled standup
      </div>
    </div>
  </V4_Bg>
);

const V4_Settings = () => (
  <V4_Bg ruled>
    <div style={{ padding: '22px 22px', height: '100%' }}>
      <Hand size={26}>preferences</Hand>
      <Squiggle w={70} c={COLORS.accent}/>
      <div style={{ marginTop: 16, fontFamily: FONTS.hand, fontSize: 17, lineHeight: '33px' }}>
        <div>input — <u>push to talk</u></div>
        <div>voice — <u>warm</u></div>
        <div>trust — <span style={{ color: COLORS.accent }}><u>on, no confirm</u></span></div>
        <div>assist — <u>margin notes</u></div>
        <div>wake word — <span style={{ color: COLORS.inkFaint }}>off</span></div>
        <div>accent — <span style={{ color: COLORS.accent }}>■ burnt</span></div>
      </div>
      <div style={{ position: 'absolute', bottom: 30, right: 22 }}>
        <Hand size={13} c={COLORS.inkSoft} italic>— end —</Hand>
      </div>
    </div>
  </V4_Bg>
);

// ═══════════════════════════════════════════════════════════════
// VARIATION 5 — RADIAL DIAL
// ═══════════════════════════════════════════════════════════════

const Dial = ({ size = 160, angle = 0, active, label }) => (
  <div style={{
    width: size, height: size, borderRadius: '50%',
    border: `2px solid ${active ? COLORS.accent : COLORS.ink}`,
    position: 'relative', flexShrink: 0,
    background: active ? COLORS.accentSoft : 'transparent',
  }}>
    {Array.from({length:24}).map((_,i)=>{
      const a = (i/24)*Math.PI*2;
      const inner = size/2 - 10, outer = size/2 - 2;
      const x1 = size/2 + Math.cos(a)*inner, y1 = size/2 + Math.sin(a)*inner;
      const x2 = size/2 + Math.cos(a)*outer, y2 = size/2 + Math.sin(a)*outer;
      return (
        <svg key={i} style={{ position: 'absolute', inset: 0 }} width={size} height={size}>
          <line x1={x1} y1={y1} x2={x2} y2={y2} stroke={active ? COLORS.accent : COLORS.ink} strokeWidth={i%6===0?2:1} opacity={i%6===0?1:0.4}/>
        </svg>
      );
    })}
    <div style={{
      position: 'absolute', top: 8, left: '50%',
      transform: `translateX(-50%) rotate(${angle}deg)`,
      transformOrigin: `50% ${size/2 - 8}px`,
      width: 3, height: 24, background: active ? COLORS.accent : COLORS.ink, borderRadius: 2,
    }}/>
    <div style={{
      position: 'absolute', inset: 30, borderRadius: '50%',
      background: COLORS.paper, border: `1.5px solid ${active ? COLORS.accent : COLORS.ink}`,
      display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
    }}>
      <Mic size={26} c={active ? COLORS.accent : COLORS.ink}/>
      {label && <Hand size={12} c={active ? COLORS.accent : COLORS.inkSoft} style={{ marginTop: 4 }}>{label}</Hand>}
    </div>
  </div>
);

const V5_Idle = () => (
  <div style={{ padding: '20px 18px', height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
    <div style={{ alignSelf: 'flex-start' }}>
      <Mono size={9} c={COLORS.inkSoft}>ATTEND</Mono>
      <Hand size={22} style={{ display: 'block' }}>what needs doing?</Hand>
    </div>
    <div style={{ flex: 1, display: 'flex', alignItems: 'center' }}>
      <Dial size={180} angle={0} label="hold"/>
    </div>
    <div style={{ display: 'flex', gap: 16, marginBottom: 4 }}>
      {[['◯','talk',true],['▢','type',false],['◇','pick',false]].map(([g,t,a],i)=>(
        <div key={i} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
          <Hand size={20} c={a ? COLORS.accent : COLORS.inkFaint}>{g}</Hand>
          <Mono size={9} c={a ? COLORS.accent : COLORS.inkSoft}>{t}</Mono>
        </div>
      ))}
    </div>
  </div>
);

const V5_Listening = () => (
  <div style={{ padding: '20px 18px', height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', background: COLORS.paperDeep }}>
    <div style={{ alignSelf: 'flex-start', width: '100%' }}>
      <Mono size={10} c={COLORS.accent}>● CAPTURING 00:04</Mono>
      <Hand size={16} style={{ display:'block', marginTop: 4 }}>"flight to berlin fri,</Hand>
      <Hand size={16}>window, under $900"</Hand>
    </div>
    <div style={{ flex: 1, display: 'flex', alignItems: 'center', position: 'relative' }}>
      {[220,200,180].map((s,i)=>(
        <div key={i} style={{
          position: 'absolute', width: s, height: s, borderRadius: '50%',
          border: `1.5px solid ${COLORS.accent}`, opacity: 0.2 + i*0.15,
          left: '50%', top: '50%', transform: 'translate(-50%, -50%)',
        }}/>
      ))}
      <Dial size={180} angle={90} active label="release"/>
    </div>
    <Mono size={9} c={COLORS.inkSoft}>SLIDE DOWN → CANCEL</Mono>
  </div>
);

// Ambient: a floating ring in the corner, filling as work progresses
const V5_Ambient = () => (
  <LiveApp>
    <div style={{
      position: 'absolute', top: 64, right: 14,
    }}>
      <div style={{ position: 'relative', width: 64, height: 64 }}>
        <svg width="64" height="64" viewBox="0 0 64 64">
          <circle cx="32" cy="32" r="28" fill={COLORS.paper} stroke={COLORS.ruleFaint} strokeWidth="3"/>
          <circle cx="32" cy="32" r="28" fill="none" stroke={COLORS.accent} strokeWidth="3"
            strokeDasharray={`${0.6 * 2 * Math.PI * 28} ${2 * Math.PI * 28}`}
            transform="rotate(-90 32 32)" strokeLinecap="round"/>
        </svg>
        <div style={{
          position: 'absolute', inset: 0,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          flexDirection: 'column',
        }}>
          <Mono size={11} c={COLORS.accent}>4/6</Mono>
          <Mono size={7} c={COLORS.inkSoft}>STEP</Mono>
        </div>
      </div>
    </div>
    <div style={{
      position: 'absolute', top: 84, right: 84,
      padding: '2px 6px', background: COLORS.ink, color: COLORS.paper, borderRadius: 4,
    }}>
      <Mono size={8} c={COLORS.paper}>HOLD RING → TALK</Mono>
    </div>
  </LiveApp>
);

// Assist: the ring expands into a dial the user can answer via twist or tap
const V5_Assist = () => (
  <LiveApp>
    <div style={{ position: 'absolute', inset: 0, background: 'rgba(26,22,19,0.25)' }}/>
    <div style={{
      position: 'absolute', left: '50%', top: '50%', transform: 'translate(-50%, -50%)',
      display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 10,
    }}>
      <Mono size={9} c={COLORS.accentSoft}>ASSIST · TWIST TO ANSWER</Mono>
      <div style={{ position: 'relative', width: 200, height: 200 }}>
        <Dial size={200} angle={-35} active/>
        {/* three labels around the ring */}
        <div style={{ position: 'absolute', top: -8, left: '50%', transform: 'translateX(-50%)', padding: '2px 8px', background: COLORS.paper, border: `1px solid ${COLORS.ink}`, borderRadius: 4 }}>
          <Hand size={12}>keep</Hand>
        </div>
        <div style={{ position: 'absolute', left: -30, top: '50%', transform: 'translateY(-50%)', padding: '2px 8px', background: COLORS.paper, border: `1px solid ${COLORS.ink}`, borderRadius: 4 }}>
          <Hand size={12}>stops ok</Hand>
        </div>
        <div style={{ position: 'absolute', right: -16, top: '50%', transform: 'translateY(-50%)', padding: '2px 8px', background: COLORS.paper, border: `1px solid ${COLORS.ink}`, borderRadius: 4 }}>
          <Hand size={12}>stop</Hand>
        </div>
      </div>
      <div style={{
        padding: '6px 12px', borderRadius: 14,
        background: COLORS.paper, border: `1.5px solid ${COLORS.ink}`,
      }}>
        <Hand size={13}>"nonstops +$180, keep?"</Hand>
      </div>
    </div>
  </LiveApp>
);

const V5_Done = () => (
  <div style={{ padding: '20px 18px', height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
    <Mono size={10} c={COLORS.ink} style={{ alignSelf: 'flex-start' }}>COMPLETE · 2m 14s</Mono>
    <div style={{ margin: '24px 0 16px' }}>
      <Circle size={140} thick={2} c={COLORS.accent} fill={COLORS.accentSoft}>
        <Check size={56} c={COLORS.accent}/>
      </Circle>
    </div>
    <Hand size={22}>berlin booked</Hand>
    <Box radius={12} style={{ marginTop: 14, padding: 12, width: '100%', borderStyle: 'dashed' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
        <Mono size={10} c={COLORS.inkSoft}>APR 24 · 09:40</Mono>
        <Mono size={10} c={COLORS.accent}>$842</Mono>
      </div>
      <Hand size={16} style={{ marginTop: 4 }}>UA 932 · SFO → BER · 1A</Hand>
    </Box>
    <div style={{ display: 'flex', gap: 10, marginTop: 12 }}>
      {['undo','share','next'].map(t=>(
        <div key={t} style={{ padding: '6px 14px', border: `1.5px solid ${COLORS.ink}`, borderRadius: 100 }}>
          <Hand size={13}>{t}</Hand>
        </div>
      ))}
    </div>
  </div>
);

const V5_Timeline = () => (
  <div style={{ padding: '20px 18px', height: '100%' }}>
    <Hand size={22}>spiral</Hand>
    <Mono size={9} c={COLORS.inkSoft}>TIME, FROM INSIDE OUT</Mono>
    <div style={{ display: 'flex', justifyContent: 'center', margin: '16px 0' }}>
      <svg width="240" height="240" viewBox="0 0 240 240">
        {[30,60,90,110].map((r,i)=>(
          <circle key={i} cx="120" cy="120" r={r} fill="none"
            stroke={i===0?COLORS.accent:COLORS.rule}
            strokeWidth={i===0?2:1}
            strokeDasharray={i===0?'none':'3 3'}/>
        ))}
        {[[30,40,COLORS.accent],[30,150,COLORS.accent],[30,260,COLORS.accent],[60,20,COLORS.ink],[60,180,COLORS.ink],[90,60,COLORS.ink],[90,220,COLORS.ink],[90,330,COLORS.ink]].map(([r,deg,c],i)=>{
          const rad = (deg-90)*Math.PI/180;
          return <circle key={i} cx={120+r*Math.cos(rad)} cy={120+r*Math.sin(rad)} r={4} fill={c}/>;
        })}
        <text x="120" y="125" textAnchor="middle" fontFamily={FONTS.hand} fontSize="14" fill={COLORS.ink}>now</text>
      </svg>
    </div>
    <div style={{ fontFamily: FONTS.hand, fontSize: 14, lineHeight: 1.7 }}>
      <div style={{ color: COLORS.accent }}>● today · 3 tasks · tap a dot for steps</div>
      <div>● yesterday · 5</div>
      <div style={{ color: COLORS.inkSoft }}>○ wed · 4</div>
    </div>
  </div>
);

const V5_Settings = () => (
  <div style={{ padding: '20px 18px', height: '100%' }}>
    <Hand size={22}>knobs</Hand>
    <Mono size={9} c={COLORS.inkSoft}>TWIST TO CHANGE</Mono>
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14, marginTop: 16 }}>
      {[['voice','PTT',120,COLORS.accent],['speed','1.0×',60,COLORS.ink],['trust','high',180,COLORS.accent],['assist','dial',90,COLORS.accent]].map(([k,v,angle,c],i)=>(
        <div key={i} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
          <div style={{ width: 72, height: 72, position: 'relative' }}>
            <div style={{ position: 'absolute', inset: 0, borderRadius: '50%', border: `1.5px solid ${COLORS.ink}` }}/>
            <div style={{
              position: 'absolute', top: 6, left: '50%',
              transform: `translateX(-50%) rotate(${angle}deg)`,
              transformOrigin: '50% 30px',
              width: 2, height: 14, background: c,
            }}/>
          </div>
          <Hand size={14}>{k}</Hand>
          <Hand size={12} c={c} italic>{v}</Hand>
        </div>
      ))}
    </div>
    <div style={{ marginTop: 18, padding: 12, borderTop: `1px dashed ${COLORS.rule}` }}>
      {['wake word','haptics','dark mode'].map(k=>(
        <div key={k} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0' }}>
          <Hand size={14}>{k}</Hand>
          <div style={{ width: 28, height: 16, borderRadius: 10, border: `1.5px solid ${COLORS.ink}`, position: 'relative' }}>
            <div style={{ position: 'absolute', top: 1, left: 1, width: 12, height: 12, borderRadius: 6, background: COLORS.ink }}/>
          </div>
        </div>
      ))}
    </div>
  </div>
);

// ═══════════════════════════════════════════════════════════════
// Grid / Layout
// ═══════════════════════════════════════════════════════════════

const SCREENS = ['Idle / Home', 'Listening', 'Ambient (agent working)', 'Assist (agent asking)', 'Done', 'Timeline', 'Settings'];

const VARIATIONS = [
  {
    key: 'orb', name: '01 · Orb',
    tag: 'big central PTT · timeline secondary',
    desc: 'Hero is a bold circular mic orb. During work, a small dark chip at top-left plus a thin bottom hint — live app is the screen.',
    screens: [V1_Idle, V1_Listening, V1_Ambient, V1_Assist, V1_Done, V1_Timeline, V1_Settings],
  },
  {
    key: 'bar', name: '02 · Bottom Capsule',
    tag: 'persistent PTT bar · content-first',
    desc: 'Docked bottom capsule. During work it morphs into a dark status pill; for assist it inflates into a full card with buttons + "speak to reply."',
    screens: [V2_Idle, V2_Listening, V2_Ambient, V2_Assist, V2_Done, V2_Timeline, V2_Settings],
  },
  {
    key: 'bleed', name: '03 · Full-bleed + Side Handle',
    tag: 'zero chrome · PTT on the edge',
    desc: 'Content-first. While working, a thin accent bar at the top + a pulsing dot on the side handle. Assist slides in from the handle side.',
    screens: [V3_Idle, V3_Listening, V3_Ambient, V3_Assist, V3_Done, V3_Timeline, V3_Settings],
  },
  {
    key: 'paper', name: '04 · Paper',
    tag: 'notebook metaphor · handwritten',
    desc: 'Ruled paper everywhere. Agent output appears as sticky notes (ambient) and margin notes (assist) laid on top of the live app.',
    screens: [V4_Idle, V4_Listening, V4_Ambient, V4_Assist, V4_Done, V4_Timeline, V4_Settings],
  },
  {
    key: 'dial', name: '05 · Radial Dial',
    tag: 'gestural · twist to operate',
    desc: 'PTT is a dial. Ambient shrinks it into a progress ring in the corner. Assist inflates the ring into a dial with 2–3 labels around it — twist or speak.',
    screens: [V5_Idle, V5_Listening, V5_Ambient, V5_Assist, V5_Done, V5_Timeline, V5_Settings],
  },
];

function App() {
  const [active, setActive] = React.useState(() => localStorage.getItem('wf_active') || 'all');
  const [density, setDensity] = React.useState(() => localStorage.getItem('wf_density') || 'comfy');
  const [accent, setAccent] = React.useState(() => localStorage.getItem('wf_accent') || '#c85a1e');
  const [showTweaks, setShowTweaks] = React.useState(false);

  React.useEffect(() => {
    localStorage.setItem('wf_active', active);
    localStorage.setItem('wf_density', density);
    localStorage.setItem('wf_accent', accent);
    document.documentElement.style.setProperty('--accent', accent);
  }, [active, density, accent]);

  React.useEffect(() => {
    const handler = (e) => {
      if (e.data?.type === '__activate_edit_mode') setShowTweaks(true);
      if (e.data?.type === '__deactivate_edit_mode') setShowTweaks(false);
    };
    window.addEventListener('message', handler);
    window.parent.postMessage({ type: '__edit_mode_available' }, '*');
    return () => window.removeEventListener('message', handler);
  }, []);

  const variationsToShow = active === 'all' ? VARIATIONS : VARIATIONS.filter(v => v.key === active);
  const gap = density === 'tight' ? 22 : density === 'airy' ? 48 : 32;

  return (
    <div style={{
      minHeight: '100vh', background: COLORS.paper, color: COLORS.ink,
      fontFamily: FONTS.hand, padding: '40px 48px',
    }}>
      <div style={{ maxWidth: 1400, margin: '0 auto 32px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', flexWrap: 'wrap', gap: 20 }}>
          <div>
            <Mono size={11} c={COLORS.inkSoft}>WIREFRAMES · V2 · APR 18</Mono>
            <Hand size={46} style={{ display: 'block', lineHeight: 1 }}>voice computer use, for phone.</Hand>
            <Hand size={20} c={COLORS.inkSoft} italic style={{ display: 'block', marginTop: 6 }}>
              the agent drives the phone itself — our UI is <u>overlays</u> on the live app
            </Hand>
          </div>
          <div style={{
            padding: 12, border: `1.5px dashed ${COLORS.ink}`, borderRadius: 8, maxWidth: 340,
          }}>
            <Mono size={10} c={COLORS.inkSoft}>MENTAL MODEL</Mono>
            <Hand size={14} style={{ display: 'block', marginTop: 4, lineHeight: 1.4 }}>
              the user only sees our UI in 4 moments: <u>speaking</u> to it, while it's working (<u>ambient</u> status), when it <u>asks</u> for input, and <u>after</u> (done / timeline).
              the rest of the time they see the real app.
            </Hand>
          </div>
        </div>

        <div style={{ display: 'flex', gap: 8, marginTop: 28, flexWrap: 'wrap', alignItems: 'center' }}>
          <Mono size={10} c={COLORS.inkSoft}>VIEW:</Mono>
          {[['all','all 5']].concat(VARIATIONS.map(v => [v.key, v.name.split(' · ')[1].toLowerCase()])).map(([k,label]) => (
            <div key={k} onClick={() => setActive(k)} style={{
              padding: '6px 14px', borderRadius: 100,
              border: `1.5px solid ${COLORS.ink}`,
              background: active === k ? COLORS.ink : 'transparent',
              cursor: 'pointer', fontFamily: FONTS.hand, fontSize: 14,
              color: active === k ? COLORS.paper : COLORS.ink,
            }}>{label}</div>
          ))}
          <div style={{ marginLeft: 'auto', display: 'flex', gap: 6 }}>
            <Mono size={10} c={COLORS.inkSoft} style={{ alignSelf: 'center' }}>SPACING:</Mono>
            {['tight','comfy','airy'].map(d => (
              <div key={d} onClick={() => setDensity(d)} style={{
                padding: '4px 10px', borderRadius: 12,
                border: `1px solid ${COLORS.ink}`,
                background: density === d ? COLORS.ink : 'transparent',
                color: density === d ? COLORS.paper : COLORS.ink,
                cursor: 'pointer', fontFamily: FONTS.hand, fontSize: 12,
              }}>{d}</div>
            ))}
          </div>
        </div>

        {/* legend for new columns */}
        <div style={{ display: 'flex', gap: 16, marginTop: 20, padding: '12px 14px', background: COLORS.paperDeep, borderRadius: 8, flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <div style={{ width: 10, height: 10, borderRadius: 2, background: '#fafafa', border: '1px solid #cbd5e0' }}/>
            <Mono size={10} c={COLORS.inkSoft}>= LIVE APP (not our UI)</Mono>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <div style={{ width: 10, height: 10, borderRadius: 2, background: COLORS.accentSoft, border: `1px solid ${COLORS.accent}` }}/>
            <Mono size={10} c={COLORS.inkSoft}>= OUR OVERLAY</Mono>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <Mono size={10} c={COLORS.inkSoft}>AMBIENT = agent working, minimal</Mono>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <Mono size={10} c={COLORS.accent}>ASSIST = agent needs input</Mono>
          </div>
        </div>
      </div>

      <div style={{ maxWidth: 1800, margin: '0 auto' }}>
        {variationsToShow.map(variation => (
          <div key={variation.key} style={{ marginBottom: gap * 2 }}>
            <div style={{
              padding: '14px 0 18px', borderBottom: `1.5px solid ${COLORS.ink}`,
              marginBottom: 28,
              display: 'flex', alignItems: 'baseline', gap: 18, flexWrap: 'wrap',
            }}>
              <Hand size={34}>{variation.name}</Hand>
              <Hand size={16} c={COLORS.inkSoft} italic>— {variation.tag}</Hand>
              <div style={{ flex: 1, minWidth: 200 }}/>
              <Hand size={14} c={COLORS.inkSoft} style={{ maxWidth: 400 }}>
                {variation.desc}
              </Hand>
            </div>
            <div style={{ display: 'flex', gap, flexWrap: 'wrap', justifyContent: 'flex-start' }}>
              {variation.screens.map((ScreenComp, i) => (
                <Phone key={i} label={SCREENS[i]}>
                  <ScreenComp/>
                </Phone>
              ))}
            </div>
          </div>
        ))}

        <div style={{
          marginTop: 40, padding: 20,
          border: `1.5px dashed ${COLORS.ink}`, borderRadius: 10, maxWidth: 720,
        }}>
          <Mono size={10} c={COLORS.accent}>NOTES · NEXT</Mono>
          <div style={{ fontFamily: FONTS.hand, fontSize: 16, lineHeight: 1.45, marginTop: 6 }}>
            — ambient = 95% of the agent's working time; must not cover actionable UI in the live app.<br/>
            — assist = moments where the agent truly needs a human — dim the live app, offer 2–3 chips + "talk to reply".<br/>
            — step log (✓ opened kayak · ✓ searched...) now lives in Timeline as an expandable detail, not during work.<br/>
            — which direction should go to hi-fi? pick overall vibe + ambient style + assist style independently.
          </div>
        </div>
      </div>

      {showTweaks && (
        <div style={{
          position: 'fixed', bottom: 20, right: 20,
          width: 260, padding: 16, background: COLORS.paper,
          border: `2px solid ${COLORS.ink}`, borderRadius: 10,
          boxShadow: '4px 6px 0 rgba(0,0,0,0.1)', zIndex: 100,
        }}>
          <Mono size={10} c={COLORS.accent}>TWEAKS</Mono>
          <Hand size={18} style={{ display: 'block', marginTop: 2 }}>adjust</Hand>
          <div style={{ marginTop: 10 }}>
            <Mono size={9}>ACCENT</Mono>
            <div style={{ display: 'flex', gap: 6, marginTop: 6 }}>
              {['#c85a1e','#3a5a40','#1a1613','#7b3aed','#b8860b'].map(c => (
                <div key={c} onClick={() => setAccent(c)} style={{
                  width: 24, height: 24, borderRadius: '50%', background: c,
                  border: accent === c ? `2px solid ${COLORS.ink}` : 'none', cursor: 'pointer',
                }}/>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
