package sos.scheduler.job;

/** @author andreas pueschel */
public class JobSchedulerRestart extends JobSchedulerJobAdapter {

    private int timeout = 600;

    public boolean spooler_process() {
        spooler_log.info(".......extending JobSchedulderAdapter");
        if (spooler_task.params().var("timeout") != null && !spooler_task.params().var("timeout").isEmpty()) {
            timeout = Integer.parseInt(spooler_task.params().var("timeout"));
            spooler_log.info(".. job parameter [timeout]: " + timeout);
        }
        if (timeout > 0) {
            spooler.terminate_and_restart(timeout);
        } else {
            spooler.terminate_and_restart();
        }
        return false;
    }

}