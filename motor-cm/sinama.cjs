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

  // Ctrl+Enter: defaultKeymap'in "Mod-Enter" -> insertBlankLine'ı FiddleEditor.scala'nın
  // global Derle kısayoluyla çakışıyordu (bir Windows/Linux sınayıcı ölçtü: hem çalıştırıyor
  // hem boş satır ekliyordu). Motor bu tuşa karışmamalı: metin değişmemeli.
  const öncekiDeğer = await p.evaluate(() => window.m.getValue());
  await p.keyboard.press("Control+Enter"); await p.waitForTimeout(50);
  sav("Ctrl+Enter metni değiştirmez (Mod-Enter süzüldü)", (await p.evaluate(() => window.m.getValue())) === öncekiDeğer);

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

  // satır kaydırma seçeneği (Motor.scala: ?wrap=): öntanımlı sarılı, {kaydır:false} kapatır.
  sav("öntanımlı: satırlar sarılı (cm-lineWrapping sınıfı var)", await p.evaluate(() => document.querySelector(".cm-content").classList.contains("cm-lineWrapping")));
  const sarmaKapalı = await p.evaluate(() => {
    const d = document.createElement("div");
    d.style.cssText = "position:absolute;inset:0";
    document.body.appendChild(d);
    const m2 = KocoMotor.ac(d, {kaydır: false});
    return d.querySelector(".cm-content").classList.contains("cm-lineWrapping");
  });
  sav("kaydır:false ile satırlar sarılmaz", !sarmaKapalı);

  // Enter girintisi Ace gibi (önceki satırı kopyala, açıcıdan sonra +1 birim, kapatan açıcıya hizalanır).
  // Öntanımlı CodeMirror girintiyi derinlik x 2 hesaplıyordu: 4'lü betikte `val` satırından Enter 6'ya iniyordu.
  const dört = "def a() = {\n    while (true) {\n        val x = 5\n    }    \n}";
  async function enterSonrası(metin, satır, sütun, sonra) {
    await p.evaluate(([t, r, c]) => { window.m.setValue(t); const s = t.split("\n")[r]; window.m.moveCursorTo(r, c === null ? s.length : c); window.m.focus(); }, [metin, satır, sütun === undefined ? null : sütun]);
    await p.keyboard.press("Enter");
    if (sonra) await p.keyboard.type(sonra);
    return p.evaluate(() => window.m.getValue().split("\n"));
  }
  const g = (l) => l.match(/^ */)[0].length;
  let l1 = await enterSonrası(dört, 2, undefined);
  sav("4'lü betik: val satırından Enter -> 8 boşluk (önceki satırla hizalı)", g(l1[3]) === 8, l1[3]);
  l1 = await enterSonrası(dört, 1, undefined);
  sav("{ ile biten satırdan Enter -> önceki girinti + 1 birim (4 + 2)", g(l1[2]) === 6, l1[2]);
  l1 = await enterSonrası(dört, 3, undefined);
  sav("} satırından Enter -> önceki satırla hizalı (4)", g(l1[4]) === 4, l1[4]);
  l1 = await enterSonrası("def a() = {\n    while (true) {", 1, undefined, "}");
  sav("{ satırından Enter, sonra } yazınca eşleşen { satırının girintisine (4) iner", l1[2] === "    }", l1[2]);
  l1 = await enterSonrası("    f {}", 0, 7);
  sav("{|} arasında Enter: orta satır +1 birim, kapatan açıcının girintisinde", l1.length === 3 && g(l1[1]) === 6 && l1[2] === "    }", l1);
  l1 = await enterSonrası("def a() = {\n    // not {\n}", 1, undefined);
  sav("yorumda biten { girintiyi artırmaz", g(l1[2]) === 4, l1[2]);

  // girinti seçeneği (Motor.scala: ?indent=): öntanımlı 2, {girinti: 4} Tab ve açıcı sonrası Enter'ı 4 yapar.
  async function ikinci(sec, metin, satır, tuşlar) {
    return p.evaluate(([sec, t, r]) => {
      const d = document.createElement("div"); d.style.cssText = "position:absolute;inset:0;background:#fff"; document.body.appendChild(d);
      window.m3 = KocoMotor.ac(d, sec); window.m3.setValue(t); const s = t.split("\n")[r]; window.m3.moveCursorTo(r, s.length); window.m3.focus();
    }, [sec, metin, satır]).then(async () => { for (const k of tuşlar) await p.keyboard.press(k); return p.evaluate(() => window.m3.getValue().split("\n")); });
  }
  let gl = await ikinci({girinti: 4}, "def a() = {", 0, ["Enter"]);
  sav("girinti:4 -> { sonrası Enter 4 boşluk", g(gl[1]) === 4, gl[1]);
  gl = await ikinci({girinti: 4}, "def a() = {\n    while (true) {", 1, ["Enter"]);
  sav("girinti:4, 4'lü betik -> { sonrası Enter 4 + 4 = 8", g(gl[2]) === 8, gl[2]);
  gl = await ikinci({girinti: 4}, "x", 0, ["Home", "Tab"]);
  sav("girinti:4 -> Tab satır başına 4 boşluk koyar", g(gl[0]) === 4, gl[0]);
  gl = await ikinci(undefined, "x", 0, ["Home", "Tab"]);
  sav("öntanımlı -> Tab 2 boşluk", g(gl[0]) === 2, gl[0]);
  gl = await ikinci({girinti: 99}, "x", 0, ["Home", "Tab"]);
  sav("geçersiz girinti (99) -> 2'ye döner", g(gl[0]) === 2, gl[0]);

  sav("sayfa hatası yok", hatalar.length === 0, hatalar);
  await b.close();
  console.log(sayı + " sav, " + kalan + " kaldı");
  process.exit(kalan ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
