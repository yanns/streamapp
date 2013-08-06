import com.github.play2war.plugin._
import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "stream"
  val appVersion      = "1.0-SNAPSHOT"

  val appDependencies = Seq(
  )


  val main = play.Project(appName, appVersion, appDependencies).settings(
    Play2WarKeys.servletVersion := "3.0"
  ).settings(Play2WarPlugin.play2WarSettings: _*)

}
