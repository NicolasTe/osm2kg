#!/usr/bin/env python3

# This script can be used to train key-value embeddings for OpenStreetMap nodes.
# The following arguments are expected
#
# 1: List of OpenStreetMap nodes and their tags in tsv format, i.e. each row should have the format nodeid \tab key \tab value
# 2: Number of epochs to compute
# 3: Size of the embedding, i.e. number of dimensions
# 4: Directory to save the embeddings

# ==============================================================================

import tensorflow as tf
import numpy as np
import math
import datetime as dt
import collections
import os
import sys


NUM_VALUES=int(math.pow(10, 3))
BATCH_SIZE=1000
NUM_SAMPLED = 1000

VALIDATION_SIZE=15

# Parses a single line
def lineToIdKeyValue(l):
    parts = l.strip().split("\t")
    id = parts[0]
    key = parts[1]
    value = " ".join(parts[2:])
    return id,key,value


# Parses the OSM data
def parseData(input):
    print("Parsing data...")

    raw_ids=[]
    raw_keys=[]
    raw_values=[]

    with open(input, 'r', encoding="utf-8") as fi:
        for l in fi:
            id, key, value = lineToIdKeyValue(l)
            raw_ids.append(id)
            raw_keys.append(key)
            raw_values.append(value)

    return raw_ids, raw_keys, raw_values


# Encodes the OSM ids, keys and values
def encodeRecords(raw_ids, raw_keys, raw_values):

    print("Determining most common values...")
    count = [['UNK', -1]]
    count.extend(collections.Counter(raw_values).most_common(NUM_VALUES - 1))

    print("Encoding values...")
    values={}
    for v,_ in count:
        if not v in values:
            values[v]=len(values)


    print("Encoding data...")
    ids={}
    keys={}
    records=[]
    for i,_ in enumerate(raw_ids):
        if raw_ids[i] not in ids:
            ids[raw_ids[i]] = len(ids)

        if raw_keys[i] not in keys:
            keys[raw_keys[i]] = len(keys)

        #handle unknown values
        if raw_values[i] in values:
            v = raw_values[i]
        else:
            v = 'UNK'

        records.append((ids[raw_ids[i]], keys[raw_keys[i]], values[v]))

    noIds = len(ids)
    noKeys = len(keys)
    noVals = len(values)

    reversed_dictionary = dict(zip(ids.values(), ids.keys()))


    print("Number of ids: %s\tNumber of keys: %s\tNumber of values %s" % (str(noIds), str(noKeys), str(noVals)))
    return records, noIds, noKeys, noVals, reversed_dictionary


# Generates a batch for training with respect to the current progress
def generate_batch(records, batch_index):

    #2 records per key / value
    noRecords=BATCH_SIZE//2


    #determine size of current batch
    if len(records) < (batch_index+1)*noRecords:
        current_batch_size= (len(records)-batch_index*noRecords)*2
    else:
        current_batch_size=BATCH_SIZE


    batch_ids= np.ndarray(shape=(current_batch_size), dtype=np.int32)
    batch_keys_values = np.ndarray(shape=(current_batch_size, 1), dtype=np.int32)

    noRecords=current_batch_size//2

    for i in range(noRecords):

        #position of key and value in training data
        index_1 = i*2
        index_2=  i*2+1

        r = records[batch_index*noRecords+i]

        #keypart
        batch_ids[index_1] = r[0]
        batch_keys_values[index_1, 0] = r[1]

        #valuepart
        batch_ids[index_2]= r[0]
        batch_keys_values[index_2, 0] = r[2]

    return batch_ids, batch_keys_values


# Main method to train the embeddings. Constructs the Tensorflow graph and computes the embeddings.
def trainEmbeddings(input, records, noIds, noKeys, noVals, reverse_dictionary, epochs, embeddingsize, outDir, save=True):
    graph = tf.Graph()

    #construct Tensorflow Graph
    with graph.as_default():
        train_inputs = tf.placeholder(tf.int32, shape=[None])
        train_labels = tf.placeholder(tf.int32, shape=[None, 1])

        input_layer_size=noIds
        output_layer_size=noKeys+noVals

        embeddings = tf.Variable(tf.random_uniform([input_layer_size, embeddingsize], -1.0, 1.0), name="embeddings")
        embed = tf.nn.embedding_lookup(embeddings, train_inputs)

        #softmax
        weights = tf.Variable(tf.truncated_normal([output_layer_size, embeddingsize],
                                              stddev=1.0 / math.sqrt(embeddingsize)))
        biases = tf.Variable(tf.zeros([output_layer_size]))
        hidden_out = tf.matmul(embed, tf.transpose(weights)) + biases

        train_one_hot = tf.one_hot(train_labels, output_layer_size)
        cross_entropy = tf.reduce_mean(tf.nn.softmax_cross_entropy_with_logits(logits=hidden_out, labels=train_one_hot))

        optimizer = tf.train.GradientDescentOptimizer(1.0).minimize(cross_entropy)
        init = tf.global_variables_initializer()

        #Run training
        with tf.Session(graph=graph) as session:
            init.run()
            print("TensorFlow Initialization done")

            for step in range(epochs):
                # determine number of batches
                if len(records) % (BATCH_SIZE // 2) == 0:
                    noBatches = len(records) * 2 // BATCH_SIZE

                else:
                    noBatches = len(records) * 2  // BATCH_SIZE + 1

                for batch_index in range(noBatches):
                    instances, labels = generate_batch(records, batch_index)

                    feed_dict = {train_inputs: instances, train_labels: labels}

                    _, loss_val = session.run([optimizer, cross_entropy], feed_dict=feed_dict)

            if save:
                saveEmbeddings(input, session.run(embeddings), noIds, reverse_dictionary, epochs, embeddingsize, outDir)

# Saves the embeddings to tsv file.
def saveEmbeddings(input, embeddings, noIds, reverse_dictionary, epochs, embeddingsize,  outDir):
    fname  = outDir+"/"+os.path.split(input)[1]+"_embeddings_"+str(embeddingsize)+"_"+str(epochs)
    with open(fname, 'w') as fo:
        for i in range(noIds):
            embed = embeddings[i, :]
            id = reverse_dictionary[i]
            print("%s %s" % (id, " ".join(map(str, embed))), file=fo)

# Parses the data and calls the main training method.
def run(input, epochs, embeddingsize, outdir, save=True):
    raw_ids, raw_keys, raw_values = parseData(input)
    records, noIds, noKeys, noVals, reverse_dictionary, = encodeRecords(raw_ids, raw_keys, raw_values)

    start_time = dt.datetime.now()
    trainEmbeddings(input, records, noIds, noKeys, noVals, reverse_dictionary, epochs, embeddingsize, outdir, save=save)
    end_time = dt.datetime.now()
    print("Training of embeddings took {}".format((end_time-start_time).total_seconds()))

if __name__ == "__main__":
    # input file, n epochs, embeddingsize, outdir
    print("Starting training")
    run(sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), sys.argv[4])
