// Üst şerit telefonda sığıyor mu? (kojojs-dev#183 sonrası, "Çevir" düğmesi şeridi taşırdı)
//
// Gerçek editör bu ortamda/CI'da derlenemiyor (sbt 0.13, Scala.js 0.6). Bu betik
// GERÇEK `main.less`'i (Semantic UI 2.3.1 ve Font Awesome ile) derler, şeridin DOM'unu
// FiddleEditor.scala / UserLogin.scala'dakiyle aynı sınıflarla kurar ve başsız
// Chromium'da ölçer.
//
// BİLİNEN SINIR: DOM burada elle yazılı bir KOPYA. Kopya ile Scala ayrışırsa
// ölçüm eskimiş şeride bakar. Bunu kısmen denetliyoruz: kopyada kullanılan sınıf ve
// öznitelikler Scala kaynağında da geçmeli (aşağıda `kaynakDenetimi`). Sınıf ADI
// kalıp YAPI değişirse (ör. düğme başka kaba taşınırsa) yakalanmaz.
//
// Ölçümün dayanağı (2026-10, Pixel 11 Pro, CSS genişliği düğme boyutlarından çıkarılan
// ≈420px dikey, ≈873px yatay): mock'un gerçek ekran görüntüleriyle karşılaştırması:
//   giriş yok, betik kayıtsız: mock 143px taşma, ekranda ~150px
//   giriş var, betik kayıtsız: mock 19px taşma, ekranda ~17px
// Mock'un ilk sürümünde logo 40px genişlikteydi (gerçeği 102px) ve taşmayı ~55px az
// gösteriyordu; ekran görüntüsü bunu ortaya çıkardı.
//
// Çalıştırma (bu depoda bağımlılık yok, geçici dizinde kurun):
//   mkdir /tmp/ust && cd /tmp/ust && npm init -y
//   npm i semantic-ui-css@2.3.1 font-awesome@4.7.0 less@4 playwright-core
//   cp <bu dosya> . && KOJO_REPO=/yol/kojojs-editor KOJO_CHROME=/yol/chromium node ust-serit-olcumu.js
const { chromium } = require('playwright-core');
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const REPO = process.env.KOJO_REPO || path.resolve(__dirname, '..');
const KOK = process.cwd();
const sav = [];
const kontrol = (ad, tamam, ayrinti = '') => { sav.push({ ad, tamam, ayrinti }); };

// ---- 1. Scala kaynağıyla sınıf eşlemesi (kopya sürüklenmesine karşı) -----------
const scala = ['FiddleEditor.scala', 'UserLogin.scala']
  .map(f => fs.readFileSync(path.join(REPO, 'client/src/main/scala/scalafiddle/client/component', f), 'utf8')).join('\n');
for (const s of ['logo-tam', 'logo-daire', 'genis-ekran', 'dar-ekran', 'etiket', 'ui basic button', 'ui button login',
                 'aria-label', 'Güncelle / Çatalla']) {
  kontrol(`Scala kaynağında "${s}" var`, scala.includes(s));
}

// ---- 2. Gerçek main.less'i derle -------------------------------------------------
const less = path.join(REPO, 'server/src/main/assets/stylesheets/main.less');
fs.mkdirSync('lib/Semantic-UI', { recursive: true });
fs.mkdirSync('lib/font-awesome', { recursive: true });
fs.copyFileSync('node_modules/semantic-ui-css/semantic.min.css', 'lib/Semantic-UI/semantic.min.css');
fs.cpSync('node_modules/font-awesome/less', 'lib/font-awesome/less', { recursive: true });
fs.cpSync('node_modules/font-awesome/fonts', 'lib/font-awesome/fonts', { recursive: true });
fs.copyFileSync(less, 'main.less');
execFileSync('node_modules/.bin/lessc', ['--js', 'main.less', 'main.css'], { stdio: ['ignore', 'ignore', 'ignore'] });
fs.copyFileSync(path.join(REPO, 'server/src/main/assets/images/scalafiddle-logo.png'), 'scalafiddle-logo.png');

// ---- 3. Şeridin DOM'u (FiddleEditor.render ile aynı sınıflar) --------------------
const ik = n => `<i class="icon ${n}"></i>`;
const GIF = 'data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==';
const dugme = (n, et, ek = '') =>
  `<div class="ui basic button ${ek}" title="${et}">${ik(n)}<span class="etiket">${et}</span></div>`;
const menu = (...ogeler) => `<div class="ui vertical menu" style="display:block">${ogeler.map(o => `<a class="item">${o}</a>`).join('')}</div>`;
const acik = (ad, icerik, menuIcerik, ac) =>
  `<div class="ui dropdown ${ad} ${ac ? 'active visible' : ''}">${icerik}${ac ? menuIcerik : ''}</div>`;

function serit({ girisVar, kayitli, acikMenu }) {
  const cevirDar = acik('basic button',
    `<span title="Çevir">${ik('language')}<span class="etiket">Çevir</span></span>`,
    menu('Otomatik (yönü betikten bul)', 'İngilizceye çevir', 'Türkçeye çevir'), acikMenu === 'cevir');
  const dahaFazla = acik('basic button', `<span title="Güncelle / Çatalla">${ik('ellipsis h')}</span>`,
    menu('Güncelle', 'Çatalla'), acikMenu === 'daha');
  const sol = [
    dugme('play', 'Çalıştır'), dugme('stop', 'Durdur/Yenile'),
    kayitli ? dugme('pencil square', 'Güncelle', 'genis-ekran') + dugme('code fork', 'Çatalla', 'genis-ekran')
              + `<span class="dar-ekran">${dahaFazla}</span>`
            : dugme('save', 'Kaydet'),
    dugme('language', 'Çevir', 'genis-ekran'),
    `<span class="genis-ekran"><div class="ui dropdown basic button"><span>${ik('caret down')}</span></div></span>`,
    `<span class="dar-ekran">${cevirDar}</span>`
  ].join('');
  const kullanici = girisVar
    ? `<div class="userinfo"><div class="ui dropdown top right pointing"><div class="username"><img class="author" src="${GIF}" width=42 height=42><span class="etiket">Ad Soyad</span></div></div><a class="cikis"><div class="ui basic button">Çıkış yap</div></a></div>`
    : `<a><div class="ui button login" title="GitHub ile giriş yap"><img src="${GIF}" width=20 height=20><span class="etiket">GitHub ile giriş yap</span></div></a>`;
  return `<!doctype html><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1"><link rel=stylesheet href="main.css"><body>
<div class="full-screen"><header><div class="left"><div class="logo"><a class="logo-tam" href="#"><img src="scalafiddle-logo.png"></a><a class="logo-daire" href="#x" title="Ana sayfa" aria-label="Ana sayfa"></a></div>${sol}</div>
<div class="right"><a class="ui basic button" title="Yardım / Belgeler">${ik('book')}<span class="etiket">Yardım / Belgeler</span></a>${kullanici}</div></header></div>`;
}

(async () => {
  const b = await chromium.launch({ executablePath: process.env.KOJO_CHROME || '/opt/pw-browsers/chromium', args: ['--no-sandbox'] });
  const durumlar = [
    { ad: 'giriş yok, kayıtsız', girisVar: false, kayitli: false },
    { ad: 'giriş yok, kayıtlı', girisVar: false, kayitli: true },
    { ad: 'giriş var, kayıtsız', girisVar: true, kayitli: false },
    { ad: 'giriş var, kayıtlı', girisVar: true, kayitli: true }
  ];
  // Zorunlu: Pixel 11 Pro dikey (≈420) ve yatay (≈873), 412 (yaygın telefon), 960 (eşik).
  // Bilgi amaçlı: 360, 320 (dar telefonlar; sığması şart koşulmuyor, rakam yazılıyor).
  const zorunlu = [960, 873, 420, 412, 390];
  const bilgi = [360, 320];
  const tablo = [];
  for (const d of durumlar) {
    fs.writeFileSync('serit.html', serit(d));
    for (const w of [...zorunlu, ...bilgi]) {
      const p = await b.newPage({ viewport: { width: w, height: 400 } });
      await p.goto('file://' + path.resolve('serit.html'));
      const r = await p.evaluate(() => {
        const h = document.querySelector('header');
        const sag = Math.max(...[...h.querySelectorAll('*')]
          .filter(e => e.offsetParent !== null && !e.closest('.etiket') && getComputedStyle(e).position !== 'absolute')
          .map(e => e.getBoundingClientRect().right));
        const dugmeler = [...h.querySelectorAll('.ui.button')].filter(e => e.offsetParent !== null).map(e => e.getBoundingClientRect());
        return { tasma: Math.round(sag - innerWidth), baslikH: h.getBoundingClientRect().height,
                 enYuksek: Math.round(Math.max(...dugmeler.map(r => r.height))) };
      });
      tablo.push({ durum: d.ad, w, ...r });
      if (zorunlu.includes(w)) kontrol(`${d.ad} @${w}px: şerit sığıyor (taşma ${r.tasma}px)`, r.tasma <= 1);
      if (zorunlu.includes(w)) kontrol(`${d.ad} @${w}px: düğmeler başlığa sığıyor (${r.enYuksek} ≤ ${r.baslikH})`, r.enYuksek <= r.baslikH);
      await p.close();
    }
  }

  // Logo: dar ekranda yalnız kırmızı daire bağlantı, geniş ekranda bütün logo bağlantı.
  fs.writeFileSync('serit.html', serit({ girisVar: true, kayitli: false }));
  const logo = async (w) => {
    const p = await b.newPage({ viewport: { width: w, height: 400 } });
    await p.goto('file://' + path.resolve('serit.html'));
    const r = await p.evaluate(() => {
      const img = document.querySelector('.logo img').getBoundingClientRect();
      const baglanti = (x, y) => { const e = document.elementFromPoint(x, y); const a = e && e.closest('a'); return a ? a.className || 'a' : null; };
      return {
        daire: baglanti(img.left + img.width * 0.31, img.top + img.height * 0.5),   // kırmızı dairenin ortası
        yaziOjo: baglanti(img.left + img.width * 0.85, img.top + img.height * 0.5), // "ojo" yazısı
        i: baglanti(img.left + img.width * 0.05, img.top + img.height * 0.5),       // soldaki "i"
        w: Math.round(img.width)
      };
    });
    await p.close(); return r;
  };
  const dar = await logo(420), genis = await logo(1280);
  kontrol('dar ekran: kırmızı daireye dokunmak ana sayfa bağlantısı', dar.daire === 'logo-daire', JSON.stringify(dar));
  kontrol('dar ekran: "ojo" yazısına dokunmak bağlantı DEĞİL', dar.yaziOjo === null, JSON.stringify(dar));
  kontrol('dar ekran: "i" harfine dokunmak bağlantı DEĞİL', dar.i === null, JSON.stringify(dar));
  kontrol('geniş ekran: bütün logo bağlantı (daire, ojo, i)', genis.daire === 'logo-tam' && genis.yaziOjo === 'logo-tam' && genis.i === 'logo-tam', JSON.stringify(genis));

  // Geniş ekran (masaüstü) değişmedi: yazılı düğmeler ve ayrı ▾ görünür, dar-ekran öğeleri gizli.
  fs.writeFileSync('serit.html', serit({ girisVar: true, kayitli: true }));
  {
    const p = await b.newPage({ viewport: { width: 1280, height: 400 } });
    await p.goto('file://' + path.resolve('serit.html'));
    const r = await p.evaluate(() => {
      const gor = s => [...document.querySelectorAll(s)].filter(e => e.offsetParent !== null).length;
      const yazi = [...document.querySelectorAll('header .etiket')].filter(e => e.getBoundingClientRect().width > 5).length;
      return { genis: gor('.genis-ekran'), dar: gor('.dar-ekran'), yazi };
    });
    kontrol(`1280px: yalnız-geniş öğeler görünür (${r.genis}), yalnız-dar gizli (${r.dar})`, r.genis >= 3 && r.dar === 0, JSON.stringify(r));
    kontrol(`1280px: düğme yazıları görünür (${r.yazi} etiket)`, r.yazi >= 5, JSON.stringify(r));
    await p.close();
  }

  // Açılan menüler görünür alanın içinde kalıyor mu?
  for (const [ad, acikMenu, durum] of [['Çevir menüsü', 'cevir', { girisVar: true, kayitli: false }],
                                       ['⋯ (Güncelle/Çatalla) menüsü', 'daha', { girisVar: true, kayitli: true }]]) {
    fs.writeFileSync('serit.html', serit({ ...durum, acikMenu }));
    for (const w of [420, 873]) {
      const p = await b.newPage({ viewport: { width: w, height: 400 } });
      await p.goto('file://' + path.resolve('serit.html'));
      const r = await p.evaluate(() => {
        const m = document.querySelector('header .ui.dropdown > .menu'); const q = m.getBoundingClientRect();
        return { sol: Math.round(q.left), sag: Math.round(q.right), ust: Math.round(q.top), iw: innerWidth };
      });
      kontrol(`${ad} @${w}px ekranın içinde (sol ${r.sol}, sağ ${r.sag}/${r.iw})`, r.sol >= 0 && r.sag <= r.iw);
      await p.close();
    }
  }
  await b.close();

  console.log('durum                  genişlik  taşma(px)  en yüksek düğme / başlık');
  for (const t of tablo) console.log(`${t.durum.padEnd(22)} ${String(t.w).padStart(5)}px  ${String(t.tasma).padStart(6)}      ${t.enYuksek} / ${t.baslikH}`);
  console.log('');
  let kirik = 0;
  for (const s of sav) { if (!s.tamam) kirik++; console.log(`${s.tamam ? 'GEÇTİ ' : 'KIRIK '} ${s.ad}${s.tamam ? '' : '  ' + s.ayrinti}`); }
  console.log(`\n${sav.length - kirik}/${sav.length} sav geçti`);
  process.exit(kirik ? 1 : 0);
})();
