How to release to clojars:

Ensure you have committed

    git status

Get a list of tags.

    git tag -l
    
Decide on the next one and tag

    git tag 1.0
    
Build the jar and the Maven pom.xml

    lein jar
    lein pom
    
Push the code and tags 

    git push --tags
    
Release to clojars

    scp target/liberator-1.0.jar pom.xml clojars@clojars.org:
    
  
