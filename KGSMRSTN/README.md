#Onboard Document
##Walkthrough
1. This directory consists of both KGSUM server and web application.
2. The web app is in `./src/main/resources/webapp`.
3. No need to install the Apache server. Spring Boot already provides Tomcat server.
4. The KG is in RDF format. Learn about RDF at https://www.w3.org/TR/rdf11-primer/.
5. The server fetches KG(RDF) data from http://dbpedia.org/.
6. Hence, the webapp doesn't need to upload the RDF data, just specify the dataset name (entity).
7. Get to know more about DBpedia at https://wiki.dbpedia.org/about.

##Suggestions
1. Proper abstraction of data layer.
2. Switch to React.js for maintainability, which leads to
3. Separation of webapp repository.
4. Commit `.idea/` to `git` as well, for consistency of code style.

##Problems
1. Can't successfully apply LinkSUM with `Goalkeeper association football` entity. Apache Lucene asks for index directory.