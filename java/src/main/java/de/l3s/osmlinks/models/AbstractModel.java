package de.l3s.osmlinks.models;

import de.l3s.osmlinks.ProgressBar;
import de.l3s.osmlinks.blocking.Candidate;
import de.l3s.osmlinks.OSMRecord;
import org.apache.lucene.queryparser.classic.ParseException;


import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Vector;

/**
 * This class is a prototype for linking models.
 */
public abstract class AbstractModel {

    /**
     * Given a OSMRecord, this method returns the entity to which a link should be created.
     * @param r The OSMRecord
     * @param foldNo The current fold
     * @return The entity to which a link should be created
     * @throws IOException
     * @throws SQLException
     */
    public Candidate findLink(OSMRecord r, int foldNo) throws IOException,  SQLException, ParseException {
        return findLink(r);
    }


    /**
     *  Given a OSMRecord, this method returns the entity to which a link should be created.
     * @param r The OSMRecord
     * @return The entity to which a link should be created
     * @throws IOException
     * @throws SQLException
     */
    public abstract Candidate findLink(OSMRecord r) throws IOException, SQLException, ParseException;

    /**
     * Destroys the models (e.g. closes database connections)
     * @throws SQLException
     */
    public void destroy() throws SQLException {};


    public Map<Double, Candidate> learnFindLink(OSMRecord osmRecord) throws SQLException, IOException {
        System.err.println("Learn find link Not implemented!");
        System.exit(99);
        return null;
    }

        /**
         * Starts the training process of the model
         * @param train Path to training data
         * @param test Path to test data (not used in the training process)
         * @param foldNo Number of the current fold
         * @param trainProgress Progressbar that reflects the current trainProgress
         * @throws IOException
         */
    public void train(Vector<OSMRecord> train, Vector<OSMRecord> test, int foldNo, ProgressBar trainProgress) throws IOException {
        trainProgress.stop();
    };

    /**
     * Sets the id of the current experiment
     * @param experimentId
     */
    public void setExperimentId(int experimentId) {
    }

    /**
     * Returns a dummy id for the parameter set
     * @return The dummy id
     */
    public int getParamId() {
        //id for non Embedding Models
        return 1;
    }
}
