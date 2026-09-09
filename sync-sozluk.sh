#!/bin/bash
# Sözlüğü kojojs-dev'den bu depoya kopyalar.
#
# Neden betik: assets/sozluk altındaki kopyanın kaynakla aynı kaldığını hiçbir
# şey denetlemiyordu; kopyalama elle yapıldığı için bir turda unutuldu ve
# sunulan sözlük 754 girdide kaldı, kaynak 975'e çıkmışken. Bu betik hem
# kopyalıyor hem de --denetle ile yalnız fark olup olmadığını söylüyor.
#
#   ./sync-sozluk.sh [kojojs-dev dizini]      # kopyala   (varsayılan ../kojojs-dev)
#   ./sync-sozluk.sh --denetle [dizin]        # yalnız karşılaştır, fark varsa 1 döner
set -eu

DENETLE=0
if [ "${1:-}" = "--denetle" ]; then DENETLE=1; shift; fi
KAYNAK="${1:-../kojojs-dev}/sozluk"
HEDEF="$(dirname "$0")/server/src/main/assets/sozluk"

[ -d "$KAYNAK" ] || { echo "kaynak dizin yok: $KAYNAK" >&2; exit 2; }

farkli=0
for f in koco-sozlugu.html ornekler.json; do
  [ -f "$KAYNAK/$f" ] || { echo "kaynakta yok: $KAYNAK/$f" >&2; exit 2; }
  if [ -f "$HEDEF/$f" ] && cmp -s "$KAYNAK/$f" "$HEDEF/$f"; then
    echo "aynı      : $f"
  else
    farkli=1
    if [ "$DENETLE" = 1 ]; then
      echo "FARKLI    : $f"
    else
      cp "$KAYNAK/$f" "$HEDEF/$f"
      echo "kopyalandı: $f"
    fi
  fi
done

if [ "$DENETLE" = 1 ] && [ "$farkli" = 1 ]; then
  echo "Sunulan kopya kaynakla aynı değil; ./sync-sozluk.sh ile tazeleyin." >&2
  exit 1
fi
