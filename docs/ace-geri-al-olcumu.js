// Ace'e metin yazmanın hangi yolu Ctrl+Z geçmişini koruyor? (kojojs-dev#183, "Çevir" düğmesi)
//
// "Çevir" çeviriyi editöre yazıyor ve kullanıcı Ctrl+Z ile geri alabilmeli.
// Ace'in `session.setValue`'su geri alma geçmişini siliyor; aşağıdaki betik dört
// yazma yolunu gerçek Ace'te, başsız Chromium'da dener ve her biri için tek
// Ctrl+Z'nin ne yaptığını yazdırır.
//
// Editör Ace 1.2.4'ü kullanıyor (server/src/main/twirl/views/index.scala.html).
//
// Çalıştırma (bu depoda bağımlılık yok, geçici dizinde kurun):
//   mkdir /tmp/ace && cd /tmp/ace && npm init -y && npm i ace-builds@1.2.4 playwright-core
//   cp <bu dosya> . && KOJO_CHROME=/yol/chromium node ace-geri-al-olcumu.js
//
// Ölçülen sonuç (2026-10, Ace 1.2.4, Chromium 141):
//   A session.setValue                geri alma geçmişi SİLİNİYOR: Ctrl+Z hiçbir şey yapmıyor
//   B editor.setValue(t, -1)          tek Ctrl+Z çeviri öncesi metne döner; ikincisi öncekine
//   C session.getDocument().setValue  aynısı  <- AceMotor.yazGeriAlinabilir bunu kullanıyor
//   D session.replace(tam aralık)     aynısı
const { chromium } = require('playwright-core');

(async () => {
  const b = await chromium.launch({
    executablePath: process.env.KOJO_CHROME || '/opt/pw-browsers/chromium',
    args: ['--no-sandbox']
  });
  const p = await b.newPage();
  await p.setContent('<!doctype html><meta charset="utf-8"><div id="k"></div>');
  await p.addScriptTag({ path: require.resolve('ace-builds/src-min-noconflict/ace.js') });

  const sonuc = await p.evaluate(async () => {
    const bekle = ms => new Promise(r => setTimeout(r, ms));
    const yollar = {
      'A session.setValue': (e, t) => e.getSession().setValue(t),
      'B editor.setValue(t, -1)': (e, t) => e.setValue(t, -1),
      'C session.getDocument().setValue': (e, t) => e.getSession().getDocument().setValue(t),
      'D session.replace(tam aralık)': (e, t) => {
        const s = e.getSession(), d = s.getDocument();
        s.replace({ start: { row: 0, column: 0 }, end: { row: d.getLength() - 1, column: d.getLine(d.getLength() - 1).length } }, t);
      }
    };
    const out = {};
    for (const [ad, yaz] of Object.entries(yollar)) {
      const el = document.createElement('div');
      el.style.cssText = 'height:100px;width:300px';
      document.body.appendChild(el);
      const e = ace.edit(el);
      e.getSession().setOption('useWorker', false);
      e.getSession().setValue('ilk satır\nikinci'); // yükleme: geçmişe girmemeli
      e.insert('X');                                 // kullanıcı bir şey yazıyor
      await bekle(5);
      const once = e.getValue();
      yaz(e, 'ÇEVRİLEN\nkod');
      await bekle(5);
      const sonra = e.getValue();
      e.execCommand('undo'); const geri1 = e.getValue();
      e.execCommand('undo'); const geri2 = e.getValue();
      out[ad] = { once, sonra, geri1, geri2, korunuyor: geri1 === once };
      e.destroy();
    }
    return out;
  });

  for (const [ad, r] of Object.entries(sonuc)) {
    console.log(`${ad.padEnd(36)} geçmiş ${r.korunuyor ? 'KORUNUYOR' : 'silindi   '}  ${JSON.stringify(r)}`);
  }
  await b.close();
  // Beklenen: yalnız A silindi; B, C, D korunuyor.
  const beklenen = Object.entries(sonuc).every(([ad, r]) => r.korunuyor === !ad.startsWith('A '));
  process.exit(beklenen ? 0 : 1);
})();
