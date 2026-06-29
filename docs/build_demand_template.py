"""Build demand-letter template with ${DL_*} placeholders from sample docx."""
import shutil
import zipfile
from pathlib import Path

SRC = Path(r"c:\work_floor21\floor21_V1\docs\ademnpw.docx")
DST = Path(r"c:\work_floor21\floor21_V1\src\main\resources\demand-letter\default-demand-letter.docx")
DST.parent.mkdir(parents=True, exist_ok=True)

# Order matters: longer / more specific strings first.
replacements = [
    ("Rizwana Nadeem TambeNadeem Murad Tambe", "${DL_OWNER_NAMES}"),
    (
        "A-25-2, Sneh Co-op Housing SocietySector - 19A, NerulNavi Mumbai - 400706",
        "${DL_OWNER_ADDRESS}",
    ),
    ("Date: 30-Jun-2026", "Date: ${DL_LETTER_DATE}"),
    ("Due Date: 15-Jul-2026", "Due Date: ${DL_DUE_DATE}"),
    ("Project : LA VESTA", "Project : ${DL_PROJECT}"),
    ("Unit No: 1605", "Unit No: ${DL_UNIT_NO}"),
    ("GSTIN: 27AFGFS8624D1ZU", "GSTIN: ${DL_GSTIN}"),
    ("TAN:MUMS33415L", "TAN:${DL_TAN}"),
    ("Ph- 9167079377", "Ph- ${DL_PHONE}"),
    (
        'REFRENCE - Flat No.  1605 , 16th Floor  in Proposed Project Name: \u201cLA VESTA\u201d At Plot No.17+31+32, Sector-13, Nerul, Navi Mumbai.',
        "REFRENCE - Flat No.  ${DL_FLAT_NO} , ${DL_FLOOR}  in Proposed Project Name: \u201c${DL_PROJECT}\u201d At ${DL_SITE}.",
    ),
    (
        "registered in the office of the sub-registrar of assurance at CBD-Belapur vide agreement dated ,",
        "registered in the office of the sub-registrar of assurance at ${DL_REG_PLACE} vide agreement dated ${DL_AGREEMENT_DATE},",
    ),
    ("Rs.  2,11,93,000 /", "Rs.  ${DL_CONSIDERATION_FIGURES} /"),
    (
        "Rupees Two Crore Eleven Lakh Ninety Three Thousand Only",
        "${DL_CONSIDERATION_WORDS}",
    ),
    (
        "till  On or before completion 4th Slab  and",
        "till  ${DL_MILESTONE}  and",
    ),
    ("For SEAVISTA INFRASTRUCTURE LLP", "For ${DL_SIGNATORY}"),
    ("Upto \u2013 On or before completion 2nd Slab", "${DL_PAY_R1_NAME}"),
    ("9966008", "${DL_PAY_R1_INST}"),
    ("1,00,667", "${DL_PAY_R1_TDS}"),
    ("5,03,334", "${DL_PAY_R1_GST}"),
    ("On or before completion 4th Slab", "${DL_PAY_R2_NAME}"),
    ("524527", "${DL_PAY_R2_INST}"),
    ("5,298", "${DL_PAY_R2_TDS}"),
    ("26,491", "${DL_PAY_R2_GST}"),
    ("1,04,90,535", "${DL_PAY_TOTAL_INST}"),
    ("1,05,965", "${DL_PAY_TOTAL_TDS}"),
    ("5,29,825", "${DL_PAY_TOTAL_GST}"),
    ("22,00,000", "${DL_PAY_RECV_INST}"),
    ("2,11,930", "${DL_PAY_RECV_TDS}"),
    ("82,90,535", "${DL_PAY_DUE_INST}"),
    ("0", "${DL_PAY_DUE_TDS}"),
    ("10210819652", "${DL_BANK_INST_ACCT}"),
    ("409002306453", "${DL_BANK_GST_ACCT}"),
    ("SEAVISTA INFRASTRUCTURE LLP", "${DL_BANK_INST_HOLDER}"),
    ("SEAVISTA INFRSTRUCTURE LLP", "${DL_BANK_GST_HOLDER}"),
    ("IDFC FIRST Bank", "${DL_BANK_INST_NAME}"),
    ("RBL Bank Ltd", "${DL_BANK_GST_NAME}"),
    ("Kharghar", "${DL_BANK_BRANCH}"),
    ("Mumbai", "${DL_BANK_CITY}"),
    ("IDFB0040134", "${DL_BANK_INST_IFSC}"),
    ("RATN0000078", "${DL_BANK_GST_IFSC}"),
]

with zipfile.ZipFile(SRC, "r") as zin:
    xml = zin.read("word/document.xml").decode("utf-8")
    for old, new in replacements:
        if old not in xml:
            print("WARN missing:", repr(old[:60]))
        else:
            xml = xml.replace(old, new, 1)

    tmp = DST.with_suffix(".tmp.docx")
    with zipfile.ZipFile(tmp, "w", compression=zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            data = zin.read(item.filename)
            if item.filename == "word/document.xml":
                data = xml.encode("utf-8")
            zout.writestr(item, data)
    if DST.exists():
        DST.unlink()
    tmp.rename(DST)
    print("Wrote", DST)
