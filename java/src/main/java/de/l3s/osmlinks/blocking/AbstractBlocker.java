package de.l3s.osmlinks.blocking;

import de.l3s.osmlinks.OSMRecord;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Prototype for candidate generation implementations
 */
public abstract class AbstractBlocker {

    /**
     * Destroy the current blocker.
     */
    public void destroy() {
    }

    ;

    /**
     * Generates a list of candidates for a given OSM node.
     *
     * @param r The OSM node.
     * @return List of candidates
     * @throws IOException
     * @throws SQLException
     */
    public abstract List<Candidate> generateCandidates(OSMRecord r) throws IOException, SQLException;

    /**
     * Returns the name of the current blocker.
     *
     * @return The name.
     */
    public abstract String getName();
}
