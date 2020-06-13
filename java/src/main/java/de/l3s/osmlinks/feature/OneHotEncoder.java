package de.l3s.osmlinks.feature;

import de.l3s.osmlinks.models.EmbeddingModel;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class is a implementation of an one-hotencoder.
 */
public class OneHotEncoder {


    private int maxWords;
    private ConcurrentMap<String, AtomicInteger> wordCounts;
    private Map<String, Integer> wordToDimension;

    /**
     * Constructor
     * @param maxWords Number of maximum allowed words, i.e. maxmium number of dimensions.
     */
    public OneHotEncoder(int maxWords) {
        this.maxWords = maxWords;
        wordCounts = new ConcurrentHashMap<>();
        wordToDimension = new HashMap<>();
    }

    /**
     * Computes the encoding
     * @param train List of OpenStreetMap node knowledge graph entity pairs
     * @param KGToType Maps knowledgraph entities to types
     */
    public void fit(List<EmbeddingModel.Instance> train, Map<String, String> KGToType) {
        //determine wordcounts
        train.stream().parallel().forEach( r -> {
            String typeString = KGToType.get(r.kgID);


            if (typeString==null || typeString.equals("")) return;

            String[] types =typeString.split(",");
            for(String t: types) {
                if (!wordCounts.containsKey(t)) {
                    wordCounts.put(t, new AtomicInteger(0));
                }
                wordCounts.get(t).getAndIncrement();
            }
        });

        List<Entry<String, AtomicInteger>> sortedWords = new ArrayList<>(wordCounts.entrySet());

        sortedWords.sort((e1, e2) -> {
            return e2.getValue().get() - e1.getValue().get();
        });


        int limit = Math.min(maxWords, sortedWords.size());
        for(int i=0; i<limit; ++i) {
            wordToDimension.put(sortedWords.get(i).getKey(), i);
        }
    }

    /**
     * Encodes the pairs
     * @param records List of OpenStreetMap node knowledge graph entity pairs
     * @param KGToType Maps knowledgraph entities to types
     */
    public void transform(List<EmbeddingModel.Instance> records, Map<String, String> KGToType) {
            records.stream().parallel().forEach( r -> {
                String typeString = r.features.remove(0);

                if (typeString.equals("")) {
                        r.features.addAll(zeros(maxWords));
                    return;
                }

                String[] types =typeString.split(",");
                List<Integer> indices = new ArrayList<>();
                for(String s: types) {
                    if (wordToDimension.keySet().contains(s)) {
                        indices.add(wordToDimension.get(s));
                    }
                }
                r.features.addAll(nHot(indices, maxWords));
            });
        }

    /**
     * Creates a list containing zeros
     * @param n Number of zeors
     * @return List of zeros
     */
    private static List<String> zeros(int n) {
        List<String> result = new ArrayList<>();
        for(int i=0; i<n; ++i) {
                result.add("0");
            }
            return result;
    }

    /**
     * Transform a list of numbers to a vector where the corresponding dimension are set to 1.
     * E.g. 1,3 -> 1 0 1
     * @param n List of numbers
     * @param dim Number of dimensions
     * @return
     */
    private static List<String> nHot(List<Integer> n, int dim) {
        List<String> result = new ArrayList<>();
        for(int i=0; i<dim; ++i) {
            if (n.contains(i)) {
                result.add("1");
            } else {
                result.add("0");
            }
        }
        return result;
    }
}
