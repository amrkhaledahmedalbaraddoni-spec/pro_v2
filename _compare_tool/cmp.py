import os, hashlib, difflib, json, collections, time

A = r"D:\RED_Golden\RED_Ultimate_V1-main"
B = r"D:\pro_new\pro_new\RED_Ultimate_V1-main"
OUT = os.path.dirname(os.path.abspath(__file__))

EXCL_DIRS = {".git", ".gradle", ".gradle_home", ".gradle-home", "build", "node_modules",
             ".kotlin", ".idea", ".cluster", "dist", "local-artifacts", ".artifacts",
             "local-tools", ".cxx", ".externalNativeBuild", "__pycache__", "apk-output"}


def walk(root):
    out = {}
    for dp, dns, fns in os.walk(root):
        dns[:] = [d for d in dns if d not in EXCL_DIRS]
        for fn in fns:
            p = os.path.join(dp, fn)
            rel = os.path.relpath(p, root).replace("\\", "/")
            try:
                st = os.stat(p)
            except OSError:
                continue
            out[rel] = (st.st_size, int(st.st_mtime))
    return out


def sha(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for c in iter(lambda: f.read(1 << 20), b""):
            h.update(c)
    return h.hexdigest()


def read_text(p):
    try:
        b = open(p, "rb").read()
    except OSError:
        return None
    if b"\x00" in b[:8192]:
        return None
    for enc in ("utf-8", "utf-8-sig"):
        try:
            return b.decode(enc)
        except UnicodeDecodeError:
            continue
    return None


ma, mb = walk(A), walk(B)
cache = {}


def h(root, rel):
    k = (root, rel)
    if k not in cache:
        cache[k] = sha(os.path.join(root, rel))
    return cache[k]


setA, setB = set(ma), set(mb)
onlyA, onlyB = sorted(setA - setB), sorted(setB - setA)
common = sorted(setA & setB)
diff = []
for rel in common:
    try:
        if h(A, rel) != h(B, rel):
            diff.append(rel)
    except OSError:
        pass
same = len(common) - len(diff)

linesA, linesB = collections.Counter(), collections.Counter()
textA, textB = {}, {}
for root, files, tstore, lstore in ((A, setA, textA, linesA), (B, setB, textB, linesB)):
    for rel in files:
        t = read_text(os.path.join(root, rel))
        if t is not None:
            tstore[rel] = t
            lstore[rel] = t.count("\n") + (0 if t.endswith("\n") or t == "" else 1)


def modkey(rel):
    parts = rel.split("/")
    if parts[0] == "RED_Ultimate" and len(parts) > 1:
        return "RED_Ultimate/" + parts[1]
    if len(parts) > 1:
        return parts[0] + "/"
    return "(root)"


mods = collections.defaultdict(lambda: [0, 0, 0, 0, 0, 0])
for rel in setA:
    m = mods[modkey(rel)]
    m[0] += 1
    m[1] += linesA.get(rel, 0)
for rel in setB:
    m = mods[modkey(rel)]
    m[2] += 1
    m[3] += linesB.get(rel, 0)
for rel in onlyA:
    mods[modkey(rel)][4] += 1
for rel in onlyB:
    mods[modkey(rel)][5] += 1

diff_rows = []
for rel in diff:
    ta, tb = textA.get(rel), textB.get(rel)
    if ta is None or tb is None:
        diff_rows.append((rel, -1, -1, -1))
        continue
    sm = difflib.SequenceMatcher(a=ta.splitlines(), b=tb.splitlines(), autojunk=False)
    add = rem = 0
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag == "delete":
            rem += i2 - i1
        elif tag == "insert":
            add += j2 - j1
        elif tag == "replace":
            rem += i2 - i1
            add += j2 - j1
    diff_rows.append((rel, add, rem, max(add, rem)))

onlyB_rows = sorted(((r, linesB.get(r, 0)) for r in onlyB), key=lambda x: -x[1])[:30]
onlyA_rows = sorted(((r, linesA.get(r, 0)) for r in onlyA), key=lambda x: -x[1])[:30]

fmt = lambda ts: time.strftime("%Y-%m-%d %H:%M", time.localtime(ts))
newA = sorted(((ma[r][1], r) for r in ma), reverse=True)[:8]
newB = sorted(((mb[r][1], r) for r in mb), reverse=True)[:8]

out = []
out.append("=== TOTALS (build/.git/.gradle/node_modules/bin dirs excluded) ===")
out.append(f"A files={len(ma)}  text-lines={sum(linesA.values())}  bytes={sum(v[0] for v in ma.values()):,}")
out.append(f"B files={len(mb)}  text-lines={sum(linesB.values())}  bytes={sum(v[0] for v in mb.values()):,}")
out.append(f"common={len(common)} identical={same} differing={len(diff)} onlyA={len(onlyA)} onlyB={len(onlyB)}")

cutoff = time.mktime(time.strptime("2026-09-20", "%Y-%m-%d"))
out.append(f"files modified after 2026-09-20:  A={sum(1 for v in ma.values() if v[1] >= cutoff)}  B={sum(1 for v in mb.values() if v[1] >= cutoff)}")
out.append("newest A: " + " | ".join(f"{fmt(t)} {r}" for t, r in newA))
out.append("newest B: " + " | ".join(f"{fmt(t)} {r}" for t, r in newB))
out.append("")
out.append("=== MODULES (A files/lines | B files/lines | onlyA onlyB) ===")
for m, v in sorted(mods.items(), key=lambda kv: -(kv[1][0] + kv[1][2])):
    out.append(f"{m:42s} A:{v[0]:5d}/{v[1]:7d}  B:{v[2]:5d}/{v[3]:7d}  onlyA:{v[4]:4d} onlyB:{v[5]:4d}")

out.append("")
out.append("=== TOP ONLY-B (files present only in B, by lines) ===")
out += [f"{n:6d}  {r}" for r, n in onlyB_rows]
out.append("")
out.append("=== TOP ONLY-A (files present only in A, by lines) ===")
out += [f"{n:6d}  {r}" for r, n in onlyA_rows]
out.append("")
out.append("=== TOP DIFFERING TEXT FILES (+added/-removed) ===")
out += [f"+{a:<5d} -{r:<5d} {rel}" for rel, a, r, m in sorted(diff_rows, key=lambda x: -x[3])[:40]]
out.append("")
out.append("=== TOP-LEVEL EXTRAS (outside RED_Ultimate/) ===")
out.append("onlyA: " + ", ".join(sorted({r.split('/')[0] for r in onlyA if not r.startswith('RED_Ultimate/')})))
out.append("onlyB: " + ", ".join(sorted({r.split('/')[0] for r in onlyB if not r.startswith('RED_Ultimate/')})))

body = "\n".join(out)
open(os.path.join(OUT, "summary.txt"), "w", encoding="utf-8").write(body)
json.dump({"onlyA": onlyA, "onlyB": onlyB, "diff": diff, "diffRows": diff_rows,
           "linesA": linesA, "linesB": linesB},
          open(os.path.join(OUT, "data.json"), "w", encoding="utf-8"))
print(body)
