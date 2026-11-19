/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 *
 * Copied verbatim from the Gua Wallpaper Lab page, script 1 of 4 (the Live section).
 * Do not edit here: change the Lab, then copy the whole script again.
 */

// Gua live wallpapers (section 'live', kind 'webgl').
// Original work, written from scratch for Gua. Licence: ours.
// The only adapted snippet is hash12 / hash22 ("Hash without Sine", David Hoskins, MIT licence).
//
// Uniform contract: u_res (px), u_time (s), u_dark (0|1), u_pulse (0..1 send envelope),
// u_tilt (-1..1). Optional extension: u_origin (px, same space as gl_FragCoord) is the centre
// of the bubble that was just sent. A host that never sets it leaves it at (0,0), and every
// shader then falls back to where the newest outgoing bubble sits (83% of the width, 11% of
// the height from the bottom), so the plain contract keeps working.
// Coordinates are GL style: gl_FragCoord origin bottom-left, y up. Metal and AGSL put the
// origin top-left, so ports use y = u_res.y - fragCoord.y (and flip u_origin the same way).
// In q-space below, q = fragCoord / u_res.y: x runs 0..aspect, y runs 0..1 from the bottom.
// The bubble band (45% to 92% from the top) is q.y 0.08..0.55; subjects live in q.y 0.55..0.85.
(function () {
  'use strict';

  // Shared prelude, pasted into every shader so each string is self-contained.
  var HEAD = [
    'precision highp float;',
    'uniform vec2 u_res;',
    'uniform float u_time;',
    'uniform float u_dark;',
    'uniform float u_pulse;',
    'uniform vec2 u_tilt;',
    'uniform vec2 u_origin;',
    // Hash without Sine, David Hoskins, MIT licence.
    'float hash12(vec2 p){vec3 p3=fract(vec3(p.xyx)*0.1031);p3+=dot(p3,p3.yzx+33.33);return fract((p3.x+p3.y)*p3.z);}',
    'vec2 hash22(vec2 p){vec3 p3=fract(vec3(p.xyx)*vec3(0.1031,0.1030,0.0973));p3+=dot(p3,p3.yzx+33.33);return fract((p3.xx+p3.yz)*p3.zy);}',
    'float sq(float x){return x*x;}',
    // Triangular dither of about 1.5 LSB so 8-bit output never bands.
    'vec3 dither(vec3 c){float n=hash12(gl_FragCoord.xy)+hash12(gl_FragCoord.yx+17.17)-1.0;return c+n*(1.5/255.0);}',
    // Where the send reaction starts, in q-space.
    'vec2 sendAt(float asp){return (u_origin.x+u_origin.y>0.5)?u_origin/u_res.y:vec2(asp*0.83,0.113);}',
    // Send bloom: Gaussian that has faded to 5% at 45% of the screen width.
    'float sendGlow(vec2 q,vec2 o,float asp){vec2 v=(q-o)/(0.45*asp);return u_pulse*exp(-3.0*dot(v,v));}',
    // Radius of a ring that leaves the sent bubble as the envelope decays.
    'float ringR(float asp){return 0.02+0.75*asp*pow(max(1.0-u_pulse,0.0),0.6);}',
    // 1 inside the bubble band, 0 above and below it.
    'float bubbleBand(float y){return smoothstep(0.03,0.12,y)*smoothstep(0.62,0.50,y);}',
    // Cheap value noise (ours).
    'float vnoise(vec2 p){vec2 i=floor(p);vec2 f=fract(p);f=f*f*(3.0-2.0*f);',
    '  return mix(mix(hash12(i),hash12(i+vec2(1.0,0.0)),f.x),mix(hash12(i+vec2(0.0,1.0)),hash12(i+vec2(1.0,1.0)),f.x),f.y);}',
    ''
  ].join('\n');

  var W = window.GUA_WALLPAPERS = window.GUA_WALLPAPERS || [];
  // Colours are written as hex(0xRRGGBB.0) for readability and expanded here to vec3 literals,
  // so the stored shader is plain GLSL that ports directly to Metal and AGSL.
  function lit(m, h) {
    var n = parseInt(h, 16);
    var f = function (v) { return (v / 255).toFixed(4); };
    return 'vec3(' + f((n >> 16) & 255) + ',' + f((n >> 8) & 255) + ',' + f(n & 255) + ')';
  }
  function add(o) {
    o.section = 'live';
    o.kind = 'webgl';
    o.frag = HEAD + o.frag.replace(/hex\(0x([0-9A-Fa-f]{6})\.0\)/g, lit);
    W.push(o);
  }
  function hx(s) { return 'hex(0x' + s.replace('#', '') + '.0)'; }
  function fl(n) { return Number(n).toFixed(3); }

  // ------------------------------------------------------------- Glass Drops
  // Glass beads that rise in three lanes; only the two drops of a lane ever meet, so at most
  // two merge at once. Each bead refracts the ribbed background, carries one upper-left
  // highlight, a hairline rim and a warm inner caustic. Sending repels nearby beads and buds
  // a new bead from the sent bubble.
  function glass(P) {
    var L = P.light, D = P.dark;
    return `
float smin(float a,float b,float k){float h=clamp(0.5+0.5*(b-a)/k,0.0,1.0);return mix(b,a,h)-k*h*(1.0-h);}
vec3 bgc(vec2 p,vec3 b0,vec3 b1,vec3 bs,float bw){
  vec3 c=mix(b0,b1,clamp(p.y,0.0,1.0));
  float a=0.5+0.5*sin(p.x*5.0+p.y*9.0+0.8*sin(p.y*2.3-p.x*1.7+u_time*0.04)+u_time*0.025);
  float r=0.5+0.5*sin(p.x*48.0-p.y*20.0);
  return mix(c,bs,bw*(0.75*a*a+0.25*r*a));
}
void main(){
  float asp=u_res.x/u_res.y;
  vec2 q=gl_FragCoord.xy/u_res.y;
  float t=u_time;
  vec3 B0,B1,BS,D0,D1,RM,GW,PG; float RA,RIM,BW,CT,SH;
  if(u_dark>0.5){
    B0=${hx(D.b0)};B1=${hx(D.b1)};BS=${hx(D.bs)};D0=${hx(D.d0)};D1=${hx(D.d1)};RM=${hx(D.rim)};GW=${hx(D.glow)};PG=${hx(D.pg)};
    RA=${fl(D.ra)};RIM=${fl(D.rimA)};BW=${fl(D.bw)};CT=${fl(D.ct)};SH=${fl(D.sh)};
  } else {
    B0=${hx(L.b0)};B1=${hx(L.b1)};BS=${hx(L.bs)};D0=${hx(L.d0)};D1=${hx(L.d1)};RM=${hx(L.rim)};GW=${hx(L.glow)};PG=${hx(L.pg)};
    RA=${fl(L.ra)};RIM=${fl(L.rimA)};BW=${fl(L.bw)};CT=${fl(L.ct)};SH=${fl(L.sh)};
  }
  vec2 o=sendAt(asp);
  vec2 p=q+u_tilt*0.02;
  float d=1000.0; vec2 g=vec2(0.0,1.0);
  for(int i=0;i<6;i++){
    float fi=float(i);
    float pr=floor(fi*0.5);
    float sd=mod(fi,2.0)*2.0-1.0;
    float yy=fract(t/58.0+pr*0.3333+0.08)*1.5-0.25;
    float lane=asp*(0.20+0.30*pr)+0.035*sin(t*0.07+pr*2.1)+0.025*sin(yy*5.0+pr);
    float sep=0.050+0.032*sin(t*0.09+pr*1.7);
    float ang=t*0.045+pr*2.0;
    vec2 c=vec2(lane,yy)+sd*sep*vec2(cos(ang),0.7*sin(ang));
    vec2 oc=c-o;
    c+=oc/(length(oc)+0.001)*0.07*u_pulse*exp(-length(oc)*5.0);
    float r=asp*mix(0.070,0.138,fract(fi*0.618+0.15));
    vec2 v=p-c; float l=length(v);
    float di=l-r;
    float h=clamp(0.5+0.5*(di-d)/0.022,0.0,1.0);
    g=mix(v/(l+0.0001),g,h);
    d=mix(di,d,h)-0.022*h*(1.0-h);
  }
  // The bead that buds from the sent bubble.
  vec2 bc=o+vec2(-0.05*asp,0.060);
  vec2 bv=p-bc; float bl=length(bv);
  float bdi=bl-asp*0.11*u_pulse;
  float bh=clamp(0.5+0.5*(bdi-d)/0.03,0.0,1.0);
  g=mix(bv/(bl+0.0001),g,bh);
  d=mix(bdi,d,bh)-0.03*bh*(1.0-bh);
  g=normalize(g);
  float px=1.0/u_res.y;
  float inside=smoothstep(px,-px,d);
  float s=clamp(-d/0.030,0.0,1.0);
  float sl=sqrt(max(1.0-s*s,0.0));
  vec3 n=normalize(vec3(g*sl*1.4,0.25+s));
  float fac=dot(g*sl,vec2(-0.64,0.77));
  // Refraction: the background seen through the bead is pulled in and its ribbing magnified.
  vec3 bgR=bgc(p-n.xy*0.07,B0,B1,BS,min(BW*2.2,1.0));
  float edgeB=sq(1.0-s);
  vec3 tint=mix(D0,D1,clamp(0.15-0.35*fac+0.85*edgeB,0.0,1.0));
  vec3 body=mix(bgR,tint,RA*(CT+(1.0-CT)*edgeB));
  float caus=sq(max(-fac,0.0))*smoothstep(0.10,0.55,s);
  body=mix(body,GW,caus*0.75);
  vec3 H=normalize(vec3(-0.42,0.52,1.0));
  float nh=max(dot(n,H),0.0);
  float spec=pow(nh,90.0)*1.3+pow(nh,14.0)*0.22;
  float rim=exp(-abs(d)/(1.1*px));
  float fade=max(1.0-0.6*bubbleBand(q.y),u_pulse*exp(-length(q-bc)*9.0));
  vec3 col=bgc(p,B0,B1,BS,BW);
  float shadow=exp(-max(d,0.0)*16.0)*max(dot(g,vec2(0.55,-0.83)),0.0)*(1.0-inside);
  col=mix(col,D1,shadow*SH*fade);
  // Outside: a soft coloured caustic thrown down-right, a faint halo all round.
  float outd=max(d,0.0);
  float thrown=exp(-outd*30.0)*max(dot(g,vec2(0.55,-0.83)),0.0)*(1.0-inside);
  float halo=exp(-outd*40.0)*(1.0-inside);
  col=mix(col,GW,clamp((thrown*0.45+halo*0.12)*fade,0.0,1.0));
  col=mix(col,body,inside*fade);
  col=mix(col,vec3(1.0),clamp(spec,0.0,1.0)*inside*fade);
  col=mix(col,RM,rim*RIM*fade*(0.35+0.65*max(fac,0.0)));
  float sg=sendGlow(q,o,asp);
  col=mix(col,PG,clamp(sg*0.65,0.0,1.0));
  gl_FragColor=vec4(dither(clamp(col,0.0,1.0)),1.0);
}`;
  }

  add({
    id: 'live-bubble-lamp',
    name: 'Glass Drops',
    blurb: 'Rose glass beads rise and gently merge, bending the light behind them; each message buds a new bead from your bubble.',
    bubble: { light: 'purple', dark: 'pink' },
    frag: glass({
      light: { b0: '#F7E9EC', b1: '#FBF4EF', bs: '#F3DCD9', d0: '#F7C3B5', d1: '#EF8E80', rim: '#FFFFFF', glow: '#FBD9CC', pg: '#F6C2BB', ra: 0.75, rimA: 0.9, bw: 0.55, ct: 0.40, sh: 0.10 },
      dark: { b0: '#130B19', b1: '#1E1026', bs: '#2A1531', d0: '#D9607F', d1: '#6B2A55', rim: '#FFB3C4', glow: '#F08AA0', pg: '#7A2C55', ra: 0.75, rimA: 0.5, bw: 0.6, ct: 0.55, sh: 0.0 }
    })
  });
  add({
    id: 'live-glass-drops-blue',
    name: 'Glass Drops Blue',
    blurb: 'The same glass beads in sea blue and ice; each message buds a new bead from your bubble.',
    bubble: { light: 'teal', dark: 'teal' },
    frag: glass({
      light: { b0: '#E8F0F6', b1: '#F4F7FA', bs: '#DAE7F1', d0: '#BFE3F0', d1: '#5FA3CF', rim: '#FFFFFF', glow: '#CDEFF7', pg: '#B9DCF0', ra: 0.72, rimA: 0.9, bw: 0.55, ct: 0.40, sh: 0.10 },
      dark: { b0: '#0C1420', b1: '#101C2C', bs: '#16263A', d0: '#7FD3E6', d1: '#2F6FA3', rim: '#CFF4FF', glow: '#8FE0F0', pg: '#1F5A80', ra: 0.75, rimA: 0.5, bw: 0.6, ct: 0.55, sh: 0.0 }
    })
  });

  // ------------------------------------------------------------- Molten Pearl
  // Broad curved folds of mother-of-pearl poured from the top-left corner. Each fold has a
  // 2-3 px specular crest, a broad soft shadow just past it and an iridescent lit flank.
  add({
    id: 'live-molten-pearl',
    name: 'Molten Pearl',
    blurb: 'Slow folds of liquid mother-of-pearl with a quiet iridescent sheen; each message sends a fold rolling out from your bubble.',
    bubble: { light: 'purple', dark: 'blue' },
    frag: `
float phs(vec2 p,float t){
  vec2 w=p+0.09*vec2(sin(p.y*2.9+t*0.9),sin(p.x*2.5-t*0.7+1.3))
          +0.035*vec2(sin(p.y*6.1-t*0.6+2.0),sin(p.x*5.7+t*0.5));
  return length(w-vec2(-0.30,1.30))*24.0+2.4*sin(w.x*2.1-w.y*1.6+t*0.8)+1.2*sin(w.x*4.3+w.y*3.1-t*0.6)-t*1.2;
}
void main(){
  float asp=u_res.x/u_res.y;
  vec2 q=gl_FragCoord.xy/u_res.y;
  float t=u_time*0.10;
  vec2 o=sendAt(asp);
  vec2 p=q+u_tilt*0.012;
  float od=length(q-o);
  float wave=exp(-sq((od-ringR(asp))/0.06))*sqrt(u_pulse);
  float e=1.5/u_res.y;
  float ph=phs(p,t)+2.2*wave;
  float phx=phs(p+vec2(e,0.0),t);
  float phy=phs(p+vec2(0.0,e),t);
  float gm=max(length(vec2(phx-ph+2.2*wave,phy-ph+2.2*wave))/e,1.0);
  float x=fract(ph/6.2832);
  float dc=min(x,1.0-x)*6.2832/gm*u_res.y;
  float ridge=exp(-sq(dc/1.3));
  float lit=pow(smoothstep(0.30,1.0,x),1.6);
  float shade=smoothstep(0.0,0.03,x)*(1.0-smoothstep(0.03,0.42,x));
  float k=1.0-0.4*bubbleBand(q.y);
  float hc=fract(ph*0.035+q.x*0.35+q.y*0.25+t*0.25)*3.0;
  vec3 col;
  if(u_dark>0.5){
    vec3 s0=hex(0x3A3354.0),s1=hex(0x1E3A44.0),s2=hex(0x463047.0);
    vec3 sh=hc<1.0?mix(s0,s1,smoothstep(0.0,1.0,hc)):(hc<2.0?mix(s1,s2,smoothstep(1.0,2.0,hc)):mix(s2,s0,smoothstep(2.0,3.0,hc)));
    col=mix(hex(0x0C0E15.0),hex(0x1F2334.0),lit);
    col=mix(col,sh,sq(lit)*0.85*k);
    col=mix(col,hex(0x07080C.0),shade*0.75);
    col=mix(col,hex(0x9AA6CF.0),ridge*0.30*mix(0.75,1.0,k));
    col+=hex(0x6070A8.0)*0.30*sendGlow(q,o,asp);
    col=mix(col,hex(0x9AA6CF.0),wave*0.18);
  } else {
    vec3 s0=hex(0xF3D6E8.0),s1=hex(0xD5E6F7.0),s2=hex(0xDDF3E6.0);
    vec3 sh=hc<1.0?mix(s0,s1,smoothstep(0.0,1.0,hc)):(hc<2.0?mix(s1,s2,smoothstep(1.0,2.0,hc)):mix(s2,s0,smoothstep(2.0,3.0,hc)));
    col=hex(0xF4F1F6.0);
    col=mix(col,sh,(0.25+0.75*lit)*0.9*k);
    col=mix(col,hex(0xDCD6E4.0),shade*0.70*k);
    col=mix(col,vec3(1.0),ridge*0.70);
    col=mix(col,hex(0xDCC8F0.0),clamp(sendGlow(q,o,asp)*0.7,0.0,1.0));
  }
  gl_FragColor=vec4(dither(clamp(col,0.0,1.0)),1.0);
}`
  });

  // ---------------------------------------------------------------- Boreal
  // Dark: broad, soft aurora bands that curl and fan across the sky over a spruce ridge; the send
  // brightens the band near the sent bubble.
  // Light: the same ridge on a winter afternoon with snow drifting down; the send blows a
  // puff of snow off the branches around the sent bubble.
  add({
    id: 'live-boreal',
    name: 'Boreal',
    blurb: 'Spruce forest in falling snow by day and under the northern lights by night; a message shakes snow from the branches.',
    bubble: { light: 'blue', dark: 'green' },
    frag: `
// Exact cell hash: every value is a whole number below 2^20, which fp32 holds exactly, so a fused
// multiply-add cannot change it and what the baked still shows is what every GPU draws.
// (x+0.5)/1019 is at least 1/2038 from a whole number and the product below is within 1e-4 of it,
// so floor() is exact.
float ihm(float x){return x-1019.0*floor((x+0.5)*(1.0/1019.0));}
float icube(float n){return ihm(ihm(n*n)*n);}
// c: whole-number cell within +-4096, s: whole-number salt; returns a whole number in 0..1018.
float ih(vec2 c,float s){return icube(ihm(icube(ihm(c.x*19.0+c.y*56.0+s*211.0+101.0))+c.x*233.0+s*331.0+13.0));}
// For whole n in 1..1018, n*K stays at least 9e-4 from a whole number (n = 0 gives exactly 0), so fract() cannot wrap.
vec2 ih2(float n){return fract(n*vec2(0.3271483,0.9194175));}
// One cube per point of a 1D lattice: x whole within +-4096, y a whole row. Neighbours along x stay nearly
// uncorrelated and each row is a different stretch of the sequence. Returns a whole number in 0..1018.
float ih1(float x,float y){return icube(ihm(x*56.0+y*7.0+97.0));}
// 2D lattice value for a reduced x (ihm) and y within +-50; the cross term gives each row its own stride.
float ivx(float x,float y){return icube(ihm(x*13.0+y*56.0+x*y*19.0+1156.0));}
float iv(vec2 c){return ivx(ihm(c.x),c.y);}
// vnoise on the exact hash, so no lattice value can jump between 0 and 1 where the GPU fuses.
float ivnoise(vec2 p){vec2 i=floor(p);vec2 f=fract(p);f=f*f*(3.0-2.0*f);float a=ihm(i.x);float b=ihm(i.x+1.0);
  return mix(mix(ivx(a,i.y),ivx(b,i.y),f.x),mix(ivx(a,i.y+1.0),ivx(b,i.y+1.0),f.x),f.y)/1019.0;}
float flakes(vec2 q,vec2 o,float t,float asp){
  float acc=0.0;
  for(int i=0;i<3;i++){
    float fi=float(i);
    float sc=14.0+fi*10.0;
    vec2 p=q;
    vec2 ov=p-o; float od=length(ov);
    p-=ov/(od+0.001)*0.10*u_pulse*exp(-od*6.0);
    p.y+=t*(0.018-fi*0.004);
    p.x+=0.02*sin(p.y*3.0+t*0.2+fi*2.0)+u_tilt.x*(0.004+fi*0.006);
    p*=sc;
    vec2 id=floor(p); vec2 f=fract(p)-0.5;
    // Rows fold into 64 (exact: a power of two) to keep iv's row small; the snow repeats only after 64 rows.
    float n=iv(vec2(id.x+fi*307.0,id.y-64.0*floor(id.y*0.015625)-32.0));
    float h=n/1019.0;
    vec2 jo=(ih2(n)-0.5)*0.6;
    float rpx=mix(3.4,1.4,fi*0.5)*(0.7+0.6*h);
    float r=length(f-jo)*u_res.y/sc;
    float on=step(0.62-0.55*exp(-od*6.0)*u_pulse,h);
    acc+=on*smoothstep(rpx,rpx*0.2,r)*(1.0-fi*0.22);
  }
  return acc;
}
// 1D value noise along x that repeats every n cells, so a drift of n cells per 1800 s wraps
// without a jump; y selects an independent row. The lattice index is reduced with a half-cell margin, so a
// division a few ulps off cannot pick the wrong cell.
float vn1(float x,float y,float n){
  float i=floor(x); float f=x-i;
  float a=i-n*floor((i+0.5)/n);
  float b=a+1.0; b-=n*step(n-0.5,b);
  return mix(ih1(a,y+20.0),ih1(b,y+20.0),f*f*(3.0-2.0*f))/1019.0;
}
// Send surge at distance dA along the band from the sent bubble: a brightening there and a front
// that runs outward as the envelope decays. Zero at p = 0.
float surgeAt(float dA,float p){
  float fr=0.04+0.56*pow(max(1.0-p,0.0),0.8);
  return p*(exp(-dA/0.22)+1.5*exp(-sq((dA-fr)/0.13)));
}
// The aurora band at p. c is the 1800 s cycle angle and tc the cycle fraction: every slow motion
// runs a whole number of cycles per 1800 s. pz is the eased send pulse and ox the sent bubble's x.
vec3 aurora(vec2 p,float asp,float c,float tc,float pz,float ox){
  // Every slow layout motion is a whole number of turns per loop, taken from powers of one unit phasor,
  // so they cost two trig calls.
  vec2 z1=vec2(cos(c),sin(c));
  vec2 z2=vec2(z1.x*z1.x-z1.y*z1.y,2.0*z1.x*z1.y);
  vec2 z3=vec2(z2.x*z1.x-z2.y*z1.y,z2.x*z1.y+z2.y*z1.x);
  vec2 z4=vec2(z2.x*z2.x-z2.y*z2.y,2.0*z2.x*z2.y);
  vec2 z5=vec2(z4.x*z1.x-z4.y*z1.y,z4.x*z1.y+z4.y*z1.x);
  vec2 z6=vec2(z3.x*z3.x-z3.y*z3.y,2.0*z3.x*z3.y);
  vec2 z7=vec2(z6.x*z1.x-z6.y*z1.y,z6.x*z1.y+z6.y*z1.x);
  // The band's lower edge is a wide V with a rounded tip and asymmetric arms; a vortex warp curls one
  // arm over a dark gap. m turns the layout over once per loop, and while it turns the band is a
  // broad, even U.
  float m=clamp(0.8+2.4*dot(z1,vec2(0.8085,-0.5885)),-1.0,1.0);
  float hm=0.5+0.5*m;
  float xf=asp*(0.5-0.12*m+0.09*dot(z2,vec2(0.3894,0.9211))+0.04*dot(z5,vec2(0.8912,0.4536)));
  float yf=0.655+0.018*dot(z3,vec2(0.9093,-0.4161))+0.01*z7.y;
  // The fold never closes tighter than this, so its tip stays round instead of a chevron.
  float ef=0.078+0.015*dot(z4,vec2(0.7174,0.6967));
  float ka=0.95+0.12*dot(z3,vec2(0.9917,-0.1288));
  float kb=(-0.05+0.1*dot(z2,vec2(0.2392,-0.971)))*m;
  // The vortex sits a little above the curled arm, so the arm wraps part way round a dark gap instead of
  // knotting on itself. Its turn has the sign of m, so it fades out as the layout turns over and it
  // jumps to the other side only at m = 0, where it has no effect.
  float vx=asp*(0.5+sign(m)*(0.07*dot(z3,vec2(0.8415,0.5403))-0.36));
  vec2 v1=vec2(vx,min(yf+ka*(sqrt(sq(vx-xf)+ef*ef)-ef)+kb*(vx-xf)+0.075+0.02*dot(z4,vec2(0.5985,-0.8011)),0.86));
  float S1=(1.45+0.3*dot(z6,vec2(0.2955,0.9553)))*m;
  float R1=0.14;
  vec2 d1=p-v1;
  float th=S1*exp(-dot(d1,d1)/(R1*R1));
  float cs=cos(th); float sn=sin(th);
  vec2 e1=vec2(cs*d1.x-sn*d1.y,sn*d1.x+cs*d1.y);
  vec2 w=v1+e1;
  float dx=w.x-xf;
  float far=smoothstep(0.06,0.35,abs(dx));
  float ph=11.0*w.x-38.0*c+1.0;
  float fa=0.02+0.016*far;
  w.y+=fa*sin(ph);
  float hy=sqrt(dx*dx+ef*ef);
  float ph2=17.0*w.x+23.0*c+2.0;
  // The arms bow upward as they leave the fold, so neither runs as a straight ruler edge.
  float yl=yf+ka*(hy-ef)+kb*dx+0.6*dx*dx+0.012*sin(ph2);
  float F=w.y-yl;
  // Slope for the distance estimate, taken from a fold rounded by the distance |F|, so the glow's
  // contours round off below the tight fold instead of creasing straight down from it.
  float dyl=ka*dx/sqrt(dx*dx+sq(ef+0.7*abs(F)))+kb+1.2*dx+0.204*cos(ph2);
  // Screen gradient of F = w.y - yl, chained back through the fold wave and the vortex, so f is a
  // screen distance and g tells how much the band is squeezed on screen.
  vec2 g=vec2(-dyl,1.0);
  g.x+=11.0*fa*cos(ph)*g.y;
  vec2 gr=vec2(cs*g.x+sn*g.y,-sn*g.x+cs*g.y);
  g=gr-2.0*th/(R1*R1)*d1*dot(vec2(-e1.y,e1.x),g);
  float G=length(g);
  float f=F/G;
  // Rays converge on a point high overhead.
  vec2 V=vec2(asp*(0.5+0.1*dot(z2,vec2(0.9636,0.2675))),1.8);
  float ra=(p.x-V.x)/(V.y-p.y);
  float r1=vn1(ra*170.0+tc*900.0,1.0,900.0);
  float r2=vn1(ra*65.0-tc*120.0,2.0,120.0);
  float r3=vn1(ra*20.0+tc*60.0,3.0,60.0);
  float rt=smoothstep(0.15,0.95,0.5*r1+0.5*r2);
  float lf=mix(smoothstep(0.0,0.25,dx),smoothstep(0.0,-0.25,dx),hm);
  float rg=mix(smoothstep(0.0,-0.3,dx),smoothstep(0.0,0.3,dx),hm);
  float fold=exp(-dx*dx/0.004);
  float al=0.5+0.5*sin(6.0*w.x+29.0*c+0.4);
  // Long stretches of the band dim, the lower margin most, so the bright edge never runs as one even
  // stroke.
  float gm=smoothstep(0.1,0.75,vn1(w.x*4.0-tc*60.0,7.0,60.0));
  // Higher parts of the band are nearer the zenith, so they are thicker and softer.
  float so=(0.026*(r3-0.5)+0.005*(r2-0.5))*(1.0-0.7*fold);
  float top=(0.085+0.04*lf+0.025*rg+0.2*clamp(p.y-0.62,0.0,0.35))*(0.7+0.6*r3)*(0.9+0.2*r2)*(0.8+0.4*al);
  float es=0.014+0.03*lf+0.045*rg*far;
  float fe=f-so;
  float up=max(fe,0.0);
  float inside=smoothstep(-es,es,fe);
  float gp=ivnoise(w*vec2(5.0,6.0)+3.0*z1+vec2(0.6*z5.x,0.0));
  float bm=0.5+0.5*smoothstep(0.2,0.7,gp);
  float sk=smoothstep(0.25,0.8,gp);
  float u=up/top;
  float body=inside*exp(-u*u)*mix(1.0,0.35+0.65*rt,smoothstep(0.0,0.05,up)*(0.45+0.55*(1.0-lf)))*bm;
  body+=inside*0.55*exp(-up/0.03)*(1.0-0.5*lf)*(0.55+0.45*gm)*(0.75+0.25*rt);
  body+=(0.5*rt+0.12)*r3*(0.2+0.8*rg*rg)*exp(-max(F,0.0)/(0.05+0.26*rg*rg))*smoothstep(-0.01,0.02,F);
  float below=(1.0-inside)*exp(fe/(0.03+0.02*lf+0.05*rg*far))*0.5*(0.6+0.4*gm);
  float glow=exp(-abs(f)/0.08)*0.2;
  float halo=(0.3+0.9*sk)/(1.0+sq(f/0.13));
  float lo=(1.0-inside)*exp(f/0.125);
  float veil=lo*0.17*smoothstep(-0.3,1.0,sin(5.0*w.x-17.0*c+1.0));
  float rip=smoothstep(0.2,1.0,sin(9.0*w.x-47.0*c+0.7));
  float inten=(0.65+0.55*fold+0.35*rip+0.3*al+0.15*lf)*clamp(G,0.7,1.6);
  // The send surge brightens the band itself and the glow just under it, not the sky far below.
  float sv=0.0;
  if(pz>0.0) sv=surgeAt(abs(p.x-ox),pz);
  vec3 cl=mix(hex(0x34F27F.0),hex(0xC4FF4A.0),max(fold,0.3*rip)*exp(-up/0.045));
  vec3 cb=mix(cl,hex(0x22CC8A.0),smoothstep(0.5,1.2,u));
  cb=mix(cb,hex(0x7A4FD0.0),smoothstep(1.1,2.2,u)*0.6);
  float endm=smoothstep(0.12,0.3,abs(dx));
  vec3 ac=(cb*body*1.65+mix(hex(0x2EE88A.0),hex(0xA040C8.0),lf*0.8)*below)*(1.0+1.3*sv)+hex(0x2EE88A.0)*(glow+veil);
  ac=mix(ac,ac.g*0.75*hex(0xA23CC8.0),endm*0.35*smoothstep(0.0,0.08,up+0.04));
  ac+=hex(0x7A2CA8.0)*lf*lo*0.3;
  ac*=inten;
  vec3 hc=mix(hex(0x1E9E70.0),hex(0x2A3290.0),smoothstep(0.12,0.35,f));
  hc=mix(hc,hex(0x522A88.0),max(endm,lf*0.6)*smoothstep(0.0,-0.2,f));
  ac+=hc*halo*0.16;
  return ac;
}
void main(){
  float asp=u_res.x/u_res.y;
  vec2 q=gl_FragCoord.xy/u_res.y;
  float t=u_time;
  bool dk=u_dark>0.5;
  vec2 o=sendAt(asp);
  float hz=0.15;
  float s=clamp((q.y-hz)/(1.0-hz),0.0,1.0);
  vec3 col;
  if(dk) col=mix(mix(hex(0x0E2733.0),hex(0x061024.0),smoothstep(0.0,0.25,s)),hex(0x050A1C.0),smoothstep(0.25,0.6,s));
  else {
    col=mix(hex(0xF7EFEA.0),hex(0xEAF1F7.0),smoothstep(0.0,0.75,s));
    col=mix(col,hex(0xDCE7F1.0),smoothstep(0.55,1.0,s)*0.6);
    vec2 sv=q-vec2(asp*0.74,0.80);
    col=mix(col,hex(0xFFF8EE.0),exp(-dot(sv,sv)*14.0)*0.75);
    col=mix(col,hex(0xFFFDF8.0),smoothstep(0.034,0.028,length(sv))*0.9);
    // Far hills in winter haze.
    float fh=hz+0.055+0.025*sin(q.x*5.0+1.3)+0.012*sin(q.x*13.0+0.4);
    col=mix(col,hex(0xD6E0EA.0),smoothstep(fh+0.002,fh-0.002,q.y)*0.8);
  }
  if(dk){
    // One 2 pi cycle per 1800 s.
    float cyc=u_time*0.0034906585;
    float tc=u_time/1800.0;
    float pz=u_pulse*smoothstep(0.0,0.08,u_pulse);
    // Low in the sky the aurora adds only a few LSB, so it fades out there and is skipped below.
    vec3 ac=vec3(0.0);
    if(q.y>0.2) ac=aurora(q+u_tilt*vec2(0.012,0.008),asp,cyc,tc,pz,o.x)*smoothstep(0.2,0.4,q.y);
    // Hue-preserving roll-off, then a whitish lime core where it saturates.
    float mx=max(ac.g,max(ac.r,ac.b));
    vec3 tm=ac*(1.0-exp(-mx))/max(mx,0.0001);
    tm+=(hex(0xF0FFD8.0)-tm)*smoothstep(2.4,4.2,mx)*0.25;
    tm*=1.0-0.6*bubbleBand(q.y);
    float lum=dot(tm,vec3(0.3,0.5,0.2));
    // Stars with a radius in device pixels, twinkling at whole cycles per 1800 s, fading behind the aurora.
    vec2 sp=(q+u_tilt*vec2(0.02,0.0133))*70.0; vec2 id=floor(sp); vec2 f=fract(sp)-0.5;
    float ns=ih(id,0.0);
    float h=ns/1019.0;
    vec2 jo=ih2(ns)-0.5;
    float tw=0.6+0.4*sin(6.2831853*fract(tc*floor(229.0+430.0*h))+h*40.0);
    float sb=fract(h*13.0);
    float sr=length(f-jo*0.7)*u_res.y/70.0;
    float st=step(0.925,h)*smoothstep(1.1+1.3*sb,0.3,sr)*tw*(0.45+0.55*sb);
    col+=st*0.55*smoothstep(0.2,0.42,q.y)*exp(-1.3*lum);
    col+=tm;
  }
  // Two spruce ridges with parallax; each cell holds one narrow tiered spruce.
  for(int l=0;l<2;l++){
    float fl=float(l);
    float x=q.x+u_tilt.x*(0.006+0.012*fl);
    float n=mix(44.0,27.0,fl);
    float cx=x*n;
    float idx=floor(cx);
    float hh=ih1(idx,fl*7.0+1.0)/1019.0;
    float fx=fract(cx)-0.5-(ih1(idx,fl*3.0+5.0)/1019.0-0.5)*0.25;
    float base=hz-0.02-fl*0.05+0.014*sin(x*7.0+fl*3.0)+0.006*sin(x*19.0+fl);
    float ht=mix(0.035,0.085,hh*hh)*mix(0.85,1.3,fl);
    float vrel=(base+ht-q.y)/ht;
    float tier=fract(vrel*4.5+hh);
    float w=vrel*0.40*(0.68+0.32*tier);
    float aa=n/u_res.y;
    float tree=smoothstep(w+aa,w-aa,abs(fx))*step(0.0,vrel);
    float ground=smoothstep(base+0.002,base-0.002,q.y);
    vec3 tc;
    if(dk){
      tc=mix(hex(0x0B1C27.0),hex(0x04090F.0),fl);
      col=mix(col,tc,max(tree,ground));
    } else {
      tc=mix(hex(0xA9B8C8.0),hex(0x6F8499.0),fl);
      tc=mix(tc,hex(0x8FA3B8.0),(1.0-fl)*smoothstep(0.5,1.0,vrel)*0.6);
      float sn=ih1(idx,floor(vrel*4.5+hh)+fl*9.0+40.0)/1019.0;
      tc=mix(tc,hex(0xF2F6FA.0),smoothstep(0.22,0.04,tier)*step(0.70,sn)*0.45*smoothstep(0.0,0.2,abs(fx)*2.0/max(w,0.001)-0.3));
      col=mix(col,tc,tree*(1.0-ground));
      col=mix(col,mix(hex(0xFFFFFF.0),hex(0xEDF2F7.0),fl*0.6+(base-q.y)*3.0),ground);
    }
  }
  float sg=sendGlow(q,o,asp);
  if(dk){
    col+=hex(0x3DF5A6.0)*0.12*sg;
  } else {
    float fk=flakes(q,o,t,asp);
    col=mix(col,vec3(1.0),clamp(fk,0.0,1.0));
    col=mix(col,hex(0xBFD5EE.0),clamp(sg*0.55,0.0,1.0));
  }
  gl_FragColor=vec4(dither(clamp(col,0.0,1.0)),1.0);
}`
  });

  // ------------------------------------------------------------------ Silk
  // Heavy satin in pale jade by day and deep emerald by night. The send sends one crease
  // travelling out from the sent bubble.
  add({
    id: 'live-silk',
    name: 'Silk',
    blurb: 'Heavy jade satin drapes and slowly shifts in the light; each message sends one crease rippling out from your bubble.',
    bubble: { light: 'teal', dark: 'teal' },
    frag: `
float phs(vec2 p,float t){
  return p.x*30.0+p.y*9.0
    +3.2*sin(p.y*3.1+p.x*2.0+t)
    +1.6*sin(p.y*5.3-p.x*6.0-t*0.7+1.0)
    +0.8*sin(p.x*9.0+p.y*3.0+t*0.5);
}
void main(){
  float asp=u_res.x/u_res.y;
  vec2 q=gl_FragCoord.xy/u_res.y;
  float t=u_time*0.09;
  vec2 o=sendAt(asp);
  vec2 p=q+u_tilt*0.015;
  vec2 dv=q-o;
  float d=length(dv);
  float crease=exp(-sq((d-ringR(asp))/0.05))*sqrt(u_pulse);
  p+=dv/(d+0.001)*0.045*crease;
  float ph=phs(p,t)+3.0*crease;
  float s2=sin(phs(p*0.35+vec2(3.1,1.7),t*0.8+2.0)*0.5);
  float body=0.5+0.5*cos(ph-0.9);
  body=mix(body,0.5+0.5*s2,0.30);
  float sheen=pow(max(cos(ph-0.5),0.0),10.0)*(0.55+0.45*s2);
  float mid=smoothstep(0.05,0.40,abs(q.y-0.5));
  float k=mix(0.75,1.0,mid);
  float sg=sendGlow(q,o,asp);
  sheen+=crease*0.35*(0.5+0.5*cos(ph));
  vec3 col;
  if(u_dark>0.5){
    vec3 lo=hex(0x061A18.0), md=hex(0x12403A.0), hi=hex(0x2F7A6A.0);
    col=mix(lo,md,smoothstep(0.0,0.7,body));
    col=mix(col,hi,smoothstep(0.6,1.0,body)*k);
    col+=hex(0xE9D7AE.0)*sheen*0.35*k;
    col+=hex(0x2F7A6A.0)*0.35*sg;
  } else {
    vec3 lo=hex(0xC9D6CC.0), md=hex(0xE9F0EA.0), hi=hex(0xFFFFFF.0);
    col=mix(lo,md,smoothstep(0.0,0.6,body));
    col=mix(col,hi,smoothstep(0.55,1.0,body));
    col=mix(md,col,k);
    col+=vec3(1.0)*sheen*0.18;
    col=mix(col,hex(0xBFDCCB.0),clamp(sg*0.5,0.0,1.0));
  }
  gl_FragColor=vec4(dither(clamp(col,0.0,1.0)),1.0);
}`
  });

  // ---------------------------------------------------------------- Lagoon
  // Sunlight nets on a shallow lagoon floor. Light fades from clear water to warm sand;
  // the caustics thin out and soften across the bubble band. The send drops a ring.
  add({
    id: 'live-lagoon',
    name: 'Lagoon',
    blurb: 'Sunlight nets dance across a shallow lagoon floor; your message drops a ripple into the water.',
    bubble: { light: 'blue', dark: 'teal' },
    frag: `
float caust(vec2 w,float t,float e){
  w+=0.40*vec2(sin(w.y*1.3+t),cos(w.x*1.2-t*0.8));
  w+=0.22*vec2(sin(w.y*2.6-t*1.1+1.0),cos(w.x*2.3+t*0.9+2.0));
  float a=1.0-abs(sin(w.x*1.6+w.y*0.5));
  float b=1.0-abs(sin(w.y*1.7-w.x*0.9+1.3));
  float c=1.0-abs(sin((w.x-w.y)*1.2+0.7+t*0.3));
  float m=a*b*c;
  return pow(a,e)+pow(b,e)+pow(c,e)+m*m*1.5;
}
void main(){
  float asp=u_res.x/u_res.y;
  vec2 q=gl_FragCoord.xy/u_res.y;
  float t=u_time*0.32;
  vec2 o=sendAt(asp);
  vec2 dv=q-o;
  float d=length(dv);
  float rr=ringR(asp);
  float ring=exp(-sq((d-rr)/0.016))*sqrt(u_pulse);
  vec2 rip=dv/(d+0.001)*(0.012*ring+0.02*u_pulse*sin(d*40.0-u_time*7.0)*exp(-d*4.0));
  vec2 w=(q+rip+u_tilt*0.01)*7.0;
  float bb=bubbleBand(q.y);
  float e=mix(8.0,15.0,bb);
  float v=caust(w,t,e)*0.55+caust(w*0.6+vec2(4.0,2.0),t*0.7+3.0,e)*0.45;
  float sg=sendGlow(q,o,asp);
  vec3 col;
  if(u_dark>0.5){
    col=mix(hex(0x06202A.0),hex(0x0A2F3A.0),0.5+0.5*sin(q.x*3.0+q.y*2.0+t*0.2));
    col=mix(col,hex(0x041820.0),smoothstep(0.4,1.0,q.y));
    float k=1.0-0.4*bb;
    col+=hex(0x3FA9B5.0)*v*0.16*k;
    col+=hex(0xA8F2EC.0)*sq(v)*0.05*k;
    col+=hex(0x7FE0E6.0)*(0.30*ring+0.16*sg);
  } else {
    col=mix(hex(0xF2E9D6.0),hex(0xE8F5F2.0),smoothstep(0.18,0.62,q.y));
    col=mix(col,hex(0xD7EEEA.0),smoothstep(0.55,1.0,q.y)*0.55);
    float k=1.0-0.5*bb;
    col=mix(col,col*hex(0xE7EEE6.0),(1.0-clamp(v,0.0,1.0))*0.35*k);
    col=mix(col,vec3(1.0),clamp(v*0.35*k,0.0,1.0));
    col=mix(col,hex(0xA9DCDC.0),clamp(sg*0.55,0.0,1.0));
    col=mix(col,hex(0xFFFFFF.0),clamp(ring*0.85,0.0,1.0));
    col=mix(col,hex(0x8CCBCF.0),clamp(exp(-sq((d-ringR(asp)+0.02)/0.012))*sqrt(u_pulse)*0.35,0.0,1.0));
  }
  gl_FragColor=vec4(dither(clamp(col,0.0,1.0)),1.0);
}`
  });

  // ------------------------------------------------------------ Sea Sparks
  // Dark: bioluminescent plankton on a night tide; the send lights a flare of sparks
  // around the sent bubble. Light: sunlit shallow water with crisp four-point glints.
  add({
    id: 'live-sea-sparks',
    name: 'Sea Sparks',
    blurb: 'Sun glints on shallow water by day, glowing plankton on the night tide; your message lights up the water around it.',
    bubble: { light: 'blue', dark: 'teal' },
    frag: `
// Exact cell hash: every value is a whole number below 2^20, which fp32 holds exactly, so a fused
// multiply-add cannot change it and a spark lit in the baked still is lit on every GPU.
// (x+0.5)/1019 is at least 1/2038 from a whole number and the product below is within 1e-4 of it,
// so floor() is exact.
float ihm(float x){return x-1019.0*floor((x+0.5)*(1.0/1019.0));}
float icube(float n){return ihm(ihm(n*n)*n);}
// c: whole-number cell within +-4096, s: whole-number salt; returns a whole number in 0..1018.
float ih(vec2 c,float s){return icube(ihm(icube(ihm(c.x*19.0+c.y*56.0+s*211.0+101.0))+c.x*233.0+s*331.0+13.0));}
// For whole n in 1..1018, n*K stays at least 9e-4 from a whole number (n = 0 gives exactly 0), so fract() cannot wrap.
vec2 ih2(float n){return fract(n*vec2(0.3271483,0.9194175));}
// Swell-noise lattice value for c.y within +-50 (3..16 here); the cross term gives each row its own stride.
float iv(vec2 c){float x=ihm(c.x);return icube(ihm(x*13.0+c.y*56.0+x*c.y*19.0+1156.0));}
// vnoise on the exact hash, so no lattice value can jump between 0 and 1 where the GPU fuses.
float ivnoise(vec2 p){vec2 i=floor(p);vec2 f=fract(p);f=f*f*(3.0-2.0*f);
  return mix(mix(iv(i),iv(i+vec2(1.0,0.0)),f.x),mix(iv(i+vec2(0.0,1.0)),iv(i+vec2(1.0,1.0)),f.x),f.y)/1019.0;}
void main(){
  float asp=u_res.x/u_res.y;
  vec2 q=gl_FragCoord.xy/u_res.y;
  float t=u_time;
  bool dk=u_dark>0.5;
  vec2 o=sendAt(asp);
  float od=length(q-o);
  float sg=sendGlow(q,o,asp);
  vec3 col;
  if(dk){
    // Plankton gather in two slow diagonal streams; the middle stays quieter.
    float sv=q.y-0.9*(q.x-asp*0.5)+0.05*sin(q.x*7.0+t*0.05);
    float stream=exp(-sq((sv-0.80)/0.13))+exp(-sq((sv-0.18)/0.15))+0.25;
    float glow=0.0; float halo=0.0; float tint=0.0;
    for(int i=0;i<3;i++){
      float fi=float(i);
      float sc=15.0+fi*9.0;
      vec2 p=q+u_tilt*(0.004+fi*0.008);
      p+=0.035*vec2(sin(p.y*4.0+t*0.07+fi),sin(p.x*5.0-t*0.06+fi*2.0));
      p.y+=t*(0.004+fi*0.002);
      p*=sc;
      vec2 id=floor(p);
      vec2 f=fract(p)-0.5;
      float n=ih(id,fi);
      vec2 h=ih2(n);
      float on=step(509.5,n)*clamp(stream*0.9,0.2,1.0);
      vec2 jo=(h-0.5)*0.32+0.05*vec2(sin(t*0.4+h.x*6.28),cos(t*0.33+h.y*6.28));
      vec2 v=f-jo;
      float r2=dot(v,v);
      float tw=(0.55+0.45*sin(t*(0.5+h.y*0.9)+h.x*6.28))*on*(1.0-fi*0.25);
      glow+=exp(-r2*200.0)*tw;
      halo+=exp(-r2*22.0)*tw*smoothstep(0.5,0.32,max(abs(f.x),abs(f.y)));
      tint+=tw*h.y;
    }
    tint=clamp(tint/1.5,0.0,1.0);
    vec3 c=mix(hex(0x55F0E2.0),hex(0x5AA6FF.0),tint);
    col=mix(hex(0x04202C.0),hex(0x020A16.0),smoothstep(0.0,1.0,q.y));
    col+=hex(0x0C4554.0)*(stream-0.25)*0.30;
    col+=c*(glow*0.75+halo*0.16)*(1.0+2.0*sg);
    // Send flare: about 40 sparks around the bubble flare to pale cyan and fade.
    vec2 fp=(q-o)*30.0;
    vec2 fid=floor(fp); vec2 ff=fract(fp)-0.5;
    float fn=ih(fid,6.0);
    vec2 fh=ih2(fn);
    vec2 fv=ff-(fh-0.5)*0.6;
    float fon=step(458.5,fn);
    float fl=(exp(-dot(fv,fv)*70.0)+0.25*exp(-dot(fv,fv)*9.0))*fon*exp(-sq(od/(0.26*asp+0.05)))*u_pulse*(0.75+0.25*sin(u_time*9.0+fh.x*6.28));
    col+=hex(0x9FF4FF.0)*(fl*1.5+sg*0.18);
  } else {
    // Shallow water seen at a low angle: deeper aqua at the top, pale over the sand below.
    float bb=bubbleBand(q.y);
    col=mix(hex(0xF3F8F6.0),hex(0xDDF0F1.0),smoothstep(0.05,0.55,q.y));
    col=mix(col,hex(0xBFE1E5.0),smoothstep(0.55,1.0,q.y));
    // Swell crests: thin bright lines that compress towards the top (perspective).
    float zy=1.0/(1.25-q.y);
    float cr=sin(zy*26.0+2.0*sin(q.x*5.0*zy*0.5+t*0.20)+1.2*sin(q.x*13.0-t*0.25)-t*0.55);
    float brk=smoothstep(0.45,0.80,ivnoise(vec2(q.x*9.0*sqrt(zy),zy*4.0)+vec2(t*0.06,0.0)));
    float crest=pow(max(cr,0.0),12.0)*brk;
    float trough=pow(max(-cr,0.0),3.0);
    float topk=smoothstep(0.35,0.85,q.y);
    col=mix(col,hex(0xA9D6DC.0),trough*0.22*(0.25+0.75*topk)*(0.5+0.5*brk));
    col=mix(col,vec3(1.0),crest*0.75*(0.2+0.8*topk));
    // Crisp four-point glints that flash briefly, mostly up in the deeper water.
    float gl=0.0;
    for(int i=0;i<2;i++){
      float fi=float(i);
      float sc=16.0+fi*11.0;
      vec2 p=(q+u_tilt*0.006*(fi+1.0))*sc;
      vec2 id=floor(p); vec2 f=fract(p)-0.5;
      float n=ih(id,fi+3.0);
      float h=n/1019.0;
      vec2 jo=(ih2(n)-0.5)*0.6;
      vec2 v=(f-jo)*u_res.y/sc;
      float boost=exp(-sq(od/(0.25*asp+0.04)))*u_pulse;
      float on=step(mix(0.90,0.55,topk)-0.45*boost,h)*(1.0-0.6*bb);
      float tw=pow(max(sin(t*(0.9+h*1.4)+h*40.0),0.0),4.0)*(0.3+0.7*brk);
      tw=max(tw,boost*(0.6+0.4*sin(u_time*7.0+h*30.0)));
      float sz=mix(1.2,2.8,fract(h*7.3))*(1.0-fi*0.25)*(1.0+0.6*boost);
      vec2 av=abs(v);
      float star=exp(-dot(v,v)/(sz*sz))+0.7*exp(-av.x/(sz*2.6)-sq(av.y/(sz*0.30)))+0.7*exp(-av.y/(sz*2.6)-sq(av.x/(sz*0.30)));
      gl+=star*tw*on;
    }
    col=mix(col,hex(0x9FD3DC.0),clamp(sg*0.55,0.0,1.0));
    col=mix(col,vec3(1.0),clamp(gl,0.0,1.0));
  }
  gl_FragColor=vec4(dither(clamp(col,0.0,1.0)),1.0);
}`
  });

  // ------------------------------------------------------------ Tropic Dusk
  // Horizon at 38% from the top, sun just above it, a rounded headland on the left.
  // The sea fills the bubble band with fine glitter. The send warms the sun and the water.
  add({
    id: 'live-tropic-dusk',
    name: 'Tropic Dusk',
    blurb: 'Golden hour on a Brazilian beach and the violet dusk after; cloud bands drift, the sea glitters, and the sun warms when you send.',
    bubble: { light: 'pink', dark: 'orange' },
    frag: `
float cn(vec2 p){ return 0.5+0.25*sin(p.x*1.7+sin(p.y*2.3))+0.25*sin(p.x*3.9-p.y*1.3+1.7*sin(p.x*0.7)); }
void main(){
  float asp=u_res.x/u_res.y;
  vec2 q=gl_FragCoord.xy/u_res.y;
  float t=u_time;
  bool dk=u_dark>0.5;
  vec2 o=sendAt(asp);
  float sg=sendGlow(q,o,asp);
  float hz=0.62;
  vec2 sun=vec2(asp*0.66+u_tilt.x*0.01,hz+0.055);
  float s=clamp((q.y-hz)/(1.0-hz),0.0,1.0);
  float sw=1.0+u_pulse;
  vec3 col,sunc,glc,cl,cd;
  if(dk){
    col=mix(hex(0xE8866A.0),hex(0x9C4E68.0),smoothstep(0.0,0.22,s));
    col=mix(col,hex(0x4A2C55.0),smoothstep(0.15,0.55,s));
    col=mix(col,hex(0x2A1D3F.0),smoothstep(0.5,1.0,s));
    sunc=hex(0xFFD08A.0); glc=hex(0xFFB070.0); cl=hex(0xF2A07E.0); cd=hex(0x3A2448.0);
  } else {
    col=mix(hex(0xF6C3B1.0),hex(0xF9D6C3.0),smoothstep(0.0,0.3,s));
    col=mix(col,hex(0xFDEBD9.0),smoothstep(0.25,1.0,s));
    sunc=hex(0xFFF4E0.0); glc=hex(0xFFE7C4.0); cl=hex(0xFFF5EA.0); cd=hex(0xEDB9AC.0);
  }
  vec2 dv=q-sun; dv.y*=1.3;
  float sd=length(dv);
  float gr=mix(9.0,6.5,u_dark)/(1.0+0.3*u_pulse);
  col+=glc*exp(-sd*gr)*mix(0.45,0.42,u_dark)*(1.0+0.6*u_pulse);
  if(!dk) col=mix(col,hex(0xFFF1DC.0),exp(-sd*16.0)*0.6);
  // Three cloud bands in the upper sky.
  for(int i=0;i<3;i++){
    float fi=float(i);
    float y0=hz+0.12+fi*0.085+0.02*sin(fi*2.7);
    float x=q.x*(2.4+fi*0.6)-t*0.006*(1.0+fi*0.35)+fi*3.7;
    float yy=q.y-y0-0.018*sin(x*1.3+fi);
    float th=0.016+0.010*fi;
    float band=exp(-yy*yy/(th*th));
    float br=smoothstep(0.36,0.72,cn(vec2(x,fi*1.7+yy*8.0)));
    float lt=smoothstep(th,-th,yy)*exp(-abs(q.x-sun.x)*1.4);
    vec3 cc=mix(cd,cl,clamp(lt*1.5+0.2,0.0,1.0));
    col=mix(col,cc,band*br*mix(0.70,0.80,u_dark)*(1.0-fi*0.15));
  }
  float disc=smoothstep(0.036,0.031,length(q-sun));
  col=mix(col,min(sunc*(1.0+0.15*u_pulse),vec3(1.0)),disc);
  float px=1.0/u_res.y;
  // Sea below the horizon.
  if(q.y<hz){
    float dd=(hz-q.y)/hz;
    vec3 sea;
    if(dk){
      sea=mix(hex(0x6A3A5A.0),hex(0x2B2140.0),smoothstep(0.0,0.35,dd));
      sea=mix(sea,hex(0x221A35.0),smoothstep(0.4,1.0,dd));
    } else {
      sea=mix(hex(0xE8C9C0.0),hex(0x9CCAD6.0),smoothstep(0.0,0.30,dd));
      sea=mix(sea,hex(0xAFD5DC.0),smoothstep(0.5,1.0,dd));
    }
    float bb=bubbleBand(q.y);
    // Long low swell lines, very faint.
    float freq=140.0/(dd+0.25);
    float wl=0.5+0.5*sin(q.y*freq+2.0*sin(q.x*6.0/(dd+0.3)+t*0.3)+t*0.5);
    sea=mix(sea,sea*mix(0.96,0.90,u_dark),wl*(1.0-0.5*bb));
    float pw=0.020+dd*0.16;
    float path=exp(-sq((q.x-sun.x)/pw));
    float gl=pow(0.5+0.5*sin(q.y*freq*1.8+3.0*sin(q.x*40.0/(dd+0.3)+t*0.6)+t*0.8),8.0)
            *smoothstep(0.55,0.9,0.5+0.5*sin(q.x*90.0/(dd+0.2)-t*0.4+q.y*30.0));
    float pathA=path*(1.0-dd*0.55)*(1.0-0.45*bb);
    sea+=glc*exp(-sq((q.x-sun.x)/(0.10+dd*0.3)))*(1.0-dd)*mix(0.10,0.12,u_dark)*sw;
    sea=mix(sea,hex(0xFFE3C2.0),clamp(gl*(0.25*pathA+0.06*(1.0-bb))+gl*sg*0.6,0.0,1.0));
    sea=mix(sea,mix(hex(0xFFE9CF.0),hex(0xFFB48A.0),u_dark),clamp(pathA*0.18*sw,0.0,1.0));
    if(!dk){
      // Wet sand and a soft foam line at the very bottom.
      float fy=0.055+0.008*sin(q.x*9.0+t*0.15)+0.004*sin(q.x*23.0-t*0.2);
      float foam=exp(-sq((q.y-fy)/0.004));
      sea=mix(sea,hex(0xF0DCC8.0),smoothstep(fy+0.002,fy-0.004,q.y));
      sea=mix(sea,hex(0xFFFFFF.0),foam*0.7);
    }
    col=sea;
  }
  col+=mix(hex(0xFFD2A0.0)*0.16,hex(0xFF9E6E.0)*0.22,u_dark)*sg;
  // Rounded headland on the left (28% to 40% from the top), and a low far one on the right.
  float hx=q.x+u_tilt.x*0.008;
  float hill=hz+0.095*exp(-sq((hx-0.00)/0.11))+0.050*exp(-sq((hx-0.14)/0.06))+0.010*exp(-sq((hx-0.22)/0.03));
  float hm=smoothstep(hill+px,hill-px,q.y)*step(hz-0.0015,q.y);
  vec3 hc=mix(hex(0xD3A09E.0),hex(0x2A1735.0),u_dark);
  hc=mix(hc,mix(hex(0xE6B8AE.0),hex(0x4A2840.0),u_dark),smoothstep(hz+0.06,hz,q.y)*0.5);
  col=mix(col,hc,hm);
  float far=hz+0.012*exp(-sq((hx-asp*0.98)/0.07));
  col=mix(col,mix(hex(0xE7BDB2.0),hex(0x5A3352.0),u_dark),smoothstep(far+px,far-px,q.y)*step(hz-0.0015,q.y)*(1.0-hm));
  // Headland reflection.
  if(q.y<hz){
    float ry=2.0*hz-q.y;
    float rh=smoothstep(hill+px,hill-px,ry)*smoothstep(hz-0.05,hz,q.y);
    col=mix(col,hc,rh*0.35);
  }
  gl_FragColor=vec4(dither(clamp(col,0.0,1.0)),1.0);
}`
  });

  // ------------------------------------------------------------------- Ipê
  // A flowering ipê canopy across the top of the screen with blossoms drifting down.
  // Light: pink ipê against a pale spring sky. Dark: yellow ipê at blue hour.
  // The send blows a gust that pushes the falling blossoms away from the sent bubble.
  add({
    id: 'live-ipe',
    name: 'Ipê',
    blurb: 'Blossoms drift down from a flowering ipê, pink by day and gold at blue hour; your message sends a gust through them.',
    bubble: { light: 'pink', dark: 'yellow' },
    frag: `
void main(){
  float asp=u_res.x/u_res.y;
  vec2 q=gl_FragCoord.xy/u_res.y;
  float t=u_time;
  bool dk=u_dark>0.5;
  vec2 o=sendAt(asp);
  float sg=sendGlow(q,o,asp);
  vec3 sk0,sk1,bk,bl0,bl1,bl2,pt0,pt1;
  if(dk){
    sk0=hex(0x141A33.0);sk1=hex(0x232546.0);bk=hex(0x0B0A14.0);
    bl0=hex(0x7A4F18.0);bl1=hex(0xD99E32.0);bl2=hex(0xFFD877.0);pt0=hex(0xC08A2C.0);pt1=hex(0xFFDB80.0);
  } else {
    sk0=hex(0xF8F2EE.0);sk1=hex(0xE2ECF5.0);bk=hex(0x6A5560.0);
    bl0=hex(0xC9668F.0);bl1=hex(0xE993B6.0);bl2=hex(0xFBD3E3.0);pt0=hex(0xE48AB0.0);pt1=hex(0xF9CFE0.0);
  }
  vec3 col=mix(sk0,sk1,smoothstep(0.1,1.0,q.y));
  if(dk) col=mix(col,hex(0x3A2F55.0),exp(-sq((q.y-0.62)/0.22))*0.30);
  vec2 cp=q+u_tilt*0.010;
  // Canopy seen out of focus: soft blossom masses across the top, ragged towards 30%.
  float cn=vnoise(cp*vec2(3.2,4.0)+vec2(1.3,t*0.004))*0.6+vnoise(cp*vec2(7.0,8.0)+vec2(7.1,2.2+t*0.006))*0.4;
  float edge=0.76+0.05*sin(cp.x*4.0+0.7);
  float mass=smoothstep(-0.04,0.10,cp.y-edge+(cn-0.5)*0.30);
  vec3 cc=mix(bl0,bl1,smoothstep(0.3,0.75,cn));
  cc=mix(cc,bl2,smoothstep(0.62,0.85,vnoise(cp*vec2(6.0,7.0)+vec2(3.0,9.0)))*0.55);
  col=mix(col,cc,mass*0.92);
  // Defocused blossoms: bokeh discs with a brighter rim, mostly along the canopy edge.
  for(int i=0;i<7;i++){
    float fi=float(i);
    vec2 h=hash22(vec2(fi*5.3,1.7));
    vec2 c=vec2(asp*(0.04+0.92*h.x),0.80+0.16*h.y+0.010*sin(t*0.08+fi*1.7));
    float r=0.030+0.028*fract(h.x*7.1+h.y*3.3);
    float dd=length(cp+u_tilt*0.012*(1.0+fi*0.1)-c)/r;
    float bo=smoothstep(1.0,0.86,dd)*(0.75+0.25*smoothstep(0.55,0.95,dd));
    col=mix(col,mix(bl1,bl2,0.35+0.4*h.y),bo*0.55);
  }
  // Falling petals in three depth layers; the send gusts them away from the bubble.
  float bb=bubbleBand(q.y);
  for(int i=0;i<3;i++){
    float fi=float(i);
    float sc=7.0+fi*4.0;
    vec2 p=q+u_tilt*(0.004+0.008*(2.0-fi));
    vec2 ov=p-o; float od=length(ov);
    p-=ov/(od+0.001)*0.14*u_pulse*exp(-od*5.0);
    p.y+=t*(0.018-fi*0.004);
    p.x+=0.035*sin(p.y*2.5+t*0.15+fi*2.0);
    vec2 gp=p*sc;
    vec2 id=floor(gp); vec2 f=fract(gp)-0.5;
    float h=hash12(id+fi*19.0);
    vec2 jo=(hash22(id+fi*3.0)-0.5)*0.5;
    float on=step(0.58,h);
    float ang=t*(0.25+0.4*h)*sign(h-0.79)+h*20.0;
    vec2 v=f-jo;
    v=mat2(cos(ang),sin(ang),-sin(ang),cos(ang))*v;
    float sz=mix(0.085,0.12,fract(h*7.0))*(1.0-fi*0.10);
    vec2 e=v/vec2(sz,sz*0.62);
    e.x+=0.18*e.y*e.y;
    float rr=length(e);
    float soft=mix(0.10,0.30,1.0-fi*0.5);
    float petal=smoothstep(1.0,1.0-soft,rr)*on;
    vec3 pc=mix(pt1,pt0,smoothstep(-0.8,0.9,e.x));
    float al=mix(0.92,0.60,fi*0.5)*(1.0-mix(0.30,0.45,u_dark)*bb);
    col=mix(col,pc,petal*al);
  }
  if(dk) col+=hex(0xFFC960.0)*0.16*sg;
  else   col=mix(col,hex(0xF4BCD2.0),clamp(sg*0.55,0.0,1.0));
  gl_FragColor=vec4(dither(clamp(col,0.0,1.0)),1.0);
}`
  });

  // ------------------------------------------------------------- Rain Glass
  // Looking out through a rainy window: a soft, defocused scene behind glass beaded with
  // drops that refract it upside down, plus a few drops that slide down leaving clear trails.
  // Light: a green garden on a grey morning. Dark: city lights at night. The send wipes a
  // clear streak up the glass from the sent bubble.
  add({
    id: 'live-rain-glass',
    name: 'Rain on Glass',
    blurb: 'Rain beads on the window over a soft garden by day and city lights at night; your message wipes a clear streak.',
    bubble: { light: 'green', dark: 'blue' },
    frag: `
vec3 scene(vec2 p,float asp,bool dk){
  vec3 c;
  if(dk) c=mix(hex(0x101826.0),hex(0x0B111C.0),smoothstep(0.3,1.0,p.y));
  else   c=mix(hex(0xD3E0D8.0),hex(0xC9D8E2.0),smoothstep(0.3,1.0,p.y));
  for(int i=0;i<7;i++){
    float fi=float(i);
    vec2 h=hash22(vec2(fi*3.1,7.7));
    vec2 ct=vec2(asp*(0.06+0.88*h.x),0.60+0.30*h.y);
    if(i>4) ct.y=0.22+0.18*h.y;
    float r=mix(0.05,0.11,fract(h.x*7.3+h.y));
    float dd=length(p-ct)/r;
    float disc=smoothstep(1.0,0.70,dd)*(0.78+0.22*smoothstep(0.5,0.92,dd));
    vec3 lc;
    if(dk) lc=fi<2.0?hex(0xE2A55C.0):(fi<4.0?hex(0xC9607A.0):hex(0x3F9EB0.0));
    else   lc=fi<3.0?hex(0x7FA88B.0):(fi<5.0?hex(0x9DBF8A.0):hex(0xA8C4B4.0));
    float a=(i>4?0.45:0.85)*disc;
    if(dk) c+=lc*a*0.50; else c=mix(c,lc,a*0.75);
  }
  return c;
}
void main(){
  float asp=u_res.x/u_res.y;
  vec2 q=gl_FragCoord.xy/u_res.y;
  float t=u_time;
  bool dk=u_dark>0.5;
  vec2 o=sendAt(asp);
  float sg=sendGlow(q,o,asp);
  float bb=bubbleBand(q.y);
  // Wipe: a clear vertical streak above the sent bubble that refills from the top.
  float wtop=o.y+0.75*sqrt(u_pulse);
  float wx=abs(q.x-o.x+0.03*(q.y-o.y));
  float wipe=step(0.02,u_pulse)*smoothstep(0.085,0.045,wx)*smoothstep(o.y-0.02,o.y+0.01,q.y)*smoothstep(wtop,wtop-0.03,q.y);
  vec2 off=vec2(0.0);
  float shade=0.0; float hi=0.0;
  // Beaded drops: two grids.
  for(int i=0;i<2;i++){
    float fi=float(i);
    float sc=mix(16.0,34.0,fi);
    vec2 gp=q*sc+vec2(fi*0.37,fi*0.71);
    vec2 id=floor(gp); vec2 f=fract(gp)-0.5;
    float h=hash12(id+fi*41.0);
    vec2 jo=(hash22(id+fi*9.0)-0.5)*0.55;
    float r=mix(0.10,0.30,fract(h*13.1))*(1.0-0.25*fi);
    float on=step(mix(0.35,0.20,fi)+0.25*bb,h)*(1.0-wipe);
    vec2 v=(f-jo)/r;
    float l=length(v*vec2(1.0,0.92));
    float in_=smoothstep(1.0,0.86,l)*on;
    off+=-v*in_*(r/sc)*2.6;
    shade+=in_*smoothstep(0.55,1.0,l)*smoothstep(-0.2,0.6,-v.y);
    hi+=in_*exp(-dot(v-vec2(-0.35,0.40),v-vec2(-0.35,0.40))*14.0);
  }
  // Sliding drops: one per column, each leaving a clear trail with a few beads.
  float cw=asp/7.0;
  float ci=floor(q.x/cw);
  float ch=hash12(vec2(ci,3.3));
  float per=11.0+9.0*ch;
  float yd=1.15-fract(t/per+ch)*1.4;
  float xd=(ci+0.5)*cw+cw*0.22*sin(yd*9.0+ch*6.0);
  vec2 dv=(q-vec2(xd,yd))/vec2(0.020,0.026);
  float run=smoothstep(1.0,0.85,length(dv))*step(0.35,ch)*(1.0-wipe);
  off+=-dv*run*0.025;
  shade+=run*smoothstep(0.5,1.0,length(dv))*smoothstep(-0.2,0.6,-dv.y);
  hi+=run*exp(-dot(dv-vec2(-0.3,0.4),dv-vec2(-0.3,0.4))*12.0);
  float trail=step(0.35,ch)*smoothstep(0.012,0.006,abs(q.x-xd))*step(yd,q.y)*smoothstep(yd+0.35,yd+0.02,q.y);
  vec3 col=scene(q+off,asp,dk);
  float wedge=exp(-sq((wx-0.072)/0.005))*smoothstep(o.y-0.02,o.y+0.01,q.y)*smoothstep(wtop,wtop-0.03,q.y)*step(0.02,u_pulse);
  // Fine mist on the glass, cleared by trails and by the wipe.
  float mist=(1.0-max(trail,wipe))*(0.65+0.35*vnoise(q*vec2(40.0,28.0)));
  if(dk){
    col=mix(col,hex(0x283246.0),mist*0.45*(1.0-0.2*bb));
    col=mix(col,hex(0x05080E.0),clamp(shade,0.0,1.0)*0.25);
    col*=1.0+0.35*clamp(hi+shade,0.0,1.0);
    col+=hex(0xDDE8FF.0)*clamp(hi,0.0,1.0)*0.40;
    col+=hex(0x6FA0D8.0)*0.16*sg+hex(0xBFD3F0.0)*wedge*0.10;
  } else {
    col=mix(col,hex(0xF3F6F5.0),mist*0.62*(1.0-0.15*bb));
    col=mix(col,hex(0x8FA19A.0),clamp(shade,0.0,1.0)*0.40);
    col=mix(col,vec3(1.0),clamp(hi,0.0,1.0)*0.85);
    
    col=mix(col,hex(0x7E9389.0),wedge*0.35);
    col=mix(col,hex(0xC4DDC8.0),clamp(sg*0.40,0.0,1.0));
  }
  gl_FragColor=vec4(dither(clamp(col,0.0,1.0)),1.0);
}`
  });

  // --------------------------------------------------------------- Pantanal
  // Wetland with a treeline of fan palms and broad trees at 30% to 42% from the top,
  // mirrored in still water that fills the bubble band.
  // Dark: fireflies drift and blink over the water. Light: dawn mist with gold motes.
  // The send makes the fireflies around the sent bubble flash together.
  add({
    id: 'live-pantanal',
    name: 'Pantanal',
    blurb: 'Fan palms over still water in the Pantanal: dawn mist with drifting light by day, fireflies at night; they flash together when you send.',
    bubble: { light: 'green', dark: 'green' },
    frag: `
float trees(vec2 p,float hz,float asp){
  float m=step(p.y,hz+0.004+0.004*sin(p.x*30.0));
  // Broad rounded crowns.
  for(int i=0;i<3;i++){
    float fi=float(i);
    vec2 c=vec2(asp*(0.18+0.36*fi)+0.03*sin(fi*5.0),hz+0.035+0.012*fi);
    float r=0.045+0.015*fract(fi*0.618);
    vec2 v=(p-c)/vec2(1.7,1.0);
    float bump=0.014*(vnoise(p*150.0+fi*7.0)-0.5)+0.012*(vnoise(p*45.0+fi*3.0)-0.5);
    float lob=0.25*r*sin(v.x*90.0+fi)*smoothstep(-0.3*r,r,v.y);
    m=max(m,smoothstep(r+0.0015,r-0.0015,length(v)+bump+lob*0.15)*step(hz,p.y));
  }
  // Fan palms: thin trunk plus a spiky round crown.
  for(int i=0;i<5;i++){
    float fi=float(i);
    float x0=asp*fract(0.09+fi*0.217);
    float ht=0.085+0.035*fract(fi*0.71);
    float lean=0.06*(fract(fi*0.37)-0.5);
    float yy=(p.y-hz)/ht;
    float tx=x0+lean*yy*ht;
    float trunk=smoothstep(0.0022,0.0012,abs(p.x-tx))*step(0.0,yy)*step(yy,1.0);
    vec2 v=p-vec2(x0+lean*ht,hz+ht);
    float a=atan(v.y,v.x);
    float rc=0.024*(0.45+0.55*sqrt(abs(sin(a*7.0+fi))))*(0.75+0.25*smoothstep(-1.2,0.6,sin(a)));
    float crown=smoothstep(rc+0.002,rc-0.001,length(v*vec2(1.0,1.25)));
    m=max(m,max(trunk,crown));
  }
  return m;
}
void main(){
  float asp=u_res.x/u_res.y;
  vec2 q=gl_FragCoord.xy/u_res.y;
  float t=u_time;
  bool dk=u_dark>0.5;
  vec2 o=sendAt(asp);
  float sg=sendGlow(q,o,asp);
  float hz=0.58;
  float px=1.0/u_res.y;
  bool water=q.y<hz;
  vec2 p=q+vec2(u_tilt.x*0.008,0.0);
  if(water){
    p.y=2.0*hz-q.y;
    p.x+=0.0025*sin(q.y*180.0+t*0.8)*smoothstep(0.0,0.1,hz-q.y);
  }
  float s=clamp((p.y-hz)/(1.0-hz),0.0,1.0);
  vec3 sky,tc;
  if(dk){
    sky=mix(hex(0x24364A.0),hex(0x101C30.0),smoothstep(0.0,0.4,s));
    sky=mix(sky,hex(0x080E1C.0),smoothstep(0.4,1.0,s));
    tc=hex(0x050A0F.0);
  } else {
    sky=mix(hex(0xF8DDBC.0),hex(0xF3EBDC.0),smoothstep(0.0,0.45,s));
    sky=mix(sky,hex(0xE3ECEE.0),smoothstep(0.45,1.0,s));
    vec2 sv=p-vec2(asp*0.30,hz+0.075);
    sky=mix(sky,hex(0xFFF0D6.0),exp(-dot(sv,sv)*90.0)*0.8);
    sky=mix(sky,hex(0xFFF8EC.0),smoothstep(0.030,0.026,length(sv)));
    tc=hex(0x7F9386.0);
  }
  float tm=trees(p,hz,asp);
  vec3 col=mix(sky,tc,tm);
  if(!dk){
    // Back treeline, paler in the mist.
    float bt=hz+0.012+0.006*sin(p.x*17.0)+0.004*sin(p.x*41.0+1.0);
    col=mix(col,hex(0xBCC8B8.0),smoothstep(bt+px,bt-px,p.y)*(1.0-tm)*step(hz,p.y));
    // Mist drifting along the treeline.
    float mi=exp(-sq((p.y-hz-0.03)/0.035))*(0.55+0.45*sin(p.x*6.0-t*0.03+sin(p.x*2.0+t*0.02)*2.0));
    col=mix(col,hex(0xF7F1E6.0),clamp(mi,0.0,1.0)*0.65);
  }
  if(water){
    float dd=(hz-q.y)/hz;
    col=mix(col,dk?hex(0x08111C.0):hex(0xE8EDE8.0),0.50+0.45*smoothstep(0.0,0.30,dd));
    float ln=0.5+0.5*sin(q.y*260.0+3.0*sin(q.x*9.0+t*0.2));
    col=mix(col,col*(dk?1.25:0.97),ln*0.25*(1.0-0.5*bubbleBand(q.y)));
  }
  if(!dk){
    // Water hyacinth pads near the bottom edge.
    float pads=smoothstep(0.62,0.66,vnoise(q*vec2(9.0,24.0)+vec2(t*0.005,0.0)))*smoothstep(0.10,0.02,q.y);
    col=mix(col,hex(0x9DB89B.0),pads*0.5);
  }
  // Fireflies (dark) or gold motes (light). Far ones hug the treeline and mirror in the water.
  float ff=0.0;
  for(int i=0;i<2;i++){
    float fi=float(i);
    float sc=mix(26.0,11.0,fi);
    vec2 fp=(i==0)?p:q;
    fp+=vec2(0.012*sin(fp.y*7.0+t*0.11+fi),0.008*sin(fp.x*6.0-t*0.09));
    fp.x+=t*0.002*(1.0+fi);
    vec2 gp=fp*sc;
    vec2 id=floor(gp); vec2 f=fract(gp)-0.5;
    float h=hash12(id+fi*23.0);
    vec2 jo=(hash22(id+fi*2.0)-0.5)*0.6+0.12*vec2(sin(t*0.25+h*30.0),cos(t*0.21+h*20.0));
    vec2 v=(f-jo)*u_res.y/sc;
    float zone=(i==0)?exp(-sq((fp.y-hz-0.06)/0.07)):(1.0-0.75*bubbleBand(q.y))*step(0.8,fp.y*0.0+1.0);
    float on=step(i==0?0.35:0.70,h)*zone;
    float near=exp(-sq(length(q-o)/(0.30*asp)))*u_pulse;
    float blink=smoothstep(0.1,1.0,sin(t*(0.35+0.4*h)+h*40.0));
    blink=max(blink*(1.0-near),near);
    float rr=(i==0)?1.7:2.8;
    ff+=(exp(-dot(v,v)/(rr*rr))+0.30*exp(-dot(v,v)/(rr*rr*14.0)))*on*blink*((water&&i==0)?0.45:1.0);
  }
  vec2 fp2=(q-o)*24.0;
  vec2 fid=floor(fp2); vec2 fv=fract(fp2)-0.5-(hash22(fid+5.1)-0.5)*0.6;
  float fla=(exp(-dot(fv,fv)*90.0)+0.2*exp(-dot(fv,fv)*10.0))*step(0.55,hash12(fid*1.9+2.0))
           *exp(-sq(length(q-o)/(0.24*asp+0.05)))*u_pulse;
  if(dk){
    col+=hex(0xD9F06A.0)*(ff*0.9+fla*1.3);
    col+=hex(0xC8E86A.0)*0.16*sg;
  } else {
    col=mix(col,hex(0xF6C565.0),clamp(ff*0.55+fla*0.8,0.0,1.0));
    col=mix(col,hex(0xCFE0C4.0),clamp(sg*0.45,0.0,1.0));
  }
  gl_FragColor=vec4(dither(clamp(col,0.0,1.0)),1.0);
}`
  });

})();

