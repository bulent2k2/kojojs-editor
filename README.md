### How to run in terminal:

* Start `sbt` i terminal.
* Execute the `update` command inside sbt.
* Execute the `run` command inside sbt to compile/run the web-server
* Run the kojojs-core server in a separate terminal window, see instructions here: https://github.com/litan/kojojs-core
* Navigate to localhost:9000 in a Browser to start using KojoJS

### Instructions for using with IntelliJ Idea:

* Import sbt project
* Open sbt shell
* Execute the `run` command to compile/run the web-server
* Run the router and compiler services in the kojojs-core project
* Navigate to localhost:9000 to start using KojoJS

## Koco dağıtımı (ikojo.fly.dev)

Türkçe (Koco) sürüm — router + compilerServer + editör tek konteynerde,
nginx önünde — `bulent2k2/koco-deploy` ile paketlenip Fly.io'ya dağıtılıyor.
Tam belge: koco-deploy/README.md. Özet:

```sh
cd <yol>/koco-deploy
git -C ../kojojs-core pull      # güncel 'page' (kojojs-dev'den senkronlanmış runtime)
git -C ../kojojs-dev  pull      # kaynak (isteğe bağlı)

# build.sh İKİ JDK + yamalı derleyici yolu İSTİYOR (yoksa derleme patlar):
export KOCO_JDK_CORE=/path/to/jdk11    # 11/17/21 — kojojs-core Java 9+ ister (readAllBytes)
export KOCO_JDK_EDITOR=/path/to/jdk8   # kojojs-editor sbt 0.13 + Play 2.6 → Java 8
export KOCO_SCALA_TR=/path/to/kojo/scala-tr/build/pack/lib   # yan yana kojo klonu yoksa

./build.sh                     # üç servisi paketler + yamalı jar takası (KOCO_TOOLCHAIN=tr)

# Yerel makinede çalıştır / test et:
docker build -t koco .
docker run --rm -p 7860:7860 --memory 4g koco    # -> http://localhost:7860

# Fly'a dağıt (yerelde kurulan imajı iter):
fly deploy --local-only -a ikojo
```

Not: Editör (Play 2.6, sbt 0.13) Java 8 ile derlenir; `build.sh` bunu
`KOCO_JDK_EDITOR` ile seçer. İmajda router/compilerServer Java 21, editör Java 8.
