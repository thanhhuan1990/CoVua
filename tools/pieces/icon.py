# Sinh icon app (adaptive icon) từ hình quân trong gen.py: Vua vàng kim trên nền xanh hoàng gia + sàn bàn cờ phối cảnh.
# Dùng: python3 tools/pieces/icon.py <thư mục ra>  → bg.svg, fg.svg, full.svg, mono.txt (tx|ty|scale|pathData mỗi dòng 1 mảng)
import sys, os, importlib.util
here = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('g', os.path.join(here, 'gen.py')); g = importlib.util.module_from_spec(spec); spec.loader.exec_module(g)
out = sys.argv[1]; os.makedirs(out, exist_ok=True)

KING_TX, KING_TY, KING_S = 24, 27, 0.6   # Vua: tâm ~ (54, 55), đế chạm sàn ở y ≈ 80, nằm trong vùng an toàn 66dp

def bg():
    ys = [74, 80, 87, 96, 108]; vx, vy = 54, 40
    X = lambda x0, y: vx + (x0 - vx) * (y - vy) / (108 - vy)
    cols = [-60, -30, 0, 30, 54, 78, 108, 138, 168]
    floor = []
    for i in range(len(ys) - 1):
        for j in range(len(cols) - 1):
            y0, y1 = ys[i], ys[i + 1]
            p = [(X(cols[j], y0), y0), (X(cols[j + 1], y0), y0), (X(cols[j + 1], y1), y1), (X(cols[j], y1), y1)]
            floor.append('<polygon points="' + ' '.join(f'{a:.2f},{b:.2f}' for a, b in p) + f'" fill="{"#E9D3A6" if (i + j) % 2 == 0 else "#8A5A34"}"/>')
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><defs>'
            '<linearGradient id="a1" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1E3F78"/><stop offset="1" stop-color="#0B1630"/></linearGradient>'
            '<radialGradient id="a2" cx=".5" cy=".46" r=".36"><stop offset="0" stop-color="#FFC24A" stop-opacity=".62"/><stop offset=".55" stop-color="#E8962A" stop-opacity=".22"/><stop offset="1" stop-color="#E8962A" stop-opacity="0"/></radialGradient>'
            '<linearGradient id="fl" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0B1630" stop-opacity=".55"/><stop offset=".5" stop-color="#0B1630" stop-opacity="0"/></linearGradient></defs>'
            '<rect width="108" height="108" fill="url(#a1)"/>' + ''.join(floor) +
            '<rect y="74" width="108" height="34" fill="url(#fl)"/><rect width="108" height="108" fill="url(#a2)"/></svg>')

def fg():
    t = 1; line = '#5A3A0E'
    o = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><defs>'
         '<linearGradient id="gold" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFE9A6"/><stop offset=".5" stop-color="#F2BE45"/><stop offset="1" stop-color="#B9791C"/></linearGradient></defs>',
         '<ellipse cx="54" cy="80" rx="21" ry="3.6" fill="#000" opacity=".35"/>',
         f'<g transform="translate({KING_TX},{KING_TY}) scale({KING_S})">']
    for q in g.P[t]:
        o.append(g.svg_shape(q).replace('/>', f' fill="url(#gold)" stroke="{line}" stroke-width="3.8" stroke-linejoin="round"/>'))
    for d in g.D[t]:
        o.append(f'<path d="{d}" fill="none" stroke="{line}" stroke-width="3.2" stroke-linecap="round"/>')
    o.append('</g></svg>')
    return ''.join(o)

def full():
    a = bg().split('</defs>', 1)[1].rsplit('</svg>', 1)[0]
    b = fg()
    defs_bg = bg().split('<defs>', 1)[1].split('</defs>', 1)[0]
    defs_fg = b.split('<defs>', 1)[1].split('</defs>', 1)[0]
    body_fg = b.split('</defs>', 1)[1].rsplit('</svg>', 1)[0]
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><defs>{defs_bg}{defs_fg}</defs>{a}{body_fg}</svg>'

def shape_d(q):
    if q[0] == 'p': return q[1]
    if q[0] == 'c':
        _, cx, cy, r = q
        return f'M {cx-r} {cy} A {r} {r} 0 1 0 {cx+r} {cy} A {r} {r} 0 1 0 {cx-r} {cy} Z'
    _, l, t, r, b, rad = q
    return (f'M {l+rad} {t} L {r-rad} {t} A {rad} {rad} 0 0 1 {r} {t+rad} L {r} {b-rad} A {rad} {rad} 0 0 1 {r-rad} {b} '
            f'L {l+rad} {b} A {rad} {rad} 0 0 1 {l} {b-rad} L {l} {t+rad} A {rad} {rad} 0 0 1 {l+rad} {t} Z')

# Đơn sắc / thông báo: bóng quân Vua, mỗi mảng một path (tô gộp, không khoét)
def mono_parts():
    return [shape_d(q) for q in g.P[1]]

open(os.path.join(out, 'bg.svg'), 'w').write(bg())
open(os.path.join(out, 'fg.svg'), 'w').write(fg())
open(os.path.join(out, 'full.svg'), 'w').write(full())
open(os.path.join(out, 'mono.txt'), 'w').write('\n'.join(mono_parts()) + '\n')
