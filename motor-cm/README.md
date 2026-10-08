# motor-cm — CodeMirror 6 motoru

iKoco kod düzenleyicisinin Ace yerine kullanacağı motor (kojojs-editor#75). `cephe.js`,
FiddleEditor.scala'nın Ace'ten kullandığı yüzeyi `window.KocoMotor` olarak verir; esbuild
tek dosyaya paketler: `server/src/main/assets/javascript/motor-cm.js` (**commit'li**).

Neden üretilmiş dosya commit'li: editörün kendi derlemesi (sbt 0.13) npm'den haberdar değil
ve CI'da koşmuyor; dosya `mode-scala.js` gibi `assets/javascript` altından sunuluyor.
CI dosyayı yeniden üretip bayt bayt karşılaştırır (`uretilmis` adımı), listeyi Ace'inkiyle
karşılaştırır ve cepheyi başsız Chrome'da sınar.

```
cd motor-cm
npm ci
npm run uret        # motor-cm.js'yi yaz
npm run denetle     # yeniden üret + karşılaştır; anahtar sözcük listesi
KOJO_CHROME=/yol/chrome npm run sina   # sözleşme sınaması (Playwright)
```

Sürümler `package.json` + `package-lock.json`'da sabit. `node_modules/` kök `.gitignore`'da.

`ornek.kojo` sınamanın sabit girdisi: kojojs-dev `ornekler/18-birim-cember.kojo`'nun başlıksız
bir anlık görüntüsü. Ayna değil; sınama 15 Türkçe anahtar sözcük saymaya dayandığı için bilerek
dondurulmuş, kaynağı değişince burası değişmez.
