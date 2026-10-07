// Bulent Basaran 2022; telefon ekranlarına uyacak şekilde düzenlendi 2023
gizle()
dez (rb, rc) = (2.0, 40) // küçük topun yarıçapı ve dairesel yörüngesinin yarıçapı
tuvaliYakınlaştır(1, 1, 35, -75)
dez (cBall, cCos, cSin) = (mavi, kırmızı, yeşil)
dez ball = öteleme(-rc, rc) * boyaRengi(cBall) * kalemRengi(cBall) * kalemKalınlığı(4) -> Resim.daire(rb)
dez yörünge = öteleme(-2 * rc, rc) * kalemRengi(gri) * kalemKalınlığı(0.5) -> Resim.daire(rc)
dez (eksenRengi, eksen2) = (kalemRengi(yeşil.soluk(0.8)), kalemRengi(pembe))
dez merkez = öteleme(-2 * rc, rc) * kalemRengi(mor) -> Resim.daire(1.2 * rb) // daire/yörünge merkezi
çiz(öteleme(0, -0.8 * rc) * eksenRengi -> Resim.çizgi(0, 3.8 * rc)) // sinüsün dikey ekseni
çiz(öteleme(-4 * rc, rc) * eksenRengi -> Resim.çizgi(17 * rc, 0)) // merkezden geçen yatay eksen
çiz(öteleme(-2 * rc, -14 * rc) * eksen2 -> Resim.çizgi(0, 17 * rc)) // merkezden geçen dikey eksen
çiz(öteleme(-4 * rc, -rc) * eksen2 -> Resim.çizgi(3.8 * rc, 0)) // yatay eksen
den yarıçap = öteleme(-2 * rc, rc) * kalemRengi(siyah) -> Resim.çizgi(rc, 0)
çiz(merkez, ball, yörünge, yarıçap)
// Topun yatay eksene izdüşümü açının kosinüsü, dikey eksene izdüşümü sinüsüdür:
den xİz = öteleme(0, 0) -> Resim.çizgi(1, 1); çiz(xİz)
den yİz = öteleme(0, 0) -> Resim.çizgi(1, 1); çiz(yİz)
den xTakip = öteleme(0, 0) -> Resim.çizgi(1, 1); çiz(xTakip)
den yTakip = öteleme(0, 0) -> Resim.çizgi(1, 1); çiz(yTakip)
tanım hareket(p: Nokta): Birim = { // 6 canlanan şey var:
  Diz(yarıçap, xİz, yİz, xTakip, yTakip) işle (_.sil())
  yarıçap = öteleme(-2 * rc, rc) * kalemRengi(siyah) -> Resim.çizgi(p.x + 2 * rc, p.y - rc)
  xİz     = öteleme(-2 * rc, p.y) * kalemKalınlığı(4) * kalemRengi(cCos) -> Resim.çizgi(p.x + 2 * rc, 0)
  yİz     = öteleme(p.x, rc) * kalemKalınlığı(4) * kalemRengi(cSin) -> Resim.çizgi(0, p.y - rc)
  xTakip  = öteleme(-2 * rc, p.y) * kalemKalınlığı(0.8) * kalemRengi(mavi) -> Resim.çizgi(17 * rc, 0)
  yTakip  = öteleme(p.x, p.y - 17 * rc) * kalemKalınlığı(0.8) * kalemRengi(mavi) -> Resim.çizgi(0, 17 * rc)
  çiz(yarıçap, xTakip, yTakip, xİz, yİz)
}
den zaman = 0
canlandır {
  dez x = 2 * zaman / 3.0 // telefon ekranlarına sığsın diye
  ball.döndürMerkezli(6, -rc, 0) // top merkez çevresinde 6'şar derece dönüyor
  çiz(öteleme(x, ball.konum.y) * kalemKalınlığı(0.3) * kalemRengi(cSin) -> Resim.daire(rb / 2)) // sinüs
  çiz(öteleme(ball.konum.x, -rc - x) * kalemKalınlığı(0.3) * kalemRengi(cCos) -> Resim.daire(rb / 2)) // kosinüs
  hareket(ball.konum)
  zaman += 1; eğer (zaman == 240 - 5) canlandırmayıDurdur()
}
çiz(büyütme(0.4) * öteleme(80, -60) -> Resim.renkliYazı("Sinüs dalgası => ...", 40, cSin))
çiz(büyütme(0.4) * öteleme(0, -200) * döndürme(-90) -> Resim.renkliYazı("Kosinüs dalgası => ...", 40, cCos))
