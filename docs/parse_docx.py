import xml.etree.ElementTree as ET

NS = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"


def cell_text(tc):
    return "".join(t.text or "" for t in tc.iter(W + "t")).strip()


def run_info(r):
    texts = "".join(t.text or "" for t in r.findall(".//w:t", NS))
    rpr = r.find("w:rPr", NS)
    bold = rpr is not None and rpr.find("w:b", NS) is not None
    underline = rpr is not None and rpr.find("w:u", NS) is not None
    sz = rpr.find("w:sz", NS) if rpr is not None else None
    font = rpr.find("w:rFonts", NS) if rpr is not None else None
    size = sz.get(W + "val") if sz is not None else ""
    fam = font.get(W + "ascii") if font is not None else ""
    return texts, bold, underline, size, fam


path = r"c:\work_floor21\floor21_V1\docs\ademnpw_extracted\word\document.xml"
root = ET.parse(path).getroot()
body = root.find("w:body", NS)

for i, child in enumerate(body):
    tag = child.tag.split("}")[-1]
    if tag == "p":
        ppr = child.find("w:pPr", NS)
        align = ""
        style = ""
        if ppr is not None:
            jc = ppr.find("w:jc", NS)
            if jc is not None:
                align = jc.get(W + "val", "")
            ps = ppr.find("w:pStyle", NS)
            if ps is not None:
                style = ps.get(W + "val", "")
        parts = []
        for r in child.findall("w:r", NS):
            t, b, u, sz, fam = run_info(r)
            if t:
                parts.append(f"[{'B' if b else ''}{'U' if u else ''} sz={sz} fam={fam}]{t}")
        line = " ".join(parts)
        if line.strip():
            print(f"P[{i}] style={style} align={align}: {line[:400]}")
    elif tag == "tbl":
        rows = child.findall("w:tr", NS)
        print(f"TABLE[{i}] rows={len(rows)}")
        for ri, tr in enumerate(rows):
            cells = [cell_text(tc) for tc in tr.findall("w:tc", NS)]
            print(f"  R{ri}: {' || '.join(cells)[:500]}")
    elif tag == "sectPr":
        print(f"SECTPR[{i}]")
