package scalafiddle.client

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scalafiddle.client.component.Motor

@JSExportTopLevel("AppMain")
object AppMain extends js.JSApp {
  @JSExport
  def main(): Unit = {
    // Yönlendiriciden ÖNCE: AppRouter "?motor=cm"i tanımayıp adresi "/"a çeviriyor (kojojs-editor#75).
    Motor.bayrağıOku()
    AppRouter.router().renderIntoDOM(dom.document.getElementById("root"))
  }
}
