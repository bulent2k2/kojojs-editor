#!/bin/bash
# kojojs-dev/lib altındaki JS kütüphanelerini bu deponun varlıklarına kopyalar.
#
# Neden betik: sözlükte olan burada da olabilir (bkz. sync-sozluk.sh). Bu
# kütüphaneler İKİ depoda birden duruyor -- kojojs-dev'de hem lib/ hem
# src/test/resources/ altında, burada assets/javascript altında -- ve CI
# depolar arasını göremiyor. PIXI'de bir kez ısırdı: sınama harness'ı uzun
# süre PIXI 4'te kalınca PixiUyum.beşVeÜstü hep false döndü ve bütün doku
# dolgusu yolu sessizce sınanmadan kaldı. Aynı sınıf libtess için de geçerli:
# sunulan kütüphane sınananla ayrışırsa canlı site başka bir şey koşar.
#
#   ./sync-lib.sh [kojojs-dev dizini]        # kopyala   (varsayılan ../kojojs-dev)
#   ./sync-lib.sh --denetle [dizin]          # yalnız karşılaştır, fark varsa 1 döner
#
# DİKKAT: adlar iki depoda aynı değil (kojojs-dev'de pixi5.min.js, burada
# pixi.min.js), o yüzden düz bir ad listesi değil ÇİFT listesi tutuyoruz.
set -eu

DENETLE=0
if [ "${1:-}" = "--denetle" ]; then DENETLE=1; shift; fi
KAYNAK="${1:-../kojojs-dev}/lib"
HEDEF="$(dirname "$0")/server/src/main/assets/javascript"

[ -d "$KAYNAK" ] || { echo "kaynak dizin yok: $KAYNAK" >&2; exit 2; }

# kaynaktaki ad : buradaki ad
# pixi5.min.js.map: PIXI 5'in kaynak haritası. Burada sunuluyor ve pixi.min.js
# sonundaki "//# sourceMappingURL=pixi.min.js.map" işareti sayesinde tarayıcı
# devtools'ta onu çekiyor. Uzun süre senkron zincirinin DIŞINDAYDI: kaynak
# tarafta karşılığı yoktu, yani PIXI bir dahaki yükseltmede js tazelenir, harita
# sessizce eskir ve hata ayıklarken yanlış kaynak gösterirdi. Kaynağa alındı
# (kojojs-dev lib/pixi5.min.js.map) ve buraya çift olarak eklendi.
CIFTLER="
pixi5.min.js:pixi.min.js
pixi5.min.js.map:pixi.min.js.map
libtess.cat.js:libtess.cat.js
jsts.min.js:jsts.min.js
howler.min.js:howler.min.js
"

farkli=0
for cift in $CIFTLER; do
  kad="${cift%%:*}"
  had="${cift##*:}"
  [ -f "$KAYNAK/$kad" ] || { echo "kaynakta yok: $KAYNAK/$kad" >&2; exit 2; }
  if [ -f "$HEDEF/$had" ] && cmp -s "$KAYNAK/$kad" "$HEDEF/$had"; then
    echo "aynı      : $kad -> $had"
  else
    farkli=1
    if [ "$DENETLE" = 1 ]; then
      echo "FARKLI    : $kad -> $had"
    else
      cp "$KAYNAK/$kad" "$HEDEF/$had"
      echo "kopyalandı: $kad -> $had"
    fi
  fi
done

if [ "$DENETLE" = 1 ] && [ "$farkli" = 1 ]; then
  echo "Sunulan kütüphane kaynakla aynı değil; ./sync-lib.sh ile tazeleyin." >&2
  exit 1
fi
