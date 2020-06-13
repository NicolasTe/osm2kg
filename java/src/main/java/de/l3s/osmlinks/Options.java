package de.l3s.osmlinks;


import com.sun.org.apache.xpath.internal.operations.Mod;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * This class represents the options parsed from the configuration file.
 */
public class Options {

    public static boolean debug;


    public enum KGName {
        dbpedia_de, dbpedia_it, dbpedia_fr, wikidata;
    }

    public enum ModelName {
        embedding;
    }

    private static Properties config;

    /**
     * Parses a configuration file
     * @param path Path to the configuration file.
     * @throws IOException
     */
    public static void parseConfig(String path) throws IOException {
        //read config
        config = new Properties();
        BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
        config.load(input);
        input.close();

        if (config.containsKey("debug")) {
            debug=Boolean.parseBoolean(config.getProperty("debug"));
        } else {
            debug=false;
        }

    }

    /*
    The following methods simply return the values specified in the configuration file.
     */

    public static String getOSMPath() {
        return config.getProperty("OSMPath");
    }


    public static List<ModelName> getModels() {
        List<ModelName> result = new ArrayList<>();
        String[] input = config.getProperty("models").split(",");

        for (int i=0; i<input.length; ++i) {
            switch (input[i]) {
                case "embedding":
                    result.add(ModelName.embedding);
                    break;
            }
        }
        return result;
    }

    public static boolean print() {
        if (!config.containsKey("print")) {
            return false;
        } else {
            return  Boolean.parseBoolean(config.getProperty("print"));
        }
    }

    public static List<String> getOSMEmbeddingPaths() {
        String[] input = config.getProperty("osmEmbeddings").split(",");
        return Arrays.asList(input);
    }

    public static String getOsmTfIdfPath() {
        return config.getProperty("osmTfIdf");
    }

    public static String getKGEmbeddingPath() {
        if (config.containsKey("KGEmbeddingPath")) {
            return config.getProperty("KGEmbeddingPath");
        } else {
            return null;
        }
    }


    public static List<Double> getGeoThreshold() {
        String[] input = config.getProperty("geoThreshold").split(",");
        List<Double> result = new ArrayList<>();
        for (int i=0; i<input.length; ++i) {
            result.add(Double.parseDouble(input[i]));
        }
        return result;
    }

    public static String getMLModelPath() {
        return config.getProperty("MLModelPath");
    }

    public static int getNoFolds() {
        if (config.containsKey("NoFolds")) {
            return Integer.parseInt(config.getProperty("NoFolds"));
        } else {
            return 10;
        }
    }

    public static String getPythonCmd() {
        if (config.containsKey("pythonCmd")) {
            return config.getProperty("pythonCmd");
        } else {
            return "python3";
        }
    }

    public static boolean logCandidates() {
        if (config.containsKey("logCandidates")) {
            return Boolean.parseBoolean(config.getProperty("logCandidates"));
        } else {
            return false;
        }
    }

    public static List<String> features() {
        List<String> result = new ArrayList<>();
        if (config.containsKey("features")) {
            String[] features = config.getProperty("features").split(",");
            for (int i=0; i<features.length; ++i) {
                result.add(features[i]);
            }
        } else {
            result.add("distance");
            result.add("osm_embedding");
        }
        return result;
    }

    public static String getClassifier() {
        return config.getProperty("classifier");
    }

    public static String getKGFeaturePath() {
        return config.getProperty("featurePath");
    }

    public static String dbHost() {
        return config.getProperty("dbHost");
    }

    public static String dbUser() {
        return config.getProperty("dbUser");
    }

    public static String dbPassword() {
        return config.getProperty("dbPassword");
    }

    public static String dbName() {
        return config.getProperty("dbName");
    }

    public static String experimentName() {
        return config.getProperty("experimentName");
    }

    public static int dbMaxConnections() {
        if (config.containsKey("dbMaxConnections")) {
            return Integer.parseInt(config.getProperty("dbMaxConnections"));
        } else {
            return 40;
        }
    }

    public static KGName getKGName() {
        String kgString = config.getProperty("KGName");
        switch (kgString) {
            case "dbpedia_de":
                return KGName.dbpedia_de;
            case "dbpedia_it":
                return KGName.dbpedia_it;
            case "dbpedia_fr":
                return KGName.dbpedia_fr;
            case "wikidata":
                return KGName.wikidata;
            default:
                return KGName.wikidata;
        }
    }

}
