package reader

import com.thoughtworks.binding.{Binding, dom}
import com.thoughtworks.binding.Binding.{BindingSeq, Var, Vars}
import scala.scalajs.js
import scala.scalajs.js._
import org.scalajs.dom._
import org.scalajs.dom.ext._
import org.scalajs.dom.raw._
import edu.holycross.shot.cite._
import edu.holycross.shot.ohco2._
import edu.holycross.shot.citeobj._
import scala.scalajs.js.Dynamic.{ global => g }
import scala.concurrent._
//import ExecutionContext.Implicits.global

import scala.scalajs.js.annotation.JSExport
import js.annotation._
import monix.execution.Scheduler.Implicits.global
import monix.eval._

@JSExportTopLevel("O2View")
object O2View {


	// HTML Div holding messages
	@dom
	def o2messageDiv = {
		<div id="o2_message" class={ s"app_message o2_message ${O2Model.userMessageVisibility.bind} ${O2Model.userAlert.bind}"  }>
		<p>{ O2Model.userMessage.bind }  </p>
		</div>
	}


	// HTML Div: main div for text work
	@dom
	def o2div = {
		val urnValidatingKeyUpHandler = { event: KeyboardEvent =>
			(event.currentTarget, event.keyCode) match {
				case (input: html.Input, KeyCode.Enter) => {
					event.preventDefault()
					O2Controller.changeUrn(s"${input.value.toString}")
					//input.value = ""
				}
				case(input: html.Input, _) =>  O2Controller.validateUrn(s"${input.value.toString}")
				case _ =>
			}
		}

		<div id="o2_Container">
		

		{ o2messageDiv.bind }

		<p id="o2_reportingCurrentUrn" class="app_reportingCurrentUrn"> { O2Model.urn.bind.toString } </p>


		<p id="o2_urnInputP">
		<input
		class={ s"${O2Controller.validUrnInField.bind}" }
		id="o2_urnInput"
		size={ 50 }
		type="text"
		value={ O2Model.urn.bind.toString }
		onkeyup={ urnValidatingKeyUpHandler }>
		</input>

	{ O2View.citedWorksMenu.bind }
	{ O2View.retrievePassageButton.bind }
	{ O2View.clearAll.bind }
	<br/>
	</p>

	{ passageContainer.bind }

	</div>
}

@dom
def addTextButton = {
	<button> {
		"Add Text"
	}
	</button>
}

@dom
def retrievePassageButton = {
	<button
			onclick={ event: Event => {
				val s:String = js.Dynamic.global.document.getElementById("o2_urnInput").value.toString
				O2Model.urn.value = CtsUrn(s)
				O2Controller.updateUserMessage("Retrieving passage…",1)
				val task = Task{ O2Controller.changePassage }
				val future = task.runAsync
//				js.timers.setTimeout(200){
					//Future{ O2Controller.changePassage }
//				}
			}
		}
		disabled={ (O2Controller.validUrnInField.bind == false) }
> {
	if ( O2Controller.validUrnInField.bind == true ){
		"Load Passage from URN"
	} else {
		"Invalid URN"
	}

}
</button>
}

@dom
def seeAllVersionsButton(vCorp:O2Model.BoundCorpus) = {
	<button
		disabled = { if (vCorp.versionsAvailable.bind > 1) false else true }
		onclick = { event: Event => {
				O2Model.displayUrn.value = O2Model.collapseToWorkUrn(vCorp.versionUrn.value)
				//O2Model.displaynewpassage(O2Model.displayUrn.value)
				O2Model.updateTextInCurrentCorpus(vCorp.versionUrn.value, O2Model.collapseToWorkUrn(vCorp.versionUrn.value))
		}}
	>
		See All Versions of Passage
	</button>
}

@dom
def clearAll = {
	<button 
		onclick = { event: Event => {
			O2Model.clearPassage
	}} >Clear All</button>
}

/* Passage Container */
@dom
def passageContainer = {
	<div id="o2_passageContainer">

		{ previousUrnsMenu.bind }

		
		<div id="o2_xmlPassageContainer">
				
			{ 	for ( versionCorpus <- O2Model.currentCorpus) yield {
					{ textVersionContainer(versionCorpus).bind }	
				}
			}	

		</div>
		

	</div>

}

@dom
def previousUrnsMenu = {
	<div id="o2_urnHistoryMenu"
	class={
			if (O2Model.urnHistory.length.bind < 1) {
				"dropdown empty" 
			} else {
				"dropdown"
			} 
	} >
	<span>Text Passage History</span>
	{ O2View.previousUrnsMenuItems.bind }
	</div>
}

@dom
def previousUrnsMenuItems = {
	<div class="dropdown-content">
		{ O2View.loadPreviousUrns.bind }
	</div>
}

@dom
def loadPreviousUrns = {
	for (citationLabel <- O2Model.urnHistory) yield {
		<p onclick={ 
			event: Event => {
				O2Controller.changeUrn(citationLabel._2)
			}
		}>{ s"${citationLabel._1}: ${citationLabel._3}" }</p>
	}	
}

@dom
def textVersionContainer(vCorp:O2Model.BoundCorpus) = {
	<div class={s"o2_versionContainer oneOf${if (O2Model.currentCorpus.length.bind > 4) 4 else O2Model.currentCorpus.length.bind}"}>
	
		{ textNavigationButtons(vCorp).bind }		
		<p class="o2_versionDescription ltr">
			{ textVersionLabelAndLink(vCorp.versionUrn.value ,vCorp.versionLabel.value ).bind }
		</p>
		{ versionNodes(vCorp).bind }

		{ textNavigationButtons(vCorp).bind }		
	</div>
}


@dom
def versionNodes(vCorp:O2Model.BoundCorpus) = {
	for (vn <- vCorp.versionNodes) yield {
		<div class="o2_citationBlock">	
			{
				for ( n <- vn.nodes) yield {
					val checkForLong:String = {
						n.text.size match {
							case s if (s < 20) => {
								n.urn.exemplarOption match {
									case Some(eo) => " short"
									case None => " long"
								}
							}
							case _ => " long"
						}
					}
					val passageClass:String = {
						O2Model.checkForRTL(n.text) match {
							case true => s"o2_textPassage rtl ${checkForLong}"
							case false => s"o2_textPassage ltr ${checkForLong}"
						}	
					}
					<p class={ passageClass }>
						<span 
							onclick={ event: Event => {
								val textIndex:Int = {
									val tempV:Vector[CtsUrn] = O2Model.currentCorpus.value.toVector.map(_.versionUrn.value)
									g.console.log(s"""${tempV.mkString(" ")}""")
									val vu:CtsUrn = vCorp.versionUrn.value
									g.console.log(s"vu = ${vu}")
									tempV.indexOf(vu)
								}
								g.console.log(s"index = ${textIndex}")
								val el = js.Dynamic.global.document.getElementById(s"node_${n.urn}").asInstanceOf[HTMLAnchorElement]
								val classList:String = el.getAttribute("class")
								if (classList.contains("alignedNode")) {
									val newCL:String = classList.replaceAll("alignedNode[0-9]","").replaceAll("  "," ")
									el.setAttribute("class",newCL)
								} else {
									val newCL:String = classList + s" alignedNode${textIndex}"
									el.setAttribute("class",newCL)
								}

								g.console.log(s"Got: ${classList}")
							}}
							class="o2_passage"
							id={ s"node_${n.urn}"} >
							{ nodeCitationSpan(n.urn).bind }
							{ createXMLNode(n.text).bind }

						</span>
					</p>
				}
			}
		</div>
	}	
}

@dom
def nodeCitationSpan(urn:CtsUrn) = {
	<span class="o2_passageUrn">{ urn.passageComponent }</span>
}

@dom
def createXMLNode(t:String) = {
	val thisSpan = document.createElement("span").asInstanceOf[HTMLSpanElement]		
	thisSpan.innerHTML = t
	thisSpan
}


@dom
def textVersionLabelAndLink(u:CtsUrn, label:String) = {
	{ passageUrnSpan(u, label).bind }	
}

/* Individual Text Navigation Buttons */
@dom
def textNavigationButtons(vCorp:O2Model.BoundCorpus) = {
	<div class="o2_navLinks">
			{ O2View.versionNavPrevButton(vCorp).bind }
			{ O2View.seeAllVersionsButton(vCorp).bind }
			{ O2View.versionRemoveButton(vCorp).bind }
			{ O2View.versionNavNextButton(vCorp).bind }
	</div>
}

@dom
def versionRemoveButton(vCorp:O2Model.BoundCorpus) = {
	<button class="o2_removeLink navButton"
			onclick={ event: Event => O2Controller.removeThisText(vCorp) }
	>remove</button>
}

@dom
def versionNavPrevButton(vCorp:O2Model.BoundCorpus) = {
	<button class="o2_prevLink navButton"
			onclick={ event: Event => O2Controller.getPrev(vCorp) }
			disabled= {
				(vCorp.currentPrev.bind == None)
			}
	>←</button>
}

@dom
def versionNavNextButton(vCorp:O2Model.BoundCorpus) = {
	<button class="o2_nextLink navButton"
			onclick={ event: Event => O2Controller.getNext(vCorp) }
			disabled= {
				(vCorp.currentNext.bind == None)
			}
	>→</button>
}



/* Global Navigation Buttons */
@dom
def nextButton = {
	<button
	class="navButton"
	onclick={ event: Event => O2Controller.getNext }
	disabled= {
		(O2Model.currentNext.bind == None)
	}
	> → </button>
}

@dom
def prevButton = {
	<button
	class="navButton"
	onclick={ event: Event => O2Controller.getPrev }
	disabled= {
		(O2Model.currentPrev.bind == None)
	}
	> ← </button>
}

@dom
def citedWorksMenu = {
	<div id="o2_citedWorksMenu"
	class={
			if (O2Model.citedWorks.length.bind < 1) {
				"dropdown empty" 
			} else {
				"dropdown"
			} 
	} >
	<span id="o2_citedWorksMenuTitle">Add Text</span>
	{ O2View.citedWorksMenuItems.bind }
	</div>
}

@dom
def citedWorksMenuItems = {
	<div class="dropdown-content">
		{ O2View.loadCitedWorks.bind }
	</div>
}

@dom
def loadCitedWorks = {
	for (urn <- O2Model.citedWorks) yield {
		<p>
			{ workUrnSpan( urn, O2Model.textRepo.value.get.catalog.label(urn) ).bind }
			<br/>( { O2Model.textRepo.value.get.catalog.entriesForUrn(urn)(0).citationScheme  } )
		</p>
	}	
}



/* General-use functions for making clickable URNs */
@dom
def workUrnSpan(urn:CtsUrn, s:String) = {
	<span
	class="app_clickable"
	onclick={ event: Event => {
		O2Controller.insertFirstNodeUrn(urn)
		//O2Model.clearPassage
		}
	}>
	{ s }
	</span>
}

@dom
def passageUrnSpan(urn:CtsUrn, s:String) = {
	<span>
	{ s }
	</span>
	<a
	class="app_clickable"
	onclick={ event: Event => {
			val mouseEvent = event.asInstanceOf[MouseEvent]
			if (mouseEvent.metaKey){
				true
			} else {
				O2Controller.changeUrn(urn)
				false
			}
		}
	}
	href = { s"?urn=${urn}" } >
	{ urn.toString}
	</a>
}


}
