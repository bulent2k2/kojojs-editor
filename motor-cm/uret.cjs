#!/usr/bin/env node
// cephe.js -> ../server/src/main/assets/javascript/motor-cm.js (esbuild, tek dosya, minified).
//   node uret.cjs            yaz
//   node uret.cjs --denetle  yazma; commit'li dosyayla bayt bayt karşılaştır, fark varsa 1 ile çık (CI)
// Belirlenimlilik: package-lock.json sürümleri sabitliyor; başlıkta tarih yok, yalnız sürümler.
const esbuild = require("esbuild");
const fs = require("fs"), path = require("path");
const HEDEF = path.join(__dirname, "..", "server", "src", "main", "assets", "javascript", "motor-cm.js");
const kilit = require("./package-lock.json").packages;
const sürüm = (ad) => kilit["node_modules/" + ad].version;
const başlık = "/* ÜRETİLMİŞ DOSYA, ELLE DÜZENLEMEYİN: kojojs-editor/motor-cm/ (node uret.cjs). " +
  "CodeMirror view " + sürüm("@codemirror/view") + ", state " + sürüm("@codemirror/state") +
  ", autocomplete " + sürüm("@codemirror/autocomplete") + ", lint " + sürüm("@codemirror/lint") +
  ", legacy-modes " + sürüm("@codemirror/legacy-modes") + "; esbuild " + sürüm("esbuild") + ". Sözleşme: kojojs-editor#75 */\n";
(async () => {
  const r = await esbuild.build({
    entryPoints: [path.join(__dirname, "cephe.js")], bundle: true, minify: true, format: "iife",
    globalName: "KocoMotor", charset: "utf8", target: ["es2018"], legalComments: "none", write: false, logLevel: "error"
  });
  const çıktı = başlık + r.outputFiles[0].text;
  if (process.argv.includes("--denetle")) {
    const eski = fs.existsSync(HEDEF) ? fs.readFileSync(HEDEF, "utf8") : "";
    if (eski === çıktı) { console.log("aynı: motor-cm.js üreteç çıktısıyla bayt bayt aynı (" + Math.round(çıktı.length / 1024) + " KB)"); return; }
    console.error("FARKLI: server/src/main/assets/javascript/motor-cm.js üreteç çıktısından farklı (commit'li " +
      Math.round(eski.length / 1024) + " KB, üreteç " + Math.round(çıktı.length / 1024) + " KB).\n" +
      "Düzeltmek için: cd motor-cm && npm ci && npm run uret, ve çıkan farkı commit'leyin.");
    process.exit(1);
  }
  fs.writeFileSync(HEDEF, çıktı);
  console.log("yazıldı: " + path.relative(path.join(__dirname, ".."), HEDEF) + " (" + Math.round(çıktı.length / 1024) + " KB)");
})().catch((e) => { console.error(e); process.exit(1); });
