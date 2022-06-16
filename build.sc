// build.sc
import mill._, scalalib._, publish._

trait CommonSpinalModule extends ScalaModule {
  def scalaVersion = "2.12.14"

  def scalacOptions = Seq("-unchecked", "-deprecation", "-feature")

  def ivyDeps = Agg(
    ivy"com.github.spinalhdl::spinalhdl-core:1.7.0a",
    ivy"com.github.spinalhdl::spinalhdl-lib:1.7.0a",
  )
  def scalacPluginIvyDeps = Agg(ivy"com.github.spinalhdl::spinalhdl-idsl-plugin:1.4.3")
}

object alarm extends CommonSpinalModule with PublishModule {
  def mainClass = Some("alarm.AlarmVerilog")
  def publishVersion = "0.0.1-SNAPSHOT"

  def pomSettings = PomSettings(
    description = "Simple alarm",
    organization = "com.github.aplqo",
    url = "https://github.com/aplqo/alarmer",
    licenses = Seq(License.`GPL-3.0-or-later`),
    versionControl = VersionControl.github("aplqo", "alarmer"),
    developers = Seq(
      Developer("aplqo", "aplqo W", "https://github.com/aplqo")
    )
  )
}
