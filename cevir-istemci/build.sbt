// "Çevir" düğmesinin istemci mantığının (Cevir.scala) AYRI derlemesi ve sınaması.
//
// NEDEN AYRI: editörün kendi derlemesi sbt 0.13 + Scala.js 0.6 + Play 2.6 ister
// ve CI'da koşmuyor (bkz. .github/workflows/denetimler.yml). Cevir.scala ise
// BİLEREK yalnız scalajs-dom'a dayanıyor; bu proje onu güncel Scala.js'te
// (1.x, Central'da duran) derleyip Node'da CevirTest'i koşturuyor. Kaynaklar
// kopya DEĞİL: editörün kendi dosyaları, doğrudan oradan okunuyor.
//
// Bu derleme FiddleEditor.scala'yı KAPSAMAZ (React/diode/upickle 0.4 yok); oradaki
// yapıştırma kodu bu denetimle derlenmiyor.
//
// Çalıştırma:  cd cevir-istemci && sbt test
enablePlugins(ScalaJSPlugin)

name := "cevir-istemci"
scalaVersion := "2.12.20"
scalaJSUseMainModuleInitializer := false

Compile / unmanagedSources := Seq(baseDirectory.value / ".." / "client/src/main/scala/scalafiddle/client/Cevir.scala")
Test / unmanagedSources := Seq(baseDirectory.value / ".." / "client/src/test/scala/scalafiddle/client/CevirTest.scala")

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "2.8.0",
  // 3.1.x: editörün kullandığı 3.0.x API'si (org.scalatest.FunSuite) hâlâ var;
  // 3.2'de kalktı.
  "org.scalatest" %%% "scalatest" % "3.1.4" % Test
)
