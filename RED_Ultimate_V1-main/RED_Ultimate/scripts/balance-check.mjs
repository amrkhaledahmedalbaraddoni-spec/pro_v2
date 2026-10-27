#!/usr/bin/env node
/**
 * balance-check.mjs — فاحص توازن الأقواس للمصادر (Kotlin/TS).
 *
 * لماذا: ملف واحد بقوس ناقص يُنتج مئات أخطاء الترجمة الكاذبة (دوال تُقرأ كأنها
 * محلية داخل دالة، فيظهر "Modifier is not applicable to 'local function'").
 * هذا الفاحص يحدد الملف والسطر الأول الذي يختلّ فيه التوازن.
 *
 * الاستخدام:
 *   node scripts/balance-check.mjs <file...>            # فحص ملفات محددة
 *   node scripts/balance-check.mjs --scan <dir...>      # فحص كل *.kt/*.kts/*.ts/*.tsx
 *
 * المخرج: سطر UNBALANCED لكل ملف مختل + كود خروج 1 إن وُجد أي خلل.
 *
 * يدعم: تعليقات // و /* *\/ وسلاسل "..." و'...' ومحارف ثلاثية """..."""
 * وقوالب ${ ... } المتداخلة — وإلا نتجت إيجابيات كاذبة كثيرة في كود Kotlin.
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, extname } from 'node:path';

const CODE = 0, STR = 1, CHR = 2, RAW = 3, BLOCK = 4, LINE = 5, TPL = 6, REGEX = 7;

// ✅ 2026-09-26: كلمات تُتيح تعبيرًا نمطيًا بعدها (`return /re/`) — كانت الشرطة
// المائلة داخل `replace(/"/g, …)` تُقرأ كسلسلة فتنهار حصيلة الأقواس (إيجابية كاذبة).
const REGEX_KEYWORDS = new Set([
  'return', 'typeof', 'instanceof', 'in', 'of', 'new', 'delete', 'void', 'throw',
  'case', 'do', 'else', 'yield', 'await',
]);

/** هل هذه الشرطة المائلة بداية تعبير نمطي؟ سياق ما قبلها يحسم (وإلا فهي قسمة). */
function regexAllowed(recent) {
  const trimmed = recent.replace(/\s+$/, '');
  if (!trimmed) return true;                              // بداية تعبير
  const last = trimmed[trimmed.length - 1];
  // مجموعة متحفّظة عمدًا: `<` و`)` و`]` و`}` و`+` و`*` و`=` مستثناة لأنها شائعة
  // في JSX (`<X />` بعد `=`) وفي القسمة — وإدراجها يُنتج إيجابيات كاذبة أسوأ من الأصل.
  if ('(,:[!&|?;~^'.includes(last)) return true;             // بعد مُشغِّل/فتح قوس
  const word = trimmed.match(/[A-Za-z_$][A-Za-z0-9_$]*$/);
  return !!word && REGEX_KEYWORDS.has(word[0]);
}
const QUOTE = 34, APOS = 39, TICK = 96, BSLASH = 92, SLASH = 47,
      STAR = 42, NL = 10, DOLLAR = 36;

function scan(text) {
  const src = text.replace(/\r\n/g, '\n');
  let depth = 0, paren = 0, bracket = 0, line = 1, firstNeg = 0;
  let state = CODE;
  let tplDepth = 0;
  let rawTriple = false;   // """...""" (Kotlin raw) مقابل `...` (معرّف Kotlin / قالب TS)
  let regexClass = false;  // داخل [...] في تعبير نمطي
  let recent = '';         // آخر محارف CODE — تُحسم بها قسمة/تعبير نمطي
  const frames = [];

  const push = (s, d = 0, triple = false) => {
    frames.push({ state, tplDepth, rawTriple, regexClass });
    state = s; tplDepth = d; rawTriple = triple; regexClass = false;
  };
  const pop = () => {
    const f = frames.pop();
    if (f) { state = f.state; tplDepth = f.tplDepth; rawTriple = f.rawTriple; regexClass = f.regexClass; }
    else { state = CODE; tplDepth = 0; rawTriple = false; regexClass = false; }
  };

  for (let i = 0; i < src.length; i++) {
    const c = src.charCodeAt(i);
    const n = src.charCodeAt(i + 1);
    const n2 = src.charCodeAt(i + 2);
    if (c === NL) line++;

    if (state === LINE) { if (c === NL) state = CODE; continue; }
    if (state === BLOCK) { if (c === STAR && n === SLASH) { state = CODE; i++; } continue; }
    if (state === REGEX) {
      // حرفي تعبير نمطي: لا تُفسَّر محتوياته كسلاسل ولا تُحسب أقواسه
      if (c === BSLASH) { i++; continue; }
      if (c === NL) { pop(); continue; }               // التعبير النمطي لا يمتد على سطرين
      if (c === 91) { regexClass = true; continue; }   // [
      if (c === 93) { regexClass = false; continue; }  // ]
      if (c === SLASH && !regexClass) { pop(); continue; }
      continue;
    }
    if (state === CHR) {
      if (c === BSLASH) { i++; continue; }
      if (c === NL || c === APOS) pop();
      continue;
    }
    if (state === RAW) {
      if (rawTriple) {
        if (c === QUOTE && n === QUOTE && n2 === QUOTE) { pop(); i += 2; continue; }
      } else {
        if (c === BSLASH) { i++; continue; }
        if (c === TICK) { pop(); continue; }
      }
      if (c === DOLLAR && n === 123) { push(TPL, 1); i++; continue; }
      continue;
    }
    if (state === STR) {
      if (c === BSLASH) { i++; continue; }
      if (c === NL || c === QUOTE) { pop(); continue; }
      if (c === DOLLAR && n === 123) { push(TPL, 1); i++; continue; }
      continue;
    }
    if (state === TPL) {
      if (c === 123) { tplDepth++; continue; }
      if (c === 125) { tplDepth--; if (tplDepth === 0) pop(); continue; }
      // تعبير نمطي داخل `${…}` — مثل `replace(/"/g, '""')` في قوالب TS
      if (tplDepth > 0 && c === SLASH && n !== SLASH && n !== STAR && regexAllowed(recent)) {
        push(REGEX); continue;
      }
      if (c === QUOTE && n === QUOTE && n2 === QUOTE) { push(RAW, 0, true); i += 2; continue; }
      if (c === QUOTE) { push(STR); continue; }
      if (c === TICK) { push(RAW, 0, false); continue; }
      if (c === APOS) { push(CHR); continue; }
      if (c === 40) paren++;
      else if (c === 41) paren--;
      else if (c === 91) bracket++;
      else if (c === 93) bracket--;
      if (tplDepth > 0) recent = (recent + src[i]).slice(-24);
      continue;
    }

    // state === CODE
    if (c === SLASH && n === SLASH) { state = LINE; recent = ''; continue; }
    if (c === SLASH && n === STAR) { state = BLOCK; i++; continue; }
    if (c === SLASH && regexAllowed(recent)) { push(REGEX); continue; }
    if (c === QUOTE && n === QUOTE && n2 === QUOTE) { push(RAW, 0, true); i += 2; continue; }
    if (c === QUOTE) { push(STR); continue; }
    if (c === APOS) { push(CHR); continue; }
    if (c === 123) depth++;
    else if (c === 125) { depth--; if (depth < 0 && !firstNeg) firstNeg = line; }
    else if (c === 40) paren++;
    else if (c === 41) paren--;
    else if (c === 91) bracket++;
    else if (c === 93) bracket--;
    recent = (recent + src[i]).slice(-24);
  }
  // state != CODE عند EOF يعني تعليقاً/سلسلة/قالباً غير مُغلق — سبب شائع لخطأ
  // "Syntax error: Unclosed comment" الذي يُبطل الملف كاملاً (ويتخفّى خلف مئات الأخطاء المتسلسلة).
  const unterminated = state !== CODE || frames.length > 0;
  return { depth, paren, bracket, firstNeg, unterminated, finalState: state, frameStates: frames.map((f) => f.state) };
}

/** نسخة تشخيصية تُرجع أيضاً الحالة النهائية ووصف المكدّس. */
const STATE_NAMES = ['CODE', 'STR', 'CHR', 'RAW', 'BLOCK', 'LINE', 'TPL'];
function scanWithState(text) {
  const r = scan(text);
  return {
    ...r,
    state: r.frameStates.map((s) => STATE_NAMES[s]).join('>') + ' | ' + (STATE_NAMES[r.finalState] || '?'),
    open: r.frameStates.length ? `stack=${r.frameStates.length}` : ''
  };
}

function walk(dir, out = []) {
  let entries = [];
  try { entries = readdirSync(dir); } catch { return out; }
  for (const e of entries) {
    if (['node_modules', '.gradle', 'build', 'archive', '.git', 'dist'].includes(e)) continue;
    const p = join(dir, e);
    let st;
    try { st = statSync(p); } catch { continue; }
    if (st.isDirectory()) walk(p, out);
    else if (['.kt', '.kts', '.ts', '.tsx'].includes(extname(p))) out.push(p);
  }
  return out;
}

const args = process.argv.slice(2);
if (args[0] === '--trace') {
  // استخدام: --trace <file> — يطبع تغيّرات العمق وحالة الماسح عند نهاية كل سطر،
  // لتشخيص الإيجابيات الكاذبة بدقة. (O(n²) لكنه للتشخيص فقط)
  const text = readFileSync(args[1], 'utf8');
  const lines = text.replace(/\r\n/g, '\n').split('\n');
  let prev = 0;                 // عمق السطر السابق فعلياً
  for (let ln = 1; ln <= lines.length; ln++) {
    const r = scanWithState(lines.slice(0, ln).join('\n'));
    if (r.depth !== prev || r.state !== 'CODE') {
      console.log(`line ${ln}: depth ${prev}->${r.depth} state=${r.state}${r.open ? ' OPEN=' + r.open : ''}  | ${lines[ln - 1].trim().slice(0, 90)}`);
    }
    prev = r.depth;
  }
  const r = scan(text);
  console.log(`FINAL braces=${r.depth} parens=${r.paren} brackets=${r.bracket}`);
  process.exit(0);
}
let files = [];
if (args[0] === '--scan') {
  for (const dir of args.slice(1)) files = files.concat(walk(dir));
} else {
  files = args;
}
if (!files.length) {
  console.error('usage: node scripts/balance-check.mjs --scan <dir...> | <file...>');
  process.exit(2);
}

let bad = 0;
for (const f of files) {
  let text;
  try { text = readFileSync(f, 'utf8'); } catch { continue; }
  const r = scan(text);
  if (r.depth !== 0 || r.paren !== 0 || r.bracket !== 0 || r.firstNeg || r.unterminated) {
    bad++;
    const why = [
      `braces=${r.depth}`,
      `parens=${r.paren}`,
      `brackets=${r.bracket}`,
      `firstNegativeAt=${r.firstNeg || '-'}`,
      r.unterminated ? `UNTERMINATED(${STATE_NAMES[r.finalState] || '?'})` : ''
    ].filter(Boolean).join(' ');
    console.log(`UNBALANCED ${f}  ${why}`);
  }
}
console.log(`checked=${files.length} unbalanced=${bad}`);
process.exit(bad ? 1 : 0);
