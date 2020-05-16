package org.dice.kgsmrstn.selector;

import static java.util.stream.Collectors.toCollection;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.Syntax;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.sparql.engine.http.QueryEngineHTTP;
import org.dice.kgsmrstn.controller.KgsmrstnController;
import org.dice.kgsmrstn.graph.BreadthFirstSearch;
import org.dice.kgsmrstn.graph.Node;
import org.dice.kgsmrstn.graph.PageRank;
import org.dice.kgsmrstn.util.TripleIndex;
import org.slf4j.LoggerFactory;

import edu.uci.ics.jung.graph.DirectedSparseGraph;

public class EntityTriplesSelector implements TripleSelector {

	private static final String DB_RESOURCE = "http://dbpedia.org/resource/";
	private static final String DB_ONTOLOGY = "http://dbpedia.org/ontology/";
	private static final String DB_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
	private static final String ALGORITHM = "linksum";

	private org.slf4j.Logger log = LoggerFactory.getLogger(KgsmrstnController.class);

	private String endpoint;
	private String entity;

	private Double alpha = 0.5;
	private Integer k;

	private Map<Node, List<String>> allAssociationsToAnEntity = new HashMap<Node, List<String>>();
	private Map<String, Long> globalPredicateFrequency = new HashMap<String, Long>();

	private DirectedSparseGraph<Node, String> g = new DirectedSparseGraph<Node, String>();

	public EntityTriplesSelector(String endpoint, String entity, Integer k) {
		this.endpoint = endpoint;
		this.entity = entity;
		this.k = k;
	}

	@Override
	public List<Statement> getNextStatements() {
		return getAllTriples();
	}

	private List<Statement> getAllTriples() {
		String resource = "<http://dbpedia.org/resource/" + entity + ">";
		String query = "SELECT ?s ?p  WHERE {" + "?s ?p ?o ." + "FILTER (?o =" + resource + ")"
				+ "FILTER (!regex(?p,'wikiPageWikiLink'))" + "FILTER (!regex(?p,'wikiPageRedirects'))"
				+ "FILTER (!regex(?p,'wikiPageDisambiguates'))" + "FILTER (!regex(?p,'primaryTopic'))"
				+ "} GROUP BY ?s ?p ORDER BY asc(?s)";

		Query sparqlQuery = QueryFactory.create(query, Syntax.syntaxARQ);

		QueryEngineHTTP httpQuery = new QueryEngineHTTP(endpoint, sparqlQuery);
		try {
			ResultSet results = httpQuery.execSelect();
			QuerySolution solution;
			results.getResultVars().stream().forEach(result -> System.out.println(result));
			log.info("Obtained results:", results.getRowNumber());
			while (results.hasNext()) {
				solution = results.next();

				try {
					String s = solution.get("s").toString();
					String p = solution.get("p").toString();

					Node subject = new Node(s, 0, 0, ALGORITHM);
					Node object = new Node(entity, 0, 1, ALGORITHM);

					List<String> associations;
					if (!allAssociationsToAnEntity.containsKey(subject)) {
						associations = new ArrayList<String>();
						associations.add(p);
						allAssociationsToAnEntity.put(subject, associations);
					} else {
						associations = allAssociationsToAnEntity.get(subject);
						if (!associations.contains(p))
							associations.add(p);
						// see if this's required
						allAssociationsToAnEntity.replace(subject, associations);
					}
					// initialize the predicate frequency to zero
					if (!globalPredicateFrequency.containsKey(p))
						globalPredicateFrequency.put(p, Long.valueOf(0));

					// add edge between these resources
					if (!(g.containsEdge(p)))
						g.addEdge(p + g.getEdgeCount() + ":", subject, object);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			httpQuery.close();
		}

		List<Node> allInitialNodes = g.getVertices().stream().collect(toCollection(ArrayList::new));
		// Expand the grph by a hop
		DirectedSparseGraph<Node, String> graph = runBFS(allInitialNodes);

		// Beginning Resource selection
		// Run Pagerank
		List<Node> allRankedNodes = runPageRank(graph);

		// collect pagerank scores for the resources connected to the target
		List<Node> rankedInitialNodes = allRankedNodes.stream().filter(node -> allInitialNodes.contains(node))
				.collect(Collectors.toList());

		// check for the presence of backlink and filter out the ones that do
		// not have it
		rankedInitialNodes = checkForBacklink(rankedInitialNodes, resource);
		List<Node> nodesHavingBacklink = rankedInitialNodes.stream().filter(node -> node.getBacklink())
				.collect(Collectors.toList());

		// Apply LinkSum score for all Resources and sort them by it
		nodesHavingBacklink = applyLinkSum(rankedInitialNodes, nodesHavingBacklink);
		nodesHavingBacklink.sort(Comparator.comparing(node -> node.getLinksumScore()));
		nodesHavingBacklink.stream().forEach(node -> System.out.println(
				node.getCandidateURI() + " : " + " PR: " + node.getPageRank() + " , LS: " + node.getLinksumScore()));
		// End of Resource Selection

		// Beginning Predicate selection
		// select predicates by frequency
		Map<Node, String> relevantAssocToAnEntity = electPredicatesByFrequency(allAssociationsToAnEntity,
				globalPredicateFrequency);

		Model model = ModelFactory.createDefaultModel();
		model = createModel(model, relevantAssocToAnEntity, entity, k);
		return model.listStatements().toList();
	}

	private Model createModel(Model model, Map<Node, String> relevantAssocToAnEntity, String entity, Integer topk) {

		Comparator<Entry<Node, String>> valueComparator = new Comparator<Entry<Node, String>>() {
			@Override
			public int compare(Entry<Node, String> e1, Entry<Node, String> e2) {
				Double v1 = e1.getKey().getLinksumScore();
				Double v2 = e2.getKey().getLinksumScore();
				return v2.compareTo(v1);
			}
		};

		List<Entry<Node, String>> listOfEntries = new ArrayList<Entry<Node, String>>(
				relevantAssocToAnEntity.entrySet());
		Collections.sort(listOfEntries, valueComparator);

		LinkedHashMap<Node, String> entityWithRelevantPredicateRanked = new LinkedHashMap<Node, String>();
		for (int index = 0; index < topk; index++) {
			entityWithRelevantPredicateRanked.put(listOfEntries.get(index).getKey(),
					listOfEntries.get(index).getValue());
		}

		entityWithRelevantPredicateRanked.forEach((subject, predicate) -> {
			Resource sub = model.createResource(subject.getCandidateURI());
			Property pred = model.createProperty(predicate);
			model.add(sub, pred, entity);
		});

		return model;
	}

	private Map<Node, String> electPredicatesByFrequency(Map<Node, List<String>> allAssociationsToAnEntity,
			Map<String, Long> globalPredicateFrequency) {

		// for every predicates in question,find its frequency of occurence in
		// the whole of Db
		globalPredicateFrequency.keySet().stream().forEach((predicate) -> {
			String query = "SELECT (str(COUNT(*))AS ?frequency)" + "WHERE {  ?subject predicate ?object" + "} ";
			query = query.replace("predicate", "<" + predicate + ">");
			Query sparqlQuery = QueryFactory.create(query, Syntax.syntaxARQ);
			QueryEngineHTTP httpQuery = new QueryEngineHTTP(endpoint, sparqlQuery);
			try {
				ResultSet resultSet = httpQuery.execSelect();
				QuerySolution solution = resultSet.next();
				Long frequency = Long.valueOf(solution.get("frequency").toString());
				globalPredicateFrequency.replace(predicate, frequency);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				httpQuery.close();
			}
		});

		Map<Node, String> relevantAssocToAnEntity = new HashMap<Node, String>();

		// place an entity with the most frequent predicate found in a group of
		// predicates linking that entity to target
		allAssociationsToAnEntity.forEach((subject, predicates) -> {
			System.out.println("subject: " + subject);
			Long val = 0L;
			String relPredicate = "";
			for (String predicate : predicates) {
				Long globalFreqVal = globalPredicateFrequency.get(predicate);
				val = (val > globalFreqVal) ? val : globalFreqVal;
				relPredicate = (val > globalFreqVal) ? "" : predicate;
			}

			relevantAssocToAnEntity.put(subject, relPredicate);
		});
		return relevantAssocToAnEntity;
	}

	private List<Node> applyLinkSum(List<Node> rankedInitialNodes, List<Node> nodesHavingBacklink) {
		double max = rankedInitialNodes.stream().mapToDouble(node -> node.getPageRank()).max()
				.orElseThrow(NoSuchElementException::new);

		for (Node node : nodesHavingBacklink) {
			double linksumScore = (alpha * (node.getPageRank() / max)) + (1 - alpha);
			node.setLinksumScore(linksumScore);
		}

		return nodesHavingBacklink;
	}

	private List<Node> checkForBacklink(List<Node> rankedNodes, String targetEntity) {

		// check for the presence of backlink between the resources and the
		// target and set accordingly the boolean status
		for (Node node : rankedNodes) {
			String query = "PREFIX dbo: <http://dbpedia.org/ontology/> \n" + "ASK "
					+ "FROM <http://dbpedia.org/page_links>"
					+ "{ source dbo:wikiPageWikiLink|^dbo:wikiPageWikiLink target" + "}";
			query = query.replace("source", "<" + node.getCandidateURI() + ">").replace("target", targetEntity);
			Query sparqlQuery = QueryFactory.create(query, Syntax.syntaxARQ);
			QueryEngineHTTP httpQuery = new QueryEngineHTTP(endpoint, sparqlQuery);
			try {
				Boolean truthValue = httpQuery.execAsk();
				if (truthValue)
					node.setBacklink(truthValue);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				httpQuery.close();
			}
		}
		return rankedNodes;
	}

	private List<Node> runPageRank(DirectedSparseGraph<Node, String> g) {

		// run pagerank,sort the nodes according to its score and return it
		PageRank pr = new PageRank();
		pr.runPr(g, 100, 0.001);

		ArrayList<Node> orderedList = new ArrayList<Node>();
		orderedList.addAll(g.getVertices());
		Collections.sort(orderedList);

		return orderedList;
	}

	private DirectedSparseGraph<Node, String> runBFS(List<Node> highRankNodes) {
		Integer maxDepth = 2;
		String edgeType = DB_ONTOLOGY;
		String nodeType = DB_RESOURCE;
		String edgeLabel = DB_LABEL;
		DirectedSparseGraph<Node, String> graph = new DirectedSparseGraph<Node, String>();
		for (Node node : highRankNodes) {
			graph.addVertex(node);
		}
		BreadthFirstSearch bfs = null;
		try {
			bfs = new BreadthFirstSearch(new TripleIndex(), ALGORITHM);
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			graph = bfs.run(maxDepth, graph, edgeType, nodeType, edgeLabel);
		} catch (UnsupportedEncodingException e) {
		} catch (IOException e) {
			e.printStackTrace();
		}

		return graph;
	}

}
