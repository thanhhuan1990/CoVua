# Sinh icon app (adaptive icon: nền + quân + đơn sắc) từ hình quân trong gen.py.
# Dùng: python3 tools/pieces/icon.py <thư mục ra>  → bg.svg, fg.svg, full.svg, mono_path.txt
import sys, os, importlib.util, math
here = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('g', os.path.join(here, 'gen.py')); g = importlib.util.module_from_spec(spec); spec.loader.exec_module(g)
out = sys.argv[1]; os.makedirs(out, exist_ok=True)

LIGHT, DARK = '#F0D9B5', '#B58863'
def bg():
    s = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">',
         '<defs><radialGradient id="v" cx=".5" cy=".42" r=".75"><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".28"/></radialGradient></defs>']
    n = 6; c = 108 / n
    for r in range(n):
        for k in range(n):
            s.append(f'<rect x="{k*c:.3f}" y="{r*c:.3f}" width="{c+0.05:.3f}" height="{c+0.05:.3f}" fill="{LIGHT if (r+k)%2==0 else DARK}"/>')
    s.append('<rect width="108" height="108" fill="url(#v)"/></svg>')
    return ''.join(s)

def piece(t, white, tx, ty, sc, shadow=True):
    fill, line, det = ('#FBF7EE', '#2A2420', '#2A2420') if white else ('#2A2A2D', '#0B0B0B', '#E3DCCD')
    o = [f'<g transform="translate({tx},{ty}) scale({sc})">']
    if shadow:
        o.append('<g transform="translate(2.5,4)" fill="#000" fill-opacity="0.32">' + ''.join(g.svg_shape(q) for q in g.P[t]) + '</g>')
    for q in g.P[t]:
        o.append(g.svg_shape(q).replace('/>', f' fill="{fill}" stroke="{line}" stroke-width="3.6" stroke-linejoin="round"/>'))
    for d in g.D[t]:
        o.append(f'<path d="{d}" fill="none" stroke="{det}" stroke-width="3.2" stroke-linecap="round"/>')
    for (cx, cy, r) in g.DOTS.get(t, []):
        o.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{det}"/>')
    o.append('</g>'); return ''.join(o)

# Bố cục trong vùng an toàn (đường kính 66 quanh tâm 54,54): Vua đen phía sau trái, Mã trắng phía trước phải.
KING = (1, False, 20, 23, 0.50)
KNIGHT = (5, True, 35, 31, 0.56)
def fg():
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">'
            + piece(*KING) + piece(*KNIGHT) + '</svg>')

def full():
    inner_bg = bg().split('>', 1)[1].rsplit('</svg>', 1)[0]
    inner_fg = fg().split('>', 1)[1].rsplit('</svg>', 1)[0]
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">{inner_bg}{inner_fg}</svg>'

# Đơn sắc (themed icon): bóng của Mã + Vua, chuyển hết sang pathData của VectorDrawable
def shape_d(q):
    if q[0] == 'p': return q[1]
    if q[0] == 'c':
        _, cx, cy, r = q
        return f'M {cx-r} {cy} A {r} {r} 0 1 0 {cx+r} {cy} A {r} {r} 0 1 0 {cx-r} {cy} Z'
    _, l, t, r, b, rad = q
    return (f'M {l+rad} {t} L {r-rad} {t} A {rad} {rad} 0 0 1 {r} {t+rad} L {r} {b-rad} A {rad} {rad} 0 0 1 {r-rad} {b} '
            f'L {l+rad} {b} A {rad} {rad} 0 0 1 {l} {b-rad} L {l} {t+rad} A {rad} {rad} 0 0 1 {l+rad} {t} Z')
def mono_groups():
    # Chỉ quân Mã (hai quân chồng nhau thành một khối khó đọc khi đơn sắc); mắt khoét rỗng (evenOdd)
    t, sc = 5, 0.6
    tx, ty = 54 - 48 * sc, 54 - 49 * sc
    eye = g.DOTS[5][0]
    # evenOdd: các mảng không được chồng nhau (chỗ chồng sẽ bị khoét) → đặt đế ngay dưới thân Mã
    shapes = [('rr', q[1], 77, q[3], q[4], q[5]) if q[0] == 'rr' else q for q in g.P[t]]
    d = ' '.join(shape_d(q) for q in shapes) + ' ' + shape_d(('c',) + eye)
    return [(round(tx, 2), round(ty, 2), sc, d)]

open(os.path.join(out, 'bg.svg'), 'w').write(bg())
open(os.path.join(out, 'fg.svg'), 'w').write(fg())
open(os.path.join(out, 'full.svg'), 'w').write(full())
with open(os.path.join(out, 'mono.txt'), 'w') as f:
    for tx, ty, sc, d in mono_groups(): f.write(f'{tx}|{ty}|{sc}|{d}\n')
