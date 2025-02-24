import mill._
import scalalib._
import scalajslib._
import scalanativelib._
import publish._
import mill.api.Result
import mill.modules.Jvm.createJar

import mill.scalalib.api.ZincWorkerUtil.isScala3
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.4.0`
import $ivy.`com.github.lolgab::mill-mima::0.0.23`

import de.tobiasroeser.mill.vcs.version.VcsVersion
import com.github.lolgab.mill.mima._

val scala31 = "3.2.2"
val scala213 = "2.13.10"
val scala212 = "2.12.17"
val scala211 = "2.11.12"
val scalaJS1 = "1.12.0"
val scalaNative04 = "0.4.9"
val crossVersions = Seq(scala31, scala213, scala212, scala211)

object fastparse extends Module{
  object jvm extends Cross[fastparseJvmModule](crossVersions)
  trait fastparseJvmModule extends FastparseModule{
    object test extends ScalaModuleTests with CommonTestModule
  }

  object js extends Cross[fastparseJsModule](crossVersions)
  trait fastparseJsModule extends FastparseModule with ScalaJSModule {
    def scalaJSVersion = scalaJS1
    private def sourceMapOptions = T.task {
      val vcsState = VcsVersion.vcsState()
      vcsState.lastTag.collect {
        case tag if vcsState.commitsSinceLastTag == 0 =>
          val baseUrl = pomSettings().url.replace("github.com", "raw.githubusercontent.com")
          val sourcesOptionName = if(isScala3(crossScalaVersion)) "-scalajs-mapSourceURI" else "-P:scalajs:mapSourceURI"
          s"$sourcesOptionName:${T.workspace.toIO.toURI}->$baseUrl/$tag/"
      }
    }

    override def scalacOptions = super.scalacOptions() ++ sourceMapOptions()

    object test extends ScalaJSModuleTests with CommonTestModule
  }

  object native extends Cross[fastparseNativeModule](crossVersions)
  trait fastparseNativeModule extends FastparseModule with ScalaNativeModule {
    def scalaNativeVersion = scalaNative04

    object test extends ScalaNativeModuleTests with CommonTestModule
  }
}

trait FastparseModule extends CommonCrossModule with Mima{
  def ivyDeps = Agg(
    ivy"com.lihaoyi::sourcecode::0.3.0",
    ivy"com.lihaoyi::geny::1.0.0"
  )

  def compileIvyDeps =
    if(isScala3(crossScalaVersion)) Agg.empty[Dep]
    else Agg(ivy"org.scala-lang:scala-reflect:$crossScalaVersion")

  def generatedSources = T{
    val dir = T.ctx().dest
    val file = dir/"fastparse"/"SequencerGen.scala"
    // Only go up to 21, because adding the last element makes it 22
    val tuples = (2 to 21).map{ i =>
      val ts = (1 to i) map ("T" + _)
      val chunks = (1 to i) map { n =>
        s"t._$n"
      }
      val tsD = (ts :+ "D").mkString(",")
      val anys = ts.map(_ => "Any").mkString(", ")
      s"""
          val BaseSequencer$i: Sequencer[($anys), Any, ($anys, Any)] =
            Sequencer0((t, d) => (${chunks.mkString(", ")}, d))
          implicit def Sequencer$i[$tsD]: Sequencer[(${ts.mkString(", ")}), D, ($tsD)] =
            BaseSequencer$i.asInstanceOf[Sequencer[(${ts.mkString(", ")}), D, ($tsD)]]
          """
    }
    val output = s"""
      package fastparse
      trait SequencerGen[Sequencer[_, _, _]] extends LowestPriSequencer[Sequencer]{
        protected[this] def Sequencer0[A, B, C](f: (A, B) => C): Sequencer[A, B, C]
        ${tuples.mkString("\n")}
      }
      trait LowestPriSequencer[Sequencer[_, _, _]]{
        protected[this] def Sequencer0[A, B, C](f: (A, B) => C): Sequencer[A, B, C]
        implicit def Sequencer1[T1, T2]: Sequencer[T1, T2, (T1, T2)] = Sequencer0{case (t1, t2) => (t1, t2)}
      }
    """.stripMargin
    os.write(file, output, createFolders = true)
    Seq(PathRef(file))
  }

  def mimaPreviousVersions = Seq(
    VcsVersion
      .vcsState()
      .lastTag
      .getOrElse(throw new Exception("Missing last tag"))
  )

  def mimaPreviousArtifacts =
    if (isScala3(crossScalaVersion)) Agg.empty[Dep]
    else super.mimaPreviousArtifacts()

  def mimaBinaryIssueFilters = super.mimaBinaryIssueFilters() ++ Seq(
    ProblemFilter.exclude[IncompatibleResultTypeProblem]("fastparse.Parsed#Failure.unapply")
  )
}

object scalaparse extends Module{
  object js extends Cross[ScalaParseJsModule](crossVersions)
  trait ScalaParseJsModule extends ExampleParseJsModule

  object jvm extends Cross[ScalaParseJvmModule](crossVersions)
  trait ScalaParseJvmModule extends ExampleParseJvmModule

  object native extends Cross[ScalaParseNativeModule](crossVersions)
  trait ScalaParseNativeModule extends ExampleParseNativeModule
}

object cssparse extends Module{
  object js extends Cross[CssParseJsModule](crossVersions)
  trait CssParseJsModule extends ExampleParseJsModule

  object jvm extends Cross[CssParseJvmModule](crossVersions)
  trait CssParseJvmModule extends ExampleParseJvmModule

  object native extends Cross[CssParseNativeModule](crossVersions)
  trait CssParseNativeModule extends ExampleParseNativeModule
}

object pythonparse extends Module{
  object js extends Cross[PythonParseJsModule](crossVersions)
  trait PythonParseJsModule extends ExampleParseJsModule

  object jvm extends Cross[PythonParseJvmModule](crossVersions)
  trait PythonParseJvmModule extends ExampleParseJvmModule

  object native extends Cross[PythonParseNativeModule](crossVersions)
  trait PythonParseNativeModule extends ExampleParseNativeModule
}

trait ExampleParseJsModule extends CommonCrossModule with ScalaJSModule{
  def moduleDeps = Seq(fastparse.js())
  def scalaJSVersion = scalaJS1

  object test extends ScalaJSModuleTests with CommonTestModule
}

trait ExampleParseJvmModule extends CommonCrossModule{
  def moduleDeps = Seq(fastparse.jvm())

  object test extends ScalaModuleTests with CommonTestModule{
    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"net.sourceforge.cssparser:cssparser:0.9.18",
    ) ++ Agg.when(!isScala3(crossScalaVersion))(
      ivy"org.scala-lang:scala-compiler:$crossScalaVersion"
    )
  }
}

trait ExampleParseNativeModule extends CommonCrossModule with ScalaNativeModule{
  def scalaNativeVersion = scalaNative04
  def moduleDeps = Seq(fastparse.native())

  object test extends ScalaNativeModuleTests with CommonTestModule
}

trait CommonCrossModule extends CrossScalaModule with PublishModule with PlatformScalaModule{
  def publishVersion = VcsVersion.vcsState().format()

  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "com.lihaoyi",
    url = "https://github.com/lihaoyi/fastparse",
    licenses = Seq(License.MIT),
    versionControl = VersionControl.github(
      "com-lihaoyi",
      "fastparse"
    ),
    developers = Seq(
      Developer("lihaoyi", "Li Haoyi","https://github.com/lihaoyi")
    )
  )

  def scalaDocPluginClasspath = T{ Agg[PathRef]() }

  def sources = T.sources {
    super.sources() ++
    Agg.when(scalaVersion() != scala211)(PathRef(millSourcePath / "src-2.12+"))
  }
}

trait CommonTestModule extends ScalaModule with TestModule.Utest{
  def ivyDeps = Agg(ivy"com.lihaoyi::utest::0.8.1")

  override def scalacOptions =
    super.scalacOptions() ++
    Agg.when(scalaVersion() == scala213)(
      "-Xfatal-warnings",
      "-Wconf:cat=feature:s,cat=deprecation:s"
    )
}

object perftests extends Module{
  object bench2 extends PerfTestModule {
    def scalaVersion0 = scala213
    def moduleDeps = Seq(
      scalaparse.jvm(scala213).test,
      pythonparse.jvm(scala213).test,
      cssparse.jvm(scala213).test,
      fastparse.jvm(scala213).test,
    )
  }

  object benchScala3 extends PerfTestModule {
    def scalaVersion0 = scala31
    def sources = T.sources{ bench2.sources() }
    def moduleDeps = Seq(
      scalaparse.jvm(scala31).test,
      pythonparse.jvm(scala31).test,
      cssparse.jvm(scala31).test,
      fastparse.jvm(scala31).test,
    )
  }

  object compare extends PerfTestModule {
    def scalaVersion0 = scala212
    def moduleDeps = Seq(
      fastparse.jvm(scala212).test,
      scalaparse.jvm(scala212).test,
      pythonparse.jvm(scala212).test
    )

    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"org.json4s::json4s-ast:3.6.0",
      ivy"org.json4s::json4s-native:3.6.0",
      ivy"org.json4s::json4s-jackson:3.6.0",
      ivy"io.circe::circe-parser:0.9.1",
      ivy"io.argonaut::argonaut:6.2",
      ivy"com.typesafe.play::play-json:2.6.9",
      ivy"com.fasterxml.jackson.core:jackson-databind:2.9.4",
      ivy"com.lihaoyi::ujson:1.1.0",
      ivy"org.scala-lang.modules::scala-parser-combinators:1.1.1",
      ivy"org.python:jython:2.7.1b3"
    )
  }

  trait PerfTestModule extends ScalaModule with TestModule.Utest{
    def scalaVersion0: String
    def scalaVersion = scalaVersion0
    def scalacOptions = Seq("-opt:l:method")
    def resources = T.sources{
      Seq(PathRef(perftests.millSourcePath / "resources")) ++
        fastparse.jvm(scalaVersion0).test.resources()
    }

    def ivyDeps = Agg(ivy"com.lihaoyi::utest::0.8.1")
  }
}

object demo extends ScalaJSModule{
  def scalaJSVersion = scalaJS1
  def scalaVersion = scala213
  def moduleDeps = Seq(
    scalaparse.js(scala213),
    cssparse.js(scala213),
    pythonparse.js(scala213),
    fastparse.js(scala213).test,
  )

  def ivyDeps = Agg(
    ivy"org.scala-js::scalajs-dom::0.9.8",
    ivy"com.lihaoyi::scalatags::0.9.3"
  )
}
