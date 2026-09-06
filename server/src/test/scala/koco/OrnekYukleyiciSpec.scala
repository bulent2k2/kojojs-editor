package koco

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import org.scalatest._

/**
  * OrnekYukleyici birim testleri. Geçici bir dizinde kojojs-dev/ornekler'in küçük bir
  * kopyası kurulur (ikojo örneği + masaustu/src/main/resources + installer/examples/othello).
  */
class OrnekYukleyiciSpec extends WordSpec with Matchers with BeforeAndAfterAll {
  var kok: Path = _

  private def yaz(goreli: String, icerik: String): Path = {
    val p = kok.resolve(goreli)
    Files.createDirectories(p.getParent)
    Files.write(p, icerik.getBytes(StandardCharsets.UTF_8))
    p
  }

  override def beforeAll(): Unit = {
    kok = Files.createTempDirectory("ornekler-test")
    yaz("01-ilk-adimlar.kojo", "sil()\nileri(100)\n")
    yaz("masaustu/src/main/resources/samples/tr/oyku-tanimlari.kojo", "dez sayfa = 1\n")
    yaz("masaustu/src/main/resources/samples/tr/kojo-kilavuz/turler.kojo", "// #yükle /samples/tr/oyku-tanimlari\ndez türler = 2\n")
    yaz("masaustu/src/main/resources/samples/tr/belge.kojo",
        "// #yükle /samples/tr/oyku-tanimlari\nsatıryaz(sayfa)\n// #yükle /samples/tr/kojo-kilavuz/turler\nsatıryaz(türler)\n")
    yaz("masaustu/src/main/resources/robosim/tr/robot.kojo", "sınıf Robot\n")
    yaz("masaustu/src/main/resources/robosim/tr/engel.kojo", "// #yükle /robosim/tr/robot.kojo\nyeni Robot\n")
    yaz("masaustu/src/main/resources/ka-bridge/tr/sample.kojo", "// #yükle ~/kojo-includes/ka-bridge.kojo\nköprü()\n")
    yaz("masaustu/src/main/resources/samples/tr/kayip.kojo", "// #yükle /samples/tr/yok-boyle-dosya\nileri()\n")
    yaz("masaustu/installer/examples/othello/tr/anaTanimlar.kojo", "dez N = 8\n")
    yaz("masaustu/installer/examples/othello/tr/tahta.kojo", "//#yükle tr/anaTanimlar\nsınıf Tahta\n")
    yaz("masaustu/installer/examples/othello/tr/otello.kojo", "//#yükle tr/anaTanimlar\n//#yükle tr/tahta\noyna()\n")
    yaz("masaustu/installer/examples/othello/menu_tr.kojo.installed", "//#yükle tr/anaTanimlar\nmenü()\n")
    yaz("dongu/a.kojo", "// #include b\nA\n")
    yaz("dongu/b.kojo", "// #include a\nB\n")
    yaz("notlar.txt", "gizli\n")
  }

  override def afterAll(): Unit = {
    import scala.collection.JavaConverters._
    Files.walk(kok).iterator().asScala.toList.reverse.foreach(Files.delete)
  }

  private def genislet(yol: String): String = OrnekYukleyici.genislet(kok, yol) match {
    case Right(kod)  => kod
    case Left(neden) => fail(s"$yol genişletilemedi: $neden")
  }

  "OrnekYukleyici.yolCoz" should {
    "kök altındaki .kojo dosyasını bulur" in {
      OrnekYukleyici.yolCoz(kok, "01-ilk-adimlar.kojo") shouldBe Right(kok.resolve("01-ilk-adimlar.kojo"))
      OrnekYukleyici.yolCoz(kok, "masaustu/installer/examples/othello/menu_tr.kojo.installed").isRight shouldBe true
    }
    "URL kodlu yolu çözer" in {
      OrnekYukleyici.yolCoz(kok, "01-ilk-adimlar%2Ekojo").isRight shouldBe true
    }
    "kök dışına çıkan, mutlak ve ev dizinli yolları reddeder" in {
      OrnekYukleyici.yolCoz(kok, "../01-ilk-adimlar.kojo").isLeft shouldBe true
      OrnekYukleyici.yolCoz(kok, "masaustu/../../x.kojo").isLeft shouldBe true
      OrnekYukleyici.yolCoz(kok, "%2e%2e/x.kojo").isLeft shouldBe true
      OrnekYukleyici.yolCoz(kok, "/etc/passwd.kojo").isLeft shouldBe true
      OrnekYukleyici.yolCoz(kok, "~/x.kojo").isLeft shouldBe true
      OrnekYukleyici.yolCoz(kok, "").isLeft shouldBe true
    }
    "yalnız .kojo ve .kojo.installed sunar" in {
      OrnekYukleyici.yolCoz(kok, "notlar.txt").isLeft shouldBe true
      OrnekYukleyici.yolCoz(kok, "masaustu").isLeft shouldBe true
    }
    "olmayan dosyaya Left verir" in {
      OrnekYukleyici.yolCoz(kok, "yok.kojo").isLeft shouldBe true
    }
  }

  "OrnekYukleyici.genislet" should {
    "içe alma olmayan betiği olduğu gibi verir" in {
      genislet("01-ilk-adimlar.kojo") shouldBe "sil()\nileri(100)\n"
    }
    "mutlak #yükle yolunu masaustu/src/main/resources altında çözer, başa ekler ve satırı işaretler" in {
      val kod = genislet("masaustu/src/main/resources/robosim/tr/engel.kojo")
      kod shouldBe
        "// --- #yükle /robosim/tr/robot.kojo başı (masaustu/src/main/resources/robosim/tr/robot.kojo) ---\n" +
          "sınıf Robot\n" +
          "// --- #yükle /robosim/tr/robot.kojo sonu ---\n" +
          "// #Yükle /robosim/tr/robot.kojo -- içeriği yukarıya alındı\n" +
          "yeni Robot\n"
    }
    "uzantısız ada .kojo ekler, iç içe alır ve aynı dosyayı bir kez alır" in {
      val kod = genislet("masaustu/src/main/resources/samples/tr/belge.kojo")
      kod should include("// --- #yükle /samples/tr/oyku-tanimlari başı (masaustu/src/main/resources/samples/tr/oyku-tanimlari.kojo) ---\ndez sayfa = 1\n")
      kod should include("dez türler = 2")
      // turler.kojo'nun kendi #yükle'si daha önce alındığı için tekrar eklenmez
      kod.split("dez sayfa = 1").length shouldBe 2
      kod should include("// #Yükle /samples/tr/oyku-tanimlari -- daha önce alındı")
      // içe alınanlar gövdeden önce gelir
      kod.indexOf("dez türler = 2") should be < kod.indexOf("satıryaz(sayfa)")
    }
    "göreli #yükle yolunu içe alan dosyanın üst dizinlerinde arar (othello)" in {
      val kod = genislet("masaustu/installer/examples/othello/tr/otello.kojo")
      kod should include("başı (masaustu/installer/examples/othello/tr/anaTanimlar.kojo)")
      kod should include("başı (masaustu/installer/examples/othello/tr/tahta.kojo)")
      kod should include("sınıf Tahta")
      kod should include("// #Yükle tr/anaTanimlar -- daha önce alındı")
      kod should endWith("oyna()\n")
      kod.split("dez N = 8").length shouldBe 2
    }
    ".kojo.installed dosyasında da çalışır" in {
      val kod = genislet("masaustu/installer/examples/othello/menu_tr.kojo.installed")
      kod should include("dez N = 8")
      kod should endWith("menü()\n")
    }
    "~ yolunu içe almaz, açıklayıcı yorum bırakır" in {
      val kod = genislet("masaustu/src/main/resources/ka-bridge/tr/sample.kojo")
      kod should startWith("// #Yükle ~/kojo-includes/ka-bridge.kojo -- ev dizini (~)")
      kod should endWith("köprü()\n")
    }
    "bulunamayan dosyayı yorumla belirtir, sayfayı düşürmez" in {
      val kod = genislet("masaustu/src/main/resources/samples/tr/kayip.kojo")
      kod should startWith("// #Yükle /samples/tr/yok-boyle-dosya -- dosya bulunamadı")
    }
    "döngüsel #include'da takılmaz" in {
      val kod = genislet("dongu/a.kojo")
      kod should include("B\n")
      kod should include("// #Include a -- daha önce alındı")
      kod should endWith("A\n")
    }
    "geçersiz yolda Left verir" in {
      OrnekYukleyici.genislet(kok, "../x.kojo").isLeft shouldBe true
    }
  }

  "OrnekYukleyici.sar" should {
    val sablon =
      """import scalajs.js
        |object ScalaFiddle {
        |    import builtins._
        |
        |  // $FiddleStart
        |  // Koco kodunu buraya yazabilirsiniz
        |
        |  // $FiddleEnd
        |}
        |""".stripMargin

    "gövdeyi $FiddleStart ile $FiddleEnd arasına koyar" in {
      OrnekYukleyici.sar(sablon, "sil()\nileri(100)\n") shouldBe
        """import scalajs.js
          |object ScalaFiddle {
          |    import builtins._
          |
          |  // $FiddleStart
          |sil()
          |ileri(100)
          |
          |  // $FiddleEnd
          |}
          |""".stripMargin
    }
    "işaret yoksa son } öncesine koyar" in {
      OrnekYukleyici.sar("object ScalaFiddle {\n}\n", "ileri()") shouldBe "object ScalaFiddle {\nileri()\n}\n"
    }
    "boş gövdeyi yorumla doldurur (istemci boş gövdede min girinti hesaplayamıyor)" in {
      OrnekYukleyici.sar("object ScalaFiddle {\n}\n", "  \n") should include("// (boş betik)")
    }
  }
}
