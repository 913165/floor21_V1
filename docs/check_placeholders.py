import zipfile
import re
from pathlib import Path

docx = Path(r"c:\work_floor21\floor21_V1\docs\ademnpw.docx")
with zipfile.ZipFile(docx) as z:
    xml = z.read("word/document.xml").decode("utf-8")

needles = [
    "DEMAND LETTER",
    "Rizwana Nadeem Tambe",
    "Date: 30-Jun-2026",
    "Due Date: 15-Jul-2026",
    "LA VESTA",
    "2,11,93,000",
    "On or before completion 4th Slab",
    "Upto",
    "9966008",
    "Total Amount",
    "SEAVISTA INFRASTRUCTURE LLP",
]
for n in needles:
    print(n, "=>", "FOUND" if n in xml else "MISSING")

# show text nodes around milestone
for m in re.finditer(r"<w:t[^>]*>([^<]{1,80})</w:t>", xml):
    t = m.group(1)
    if "Upto" in t or "9966008" in t or "SUBJECT" in t:
        print("TEXT:", t)
