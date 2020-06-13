# OSM2KG

## Contents

This repository contains an implementation of the OSM2KG approach for link discovery.

It consists of the following components.

1) /sql contains sql scripts to setup the Postgre database
2) /java contains an implementation of OSM2KG as well as the framework use for experiments
3) /python contains scripts to train the key-value embeddings as well as implementations of the supervised classification models used by OSM2KG.

## Key Components

The key component described in the paper are located in the following places:

1) The OSM2KG Approach is implemented in the java/src/main/java/anonym/osmlinks/models/EmbeddingModel.java class.
2) The key-value embeddings can be computed with the python/EmbeddingKeyValue.py script.
3) Experiments can be run by running the main method of the java/src/main/java/anonym/osmlinks/application/LinkingExperiment.java class.

## Setup

### Prerequisites 

The following components need to be installed in order to run OSM2KG:

Java 1.8

Python 3.6

Postgre SQL 9.6

PostGIS 2.3

### SQL
To setup the database run the sql files in the /sql directory vial the following commands:

psql < extensions.sql
psql < setup.sql

### Python

Install the modules specified in the requirements.txt via pip:

pip3 install -r requirements.txt 

### Java
Create a maven project using the provided pom.xml file.

## Configuration Parameters
The "config" files provides a sample configuration file. The following configuration parameters are mandatory:

dbHost  -  IP of the Postgre database

dbUser  -   Name of the database use

dbPassword -    Password of the database user

dbName -    Name of the database

dbMaxConnections -  Number of maximum allowed simultaneous connections

KGName  -    Name of the current knowledge graph

models -    Name of the considered model, set to "embedding" to run OSM2KG

osmEmbeddings - Path to key-value embeddings of OSM nodes

geoThreshold - Value of th_block in meters 

MlModelPath -   Path of the BinaryLinkClassifier.py file

classifier - Name of the supervised classification model. Currently supported: dtree (decision tree), rf (random forest), logistic_reg (logistic regression), nb (naive bayes)

NoFolds - Number folds used for cross fold validation

pythonCmd - path to the python interpreter

features - The set of considered features available features are types, statement_count (popularity), distance, osm_embedding

featurePath - Path to the features for the knowledegraph entities



## Running Experiments
Experiments can be run by calling the main method of the java/src/main/java/anonym/osmlinks/application/LinkingExperiment.java class. 
One ore more configuration file should be provided as argument.

## Training of Key-Value Embeddings

Key-value embeddings can be trained using the python/EmbeddingKeyValue.py script. The script requires the following arguments:

1: List of OpenStreetMap nodes and their tags in tsv format, i.e. each row should have the format nodeid \tab key \tab value

2: Number of epochs to compute

3: Size of the embedding, i.e. number of dimensions

4: Directory to save the embeddings


## License (MIT)
The MIT License (MIT)

Copyright 2019 Nicolas Tempelmeier

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.


