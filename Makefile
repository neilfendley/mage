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

# JVM --add-opens flags for Java 17+ are in .mvn/jvm.config
# (auto-loaded by Maven on all platforms)

# KrenkoMain config (override with: make run-krenko GAMES=5 THREADS=4)
GAMES   ?= 30
TESTS   ?= 4
TURNS   ?= 25
THREADS ?= 10
BUDGET  ?= 1000
SKILL   ?= 6
DECK    ?= decks/IzzetElementals.dck

.PHONY: run-krenko
run-krenko:
	mvn -pl Mage.Tests exec:java \
		-Dexec.mainClass="org.mage.test.AI.KrenkoMain" \
		-Dexec.classpathScope=test \
		-Dkrenko.games=$(GAMES) \
		-Dkrenko.tests=$(TESTS) \
		-Dkrenko.maxTurns=$(TURNS) \
		-Dkrenko.threads=$(THREADS) \
		-Dkrenko.searchBudget=$(BUDGET) \
		-Dkrenko.minimaxSkill=$(SKILL) \
		-Dkrenko.deck=$(DECK)

.PHONY: run-server
run-server:
	cd Mage.Server && mvn exec:java \
		-Dexec.mainClass="mage.server.Main" \
		-Dxmage.dataCollectors.rlTrainingData=true

.PHONY: run-client
run-client:
	cd Mage.Client && mvn exec:java \
		-Dexec.mainClass="mage.client.MageFrame"

