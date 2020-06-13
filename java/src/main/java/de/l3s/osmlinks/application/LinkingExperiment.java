package de.l3s.osmlinks.application;

import de.l3s.osmlinks.*;
import de.l3s.osmlinks.blocking.Candidate;
import de.l3s.osmlinks.models.*;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.queryparser.classic.ParseException;
import org.postgresql.util.PSQLException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * This is the main class  used to run the experiment.
 */
public class LinkingExperiment {

    private String configName;
    private Vector<OSMRecord> osmData;
    private List<AbstractModel> models;
    private Random rand;
    private List<Vector<OSMRecord>> folds;
    private Map<AbstractModel, Score> scores;
    private Map<AbstractModel,Integer> experimentIds;
    private int noFolds=Options.getNoFolds();
    private Map<String, String> kgToType;


    /**
     * Create an experiment from a given configuration file
     * @param configName Path to the configuration file
     */
    public LinkingExperiment(String configName) {
        this.configName = configName;
        this.rand = new Random(1);
        this.folds = new ArrayList<>();
        this.scores = new HashMap<>();
        this.models=new ArrayList<>();
        this.experimentIds=new ConcurrentHashMap<>();
        this.kgToType=new ConcurrentHashMap<>();
    }

    /**
     * Parses data which is shared accross experiments
     * @throws IOException
     * @throws SQLException
     */
    private void parseData() throws IOException, SQLException {
       osmData = Util.parseOSMRecords(Options.getOSMPath());

       List<Options.ModelName> models = Options.getModels();
       for (Options.ModelName n: models) {
           AbstractModel m=null;
           switch (n) {
               case embedding:
                    for (String osmEmbedding: Options.getOSMEmbeddingPaths()) {
                            for (double geoThreshold: Options.getGeoThreshold()) {
                                    m = new EmbeddingModel(osmEmbedding,
                                            Options.getOsmTfIdfPath(),
                                            Options.getKGEmbeddingPath(),
                                            geoThreshold,
                                            Options.getMLModelPath(),
                                            configName,
                                            Options.logCandidates(),
                                            Options.features(),
                                            Options.getKGFeaturePath());

                            }
                            scores.put(m, new Score(noFolds));
                            this.models.add(m);
                    }
                    break;
            }
       }

       try(Stream<String> inputStream = Files.lines(new File(Options.getKGFeaturePath()).toPath(), StandardCharsets.UTF_8)) {
                inputStream.parallel().forEach( line -> {
                    String[] parts = line.split("\t");
                    kgToType.put(parts[0], parts[1]);
                });
            }
        }

    /**
     * Runs the experiment
     * @throws IOException
     * @throws SQLException
     */
    private void run() throws IOException, SQLException {
        parseData();

        createFolds(noFolds);
        runExperiments();
        destroyModels();

        reportResults(Options.print());
    }

    /**
     * Destroy all created models
     * @throws SQLException
     */
    private void destroyModels() throws SQLException {
        for (AbstractModel m: models) {
            m.destroy();
        }
    }

    /**
     * Creates the folds from the groundtruh for cross-fold validation
     * @param n Number of folds
     */
    private void createFolds(int n) {
        List<OSMRecord> shuffled = new ArrayList(osmData);
        Collections.shuffle(shuffled, rand);

        int size = shuffled.size() / n;
        int remainder = shuffled.size() % n;
        int lastPos=0;

        for (int i=1; i<=n; ++i) {
            int nextPos = (i)*size;

            if (n==1) {
                nextPos=size/5;
            }

            if (i <= remainder) {
                nextPos+=1;
            }
            folds.add(new Vector(shuffled.subList(lastPos, nextPos)));
            lastPos=nextPos;
        }
    }

    /**
     * Runs the experiments
     * @throws SQLException
     */
    private void runExperiments() throws  SQLException {

        AtomicInteger foldNo = new AtomicInteger(0);
        determineExperimentIds();

        int workload=noFolds*osmData.size();

        ProgressBar blockingProgress = new ProgressBar("Training", workload);
        blockingProgress.start();


        folds.stream().parallel().forEach(test -> {
            int i = foldNo.getAndIncrement();
            Vector<OSMRecord> train = new Vector<>(osmData);
            train.removeAll(test);

            for (AbstractModel m : models) {
                    m.setExperimentId(experimentIds.get(m));
                try {
                    m.train(train, test, i, blockingProgress);
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(3);
                }

                Score sc = scores.get(m);
                final int currentIndex = i;
                test.stream().parallel().forEach(r ->  {
                    Candidate c = null;
                    try {
                        c = m.findLink(r, currentIndex);
                    } catch (IOException | SQLException | ParseException e) {
                        e.printStackTrace();
                        System.exit(1);
                    }

                    if (!kgToType.containsKey(r.getKgId())) {
                        System.out.println("[LinkingExperiment]: No type for: "+r.getKgId());
                    }

                    if (c.equals(Candidate.negativeHit())) {
                        sc.noCandidate(currentIndex, kgToType.get(r.getKgId()));
                    } else if (c.getId().equals(r.getKgId())) {
                        sc.correct(currentIndex, kgToType.get(r.getKgId()));
                    } else {
                        sc.incorrect(currentIndex, kgToType.get(r.getKgId()));
                    }
                });
            }
        });

        //make sure the progressbar stops
        blockingProgress.stop();
    }

    /**
     * Reports the results either to stdout or to the database.
     * @param print If true, results will be printed but not stored in the database.
     * @throws SQLException
     * @throws IOException
     */
    private void reportResults(boolean print) throws SQLException, IOException {
        if (print){
            printResults();
        } else {
            writeResultsToDB();
        }
        reportTypes();
    }

    /**
     * Creates a report with respect to entity types
     * @throws IOException
     */
    private void reportTypes() throws IOException {
        File directory = new File("typeReports");
        if (! directory.exists()){
            directory.mkdir();
        }

        String osmName = new File(Options.getOSMPath()).getName();


        for (AbstractModel m: models) {
            String fName= "typeReports/"+osmName+"_"+m.getClass().getSimpleName();
            Score sc = scores.get(m);
            List<String> lines = sc.typeReport();
            FileUtils.writeStringToFile(new File(fName), String.join("\n", lines), "utf-8");
        }
    }


    /**
     * Prints the results of the experiment to stdout.
     */
    private void printResults() {
        for (AbstractModel m: models) {
            Score sc = scores.get(m);

            System.out.println(m.getClass().getSimpleName());
            System.out.println("\t\tFold\tCorrect\tIncorrect\tNo Candidate\tPrecision\tRecall\tF1");
            for (int i=0; i<noFolds; ++i) {
                System.out.printf("\t\t%d\t%d\t%d\t%d\t%f\t%f\t%f\n", i, sc.getCorrect(i), sc.getIncorrect(i), sc.getNoCandidate(i), sc.getPrecision(i), sc.getRecall(i), sc.getF1(i));
            }
            System.out.println();
            System.out.printf("\t\t%s\t%f\t%f\t%f\t%f\t%f\t%f\n", "AVG", sc.getAvgCorrect(), sc.getAvgIncorrect(), sc.getAvgNoCandidate(), sc.getAvgPrecision(), sc.getAvgRecall(), sc.getAvgF1());

        }
    }

    /**
     * Determines the id of the current experiment from the database.
     * @throws SQLException
     */
    private void determineExperimentIds() throws SQLException {
        PostGreDB db = new PostGreDB(Options.dbHost(), Options.dbName(), Options.dbUser(), Options.dbPassword(), 1);
        Connection con = db.getConnection();
        Statement stmt = con.createStatement();

        String q = "insert into osmlinks.LinkingExperiment (model) values (null) returning id;";

        for (AbstractModel m: models) {
           ResultSet rs= stmt.executeQuery(q);
           int expId = -1;
           while (rs.next()){
               expId = rs.getInt(1);
           }
           experimentIds.put(m, expId);
            rs.close();

            String q_fold = "insert into osmlinks.LinkingExperiment_folds (experiment, fold) values " ;
            for (int i=0; i< noFolds; ++i) {
                q_fold+="("+expId+","+i+"),";
            }

            q_fold=q_fold.substring(0, q_fold.length()-1)+";";
            stmt.execute(q_fold);
        }
        stmt.close();
        con.close();
        db.close();
    }

    /**
     * Stores the results for the current experiment to the database.
     * @throws SQLException
     */
    private void writeResultsToDB() throws SQLException {
        PostGreDB db = new PostGreDB(Options.dbHost(), Options.dbName(), Options.dbUser(), Options.dbPassword(), 1);
        Connection con = db.getConnection();
        Statement cur = con.createStatement();


        for (AbstractModel m: models) {
            Score sc = scores.get(m);
            int params = m.getParamId();

            String avgQuery  = "update osmlinks.LinkingExperiment  SET ";

            //(model, osm, kg, params, correct, incorrect, not_found, precision , recall, f1)

            avgQuery += "osm='"+Options.getOSMPath()+"', ";
            avgQuery += "kg='"+Options.getKGName()+"', ";
            avgQuery += "model='"+m.getClass().getSimpleName()+"', ";


            if (EmbeddingModel.class.isInstance(m)) {
                EmbeddingModel emb = (EmbeddingModel) m;

                avgQuery +="osm_embedding='"+emb.getOsmEmbeddingPath()+"', ";
                avgQuery +="features='"+String.join(",", emb.getFeatures())+"', ";
                avgQuery +="classifier='"+Options.getClassifier()+"', ";
                avgQuery +="threshold="+Options.getGeoThreshold().get(0)+", ";
            }



           if (Options.experimentName() != null) {
               avgQuery +="name='"+Options.experimentName()+"', ";
           }

            avgQuery += "correct="+Util.checkNan(sc.getAvgCorrect())+", ";
            avgQuery += "incorrect="+Util.checkNan(sc.getAvgIncorrect())+", ";
            avgQuery += "not_found="+Util.checkNan(sc.getAvgNoCandidate())+", ";
            avgQuery += "precision="+Util.checkNan(sc.getAvgPrecision())+", ";
            avgQuery += "recall="+Util.checkNan(sc.getAvgRecall())+", ";
            avgQuery += "f1="+Util.checkNan(sc.getAvgF1())+" ";


            avgQuery = avgQuery +" WHERE id="+experimentIds.get(m)+";";

            System.out.println(avgQuery);

            cur.execute(avgQuery);


            String foldQuery = "update osmlinks.LinkingExperiment_folds SET ";


            for (int i=0; i<noFolds; ++i) {
                String row = "";
                row += "correct="+Util.checkNan(sc.getCorrect(i))+",";
                row += "incorrect="+Util.checkNan(sc.getIncorrect(i))+",";
                row += "not_found="+Util.checkNan(sc.getNoCandidate(i))+",";
                row += "precision="+Util.checkNan(sc.getPrecision(i))+",";
                row += "recall="+Util.checkNan(sc.getRecall(i))+",";
                row += "f1="+Util.checkNan(sc.getF1(i))+" ";

                String q=foldQuery+row+"WHERE experiment="+experimentIds.get(m)+" AND fold="+i+";";
                try {
                    cur.execute(q);
                } catch (PSQLException e) {
                    System.out.println("PSQL!");
                    System.exit(-200);
                }
            }
        }

        cur.close();
        con.close();
        db.close();
    }


    /**
     * Main method. Expects configuration file as argument. If multiple files are given,
     * multiple independent experiments are run.
     * @param args One ore more configuration files.
     * @throws IOException
     * @throws SQLException
     */
    public static void main(String[] args) throws IOException, SQLException {
        for (int i=0; i<args.length; ++i) {
            System.out.println("[LinkingExperiment]: Running "+args[i]);
            Options.parseConfig(args[i]);
            LinkingExperiment app = new LinkingExperiment(args[i]);
            app.run();
        }
    }
}
