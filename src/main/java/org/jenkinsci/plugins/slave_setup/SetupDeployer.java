package org.jenkinsci.plugins.slave_setup;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.tasks.Shell;
import hudson.util.LogTaskListener;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a deployment to all or a single node of the given fileset and executes the command line.
 *
 * @author Frederik Fromm
 */
public class SetupDeployer {
    /**
     * the logger.
     */
    private static final Logger LOGGER = Logger.getLogger(SetupDeployer.class.getName());

    /**
     * Returns a list of all active slaves connected to the master.
     *
     * @return a list of all active slaves connected to the master
     */
    public List<Computer> getAllActiveSlaves() {
        final List<Computer> computers = Arrays.asList(Jenkins.getInstance().getComputers());

        List<Computer> activeComputers = new ArrayList<Computer>();

        for (Computer computer : computers) {
            if (!(computer instanceof Jenkins.MasterComputer) && computer.isOnline()) {
                activeComputers.add(computer);
            }
        }

        return activeComputers;
    }

    /**
     * @param c        the computer to upload th files to
     * @param root     the computer's target directory
     * @param listener the listener for logging etc.
     * @param config   the SetupConfig object containing the all SetupConfigItems
     * @throws IOException
     * @throws InterruptedException
     */
    public void deployToComputer(Computer c, FilePath root, TaskListener listener, SetupConfig config) throws IOException, InterruptedException {
        List<SetupConfigItem> setupConfigItems = config.getSetupConfigItems();

        for (SetupConfigItem setupConfigItem : setupConfigItems) {
            this.deployToComputer(c, root, listener, setupConfigItem);
        }
    }

    /**
     * @param c        the computer to upload th files to
     * @param root     the computer's target directory
     * @param listener the listener for logging etc.
     * @param setupConfigItem   the SetupConfigItem object containing the source dir and the command line
     * @throws IOException
     * @throws InterruptedException
     */
    public void deployToComputer(Computer c, FilePath root, TaskListener listener, SetupConfigItem setupConfigItem) throws IOException, InterruptedException {

        //do not deploy if label of computer and config do not match.
        if (StringUtils.isNotBlank(setupConfigItem.getAssignedLabelString())) {
            Label label = Label.get(setupConfigItem.getAssignedLabelString());

            if (label != null) {
                if (!label.contains(c.getNode())) {
                    return;
                }
            }
        }

        // copy files
        File sd = setupConfigItem.getFilesDir();
        if (sd != null) {
            listener.getLogger().println("Copying setup script files");
            new FilePath(sd).copyRecursiveTo(root);
        }

        // execute command line
        String cmdLine = setupConfigItem.getCommandLine();
        executeScript(c, root, listener, cmdLine);
    }

    private void executeScript(Computer c, FilePath root, TaskListener listener, String cmdLine) throws IOException, InterruptedException {
        if (cmdLine != null) {
            listener.getLogger().println("Executing script");
            Node node = c.getNode();
            Launcher launcher = root.createLauncher(listener);
            Shell s = new Shell(cmdLine);
            FilePath script = s.createScriptFile(root);
            int r = launcher.launch().cmds(s.buildCommandLine(script)).envs(getEnvironment(node)).stdout(listener).pwd(root).join();

            if (r != 0) {
                throw new AbortException("script failed");
            }

            listener.getLogger().println("script completed successfully");
        }
    }

    /**
     * @param computerList the list of computers to upload the setup files and execute command line
     * @param setupConfigItem       the SetupConfigItem object
     */
    public void deployToComputers(List<Computer> computerList, SetupConfigItem setupConfigItem) {
        for (Computer computer : computerList) {
            try {
                FilePath root = computer.getNode().getRootPath();
                LogTaskListener listener = new LogTaskListener(LOGGER, Level.ALL);
                this.deployToComputer(computer, root, listener, setupConfigItem);
            } catch (IOException e) {
                LOGGER.severe(e.getMessage());
            } catch (InterruptedException e) {
                LOGGER.severe(e.getMessage());
            }
        }
    }

    /**
     * @param computerList the list of computers to upload the setup files and execute command line
     * @param config       the SetupConfig object
     */
    public void deployToComputers(List<Computer> computerList, SetupConfig config) {
        for (Computer computer : computerList) {
            try {
                FilePath root = computer.getNode().getRootPath();
                LogTaskListener listener = new LogTaskListener(LOGGER, Level.ALL);
                this.deployToComputer(computer, root, listener, config);
            } catch (IOException e) {
                LOGGER.severe(e.getMessage());
            } catch (InterruptedException e) {
                LOGGER.severe(e.getMessage());
            }
        }
    }

    /**
     * Returns 0 if all prepare scripts were executes without error.
     * @return 0 if all prepare scripts were executes without error
     */
    public int executePrepareScripts(SetupConfig config, TaskListener listener) {
        Computer computer = Jenkins.MasterComputer.currentComputer();

        int returnCode = 0; // everything went well.

        for (SetupConfigItem setupConfigItem : config.getSetupConfigItems()) {
            try {
                FilePath filePath = new FilePath(setupConfigItem.getFilesDir());
                this.executeScript(computer, filePath, listener, setupConfigItem.getPrepareScript());
            } catch(Exception e) {
                returnCode++; // increment return code;
            }
        }

        return returnCode;
    }

    /**
     * Returns the environment variables for the given node.
     *
     * @param node node to get the environment variables from
     * @return the environment variables for the given node
     */
    private EnvVars getEnvironment(Node node) {
        EnvironmentVariablesNodeProperty env = node.getNodeProperties().get(EnvironmentVariablesNodeProperty.class);
        return env != null ? env.getEnvVars() : new EnvVars();
    }


}
