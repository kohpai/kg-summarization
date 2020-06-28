package org.dice.kgsmrstn.controller;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.List;
import java.util.ListIterator;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.dice.kgsmrstn.config.KgsmrstnRunConfig;
import org.dice.kgsmrstn.selector.AbstractSummarizationSelectorHits;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class KgsmrstnController {
	
	private static final String ENDPOINT = "http://dbpedia.org/sparql";
	private org.slf4j.Logger log = LoggerFactory.getLogger(KgsmrstnController.class);

    @GetMapping(value = "/kgraphHits", produces = MediaType.APPLICATION_JSON_VALUE)//, produces = "text/plain"
    public String getKGraphHITS() {

    	log.info("In getKGraph");
    	
        
        KgsmrstnRunConfig runConfig = new KgsmrstnRunConfig();
        runConfig.setSqparqlEndPoint(ENDPOINT);

        List<Statement> triples;
            
        AbstractSummarizationSelectorHits Ash = new AbstractSummarizationSelectorHits(runConfig.getSqparqlEndPoint(), null);

        triples = Ash.getResources();

        //Possible Solution #1,but written as a JSON file.
        Model m = ModelFactory.createDefaultModel();
        ListIterator<Statement> StmtIterator = triples.listIterator();
        try {
            while (StmtIterator.hasNext()) {
                Statement stmt = (Statement) StmtIterator.next();
                m.add(stmt);
            }
        } catch (Exception e) {
            return "callback(" +
                    "{" +
                    "'status':" + false +
                    ",'msg' :\"" + e.getMessage() + "\"" +
                    "}" +
                    ")";
        }
        FileOutputStream oFile = null;
        try {
        	System.out.println("Data Wriiten");
            oFile = new FileOutputStream("./src/main/resources/webapp/summarized_18062020.ttl", false);
        } catch (FileNotFoundException e1) {
            return "callback(" +
                    "{" +
                    "'status':" + false +
                    ",'msg' :\"" + e1.getMessage() + "\"" +
                    "}" +
                    ")";
        }
        m = m.write(oFile, "Turtle");
        if (!(m.isEmpty())) {
            return "callback(" +
                "{" +
                "'status':" + true +
                "}" +
                ")";
        } else {
            return "callback(" +
                    "{" +
                    "'status':" + false +
                    ",'msg' :\"\"" +
                    "}" +
                    ")";
        }
    }
}
