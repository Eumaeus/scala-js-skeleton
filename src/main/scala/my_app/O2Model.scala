package reader

import com.thoughtworks.binding.{Binding, dom}
import com.thoughtworks.binding.Binding.{BindingSeq, Var, Vars}
import scala.scalajs.js
import scala.scalajs.js._
import scala.scalajs.js.Dynamic.{ global => g }
import scala.collection.mutable.LinkedHashMap
import org.scalajs.dom._
import org.scalajs.dom.ext._
import org.scalajs.dom.raw._
import edu.holycross.shot.cite._
import edu.holycross.shot.ohco2._
import edu.holycross.shot.citeobj._
import scala.collection.immutable.SortedMap
import monix.execution.Scheduler.Implicits.global
import monix.eval._

import scala.scalajs.js.annotation.JSExport
import js.annotation._

@JSExportTopLevel("O2Model")
object O2Model {

	var msgTimer:scala.scalajs.js.timers.SetTimeoutHandle = null

	case class VersionNodeBlock(versionUrn:Var[CtsUrn],nodes:Vars[CitableNode])

	case class BoundCorpus(versionUrn:Var[CtsUrn], versionLabel:Var[String], versionNodes:Vars[VersionNodeBlock], currentPrev:Var[Option[CtsUrn]] = Var[Option[CtsUrn]](None), currentNext:Var[Option[CtsUrn]] = Var[Option[CtsUrn]](None), versionsAvailable:Var[Int] = Var(1) )

	val currentCorpus = Vars.empty[BoundCorpus]

	val currentNumberOfCitableNodes = Var(0)
	val currentListOfUrns = Vars.empty[CtsUrn]
	val isRtlPassage = Var(false)

	// for navigation
	val urnHistory = Vars.empty[(Int,CtsUrn,String)]

	// Add an entry to the citation-history
	def updateUrnHistory(u:CtsUrn):Unit = {
		try {
			if (textRepo.value != None) {

				val tempList:List[Tuple2[CtsUrn,String]] = urnHistory.value.toList.reverse.map(t => { Tuple2(t._2, t._3)})
				val newTempList:List[Tuple2[CtsUrn,String]] = tempList ++ List(Tuple2(u,textRepo.value.get.label(u)))
				val indexedTempList:List[Tuple3[Int,CtsUrn,String]] = newTempList.zipWithIndex.map( t => {
					Tuple3((t._2 + 1),t._1._1,t._1._2)
				})
				val reversedList = indexedTempList.reverse
				urnHistory.value.clear
				for (t <- reversedList) { urnHistory.value += t }
			}
		} catch{
			case e:Exception => O2Controller.updateUserMessage(s"Unable to make label for ${u}: ${e}",2)
		}
	}

	// urn is what the user requested
	val urn = Var(CtsUrn("urn:cts:ns:group.work.version.exemplar:passage"))
	// displayUrn is what will be shown
	val displayUrn = Var(CtsUrn("urn:cts:ns:group.work.version.exemplar:passage"))
	//val versionsForCurrentUrn = Var(1)

	val userMessage = Var("")
	val userAlert = Var("default")
	val userMessageVisibility = Var("app_hidden")

	val textRepo = Var[Option[TextRepository]](None)
	val citedWorks = Vars.empty[CtsUrn]

	val currentNext = Var[Option[CtsUrn]](None)
	val currentPrev = Var[Option[CtsUrn]](None)


	/* Some methods for working the model */
	def versionsForUrn(urn:CtsUrn):Int = {
		O2Model.textRepo.value match {
			case None => 0
			case Some(tr) => {
				val s = s"urn:cts:${urn.namespace}:${urn.textGroup}.${urn.work}:"
				val versionVector = tr.catalog.entriesForUrn(CtsUrn(s))
				versionVector.size
			}
		}
	}

	def getPrevNextUrn(urn:CtsUrn):Unit = {
		O2Model.currentPrev.value = O2Model.textRepo.value.get.corpus.prevUrn(urn)
		O2Model.currentNext.value = O2Model.textRepo.value.get.corpus.nextUrn(urn)
	}

	def getPrevNextUrn(urn:CtsUrn, boundCorp:BoundCorpus):Unit = {
		boundCorp.currentPrev.value = O2Model.textRepo.value.get.corpus.prevUrn(urn)
		boundCorp.currentNext.value = O2Model.textRepo.value.get.corpus.nextUrn(urn)
	}

	def collapseToWorkUrn(urn:CtsUrn):CtsUrn = {
		val s = {
			urn.passageComponentOption match {
				case Some(pc) => s"urn:cts:${urn.namespace}:${urn.textGroup}.${urn.work}:${pc}"
				case None => s"urn:cts:${urn.namespace}:${urn.textGroup}.${urn.work}:"
			}
		}
		val u = CtsUrn(s)
		u
	}

	def updateCurrentListOfUrns(c:Corpus):Unit = {
		O2Model.currentListOfUrns.value.clear
		for (n <- c.nodes){
			O2Model.currentListOfUrns.value += n.urn
		}	
	}

	def removeTextFromCurrentCorpus(vCorp:O2Model.BoundCorpus):Unit = {
		try {
			val tempCorpus:Vector[O2Model.BoundCorpus] = {
				O2Model.currentCorpus.value.toVector.filter(_ != vCorp)
			}
			O2Model.currentCorpus.value.clear
			for ( tc <- tempCorpus){
				O2Model.currentCorpus.value += tc 
			}	

		} catch {
			case e:Exception => {
				O2Controller.updateUserMessage(s"O2Model Exception in 'removeTextFromCurrentCorpus': ${e}",2)
			}
		}
	}

	def removeTextFromCurrentCorpus(urn:CtsUrn):Unit = {
		try {
			val tempCorpus:Vector[O2Model.BoundCorpus] = {
				O2Model.currentCorpus.value.toVector.filter( vc => (urn >= vc.versionUrn.value) == false )
			}
			O2Model.currentCorpus.value.clear
			for ( tc <- tempCorpus){
				O2Model.currentCorpus.value += tc 
			}	

		} catch {
			case e:Exception => {
				O2Controller.updateUserMessage(s"O2Model Exception in 'removeTextFromCurrentCorpus': ${e}",2)
			}
		}
	}

	def updateTextInCurrentCorpus(oldurn:CtsUrn, newurn:CtsUrn):Unit = {
		removeTextFromCurrentCorpus(oldurn)	
		displayPassage(newurn)	
	}

	def updateCurrentCorpus(c:Corpus, u:CtsUrn):Unit = {
		try {
			//O2Model.currentCorpus.value.clear
			if (O2Model.textRepo.value != None) {
				// Since GroupBy doesn't preserve order, let's preserve our own order
				val versionLevelOrder:Vector[CtsUrn] = {
					c.urns.map(u => dropOneLevel(u)).distinct.toVector
				}
				// Get Corpus into a Vector of tuples: (version-level-urn, vector[CitableNode])
				val tempCorpusVector:Vector[(CtsUrn, Vector[CitableNode])] = c.nodes.groupBy(_.urn.dropPassage).toVector
				for (tc <- tempCorpusVector) {
					val versionLabel:String = O2Model.textRepo.value.get.catalog.label(tc._1)		
					val passageString:String = {
						u.passageComponentOption match {
							case Some(s) => s
							case None => ""
						}
					}
					val boundVersionLabel = Var(versionLabel)

					val versionUrn:CtsUrn = CtsUrn(s"${tc._1}${passageString}")
					val boundVersionUrn = Var(versionUrn)

					// Group node urns according to nodeBlocks
					val nodeBlocks:Vector[(CtsUrn, Vector[CitableNode])] = {
						tc._1.exemplarOption match {
							case Some(eo) => {
								val block:Vector[(CtsUrn,Vector[CitableNode])] = {
									tc._2.groupBy(n => dropOneLevel(n.urn)).toVector	
								}
								val block2 = tc._2.zipWithIndex.groupBy(n => dropOneLevel(n._1.urn))
								val lhm = LinkedHashMap(block2.toSeq sortBy (_._2.head._2): _*)
								val block3 = lhm mapValues (_ map (_._1))
								val sortedBlock = block3.toVector
								sortedBlock
							}
							case None => {
								Vector( (tc._1,tc._2)	)
							}
						}	
					}	
					// Get this nodeBlock into a versionNodeBlock
					val tempNodeBlockVec = Vars.empty[VersionNodeBlock]	
					for (b <- nodeBlocks){
						val tempBlockUrn = Var(b._1)
						val tempNodesVec = Vars.empty[CitableNode]
						for (n <- b._2) tempNodesVec.value += n
						tempNodeBlockVec.value += VersionNodeBlock(tempBlockUrn, tempNodesVec)
					}
					val newBoundCorpus:BoundCorpus = BoundCorpus(boundVersionUrn, boundVersionLabel, tempNodeBlockVec) 


				//	O2Model.currentCorpus.value += newBoundCorpus

					/* Sort corpora */
					val sortingCorpus:Vector[BoundCorpus] = O2Model.currentCorpus.value.toVector ++ Vector(newBoundCorpus)
					val sortedCorpora:Vector[BoundCorpus] = sortingCorpus.sortBy(_.versionUrn.value.toString)
					O2Model.currentCorpus.value.clear
					for ( tc <- sortedCorpora){
						O2Model.currentCorpus.value += tc 
					}

					val task = Task{ O2Model.getPrevNextUrn(newBoundCorpus.versionUrn.value, newBoundCorpus) }
					val future = task.runAsync
					val task2 = Task { newBoundCorpus.versionsAvailable.value = O2Model.versionsForUrn(newBoundCorpus.versionUrn.value) }
					val future2 = task2.runAsync
				}	

			}

		} catch {
			case e:Exception => {
				O2Controller.updateUserMessage(s"O2Model Exception in 'updateCurrentCorpus': ${e}",2)
			}
		}
	}

	def dropOneLevel(u:CtsUrn):CtsUrn = { 
		try {
			val passage:String = u.passageComponent
			val plainUrn:String = u.dropPassage.toString
			val newPassage:String = passage.split('.').dropRight(1).mkString(".")
			val droppedUrn:CtsUrn = CtsUrn(s"${plainUrn}${newPassage}") 
			droppedUrn
		} catch {
			case e:Exception => {
				O2Controller.updateUserMessage(s"Error dropping one level from ${u}: ${e}",2)
				throw new Exception(s"${e}")
			}
		}
	}


	def displayNewPassage(urn:CtsUrn):Unit = {
			O2Model.displayPassage(urn)
	}

	@dom
	def clearPassage:Unit = {
		//O2Model.xmlPassage.innerHTML = ""
		//O2Model.versionsForCurrentUrn.value = 0
		O2Model.currentListOfUrns.value.clear
		O2Model.currentCorpus.value.clear
	}

	def passageLevel(u:CtsUrn):Int = {
		try {
			val urn = u.dropSubref
			if (urn.isRange) throw new Exception(s"Cannot report passage level for ${urn} becfause it is a range.")
			urn.passageComponentOption match {
				case Some(p) => {
					p.split('.').size
				}
				case None => throw new Exception(s"Cannot report passage level for ${u} because it does not have a passage component.")
			}
		} catch {
			case e:Exception => throw new Exception(s"${e}")	
		}
	} 

	def valueForLevel(u:CtsUrn,level:Int):String = {
		try {
			val urn = u.dropSubref
			val pl:Int = passageLevel(urn)
			if (pl < level) throw new Exception(s"${u} has a ${pl}-deep citation level, which is less than ${level}.")
			urn.passageComponentOption match {
				case Some(p) => {
					p.split('.')(level-1)
				}
				case None => throw new Exception(s"Cannot report passage level for ${u} because it does not have a passage component.")
			}
		} catch {
			case e:Exception => throw new Exception(s"${e}")	
		}	
	}

	@dom
	def displayPassage(newUrn: CtsUrn):Unit = {
		val tempCorpus: Corpus = O2Model.textRepo.value.get.corpus >= newUrn
		O2Model.updateCurrentListOfUrns(tempCorpus)
		O2Model.updateCurrentCorpus(tempCorpus, newUrn)
		O2Model.currentNumberOfCitableNodes.value = tempCorpus.size
	}



def checkForRTL(s:String):Boolean = {
		val sStart = s.take(10)
		val arabicBlock = "[\u0600-\u06FF]".r
		val hebrewBlock = "[\u0591-\u05F4]".r
		var isRtl:Boolean = ((arabicBlock findAllIn sStart).nonEmpty || (hebrewBlock findAllIn sStart).nonEmpty)
		isRtl
}



	@dom
	def updateCitedWorks = {
		O2Model.citedWorks.value.clear
		for ( cw <- O2Model.textRepo.value.get.corpus.citedWorks){
			O2Model.citedWorks.value += cw
		}
	}


}
