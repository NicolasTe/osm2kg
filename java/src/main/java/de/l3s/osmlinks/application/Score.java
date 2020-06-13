package de.l3s.osmlinks.application;

import java.util.*;

/**
 * This class represents the perfomance achieved in the link discovery experiment.
 */
public class Score {

    Map<Integer, Integer> correct;
    Map<Integer, Integer> incorrect;
    Map<Integer, Integer> noCandidate;
    Map<String, Map<Integer, Integer>> type_correct;
    Map<String, Map<Integer, Integer>> type_incorrect;
    Map<String, Map<Integer, Integer>> type_noCandidate;

    /**
     * Constructor
     * @param noFolds Number of considered folds
     */
    public Score(int noFolds) {
        this.correct=new HashMap<>();
        this.incorrect=new HashMap<>();
        this.noCandidate=new HashMap<>();
        this.type_correct=new HashMap<>();
        this.type_incorrect=new HashMap<>();
        this.type_noCandidate=new HashMap<>();


        for (int i=0; i<noFolds; ++i) {
            correct.put(i, 0);
            incorrect.put(i, 0);
            noCandidate.put(i, 0);

        }
    }

    /**
     * Creates a reporty with respect to the entity type
     * @return
     */
    public List<String> typeReport() {
        List<String> result = new ArrayList<>();

        List<String> header = new ArrayList<>();
        header.add("Type");
        header.add("No. Instances");
        header.add("Correct");
        header.add("Incorrect");
        header.add("No_Candidate");
        header.add("Precision");
        header.add("Recall");
        header.add("F1");

        result.add(String.join("\t", header));

        Set<String> types = new HashSet<>();
        types.addAll(type_correct.keySet());
        types.addAll(type_incorrect.keySet());
        types.addAll(type_noCandidate.keySet());


        for (String t: types) {
            int t_correct = 0;
            int t_incorrect = 0;
            int t_noCandidate =0;

            if (type_correct.containsKey(t)) {
                Map<Integer, Integer> foldMap = type_correct.get(t);
                for (int i: foldMap.keySet()) {
                    t_correct += foldMap.get(i);
                }
                t_correct /= ((double) foldMap.keySet().size());
            }

            if (type_incorrect.containsKey(t)) {
                Map<Integer, Integer> foldMap = type_incorrect.get(t);
                for (int i: foldMap.keySet()) {
                    t_incorrect += foldMap.get(i);
                }
                t_incorrect /= ((double) foldMap.keySet().size());
            }

            if (type_noCandidate.containsKey(t)) {
                Map<Integer, Integer> foldMap = type_noCandidate.get(t);
                for (int i: foldMap.keySet()) {
                    t_noCandidate += foldMap.get(i);
                }
                t_noCandidate /= ((double) foldMap.keySet().size());
            }

                double precision=precision(t_correct, t_incorrect);
            double recall=recall(t_correct, t_incorrect, t_noCandidate);

            List<String> cols = new ArrayList<>();
            cols.add(t);
            cols.add(""+(t_correct+t_incorrect+t_noCandidate));
            cols.add(""+t_correct);
            cols.add(""+t_incorrect);
            cols.add(""+t_noCandidate);
            cols.add(""+precision);
            cols.add(""+recall);
            cols.add(""+F1(precision, recall));
            result.add(String.join("\t", cols));
        }
        return result;
    }

    /**
     * Helper method to log the progress
     */
    private void increaseTypeMapCount(Map<String, Map<Integer, Integer>> m, String types, int fold) {
        if (types == null) {
            types = "UNK";
        }

        String[] typeArray = types.split(",");
        for(String t: typeArray) {
            if (!m.containsKey(t)) {
                m.put(t, new HashMap<>());
            }

            Map<Integer, Integer> foldMap = m.get(t);

            if (!foldMap.containsKey(fold)) {
                foldMap.put(fold, 0);
            }
            foldMap.put(fold, foldMap.get(fold)+1);
        }
    }

    /*
    The following methods are simple methods that compute
    the considered metrics.
     */

    public synchronized void correct(int fold, String types) {
        correct.put(fold, correct.get(fold)+1);
        increaseTypeMapCount(type_correct, types, fold);
    }

    public synchronized void incorrect(int fold, String types) {
        incorrect.put(fold, incorrect.get(fold)+1);
        increaseTypeMapCount(type_incorrect, types, fold);

    }

    public synchronized void noCandidate(int fold, String types) {
        noCandidate.put(fold, noCandidate.get(fold)+1);
        increaseTypeMapCount(type_noCandidate, types, fold);
    }

    public int getCorrect(int fold) {
        return correct.get(fold);
    }

    public int getIncorrect(int fold) {
        return incorrect.get(fold);
    }

    public int getNoCandidate(int fold) {
        return noCandidate.get(fold);
    }

    private double precision(double correct, double incorrect) {
        return correct / (correct+incorrect);
    }

    private double recall(double correct, double incorrect, double noCandidate) {
        return correct / (correct + incorrect + noCandidate);
    }

    private double F1(double precision, double recall) {
        if (precision==0 && recall==0) {
            return 0;
        }
        return 2d * precision * recall / (precision + recall);
    }

    public double getPrecision(int fold) {
        return precision(correct.get(fold), incorrect.get(fold));
    }

    public double getRecall(int fold) {
        return recall(correct.get(fold), incorrect.get(fold), noCandidate.get(fold));
    }

    public double getF1(int fold) {
        return F1(getPrecision(fold), getRecall(fold));
    }

    public double getAvgCorrect() {
        double result = 0;
        for (Integer i: correct.keySet()) {
            result += correct.get(i);
        }
        result /= ((double) correct.size());
        return result;
    }

    public double getAvgIncorrect() {
        double result = 0;
        for (Integer i: incorrect.keySet()) {
            result += incorrect.get(i);
        }
        result /= ((double) incorrect.size());
        return result;
    }

    public double getAvgNoCandidate() {
        double result = 0;
        for (Integer i: noCandidate.keySet()) {
            result += noCandidate.get(i);
        }
        result /= ((double) noCandidate.size());
        return result;
    }

    public double getAvgPrecision() {
        double result = 0;

        for (Integer i: correct.keySet()) {
            result += getPrecision(i);
        }
        result /= ((double) correct.size());

        return result;
    }

    public double getAvgRecall() {
        double result = 0;
        for (Integer i: correct.keySet()) {
            result += getRecall(i);
        }
        result /= ((double) correct.size());
        return result;
    }

    public double getAvgF1() {
        return F1(getAvgPrecision(), getAvgRecall());
    }

}

