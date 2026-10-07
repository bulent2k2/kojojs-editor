// Librarian'ın (kütüphane listesini libraries.json'dan okuyan sınıf) AYRI derlemesi ve sınaması.
//
// NEDEN AYRI: editörün `server/test`i sbt 0.13 + Scala.js 0.6 + Play 2.6 ister ve CI'da koşmuyor
// (bkz. .github/workflows/denetimler.yml), bu yüzden LibrarianSpec 2017'den 2026'ya kırmızı kaldı
// ve kimse görmedi (#64). Librarian ise yalnız upickle 0.4.4, slf4j ve shared'daki `Library`ya
// dayanıyor; bu proje onları güncel sbt'de derleyip LibrarianSpec'i koşturuyor. Kaynaklar kopya
// DEĞİL: editörün kendi dosyaları, doğrudan oradan okunuyor (cevir-istemci/ ile aynı yöntem).
//
// KAPSAM DIŞI: server'ın geri kalanı (Play denetleyicileri, ApiGuardSpec, UpdateFiddleSpec...)
// bu derlemede YOK; onlar hâlâ yalnız `JAVA_HOME=…1.8 sbt server/test` ile koşuyor.
//
// Çalıştırma:  cd librarian-sinama && sbt test
name := "librarian-sinama"
// Editör 2.12.10 kullanıyor; o sürüm Java 21'de çalışmıyor. Librarian'da sürüme bağlı kod yok.
scalaVersion := "2.12.20"

Compile / unmanagedSources :=
  Seq(baseDirectory.value / ".." / "server/src/main/scala/scalafiddle/server/Librarian.scala") ++
    ((baseDirectory.value / ".." / "shared/src/main/scala") ** "*.scala").get
Test / unmanagedSources := Seq(baseDirectory.value / ".." / "server/src/test/scala/scalafiddle/server/LibrarianSpec.scala")
// Yalnız libraries.json: server/src/main/resources'ın geri kalanı bu sınamanın işi değil.
Test / unmanagedResourceDirectories += baseDirectory.value / ".." / "server/src/main/resources"
Test / unmanagedResources / includeFilter := "libraries.json"

libraryDependencies ++= Seq(
  // editörle aynı: Settings.scala'daki versions.upickle
  "com.lihaoyi" %% "upickle" % "0.4.4",
  "org.slf4j" % "slf4j-api" % "1.7.25",
  // 3.1.x: org.scalatest.WordSpec / Matchers hâlâ var (3.2'de kalktı); editör 3.0.3 kullanıyor
  "org.scalatest" %% "scalatest" % "3.1.4" % Test
)
