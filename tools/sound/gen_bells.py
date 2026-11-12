import numpy as np, wave, subprocess
SR=44100
rng=np.random.default_rng(7)
def t(d): return np.arange(int(SR*d))/SR
def wood(f, dur=0.18, amp=1.0, bright=1.0, thud=0.5):
    x=t(dur); s=np.zeros_like(x)
    for r,a,tau in [(1,1,.035),(2.32,.55*bright,.018),(3.9,.3*bright,.010),(5.6,.15*bright,.006)]:
        s+=a*np.sin(2*np.pi*f*r*x+rng.uniform(0,6))*np.exp(-x/tau)
    s+=thud*np.sin(2*np.pi*f*0.3*x)*np.exp(-x/.03)
    n=rng.standard_normal(len(x))*np.exp(-x/.0015)*0.6*bright
    n=np.convolve(n,[1,-1],'same')  # bật tiếng gõ
    return amp*(s+n)
def bell(f, dur=0.9, amp=1.0, tau=.35):
    x=t(dur); s=np.zeros_like(x)
    for r,a,k in [(1,1,1),(2.0,.35,.6),(2.76,.25,.45),(5.4,.12,.25)]:
        s+=a*np.sin(2*np.pi*f*r*x)*np.exp(-x/(tau*k))
    att=np.minimum(1,x/0.004)
    return amp*s*att
def mix(parts, dur):
    out=np.zeros(int(SR*dur))
    for start,sig in parts:
        i=int(SR*start); out[i:i+len(sig)]+=sig[:len(out)-i]
    return out
def save(name, s, peak=0.7):
    s=s/np.max(np.abs(s))*peak
    fade=np.minimum(1,(len(s)-np.arange(len(s)))/(SR*0.01)); s*=fade
    d=(s*32767).astype(np.int16)
    with wave.open(f'{name}.wav','wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(d.tobytes())
    subprocess.run(['ffmpeg','-y','-loglevel','error','-i',f'{name}.wav','-c:a','libvorbis','-q:a','4',f'{name}.ogg'],check=True)
N=lambda n: 440*2**((n-69)/12)
save('move', mix([(0,wood(720,0.2,1,1,.6))],0.22), 0.6)
save('capture', mix([(0,wood(560,0.25,1,1.2,.9)),(0.028,wood(980,0.15,.55,1.3,.2))],0.3), 0.75)
save('check', mix([(0,wood(1250,0.12,1,1.4,.1)),(0.11,wood(1250,0.12,1,1.4,.1)),(0.2,bell(N(81),0.6,.35,.25))],0.8), 0.7)
save('win', mix([(0,bell(N(74),.9,.8)),(0.13,bell(N(76),.9,.8)),(0.26,bell(N(79),.9,.85)),(0.39,bell(N(86),1.4,1,.5))],1.8), 0.7)
save('lose', mix([(0,bell(N(69),.9,.7,.3)),(0.2,bell(N(67),.9,.65,.3)),(0.4,bell(N(62),1.3,.7,.45))],1.7), 0.55)
save('draw', mix([(0,bell(N(67),.9,.8,.35)),(0.22,bell(N(74),1.2,.8,.45))],1.4), 0.55)
save('correct', mix([(0,bell(N(76),.6,.8,.22)),(0.1,bell(N(81),.9,1,.3))],1.0), 0.6)
save('wrong', mix([(0,wood(240,0.25,1,.4,1.0)),(0.09,wood(200,0.25,.8,.3,1.0))],0.35), 0.55)
