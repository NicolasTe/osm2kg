package de.l3s.osmlinks;

import org.mozilla.universalchardet.UniversalDetector;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Utility class with miscellaneous methods.
 */
public class Util {

    /**
     *  Parases the OpenStreetMap data, i.e. the OSM nodes.
     * @param path Path to the file containing the OSM data.
     * @return Vector of all OSM nodes.
     * @throws IOException
     */
    public static Vector<OSMRecord> parseOSMRecords(String path) throws IOException {
        System.out.println("[OSM-Parser]: Parsing OSM Records...");
        Vector<OSMRecord> records = new Vector<>();

        List<Options.KGName> dBPediaKgs = Arrays.asList(Options.KGName.dbpedia_de, Options.KGName.dbpedia_it,Options.KGName.dbpedia_fr);

        try(BufferedReader br = new BufferedReader(new FileReader(path))) {
            boolean first=true;
            for(String line; (line = br.readLine()) != null; ) {
                if (first) {
                    first = false;
                    continue;
                }
                OSMRecord r = new OSMRecord(line);
                //only add records that link to the current KG
                if (r.getKgId().equals("")) {
                    continue;
                } else if (dBPediaKgs.contains(Options.getKGName())) {
                    String geoEntityFoundFlag = line.split("\t")[8];
                    if (geoEntityFoundFlag.equals("False")) continue;
                }

                records.add(r);
            }
        }
        System.out.println("[OSM-Parser]: Parsing OSM Records... done");

        return records;
    }

    /**
     * Returns the current id of the process
     */
    public static long getPID() {
        String processName =
                java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        return Long.parseLong(processName.split("@")[0]);
    }

    /**
     * Creates a dummy embedding. Used for debugging.
     * @param n Number of dimensions
     */
    public static List<Double> getDummyEmbedding(int n) {
        List<Double> result= new ArrayList<>();

        for (int i=0; i<n; ++i) {
            result.add(0d);
        }
        return result;
    }

    /**
     * Concers a list of doubles to a list of strings
     * @param in List of doubles
     * @return List of strings
     */
    public static List<String> listDoubleToString(List<Double> in) {
        List<String> result = new ArrayList<>();
        for (int i=0; i< in.size(); ++i) {
            result.add(in.get(i).toString());
        }
        return result;
    }

    /**
     * Get the name of the table in the database in wich the current KG is stored
     * @return Name of the KG table
     */
    public static String getTableName() {
        switch (Options.getKGName()) {
            case dbpedia_de:
                return "osmlinks.dbpedia_de";
            case dbpedia_it:
                return "osmlinks.dbpedia_it";
            case dbpedia_fr:
                return "osmlinks.dbpedia_fr";
            case wikidata:
                return "osmlinks.wikidata";
            default:
                return "osmlinks.wikidata";
        }
    }

    /**
     * Returns the name of the column in which the URI of the geo entity is stored
     * @return Column name of the URI
     */
    public static String getIdCol() {
        switch (Options.getKGName()){
            case dbpedia_de:
            case dbpedia_it:
            case dbpedia_fr:
                return "\"URI\"";
            case wikidata:
                return "wkid";
            default:
                return "wkid";
        }
    }

    /**
     * Returns the name of the column in which the name of the geo entity is stored
     * @return Column name of the entity name
     */
    public static String getNameCol() {
        switch (Options.getKGName()) {
            case dbpedia_de:
            case dbpedia_it:
            case dbpedia_fr:
                return "\"Name\"";
            case wikidata:
                return "name_en";
            default:
                return "name_en";
        }
    }


    /**
     * Converts nans to -1. Used for convinience when storing results in the database.
     * @param d Number to be checked for nan value
     * @return -1 if d is nan, otherwise d
     */
    public static double checkNan(double d) {
        if (Double.isNaN(d)) return -1;
        return d;
    }

}
