package de.l3s.osmlinks.blocking;

import java.util.Objects;

/**
 * Class that represents a potential match (i.e. a knowledge graph entity)
 * for a given OSM node.
 */
public class Candidate {

    private String id;
    private String name;
    private double geoDistance;

    /**
     * Constructor
     * @param id ID of the entity
     */
    public Candidate(String id) {
        this.id = id;
    }

    public Candidate(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    /**
     * Static method to represent empty candidate sets.
     * @return A negative candidate
     */
    public static Candidate negativeHit() {
        return new Candidate("-1");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Candidate candidate = (Candidate) o;
        return Objects.equals(id, candidate.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public double getGeoDistance() {
        return geoDistance;
    }

    public void setGeoDistance(double geoDistance) {
        this.geoDistance = geoDistance;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
