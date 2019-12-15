package org.dice.kgsmrstn.controller;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.dice.kgsmrstn.config.KgsmrstnRunConfig;
import org.dice.kgsmrstn.config.ModelDTO;
import org.dice.kgsmrstn.selector.TripleSelector;
import org.dice.kgsmrstn.selector.TripleSelectorFactory;
import org.dice.kgsmrstn.selector.TripleSelectorFactory.SelectorType;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

@RestController
public class KgsmrstnController {

	@GetMapping(value = "/kgraph/type/{type}/max/{max}/min/{min}",produces = MediaType.APPLICATION_JSON_VALUE)//, produces = "text/plain"
    public Boolean getKGraph( @PathVariable("type") String type, @PathVariable("max") int max, @PathVariable("min") int min) {
		
		final TripleSelectorFactory factory = new TripleSelectorFactory();
		TripleSelector tripleSelector = null;
		KgsmrstnRunConfig runConfig = new KgsmrstnRunConfig();
		runConfig.setSqparqlEndPoint("http://dbpedia.org/sparql");
		runConfig.setMinSentence(min);
		runConfig.setMaxSentence(max);
		runConfig.setSeed(System.nanoTime());
		runConfig.setSelectorType(type);
		
		List<Statement> triples;
		final Set<String> classes = new HashSet<>();
		classes.add("<http://dbpedia.org/ontology/Person>");
		classes.add("<http://dbpedia.org/ontology/Place>");
		classes.add("<http://dbpedia.org/ontology/Organisation>");
		
		SelectorType selectorType = runConfig.getSelectorTypeEnum();
		
		tripleSelector = factory.create(selectorType, classes,
				 new HashSet<>(), runConfig.getSqparqlEndPoint(), null, runConfig.getMinSentence(), runConfig.getMaxSentence(),
				runConfig.getSeed());
		
		triples = tripleSelector.getNextStatements();
		
		//Possible Solution #1,but written as a JSON file.
		Model m = ModelFactory.createDefaultModel();
		ListIterator<Statement> StmtIterator = triples.listIterator();
		try {
            while (StmtIterator.hasNext()) {
            	Statement stmt = (Statement) StmtIterator.next();
                m.add(stmt);
            }
        } 
        catch(Exception e){
        	e.printStackTrace();
        }
        FileOutputStream oFile = null;
		try {
			oFile = new FileOutputStream("./src/main/resources/output.json", false);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
        m = m.write(oFile, "RDF/JSON");
		if(!(m.isEmpty()))
			return true;
		else return false;
		
		
		/*FileOutputStream oFile;
		oFile = new FileOutputStream("output4.json", false);
		ResultSetFormatter.outputAsJSON(oFile, triples);*/
		
		
        
        //Sol #2,Exception thrown
        /*List<ModelDTO> list=new ArrayList<ModelDTO>();
		StmtIterator = triples.listIterator();
        try {
            while (StmtIterator.hasNext()) {
            	Statement stmt = (Statement) StmtIterator.next();
                Resource s = stmt.getSubject();
                Resource p = stmt.getPredicate();
                RDFNode o = stmt.getObject();
                ModelDTO modelDTO = new ModelDTO();
                modelDTO.setSubject(s);
                modelDTO.setPredicate(p);
                modelDTO.setObject(o);
                list.add(modelDTO);
            }
        } 
        catch(Exception e){
        	e.printStackTrace();
        }
        return list;*/
      
        
      //Sol #4,Exception again
        /*String triplesAsString  = null;
        ObjectMapper objectMapper = new ObjectMapper();
	    objectMapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
	    try {
			triplesAsString = objectMapper.writeValueAsString(triples);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    return triplesAsString;*/
	    
	  //Sol #3,Exception thrown
  		/*Gson json = new Gson();
  		String response = json.toJson(m);
        return response;*/
        
        
    }

}