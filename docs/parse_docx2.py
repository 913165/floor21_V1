import xml.etree.ElementTree as ET
import re

NS = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"

path = r"c:\work_floor21\floor21_V1\docs\ademnpw_extracted\word\document.xml"
xml = open(path, encoding="utf-8").read()

# First elements before table
for m in re.finditer(r"<w:tbl>", xml):
    start = max(0, m.start() - 2000)
    snippet = xml[start:m.start()]
    if "DEMAND" in snippet or "To," in snippet:
        print("=== HEADER TABLE CONTEXT ===")
        print(snippet[-1500:])
        break

# Payment table XML snippet
idx = xml.find("Sr.no.")
if idx > 0:
    print("\n=== PAYMENT TABLE SNIPPET ===")
    print(xml[idx-500:idx+2500])

# styles from styles.xml
styles_path = r"c:\work_floor21\floor21_V1\docs\ademnpw_extracted\word\styles.xml"
styles = open(styles_path, encoding="utf-8").read()
for name in ["ListParagraph", "Footer", "Normal"]:
    m = re.search(rf'<w:style w:type="paragraph" w:styleId="{name}"[^>]*>.*?</w:style>', styles, re.DOTALL)
    if m:
        print(f"\n=== STYLE {name} ===")
        print(m.group(0)[:800])
