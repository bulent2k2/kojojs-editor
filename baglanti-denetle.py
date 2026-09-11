#!/usr/bin/env python3
"""Yardım sayfalarındaki ?zrc= bağlantıları sunucunun URI sınırına sığıyor mu.

  python3 baglanti-denetle.py          # denetle, aşan varsa çıkış 1

NEDEN AYRI BİR SAV: bu bağlantılar ELLE yapıştırılıyor ve uzunlukları kimse
saymıyor. Sınırı aşan bir bağlantı derlemeyi, sınamayı, dağıtımı sessizce
geçiyor; ancak KULLANICI tıkladığında patlıyor:

    HTTP 414 -- URI length exceeds the configured limit of 2048 characters

Eylül 2026'da tam bu oldu: kojoOgren'deki "XOX -- yenilmez" kartının bağlantısı
3698 karakterdi (sınırın 1650 üstü) ve yayına çıktı. 618 bağlantı içinde tek
bozuk olan oydu; elle bulunamayacak bir iğneydi.

SINIR NEREDEN: editör (Play/akka-http) `akka.http.server.parsing.max-uri-length`
varsayılanında, yani 2048. Ölçüldü: URI 2048 -> 200, 2049 -> 414. Kardeş
servisler bunu 64k'ya çıkarmış (kojojs-core router ve compiler-server
reference.conf), editör atlanmış. Editör de yükseltilirse aşağıdaki SINIR
oradaki değerle birlikte güncellenmeli.
"""
import glob
import os
import re
import sys

SINIR = 2048
DIZIN = "server/src/main/twirl/views"

# href mutlak yazılırsa ("https://ikojo.fly.dev/?zrc=...") sunucunun gördüğü
# istek URI'si yalnız yol+sorgudur; şema ve konağı sayarsak yanlış alarm veririz.
ONEK = re.compile(r"^[a-zA-Z][a-zA-Z0-9+.-]*://[^/]*")


def uri_uzunluk(href: str) -> int:
    return len(ONEK.sub("", href))


def main() -> int:
    kok = os.path.dirname(os.path.abspath(__file__))
    desen = os.path.join(kok, DIZIN, "*.html")
    dosyalar = sorted(glob.glob(desen))
    if not dosyalar:
        print("hata: görünüm dosyası yok: %s" % desen, file=sys.stderr)
        return 2

    toplam = 0
    tasanlar = []
    for yol in dosyalar:
        metin = open(yol, encoding="utf-8").read()
        for m in re.finditer(r'href="([^"]*\?zrc=[A-Za-z0-9_\-]+=*)"', metin):
            toplam += 1
            u = uri_uzunluk(m.group(1))
            if u > SINIR:
                satir = metin[: m.start()].count("\n") + 1
                tasanlar.append((os.path.basename(yol), satir, u))

    if not toplam:
        print("hata: hiç ?zrc= bağlantısı bulunamadı -- desen bozulmuş olabilir", file=sys.stderr)
        return 2

    if tasanlar:
        print("hata: %d bağlantı URI sınırını (%d) aşıyor:" % (len(tasanlar), SINIR), file=sys.stderr)
        for ad, satir, u in tasanlar:
            print("       %s:%d  URI %d karakter (%d fazla)" % (ad, satir, u, u - SINIR), file=sys.stderr)
        print("       Kullanıcı tıklayınca HTTP 414 alır.", file=sys.stderr)
        print("       Çare: kodu kısaltın ya da kayıtlı yazılımcık bağlantısı kullanın (/sf/<id>/<sürüm>).", file=sys.stderr)
        return 1

    enUzun = max(
        (uri_uzunluk(m.group(1)) for yol in dosyalar
         for m in re.finditer(r'href="([^"]*\?zrc=[A-Za-z0-9_\-]+=*)"', open(yol, encoding="utf-8").read())),
        default=0,
    )
    print("aynı: %d ?zrc= bağlantısının hepsi sınıra sığıyor (en uzunu %d / %d)" % (toplam, enUzun, SINIR))
    return 0


if __name__ == "__main__":
    sys.exit(main())
