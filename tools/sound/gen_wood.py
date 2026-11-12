# Tiếng quân cờ gỗ (bản 3): bộ cộng hưởng nhiều mode, tham số (tần số, mức, thời gian tắt) đo từ một video tham chiếu.
# Không dùng mẫu âm thanh gốc – chỉ các số đo phổ. Chạy: python3 gen_wood.py (cần numpy, scipy, ffmpeg) → move1-3, capture, check (.ogg)
PARAMS = [np.float64(-7.36), np.float64(0.0), np.float64(0.0), np.float64(0.0), np.float64(-2.68), np.float64(8.13), np.float64(8.62), np.float64(16.93)]  # gl, chỉnh mức theo dải 60/125/250/500/1k/2k/4k Hz
import numpy as np
from scipy.signal import lfilter, butter, stft
SR=44100
CONTACT=1.5
LOW=[(70,-1.0,520),(97,-3.0,560),(118,0,219),(135,-8.4,304),(161,-1.1,293),(194,-3.7,452),(215,-4.9,174),(231,-5.2,182),(253,-2.4,185),(307,-5.4,253),(328,-4.7,211),(366,-4.4,357),(393,-5.1,283),(415,-5,254)]
MID=[(431,0,185),(538,-2.6,192),(754,-5.9,713),(1034,-15.9,155),(1184,-12.8,322),(1335,-12.7,140),(1658,-18.8,177),(1852,-17.6,289),(2024,-22.6,185),(2153,-24.8,205),(2347,-21.2,205),(2541,-21.4,196),(2799,-25.9,231),(3101,-29.3,168),(3359,-29.6,137),(3531,-30.2,175),(3833,-35.5,110),(4048,-36.6,107)]
BANDS=[60,125,250,500,1000,2000,4000]
def bands(x):
    f,t,Z=stft(x,SR,nperseg=2048,noverlap=2048-256); M=np.abs(Z); m0=M[:,1:5]
    ref0=m0.mean(1).max()
    return np.array([20*np.log10(M[(f>=lo)&(f<2*lo),1:5].mean()+1e-12) for lo in BANDS]), np.array([20*np.log10(M[(f>=lo)&(f<2*lo),np.argmin(abs(t-0.2))].mean()+1e-12) for lo in BANDS])
def env(x):
    e=np.convolve(x**2,np.ones(220)/220,'same'); return np.array([10*np.log10(e[int(ms*44.1)]/e.max()+1e-12) for ms in [2,5,10,20,40,80,150,300,600,900]])
def res(x,f,tau):
    r=np.exp(-1/(tau/1000*SR)); w=2*np.pi*f/SR
    return lfilter([1-r],[1,-2*r*np.cos(w),r*r],x)
LOW[0]=(70,0.0,450); LOW[1]=(97,-1.0,520); LOW[2]=(118,1.5,260)
TS=0.85
gl=PARAMS[0]; adj=np.array(PARAMS[1:])
def synth(gl, gm, tscale=1.0, seed=1, n=1.2, hard=1.0, jitter=0.02, click=0.0):
    rng=np.random.default_rng(seed); N=int(SR*n)
    e=np.zeros(N); C=int(SR*CONTACT/1000)
    p=np.sin(np.pi*np.arange(C+1)/C)**2; e[:C]=np.diff(p)*C/4   # va chạm mềm vài ms (lực → vận tốc: không có DC)
    L=int(SR*.001); e[:L]+=0.15*rng.standard_normal(L)
    out=np.zeros(N)
    for (f,db,tau) in LOW:
        y=res(e,f*(1+rng.uniform(-jitter,jitter)),tau*tscale); out+=10**((db+gl)/20)*y/np.max(abs(y))
    for (f,db,tau) in MID:
        y=res(e,f*(1+rng.uniform(-jitter,jitter)),tau*tscale); out+=10**((db+gm(f))/20)*y/np.max(abs(y))
    return out*hard
def band_gain_fn(adj):
    def g(f):
        k=min(range(len(BANDS)),key=lambda i: abs(np.log2(f/(BANDS[i]*1.41))))
        return adj[k]
    return g
def attack(x, ms=12):
    t=np.arange(len(x))/SR*1000; return x*np.minimum(1,(t/ms))**0.8*np.exp(-np.maximum(0,t-300)/450)
def thump(seed, hard=1.0, bright=0.0, n=1.0):
    a=adj.copy(); a[3:]+=bright
    return attack(synth(gl, band_gain_fn(a), seed=seed, n=n, hard=hard, tscale=TS))
def save(name, s, peak):
    s=s-np.mean(s); s=s/np.max(np.abs(s))*peak
    s*=np.minimum(1,(len(s)-np.arange(len(s)))/(SR*0.05))
    with wave.open(name+'.wav','wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes((s*32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg','-y','-loglevel','error','-i',name+'.wav','-c:a','libvorbis','-q:a','5',name+'.ogg'],check=True)
    return s
def put(out,start,sig): i=int(SR*start); out[i:i+len(sig)]+=sig[:len(out)-i]
for v in range(3):
    s=save(f'move{v+1}', thump(10+v, bright=[0,1.5,-1.5][v], n=1.0), 0.8)
    print('move',v+1,np.round(env(s),1))

# Ăn quân: quân chạm quân (sắc, sáng) rồi đặt mạnh xuống bàn
out=np.zeros(int(SR*1.0))
put(out,0,0.35*synth(gl, band_gain_fn(adj+np.array([0,0,0,8,10,10,10])), seed=21, n=0.12, hard=1)*np.exp(-np.arange(int(SR*.12))/(SR*.02)))
put(out,0.035,1.25*thump(22,bright=1.0,n=0.95))
save('capture',out,0.9)
# Chiếu: đặt quân dứt khoát + 2 tiếng mõ gỗ (check_old.wav = bản do gen_bells/gen cũ tạo)
w=wave.open('check_old.wav'); old=np.frombuffer(w.readframes(w.getnframes()),dtype=np.int16).astype(float)/32768
mo=old[int(.16*SR):]
out=np.zeros(int(SR*1.0)); t1=thump(31,hard=1.0,n=1.0); put(out,0,t1/np.max(abs(t1))); put(out,0.2,mo/np.max(abs(mo))*0.6)
save('check',out,0.9)
