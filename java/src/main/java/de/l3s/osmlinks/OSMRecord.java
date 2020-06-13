package de.l3s.osmlinks;

/**
 * This class represents an OpenStreetMap node and only used to store data.
 */
public class OSMRecord {
    private String osmId;
    private String kgId;
    private String name;
    private String name_en;
    private String lat, lon;

    /**
     * Creates a OSMRecord from a line in the tsv file
     * @param tsvEntry Line in the tsv file.
     */
    public OSMRecord(String tsvEntry) {
        String[] cols = tsvEntry.split("\t");

        osmId = cols[0];
        lat = cols[1];
        lon =cols[2];
        name = cols[3];
        name_en = cols[4];

        switch (Options.getKGName()) {
            case dbpedia_de:
            case dbpedia_it:
            case dbpedia_fr:
                kgId=cols[7];
                break;
            case wikidata:
                kgId=cols[5];
                break;
        }
    }

    public String getLat() {
        return lat;
    }

    public String getLon() {
        return lon;
    }

    public String getOsmId() {
        return osmId;
    }

    public String getKgId() {
        return kgId;
    }

    public String getName() {
        return name;
    }

    public String getName_en() {
        if (name_en.equals("")) {
            return name;
        }
        return name_en;
    }

    @Override
    public String toString() {
        return "OSMRecord{" +
                "osmId='" + osmId + '\'' +
                ", kgId='" + kgId + '\'' +
                ", name='" + name + '\'' +
                ", name_en='" + name_en + '\'' +
                ", lat='" + lat + '\'' +
                ", lon='" + lon + '\'' +
                '}';
    }
}
