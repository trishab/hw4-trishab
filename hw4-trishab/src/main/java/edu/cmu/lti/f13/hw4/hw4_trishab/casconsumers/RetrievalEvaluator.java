package edu.cmu.lti.f13.hw4.hw4_trishab.casconsumers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.collection.CasConsumer_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSList;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;
import org.apache.uima.util.ProcessTrace;

import edu.cmu.lti.f13.hw4.hw4_trishab.typesystems.Document;
import edu.cmu.lti.f13.hw4.hw4_trishab.typesystems.Token;
import edu.cmu.lti.f13.hw4.hw4_trishab.utils.Utils;

public class RetrievalEvaluator extends CasConsumer_ImplBase {

	/** query id number **/
	public HashSet<Integer> qIdList;

	/** find query id and connects to string **/
	public HashMap<Integer, Map<String, Integer>> QidQuery;
	/** Save score of qid**/ 
	public HashMap<Integer, ArrayList<Double>> QidScoreList;	
	
	/**Save score of the incorrect candidates**/
	public HashMap<Integer, ArrayList<Map<String, Integer>>> QidCandidateList;
	
	/** Save correct candidates of a qid **/
  public HashMap<Integer, Map<String, Integer>> QidCorrectCandidate;
	
  /** Save score of the correct candidate **/
  public HashMap<Integer, Double> QidCorrectCandidateScore;
  

  /** Save ranks of the correct candidates **/
  public HashMap<Integer, Double> Ranks;
  
  /** Save text of the correct candidates **/
  public HashMap<Integer, String> QidCorrectCandidateText;
  
  
	public void initialize() throws ResourceInitializationException {

		qIdList = new HashSet<Integer>();

		QidQuery = new HashMap<Integer, Map<String, Integer>>();

	    QidCorrectCandidate = new HashMap<Integer, Map<String, Integer>>();

	    QidCandidateList = new HashMap<Integer, ArrayList<Map<String, Integer>>>();

	    QidScoreList = new HashMap<Integer, ArrayList<Double>>();

	    QidCorrectCandidateScore = new HashMap<Integer, Double>();

	    Ranks = new HashMap<Integer, Double>();

	    QidCorrectCandidateText = new HashMap<Integer, String>();

	}

	/**
	 * TODO :: 1. construct the global word dictionary 2. keep the word
	 * frequency for each sentence
	 */
	@Override
	public void processCas(CAS aCas) throws ResourceProcessException {

		JCas jcas;
		try {
			jcas =aCas.getJCas();
		} catch (CASException e) {
			throw new ResourceProcessException(e);
		}

		FSIterator it = jcas.getAnnotationIndex(Document.type).iterator();
	
		if (it.hasNext()) {
			Document doc = (Document) it.next();

			//Make sure that your previous annotator populated this in CAS
			FSList fsTokenList = doc.getTokenList();
			ArrayList<Token>tokenList=Utils.fromFSListToCollection(fsTokenList, Token.class);

		    Map<String, Integer> tokenListMap = TokenListToMap(tokenList);

		      Integer Qid = doc.getQueryID();
		      Integer Rel = doc.getRelevanceValue();
		      qIdList.add(Qid);
		      
			//Do something useful here
		      if (Rel == 99) {
		          QidQuery.put(Qid, tokenListMap);
		        } else if (Rel == 1) {
		          QidCorrectCandidate.put(Qid, tokenListMap);
		          QidCorrectCandidateText.put(Qid, doc.getText());
		        } else if (Rel == 0) {
		          if (!QidCandidateList.containsKey(Qid)) {
		            QidCandidateList.put(Qid, new ArrayList<Map<String, Integer>>());
		          }
		          QidCandidateList.get(Qid).add(tokenListMap);
		        }
		}

	}

	/**
	 * TODO 1. Compute Cosine Similarity and rank the retrieved sentences 2.
	 * Compute the MRR metric
	 */
	@Override
	public void collectionProcessComplete(ProcessTrace arg0)
			throws ResourceProcessException, IOException {

		super.collectionProcessComplete(arg0);

		// TODO :: compute the cosine similarity measure
		
		 for (Integer qid : qIdList) {
		      Map<String, Integer> queryMap = QidQuery.get(qid);

		      Map<String, Integer> correctCandidateMap = QidCorrectCandidate.get(qid);

		      QidCorrectCandidateScore.put(qid, computeCosineSimilarity(queryMap, correctCandidateMap));

		      int j = 0;
		      System.out.println(QidCandidateList.get(qid));
		      for (Map<String, Integer> candidateMap : QidCandidateList.get(qid)) {
		        if (!QidScoreList.containsKey(qid)) {
		          QidScoreList.put(qid, new ArrayList<Double>());
		        }
		        QidScoreList.get(qid).add(computeCosineSimilarity(queryMap, candidateMap));
		      }
		    }
		
		// TODO :: compute the rank of retrieved sentences
		
		 for (Integer qid : qIdList) {

		      Collections.sort(QidScoreList.get(qid), new Comparator<Double>() {
		        public int compare(Double a, Double b) {
		          if (b > a)
		            return 1;
		          return -1;
		        }
		      });

		      Double correctCandidateScore = QidCorrectCandidateScore.get(qid);
		      int i;
		      for (i = 0; i < QidScoreList.get(qid).size(); ++i) {
		        if (correctCandidateScore > QidScoreList.get(qid).get(i)) {
		          break;
		        }
		      }
		      Ranks.put(qid, 1. + i);

		      System.out.format("Score:%f\trank=%d\trel=1 qid=%d\t%s\n", correctCandidateScore, 1 + i, qid,
		              QidCorrectCandidateText.get(qid));

		    }
		
		// TODO :: compute the metric:: mean reciprocal rank
		double metric_mrr = compute_mrr();
		System.out.println(" (MRR) Mean Reciprocal Rank ::" + metric_mrr);
	}
	  private Map<String, Integer> TokenListToMap(ArrayList<Token> input) {
		    HashMap<String, Integer> answer = new HashMap<String, Integer>();
		    for (Token token : input) {
		      answer.put(token.getText(), token.getFrequency());
		    }
		    return answer;
		  }
	/**
	 * 
	 * @return cosine_similarity
	 */
	private double computeCosineSimilarity(Map<String, Integer> queryVector,
			Map<String, Integer> docVector) {
		double cosine_similarity=0.0;
		 double queryLength = 0;

		    double docLength = 0;

		    for (String s : queryVector.keySet()) {
		      queryLength = queryLength + queryVector.get(s) * queryVector.get(s);
		    }
		    queryLength = Math.sqrt(queryLength);

		    for (String s : docVector.keySet()) {
		      docLength = docLength + docVector.get(s) * docVector.get(s);
		    }

		    docLength = Math.sqrt(docLength);

		    for (String s : queryVector.keySet()) {
		      if (docVector.containsKey(s)) {
		        cosine_similarity = cosine_similarity + queryVector.get(s) * docVector.get(s);
		      }
		    }

		    cosine_similarity = cosine_similarity / queryLength / docLength;

		    return cosine_similarity;
		  }


		// TODO :: compute cosine similarity between two sentences
		


	/**
	 * 
	 * @return mrr
	 */
	private double compute_mrr() {
		double metric_mrr=0.0;

		// TODO :: compute Mean Reciprocal Rank (MRR) of the text collection

	    Double count = 0.;

	    for (Integer qid : qIdList) {
	      metric_mrr = metric_mrr + 1 / Ranks.get(qid);
	      count = count + 1;
	    }

	    if (count > 0.5) {
	      metric_mrr = metric_mrr / count;
	    }		
		return metric_mrr;
	}

}
