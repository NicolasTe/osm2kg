package de.l3s.osmlinks.blocking;

import de.l3s.osmlinks.OSMRecord;
import de.l3s.osmlinks.Options;
import de.l3s.osmlinks.PostGreDB;
import de.l3s.osmlinks.Util;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * This class represnts the candidate generation step with respect
 * to geographic distance of the candidate entities to the OSM node.
 */
public class GeoBlocker extends AbstractBlocker{

    private PostGreDB db;
    private double threshold;
    private List<Integer> noCandidates;

    /*
     * @param threshold Threshold used for blocking, i.e. th_block
     */
    public GeoBlocker(double threshold) {
        this.db = new PostGreDB(Options.dbHost(), Options.dbName(), Options.dbUser(), Options.dbPassword(), Options.dbMaxConnections());
        this.threshold = threshold;
        this.noCandidates = new ArrayList<>();
    }

    /**
     * Closes the connection to the database
     */
    @Override
    public void destroy() {
        db.close();
    }

    /**
     * Given a OSM node, determines all entities which are in geograhpic proximity of th_block.
     * @param r The OSM node.
     * @return
     * @throws IOException
     * @throws SQLException
     */
    @Override
    public List<Candidate> generateCandidates(OSMRecord r) throws IOException,  SQLException {
        Connection con = db.getConnection();
        Statement stmt = con.createStatement();

        String tableName= Util.getTableName();
        String idColumn=Util.getIdCol();


        ResultSet rs = stmt.executeQuery("select  "+idColumn+", " +Util.getNameCol()+", "+
                "ST_Distance(geometry, ST_PointFromText('POINT("+r.getLat()+" "+r.getLon()+")', 4326)::geography)" +
                "\n " +
                "from " +tableName+" "+
                "where ST_DWithin(geometry, " +
                "ST_PointFromText('POINT("+r.getLat()+" "+r.getLon()+")', 4326)::geography, " +
                +threshold+");");


        List<Candidate> result = new ArrayList<>();
        while(rs.next()) {
            String id = rs.getString(1);
            String name = rs.getString(2);
            Double dist = rs.getDouble(3);
            Candidate c = new Candidate(id, name);
            c.setGeoDistance(dist);
            result.add(c);
        }

        rs.close();
        stmt.close();
        con.close();

        logCandidates(result.size());
        return result;
    }

    /**
     * Method use to keep track of candidate list sizes
     * @param n
     */
    private synchronized void logCandidates(int n) {
        noCandidates.add(n);
    }

    /**
     * Returns the average number of candidates
     * @return Number of candidates
     */
    public double getAvgCandidates() {
        double result=0;
        for (Integer i: noCandidates) {
            result += i;
        }

        result /= (double) noCandidates.size();
        return result;
    }

    /**
     * Returns the name of the blocker
     * @return The name
     */
    @Override
    public String getName() {
        return this.getClass().getSimpleName()+" "+threshold;
    }

    /**
     * Returns the geographic distance threshold
     * @return The geographic distance threshold
     */
    public double getThreshold() {
        return threshold;
    }
}
