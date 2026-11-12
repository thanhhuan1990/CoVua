# Nguồn duy nhất cho hình quân: sinh SVG xem trước + mã Kotlin (android.graphics.Path, toạ độ 0..100).
import sys
BASE=lambda l=24,r=76:('rr',l,76,r,88,4)
P={
 6:[ # Tốt
   ('p','M 37 77 Q 39 60 44 47 L 56 47 Q 61 60 63 77 Z'),
   ('rr',37,40,63,48,3),
   ('c',50,28,12),
   BASE(26,74)],
 3:[ # Xe
   ('p','M 34 77 L 37 42 L 63 42 L 66 77 Z'),
   ('p','M 28 42 L 28 17 L 38 17 L 38 25 L 45 25 L 45 17 L 55 17 L 55 25 L 62 25 L 62 17 L 72 17 L 72 42 Z'),
   BASE()],
 4:[ # Tượng
   ('p','M 38 77 Q 42 66 44 60 L 56 60 Q 58 66 62 77 Z'),
   ('rr',35,54,65,61,3),
   ('p','M 50 15 C 66 26 69 42 60 55 L 40 55 C 31 42 34 26 50 15 Z'),
   ('c',50,11,5),
   BASE()],
 5:[ # Mã
   ('p','M 30 77 L 33 63 C 36 55 42 51 45 46 C 38 49 32 51 27 48 C 21 45 21 38 26 34 L 39 21 L 41 10 L 50 17 C 65 20 75 34 73 52 C 72 62 69 70 69 77 Z'),
   BASE()],
 2:[ # Hậu
   ('p','M 32 77 L 38 52 L 62 52 L 68 77 Z'),
   ('p','M 24 27 L 36 44 L 38 21 L 46 42 L 50 16 L 54 42 L 62 21 L 64 44 L 76 27 L 67 49 L 33 49 Z'),
   ('rr',34,46,66,54,3),
   ('c',24,25,4.2),('c',38,19,4.2),('c',50,13,4.2),('c',62,19,4.2),('c',76,25,4.2),
   BASE(22,78)],
 1:[ # Vua
   ('p','M 32 77 L 38 52 L 62 52 L 68 77 Z'),
   ('p','M 31 49 C 21 37 28 23 41 28 C 45 22 55 22 59 28 C 72 23 79 37 69 49 Z'),
   ('rr',34,46,66,54,3),
   ('rr',47,5,53,27,1.5),('rr',41,11,59,17,1.5),
   BASE(22,78)],
}
# Nét chi tiết (vẽ màu nét, không tô)
D={
 6:[], 3:['M 36 70 L 64 70'],
 4:['M 54 27 L 45 41','M 50 30 L 50 46' ][:1],
 5:['M 55 22 C 64 30 68 44 64 72'],
 2:[], 1:['M 50 30 L 50 46'],
}
DOTS={5:[(41,29,2.9),(29,40,1.7)]}

def svg_shape(s):
    if s[0]=='p': return f'<path d="{s[1]}"/>'
    if s[0]=='c': return f'<circle cx="{s[1]}" cy="{s[2]}" r="{s[3]}"/>'
    _,l,t,r,b,rad=s; return f'<rect x="{l}" y="{t}" width="{r-l}" height="{b-t}" rx="{rad}"/>'

def svg(white):
    fill,out,det=('#FBF7EE','#2A2420','#2A2420') if white else ('#2A2A2D','#0B0B0B','#E3DCCD')
    g=[]
    for i,t in enumerate([1,2,3,4,5,6]):
        x=i*100
        g.append(f'<g transform="translate({x},0)">')
        g.append('<g transform="translate(1.5,3)" fill="#000" fill-opacity="0.25">'+''.join(svg_shape(s) for s in P[t])+'</g>')
        for s in P[t]: g.append(svg_shape(s).replace('/>',f' fill="{fill}" stroke="{out}" stroke-width="3.4" stroke-linejoin="round"/>'))
        for d in D[t]: g.append(f'<path d="{d}" fill="none" stroke="{det}" stroke-width="3" stroke-linecap="round"/>')
        for (cx,cy,r) in DOTS.get(t,[]): g.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{det}"/>')
        g.append('</g>')
    return ''.join(g)

def preview(path):
    sq=[]
    for row in range(2):
        for col in range(6):
            c='#F0D9B5' if (row+col)%2==0 else '#B58863'
            sq.append(f'<rect x="{col*100}" y="{row*100}" width="100" height="100" fill="{c}"/>')
    s=f'<svg xmlns="http://www.w3.org/2000/svg" width="600" height="200" viewBox="0 0 600 200">{"".join(sq)}<g>{svg(True)}</g><g transform="translate(0,100)">{svg(False)}</g></svg>'
    open(path,'w').write(s)

def kt_path(d):
    toks=d.split(); i=0; out=[]
    while i<len(toks):
        c=toks[i]; i+=1
        if c=='M': out.append(f'moveTo({toks[i]}f, {toks[i+1]}f)'); i+=2
        elif c=='L': out.append(f'lineTo({toks[i]}f, {toks[i+1]}f)'); i+=2
        elif c=='Q': out.append('quadTo('+', '.join(t+'f' for t in toks[i:i+4])+')'); i+=4
        elif c=='C': out.append('cubicTo('+', '.join(t+'f' for t in toks[i:i+6])+')'); i+=6
        elif c=='Z': out.append('close()')
    return '; '.join(out)

def kt():
    L=[]
    for t in [1,2,3,4,5,6]:
        parts=[]
        for s in P[t]:
            if s[0]=='p': parts.append(f'path {{ {kt_path(s[1])} }}')
            elif s[0]=='c': parts.append(f'circle({s[1]}f, {s[2]}f, {s[3]}f)')
            else: parts.append(f'rrect({s[1]}f, {s[2]}f, {s[3]}f, {s[4]}f, {s[5]}f)')
        L.append(f'        {t} -> listOf(\n            '+',\n            '.join(parts)+',\n        )')
    shapes='\n'.join(L)
    dl=[]
    for t in [1,2,3,4,5,6]:
        if D[t]: dl.append(f'        {t} -> listOf('+', '.join(f'path {{ {kt_path(d)} }}' for d in D[t])+')')
    dots=[]
    for t,v in DOTS.items(): dots.append(f'        {t} -> listOf('+', '.join(f'floatArrayOf({a}f, {b}f, {c}f)' for a,b,c in v)+')')
    return shapes,'\n'.join(dl),'\n'.join(dots)

if __name__=='__main__':
    if sys.argv[1]=='svg': preview(sys.argv[2])
    else:
        a,b,c=kt(); print(a); print('====='); print(b); print('====='); print(c)
