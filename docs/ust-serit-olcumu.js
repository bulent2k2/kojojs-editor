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
// Tek satırlı bir düğme ~37-40px yüksekliğinde; flex ile daralıp yazısı simgenin altına kırılan düğme ~50px.
// (İlk sürüm yalnız "sağ kenar taşıyor mu" ve "başlıktan (55px) yüksek mi" diye bakıyordu: kırılan düğme
// ikisini de geçiyor ve "sığıyor" görünüyordu.)
const TEK_SATIR = 44;
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
// Simge sınıfları Icon.scala'dan okunur (elle yazılı ad kopyası ayrışmasın): "ellipsis h" Semantic'te
// kutulu H (h-square) çiziyordu ve mock bunu yakalamadı çünkü adı elle yazmıştı.
const iconScala = fs.readFileSync(path.join(REPO, 'client/src/main/scala/scalafiddle/client/component/Icon.scala'), 'utf8');
const ikonAdi = (def) => { const m = iconScala.match(new RegExp('def ' + def + '\\s*=\\s*apply\\("([^"]+)"\\)')); if (!m) throw new Error('Icon.scala\'da yok: ' + def); return m[1]; };
const ikAd = { play: 'play', stop: 'stop', 'pencil square': 'pencilSquare', 'code fork': 'codeFork', save: 'save',
               language: 'language', 'caret down': 'caretDown', book: 'book', 'ellipsis h': 'ellipsisH' };
const ik = n => `<i class="icon ${ikAd[n] ? ikonAdi(ikAd[n]) : n}"></i>`;
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
    ? `<div class="userinfo"><div class="ui dropdown top right pointing ${acikMenu === 'avatar' ? 'active visible' : ''}"><div class="username"><img class="author" src="${GIF}" width=42 height=42><span class="etiket">Ad Soyad</span></div>${acikMenu === 'avatar' ? menu('Betiklerim', 'Çıkış yap') : ''}</div><a class="cikis"><div class="ui basic button">Çıkış yap</div></a></div>`
    : `<a><div class="ui button login" title="GitHub ile giriş yap"><img src="${GIF}" width=20 height=20><span class="etiket">GitHub ile giriş yap</span></div></a>`;
  return `<!doctype html><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1"><link rel=stylesheet href="main.css"><body>
<div class="full-screen"><header><div class="left"><div class="logo"><a class="logo-tam" href="#"><img src="scalafiddle-logo.png"></a><a class="logo-daire" href="#x" title="Ana sayfa" aria-label="Ana sayfa"></a></div>${sol}</div>
<div class="right"><a class="ui basic button" title="Yardım / Belgeler">${ik('book')}<span class="etiket">Yardım / Belgeler</span></a>${kullanici}</div></header></div>`;
}

// KOJO_TARAMA=1: savlar yerine 300-1400px arasını 10'ar px tarar ve her durum için SIĞMAYAN
// aralıkları yazar (eşik seçmek için; CI'da koşmaz).
async function tara(b) {
  const durumlar = [['giriş yok, kayıtsız', false, false], ['giriş yok, kayıtlı', false, true],
                    ['giriş var, kayıtsız', true, false], ['giriş var, kayıtlı', true, true]];
  for (const [ad, girisVar, kayitli] of durumlar) {
    fs.writeFileSync('serit.html', serit({ girisVar, kayitli }));
    const p = await b.newPage({ viewport: { width: 400, height: 300 } });
    await p.goto('file://' + path.resolve('serit.html'));
    const sigmayan = [];
    for (let w = 300; w <= 1400; w += 10) {
      await p.setViewportSize({ width: w, height: 300 });
      const t = await p.evaluate((tek) => {
        const h = document.querySelector('header');
        const sag = Math.max(...[...h.querySelectorAll('*')].filter(e => e.offsetParent !== null && !e.closest('.etiket') && getComputedStyle(e).position !== 'absolute').map(e => e.getBoundingClientRect().right));
        const dugme = Math.max(...[...h.querySelectorAll('.ui.button')].filter(e => e.offsetParent !== null).map(e => e.getBoundingClientRect().height));
        return sag - innerWidth > 1 || dugme > tek;
      }, TEK_SATIR);
      if (t) sigmayan.push(w);
    }
    // ardışık aralıklara topla
    const aralik = []; for (const w of sigmayan) { const son = aralik[aralik.length - 1]; if (son && w - son[1] === 10) son[1] = w; else aralik.push([w, w]); }
    console.log(`${ad.padEnd(22)} sığmayan: ${aralik.map(([x, y]) => x === y ? x : x + '-' + y).join(', ') || 'yok'}`);
    await p.close();
  }
}

(async () => {
  const b = await chromium.launch({ executablePath: process.env.KOJO_CHROME || '/opt/pw-browsers/chromium', args: ['--no-sandbox'] });
  if (process.env.KOJO_TARAMA) { await tara(b); await b.close(); return; }
  const durumlar = [
    { ad: 'giriş yok, kayıtsız', girisVar: false, kayitli: false },
    { ad: 'giriş yok, kayıtlı', girisVar: false, kayitli: true },
    { ad: 'giriş var, kayıtsız', girisVar: true, kayitli: false },
    { ad: 'giriş var, kayıtlı', girisVar: true, kayitli: true }
  ];
  // Zorunlu: Pixel 11 Pro dikey (≈420) ve yatay (≈873), 412/390 (yaygın telefonlar), 1024 (tablet yatay) ve
  // üç kademenin sınırları (840/841 kısa|orta, 1220/1221 orta|geniş).
  // Bilgi amaçlı: 360, 320 (dar telefonlar; sığması şart koşulmuyor, rakam yazılıyor).
  const zorunlu = [1400, 1280, 1221, 1220, 1100, 1024, 960, 873, 841, 840, 800, 700, 600, 420, 412, 390];
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
      if (zorunlu.includes(w)) kontrol(`${d.ad} @${w}px: düğmeler tek satır (en yüksek ${r.enYuksek}px ≤ ${TEK_SATIR}px; yazı simgenin altına kırılmıyor)`, r.enYuksek <= TEK_SATIR);
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

  // Simgeler gerçekten glif çiziyor ve ⋯ kutulu H (\\f0fd) değil, yatay üç nokta (\\f141).
  fs.writeFileSync('serit.html', serit({ girisVar: true, kayitli: true }));
  {
    const p = await b.newPage({ viewport: { width: 420, height: 400 } });
    await p.goto('file://' + path.resolve('serit.html'));
    const g = await p.evaluate(() => [...document.querySelectorAll('header i.icon')].filter(i => i.offsetParent !== null)
      .map(i => ({ sinif: i.className, icerik: getComputedStyle(i, '::before').content })));
    const bos = g.filter(x => x.icerik === 'none' || x.icerik === '""' || x.icerik === 'normal');
    const kod = x => [...x].map(c => c.codePointAt(0).toString(16)).join(' ');
    kontrol(`dar ekranda görünen ${g.length} simgenin hepsi glif çiziyor`, bos.length === 0, JSON.stringify(bos.map(x => ({ sinif: x.sinif, icerik: kod(x.icerik) }))));
    const uc = g.find(x => /ellipsis/.test(x.sinif));
    kontrol('⋯ simgesi yatay üç nokta (\\f141), kutulu H (\\f0fd) değil', !!uc && uc.icerik === '"\uf141"', uc ? uc.sinif + ' -> ' + kod(uc.icerik) : 'simge yok');
    await p.close();
  }

  // Kademeler: orta (873) düğme yazıları görünür ve Güncelle/Çatalla "⋯" menüsünde; kısa (420) yazı yok.
  fs.writeFileSync('serit.html', serit({ girisVar: true, kayitli: true }));
  for (const [w, yaziBeklenen] of [[873, true], [841, true], [840, false], [420, false]]) {
    const p = await b.newPage({ viewport: { width: w, height: 400 } });
    await p.goto('file://' + path.resolve('serit.html'));
    const r = await p.evaluate(() => {
      const gor = s => [...document.querySelectorAll(s)].filter(e => e.offsetParent !== null).length;
      const yazi = [...document.querySelectorAll('header .etiket')].filter(e => e.getBoundingClientRect().width > 5).length;
      return { yazi, ayri: gor('.genis-ekran'), daha: gor('.dar-ekran') };
    });
    kontrol(`${w}px: düğme yazıları ${yaziBeklenen ? 'görünür' : 'gizli'} (${r.yazi} etiket)`, yaziBeklenen ? r.yazi >= 4 : r.yazi === 0, JSON.stringify(r));
    kontrol(`${w}px: Güncelle/Çatalla "⋯" menüsünde (ayrı düğme yok)`, r.ayri === 0 && r.daha >= 2, JSON.stringify(r));
    await p.close();
  }

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
                                       ['⋯ (Güncelle/Çatalla) menüsü', 'daha', { girisVar: true, kayitli: true }],
                                       ['Avatar (Betiklerim) menüsü', 'avatar', { girisVar: true, kayitli: false }]]) {
    fs.writeFileSync('serit.html', serit({ ...durum, acikMenu }));
    for (const w of [420, 873]) {
      const p = await b.newPage({ viewport: { width: w, height: 400 } });
      await p.goto('file://' + path.resolve('serit.html'));
      const r = await p.evaluate(() => {
        const m = document.querySelector('header .ui.dropdown > .menu'); const q = m.getBoundingClientRect();
        return { sol: Math.round(q.left), sag: Math.round(q.right), ust: Math.round(q.top), alt: Math.round(q.bottom), iw: innerWidth, ih: innerHeight };
      });
      kontrol(`${ad} @${w}px ekranın içinde (sol ${r.sol}, sağ ${r.sag}/${r.iw}, üst ${r.ust}, alt ${r.alt}/${r.ih})`,
              r.sol >= 0 && r.sag <= r.iw && r.ust >= 0 && r.alt <= r.ih);
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
