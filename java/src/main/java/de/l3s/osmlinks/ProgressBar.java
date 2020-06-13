package de.l3s.osmlinks;

import me.tongfei.progressbar.ProgressBarStyle;

/**
 * This class extends a progressbar for convenience.
 */
public class ProgressBar {

    private me.tongfei.progressbar.ProgressBar pb;
    private boolean running;
    private boolean stopped;

    /**
     * Constructor for a threadsafe progressbar.
     * @param task Name of the task
     * @param workload Number of atomic steps for this task
     */
    public ProgressBar(String task, int workload) {
        pb = new me.tongfei.progressbar.ProgressBar(task, workload, ProgressBarStyle.ASCII);
        running=false;
        stopped=false;
    }

    /**
     * Start the progress bar.
     */
    public void start() {
        if (!running) pb.start();
        running=true;
    }

    /**
     * Increment the progress bar by one. Stops the progressbar if the workload is reached.
     */
    public synchronized void step() {
        pb.step();
        if (pb.getCurrent() == pb.getMax()) stop();
    }

    /**
     * Stops the progress bar, regardless of the current progress.
     */
    public synchronized void stop() {
        if ((!running) || stopped) return;

        pb.stop();
        running=false;
        stopped=true;
    }
}