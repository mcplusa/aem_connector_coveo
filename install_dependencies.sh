# Add coveo-sdk Library to local Maven
echo "Installing coveo-sdk Library to local Maven"
mvn install:install-file -Dfile=dependency-jars/coveo-sdk-0.3.0.jar -DgroupId=com.mcplusa.coveo -DartifactId=coveo-sdk -Dversion=0.3.0 -Dpackaging=jar

# Add coveo-sdk-osgi Library to local Maven
echo "Installing coveo-sdk-osgi Library to local Maven"
mvn install:install-file -Dfile=dependency-jars/coveo-sdk-osgi-0.3.0.jar -DgroupId=com.mcplusa.coveo -DartifactId=coveo-sdk-osgi -Dversion=0.3.0 -Dpackaging=jar
