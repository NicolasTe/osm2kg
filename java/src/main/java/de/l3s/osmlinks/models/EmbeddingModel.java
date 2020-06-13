package de.l3s.osmlinks.models;

import de.l3s.osmlinks.*;
import de.l3s.osmlinks.blocking.AbstractBlocker;
import de.l3s.osmlinks.blocking.Candidate;
import de.l3s.osmlinks.blocking.GeoBlocker;
import de.l3s.osmlinks.feature.OneHotEncoder;
import org.apache.commons.text.similarity.JaroWinklerDistance;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * This class implements the OSM2KG model for link discovery.
 */
public class EmbeddingModel extends AbstractModel {

    private AbstractBlocker blocker;

    private Map<String, List<Double>> osmEmbeddings;
    private Map<String, List<Double>> osmTfIdf;
    private Map<String, Integer> KGStatementCount;
    private Map<String, String> KGTypes;

    private int experimentId;

    private String mlModelPath;
    private String configPath;
    private String osmEmbeddingPath;
    private String KGEmbeddingPath;
    private AtomicInteger kgNotFoundCounter;
    private double threshold;
    private List<String> features;


    private int sampleCandidates=10;
    private Map<Integer, Map<String, List<MLScore>>> foldToMLScore;
    private Map<Integer, Integer> foldToNoCandidate;

    //log variables
    private boolean logCandidates;
    private double avgNoCandidates;
    private double noOsmRecords;
    private Map<Integer, List<String>> candidateLog;
    private ProgressBar currentProgress;

    /**
     * Creates a model according to the current configurations
     * @param osmEmbeddingPath Path to the pretrained key-value embeddings
     * @param threshold Threshold for blocking, i.e. th_block
     * @param mlModelPath Path to the BinaryLinkClassifier.py file
     * @param configPath Path to the configuration file
     * @param logCandidates Specifies whether candidates should be logged in the database
     * @param features List of considered features
     * @param KGFeaturePath Path to the features fo the knowledge graph
     * @throws IOException
     * @throws SQLException
     */
    public EmbeddingModel(String osmEmbeddingPath,
                          String osmTfIdfPath,
                          String KGEmbeddingPath,
                          Double threshold, String mlModelPath,
                          String configPath,  boolean logCandidates,
                          List<String> features,
                          String KGFeaturePath) throws IOException, SQLException {

        this.mlModelPath = mlModelPath;
        this.configPath = configPath;
        this.osmEmbeddingPath=osmEmbeddingPath;
        this.KGEmbeddingPath=KGEmbeddingPath;
        this.threshold=threshold;
        this.logCandidates=logCandidates;
        this.features=features;
        this.avgNoCandidates=0;
        this.noOsmRecords=0;

        this.candidateLog=new ConcurrentHashMap<>();
        this.foldToMLScore=new ConcurrentHashMap();
        this.foldToNoCandidate=new ConcurrentHashMap<>();
        this.osmEmbeddings = new ConcurrentHashMap<>();
        this.osmTfIdf = new ConcurrentHashMap<>();
        this.blocker = new GeoBlocker(threshold);

        this.kgNotFoundCounter = new AtomicInteger(0);

        this.KGStatementCount = new ConcurrentHashMap<>();
        this.KGTypes = new ConcurrentHashMap<>();

        if (features.contains("osm_embedding")) {
            System.out.println("[EmbeddingModel]: Parsing OSM embeddings...");
            osmEmbeddings = parseEmbeddings(osmEmbeddingPath);
            System.out.println("[EmbeddingModel]: Parsing OSM embeddings... done");
        }

        if (features.contains("osm_tf_idf")) {
            System.out.println("[EmbeddingModel]: Parsing OSM tf_idf...");
            osmTfIdf = parseEmbeddings(osmTfIdfPath);
            System.out.println("[EmbeddingModel]: Parsing OSM tf_idf... done");

        }

        if (features.contains("types") || features.contains("statement_count")) {
            System.out.println("[EmbeddingModel]: Parsing types and statement counts... ");
            parseTypesAndStatementCounts(KGFeaturePath);
            System.out.println("[EmbeddingModel]: Parsing types and statement counts... done");

        }


    }

    /**
     * Parses the features for the knowledge graph
     * @param kgFeaturePath Path to the features fo the knowledge graph
     * @throws IOException
     */
    private void parseTypesAndStatementCounts(String kgFeaturePath) throws IOException {
        try(Stream<String> inputStream = Files.lines(new File(kgFeaturePath).toPath(), StandardCharsets.UTF_8)) {
            inputStream.parallel().forEach( line -> {
                String[] parts = line.split("\t");
                String id = parts[0];
                String types = parts[1];
                int stmtCount = Integer.parseInt(parts[2]);

                KGStatementCount.put(id, stmtCount);
                KGTypes.put(id, types);
            });
        }
    }


    /**
     * Parses embeddings where the id is in the first colum
     * @param embeddingPath Path of the pretrained embeddings
     * @return Map with embeddings
     * @throws IOException
     */
    private Map<String, List<Double>> parseEmbeddings(String embeddingPath) throws IOException {
        Map<String, List<Double>> result = new ConcurrentHashMap<>();

        try(Stream<String> inputStream = Files.lines(new File(embeddingPath).toPath(), StandardCharsets.US_ASCII)) {
            inputStream.parallel().forEach( line -> {
                String[] cols = line.split("\\s+");
                String id = cols[0];
                List<Double> vector=new ArrayList<>();
                for (int i=1; i< cols.length; ++i) {
                    vector.add(Double.parseDouble(cols[i]));
                }
                result.put(id, vector);
            });
        }

        return result;
    }

    /**
     * Trains the classification model
     * @param train Path to training data
     * @param test Path to test data (not used in the training process)
     * @param foldNo Number of the current fold
     * @throws IOException
     */
    @Override
    public void train(Vector<OSMRecord> train, Vector<OSMRecord> test, int foldNo, ProgressBar trainProgress) throws IOException {
        currentProgress = trainProgress;

        Map<String, List<MLScore>> mlscores = new ConcurrentHashMap<>();

        List<Instance> featureTrain = BlockAndTransformToFeatureSpace(train, true);
        List<Instance> featureTest = BlockAndTransformToFeatureSpace(test, false);

        System.out.println("Number of kg entries not found: "+kgNotFoundCounter.get());

        if (features.contains("types")) {
            OneHotEncoder enc = new OneHotEncoder(20);
            enc.fit(featureTrain, KGTypes);
            enc.transform(featureTrain, KGTypes );
            enc.transform(featureTest, KGTypes);
        }


        //pass to ML Model
        String trainDataPath = writeToFile(featureTrain, "train", foldNo);
        String testDataPath = writeToFile(featureTest, "test", foldNo);

        //run MLModel

        String command = "python3 "+mlModelPath+" "+trainDataPath+" "+testDataPath+" "+configPath+" "+ experimentId +" "+foldNo;
        System.out.println("[EmbeddingModel]: Running command "+command);

        Process p=null;
        try {
            ProcessBuilder pb = new ProcessBuilder().command(Options.getPythonCmd(), mlModelPath, trainDataPath, testDataPath, configPath, ""+ experimentId, ""+foldNo)
                    .redirectError(ProcessBuilder.Redirect.INHERIT);
            p= pb.start();
            int exitval = p.waitFor();

            if (exitval!=0) {
                System.out.println("Command: "+command);
                BufferedReader errinput = new BufferedReader(new InputStreamReader(
                        p.getErrorStream()));
                errinput.lines().forEach(System.out::println);
                p.destroy();
                System.exit(5);
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
            p.destroy();
        }


        //parse ML results
        BufferedReader clfOut = new BufferedReader(new FileReader(testDataPath+"_pred"));
        clfOut.lines().forEach(s -> {
            String[] cols = s.split("\t");

            String osmID = cols[0];

            if (!mlscores.containsKey(osmID)) {
                mlscores.put(osmID, new ArrayList<>());
            }

            MLScore msc = new MLScore(cols[1], cols[2].equals("correct"), Double.parseDouble(cols[3]), Double.parseDouble(cols[4]));
            mlscores.get(osmID).add(msc);
        });

        foldToMLScore.put(foldNo, mlscores);

        p.destroy();

        //delete files
        new File(trainDataPath).delete();
        new File(testDataPath).delete();
        new File(testDataPath+"_pred").delete();
    }

    /**
     * Write test and train data to a file that is read by the classifcation mopdel
     * @param instances List of instances
     * @param part Distinguishes test and train data
     * @param foldNo Number of the current fold
     * @return Name of the file
     * @throws IOException
     */
    private String writeToFile(List<Instance> instances, String part, int foldNo) throws IOException {
        File directory = new File("testTrain");
        if (! directory.exists()){
            directory.mkdir();
        }


        String fName = "testTrain/"+ Util.getPID()+part+foldNo;

        List<String> lines = new ArrayList<>();


        for (Instance i: instances) {
                List<String> cols = new ArrayList<>();
                cols.add(i.osmID);
                cols.add(i.kgID);
                cols.add(i.label);
                for (String val: i.features) {
                    cols.add(""+val);
                }
                lines.add(String.join("\t", cols));
            }

        Path textFile = Paths.get(fName);
         Files.write(textFile, lines, StandardCharsets.UTF_8);

        File f = new File(fName);
        switch (part) {
            case "train":
                fName=f.getAbsolutePath();
                break;
            case "test":
                fName=f.getAbsolutePath();
                break;
        }

        return fName;
    }

    /**
     * Computes the features for a node candidate pair
     * @param c Current candidate
     * @param r Current node
     * @return Feature representation for the current pair.
     */
    private Instance computeFeatures(Candidate c, OSMRecord r) {
        List<String> featuresValues = new ArrayList<>();

        

        if (features.contains("types")) {
            String types=null;
            if (KGTypes.get(c.getId())==null) {
                types="";
            } else {
                types=KGTypes.get(c.getId());
            }
            featuresValues.add(types);
        }

        if (features.contains("distance")) {
            featuresValues.add(""+c.getGeoDistance());
        }

        if (features.contains("lgd_distance")) {
            double d = 1.0 / (1.0 + Math.exp(-12.0 * (1.0-c.getGeoDistance()/threshold)+6));
            featuresValues.add(""+d);
        }

        if (features.contains("name")) {
            if (r.getName() == null || c.getName() == null) {
                featuresValues.add(""+0);
            } else {
                JaroWinklerDistance dist = new JaroWinklerDistance();
                featuresValues.add(""+dist.apply(r.getName(), c.getName()));
            }

        }

        if (features.contains("osm_embedding")) {
            List<Double> osmFeatures = osmEmbeddings.get(r.getOsmId());
            if (Options.debug && osmFeatures == null) {
                osmFeatures = Util.getDummyEmbedding(19);
            } else if (osmFeatures == null) {
                System.err.println("Null Feature for OSM encountered: "+r.getOsmId());
                System.exit(2);
            }
            featuresValues.addAll(Util.listDoubleToString(osmFeatures));
        }

        if (features.contains("osm_tf_idf")) {
            List<Double> osmFeatures = osmTfIdf.get(r.getOsmId());
            if (osmFeatures == null) {
                System.err.println("Null Feature for OSM encountered: "+r.getOsmId());
                System.exit(2);
            }
            featuresValues.addAll(Util.listDoubleToString(osmFeatures));
        }

        if (features.contains("statement_count")) {
            String stmtCount=null;
            if (KGStatementCount.get(c.getId()) == null) {
                stmtCount="0";
            } else {
                stmtCount=""+KGStatementCount.get(c.getId());
            }
            featuresValues.add(stmtCount);
        }

        String label;
        if (r.getKgId().equals(c.getId())) {
            label = "correct";
        } else  if (Options.debug && Math.random() < 0.5){
            label= "correct";
        } else {
            label= "incorrect";
        }

        return new Instance(r.getOsmId(), c.getId(), label, featuresValues);
    }

    /**
     * Determines candidates for OSM nodes and transforms them to the feature space
     * @param osmRecords List of OSM nodes to be transformed
     * @param train True if the osm nodes are training data
     * @return List of transformed node candidate pairs.
     */
    private List<Instance> BlockAndTransformToFeatureSpace(Vector<OSMRecord> osmRecords, boolean train)  {
        List<Instance> result = Collections.synchronizedList(new ArrayList<>());

        Random seed = new Random(2);

        osmRecords.stream().parallel().forEach(r -> {
            try {
                List<Candidate> candidates = blocker.generateCandidates(r);

                for (Candidate c:  candidates) {
                    if (r.getKgId().equals(c.getId())) {
                        result.add(computeFeatures(c, r));
                       break;
                    }
                }

                int limit = candidates.size();
                if (train) {
                    Collections.shuffle(candidates,seed);
                    limit=Math.min(sampleCandidates, limit);
                }

                for (int i=0; i<limit; ++i) {
                    Candidate c = candidates.get(i);
                    if (c.getId().equals(r.getKgId())) {
                        limit=Math.min(candidates.size(), limit+1);
                        continue;
                    }
                    result.add(computeFeatures(c, r));
                }
                currentProgress.step();
            } catch (IOException |  SQLException e) {
                e.printStackTrace();
                blocker.destroy();
                System.exit(1);
            }
        });
        return result;
    }

    /**
     * Finds a link for single OSM record.
     * @param r The OSMRecord
     * @param foldNo The current fold
     * @return The candidate with the highest confidence.
     * @throws IOException
     * @throws SQLException
     */
    @Override
    public Candidate findLink(OSMRecord r, int foldNo) throws IOException, SQLException {
        if (logCandidates && (!candidateLog.containsKey(foldNo))) {
            candidateLog.put(foldNo, Collections.synchronizedList(new ArrayList<>()));
        }

        List<MLScore> scores = foldToMLScore.get(foldNo).get(r.getOsmId());

        if (scores==null) {
            if (!foldToNoCandidate.containsKey(foldNo)) {
                foldToNoCandidate.put(foldNo, 0);
            }
            foldToNoCandidate.put(foldNo, foldToNoCandidate.get(foldNo)+1);

            if (logCandidates) {
                logNoHit(r.getOsmId(), r.getKgId(), foldNo);
            }
            return Candidate.negativeHit();
        }


        double bestConfidence = -1;
        MLScore bestScore=null;
        for (MLScore sc: scores) {
            if (sc.prediction && sc.confCorrect > bestConfidence) {
                bestConfidence=sc.confCorrect;
                bestScore=sc;
                avgNoCandidates+=1;
            }
        }

        if (bestScore == null) {
            if (logCandidates) {
                logNoHit(r.getOsmId(), r.getKgId(), foldNo);
            }
            return Candidate.negativeHit();
        }

        if (logCandidates) {
            for (MLScore sc: scores) {
                boolean picked = sc.kgID.equals(bestScore.kgID);
                boolean correct = sc.kgID.equals(r.getKgId());

                String tuple = "(";
                tuple+=experimentId+",";
                tuple+=foldNo+",";
                tuple+=r.getOsmId()+",";
                tuple+=correct+",";
                tuple+="'"+sc.kgID+"',";
                tuple+=sc.confCorrect+",";
                tuple+=sc.prediction+",";
                tuple+=picked+",";
                tuple+="false";
                tuple+= ")";
                candidateLog.get(foldNo).add(tuple);
            }
        }

        noOsmRecords+=1;
        return new Candidate(bestScore.kgID);
    }

    /**
     * Dummy method implement the interface.
     * @param r The OSMRecord
     * @return
     * @throws IOException
     * @throws SQLException
     */
    @Override
    public Candidate findLink(OSMRecord r) throws IOException,  SQLException {
        System.out.println("Needs fold!");
        System.exit(6);
        return null;
    }

    /**
     * Logs an OSM node for which no candidate could be found
     * @param osmid ID of the OSM node
     * @param kgId ID of the groundtruth entity
     * @param foldNo Number of the current fold.
     */
    private void logNoHit(String osmid, String kgId, int foldNo) {
        String tuple = "(";
        tuple+=experimentId+",";
        tuple+=foldNo+",";
        tuple+=osmid+",";
        tuple+="false,";
        tuple+="'"+kgId+"',";
        tuple+="null,";
        tuple+="null,";
        tuple+="null,";
        tuple+="true";
        tuple+= ")";
        candidateLog.get(foldNo).add(tuple);
    }

    /**
     * Destroys the model and its components
     * @throws SQLException
     */
    @Override
    public void destroy() throws SQLException {
        super.destroy();

        avgNoCandidates /= noOsmRecords;
        System.out.println("[EmbeddingModel]: Found "+avgNoCandidates+" candidates on average.");

        System.out.println("[EmbeddingModel]: Missing Candidates per Fold:");
        for(int i: foldToNoCandidate.keySet()) {
            System.out.println("\t"+i+"\t"+foldToNoCandidate.get(i));
        }

        blocker.destroy();


        if (logCandidates) {
            PostGreDB db = new PostGreDB(Options.dbHost(), Options.dbName(), Options.dbUser(), Options.dbPassword(), 1);
            Connection con = db.getConnection();
            Statement stmt = con.createStatement();

            List<String> tuples = new ArrayList<>();
            for (int fold: candidateLog.keySet()) {
                 for (String s: candidateLog.get(fold)) {
                 }

                tuples.addAll(candidateLog.get(fold));
            }
            String query="INSERT INTO osmlinks.candidates (experiment, fold, osmid, correct, kgid, confidence, label, picked, no_candidate) VALUES ";


            stmt.execute(query+String.join(",", tuples));

            stmt.close();
            con.close();
            db.close();

        }

    }

    public List<String> getFeatures() {
        return features;
    }

    public String getOsmEmbeddingPath() {
        return osmEmbeddingPath;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setExperimentId(int experimentId) {
        this.experimentId = experimentId;
    }

    @Override
    public int getParamId() {
        return experimentId;
    }

    /**
     * Class used to represetn a node entity pair in feature space.
     */
    public static class Instance {
        public String osmID, kgID;
        public String label;
        public List<String> features;


        public Instance(String osmID, String kgID, String label, List<String> features) {
            this.osmID = osmID;
            this.kgID = kgID;
            this.features = features;
            this.label=label;
        }
    }

    /**
     * Class that represents the prediction for a single entity with respect to a single node.
     */
    private static class MLScore {
        String kgID;
        boolean prediction;
        double confCorrect;
        double conIncorrect;

        public MLScore(String kgID, boolean prediction, double confCorrect, double conIncorrect) {
            this.kgID = kgID;
            this.prediction = prediction;
            this.confCorrect = confCorrect;
            this.conIncorrect = conIncorrect;
        }
    }
}
