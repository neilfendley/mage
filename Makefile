-include .env

# The target directory is used for setting where the output zip files will end up
# You can override this with an environment variable, ex
# TARGET_DIR=my_custom_directory make deploy
# Alternatively, you can set this variable in the .env file
TARGET_DIR ?= deploy/

.PHONY: clean
clean:
	mvn clean

.PHONY: build
build:
	mvn install package -DskipTests

.PHONY: package
package:
	# Packaging Mage.Client to zip
	cd Mage.Client && mvn assembly:single
	# Packaging Mage.Server to zip
	cd Mage.Server && mvn assembly:single
	# Copying the files to the target directory
	mkdir -p $(TARGET_DIR)
	cp ./Mage.Server/target/mage-server.zip $(TARGET_DIR)
	cp ./Mage.Client/target/mage-client.zip $(TARGET_DIR)

# Note that the proper install script is located under ./Utils/build-and-package.pl
# and that should be used instead. This script is purely for convenience.
# The perl script bundles the artifacts into a single zip
.PHONY: install
install: clean build package

JAVA_OPENS = --add-opens java.base/java.io=ALL-UNNAMED \
	--add-opens java.base/java.lang=ALL-UNNAMED \
	--add-opens java.base/java.util=ALL-UNNAMED \
	--add-opens java.base/sun.misc=ALL-UNNAMED

.PHONY: run-krenko
run-krenko:
	MAVEN_OPTS="-Xmx4g" mvn -pl Mage.Tests exec:java \
		-Dexec.mainClass="org.mage.test.AI.KrenkoMain" \
		-Dexec.classpathScope=test

.PHONY: run-server
run-server:
	cd Mage.Server && MAVEN_OPTS="$(JAVA_OPENS)" mvn exec:java \
		-Dexec.mainClass="mage.server.Main" \
		-Dxmage.dataCollectors.rlTrainingData=true

.PHONY: run-client
run-client:
	cd Mage.Client && MAVEN_OPTS="$(JAVA_OPENS)" mvn exec:java \
		-Dexec.mainClass="mage.client.MageFrame"

