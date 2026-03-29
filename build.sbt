import scala.sys.process.Process
import sbtcrossproject.CrossPlugin.autoImport.{ crossProject, CrossType }

ThisBuild / version := "0.6.0"

ThisBuild / scalaVersion := "3.7.2"

lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(
    name := "happy-farm-shared",
    libraryDependencies ++= Seq(
      "dev.zio"     %% "zio-schema"            % "1.7.4",
      "dev.zio"     %% "zio-schema-derivation" % "1.7.4",
      "com.lihaoyi" %% "upickle"               % "4.3.1"
    )
  )

lazy val frontend = project
  .in(file("frontend"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name                            := "happy-farm-frontend",
    scalaVersion                    := "3.7.2",
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.8.0",
      "com.raquo"    %%% "laminar"     % "17.2.1",
      "com.lihaoyi"  %%% "upickle"     % "4.3.1",
      "dev.laminext" %%% "websocket"   % "0.17.1"
    )
  )
  .dependsOn(shared.js)

lazy val commonBackendSettings = Seq(
  scalaVersion         := "3.7.2",
  Compile / mainClass  := Some("com.happyfarm.backend.HappyFarmMain"),
  executableScriptName := "happy-farm-messenger",
  Docker / packageName := "happy-farm-messenger",
  dockerEntrypoint     := Seq(s"/opt/docker/bin/${executableScriptName.value}"),
  dockerBaseImage := "eclipse-temurin:21-jre-jammy", // https://hub.docker.com/layers/library/eclipse-temurin/21-jre-jammy
  dockerExposedPorts := Seq(8080),
  libraryDependencies ++= Seq(
    "dev.zio"                    %% "zio-http"                  % "3.4.0",
    "dev.zio"                    %% "zio-cache"                 % "0.2.7",
    "com.typesafe.scala-logging" %% "scala-logging"             % "3.9.5",
    "com.typesafe"                % "config"                    % "1.4.4",
    "com.github.pureconfig"      %% "pureconfig-generic-scala3" % "0.17.9",
    "com.zaxxer"                  % "HikariCP"                  % "7.0.2",
    "org.postgresql"              % "postgresql"                % "42.7.7"
  )
)

lazy val backend = project
  .in(file("backend"))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(
    commonBackendSettings,
    name := "happy-farm-backend-local"
  )
  .dependsOn(shared.jvm)


lazy val pushGHCR = taskKey[Unit]("Push Docker image to GHCR")
lazy val backendRailway = project
  .in(file("backend"))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(
    commonBackendSettings,
    name := "happy-farm-backend-railway",
    Docker / dockerRepository := Some("ghcr.io/ssdong"),
    Docker / version := (ThisBuild / version).value,
    target := baseDirectory.value / "target-railway",
    Docker / dockerBuildCommand := {
      val platform = "linux/amd64"
      // .value on dockerAlias automatically combines repository + name + version
      val alias = (Docker / dockerAlias).value.toString
      Seq(
        "docker",
        "buildx",
        "build",
        "--platform",
        platform,
        "--load",
        "-t",
        alias,
        "."
      )
    },
    pushGHCR := {
      val alias = (Docker / dockerAlias).value.toString
      val cmd = Seq("docker", "push", alias)
      val exitCode = Process(cmd).!

      if (exitCode != 0) sys.error(s"Docker push failed with exit code $exitCode")
    }
  )
  .dependsOn(shared.jvm)

lazy val fastLinkCompileCopy = taskKey[Unit]("Copy DEV JS file")

val jsPath = "backend/src/main/resources/public/assets"

fastLinkCompileCopy := {
  val files = (frontend / Compile / fastLinkJSOutput).value.listFiles()
  for (file <- files) {
    IO.copyFile(
      file,
      baseDirectory.value / jsPath / file.name
    )
  }
}

lazy val fullLinkCompileCopy = taskKey[Unit]("Copy PROD JS file")

fullLinkCompileCopy := {
  val files = (frontend / Compile / fullLinkJSOutput).value.listFiles()
  for (file <- files) {
    IO.copyFile(
      file,
      baseDirectory.value / jsPath / file.name
    )
  }
}

val tailwindIn  = "backend/src/main/resources/public/assets/app.css"
val tailwindOut = "backend/src/main/resources/public/assets/main.css"

lazy val tailwindBuild = taskKey[Unit]("Build Tailwind CSS")

/** Tailwind scans Scala source files and generate CSS if it encounters valid Tailwind class names
  */
tailwindBuild := {
  val base = (ThisBuild / baseDirectory).value
  val cmd  = Seq("npx", "@tailwindcss/cli", "-i", tailwindIn, "-o", tailwindOut)
  Process(cmd, base).!
}

addCommandAlias("dev", ";fastLinkCompileCopy; tailwindBuild; backend/copyResources; backend/compile")
addCommandAlias("prod", ";fullLinkCompileCopy; tailwindBuild; backend/copyResources; backend/compile")
addCommandAlias(
  "releaseDockerLocal",
  ";fullLinkCompileCopy; tailwindBuild; backend/copyResources; backend/Docker/publishLocal"
)

addCommandAlias(
  "releaseDockerRailway",
  ";fullLinkCompileCopy; tailwindBuild; backendRailway/copyResources; backendRailway/Docker/publishLocal"
)

addCommandAlias("push", ";backendRailway/pushGHCR")
