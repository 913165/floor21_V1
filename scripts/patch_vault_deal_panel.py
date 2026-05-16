"""Replace vault deal amounts block in list.html."""
from pathlib import Path
import re

path = Path(__file__).resolve().parents[1] / "src/main/resources/templates/vault/list.html"
text = path.read_text(encoding="utf-8")

start = '    <motion class="card shadow-sm mb-3 border-primary border-opacity-25" th:if="${amountForm != null}">'
start = '    <div class="card shadow-sm mb-3 border-primary border-opacity-25" th:if="${amountForm != null}">'
end = '    </script>\n\n    <div th:if="${#lists.isEmpty(slabRows)}"'

i = text.find(start)
j = text.find(end)
if i < 0 or j < 0:
    raise SystemExit(f"markers not found i={i} j={j}")

new_block = path.parent.joinpath("vault_deal_block.html").read_text(encoding="utf-8")
new_block = re.sub(r"</?motion[^>]*>", "", new_block)

out = text[:i] + new_block + text[j:]
path.write_text(out, encoding="utf-8", newline="\n")
print("patched", path)
