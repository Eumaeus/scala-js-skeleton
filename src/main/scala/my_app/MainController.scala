package reader 
import com.thoughtworks.binding.{Binding, dom}
import com.thoughtworks.binding.Binding.{BindingSeq, Var, Vars}
import scala.scalajs.js
import scala.scalajs.js._
import js.annotation._
import scala.concurrent._
import collection.mutable
import collection.mutable._
import scala.scalajs.js.Dynamic.{ global => g }
import org.scalajs.dom._
import org.scalajs.dom.ext._
import org.scalajs.dom.raw._
import edu.holycross.shot.cite._
import edu.holycross.shot.scm._
import edu.holycross.shot.ohco2._
import edu.holycross.shot.citeobj._

import monix.execution.Scheduler.Implicits.global
import monix.eval._

import scala.scalajs.js.annotation.JSExport

@JSExportTopLevel("MainController")
object MainController {


	/* 
		Initiate app with a URL to an online CEX file	
	*/
	@JSExport
	def main(libUrl: String): Unit = {
		val task = Task{ MainController.loadRemoteLibrary(libUrl) }
		val future = task.runAsync
		dom.render(document.body, MainView.mainDiv)
		welcomeUser
	}

	/*
	 	Handles displaying messages to the user, color-coded according to type.
	 	Fades after 10 seconds.		
	*/
	def updateUserMessage(msg: String, alert: Int): Unit = {
		MainModel.userMessageVisibility.value = "app_visible"
		MainModel.userMessage.value = msg
		alert match {
			case 0 => MainModel.userAlert.value = "default"
			case 1 => MainModel.userAlert.value = "wait"
			case 2 => MainModel.userAlert.value = "warn"
		}
		js.timers.clearTimeout(MainModel.msgTimer)
		MainModel.msgTimer = js.timers.setTimeout(6000){ MainModel.userMessageVisibility.value = "app_hidden" }
	}

	def welcomeUser:Unit = {
		MainController.updateUserMessage(s"Welcoming user.", 0)
		MainModel.welcomeMessage.value = "Welcome User!"	
	}

/*
		Use AJAX request to get remote CEX file; update repository with CEX data
	*/
	def loadRemoteLibrary(url: String):Unit = {

		val xhr = new XMLHttpRequest()
		xhr.open("GET", url )
		xhr.onload = { (e: Event) =>
			if (xhr.status == 200) {
				val contents:String = xhr.responseText
				MainModel.requestParameterUrn.value = MainController.getRequestUrn
				MainController.updateRepository(contents)
				// Load request params if possible
				MainModel.requestParameterUrn.value match {
					case Some(rqv) => {
						for (u <- rqv) {
							O2Model.displayPassage(u)
						}	
					}
					case None =>
				}
			} else {
				MainController.updateUserMessage(s"Request for remote library failed with code ${xhr.status}",2)
			}
		}
		xhr.send()
	}

	/*
		Loads library from local CEX file; updates repository
	*/
	def loadLocalLibrary(e: Event):Unit = {
		val reader = new org.scalajs.dom.raw.FileReader()
		MainController.updateUserMessage("Loading local library.",0)
		reader.readAsText(e.target.asInstanceOf[org.scalajs.dom.raw.HTMLInputElement].files(0))
		reader.onload = (e: Event) => {
			val contents = reader.result.asInstanceOf[String]
			MainModel.requestParameterUrn.value = MainController.getRequestUrn
			MainController.updateRepository(contents)
		}
	}

	/* 
		Gets the http url and splits off any request parameter, parsing
		it as a CTS or CITE2 URN if possible
	*/	
	def getRequestUrn:Option[Vector[CtsUrn]] = {
		val currentUrl = 	js.Dynamic.global.location.href
		val requestParamUrnString = currentUrl.toString.split('?')
		val requestUrn:Option[Vector[CtsUrn]] = requestParamUrnString.size match {
			case s if (s > 1) => {
				try {
					val parts = requestParamUrnString(1).split("&")
					if ( parts.size > 0) {
						val sv:Vector[String] = parts.map(_.replaceAll("urn=","")).filter(_.size > 0).filter(_.take(8) == "urn:cts:").toVector
						val uv:Vector[CtsUrn] = sv.map(CtsUrn(_))
						uv.size match {
							case n if (n > 0) => Some(uv)
							case _ => None
						}
					} else {
						None
					}
				} catch {
					case e:Exception => {
						MainController.updateUserMessage(s"Failed to load request-parameter URN: ${e}",1)
						None
					}
				}
			}
			case _  => {
				None
			}
		}
		requestUrn
	}

	// Reads CEX file, creates repositories for Texts, Objects, and Images
	// *** Apropos Microservice ***
	@dom
	def updateRepository(cexString: String) = {
		//hideTabs
		//clearRepositories


		try {
			// Set up repo 
			val repo:CiteLibrary = CiteLibrary(cexString, MainModel.cexMainDelimiter, MainModel.cexSecondaryDelimiter)
			val mdString = s"Repository: ${repo.name}. Library URN: ${repo.urn}. License: ${repo.license}"
			var loadMessage:String = ""

			MainModel.mainLibrary.value = Some(repo)

			// Text Repository Stuff
			repo.textRepository match {
				case Some(tr) => {
					MainModel.showTexts.value = true
					MainModel.currentLibraryMetadataString.value = mdString
					O2Model.textRepo.value = Some(tr)
					MainController.updateUserMessage(s"Updated text repository: ${ O2Model.textRepo.value.get.catalog.size } works. ",0)
					loadMessage += s"Updated text repository: ${ O2Model.textRepo.value.get.catalog.size } works. "
					O2Model.updateCitedWorks
					O2Model.clearPassage

					O2Controller.preloadUrn
				}
				case None => {
					loadMessage += "No texts. "
				}
			}
			g.console.log(s"Initialized TextRepository.")

			// Collection Repository Stuff
			/*
			timeStart = new js.Date().getTime()
			repo.collectionRepository match {
				case Some(cr) => {
					CiteMainModel.showCollections.value = true
					val numCollections:Int = cr.collections.size
					val numObjects:Int = cr.citableObjects.size
					loadMessage += s" ${numCollections} collections. ${numObjects} objects. "
					ObjectModel.collRep.value	= Some(cr)			
					ObjectModel.clearObject
					ObjectModel.updateCollections
				}
				case None => {
					loadMessage += "No Collections. "	
				}
			}
			timeEnd = new js.Date().getTime()
			g.console.log(s"Initialized collectionRepository in ${(timeEnd - timeStart)/1000} seconds.")
			*/

			// Relations stuff
			/*
			timeStart = new js.Date().getTime()
			RelationsModel.clearRelations
			repo.relationSet match {
				case Some(rs) => {
					RelationsModel.citeRelations.value = Some(rs)
					CiteMainModel.showRelations.value = true
					RelationsModel.loadAllVerbs
				}
				case None => {
					RelationsModel.citeRelations.value = None
					CiteMainModel.showRelations.value = false 
				}
			}
			timeEnd = new js.Date().getTime()
			g.console.log(s"Initialized CiteRelations in ${(timeEnd - timeStart)/1000} seconds.")
			*/

			// Data Model Stuff
			/*
			timeStart = new js.Date().getTime()
			repo.dataModels match {
				case Some(dm) => {
					DataModelModel.dataModels.value = Some(dm)
					CiteBinaryImageController.discoverProtocols
					CiteBinaryImageController.setImageSwitch
					CiteBinaryImageModel.hasBinaryImages.value match {
						case true => CiteMainModel.showImages.value = true 
						case _ => CiteMainModel.showImages.value = false
					}
					CiteBinaryImageController.setBinaryImageCollections
					CommentaryModel.loadAllComments
				}
				case None => { 
					DataModelController.clearDataModels
				}
			}
			timeEnd = new js.Date().getTime()
			g.console.log(s"Initialized DataModels in ${(timeEnd - timeStart)/1000} seconds.")
			g.console.log(s"=====================")
			g.console.log(s"Whole initialization in ${(timeEnd - wholeTimeStart)/1000} seconds.")
			g.console.log(s"=====================")
			*/



			//checkDefaultTab

			MainController.updateUserMessage(loadMessage,0)

			// Load request parameter
			/*
			MainModel.requestParameterUrn.value match {
				case Some(u) => {
					u match {
						case CtsUrn(ctsurn) => {
							DataModelController.retrieveTextPassage(None, CtsUrn(ctsurn))
						}
						case Cite2Urn(cite2urn) => {
							DataModelController.retrieveObject(None, Cite2Urn(cite2urn))
						}
						case _ => // do nothing
					}
				}	
				case None => // do nothing
			}
			*/

		} catch  {
			case e: Exception => {
				MainController.updateUserMessage(s"""${e}. Invalid CEX file.""",2)
			}
		}

	}

}
