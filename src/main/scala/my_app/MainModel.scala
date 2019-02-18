package reader 
import com.thoughtworks.binding.{Binding, dom}
import com.thoughtworks.binding.Binding.{BindingSeq, Var, Vars}
import com.thoughtworks.binding.Binding.{Var, Vars}
import com.thoughtworks.binding.dom
import org.scalajs.dom.document
import org.scalajs.dom.raw.Event
import org.scalajs.dom.ext.Ajax
import scala.concurrent
              .ExecutionContext
              .Implicits
              .global

import scala.scalajs.js
import scala.scalajs.js._
import js.annotation._
import edu.holycross.shot.cite._
import edu.holycross.shot.scm._


@JSExportTopLevel("MainModel")
object MainModel {

		val userMessage = Var("Main loaded.")
		val userAlert = Var("default")
		val userMessageVisibility = Var("app_hidden")
		var msgTimer:scala.scalajs.js.timers.SetTimeoutHandle = null

		val welcomeMessage = Var("")
		val requestParameterUrn = Var[Option[Vector[CtsUrn]]](None)

		val cexMainDelimiter:String = "#"
		val cexSecondaryDelimiter:String = ","

		val mainLibrary = Var[Option[CiteLibrary]](None)
		
		val showTexts = Var(true)
		val currentLibraryMetadataString = Var("No library loaded.")


}
