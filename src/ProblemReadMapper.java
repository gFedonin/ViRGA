import org.jdom2.Document;
import org.jdom2.Element;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Created by Геннадий on 08.12.2014.
 */
abstract class ProblemReadMapper extends Constants{

  KMerCounter counter;
  Aligner aligner;
  Interval[] problemIntervals;
  Logger logger;
  boolean debug;
  HashSet<String>[] leftReads;
  HashSet<String>[] rightReads;


  ProblemReadMapper(Document document){
    logger = Logger.getInstance(document);
    Element element = document.getRootElement().getChild("ConsensusBuilder").getChild("Reassembler");
    debug = Boolean.parseBoolean(element.getChildText("Debug"));
  }

  void init(ArrayList<ProblemRead> problemReads, KMerCounter counter, Aligner aligner,
                      Interval[] problemIntervals){
    this.counter = counter;
    this.aligner = aligner;
    this.problemIntervals = problemIntervals;
    if(debug){
      leftReads = new HashSet[problemIntervals.length];
      rightReads = new HashSet[problemIntervals.length];
      for(int i = 0; i < problemIntervals.length; i++){
        leftReads[i] = new HashSet<>();
        rightReads[i] = new HashSet<>();
      }
    }
  }

  abstract void mapProblemReadsLeft() throws InterruptedException;

  abstract void mapProblemReadsRight() throws InterruptedException;

  abstract void countLeftEdgeFreqs();

  abstract void countRightEdgeFreqs();

  abstract void insertLoops();

}
