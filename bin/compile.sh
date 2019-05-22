mvn -DskipTests=true clean install
mvn -DskipTests=true assembly:assembly -DdescriptorId=jar-with-dependencies
