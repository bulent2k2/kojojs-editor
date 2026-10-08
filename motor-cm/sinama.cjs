#!/usr/bin/env node
// window.KocoMotor sözleşmesinin başsız Chrome'da sınaması (kojojs-editor#75).
// Çalıştırma: KOJO_CHROME=/yol/chrome node sinama.cjs   (CI: google-chrome)
const {chromium} = require("playwright-core");
const fs = require("fs"), path = require("path");
const PAKET = path.join(__dirname, "..", "server", "src", "main", "assets", "javascript", "motor-cm.js");
const TR = JSON.parse(fs.readFileSync(path.join(__dirname, "tr-anahtar.json"), "utf8"));
const ÖRNEK = fs.readFileSync(path.join(__dirname, "ornek.kojo"), "utf8");
const ADAYLAR = [
  {name: "(n: Kesir): Birim", value: "ileri"}, {name: "(n: Kesir): Birim", value: "İleri"},
  {name: "(): Birim", value: "iğne"}, {name: "(): Birim", value: "Işık"},
  {name: "(): Birim", value: "ışık"}, {name: ": Kesir", value: "ısı"},
  {name: "(x: Kesir, y: Kesir): Birim", value: "ilerleGit"}
];
let sayı = 0, kalan = 0;
function sav(ad, koşul, ayrıntı) { sayı++; if (koşul) console.log("  ok  " + ad); else { kalan++; console.log("  KALDI  " + ad + (ayrıntı !== undefined ? "  -> " + JSON.stringify(ayrıntı) : "")); } }

(async () => {
  const b = await chromium.launch({executablePath: process.env.KOJO_CHROME || undefined, args: ["--no-sandbox"]});
  const c = await b.newContext({viewport: {width: 412, height: 915}, deviceScaleFactor: 2, isMobile: true, hasTouch: true});
  const p = await c.newPage();
  const hatalar = []; p.on("pageerror", (e) => hatalar.push(String(e))); p.on("console", (m) => { if (m.type() === "error") hatalar.push(m.text()); });
  await p.setContent('<!doctype html><meta charset="utf-8"><div id="editor" style="position:absolute;inset:0"></div>');
  await p.addScriptTag({path: PAKET});
  await p.evaluate(({örnek, adaylar}) => {
    window.m = KocoMotor.ac(document.getElementById("editor"));
    window.girdi = 0; window.m.onInput(() => { window.girdi++; });
    window.çağrılar = []; window.m.setCompleter((r, c, ö, cb) => { window.çağrılar.push([r, c, ö]); cb(adaylar); });
    window.m.setValue(örnek);
  }, {örnek: ÖRNEK, adaylar: ADAYLAR});
  await p.waitForSelector(".cm-content");

  console.log("sözleşme:");
  sav("ac() + setValue/getValue aynı metni verir", (await p.evaluate(() => window.m.getValue())) === ÖRNEK);
  sav("setValue geçmişi sıfırlar: undo hiçbir şey değiştirmez", await p.evaluate(() => { const ö = window.m.getValue(); window.m.undo(); return window.m.getValue() === ö; }));

  // imleç
  const k = await p.evaluate(() => { window.m.moveCursorTo(2, 4); return window.m.getCursorPosition(); });
  sav("moveCursorTo(2,4) -> getCursorPosition {row:2,column:4} (0 tabanlı)", k.row === 2 && k.column === 4, k);
  const k2 = await p.evaluate(() => { window.m.moveCursorTo(1, 999); return window.m.getCursorPosition(); });
  sav("sütun satır sonuna kırpılır", k2.row === 1 && k2.column === "gizle()".length, k2);

  // Türkçe anahtar sözcük dekorasyonu: beklenen sayı Node'da aynı düzenli ifadeyle
  const bekl = (ÖRNEK.match(new RegExp("(?<![\\p{L}\\p{N}_$])(" + TR.join("|") + ")(?![\\p{L}\\p{N}_$])", "gu")) || []).length;
  // sadece görünür satırlar dekore edilir: tüm belgeyi görünür kılmak için büyük bir görünüm
  await p.setViewportSize({width: 412, height: 6000}); await p.waitForTimeout(150);
  const dek = await p.evaluate(() => document.querySelectorAll(".tr-anahtar").length);
  sav("Türkçe anahtar sözcükler dekore (" + bekl + " beklenen)", dek === bekl, dek);
  await p.setViewportSize({width: 412, height: 915});

  // tanılar
  const t = await p.evaluate(() => {
    window.m.setAnnotations([{row: 1, col: 0, text: "deneme hatası", type: "error"}, {row: 3, col: 2, text: "uyarı", type: "warning"}]);
    return {hata: document.querySelectorAll(".cm-lintRange-error").length, uyarı: document.querySelectorAll(".cm-lintRange-warning").length, oluk: document.querySelectorAll(".cm-lint-marker").length};
  });
  sav("setAnnotations: 1 hata + 1 uyarı aralığı, 2 oluk işareti", t.hata === 1 && t.uyarı === 1 && t.oluk === 2, t);
  const t2 = await p.evaluate(() => { window.m.clearAnnotations(); return document.querySelectorAll(".cm-lintRange-error, .cm-lintRange-warning, .cm-lint-marker").length; });
  sav("clearAnnotations hepsini kaldırır", t2 === 0, t2);

  // yazma ve onInput
  await p.evaluate(() => { window.m.moveCursorTo(99999, 0); window.m.focus(); });
  const g0 = await p.evaluate(() => window.girdi);
  await p.keyboard.type("\nçşğıİöü", {delay: 10});
  sav("klavyeyle yazılan Türkçe harfler belgede", (await p.evaluate(() => window.m.getValue())).endsWith("\nçşğıİöü"));
  sav("onInput yazarken ateşlenir", (await p.evaluate(() => window.girdi)) > g0);

  // tamamlama: Türkçe süzgeç
  async function öneriler(önek) {
    await p.keyboard.type("\n" + önek, {delay: 20}); await p.waitForTimeout(350);
    const l = await p.evaluate(() => [...document.querySelectorAll(".cm-tooltip-autocomplete li .cm-completionLabel")].map((e) => e.textContent));
    await p.keyboard.press("Escape");
    return l;
  }
  const i = await öneriler("i"); sav("önek i -> ileri, İleri, iğne (Işık ve ısı değil)", JSON.stringify(i.slice().sort()) === JSON.stringify(["ileri", "ilerleGit", "iğne", "İleri"].sort()), i);
  const I = await öneriler("I"); sav("önek I -> Işık, ışık, ısı (ileri değil)", JSON.stringify(I.slice().sort()) === JSON.stringify(["Işık", "ışık", "ısı"].sort()), I);
  const ı = await öneriler("ı"); sav("önek ı -> Işık, ışık, ısı", JSON.stringify(ı.slice().sort()) === JSON.stringify(["Işık", "ışık", "ısı"].sort()), ı);
  const ç = await p.evaluate(() => window.çağrılar[window.çağrılar.length - 1]);
  sav("tamamlayıcı (row, col, önek) ile çağrılır; son çağrı önek ı, sütun 1", Array.isArray(ç) && ç[2] === "ı" && ç[1] === 1, ç);

  // ekleme: parametreli -> imleç içeride, ilk parametre seçili
  await p.keyboard.type("\nilerle", {delay: 20}); await p.waitForTimeout(350);
  await p.keyboard.press("Enter"); await p.waitForTimeout(100);
  const e1 = await p.evaluate(() => { const s = window.m._view.state; const r = s.selection.main; return {son: s.doc.lineAt(r.anchor).text, seçili: s.sliceDoc(r.from, r.to)}; });
  sav("ilerle + Enter -> ilerleGit(x, y), x seçili", e1.son === "ilerleGit(x, y)" && e1.seçili === "x", e1);
  // parantezsiz imza -> ad eklenir, imleç sonda
  await p.evaluate(() => { window.m.moveCursorTo(99999, 99999); });
  await p.keyboard.type("\nıs", {delay: 20}); await p.waitForTimeout(350);
  await p.keyboard.press("Enter"); await p.waitForTimeout(100);
  const e2 = await p.evaluate(() => { const s = window.m._view.state; const r = s.selection.main; return {son: s.doc.lineAt(r.anchor).text, boş: r.empty, sonda: r.head === s.doc.lineAt(r.head).to}; });
  sav("ıs + Enter -> ısı, imleç sonda, seçim yok", e2.son === "ısı" && e2.boş && e2.sonda, e2);
  // complete(): boş önekte açık istek listeyi açar
  await p.keyboard.type("\n"); await p.evaluate(() => window.m.complete()); await p.waitForTimeout(350);
  const açık = await p.evaluate(() => document.querySelectorAll(".cm-tooltip-autocomplete li").length);
  sav("complete() boş önekte tüm adayları açar (" + ADAYLAR.length + ")", açık === ADAYLAR.length, açık);
  await p.keyboard.press("Escape");

  // Çevir: tek adımlı geri alma
  const önce = await p.evaluate(() => window.m.getValue());
  const y = await p.evaluate(() => { window.m.yazGeriAlinabilir("dez a = 1\n"); const yeni = window.m.getValue(); const k = window.m.getCursorPosition(); window.m.undo(); return {yeni, k, geri: window.m.getValue()}; });
  sav("yazGeriAlinabilir metni değiştirir, imleç 0,0", y.yeni === "dez a = 1\n" && y.k.row === 0 && y.k.column === 0, y.k);
  sav("tek undo önceki metnin tamamını geri getirir", y.geri === önce);

  sav("sayfa hatası yok", hatalar.length === 0, hatalar);
  await b.close();
  console.log(sayı + " sav, " + kalan + " kaldı");
  process.exit(kalan ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
