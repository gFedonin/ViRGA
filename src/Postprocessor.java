import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;

import java.io.*;
import java.util.ArrayList;
import java.util.TreeSet;

/**
 * Created by Gennady on 20.01.2016.
 */
class Postprocessor extends Constants{

  private float minIdentity;
  //private float threshold;
  private int minLen;
  private String outPath;
  private boolean debug;

  Postprocessor(Document document){
    Element element = document.getRootElement().getChild("Postprocessor");
    debug = Boolean.parseBoolean(element.getChildText("Debug"));
    outPath = document.getRootElement().getChildText("OutPath");
    minLen = Integer.parseInt(element.getChildText("MinFragmentLength"));
    minIdentity = Float.parseFloat(element.getChildText("MinIdentity"));
    //threshold = Float.parseFloat(element.getChildText("MinIdentityDifference"));
  }

  private void makeBlastDB(String[] assemblies, Reference[] refSeqs) throws IOException, InterruptedException{
    for(int i = 0; i < assemblies.length; i++){
      String seq = assemblies[i];
      String name = refSeqs[i].name;
      Process p = Runtime.getRuntime().exec("makeblastdb -in - -parse_seqids -dbtype nucl -title " +
        name + " -out " + outPath + name);
      BufferedWriter streamWriter =
          new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
      streamWriter.write(">" + name + "\n");
      streamWriter.write(seq);
      streamWriter.close();
      p.waitFor();
    }
    Process p = Runtime.getRuntime().exec("makeblastdb -in - -parse_seqids -dbtype nucl " +
        "-title refSeqDB -out " + outPath + "refSeqDB");
    BufferedWriter streamWriter =
        new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
    for(Reference seq: refSeqs){
      streamWriter.write(">" + seq.name + "\n");
      streamWriter.write(seq.seq);
      streamWriter.newLine();
    }
    streamWriter.close();
    p.waitFor();
  }

  private void deleteBlastDB(Reference[] refSeqs){
    for(Reference seq: refSeqs){
      File file = new File(outPath + seq.name + ".nhr");
      file.delete();
      file = new File(outPath + seq.name + ".nin");
      file.delete();
      file = new File(outPath + seq.name + ".nog");
      file.delete();
      file = new File(outPath + seq.name + ".nsd");
      file.delete();
      file = new File(outPath + seq.name + ".nsi");
      file.delete();
      file = new File(outPath + seq.name + ".nsq");
      file.delete();
    }
    File file = new File(outPath + "refSeqDB.nhr");
    file.delete();
    file = new File(outPath + "refSeqDB.nin");
    file.delete();
    file = new File(outPath + "refSeqDB.nog");
    file.delete();
    file = new File(outPath + "refSeqDB.nsd");
    file.delete();
    file = new File(outPath + "refSeqDB.nsi");
    file.delete();
    file = new File(outPath + "refSeqDB.nsq");
    file.delete();
  }

  private float refIdentity(HSP interval, Reference seq1, Reference seq2){
    int identity = 0;
    int len = 0;
    int start = seq1.seqToAln[interval.startS];
    int end;
    if(interval.endS == seq1.seq.length()){
      end = seq1.aln.length();
    }else{
      end = seq1.seqToAln[interval.endS];
    }
    for(int i = start; i < end; i++){
      char c1 = seq1.aln.charAt(i);
      char c2 = seq2.aln.charAt(i);
      if(c1 == GAP){
        if(c2 != GAP){
          len ++;
        }
      }else{
        if(c2 == c1){
          identity ++;
        }
        len ++;
      }
    }
    if(debug){
      System.out.println(seq1.name + " vs " + seq2.name);
      System.out.println(interval.startS + " " + interval.endS + " id = " + (float) identity/len);
    }
    return (float)identity/len;
  }

  private int chooseRef(HSP interval, String assembly, Reference ref1, Reference ref2, int readNum1, int readNum2) throws IOException, JDOMException, InterruptedException{
    String hitSeq = assembly.substring(interval.startQ, interval.endQ);
//    Process p = Runtime.getRuntime().exec(
//        "blastn -outfmt 5 -db refSeqDB", new String[]{"BLASTDB=" + outPath}, null);
    int threadNum = Runtime.getRuntime().availableProcessors();
    Process p = Runtime.getRuntime().exec(
            "blastn -num_threads " + threadNum + " -outfmt 10 -db refSeqDB", new String[]{"BLASTDB=" + outPath}, null);
    BufferedWriter streamWriter =
        new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
    streamWriter.write(hitSeq);
    streamWriter.close();
    ArrayList<BlastHit> list;
    if(debug){
      BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
      BufferedWriter writer = new BufferedWriter(new FileWriter(outPath + ref1.name +
          "_" + interval.startQ + "_" + interval.endQ + ".csv"));
      String line;
      while((line = reader.readLine()) != null){
        writer.write(line);
        writer.newLine();
      }
      writer.close();
      reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
      while((line = reader.readLine()) != null){
        System.out.println(line);
      }
      p.waitFor();
      list = BlastParser.parseBlastCSV(new FileInputStream(outPath + ref1.name +
          "_" + interval.startQ + "_" + interval.endQ + ".csv"));
    }else{
      list = BlastParser.parseBlastCSV(p.getInputStream());
      BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
      String line;
      while((line = reader.readLine()) != null){
        System.out.println(line);
      }
      p.waitFor();
    }
    //ArrayList<BlastHit> list = parseBlastXML(p.getInputStream());
//    ArrayList<BlastHit> list = BlastParser.parseBlastCSV(p.getInputStream());
//    p.waitFor();
    HSP bestInt1 = null;
    HSP bestInt2 = null;
    for(BlastHit hit: list){
      if(hit.hitID.equals(ref1.name)){
        int maxLen = 0;
        for(HSP interval1: hit.hsps){
          if(interval1.endQ - interval1.startQ > maxLen){
            maxLen = interval1.endQ - interval1.startQ;
            bestInt1 = interval1;
          }
        }
      }else if(hit.hitID.equals(ref2.name)){
        int maxLen = 0;
        for(HSP interval2: hit.hsps){
          if(interval2.endQ - interval2.startQ > maxLen){
            maxLen = interval2.endQ - interval2.startQ;
            bestInt2 = interval2;
          }
        }
      }
    }
    if(bestInt1 == null){
      if(bestInt2 != null){
        return 2;
      }
    }else{
      if(bestInt2 == null){
        return 1;
      }else{
//        if(Math.abs(bestInt1.identity - bestInt2.identity) < threshold){
//          return 12;
//        }
//        if(bestInt1.identity <= bestInt2.identity){
//          if(refIdentity(bestInt2, ref1, ref2) < minIdentity){
//            return 2;
//          }else{
//            return 12;
//          }
//        }else{
//          if(refIdentity(bestInt1, ref1, ref2) < minIdentity){
//            return 1;
//          }else{
//            return 12;
//          }
//        }
        if(readNum1 <= readNum2){
          if(refIdentity(bestInt2, ref1, ref2) < minIdentity){
            return 2;
          }else{
            return 12;
          }
        }else{
          if(refIdentity(bestInt1, ref1, ref2) < minIdentity){
            return 1;
          }else{
            return 12;
          }
        }
      }
    }
    return 12;
  }

  private ArrayList<HSP> getSimilarFragments(String seqName, String seq, String dbName) throws IOException, InterruptedException, JDOMException{
    ArrayList<HSP> hsps = new ArrayList<>();
//    Process p = Runtime.getRuntime().exec(
//        "blastn -outfmt 5 -db " + dbName, new String[]{"BLASTDB=" + outPath}, null);
    int threadNum = Runtime.getRuntime().availableProcessors();
    Process p = Runtime.getRuntime().exec(
            "blastn -num_threads " + threadNum + " -outfmt 10 -db " + dbName, new String[]{"BLASTDB=" + outPath}, null);
    BufferedWriter streamWriter =
        new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
    streamWriter.write(seq);
    streamWriter.close();
    ArrayList<BlastHit> list;
    if(debug){
      BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
      BufferedWriter writer = new BufferedWriter(new FileWriter(outPath + seqName + "-" + dbName + ".csv"));
      String line;
      while((line = reader.readLine()) != null){
        writer.write(line);
        writer.newLine();
      }
      writer.close();
      reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
      while((line = reader.readLine()) != null){
        System.out.println(line);
      }
      p.waitFor();
      list = BlastParser.parseBlastCSV(new FileInputStream(outPath + seqName + "-" + dbName + ".csv"));
    }else{
      list = BlastParser.parseBlastCSV(p.getInputStream());
      p.waitFor();
    }
    //ArrayList<BlastHit> list = parseBlastXML(p.getInputStream());
    //ArrayList<BlastHit> list = BlastParser.parseBlastCSV(p.getInputStream());
    //p.waitFor();
    if(list.isEmpty()){
      return hsps;
    }
    for(BlastHit blastHit: list){
      for(HSP hsp: blastHit.hsps){
        if(hsp.identity >= minIdentity){
          if(Math.min(hsp.endQ - hsp.startQ, hsp.endS - hsp.startS) >= minLen){
            hsps.add(hsp);
          }
        }
      }
    }
    return hsps;
  }

  static class SendToInterval implements Comparable{
    int start;
    int end;
    int assemblyID;

    SendToInterval(int s, int e, int id){
      start = s;
      end = e;
      assemblyID = id;
    }

    @Override
    public int compareTo(Object o){
      SendToInterval interval = (SendToInterval) o;
      return start - interval.start;
    }
  }

  ArrayList<SendToInterval>[] findRegionsToCut(String[] assemblies, Reference[] selectedRefs, int[] readNums) throws IOException, InterruptedException, JDOMException{
    makeBlastDB(assemblies, selectedRefs);
    ArrayList<SendToInterval>[] intervals = new ArrayList[assemblies.length];
    for(int i = 0; i < intervals.length; i++){
      intervals[i] = new ArrayList<>();
    }
    for(int i = 0; i < assemblies.length; i++){
      String seq_i = assemblies[i];
      Reference ref_i = selectedRefs[i];
      for(int j = i + 1; j < selectedRefs.length; j++){
        Reference ref_j = selectedRefs[j];
        ArrayList<HSP> similarFragments = getSimilarFragments(ref_i.name, seq_i, ref_j.name);
        for(HSP interval: similarFragments){
          int refID = chooseRef(interval, seq_i, ref_i, ref_j, readNums[i], readNums[j]);
          switch(refID){
            case 1:
              intervals[j].add(new SendToInterval(interval.startS, interval.endS, i));
              break;
            case 2:
              intervals[i].add(new SendToInterval(interval.startQ, interval.endQ, j));
              break;
          }
        }
      }
    }
    ArrayList<SendToInterval>[] res = new ArrayList[intervals.length];
    for(int j = 0; j < intervals.length; j++){
      ArrayList<SendToInterval> list = intervals[j];
      if(list.isEmpty()){
        res[j] = list;
        continue;
      }
      TreeSet<SendToInterval> sortedSet = new TreeSet<>(list);
      ArrayList<SendToInterval> fIntervals = new ArrayList<>();
      SendToInterval pivot = sortedSet.pollFirst();
      while(!sortedSet.isEmpty()){
        SendToInterval curr = sortedSet.pollFirst();
        if(pivot.end > curr.start){
          // intersection
          if(pivot.assemblyID == curr.assemblyID){
            if(curr.end > pivot.end){
              pivot.end = curr.end;
            }
          }else{
            if(pivot.end - pivot.start >= curr.end - curr.start){
              // cut or merge curr to pivot
              if(curr.end > pivot.end){
                curr.start = pivot.end;
                if(curr.end - curr.start >= minLen){
                  sortedSet.add(curr);
                }
              }
            }else{
              // cut or merge pivot to curr
              pivot.end = curr.start;
              if(pivot.end - pivot.start >= minLen){
                fIntervals.add(pivot);
              }
              pivot = curr;
            }
          }
        }else{
          fIntervals.add(pivot);
          pivot = curr;
        }
      }
      fIntervals.add(pivot);
      res[j] = fIntervals;
    }
    ///if(!debug){
      deleteBlastDB(selectedRefs);
    //}
    return res;
  }

}
