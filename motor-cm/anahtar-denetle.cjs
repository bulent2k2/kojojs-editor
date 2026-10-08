#!/usr/bin/env node
// tr-anahtar.json (CodeMirror dekorasyonu) ile mode-scala.js'teki (Ace, Türkçe yamalı) anahtar
// sözcükler aynı mı. İki liste ayrı düşerse iki motor farklı sözcükleri vurgular; bu sessiz kalmasın.
//
// NASIL: yamalı mode-scala.js'nin listesi = stok Ace 1.2.4 İngilizce listesi + Türkçe eklemeler.
// Stok liste ace-stok-anahtar.json'da anlık görüntü (ace-builds@1.2.4 src-min-noconflict/mode-scala.js'ten).
// Türkçe = yamalı − stok; bu küme tr-anahtar.json ile İKİ YÖNDE aynı olmalı. (İlk sürüm yalnız ASCII
// dışı sözcüklere bakıyordu ve `dez` silinince yeşil kalıyordu -- ölçüldü; bu yüzden küme farkı.)
const fs = require("fs"), path = require("path");
const oku = (p) => fs.readFileSync(p, "utf8");
const ace = oku(path.join(__dirname, "..", "server", "src", "main", "assets", "javascript", "mode-scala.js"));
const m = ace.match(/var e="(case\|default\|[^"]*)",t="([^"]*)"/);
if (!m) { console.error("HATA: mode-scala.js içinde anahtar sözcük dizgisi bulunamadı (desen değişmiş olabilir)"); process.exit(2); }
const yamalı = new Set(m[1].split("|").concat(m[2].split("|")));
const stok = new Set(JSON.parse(oku(path.join(__dirname, "ace-stok-anahtar.json"))));
const json = JSON.parse(oku(path.join(__dirname, "tr-anahtar.json")));
const jsonKüme = new Set(json);
const stokEksik = [...stok].filter((w) => !yamalı.has(w));
if (stokEksik.length) { console.error("HATA: stok Ace sözcükleri yamalı mode-scala.js'te yok (anlık görüntü bayat?): " + stokEksik.join(", ")); process.exit(2); }
const aceTürkçe = [...yamalı].filter((w) => !stok.has(w));
const eksik = aceTürkçe.filter((w) => !jsonKüme.has(w));   // Ace'te var, JSON'da yok
const fazla = json.filter((w) => !yamalı.has(w) || stok.has(w)); // JSON'da var, Ace'in Türkçesinde yok
if (eksik.length || fazla.length) {
  if (eksik.length) console.error("mode-scala.js'te olup tr-anahtar.json'da olmayan: " + eksik.join(", "));
  if (fazla.length) console.error("tr-anahtar.json'da olup mode-scala.js'in Türkçesinde olmayan: " + fazla.join(", "));
  process.exit(1);
}
console.log("aynı: " + json.length + " Türkçe anahtar sözcük iki motorda da aynı (yamalı " + yamalı.size + " − stok " + stok.size + ")");
