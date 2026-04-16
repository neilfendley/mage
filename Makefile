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
	mvn install -DskipTests

.PHONY: package
package:
	# Packaging Mage.Client to zip
	cd Mage.Client && mvn package assembly:single
	# Packaging Mage.Server to zip
	cd Mage.Server && mvn package assembly:single
	# Copying the files to the target directory
	mkdir -p $(TARGET_DIR)
	cp ./Mage.Server/target/mage-server.zip $(TARGET_DIR)
	cp ./Mage.Client/target/mage-client.zip $(TARGET_DIR)

# Note that the proper install script is located under ./Utils/build-and-package.pl
# and that should be used instead. This script is purely for convenience.
# The perl script bundles the artifacts into a single zip
.PHONY: install
install: clean build 


.PHONY: run-server
run-server:
	cd Mage.Server && mvn exec:java \
		-Dexec.mainClass="mage.server.Main" \
		-Dxmage.dataCollectors.rlTrainingData=true

.PHONY: run-client
run-client:
	cd Mage.Client && mvn exec:java \
		-Dexec.mainClass="mage.client.MageFrame"