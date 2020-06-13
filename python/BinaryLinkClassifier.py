#!/usr/bin/env python3


#   This script contains the implementation of the supervised classifcation model
#   of the OSM2KG link discovery approach. It classifies whether an knowledge
#   graph entity and and OpenStreetMap node represent the same real world entity.
#   This script is programmatically run by the Java implementation of OSM2KG.
#
#   The following arguments are required:
#
#   1) Path to the training data
#   2) Path to the test data
#   3) Path to the config file
#   4) Id of the current experiment
#   5) Number of the current fold
#
# ==============================================================================


import sys
import numpy as np
import pandas as pd

from sklearn import svm
from sklearn.naive_bayes import GaussianNB
from sklearn.neural_network import  MLPClassifier
from sklearn.tree import DecisionTreeClassifier
from sklearn.ensemble import RandomForestClassifier
from sklearn.neighbors import KNeighborsClassifier
from sklearn.preprocessing import StandardScaler
from sklearn import metrics
from sklearn.model_selection import RandomizedSearchCV
from xgboost import XGBClassifier

from sklearn.linear_model import LogisticRegression
from imblearn.over_sampling import SMOTE
from sklearn.svm import SVC

import multiprocessing
import psycopg2

numJobs = max(1, multiprocessing.cpu_count() // 9)
np.random.seed(2)

dbHost=""
dbName=""
dbUser=""
dbPassword=""
debug=True

# Runs random search to determine the best hyper parameters.
# Also computes the predictions on the train set.
def runOptimization(clf, searchSpace, x_train, y_train, x_test):
    clName = type(clf).__name__
    print("Fitting: "+clName, file=sys.stderr)

    if clName == "DecisionTreeClassifier":
        niter=2
    else:
        niter=100

    if debug:
        clf.fit(x_train, y_train)
        return clf.predict(x_test), clf

    random_search = RandomizedSearchCV(clf, param_distributions=searchSpace, n_iter=niter, n_jobs=numJobs, \
                                       scoring=metrics.make_scorer(metrics.f1_score, average='macro'),\
                                       random_state=2)

    random_search.fit(x_train, y_train)
    print("Evaluating: "+clName, file=sys.stderr)
    pred = random_search.predict(x_test)
    return (pred, random_search)


# Parses the configuration file
def parseConfig(configPath):
    global debug, dbHost, dbName, dbUser, dbPassword

    with open(configPath, 'r') as fi:
        for l in fi:
            try:
                k,v = l.strip().split("=")
                if k == "classifier":
                    models=v.split(",")
                if k=="debug":
                    debug=(v=="true")
                if k=="dbHost":
                    dbHost=v
                if k=="dbUser":
                    dbUser=v
                if k=="dbName":
                    dbName=v
                if k=="dbPassword":
                    dbPassword=v
            except:
                continue
    return models


# Parses the train and test data created by the OSM2KG Java implementation
def parseData(dataPath):
    data = pd.read_csv(dataPath, sep="\t", header=None)
    data.columns = ['osmID', 'KGID', 'label'] + list(data.columns[3:])

    osmIDs = data.osmID.as_matrix()
    KGIDs = data.KGID.as_matrix()
    labels = data.label.as_matrix()
    features = data.iloc[:, 3:].as_matrix()

    return features, labels, osmIDs, KGIDs


# Instantiates a classification model according to the configuration
def createModel(modelNames):
    for t in modelNames:
        if t=="svmlin":
            clf = svm.SVC(kernel="linear", probability=True)
            searchSpace = {'C': [x / 100.0 for x in range(0, 5 * 100)], 'tol': [x / 10000.0 for x in range(0, 1 * 100)]}
        elif t=="dtree":
            clf = DecisionTreeClassifier()
            searchSpace = {'criterion': ["gini", "entropy"]}
        elif t=="rf":
            clf = RandomForestClassifier(random_state=3)
            searchSpace = {'criterion': ["gini", "entropy"], 'n_estimators': range(5, 21)}
        elif t=="knn":
            clf = KNeighborsClassifier()
            searchSpace={'n_neighbors': [x+1 for x in range(10)], 'weights': ['uniform', 'distance'],
                         'leaf_size': [x+10 for x in range(40)]}
        elif t=="logistc_reg":
            clf = LogisticRegression()
            searchSpace = {'C': [x / 100.0 for x in range(0, 5 * 100)], 'tol': [x / 10000.0 for x in range(0, 1 * 100)]}
        elif t=="svm":
            clf = SVC(probability=True)
            searchSpace = {'C': [x / 100.0 for x in range(0, 5 * 100)], 'tol': [x / 10000.0 for x in range(0, 1 * 100)]}
        elif t=="nb":
            clf = GaussianNB()
            searchSpace={}
        elif t=='mlp':
            clf = MLPClassifier(hidden_layer_sizes=(10, ))
            searchSpace={}
        elif t == 'xgb':
            clf = XGBClassifier()
            searchSpace={}


    return (clf,searchSpace)


# Computes scores for prediction perfomance (not link discovery performance) and
# stores the scores in the database.
def computeScoresAndSaveToDB(pred, test, experimentId, fold, cur, model):
    classifier =  type(model[0]).__name__

    labels = np.array(['correct', 'incorrect'])
    conf_mat = metrics.confusion_matrix(test, pred, labels=labels)
    prec = metrics.precision_score(test, pred, average=None, labels=labels)
    recall = metrics.recall_score(test, pred, average=None, labels=labels)
    f1 = metrics.f1_score(test, pred, average=None, labels=labels)
    acc = metrics.accuracy_score(test, pred)

    prec_mic = metrics.precision_score(test, pred, average='micro', labels=labels)
    prec_mac = metrics.precision_score(test, pred, average='macro', labels=labels)
    rec_mic = metrics.recall_score(test, pred, average='micro', labels=labels)
    rec_mac = metrics.recall_score(test, pred, average='macro', labels=labels)
    f1_mic = metrics.f1_score(test, pred, average='micro', labels=labels)
    f1_mac = metrics.f1_score(test, pred, average='macro', labels=labels)

    cur.execute("INSERT INTO osmlinks.classification_results VALUES "+\
                "(%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)", \
                (experimentId, fold, classifier, \
                 prec[0], prec[1], prec_mic, prec_mac, \
                 recall[0], recall[1], rec_mic, rec_mac, \
                 f1[0], f1[1], f1_mic, f1_mac,\
                 acc, str(conf_mat)))

    return f1_mac


# Maim method that runs preprocessing and training and classification.
# Saves the predictions for the test set in a text file that is read
# by the OSM2KG Java implementation.
def run(trainPath, testPath, configPath, experimentId, fold) :
    modelNames = parseConfig(configPath)
    x_train, y_train, _, __ = parseData(trainPath)

    x_test, y_test, osmIDs, KGIds= parseData(testPath)

    x_train, y_train = SMOTE(random_state=1).fit_sample(x_train, y_train)


    m = createModel(modelNames)

    #preprocessing
    scaler = StandardScaler()
    x_train = scaler.fit_transform(x_train)
    x_test = scaler.transform(x_test)

    con = psycopg2.connect(host=dbHost, database=dbName, user=dbUser, password=dbPassword)
    cur = con.cursor()


    pred, clf = runOptimization(m[0], m[1], x_train, y_train, x_test)
    print("Determining scores", file=sys.stderr)
    score = computeScoresAndSaveToDB(pred, y_test, experimentId, fold, cur, m)
    print("Calculating probabilities", file=sys.stderr)
    proba = clf.predict_proba(x_test)
    print("Output", file=sys.stderr)

    if clf.classes_[0] == "correct":
        correctIndex=0
        incorrectIndex=1
    else:
        correctIndex=1
        incorrectIndex=0

    with open(testPath+"_pred", 'w') as fo:

        for n,_ in enumerate(osmIDs):
            out=[]
            out.append(str(osmIDs[n]))
            out.append(KGIds[n])
            out.append(str(pred[n]))
            out.append(str(proba[n][correctIndex]))
            out.append(str(proba[n][incorrectIndex]))
            print("\t".join(out), file=fo)

    con.commit()
    con.close()

# Parses arguments and calls the main method.
if __name__ == "__main__":
    trainPath=sys.argv[1]
    testPath=sys.argv[2]
    configPath=sys.argv[3]
    experimentId = sys.argv[4]
    fold = sys.argv[5]

    run(trainPath, testPath, configPath, experimentId, fold)
