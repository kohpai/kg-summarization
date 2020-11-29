# Onboard Document

## Update 2020-11-08
1. Apache server is for serving static web app. But if you already have IntelliJ Idea, you can just right click `index.html` and open with browser. Idea will provide a built-in web server for you.
2. For Hits and Salsa, we need to upload `.ttl` files. Not sure how this is supposed to work, the files tend to be very big (like 2.4gb), and Tomcat server is not allowing such size.
3. The Hits test script doesn't work, similar to 2., but it's Java that's not allowing the file to be read.
4. I'm now skipping LinkSUM algorithm, due to the missing of Apache Lucene index directory.

## Walkthrough

1. This directory consists of both KGSUM server and web application.
2. The web app is in `./src/main/resources/webapp`.
3. No need to install the Apache server. Spring Boot already provides Tomcat server.
4. The KG is in RDF format. Learn about RDF at https://www.w3.org/TR/rdf11-primer/.
5. The server fetches KG(RDF) data from http://dbpedia.org/.
6. Hence, the webapp doesn't need to upload the RDF data, just specify the dataset name (entity).
7. Get to know more about DBpedia at https://wiki.dbpedia.org/about.

## Suggestions

1. Proper abstraction of data layer.
2. Switch to React.js for maintainability, which leads to
3. Separation of webapp repository.
4. Commit `.idea/` to `git` as well, for consistency of code style.

## Problems

1. Can't successfully apply LinkSUM with `Goalkeeper association football` entity. Apache Lucene asks for index directory.
